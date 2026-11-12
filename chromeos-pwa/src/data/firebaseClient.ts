import { initializeApp, getApps, type FirebaseApp, type FirebaseOptions } from 'firebase/app';
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
  } catch {
    // fallback to default
  }
  return DEFAULT_FIREBASE_CONFIG;
}

let cachedApp: FirebaseApp | null = null;
let cachedAuth: Auth | null = null;
let cachedDb: Firestore | null = null;

export function getFirebaseAppInstance(): FirebaseApp {
  if (cachedApp) return cachedApp;
  const existing = getApps();
  if (existing.length > 0) {
    cachedApp = existing[0];
    return cachedApp;
  }
  cachedApp = initializeApp(getActiveFirebaseOptions());
  return cachedApp;
}

export function getFirebaseAuthInstance(): Auth {
  if (!cachedAuth) {
    cachedAuth = getAuth(getFirebaseAppInstance());
  }
  return cachedAuth;
}

export function getFirestoreInstance(): Firestore {
  if (!cachedDb) {
    cachedDb = getFirestore(getFirebaseAppInstance());
  }
  return cachedDb;
}
