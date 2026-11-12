import { StrictMode } from 'react';
import { createRoot } from 'react-dom/client';
import { initSafeServiceWorker } from './data/pwaUpdater';
import './index.css';
import App from './App.tsx';

// Register offline-first Service Worker safely (preview-iframe & ChromeOS safe)
initSafeServiceWorker();

createRoot(document.getElementById('root')!).render(
  <StrictMode>
    <App />
  </StrictMode>
);
