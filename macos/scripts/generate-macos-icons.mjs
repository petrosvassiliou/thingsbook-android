import fs from 'node:fs/promises';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import { createRequire } from 'node:module';

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const repoRoot = path.resolve(__dirname, '..', '..');
const requireFromPwa = createRequire(path.join(repoRoot, 'chromeos-pwa', 'package.json'));
const sharp = requireFromPwa('sharp');

const assetsDir = path.join(repoRoot, 'macos', 'Resources', 'Assets');
const coreResourcesDir = path.join(repoRoot, 'macos', 'Sources', 'ThingsbookCore', 'Resources');
const iconsetDir = path.join(assetsDir, 'AppIcon.iconset');
const pwaPublicDir = path.join(repoRoot, 'chromeos-pwa', 'public');

// Thingsbook 3.0 — Icon B ("Glacier Liquid Glass Book", v4 Full-Presence Sizing)
// 1024x1024 Apple macOS Sequoia/Tahoe canvas with 864x864 continuous-curvature squircle (80px shadow margin),
// matching native macOS 26 Tahoe Dock icon footprint (84.4% fill) and bold 532px sculpted crystal open book.
const macAppIconSvg = `<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 1024 1024" width="1024" height="1024">
  <defs>
    <linearGradient id="glacierBg" x1="50%" y1="0%" x2="50%" y2="100%">
      <stop offset="0%" stop-color="#CBEBFF"/>
      <stop offset="36%" stop-color="#75C4FF"/>
      <stop offset="72%" stop-color="#3898EC"/>
      <stop offset="100%" stop-color="#1C74C7"/>
    </linearGradient>
    <linearGradient id="glassDome" x1="50%" y1="0%" x2="50%" y2="100%">
      <stop offset="0%" stop-color="#FFFFFF" stop-opacity="0.56"/>
      <stop offset="60%" stop-color="#FFFFFF" stop-opacity="0.16"/>
      <stop offset="100%" stop-color="#FFFFFF" stop-opacity="0.0"/>
    </linearGradient>
    <linearGradient id="glassRim" x1="50%" y1="0%" x2="50%" y2="100%">
      <stop offset="0%" stop-color="#FFFFFF" stop-opacity="0.92"/>
      <stop offset="45%" stop-color="#E4F5FF" stop-opacity="0.58"/>
      <stop offset="100%" stop-color="#B9E3FF" stop-opacity="0.86"/>
    </linearGradient>
    <linearGradient id="leftPageFront" x1="15%" y1="10%" x2="95%" y2="92%">
      <stop offset="0%" stop-color="#FFFFFF" stop-opacity="0.97"/>
      <stop offset="66%" stop-color="#DCF0FF" stop-opacity="0.90"/>
      <stop offset="100%" stop-color="#FFEAD0" stop-opacity="0.94"/>
    </linearGradient>
    <linearGradient id="rightPageFront" x1="85%" y1="10%" x2="5%" y2="92%">
      <stop offset="0%" stop-color="#FFFFFF" stop-opacity="0.97"/>
      <stop offset="66%" stop-color="#DCF0FF" stop-opacity="0.90"/>
      <stop offset="100%" stop-color="#FFEAD0" stop-opacity="0.94"/>
    </linearGradient>
    <radialGradient id="spineWarmth" cx="50%" cy="62%" r="25%">
      <stop offset="0%" stop-color="#FFE3B3" stop-opacity="0.74"/>
      <stop offset="100%" stop-color="#FFE3B3" stop-opacity="0.0"/>
    </radialGradient>
    <filter id="dropShadow" x="-8%" y="-6%" width="116%" height="118%">
      <feDropShadow dx="0" dy="16" stdDeviation="18" flood-color="#0B3B68" flood-opacity="0.28"/>
    </filter>
    <filter id="bookShadow" x="-12%" y="-10%" width="124%" height="128%">
      <feDropShadow dx="0" dy="16" stdDeviation="18" flood-color="#0B477D" flood-opacity="0.25"/>
    </filter>
    <clipPath id="higSquircle">
      <rect x="80" y="80" width="864" height="864" rx="194.4" ry="194.4"/>
    </clipPath>
  </defs>

  <!-- Outer macOS Dock Shadow + Glacier 864x864 Squircle Body -->
  <g filter="url(#dropShadow)">
    <rect x="80" y="80" width="864" height="864" rx="194.4" ry="194.4" fill="url(#glacierBg)"/>
  </g>

  <g clip-path="url(#higSquircle)">
    <!-- Liquid Glass Top Dome Specular Reflection -->
    <path d="M80,80 L944,80 L944,426 C730,516 294,516 80,426 Z" fill="url(#glassDome)"/>

    <!-- Crystalline Caustic Light Shimmer in Lower Basin -->
    <path d="M112,756 C276,660 416,702 512,792 C608,702 748,660 912,756" fill="none" stroke="#E4F6FF" stroke-opacity="0.30" stroke-width="14.5" stroke-linecap="round"/>
    <path d="M222,904 C338,772 442,746 512,712 C582,746 686,772 802,904" fill="none" stroke="#FFFFFF" stroke-opacity="0.24" stroke-width="11.5" stroke-linecap="round"/>
    <path d="M342,936 C410,836 468,798 512,754 C556,798 614,836 682,936" fill="none" stroke="#DDF3FF" stroke-opacity="0.28" stroke-width="8.5" stroke-linecap="round"/>

    <!-- Warm Golden-Ivory Spine Illumination -->
    <circle cx="512" cy="596" r="220" fill="url(#spineWarmth)"/>

    <!-- Bold Sculpted Frosted-Crystal Open Book (532px span, centered optically) -->
    <g filter="url(#bookShadow)" transform="translate(512 522) scale(1.20) translate(-512 -522)">
      <!-- Back Translucent Book Leaves -->
      <path d="M294,376 C294,363 306,354 319,357 C402,377 466,421 505,479 L505,716 C462,666 396,632 314,620 C302,618 294,608 294,596 Z"
            fill="#DFF2FF" fill-opacity="0.64" stroke="#FFFFFF" stroke-opacity="0.74" stroke-width="5"/>
      <path d="M730,376 C730,363 718,354 705,357 C622,377 558,421 519,479 L519,716 C562,666 628,632 710,620 C722,618 730,608 730,596 Z"
            fill="#DFF2FF" fill-opacity="0.64" stroke="#FFFFFF" stroke-opacity="0.74" stroke-width="5"/>

      <!-- Front Frosted-Glass Book Pages -->
      <path d="M328,342 C328,329 341,320 353,326 C426,357 479,406 506,468 L506,716 C474,655 419,608 345,580 C334,576 328,566 328,554 Z"
            fill="url(#leftPageFront)" stroke="#FFFFFF" stroke-opacity="0.95" stroke-width="6.5"/>
      <path d="M696,342 C696,329 683,320 671,326 C598,357 545,406 518,468 L518,716 C550,655 605,608 679,580 C690,576 696,566 696,554 Z"
            fill="url(#rightPageFront)" stroke="#FFFFFF" stroke-opacity="0.95" stroke-width="6.5"/>
    </g>
  </g>

  <!-- Outer & Inner Liquid Glass Specular Rim Highlights -->
  <rect x="83" y="83" width="858" height="858" rx="191.4" ry="191.4" fill="none" stroke="#7AC5FF" stroke-opacity="0.55" stroke-width="5"/>
  <rect x="96" y="96" width="832" height="832" rx="181" ry="181" fill="none" stroke="url(#glassRim)" stroke-width="6.5"/>
</svg>`;

