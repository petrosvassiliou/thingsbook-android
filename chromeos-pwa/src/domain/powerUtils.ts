export type DetectedNotesLinkType = 'WEB' | 'EMAIL' | 'PHONE';

export interface DetectedNotesLink {
  rawText: string;
  displayLabel: string;
  targetUri: string;
  type: DetectedNotesLinkType;
}

const URL_REGEX = /(https?:\/\/[^\s<>()]+|www\.[^\s<>()]+)/gi;
const EMAIL_REGEX = /([A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\.[A-Za-z]{2,})/g;
const PHONE_REGEX =
  /(?<!\w)(?:\+\d{1,3}[\s.-]?)?(?:\(?\d{2,5}\)?[\s.-]?)?\d{3,5}[\s.-]?\d{2,6}(?!\w)/g;
const ISO_DATE_REGEX = /^\d{4}-\d{2}-\d{2}$/;

export function stripTrailingPunctuation(raw: string): string {
  return raw.replace(/[.,;:!?)]+$/, '');
}

/**
 * Extracts clickable Web URLs, Email addresses, and Phone numbers from task/project notes
 * (matching Android v1.7.0 ThingsMarkdownNotesSupport.kt).
 */
export function extractDetectedLinksFromNotes(notes: string): DetectedNotesLink[] {
  if (!notes || !notes.trim()) return [];
  const results: DetectedNotesLink[] = [];
  const seenUris = new Set<string>();

  for (const match of notes.matchAll(URL_REGEX)) {
    const raw = stripTrailingPunctuation(match[0]);
    if (!raw) continue;
    const targetUri = /^https?:\/\//i.test(raw) ? raw : `https://${raw}`;
    if (!seenUris.has(targetUri)) {
      seenUris.add(targetUri);
      const cleanLabel = raw.replace(/^https?:\/\/(www\.)?/i, '').replace(/\/$/, '');
      results.push({
        rawText: raw,
        displayLabel: cleanLabel.length > 34 ? `${cleanLabel.slice(0, 32)}…` : cleanLabel,
        targetUri,
        type: 'WEB'
      });
    }
  }

  for (const match of notes.matchAll(EMAIL_REGEX)) {
    const raw = stripTrailingPunctuation(match[0]);
    if (!raw) continue;
    const targetUri = `mailto:${raw}`;
    if (!seenUris.has(targetUri)) {
      seenUris.add(targetUri);
      results.push({
        rawText: raw,
        displayLabel: raw,
        targetUri,
        type: 'EMAIL'
      });
    }
  }

  for (const match of notes.matchAll(PHONE_REGEX)) {
    const raw = match[0].trim();
    if (!raw || ISO_DATE_REGEX.test(raw)) continue;
    if (notes.includes(`/${raw}`) || notes.includes(`=${raw}`)) continue;
    const digitsOnly = raw.replace(/[^\d+]/g, '');
    const digitCount = digitsOnly.replace(/\D/g, '').length;
    if (digitCount < 7 || digitCount > 15) continue;
    const targetUri = `tel:${digitsOnly}`;
    if (!seenUris.has(targetUri)) {
      seenUris.add(targetUri);
      results.push({
        rawText: raw,
        displayLabel: raw,
        targetUri,
        type: 'PHONE'
      });
    }
  }

  return results.slice(0, 8);
}

export async function sha256Hex(input: string): Promise<string> {
  const bytes = new TextEncoder().encode(input);
  const digest = await crypto.subtle.digest('SHA-256', bytes);
  return Array.from(new Uint8Array(digest))
    .map((b) => b.toString(16).padStart(2, '0'))
    .join('');
}

export async function registerPlatformWebAuthnCredential(): Promise<string> {
  try {
    if (
      typeof window === 'undefined' ||
      !window.PublicKeyCredential ||
      !navigator.credentials?.create
    ) {
      return '';
    }
    const available =
      await PublicKeyCredential.isUserVerifyingPlatformAuthenticatorAvailable?.();
    if (!available) return '';

    const challenge = crypto.getRandomValues(new Uint8Array(32));
    const userId = crypto.getRandomValues(new Uint8Array(16));
    const cred = (await navigator.credentials.create({
      publicKey: {
        challenge,
        rp: { name: 'Thingsbook for ChromeOS' },
        user: {
          id: userId,
          name: 'thingsbook-local-lock',
          displayName: 'Thingsbook App Lock'
        },
        pubKeyCredParams: [
          { type: 'public-key', alg: -7 },
          { type: 'public-key', alg: -257 }
        ],
        authenticatorSelection: {
          authenticatorAttachment: 'platform',
          userVerification: 'required'
        },
        timeout: 25000
      }
    })) as PublicKeyCredential | null;
    if (!cred) return '';
    return btoa(String.fromCharCode(...new Uint8Array(cred.rawId)));
  } catch {
    return '';
  }
}

export async function verifyPlatformWebAuthnCredential(credIdBase64: string): Promise<boolean> {
  try {
    if (
      !credIdBase64 ||
      typeof window === 'undefined' ||
      !window.PublicKeyCredential ||
      !navigator.credentials?.get
    ) {
      return false;
    }
    const rawId = Uint8Array.from(atob(credIdBase64), (c) => c.charCodeAt(0));
    const challenge = crypto.getRandomValues(new Uint8Array(32));
    const assertion = await navigator.credentials.get({
      publicKey: {
        challenge,
        allowCredentials: [{ type: 'public-key', id: rawId }],
        userVerification: 'required',
        timeout: 25000
      }
    });
    return Boolean(assertion);
  } catch {
    return false;
  }
}
