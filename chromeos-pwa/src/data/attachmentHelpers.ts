import { localDb } from './localDb';
import { estimateBase64DecodedBytes } from './syncUtils';
import type { TranslationStrings } from '../i18n/translations';

const MAX_ATTACHMENT_BYTES = 5 * 1024 * 1024; // 5 MB
const TARGET_INLINE_IMAGE_BYTES = 680_000;

export function formatFileSize(bytes: number): string {
  if (bytes < 1024) return `${bytes} B`;
  const kb = bytes / 1024;
  if (kb < 1024) return `${kb.toFixed(kb >= 100 ? 0 : 1)} KB`;
  const mb = kb / 1024;
  return `${mb.toFixed(1)} MB`;
}

function arrayBufferToBase64(buffer: ArrayBuffer): string {
  const bytes = new Uint8Array(buffer);
  let binary = '';
  const chunkSize = 0x8000;
  for (let i = 0; i < bytes.length; i += chunkSize) {
    binary += String.fromCharCode(...bytes.subarray(i, i + chunkSize));
  }
  return btoa(binary);
}

export function base64ToBlob(base64: string, mimeType: string): Blob | null {
  if (!base64) return null;
  try {
    const clean = base64.replace(/\s+/g, '');
    const binary = atob(clean);
    const bytes = new Uint8Array(binary.length);
    for (let i = 0; i < binary.length; i++) {
      bytes[i] = binary.charCodeAt(i);
    }
    return new Blob([bytes], { type: mimeType || 'application/octet-stream' });
  } catch {
    return null;
  }
}

async function compressImageIfNeeded(
  file: File
): Promise<{ dataBase64: string; mimeType: string; sizeBytes: number }> {
  return new Promise((resolve, reject) => {
    const reader = new FileReader();
    reader.onerror = () => reject(new Error('Failed to read image file'));
    reader.onload = () => {
      const dataUrl = String(reader.result || '');
      const img = new Image();
      img.onerror = () => {
        // Fallback to raw bytes if Image decode fails
        void file.arrayBuffer().then((buf) => {
          resolve({
            dataBase64: arrayBufferToBase64(buf),
            mimeType: file.type || 'image/jpeg',
            sizeBytes: buf.byteLength
          });
        }, reject);
      };
      img.onload = () => {
        const w = img.naturalWidth || img.width;
        const h = img.naturalHeight || img.height;
        if (w <= 1600 && h <= 1600 && file.size <= TARGET_INLINE_IMAGE_BYTES) {
          const commaIdx = dataUrl.indexOf(',');
          const rawB64 = commaIdx >= 0 ? dataUrl.slice(commaIdx + 1) : '';
          resolve({
            dataBase64: rawB64,
            mimeType: file.type || 'image/jpeg',
            sizeBytes: file.size
          });
          return;
        }

        const canvas = document.createElement('canvas');
        const ctx = canvas.getContext('2d');
        if (!ctx) {
          const commaIdx = dataUrl.indexOf(',');
          resolve({
            dataBase64: commaIdx >= 0 ? dataUrl.slice(commaIdx + 1) : '',
            mimeType: file.type || 'image/jpeg',
            sizeBytes: file.size
          });
          return;
        }

        // Iteratively compress across quality steps (0.82 -> 0.68 -> 0.54) and dimension caps
        // so camera/gallery photos fit reliably under 680 KB for inline Cloud Sync.
        const maxDimensions = [1600, 1280, 960];
        const qualitySteps = [0.82, 0.68, 0.54];
        let bestB64 = '';
        let bestBytes = Number.POSITIVE_INFINITY;

        outer: for (let dIdx = 0; dIdx < maxDimensions.length; dIdx++) {
          const dimCap = maxDimensions[dIdx];
          const scale = Math.min(1, dimCap / Math.max(w, h));
          const targetW = Math.max(1, Math.round(w * scale));
          const targetH = Math.max(1, Math.round(h * scale));
          canvas.width = targetW;
          canvas.height = targetH;
          ctx.clearRect(0, 0, targetW, targetH);
          ctx.drawImage(img, 0, 0, targetW, targetH);

          const qualitiesToTry = dIdx === 0 ? qualitySteps : [0.54];
          for (const quality of qualitiesToTry) {
            const compressedUrl = canvas.toDataURL('image/jpeg', quality);
            const commaIdx = compressedUrl.indexOf(',');
            const b64 = commaIdx >= 0 ? compressedUrl.slice(commaIdx + 1) : '';
            const decodedBytes = estimateBase64DecodedBytes(b64);
            bestB64 = b64;
            bestBytes = decodedBytes;
            if (decodedBytes <= TARGET_INLINE_IMAGE_BYTES) {
              break outer;
            }
          }
        }

        resolve({
          dataBase64: bestB64,
          mimeType: 'image/jpeg',
          sizeBytes: Number.isFinite(bestBytes) ? bestBytes : 0
        });
      };
      img.src = dataUrl;
    };
    reader.readAsDataURL(file);
  });
}

export async function processAndAddAttachmentFile(
  file: File,
  target: { taskId?: string | null; projectId?: string | null },
  t: TranslationStrings,
  onToast: (msg: string) => void
): Promise<boolean> {
  try {
    const isImage = file.type.startsWith('image/');
    let dataBase64 = '';
    let mimeType = file.type || 'application/octet-stream';
    let sizeBytes = file.size;

    if (isImage) {
      const compressed = await compressImageIfNeeded(file);
      dataBase64 = compressed.dataBase64;
      mimeType = compressed.mimeType;
      sizeBytes = compressed.sizeBytes;
    } else {
      if (file.size > MAX_ATTACHMENT_BYTES) {
        onToast(`⚠️ ${t.attachmentsTooLargeError}`);
        return false;
      }
      const buf = await file.arrayBuffer();
      dataBase64 = arrayBufferToBase64(buf);
      sizeBytes = buf.byteLength;
    }

    if (sizeBytes > MAX_ATTACHMENT_BYTES) {
      onToast(`⚠️ ${t.attachmentsTooLargeError}`);
      return false;
    }

    await localDb.addAttachment({
      taskId: target.taskId ?? null,
      projectId: target.projectId ?? null,
      fileName: file.name || 'attachment',
      mimeType,
      sizeBytes,
      dataBase64
    });
    return true;
  } catch (err) {
    onToast(err instanceof Error ? err.message : 'Attachment error');
    return false;
  }
}