// Full-presence ChromeOS PWA / Favicon / Touch Icon (504x504 on 512x512 = 98.4% canvas fill)
const pwaFullBleedSvg = `<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 512 512" width="512" height="512">
  <defs>
    <linearGradient id="pwaBg" x1="50%" y1="0%" x2="50%" y2="100%">
      <stop offset="0%" stop-color="#CBEBFF"/>
      <stop offset="36%" stop-color="#75C4FF"/>
      <stop offset="72%" stop-color="#3898EC"/>
      <stop offset="100%" stop-color="#1C74C7"/>
    </linearGradient>
    <linearGradient id="pwaDome" x1="50%" y1="0%" x2="50%" y2="100%">
      <stop offset="0%" stop-color="#FFFFFF" stop-opacity="0.52"/>
      <stop offset="65%" stop-color="#FFFFFF" stop-opacity="0.14"/>
      <stop offset="100%" stop-color="#FFFFFF" stop-opacity="0.0"/>
    </linearGradient>
    <linearGradient id="pwaRim" x1="50%" y1="0%" x2="50%" y2="100%">
      <stop offset="0%" stop-color="#FFFFFF" stop-opacity="0.90"/>
      <stop offset="50%" stop-color="#E4F5FF" stop-opacity="0.55"/>
      <stop offset="100%" stop-color="#B9E3FF" stop-opacity="0.82"/>
    </linearGradient>
    <linearGradient id="pwaLeft" x1="15%" y1="10%" x2="95%" y2="92%">
      <stop offset="0%" stop-color="#FFFFFF" stop-opacity="0.97"/>
      <stop offset="68%" stop-color="#DCF0FF" stop-opacity="0.90"/>
      <stop offset="100%" stop-color="#FFEAD0" stop-opacity="0.94"/>
    </linearGradient>
    <linearGradient id="pwaRight" x1="85%" y1="10%" x2="5%" y2="92%">
      <stop offset="0%" stop-color="#FFFFFF" stop-opacity="0.97"/>
      <stop offset="68%" stop-color="#DCF0FF" stop-opacity="0.90"/>
      <stop offset="100%" stop-color="#FFEAD0" stop-opacity="0.94"/>
    </linearGradient>
    <clipPath id="pwaSquircle">
      <rect x="4" y="4" width="504" height="504" rx="114" ry="114"/>
    </clipPath>
  </defs>
  <rect x="4" y="4" width="504" height="504" rx="114" ry="114" fill="url(#pwaBg)"/>
  <g clip-path="url(#pwaSquircle)">
    <path d="M4,4 L508,4 L508,214 C386,266 126,266 4,214 Z" fill="url(#pwaDome)"/>
    <path d="M24,404 C120,346 202,370 256,422 C310,370 392,346 488,404" fill="none" stroke="#E4F6FF" stroke-opacity="0.28" stroke-width="8.5" stroke-linecap="round"/>
    <g transform="translate(256 262) scale(1.20) translate(-256 -262)">
      <path d="M126,178 C126,170 133,165 141,167 C190,179 228,205 251,239 L251,378 C226,348 187,328 138,321 C131,320 126,314 126,307 Z"
            fill="#DFF2FF" fill-opacity="0.64" stroke="#FFFFFF" stroke-opacity="0.74" stroke-width="3"/>
      <path d="M386,178 C386,170 379,165 371,167 C322,179 284,205 261,239 L261,378 C286,348 325,328 374,321 C381,320 386,314 386,307 Z"
            fill="#DFF2FF" fill-opacity="0.64" stroke="#FFFFFF" stroke-opacity="0.74" stroke-width="3"/>
      <path d="M146,158 C146,150 154,145 161,148 C204,167 236,196 252,232 L252,378 C233,342 200,314 156,298 C150,295 146,289 146,282 Z"
            fill="url(#pwaLeft)" stroke="#FFFFFF" stroke-opacity="0.95" stroke-width="4"/>
      <path d="M366,158 C366,150 358,145 351,148 C308,167 276,196 260,232 L260,378 C279,342 312,314 356,298 C362,295 366,289 366,282 Z"
            fill="url(#pwaRight)" stroke="#FFFFFF" stroke-opacity="0.95" stroke-width="4"/>
    </g>
  </g>
  <rect x="13" y="13" width="486" height="486" rx="106" ry="106" fill="none" stroke="url(#pwaRim)" stroke-width="4"/>
</svg>`;

