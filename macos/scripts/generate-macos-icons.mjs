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

// Thingsbook 3.0 — Icon B ("Glacier Liquid Glass Book")
// 1024x1024 Apple HIG canvas with 824x824 continuous-curvature squircle (100px optical margin),
// Glacier Sky Light Blue vertical gradient, top glass dome reflection, subtle crystalline caustics,
// double-layered sculpted frosted-crystal open book with warm golden-ivory spine glow, and crisp specular rim.
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
      <stop offset="0%" stop-color="#FFFFFF" stop-opacity="0.96"/>
      <stop offset="66%" stop-color="#DCF0FF" stop-opacity="0.88"/>
      <stop offset="100%" stop-color="#FFEAD0" stop-opacity="0.92"/>
    </linearGradient>
    <linearGradient id="rightPageFront" x1="85%" y1="10%" x2="5%" y2="92%">
      <stop offset="0%" stop-color="#FFFFFF" stop-opacity="0.96"/>
      <stop offset="66%" stop-color="#DCF0FF" stop-opacity="0.88"/>
      <stop offset="100%" stop-color="#FFEAD0" stop-opacity="0.92"/>
    </linearGradient>
    <radialGradient id="spineWarmth" cx="50%" cy="62%" r="22%">
      <stop offset="0%" stop-color="#FFE3B3" stop-opacity="0.72"/>
      <stop offset="100%" stop-color="#FFE3B3" stop-opacity="0.0"/>
    </radialGradient>
    <filter id="dropShadow" x="-8%" y="-6%" width="116%" height="118%">
      <feDropShadow dx="0" dy="18" stdDeviation="20" flood-color="#0B3B68" flood-opacity="0.28"/>
    </filter>
    <filter id="bookShadow" x="-12%" y="-10%" width="124%" height="128%">
      <feDropShadow dx="0" dy="16" stdDeviation="18" flood-color="#0B477D" flood-opacity="0.24"/>
    </filter>
    <clipPath id="higSquircle">
      <rect x="100" y="100" width="824" height="824" rx="185.4" ry="185.4"/>
    </clipPath>
  </defs>

  <!-- Outer macOS Dock Shadow + Glacier Squircle Body -->
  <g filter="url(#dropShadow)">
    <rect x="100" y="100" width="824" height="824" rx="185.4" ry="185.4" fill="url(#glacierBg)"/>
  </g>

  <g clip-path="url(#higSquircle)">
    <!-- Liquid Glass Top Dome Specular Reflection -->
    <path d="M100,100 L924,100 L924,430 C720,515 304,515 100,430 Z" fill="url(#glassDome)"/>

    <!-- Crystalline Caustic Light Shimmer in Lower Basin -->
    <path d="M130,740 C285,650 420,690 512,775 C604,690 739,650 894,740" fill="none" stroke="#E4F6FF" stroke-opacity="0.30" stroke-width="14" stroke-linecap="round"/>
    <path d="M235,885 C345,760 445,735 512,702 C579,735 679,760 789,885" fill="none" stroke="#FFFFFF" stroke-opacity="0.24" stroke-width="11" stroke-linecap="round"/>
    <path d="M350,915 C415,820 470,785 512,742 C554,785 609,820 674,915" fill="none" stroke="#DDF3FF" stroke-opacity="0.28" stroke-width="8" stroke-linecap="round"/>

    <!-- Warm Golden-Ivory Spine Illumination -->
    <circle cx="512" cy="590" r="195" fill="url(#spineWarmth)"/>

    <!-- Sculpted Frosted-Crystal Open Book -->
    <g filter="url(#bookShadow)">
      <!-- Back Translucent Book Leaves -->
      <path d="M294,376 C294,363 306,354 319,357 C402,377 466,421 505,479 L505,716 C462,666 396,632 314,620 C302,618 294,608 294,596 Z"
            fill="#DFF2FF" fill-opacity="0.62" stroke="#FFFFFF" stroke-opacity="0.72" stroke-width="5"/>
      <path d="M730,376 C730,363 718,354 705,357 C622,377 558,421 519,479 L519,716 C562,666 628,632 710,620 C722,618 730,608 730,596 Z"
            fill="#DFF2FF" fill-opacity="0.62" stroke="#FFFFFF" stroke-opacity="0.72" stroke-width="5"/>

      <!-- Front Frosted-Glass Book Pages -->
      <path d="M328,342 C328,329 341,320 353,326 C426,357 479,406 506,468 L506,716 C474,655 419,608 345,580 C334,576 328,566 328,554 Z"
            fill="url(#leftPageFront)" stroke="#FFFFFF" stroke-opacity="0.94" stroke-width="6.5"/>
      <path d="M696,342 C696,329 683,320 671,326 C598,357 545,406 518,468 L518,716 C550,655 605,608 679,580 C690,576 696,566 696,554 Z"
            fill="url(#rightPageFront)" stroke="#FFFFFF" stroke-opacity="0.94" stroke-width="6.5"/>
    </g>
  </g>

  <!-- Outer & Inner Liquid Glass Specular Rim Highlights -->
  <rect x="103" y="103" width="818" height="818" rx="182.4" ry="182.4" fill="none" stroke="#7AC5FF" stroke-opacity="0.55" stroke-width="5"/>
  <rect x="116" y="116" width="792" height="792" rx="172" ry="172" fill="none" stroke="url(#glassRim)" stroke-width="6.5"/>
