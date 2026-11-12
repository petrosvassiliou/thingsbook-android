import { registerSW } from 'virtual:pwa-register';
import { APP_VERSION } from '../domain/types';

let swRegistrationRef: ServiceWorkerRegistration | null = null;
let swUpdateFn: ((reloadPage?: boolean) => Promise<void>) | null = null;
let swUpdateAvailable = false;

export function initSafeServiceWorker() {
  try {
    if (typeof window === 'undefined' || !('serviceWorker' in navigator)) return;
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

function compareSemver(a: string, b: string): number {
  const pa = a.replace(/^v/i, '').split(/[-.]/).map((n) => parseInt(n, 10) || 0);
  const pb = b.replace(/^v/i, '').split(/[-.]/).map((n) => parseInt(n, 10) || 0);
  for (let i = 0; i < 3; i++) {
    const da = pa[i] || 0;
    const db = pb[i] || 0;
    if (da > db) return 1;
    if (da < db) return -1;
  }
  return 0;
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
      const data = await res.json();
      const rawTag = typeof data?.tag_name === 'string' ? data.tag_name : currentVersion;
      const latestClean = rawTag.replace(/^v/i, '').trim() || currentVersion;
      const newer = compareSemver(latestClean, currentVersion) > 0;
      return {
        hasUpdate: newer || swWaiting,
        latestVersion: latestClean,
        releaseUrl: typeof data?.html_url === 'string' ? data.html_url : undefined,
        releaseNotes: typeof data?.body === 'string' ? data.body.slice(0, 280) : undefined,
        swWaiting
      };
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
  window.location.reload();
}
