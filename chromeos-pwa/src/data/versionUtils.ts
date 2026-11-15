import { APP_BUILD_CODE, APP_VERSION } from '../domain/types.ts';

export { APP_BUILD_CODE, APP_VERSION };

interface ParsedVersion {
  major: number;
  minor: number;
  patch: number;
  prerelease: string | null;
  build: number;
}

function parseVersionString(raw: string): ParsedVersion {
  const cleaned = String(raw || '')
    .trim()
    .replace(/^[vV]/, '');
  const plusIdx = cleaned.indexOf('+');
  const withoutBuild = plusIdx >= 0 ? cleaned.slice(0, plusIdx) : cleaned;
  const buildStr = plusIdx >= 0 ? cleaned.slice(plusIdx + 1) : '';

  const dashIdx = withoutBuild.indexOf('-');
  const coreStr = dashIdx >= 0 ? withoutBuild.slice(0, dashIdx) : withoutBuild;
  const prerelease = dashIdx >= 0 ? withoutBuild.slice(dashIdx + 1) : null;

  const parts = coreStr.split('.');
  const major = Number.parseInt(parts[0] ?? '0', 10);
  const minor = Number.parseInt(parts[1] ?? '0', 10);
  const patch = Number.parseInt(parts[2] ?? '0', 10);
  const buildParsed = buildStr ? Number.parseInt(buildStr, 10) : 0;

  return {
    major: Number.isFinite(major) ? major : 0,
    minor: Number.isFinite(minor) ? minor : 0,
    patch: Number.isFinite(patch) ? patch : 0,
    prerelease,
    build: Number.isFinite(buildParsed) ? buildParsed : 0
  };
}

function comparePrerelease(a: string, b: string): number {
  const partsA = a.split('.');
  const partsB = b.split('.');
  const len = Math.max(partsA.length, partsB.length);
  for (let i = 0; i < len; i++) {
    const idA = partsA[i];
    const idB = partsB[i];
    if (idA === undefined) return -1;
    if (idB === undefined) return 1;
    if (idA === idB) continue;
    const isNumA = /^\d+$/.test(idA);
    const isNumB = /^\d+$/.test(idB);
    if (isNumA && isNumB) {
      const nA = Number.parseInt(idA, 10);
      const nB = Number.parseInt(idB, 10);
      if (nA !== nB) return nA > nB ? 1 : -1;
    } else if (isNumA && !isNumB) {
      return -1;
    } else if (!isNumA && isNumB) {
      return 1;
    } else {
      return idA > idB ? 1 : -1;
    }
  }
  return 0;
}

export function compareSemver(a: string, b: string): number {
  const va = parseVersionString(a);
  const vb = parseVersionString(b);

  if (va.major !== vb.major) return va.major > vb.major ? 1 : -1;
  if (va.minor !== vb.minor) return va.minor > vb.minor ? 1 : -1;
  if (va.patch !== vb.patch) return va.patch > vb.patch ? 1 : -1;

  if (va.prerelease === null && vb.prerelease !== null) return 1;
  if (va.prerelease !== null && vb.prerelease === null) return -1;
  if (va.prerelease !== null && vb.prerelease !== null) {
    const preCmp = comparePrerelease(va.prerelease, vb.prerelease);
    if (preCmp !== 0) return preCmp;
  }

  if (va.build !== vb.build) return va.build > vb.build ? 1 : -1;
  return 0;
}

export function isPrereleaseVersion(version: string): boolean {
  const cleaned = String(version || '')
    .trim()
    .replace(/^[vV]/, '');
  return cleaned.split('+')[0].includes('-');
}

export function isEligibleReleasePayload(
  data: unknown,
  currentVersion: string = APP_VERSION,
  includeBeta: boolean = isPrereleaseVersion(currentVersion)
): boolean {
  if (!data || typeof data !== 'object') return false;
  const rec = data as Record<string, unknown>;
  if (rec.draft === true) return false;
  if (!includeBeta && rec.prerelease === true) return false;
  if (typeof rec.tag_name !== 'string') return false;

  const rawTag = rec.tag_name.trim();
  if (!/^[vV]/.test(rawTag)) return false;

  const cleanCandidate = rawTag.replace(/^[vV]/, '').trim();
  if (!/^\d+\.\d+\.\d+/.test(cleanCandidate)) return false;

  const candidateIsPrerelease = cleanCandidate.split('+')[0].includes('-');
  if (!includeBeta && candidateIsPrerelease) {
    return false;
  }

  return true;
}

export function selectLatestEligibleRelease(
  releases: unknown,
  currentVersion: string = APP_VERSION,
  includeBeta: boolean = isPrereleaseVersion(currentVersion)
): Record<string, unknown> | null {
  if (!Array.isArray(releases)) {
    return isEligibleReleasePayload(releases, currentVersion, includeBeta)
      ? (releases as Record<string, unknown>)
      : null;
  }
  let best: Record<string, unknown> | null = null;
  let bestVersion = '';
  for (const item of releases) {
    if (!isEligibleReleasePayload(item, currentVersion, includeBeta)) continue;
    const rec = item as Record<string, unknown>;
    const candidate = String(rec.tag_name).trim().replace(/^[vV]/, '').trim();
    if (!best || compareSemver(candidate, bestVersion) > 0) {
      best = rec;
      bestVersion = candidate;
    }
  }
  return best;
}