</svg>`;

// Full-bleed variant (no 100px macOS Dock margin) for ChromeOS PWA / Maskable / Favicon
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
      <stop offset="0%" stop-color="#FFFFFF" stop-opacity="0.96"/>
      <stop offset="68%" stop-color="#DCF0FF" stop-opacity="0.88"/>
      <stop offset="100%" stop-color="#FFEAD0" stop-opacity="0.92"/>
    </linearGradient>
    <linearGradient id="pwaRight" x1="85%" y1="10%" x2="5%" y2="92%">
      <stop offset="0%" stop-color="#FFFFFF" stop-opacity="0.96"/>
      <stop offset="68%" stop-color="#DCF0FF" stop-opacity="0.88"/>
      <stop offset="100%" stop-color="#FFEAD0" stop-opacity="0.92"/>
    </linearGradient>
    <clipPath id="pwaSquircle">
      <rect x="16" y="16" width="480" height="480" rx="112" ry="112"/>
    </clipPath>
  </defs>
  <rect x="16" y="16" width="480" height="480" rx="112" ry="112" fill="url(#pwaBg)"/>
  <g clip-path="url(#pwaSquircle)">
    <path d="M16,16 L496,16 L496,210 C380,260 132,260 16,210 Z" fill="url(#pwaDome)"/>
    <path d="M36,392 C125,338 205,360 256,410 C307,360 387,338 476,392" fill="none" stroke="#E4F6FF" stroke-opacity="0.28" stroke-width="8" stroke-linecap="round"/>
    <path d="M126,178 C126,170 133,165 141,167 C190,179 228,205 251,239 L251,378 C226,348 187,328 138,321 C131,320 126,314 126,307 Z"
          fill="#DFF2FF" fill-opacity="0.62" stroke="#FFFFFF" stroke-opacity="0.72" stroke-width="3"/>
    <path d="M386,178 C386,170 379,165 371,167 C322,179 284,205 261,239 L261,378 C286,348 325,328 374,321 C381,320 386,314 386,307 Z"
          fill="#DFF2FF" fill-opacity="0.62" stroke="#FFFFFF" stroke-opacity="0.72" stroke-width="3"/>
    <path d="M146,158 C146,150 154,145 161,148 C204,167 236,196 252,232 L252,378 C233,342 200,314 156,298 C150,295 146,289 146,282 Z"
          fill="url(#pwaLeft)" stroke="#FFFFFF" stroke-opacity="0.94" stroke-width="4"/>
    <path d="M366,158 C366,150 358,145 351,148 C308,167 276,196 260,232 L260,378 C279,342 312,314 356,298 C362,295 366,289 366,282 Z"
          fill="url(#pwaRight)" stroke="#FFFFFF" stroke-opacity="0.94" stroke-width="4"/>
  </g>
  <rect x="25" y="25" width="462" height="462" rx="104" ry="104" fill="none" stroke="url(#pwaRim)" stroke-width="4"/>
</svg>`;

const pwaMaskableSvg = pwaFullBleedSvg
  .replace('<rect x="16" y="16" width="480" height="480" rx="112" ry="112" fill="url(#pwaBg)"/>', '<rect x="0" y="0" width="512" height="512" fill="url(#pwaBg)"/>')
  .replace('<rect x="16" y="16" width="480" height="480" rx="112" ry="112"/>', '<rect x="0" y="0" width="512" height="512"/>')
  .replace('<rect x="25" y="25" width="462" height="462" rx="104" ry="104" fill="none" stroke="url(#pwaRim)" stroke-width="4"/>', '');

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

console.log('Generated Icon B ("Glacier Liquid Glass Book") across macOS AppIcon.iconset & ChromeOS PWA public icons.');
