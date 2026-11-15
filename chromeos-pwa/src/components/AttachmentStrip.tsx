import React, { useEffect, useMemo, useRef, useState } from 'react';
import {
  Download,
  ExternalLink,
  FileText,
  Image as ImageIcon,
  Paperclip,
  Trash2,
  X,
  ZoomIn,
  ZoomOut
} from 'lucide-react';
import type { AttachmentRecord } from '../domain/types';
import {
  base64ToBlob,
  formatFileSize,
  processAndAddAttachmentFile
} from '../data/attachmentHelpers';
import { localDb } from '../data/localDb';
import type { TranslationStrings } from '../i18n/translations';

export const AttachmentPreviewModal: React.FC<{
  attachment: AttachmentRecord;
  t: TranslationStrings;
  onClose: () => void;
  onDelete: (id: string) => void;
}> = ({ attachment, t, onClose, onDelete }) => {
  const [zoom, setZoom] = useState(1);
  const isImage = attachment.mimeType.startsWith('image/');
  const isPdf =
    attachment.mimeType === 'application/pdf' ||
    attachment.fileName.toLowerCase().endsWith('.pdf');

  const resolvedMime = isPdf
    ? 'application/pdf'
    : attachment.mimeType || 'application/octet-stream';

  const fallbackDataUri = useMemo(
    () => `data:${resolvedMime};base64,${attachment.dataBase64}`,
    [resolvedMime, attachment.dataBase64]
  );

  const blobUrl = useMemo(() => {
    const blob = base64ToBlob(attachment.dataBase64, resolvedMime);
    return blob ? URL.createObjectURL(blob) : null;
  }, [attachment.dataBase64, resolvedMime]);

  useEffect(() => {
    return () => {
      if (blobUrl) {
        URL.revokeObjectURL(blobUrl);
      }
    };
  }, [blobUrl]);

  const handleDownload = () => {
    if (blobUrl) {
      const link = document.createElement('a');
      link.href = blobUrl;
      link.download = attachment.fileName || 'attachment';
      document.body.appendChild(link);
      link.click();
      document.body.removeChild(link);
      return;
    }
    const blob = base64ToBlob(attachment.dataBase64, resolvedMime);
    if (blob) {
      const tempUrl = URL.createObjectURL(blob);
      const link = document.createElement('a');
      link.href = tempUrl;
      link.download = attachment.fileName || 'attachment';
      document.body.appendChild(link);
      link.click();
      document.body.removeChild(link);
      window.setTimeout(() => URL.revokeObjectURL(tempUrl), 1000);
      return;
    }
    const link = document.createElement('a');
    link.href = fallbackDataUri;
    link.download = attachment.fileName || 'attachment';
    document.body.appendChild(link);
    link.click();
    document.body.removeChild(link);
  };

  return (
    <div className="modal-backdrop" onClick={onClose} style={{ zIndex: 120 }}>
      <div
        className="modal-panel"
        style={{
          maxWidth: 820,
          width: '94vw',
          maxHeight: '90vh',
          display: 'flex',
          flexDirection: 'column',
          gap: 12
        }}
        onClick={(e) => e.stopPropagation()}
      >
        <div className="modal-header" style={{ marginBottom: 0 }}>
          <div style={{ minWidth: 0 }}>
            <h3
              className="modal-title"
              style={{
                display: 'flex',
                alignItems: 'center',
                gap: 8,
                overflow: 'hidden',
                textOverflow: 'ellipsis',
                whiteSpace: 'nowrap'
              }}
            >
              {isImage ? (
                <ImageIcon size={17} style={{ color: 'var(--things-blue)', flexShrink: 0 }} />
              ) : (
                <FileText size={17} style={{ color: 'var(--things-blue)', flexShrink: 0 }} />
              )}
              <span style={{ overflow: 'hidden', textOverflow: 'ellipsis' }}>
                {attachment.fileName}
              </span>
            </h3>
            <div style={{ fontSize: 11.8, color: 'var(--text-secondary)', marginTop: 2 }}>
              {formatFileSize(attachment.sizeBytes)} · {attachment.mimeType}
            </div>
          </div>

          <div style={{ display: 'flex', alignItems: 'center', gap: 6 }}>
            {isImage && (
              <>
                <button
                  type="button"
                  className="icon-btn"
                  title="Zoom Out"
                  onClick={() => setZoom((z) => Math.max(0.5, Number((z - 0.25).toFixed(2))))}
                >
                  <ZoomOut size={16} />
                </button>
                <button
                  type="button"
                  className="icon-btn"
                  title="Zoom In"
                  onClick={() => setZoom((z) => Math.min(3, Number((z + 0.25).toFixed(2))))}
                >
                  <ZoomIn size={16} />
                </button>
              </>
            )}
            <button
              type="button"
              className="chip-btn active"
              onClick={handleDownload}
              title={t.attachmentsOpenExternal}
            >
              <Download size={13} />
              <span>{t.attachmentsOpenExternal}</span>
            </button>
            <button
              type="button"
              className="icon-btn"
              style={{ color: 'var(--deadline-red)' }}
              title={t.actionDelete}
              onClick={() => {
                onDelete(attachment.id);
                onClose();
              }}
            >
              <Trash2 size={16} />
            </button>
            <button type="button" className="icon-btn" onClick={onClose}>
              <X size={18} />
            </button>
          </div>
        </div>

        <div
          style={{
            flex: 1,
            minHeight: 280,
            maxHeight: '72vh',
            overflow: 'auto',
            borderRadius: 'var(--radius-sm)',
            background: 'var(--bg-subtle)',
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'center',
            padding: 12
          }}
        >
          {isImage ? (
            <img
              src={blobUrl || fallbackDataUri}
              alt={attachment.fileName}
              style={{
                maxWidth: zoom === 1 ? '100%' : 'none',
                maxHeight: zoom === 1 ? '66vh' : 'none',
                transform: `scale(${zoom})`,
                transformOrigin: 'center center',
                borderRadius: 8,
                objectFit: 'contain',
                transition: 'transform 0.15s ease'
              }}
            />
          ) : isPdf ? (
            blobUrl ? (
              <iframe
                src={blobUrl}
                title={attachment.fileName}
                style={{
                  width: '100%',
                  height: '65vh',
                  border: 'none',
                  borderRadius: 8,
                  background: '#fff'
                }}
              />
            ) : null
          ) : (
            <div
              style={{
                display: 'flex',
                flexDirection: 'column',
                alignItems: 'center',
                gap: 12,
                padding: 32,
                textAlign: 'center'
              }}
            >
              <FileText size={44} style={{ color: 'var(--things-blue)' }} />
              <div style={{ fontWeight: 600, fontSize: 14.5 }}>{attachment.fileName}</div>
              <div style={{ fontSize: 12.5, color: 'var(--text-secondary)' }}>
                {formatFileSize(attachment.sizeBytes)}
              </div>
              <button type="button" className="btn-primary" onClick={handleDownload}>
                <ExternalLink size={14} />
                <span>{t.attachmentsOpenExternal}</span>
              </button>
            </div>
          )}
        </div>
      </div>
    </div>
  );
};

