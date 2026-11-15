import fs from 'node:fs/promises';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import { createRequire } from 'node:module';

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const repoRoot = path.resolve(__dirname, '..', '..');
const requireFromPwa = createRequire(path.join(repoRoot, 'chromeos-pwa', 'package.json'));
const sharp = requireFromPwa('sharp');

const assetsDir = path.join(repoRoot, 'macos', 'Resources', 'Assets');
const iconsetDir = path.join(assetsDir, 'AppIcon.iconset');

// Apple macOS Human Interface Guidelines App Icon Grid:
// 1024x1024 canvas with 824x824 continuous-curvature squircle (100px optical margin),
// subtle top-edge Liquid Glass specular highlight, and multi-layered Thingsbook GTD card artwork.
const macAppIconSvg = `<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 1024 1024" width="1024" height="1024">
  <defs>
    <linearGradient id="macBg" x1="15%" y1="6%" x2="85%" y2="94%">
      <stop offset="0%" stop-color="#3898FF"/>
      <stop offset="48%" stop-color="#1967FA"/>
      <stop offset="100%" stop-color="#0B348B"/>
    </linearGradient>
    <linearGradient id="glassRim" x1="0%" y1="0%" x2="0%" y2="100%">
      <stop offset="0%" stop-color="#FFFFFF" stop-opacity="0.48"/>
      <stop offset="35%" stop-color="#FFFFFF" stop-opacity="0.08"/>
      <stop offset="100%" stop-color="#031235" stop-opacity="0.28"/>
    </linearGradient>
    <filter id="dropShadow" x="-8%" y="-6%" width="116%" height="118%">
      <feDropShadow dx="0" dy="18" stdDeviation="20" flood-color="#04112E" flood-opacity="0.34"/>
    </filter>
    <filter id="cardShadow" x="-12%" y="-10%" width="124%" height="128%">
      <feDropShadow dx="0" dy="14" stdDeviation="16" flood-color="#061947" flood-opacity="0.26"/>
    </filter>
    <clipPath id="higSquircle">
      <rect x="100" y="100" width="824" height="824" rx="185.4" ry="185.4"/>
    </clipPath>
  </defs>

  <!-- Outer macOS Dock Shadow + Squircle Body -->
  <g filter="url(#dropShadow)">
    <rect x="100" y="100" width="824" height="824" rx="185.4" ry="185.4" fill="url(#macBg)"/>
  </g>

  <g clip-path="url(#higSquircle)">
    <!-- Ambient Top-Left Liquid Glass Glow -->
    <circle cx="330" cy="270" r="410" fill="#FFFFFF" fill-opacity="0.17"/>
    <!-- Back Stacked Project Card -->
    <rect x="292" y="226" width="440" height="492" rx="74" ry="74" fill="#DCEBFF" fill-opacity="0.82"/>
    <!-- Main Pure White Elevated GTD Card -->
    <g filter="url(#cardShadow)">
      <rect x="256" y="264" width="512" height="504" rx="82" ry="82" fill="#FFFFFF"/>
    </g>
    <!-- Completed Things-Blue Checkbox Circle -->
    <circle cx="418" cy="462" r="92" fill="#1A73F7"/>
    <path d="M376,464 L406,494 L466,430" fill="none" stroke="#FFFFFF" stroke-width="26" stroke-linecap="round" stroke-linejoin="round"/>
    <!-- Task Title & Project Subtitle -->
    <rect x="538" y="422" width="166" height="38" rx="19" fill="#1C2434"/>
    <rect x="538" y="482" width="112" height="30" rx="15" fill="#7D8EA8"/>
    <!-- Secondary Open Checklist Item -->
    <circle cx="364" cy="632" r="32" fill="none" stroke="#8DA1BE" stroke-width="16"/>
    <rect x="426" y="616" width="232" height="32" rx="16" fill="#B2C2DA"/>
    <!-- Golden Today Star Badge -->
    <path fill="#FFC400" d="M704,210 L724,251 L770,258 L737,290 L745,335 L704,313 L663,335 L671,290 L638,258 L684,251 Z"/>
  </g>

  <!-- Specular Inner Rim Highlight (Apple Tahoe / Sequoia Material Polish) -->
  <rect x="103" y="103" width="818" height="818" rx="182.4" ry="182.4" fill="none" stroke="url(#glassRim)" stroke-width="6"/>
</svg>`;

// Monochrome black + alpha template icon for macOS MenuBarExtra
const menuBarSvg = `<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 36 36" width="36" height="36">
  <rect x="4" y="4" width="28" height="28" rx="7" ry="7" fill="none" stroke="#000000" stroke-width="2.6"/>
  <path d="M11.5,18.4 L16.0,22.9 L24.8,13.6" fill="none" stroke="#000000" stroke-width="2.8" stroke-linecap="round" stroke-linejoin="round"/>
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
await fs.writeFile(path.join(assetsDir, 'AppIcon.svg'), macAppIconSvg, 'utf8');
await sharp(Buffer.from(macAppIconSvg)).resize(1024, 1024).png().toFile(path.join(assetsDir, 'AppIcon-1024.png'));

for (const entry of iconSizes) {
  await sharp(Buffer.from(macAppIconSvg))
    .resize(entry.px, entry.px)
    .png()
    .toFile(path.join(iconsetDir, entry.name));
}

await sharp(Buffer.from(menuBarSvg)).resize(18, 18).png().toFile(path.join(assetsDir, 'MenuBarIconTemplate.png'));
await sharp(Buffer.from(menuBarSvg)).resize(36, 36).png().toFile(path.join(assetsDir, 'MenuBarIconTemplate@2x.png'));

console.log('Generated Apple HIG AppIcon.iconset (16px..1024px) & MenuBarIconTemplate PNGs.');
