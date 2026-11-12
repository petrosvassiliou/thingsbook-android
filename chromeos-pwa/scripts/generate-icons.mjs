import sharp from 'sharp';
import fs from 'node:fs/promises';
import path from 'node:path';

const publicDir = path.resolve('public');

// Standard rounded-square ChromeOS / Desktop PWA icon (transparent corners outside squircle so ChromeOS Launcher & Shelf look ultra-crisp)
const standardSvg = `<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 108 108" width="512" height="512">
  <defs>
    <linearGradient id="bgGrad" x1="0%" y1="0%" x2="100%" y2="100%">
      <stop offset="0%" stop-color="#2B7FFF"/>
      <stop offset="52%" stop-color="#165DFC"/>
      <stop offset="100%" stop-color="#0B358E"/>
    </linearGradient>
    <clipPath id="squircle">
      <rect x="4" y="4" width="100" height="100" rx="24" ry="24"/>
    </clipPath>
  </defs>
  <g clip-path="url(#squircle)">
    <rect x="0" y="0" width="108" height="108" fill="url(#bgGrad)"/>
    <circle cx="38" cy="38" r="48" fill="#FFFFFF" fill-opacity="0.16"/>
    <!-- Subtle Card Drop Shadow -->
    <path fill="#04153B" fill-opacity="0.24" d="M32,27 L76,27 A10,10 0 0,1 86,37 L86,79 A10,10 0 0,1 76,89 L32,89 A10,10 0 0,1 22,79 L22,37 A10,10 0 0,1 32,27 Z"/>
    <!-- Back Stacked Page -->
    <path fill="#DCEBFF" fill-opacity="0.84" d="M35,20 L73,20 A9,9 0 0,1 82,29 L82,72 A9,9 0 0,1 73,81 L35,81 A9,9 0 0,1 26,72 L26,29 A9,9 0 0,1 35,20 Z"/>
    <!-- Main Pure White Elevated GTD Card -->
    <path fill="#FFFFFF" d="M31,24 L77,24 A10,10 0 0,1 87,34 L87,76 A10,10 0 0,1 77,86 L31,86 A10,10 0 0,1 21,76 L21,34 A10,10 0 0,1 31,24 Z"/>
    <!-- Primary Completed Checkbox Circle -->
    <circle cx="44.5" cy="48" r="11.5" fill="#1B6EF3"/>
    <!-- Crisp Checkmark -->
    <path d="M39.3,48.2 L43.0,51.9 L50.4,44.0" fill="none" stroke="#FFFFFF" stroke-width="3.2" stroke-linecap="round" stroke-linejoin="round"/>
    <!-- Task Title Bar -->
    <rect x="59.5" y="42.8" width="20" height="4.8" rx="2.4" fill="#1C2536"/>
    <!-- Task Subtitle Pill -->
    <rect x="59.5" y="50.5" width="13.5" height="4.0" rx="2.0" fill="#7B8CA6"/>
    <!-- Secondary Checklist Row -->
    <circle cx="37.5" cy="68.5" r="4.0" fill="none" stroke="#8FA2BF" stroke-width="2.0"/>
    <rect x="45.5" y="66.5" width="27" height="4.0" rx="2.0" fill="#B0C0D8"/>
    <!-- Golden Today Star Badge -->
    <path fill="#FFC400" d="M77,19.5 L79.5,24.6 L85.1,25.4 L81.0,29.3 L82.0,34.9 L77,32.2 L72.0,34.9 L73.0,29.3 L68.9,25.4 L74.5,24.6 Z"/>
  </g>
</svg>`;

// Full-bleed Maskable icon for ChromeOS Adaptive Launcher Circles (keeps foreground inside safe 80% zone)
const maskableSvg = `<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 108 108" width="512" height="512">
  <defs>
    <linearGradient id="bgGradMask" x1="0%" y1="0%" x2="100%" y2="100%">
      <stop offset="0%" stop-color="#2B7FFF"/>
      <stop offset="52%" stop-color="#165DFC"/>
      <stop offset="100%" stop-color="#0B358E"/>
    </linearGradient>
  </defs>
  <rect x="0" y="0" width="108" height="108" fill="url(#bgGradMask)"/>
  <circle cx="34" cy="34" r="48" fill="#FFFFFF" fill-opacity="0.16"/>
  <path fill="#04153B" fill-opacity="0.22" d="M34,29 L74,29 A9,9 0 0,1 83,38 L83,76 A9,9 0 0,1 74,85 L34,85 A9,9 0 0,1 25,76 L25,38 A9,9 0 0,1 34,29 Z"/>
  <path fill="#DCEBFF" fill-opacity="0.82" d="M36,23 L72,23 A8,8 0 0,1 80,31 L80,71 A8,8 0 0,1 72,79 L36,79 A8,8 0 0,1 28,71 L28,31 A8,8 0 0,1 36,23 Z"/>
  <path fill="#FFFFFF" d="M33,26 L75,26 A9,9 0 0,1 84,35 L84,73 A9,9 0 0,1 75,82 L33,82 A9,9 0 0,1 24,73 L24,35 A9,9 0 0,1 33,26 Z"/>
  <circle cx="46" cy="48" r="10.5" fill="#1B6EF3"/>
  <path d="M41.2,48.2 L44.6,51.6 L51.4,44.4" fill="none" stroke="#FFFFFF" stroke-width="2.9" stroke-linecap="round" stroke-linejoin="round"/>
  <rect x="58.8" y="43.5" width="18.4" height="4.4" rx="2.2" fill="#1C2536"/>
  <rect x="59.2" y="50.5" width="12.6" height="3.6" rx="1.8" fill="#7B8CA6"/>
  <circle cx="39" cy="66.5" r="3.6" fill="none" stroke="#8FA2BF" stroke-width="1.8"/>
  <rect x="45.2" y="64.7" width="25.6" height="3.6" rx="1.8" fill="#B0C0D8"/>
  <path fill="#FFC400" d="M75,23 L77.2,27.6 L82.2,28.3 L78.6,31.8 L79.5,36.8 L75,34.4 L70.5,36.8 L71.4,31.8 L67.8,28.3 L72.8,27.6 Z"/>
</svg>`;

await fs.mkdir(publicDir, { recursive: true });
await fs.writeFile(path.join(publicDir, 'favicon.svg'), standardSvg, 'utf8');
await sharp(Buffer.from(standardSvg)).resize(192, 192).png().toFile(path.join(publicDir, 'pwa-192x192.png'));
await sharp(Buffer.from(standardSvg)).resize(512, 512).png().toFile(path.join(publicDir, 'pwa-512x512.png'));
await sharp(Buffer.from(standardSvg)).resize(180, 180).png().toFile(path.join(publicDir, 'apple-touch-icon.png'));
await sharp(Buffer.from(maskableSvg)).resize(512, 512).png().toFile(path.join(publicDir, 'maskable-512x512.png'));

console.log('Generated ChromeOS PWA icons successfully.');
