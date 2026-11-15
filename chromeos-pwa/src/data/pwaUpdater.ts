import { APP_VERSION } from '../domain/types.ts';
import { compareSemver, isEligibleReleasePayload } from './versionUtils.ts';

export { compareSemver, isEligibleReleasePayload };

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
  currentVersion: string = APP_VERSION
): Promise<UpdateCheckResult> {
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
    const res = await fetch(
      'https://api.github.com/repos/petrosvassiliou/thingsbook-android/releases/latest',
      { headers: { Accept: 'application/vnd.github+json' } }
    );
    if (res.ok) {
      const data: unknown = await res.json();
      if (isEligibleReleasePayload(data, currentVersion)) {
        const rec = data as Record<string, unknown>;
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
    }
  } catch {
    // Offline or blocked GitHub API: fall back to SW update state
  }

  return {
    hasUpdate: swWaiting,
    latestVersion: currentVersion,
    swWaiting
  };
}

export async function applyPendingSwUpdate(): Promise<void> {
  if (swUpdateFn) {
    try {
      await swUpdateFn(true);
      return;
    } catch {
      // fallback reload
    }
  }
  if (typeof window !== 'undefined') {
    window.location.reload();
  }
}
