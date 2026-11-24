import { StrictMode } from 'react';
import { createRoot } from 'react-dom/client';
import { MacosGoogleAuthBridge } from './components/MacosGoogleAuthBridge';
import { initSafeServiceWorker } from './data/pwaUpdater';
import './index.css';
import App from './App.tsx';

const searchParams = new URLSearchParams(window.location.search);
const isMacosAuthBridge = searchParams.get('macos_google_auth') === '1';
const isAndroidAuthBridge = searchParams.get('android_google_auth') === '1';
const bridgePort = Number.parseInt(searchParams.get('port') ?? '', 10);
const bridgeState = searchParams.get('state') ?? '';

const validMacosBridge =
  isMacosAuthBridge && Number.isInteger(bridgePort) && bridgePort >= 1024 && bridgePort <= 65535 && Boolean(bridgeState);
const validAndroidBridge = isAndroidAuthBridge && Boolean(bridgeState);

if (!validMacosBridge && !validAndroidBridge) {
  // Register offline-first Service Worker safely (preview-iframe & ChromeOS safe)
  initSafeServiceWorker();
}

createRoot(document.getElementById('root')!).render(
  <StrictMode>
    {validMacosBridge ? (
      <MacosGoogleAuthBridge platform="macos" port={bridgePort} state={bridgeState} />
    ) : validAndroidBridge ? (
      <MacosGoogleAuthBridge platform="android" state={bridgeState} />
    ) : (
      <App />
    )}
  </StrictMode>
);
