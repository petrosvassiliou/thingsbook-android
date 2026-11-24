import React, { useState } from 'react';
import { getApps, initializeApp } from 'firebase/app';
import {
  GoogleAuthProvider,
  getAuth,
  inMemoryPersistence,
  setPersistence,
  signInWithPopup
} from 'firebase/auth';
import { CheckSquare, Globe } from 'lucide-react';
import { getActiveFirebaseOptions } from '../data/firebaseClient';
import { TRANSLATIONS, resolveLanguage } from '../i18n/translations';

interface Props {
  platform?: 'macos' | 'android';
  port?: number;
  state: string;
}

export const MacosGoogleAuthBridge: React.FC<Props> = ({ platform = 'macos', port, state }) => {
  const lang = resolveLanguage('system');
  const t = TRANSLATIONS[lang];
  const [busy, setBusy] = useState(false);
  const [done, setDone] = useState(false);
  const [callbackHref, setCallbackHref] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);

  const handleContinueWithGoogle = async () => {
    setBusy(true);
    setError(null);
    try {
      const appName = 'thingsbook-macos-oauth-bridge';
      const existing = getApps().find((a) => a.name === appName);
      const bridgeApp = existing ?? initializeApp(getActiveFirebaseOptions(), appName);
      const auth = getAuth(bridgeApp);
      await setPersistence(auth, inMemoryPersistence);

      const provider = new GoogleAuthProvider();
      provider.setCustomParameters({ prompt: 'select_account' });

      const result = await signInWithPopup(auth, provider);
      const credential = GoogleAuthProvider.credentialFromResult(result);
      const idToken = credential?.idToken ?? '';
      const accessToken = credential?.accessToken ?? '';
      const email = result.user?.email ?? '';

      if (!idToken && !accessToken) {
        throw new Error('Google Sign-In did not return an OAuth token.');
      }

      let targetHref: string;
      if (platform === 'android') {
        const params = new URLSearchParams();
        params.set('state', state);
        if (idToken) params.set('id_token', idToken);
        if (accessToken) params.set('access_token', accessToken);
        if (email) params.set('email', email);
        targetHref = `thingsbook://oauth-callback?${params.toString()}`;
      } else {
        const callbackUrl = new URL(`http://127.0.0.1:${port}/callback`);
        callbackUrl.searchParams.set('state', state);
        if (idToken) callbackUrl.searchParams.set('id_token', idToken);
        if (accessToken) callbackUrl.searchParams.set('access_token', accessToken);
        if (email) callbackUrl.searchParams.set('email', email);
        targetHref = callbackUrl.toString();
      }

      setCallbackHref(targetHref);
      setDone(true);
      window.location.href = targetHref;
    } catch (err) {
      const msg = err instanceof Error ? err.message : String(err);
      setError(msg);
      setBusy(false);
    }
  };

  return (
    <div
      style={{
        minHeight: '100vh',
        display: 'flex',
        alignItems: 'center',
        justifyContent: 'center',
        background: 'var(--bg-canvas, #f5f6f8)',
        padding: 24,
        fontFamily: '-apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, sans-serif'
      }}
    >
      <div
        style={{
          width: '100%',
          maxWidth: 420,
          background: 'var(--bg-card, #ffffff)',
          borderRadius: 18,
          padding: 32,
          boxShadow: '0 18px 48px rgba(15, 23, 42, 0.12)',
          border: '1px solid rgba(15, 23, 42, 0.08)',
          textAlign: 'center'
        }}
      >
        <div
          style={{
            width: 56,
            height: 56,
            borderRadius: 14,
            background: 'linear-gradient(145deg, #2b84ea, #1a67c9)',
            color: '#fff',
            display: 'inline-flex',
            alignItems: 'center',
            justifyContent: 'center',
            marginBottom: 16,
            boxShadow: '0 6px 18px rgba(43, 132, 234, 0.3)'
          }}
        >
          <CheckSquare size={28} strokeWidth={2.5} />
        </div>

        <h1 style={{ margin: '0 0 8px', fontSize: 21, fontWeight: 700, color: '#111827' }}>
          {t.syncTitle} — {platform === 'android' ? 'Android' : 'macOS'}
        </h1>
        <p style={{ margin: '0 0 24px', fontSize: 13.5, lineHeight: 1.5, color: '#6b7280' }}>
          {t.authMultiDeviceHint}
        </p>

        {done ? (
          <div style={{ display: 'flex', flexDirection: 'column', gap: 10 }}>
            <div
              style={{
                padding: '12px 16px',
                borderRadius: 11,
                background: 'rgba(16, 185, 129, 0.12)',
                color: '#047857',
                fontSize: 13.5,
                fontWeight: 600
              }}
            >
              ✓ {t.syncStatusSynced}
            </div>
            {platform === 'android' && callbackHref && (
              <a
                href={callbackHref}
                style={{
                  display: 'inline-flex',
                  alignItems: 'center',
                  justifyContent: 'center',
                  padding: '11px 16px',
                  borderRadius: 11,
                  background: '#1c7ed6',
                  color: '#ffffff',
                  fontSize: 14,
                  fontWeight: 600,
                  textDecoration: 'none'
                }}
              >
                Thingsbook ↗
              </a>
            )}
          </div>
        ) : (
          <button
            type="button"
            disabled={busy}
            onClick={() => void handleContinueWithGoogle()}
            style={{
              width: '100%',
              display: 'inline-flex',
              alignItems: 'center',
              justifyContent: 'center',
              gap: 10,
              padding: '12px 18px',
              borderRadius: 11,
              border: 'none',
              background: '#1c7ed6',
              color: '#ffffff',
              fontSize: 14.5,
              fontWeight: 600,
              cursor: busy ? 'wait' : 'pointer',
              opacity: busy ? 0.75 : 1
            }}
          >
            <Globe size={17} />
            <span>{busy ? t.syncStatusSyncing : t.authSignInGoogle}</span>
          </button>
        )}

        {error && (
          <p
            style={{
              marginTop: 14,
              marginBottom: 0,
              fontSize: 12.5,
              color: '#dc2626',
              wordBreak: 'break-word'
            }}
          >
            {error}
          </p>
        )}
      </div>
    </div>
  );
};
