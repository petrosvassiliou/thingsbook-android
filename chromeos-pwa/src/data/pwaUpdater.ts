import { APP_VERSION } from '../domain/types.ts';
import {
  compareSemver,
  isEligibleReleasePayload,
  isPrereleaseVersion,
  selectLatestEligibleRelease
} from './versionUtils.ts';

export {
  compareSemver,
  isEligibleReleasePayload,
  isPrereleaseVersion,
  selectLatestEligibleRelease
};

let swRegistrationRef: ServiceWorkerRegistration | null = null;
let swUpdateFn: ((reloadPage?: boolean) => Promise<void>) | null = null;
let swUpdateAvailable = false;

export function initSafeServiceWorker() {
  try {
    if (typeof window === 'undefined' || !('serviceWorker' in navigator)) return;
    void import('virtual:pwa-register')
      .then(({ registerSW }) => {
        swUpdateFn = registerSW({
          immediate: true,
          onRegisteredSW(_swUrl, registration) {
            if (registration) {
              swRegistrationRef = registration;
            }
          },
          onNeedRefresh() {
            swUpdateAvailable = true;
          },
          onRegisterError() {
            // Gracefully ignore SW registration errors in sandboxed preview iframes
          }
        });
      })
      .catch(() => {
        // Gracefully ignore in non-PWA or Node test environments
      });
  } catch {
    // Sandboxed iframe / restricted origin: continue offline-first IndexedDB without throwing
  }
}

export interface UpdateCheckResult {
  hasUpdate: boolean;
  latestVersion: string;
  releaseUrl?: string;
  releaseNotes?: string;
  swWaiting: boolean;
}

export async function checkForSwAndReleaseUpdates(
  currentVersion: string = APP_VERSION,
  includeBeta: boolean = isPrereleaseVersion(currentVersion)
): Promise<UpdateCheckResult> {
  const effectiveIncludeBeta = Boolean(includeBeta) || isPrereleaseVersion(currentVersion);
  let swWaiting = swUpdateAvailable;
  try {
    if (swRegistrationRef) {
      await swRegistrationRef.update();
      if (swRegistrationRef.waiting) {
        swWaiting = true;
        swUpdateAvailable = true;
      }
    }
  } catch {
    // Ignore offline SW check error
  }

  try {
    const endpoint = effectiveIncludeBeta
      ? 'https://api.github.com/repos/petrosvassiliou/thingsbook-android/releases?per_page=30'
      : 'https://api.github.com/repos/petrosvassiliou/thingsbook-android/releases/latest';
    const res = await fetch(endpoint, {
      cache: 'no-store',
      headers: { Accept: 'application/vnd.github+json' }
    });
    if (res.ok) {
      const data: unknown = await res.json();
      const rec = effectiveIncludeBeta
        ? selectLatestEligibleRelease(data, currentVersion, true)
        : isEligibleReleasePayload(data, currentVersion, false)
          ? (data as Record<string, unknown>)
          : null;
      if (rec) {
        const rawTag = String(rec.tag_name).trim();
        const latestClean = rawTag.replace(/^[vV]/, '').trim() || currentVersion;
        const newer = compareSemver(latestClean, currentVersion) > 0;
        return {
          hasUpdate: newer || swWaiting,
          latestVersion: newer ? latestClean : currentVersion,
          releaseUrl: newer && typeof rec.html_url === 'string' ? rec.html_url : undefined,
          releaseNotes:
            newer && typeof rec.body === 'string' ? rec.body.slice(0, 280) : undefined,
          swWaiting
        };
      }
      return {
        hasUpdate: swWaiting,
        latestVersion: currentVersion,
        swWaiting
      };
    }
  } catch {
    // Fall through to raw.githubusercontent.com fallback
  }

  // Fallback to raw.githubusercontent.com PARITY_MANIFEST.json when GitHub REST API is rate-limited
  try {
    const rawUrls = effectiveIncludeBeta
      ? [
          'https://raw.githubusercontent.com/petrosvassiliou/thingsbook-android/beta-2.0/macos/PARITY_MANIFEST.json',
          'https://raw.githubusercontent.com/petrosvassiliou/thingsbook-android/main/macos/PARITY_MANIFEST.json'
        ]
      : [
          'https://raw.githubusercontent.com/petrosvassiliou/thingsbook-android/main/macos/PARITY_MANIFEST.json'
        ];
    let bestVersion = currentVersion;
    for (const rawUrl of rawUrls) {
      const rawRes = await fetch(rawUrl, { cache: 'no-store' });
      if (!rawRes.ok) continue;
      const manifest = (await rawRes.json()) as Record<string, unknown>;
      const verRaw = String(
        manifest.syncedAndroidVersionName ?? manifest.macosVersion ?? ''
      )
        .trim()
        .replace(/^[vV]/, '');
      if (!/^\d+\.\d+\.\d+/.test(verRaw)) continue;
      if (!effectiveIncludeBeta && isPrereleaseVersion(verRaw)) continue;
      if (compareSemver(verRaw, bestVersion) > 0) {
        bestVersion = verRaw;
      }
    }
    const newer = compareSemver(bestVersion, currentVersion) > 0;
    if (newer) {
      return {
        hasUpdate: true,
        latestVersion: bestVersion,
        releaseUrl: `https://github.com/petrosvassiliou/thingsbook-android/releases/tag/v${bestVersion}`,
        swWaiting
      };
    }
  } catch {
    // Offline: fall back to SW update state
  }

  return {
    hasUpdate: swWaiting,
    latestVersion: currentVersion,
    swWaiting
  };
}

export async function applyPendingSwUpdate(
  targetUpdate?: UpdateCheckResult | null
): Promise<'reloaded' | 'opened_release'> {
  if (swUpdateAvailable && swUpdateFn) {
    try {
      await swUpdateFn(true);
      return 'reloaded';
    } catch {
      // Fall through to manual SW & origin probe
    }
  }

  if (typeof window !== 'undefined') {
    let foundFreshWorkerOrAsset = false;

    try {
      if (swRegistrationRef) {
        await swRegistrationRef.update();
        if (swRegistrationRef.waiting || swRegistrationRef.installing) {
          foundFreshWorkerOrAsset = true;
        }
      }
    } catch {
      // Ignore transient SW check error
    }

    if (!foundFreshWorkerOrAsset) {
      try {
        const res = await fetch(`/?_tb_update_probe=${Date.now()}`, {
          cache: 'no-store',
          headers: { 'Cache-Control': 'no-cache' }
        });
        if (res.ok) {
          const html = await res.text();
          const currentScripts = Array.from(
            document.querySelectorAll<HTMLScriptElement>('script[src]')
          )
            .map((s) => s.getAttribute('src') || '')
            .filter((src) => src.includes('/assets/'));
          if (
            currentScripts.length > 0 &&
            currentScripts.some((src) => !html.includes(src))
          ) {
            foundFreshWorkerOrAsset = true;
          }
        }
      } catch {
        // Ignore offline fetch error
      }
    }

    if (foundFreshWorkerOrAsset) {
      if (swUpdateFn) {
        try {
          await swUpdateFn(true);
          return 'reloaded';
        } catch {
          // Fallback to direct reload
        }
      }
      window.location.reload();
      return 'reloaded';
    }

    if (targetUpdate?.releaseUrl && /^https:\/\//i.test(targetUpdate.releaseUrl)) {
      window.open(targetUpdate.releaseUrl, '_blank', 'noopener,noreferrer');
      return 'opened_release';
    }

    window.location.reload();
  }
  return 'reloaded';
}
