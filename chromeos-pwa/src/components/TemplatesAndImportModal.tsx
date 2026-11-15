import React, { useMemo, useState } from 'react';
import {
  CheckSquare,
  ClipboardList,
  FileCode2,
  FolderPlus,
  Heading,
  Package,
  Sparkles,
  Trash2,
  X
} from 'lucide-react';
import {
  type AreaRecord,
  type ProjectTemplate,
  type ProjectTemplateBlueprint
} from '../domain/types';
import {
  STARTER_GTD_TEMPLATES,
  parseProjectOutlineInput,
  parseTemplateBlueprintJson
} from '../domain/v2Utils';
import { localDb } from '../data/localDb';
import type { TranslationStrings } from '../i18n/translations';

const SAMPLE_MARKDOWN_OUTLINE = `# Q4 Website Redesign
Modernize landing pages, speed up Lighthouse score, and launch clean docs.

## Research & Architecture
- [ ] Audit analytics & conversion funnel @45m
  - [ ] Export top 20 landing pages
  - [ ] Identify mobile drop-off steps
- [ ] Finalize design tokens & typography @30m

## Implementation
- [ ] Build responsive hero & pricing grid @60m
  - [ ] Desktop & Chromebook layout
  - [ ] Dark mode contrast check
- [ ] Run accessibility & QA pass @30m`;

export type TemplatesModalTab = 'gallery' | 'import';

interface TemplatesAndImportModalProps {
  initialTab?: TemplatesModalTab;
  defaultAreaId?: string | null;
  areas: AreaRecord[];
  savedTemplates: ProjectTemplate[];
  t: TranslationStrings;
  onCreatedProject: (projectId: string) => void;
  onToast: (msg: string) => void;
  onClose: () => void;
}

function countBlueprintItems(bp: ProjectTemplateBlueprint): {
  headingsCount: number;
  tasksCount: number;
  subtasksCount: number;
} {
  const topTasks = bp.tasks ?? [];
  let tasksCount = topTasks.length;
  let subtasksCount = topTasks.reduce(
    (sum, tk) => sum + (tk.checklistItems?.length ?? 0),
    0
  );
  for (const hd of bp.headings) {
    tasksCount += hd.tasks.length;
    for (const tk of hd.tasks) {
      subtasksCount += tk.checklistItems?.length ?? 0;
    }
  }
  return {
    headingsCount: bp.headings.length,
    tasksCount,
    subtasksCount
  };
}