// W3C Maskable 512x512 variant (100% full-bleed background + book scaled cleanly inside 80% safe circle)
const pwaMaskableSvg = pwaFullBleedSvg
  .replace('<rect x="4" y="4" width="504" height="504" rx="114" ry="114" fill="url(#pwaBg)"/>', '<rect x="0" y="0" width="512" height="512" fill="url(#pwaBg)"/>')
  .replace('<rect x="4" y="4" width="504" height="504" rx="114" ry="114"/>', '<rect x="0" y="0" width="512" height="512"/>')
  .replace('scale(1.20)', 'scale(1.12)')
  .replace('<rect x="13" y="13" width="486" height="486" rx="106" ry="106" fill="none" stroke="url(#pwaRim)" stroke-width="4"/>', '');

// Monochrome black + alpha template open-book icon for macOS MenuBarExtra
const menuBarSvg = `<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 36 36" width="36" height="36">
  <path d="M6.5,10.5 C6.5,9.4 7.5,8.6 8.6,8.9 C12.8,10.1 16.0,12.3 18,15.2 C20.0,12.3 23.2,10.1 27.4,8.9 C28.5,8.6 29.5,9.4 29.5,10.5 L29.5,24.2 C29.5,25.2 28.7,26.0 27.7,26.2 C23.6,26.9 20.3,28.6 18,31.0 C15.7,28.6 12.4,26.9 8.3,26.2 C7.3,26.0 6.5,25.2 6.5,24.2 Z"
        fill="none" stroke="#000000" stroke-width="2.5" stroke-linejoin="round"/>
  <line x1="18" y1="15.2" x2="18" y2="30.5" stroke="#000000" stroke-width="2.3" stroke-linecap="round"/>
</svg>`;

