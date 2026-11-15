import {
  deleteApp,
  getApps,
  initializeApp,
  type FirebaseApp,
  type FirebaseOptions
} from 'firebase/app';
import { getAuth, type Auth } from 'firebase/auth';
import { getFirestore, type Firestore } from 'firebase/firestore';

export const DEFAULT_FIREBASE_CONFIG: FirebaseOptions = {
  apiKey: 'AIzaSyCUtSvssQOesHW-heLhNzNvykI1zCl7deY',
  authDomain: 'thingsbook-prod.firebaseapp.com',
  projectId: 'thingsbook-prod',
  storageBucket: 'thingsbook-prod.firebasestorage.app',
  messagingSenderId: '116792699915',
  appId: '1:116792699915:android:2e24f279bece36e1c82b0e'
};

const CUSTOM_FB_STORAGE_KEY = 'thingsbook_custom_firebase_config';

export function getActiveFirebaseOptions(): FirebaseOptions {
  try {
    if (typeof localStorage !== 'undefined') {
      const raw = localStorage.getItem(CUSTOM_FB_STORAGE_KEY);
      if (raw) {
        const parsed = JSON.parse(raw);
        if (parsed?.projectId && parsed?.apiKey) {
          return {
            apiKey: String(parsed.apiKey).trim(),
            authDomain: `${String(parsed.projectId).trim()}.firebaseapp.com`,
            projectId: String(parsed.projectId).trim(),
            storageBucket: `${String(parsed.projectId).trim()}.firebasestorage.app`,
            appId: parsed.appId ? String(parsed.appId).trim() : DEFAULT_FIREBASE_CONFIG.appId
          };
        }
      }
    }
  } catch {
    // fallback to default
  }
  return DEFAULT_FIREBASE_CONFIG;
}

function computeConfigSignature(opts: FirebaseOptions): string {
  return JSON.stringify({
    apiKey: opts.apiKey ?? '',
    authDomain: opts.authDomain ?? '',
    projectId: opts.projectId ?? '',
    storageBucket: opts.storageBucket ?? '',
    appId: opts.appId ?? ''
  });
}

function hashSignatureShort(sig: string): string {
  let hash = 2166136261;
  for (let i = 0; i < sig.length; i++) {
    hash ^= sig.charCodeAt(i);
    hash = Math.imul(hash, 16777619);
  }
  return (hash >>> 0).toString(16);
}

const DEFAULT_CONFIG_SIGNATURE = computeConfigSignature(DEFAULT_FIREBASE_CONFIG);

let cachedConfigSignature: string | null = null;
let cachedApp: FirebaseApp | null = null;
let cachedAuth: Auth | null = null;
let cachedDb: Firestore | null = null;
let cachedGcalApp: FirebaseApp | null = null;
let cachedGcalAuth: Auth | null = null;

export function invalidateFirebaseClientCache(): void {
  const prevApp = cachedApp;
  const prevGcalApp = cachedGcalApp;
  cachedConfigSignature = null;
  cachedApp = null;
  cachedAuth = null;
  cachedDb = null;
  cachedGcalApp = null;
  cachedGcalAuth = null;

  if (prevApp && prevApp.name !== '[DEFAULT]') {
    void deleteApp(prevApp).catch(() => {});
  }
  if (prevGcalApp && prevGcalApp.name !== 'thingsbook-gcal-oauth') {
    void deleteApp(prevGcalApp).catch(() => {});
  }
}

function ensureConfigFresh(): { opts: FirebaseOptions; isDefault: boolean; sigHash: string } {
  const opts = getActiveFirebaseOptions();
  const sig = computeConfigSignature(opts);
  if (cachedConfigSignature !== null && cachedConfigSignature !== sig) {
    invalidateFirebaseClientCache();
  }
  cachedConfigSignature = sig;
  return {
    opts,
    isDefault: sig === DEFAULT_CONFIG_SIGNATURE,
    sigHash: hashSignatureShort(sig)
  };
}

export function getFirebaseAppInstance(): FirebaseApp {
  const { opts, isDefault, sigHash } = ensureConfigFresh();
  if (cachedApp) return cachedApp;

  const targetName = isDefault ? '[DEFAULT]' : `thingsbook-custom-${sigHash}`;
  const existing = getApps().find((a) => a.name === targetName);
  if (existing) {
    cachedApp = existing;
    return cachedApp;
  }
  cachedApp = isDefault ? initializeApp(opts) : initializeApp(opts, targetName);
  return cachedApp;
}

export function getFirebaseAuthInstance(): Auth {
  ensureConfigFresh();
  if (!cachedAuth) {
    cachedAuth = getAuth(getFirebaseAppInstance());
  }
  return cachedAuth;
}

export function getFirestoreInstance(): Firestore {
  ensureConfigFresh();
  if (!cachedDb) {
    cachedDb = getFirestore(getFirebaseAppInstance());
  }
  return cachedDb;
}

export function getGoogleCalendarAuthInstance(): Auth {
  const { opts, isDefault, sigHash } = ensureConfigFresh();
  if (cachedGcalAuth) return cachedGcalAuth;

  getFirebaseAppInstance();
  const appName = isDefault ? 'thingsbook-gcal-oauth' : `thingsbook-gcal-oauth-${sigHash}`;
  const existing = getApps().find((a) => a.name === appName);
  cachedGcalApp = existing ?? initializeApp(opts, appName);
  cachedGcalAuth = getAuth(cachedGcalApp);
  return cachedGcalAuth;
}