export const TemplatesAndImportModal: React.FC<TemplatesAndImportModalProps> = ({
  initialTab = 'gallery',
  defaultAreaId = null,
  areas,
  savedTemplates,
  t,
  onCreatedProject,
  onToast,
  onClose
}) => {
  const [tab, setTab] = useState<TemplatesModalTab>(initialTab);
  const [selectedAreaId, setSelectedAreaId] = useState<string | null>(defaultAreaId);
  const [outlineText, setOutlineText] = useState('');
  const [busy, setBusy] = useState(false);

  const activeAreas = useMemo(() => areas.filter((a) => !a.isDeleted), [areas]);
  const visibleSavedTemplates = useMemo(
    () =>
      savedTemplates
        .filter((tpl) => !tpl.isDeleted)
        .sort((a, b) => a.sortOrder - b.sortOrder || b.updatedAt - a.updatedAt),
    [savedTemplates]
  );

  const parsedOutline = useMemo(() => {
    if (!outlineText.trim()) return null;
    return parseProjectOutlineInput(outlineText);
  }, [outlineText]);

  const parsedStats = useMemo(
    () => (parsedOutline ? countBlueprintItems(parsedOutline.blueprint) : null),
    [parsedOutline]
  );

  const handleInstantiateBlueprint = async (params: {
    title: string;
    notes: string;
    areaId: string | null;
    tagIds?: string[];
    blueprint: ProjectTemplateBlueprint;
  }) => {
    if (busy) return;
    setBusy(true);
    try {
      const projectId = await localDb.instantiateProjectFromBlueprint(params);
      onToast(`✓ ${params.title}`);
      onCreatedProject(projectId);
      onClose();
    } catch (err) {
      onToast(err instanceof Error ? err.message : 'Template error');
    } finally {
      setBusy(false);
    }
  };

  const handleImportCreate = async () => {
    if (!parsedOutline || busy) return;
    await handleInstantiateBlueprint({
      title: parsedOutline.title,
      notes: parsedOutline.notes,
      areaId: selectedAreaId,
      blueprint: parsedOutline.blueprint
    });
  };

  return (
    <div className="modal-backdrop" onClick={onClose}>
      <div
        className="modal-panel wide v2-templates-modal"
        data-testid="templates-import-modal"
        onClick={(e) => e.stopPropagation()}
      >
        <div className="modal-header">
          <div>
            <h3 className="modal-title" style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
              <ClipboardList size={19} style={{ color: 'var(--things-blue)' }} />
              <span>{t.templatesModalTitle}</span>
            </h3>
            <div style={{ fontSize: 12.2, color: 'var(--text-secondary)', marginTop: 2 }}>
              {t.templatesModalSubtitle}
            </div>
          </div>
          <button type="button" className="icon-btn" onClick={onClose}>
            <X size={18} />
          </button>
        </div>

        {/* Segmented Tabs: Gallery | Import Markdown / Outline */}
        <div className="v2-segmented-bar" style={{ marginBottom: 12 }}>
          <button
            type="button"
            data-testid="templates-tab-gallery"
            className={`v2-segmented-btn ${tab === 'gallery' ? 'active' : ''}`}
            onClick={() => setTab('gallery')}
          >
            <ClipboardList size={14} />
            <span>{t.templatesTabGallery}</span>
          </button>
          <button
            type="button"
            data-testid="templates-tab-import"
            className={`v2-segmented-btn ${tab === 'import' ? 'active' : ''}`}
            onClick={() => setTab('import')}
          >
            <FileCode2 size={14} />
            <span>{t.templatesTabImport}</span>
          </button>
        </div>

        {/* Target Area Selector */}
        {activeAreas.length > 0 && (
          <div style={{ marginBottom: 12 }}>
            <div
              style={{
                fontSize: 11.8,
                fontWeight: 650,
                color: 'var(--text-secondary)',
                marginBottom: 5
              }}
            >
              {t.templatesTargetAreaLabel}
            </div>
            <div style={{ display: 'flex', flexWrap: 'wrap', gap: 6 }}>
              <button
                type="button"
                className={`chip-btn ${selectedAreaId === null ? 'active' : ''}`}
                onClick={() => setSelectedAreaId(null)}
              >
                —
              </button>
              {activeAreas.map((area) => (
                <button
                  key={area.id}
                  type="button"
                  className={`chip-btn ${selectedAreaId === area.id ? 'active' : ''}`}
                  onClick={() => setSelectedAreaId(area.id)}
                >
                  <Package size={11.5} />
                  <span>{area.title}</span>
                </button>
              ))}
            </div>
          </div>
        )}

        {/* TAB 1: TEMPLATES GALLERY */}
        {tab === 'gallery' && (
          <div className="v2-templates-scroll">
            <div className="v2-section-label">{t.templatesStarterSection}</div>
            <div className="v2-templates-grid">
              {STARTER_GTD_TEMPLATES.map((starter) => {
                const titleStr =
                  (t[starter.titleKey as keyof TranslationStrings] as string) ||
                  starter.defaultTitle;
                const descStr =
                  (t[starter.descKey as keyof TranslationStrings] as string) ||
                  starter.defaultNotes;
                const counts = countBlueprintItems(starter.blueprint);

                return (
                  <div
                    key={starter.id}
                    className="v2-template-card"
                    data-testid={`starter-template-${starter.id}`}
                  >
                    <div className="v2-template-card-head">
                      <span className="v2-template-emoji">{starter.icon}</span>
                      <div style={{ flex: 1, minWidth: 0 }}>
                        <div className="v2-template-title">{titleStr}</div>
                        <div className="v2-template-meta">
                          {counts.headingsCount} {t.entityHeading} · {counts.tasksCount}{' '}
                          {t.entityTodos}
                        </div>
                      </div>
                    </div>
                    <p className="v2-template-desc">{descStr}</p>
                    <button
                      type="button"
                      className="btn-primary"
                      style={{ width: '100%', justifyContent: 'center' }}
                      disabled={busy}
                      onClick={() =>
                        void handleInstantiateBlueprint({
                          title: titleStr,
                          notes: descStr,
                          areaId: selectedAreaId,
                          blueprint: starter.blueprint
                        })
                      }
                    >
                      <FolderPlus size={14} />
                      <span>{t.templatesCreateProjectBtn}</span>
                    </button>
                  </div>
                );
              })}
            </div>

            <div className="v2-section-label" style={{ marginTop: 16 }}>
              {t.templatesSavedSection} ({visibleSavedTemplates.length})
            </div>

            {visibleSavedTemplates.length === 0 ? (
              <div className="v2-templates-empty">{t.templatesSavedEmpty}</div>
            ) : (
              <div className="v2-templates-grid">
                {visibleSavedTemplates.map((tpl) => {
                  const bp = parseTemplateBlueprintJson(tpl.blueprintJson);
                  const counts = countBlueprintItems(bp);
                  const effectiveAreaId =
                    selectedAreaId ??
                    (tpl.defaultAreaId && activeAreas.some((a) => a.id === tpl.defaultAreaId)
                      ? tpl.defaultAreaId
                      : null);

                  return (
                    <div
                      key={tpl.id}
                      className="v2-template-card"
                      data-testid={`saved-template-${tpl.id}`}
                    >
                      <div className="v2-template-card-head">
                        <span className="v2-template-emoji">📋</span>
                        <div style={{ flex: 1, minWidth: 0 }}>
                          <div className="v2-template-title">{tpl.title}</div>
                          <div className="v2-template-meta">
                            {counts.headingsCount} {t.entityHeading} · {counts.tasksCount}{' '}
                            {t.entityTodos}
                          </div>
                        </div>
                        <button
                          type="button"
                          className="icon-btn"
                          style={{ color: 'var(--deadline-red)' }}
                          title={t.actionDelete}
                          onClick={() => void localDb.deleteProjectTemplate(tpl.id)}
                        >
                          <Trash2 size={14} />
                        </button>
                      </div>

                      {tpl.notes.trim() && (
                        <p className="v2-template-desc">{tpl.notes.trim().split('\n')[0]}</p>
                      )}

                      <button
                        type="button"
                        className="btn-primary"
                        style={{ width: '100%', justifyContent: 'center', marginTop: 'auto' }}
                        disabled={busy}
                        onClick={() =>
                          void handleInstantiateBlueprint({
                            title: tpl.title,
                            notes: tpl.notes,
                            areaId: effectiveAreaId,
                            tagIds: tpl.tagIds,
                            blueprint: bp
                          })
                        }
                      >
                        <FolderPlus size={14} />
                        <span>{t.templatesCreateProjectBtn}</span>
                      </button>
                    </div>
                  );
                })}
              </div>
            )}
          </div>
        )}

        {/* TAB 2: IMPORT MARKDOWN / JSON OUTLINE */}
        {tab === 'import' && (
          <div>
            <div
              style={{
                display: 'flex',
                alignItems: 'center',
                justifyContent: 'space-between',
                gap: 8,
                flexWrap: 'wrap',
                marginBottom: 6
              }}
            >
              <label
                style={{
                  fontSize: 12.2,
                  fontWeight: 600,
                  color: 'var(--text-secondary)'
                }}
              >
                {t.templatesImportLabel}
              </label>
              <button
                type="button"
                className="chip-btn"
                onClick={() => setOutlineText(SAMPLE_MARKDOWN_OUTLINE)}
              >
                <Sparkles size={12} />
                <span>{t.templatesImportSampleBtn}</span>
              </button>
            </div>

            <textarea
              className="text-field v2-outline-textarea"
              data-testid="markdown-outline-input"
              rows={9}
              value={outlineText}
              onChange={(e) => setOutlineText(e.target.value)}
              placeholder={SAMPLE_MARKDOWN_OUTLINE}
            />

            {parsedOutline && parsedStats && (
              <div className="v2-outline-preview-bar">
                <div style={{ fontWeight: 700, fontSize: 13.4 }}>
                  📁 {parsedOutline.title}
                </div>
                <div style={{ display: 'flex', alignItems: 'center', gap: 10, fontSize: 12 }}>
                  <span style={{ display: 'inline-flex', alignItems: 'center', gap: 4 }}>
                    <Heading size={12.5} />
                    <strong>{parsedStats.headingsCount}</strong>
                  </span>
                  <span style={{ display: 'inline-flex', alignItems: 'center', gap: 4 }}>
                    <CheckSquare size={12.5} />
                    <strong>{parsedStats.tasksCount}</strong> {t.entityTodos}
                  </span>
                  {parsedStats.subtasksCount > 0 && (
                    <span>
                      • <strong>{parsedStats.subtasksCount}</strong> {t.actionChecklist}
                    </span>
                  )}
                </div>
              </div>
            )}

            <div
              style={{
                display: 'flex',
                justifyContent: 'flex-end',
                gap: 8,
                marginTop: 14
              }}
            >
              <button type="button" className="btn-secondary" onClick={onClose}>
                {t.actionCancel}
              </button>
              <button
                type="button"
                className="btn-primary"
                data-testid="create-from-outline-btn"
                disabled={
                  busy ||
                  !parsedOutline ||
                  (parsedStats != null &&
                    parsedStats.tasksCount === 0 &&
                    parsedStats.headingsCount === 0)
                }
                onClick={() => void handleImportCreate()}
              >
                <FolderPlus size={15} />
                <span>{t.templatesImportCreateBtn}</span>
              </button>
            </div>
          </div>
        )}
      </div>
    </div>
  );
};