const iconSizes = [
  { name: 'icon_16x16.png', px: 16 },
  { name: 'icon_16x16@2x.png', px: 32 },
  { name: 'icon_32x32.png', px: 32 },
  { name: 'icon_32x32@2x.png', px: 64 },
  { name: 'icon_128x128.png', px: 128 },
  { name: 'icon_128x128@2x.png', px: 256 },
  { name: 'icon_256x256.png', px: 256 },
  { name: 'icon_256x256@2x.png', px: 512 },
  { name: 'icon_512x512.png', px: 512 },
  { name: 'icon_512x512@2x.png', px: 1024 }
];

await fs.mkdir(iconsetDir, { recursive: true });
await fs.mkdir(coreResourcesDir, { recursive: true });
await fs.writeFile(path.join(assetsDir, 'AppIcon.svg'), macAppIconSvg, 'utf8');
await sharp(Buffer.from(macAppIconSvg)).resize(1024, 1024).png().toFile(path.join(assetsDir, 'AppIcon-1024.png'));
await sharp(Buffer.from(macAppIconSvg)).resize(1024, 1024).png().toFile(path.join(coreResourcesDir, 'AppIcon-1024.png'));

for (const entry of iconSizes) {
  await sharp(Buffer.from(macAppIconSvg))
    .resize(entry.px, entry.px)
    .png()
    .toFile(path.join(iconsetDir, entry.name));
}

await sharp(Buffer.from(menuBarSvg)).resize(18, 18).png().toFile(path.join(assetsDir, 'MenuBarIconTemplate.png'));
await sharp(Buffer.from(menuBarSvg)).resize(36, 36).png().toFile(path.join(assetsDir, 'MenuBarIconTemplate@2x.png'));
await sharp(Buffer.from(menuBarSvg)).resize(18, 18).png().toFile(path.join(coreResourcesDir, 'MenuBarIconTemplate.png'));
await sharp(Buffer.from(menuBarSvg)).resize(36, 36).png().toFile(path.join(coreResourcesDir, 'MenuBarIconTemplate@2x.png'));

// Generate ChromeOS PWA Icon B set
await fs.writeFile(path.join(pwaPublicDir, 'favicon.svg'), pwaFullBleedSvg, 'utf8');
await sharp(Buffer.from(pwaFullBleedSvg)).resize(180, 180).png().toFile(path.join(pwaPublicDir, 'apple-touch-icon.png'));
await sharp(Buffer.from(pwaFullBleedSvg)).resize(192, 192).png().toFile(path.join(pwaPublicDir, 'pwa-192x192.png'));
await sharp(Buffer.from(pwaFullBleedSvg)).resize(512, 512).png().toFile(path.join(pwaPublicDir, 'pwa-512x512.png'));
await sharp(Buffer.from(pwaMaskableSvg)).resize(512, 512).png().toFile(path.join(pwaPublicDir, 'maskable-512x512.png'));

console.log('Generated Icon B v4 ("Glacier Liquid Glass Book") across macOS AppIcon.iconset & ChromeOS PWA public icons.');