export const AttachmentsStrip: React.FC<{
  taskId?: string | null;
  projectId?: string | null;
  attachments: AttachmentRecord[];
  t: TranslationStrings;
  onToast: (msg: string) => void;
  showAddButton?: boolean;
  fileInputRef?: React.RefObject<HTMLInputElement | null>;
}> = ({
  taskId = null,
  projectId = null,
  attachments,
  t,
  onToast,
  showAddButton = true,
  fileInputRef
}) => {
  const internalInputRef = useRef<HTMLInputElement | null>(null);
  const resolvedInputRef = fileInputRef ?? internalInputRef;
  const [previewAttachment, setPreviewAttachment] = useState<AttachmentRecord | null>(null);

  const handleFilesSelected = async (e: React.ChangeEvent<HTMLInputElement>) => {
    const files = Array.from(e.target.files ?? []);
    e.target.value = '';
    for (const f of files) {
      await processAndAddAttachmentFile(f, { taskId, projectId }, t, onToast);
    }
  };

  if (attachments.length === 0 && !showAddButton) {
    return (
      <input
        ref={resolvedInputRef}
        type="file"
        multiple
        accept="image/*,application/pdf,.txt,.md,.csv,.json,.doc,.docx,.xls,.xlsx"
        style={{ display: 'none' }}
        onChange={handleFilesSelected}
      />
    );
  }

  return (
    <div
      className="attachments-strip-container"
      style={{
        display: 'flex',
        flexDirection: 'column',
        gap: 6,
        marginTop: 6,
        marginBottom: 4
      }}
      onClick={(e) => e.stopPropagation()}
    >
      <input
        ref={resolvedInputRef}
        type="file"
        multiple
        accept="image/*,application/pdf,.txt,.md,.csv,.json,.doc,.docx,.xls,.xlsx"
        style={{ display: 'none' }}
        onChange={handleFilesSelected}
      />

      <div
        style={{
          display: 'flex',
          alignItems: 'center',
          gap: 8,
          overflowX: 'auto',
          paddingBottom: 2
        }}
      >
        {attachments.map((att) => {
          const isImage = att.mimeType.startsWith('image/');
          const isPdf =
            att.mimeType === 'application/pdf' || att.fileName.toLowerCase().endsWith('.pdf');
          const thumbUri = isImage
            ? `data:${att.mimeType};base64,${att.dataBase64}`
            : undefined;

          return (
            <div
              key={att.id}
              onClick={() => setPreviewAttachment(att)}
              style={{
                display: 'inline-flex',
                alignItems: 'center',
                gap: 8,
                padding: isImage ? '4px 8px 4px 4px' : '5px 8px',
                borderRadius: 9,
                border: '1px solid var(--border-subtle)',
                background: 'var(--bg-subtle)',
                cursor: 'pointer',
                flexShrink: 0,
                maxWidth: 210
              }}
              title={`${att.fileName} (${formatFileSize(att.sizeBytes)})`}
            >
              {isImage && thumbUri ? (
                <img
                  src={thumbUri}
                  alt={att.fileName}
                  style={{
                    width: 34,
                    height: 34,
                    borderRadius: 6,
                    objectFit: 'cover',
                    flexShrink: 0
                  }}
                />
              ) : (
                <span
                  style={{
                    width: 30,
                    height: 30,
                    borderRadius: 6,
                    background: isPdf ? 'rgba(229, 57, 53, 0.12)' : 'rgba(36, 139, 253, 0.12)',
                    color: isPdf ? 'var(--deadline-red)' : 'var(--things-blue)',
                    display: 'inline-flex',
                    alignItems: 'center',
                    justifyContent: 'center',
                    fontSize: 10,
                    fontWeight: 700,
                    flexShrink: 0
                  }}
                >
                  {isPdf ? 'PDF' : <FileText size={14} />}
                </span>
              )}

              <div style={{ minWidth: 0, flex: 1 }}>
                <div
                  style={{
                    fontSize: 12,
                    fontWeight: 500,
                    color: 'var(--text-primary)',
                    overflow: 'hidden',
                    textOverflow: 'ellipsis',
                    whiteSpace: 'nowrap'
                  }}
                >
                  {att.fileName}
                </div>
                <div style={{ fontSize: 10.5, color: 'var(--text-muted)' }}>
                  {formatFileSize(att.sizeBytes)}
                </div>
              </div>

              <button
                type="button"
                className="icon-btn"
                style={{ height: 22, minWidth: 22, padding: 0 }}
                title={t.actionDelete}
                onClick={(e) => {
                  e.stopPropagation();
                  void localDb.deleteAttachment(att.id);
                }}
              >
                <X size={13} />
              </button>
            </div>
          );
        })}

        {showAddButton && (
          <button
            type="button"
            className="chip-btn"
            onClick={() => resolvedInputRef.current?.click()}
            title={t.attachmentsAddPhotoBtn}
            style={{ flexShrink: 0 }}
          >
            <Paperclip size={12} />
            <span>{t.attachmentsAddBtn}</span>
          </button>
        )}
      </div>

      {previewAttachment && (
        <AttachmentPreviewModal
          attachment={previewAttachment}
          t={t}
          onClose={() => setPreviewAttachment(null)}
          onDelete={(id) => void localDb.deleteAttachment(id)}
        />
      )}
    </div>
  );
};
