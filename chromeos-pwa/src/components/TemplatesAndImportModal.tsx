import React, { useMemo, useRef, useState } from 'react';
import {
  ArrowLeftRight,
  CheckSquare,
  ClipboardList,
  Download,
  FileCode2,
  FileSpreadsheet,
  Folder,
  FolderPlus,
  Heading,
  Sparkles,
  Trash2,
  Upload,
  X
} from 'lucide-react';
import {
  type AreaRecord,
  type ProjectRecord,
  type ProjectTemplate,
  type ProjectTemplateBlueprint,
  type SupportedLang
} from '../domain/types';
import {
  getStarterGtdTemplates,
  parseProjectOutlineInput,
  parseTemplateBlueprintJson
} from '../domain/v2Utils';
import {
  importExternalProjectAuto,
  type ExternalExportFormat
} from '../data/externalProjectCodec';
import { localDb } from '../data/localDb';
import type { TranslationStrings } from '../i18n/translations';

const SAMPLE_MARKDOWN_OUTLINES: Record<SupportedLang, string> = {
  en: `# Q4 Website Redesign
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
- [ ] Run accessibility & QA pass @30m`,
  de: `# Q4 Website-Relaunch
Landingpages modernisieren, Lighthouse-Score verbessern und Dokumentation starten.

## Recherche & Architektur
- [ ] Analytics & Conversion-Funnel prüfen @45m
  - [ ] Top-20-Landingpages exportieren
  - [ ] Mobile Abbruchstellen identifizieren
- [ ] Design-Tokens & Typografie festlegen @30m

## Umsetzung
- [ ] Responsiven Hero-Bereich & Preisübersicht bauen @60m
  - [ ] Desktop- & Chromebook-Layout
  - [ ] Kontrastprüfung im Dunkelmodus
- [ ] Barrierefreiheit & QA-Durchlauf @30m`,
  fr: `# Refonte du site web T4
Moderniser les pages d'accueil, améliorer le score Lighthouse et publier la doc.

## Recherche & Architecture
- [ ] Auditer les statistiques & le tunnel de conversion @45m
  - [ ] Exporter les 20 pages principales
  - [ ] Identifier les points d'abandon mobile
- [ ] Finaliser les jetons de design & la typographie @30m

## Implémentation
- [ ] Construire la section héro & la grille tarifaire @60m
  - [ ] Mise en page bureau & Chromebook
  - [ ] Vérification du contraste en mode sombre
- [ ] Effectuer la passe d'accessibilité & QA @30m`,
  it: `# Restyling sito web Q4
Modernizzare le landing page, migliorare il punteggio Lighthouse e lanciare la documentazione.

## Ricerca & Architettura
- [ ] Analizzare analytics & funnel di conversione @45m
  - [ ] Esportare le prime 20 landing page
  - [ ] Identificare i punti di abbandono mobile
- [ ] Definire design token & tipografia @30m

## Implementazione
- [ ] Realizzare hero responsive & griglia prezzi @60m
  - [ ] Layout desktop & Chromebook
  - [ ] Controllo contrasto tema scuro
- [ ] Eseguire verifica accessibilità & QA @30m`,
  es: `# Rediseño web Q4
Modernizar páginas de destino, optimizar Lighthouse y lanzar documentación clara.

## Investigación y Arquitectura
- [ ] Auditar analíticas y embudo de conversión @45m
  - [ ] Exportar las 20 páginas principales
  - [ ] Identificar pasos de abandono móvil
- [ ] Definir tokens de diseño y tipografía @30m

## Implementación
- [ ] Construir cabecera adaptable y tabla de precios @60m
  - [ ] Diseño para escritorio y Chromebook
  - [ ] Revisión de contraste en modo oscuro
- [ ] Ejecutar revisión de accesibilidad y QA @30m`
};

export type TemplatesModalTab = 'gallery' | 'import' | 'external';

interface TemplatesAndImportModalProps {
  initialTab?: TemplatesModalTab;
  defaultAreaId?: string | null;
  activeProjectId?: string | null;
  areas: AreaRecord[];
  projects?: ProjectRecord[];
  savedTemplates: ProjectTemplate[];
  lang: SupportedLang;
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
  activeProjectId = null,
  areas,
  projects = [],
  savedTemplates,
  lang,
  t,
  onCreatedProject,
  onToast,
  onClose
}) => {
  const [tab, setTab] = useState<TemplatesModalTab>(initialTab);
  const [selectedAreaId, setSelectedAreaId] = useState<string | null>(defaultAreaId);
  const [outlineText, setOutlineText] = useState('');
  const [busy, setBusy] = useState(false);

  const asanaFileInputRef = useRef<HTMLInputElement | null>(null);
  const plannerFileInputRef = useRef<HTMLInputElement | null>(null);

  const starterTemplates = useMemo(() => getStarterGtdTemplates(lang), [lang]);
  const sampleMarkdownOutline = SAMPLE_MARKDOWN_OUTLINES[lang] ?? SAMPLE_MARKDOWN_OUTLINES.en;

  const activeAreas = useMemo(() => areas.filter((a) => !a.isDeleted), [areas]);
  const activeProjects = useMemo(
    () =>
      projects
        .filter((p) => !p.isDeleted && p.status === 'ACTIVE')
        .sort((a, b) => a.sortOrder - b.sortOrder || b.updatedAt - a.updatedAt),
    [projects]
  );

  const [selectedExportProjectId, setSelectedExportProjectId] = useState<string>(() => {
    if (
      activeProjectId &&
      projects.some((p) => !p.isDeleted && p.status === 'ACTIVE' && p.id === activeProjectId)
    ) {
      return activeProjectId;
    }
    const firstActive = projects.find((p) => !p.isDeleted && p.status === 'ACTIVE');
    return firstActive?.id ?? '';
  });

  const effectiveExportProjectId = useMemo(() => {
    if (
      selectedExportProjectId &&
      activeProjects.some((p) => p.id === selectedExportProjectId)
    ) {
      return selectedExportProjectId;
    }
    if (activeProjectId && activeProjects.some((p) => p.id === activeProjectId)) {
      return activeProjectId;
    }
    return activeProjects[0]?.id ?? '';
  }, [selectedExportProjectId, activeProjects, activeProjectId]);

  const visibleSavedTemplates = useMemo(
    () =>
      savedTemplates
        .filter((tpl) => !tpl.isDeleted)
        .sort((a, b) => a.sortOrder - b.sortOrder || b.updatedAt - a.updatedAt),
    [savedTemplates]
  );

  const parsedOutline = useMemo(() => {
    if (!outlineText.trim()) return null;
    return parseProjectOutlineInput(outlineText, t.entityNewProject);
  }, [outlineText, t.entityNewProject]);

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

  const handleExternalFileSelected = async (file: File | null | undefined) => {
    if (!file || busy) return;
    setBusy(true);
    try {
      const buf = await file.arrayBuffer();
      const parsed = await importExternalProjectAuto(new Uint8Array(buf), file.name);
      const res = await localDb.importExternalProject(parsed, selectedAreaId);
      onToast(`✓ ${t.externalImportSuccessTitle(res.title, res.taskCount)}`);
      onCreatedProject(res.projectId);
      onClose();
    } catch (err) {
      onToast(err instanceof Error ? err.message : 'Import failed');
    } finally {
      setBusy(false);
    }
  };

  const handleExternalExport = async (format: ExternalExportFormat) => {
    if (!effectiveExportProjectId || busy) return;
    setBusy(true);
    try {
      const res = await localDb.exportProjectExternal(effectiveExportProjectId, format);
      const blobBytes = new Uint8Array(res.bytes.byteLength);
      blobBytes.set(res.bytes);
      const blob = new Blob([blobBytes], { type: res.mimeType });
      const url = URL.createObjectURL(blob);
      const link = document.createElement('a');
      link.href = url;
      link.download = res.filename;
      document.body.appendChild(link);
      link.click();
      document.body.removeChild(link);
      setTimeout(() => URL.revokeObjectURL(url), 1000);
      onToast(`✓ ${t.externalExportSuccessTitle(res.projectTitle)}`);
    } catch (err) {
      onToast(err instanceof Error ? err.message : 'Export failed');
    } finally {
      setBusy(false);
    }
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
              <ClipboardList size={19} style={{ color: 'var(--things-blue)', flexShrink: 0 }} />
              <span>{t.templatesModalTitle}</span>
            </h3>
            <div style={{ fontSize: 12.2, color: 'var(--text-secondary)', marginTop: 2 }}>
              {t.templatesModalSubtitle}
            </div>
          </div>
          <button type="button" className="icon-btn" title={t.actionDone} onClick={onClose}>
            <X size={18} />
          </button>
        </div>

        {/* Segmented Tabs: Gallery | Import Markdown / Outline | Asana & Planner */}
        <div
          className="v2-segmented-bar"
          style={{ marginBottom: 12, flexWrap: 'wrap' }}
        >
          <button
            type="button"
            data-testid="templates-tab-gallery"
            className={`v2-segmented-btn ${tab === 'gallery' ? 'active' : ''}`}
            style={{ whiteSpace: 'normal' }}
            onClick={() => setTab('gallery')}
          >
            <ClipboardList size={14} style={{ flexShrink: 0 }} />
            <span>{t.templatesTabGallery}</span>
          </button>
          <button
            type="button"
            data-testid="templates-tab-import"
            className={`v2-segmented-btn ${tab === 'import' ? 'active' : ''}`}
            style={{ whiteSpace: 'normal' }}
            onClick={() => setTab('import')}
          >
            <FileCode2 size={14} style={{ flexShrink: 0 }} />
            <span>{t.templatesTabImport}</span>
          </button>
          <button
            type="button"
            data-testid="templates-tab-external"
            className={`v2-segmented-btn ${tab === 'external' ? 'active' : ''}`}
            style={{ whiteSpace: 'normal' }}
            onClick={() => setTab('external')}
          >
            <ArrowLeftRight size={14} style={{ flexShrink: 0 }} />
            <span>{t.externalImportExportTab}</span>
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
                  <Folder size={11.5} />
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
              {starterTemplates.map((starter) => {
                const titleStr =
                  starter.defaultTitle ||
                  (t[starter.titleKey as keyof TranslationStrings] as string);
                const notesStr =
                  starter.defaultNotes ||
                  (t[starter.descKey as keyof TranslationStrings] as string);
                const counts = countBlueprintItems(starter.blueprint);
                const headingsPreview = starter.blueprint.headings
                  .map((hd) => hd.title)
                  .join(' · ');

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
                    <p className="v2-template-desc">{notesStr}</p>
                    {headingsPreview && (
                      <div
                        style={{
                          fontSize: 11.2,
                          color: 'var(--text-muted)',
                          whiteSpace: 'nowrap',
                          overflow: 'hidden',
                          textOverflow: 'ellipsis'
                        }}
                        title={headingsPreview}
                      >
                        {headingsPreview}
                      </div>
                    )}
                    <button
                      type="button"
                      className="btn-primary"
                      style={{
                        width: '100%',
                        justifyContent: 'center',
                        whiteSpace: 'normal',
                        height: 'auto'
                      }}
                      disabled={busy}
                      onClick={() =>
                        void handleInstantiateBlueprint({
                          title: titleStr,
                          notes: notesStr,
                          areaId: selectedAreaId,
                          blueprint: starter.blueprint
                        })
                      }
                    >
                      <FolderPlus size={14} style={{ flexShrink: 0 }} />
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
                        style={{
                          width: '100%',
                          justifyContent: 'center',
                          marginTop: 'auto',
                          whiteSpace: 'normal',
                          height: 'auto'
                        }}
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
                        <FolderPlus size={14} style={{ flexShrink: 0 }} />
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
                data-testid="templates-load-sample-btn"
                onClick={() => setOutlineText(sampleMarkdownOutline)}
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
              placeholder={sampleMarkdownOutline}
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
                flexWrap: 'wrap',
                gap: 8,
                marginTop: 14
              }}
            >
              <button
                type="button"
                className="btn-secondary"
                style={{ whiteSpace: 'normal', height: 'auto' }}
                onClick={onClose}
              >
                {t.actionCancel}
              </button>
              <button
                type="button"
                className="btn-primary"
                data-testid="create-from-outline-btn"
                style={{ whiteSpace: 'normal', height: 'auto' }}
                disabled={
                  busy ||
                  !parsedOutline ||
                  (parsedStats != null &&
                    parsedStats.tasksCount === 0 &&
                    parsedStats.headingsCount === 0)
                }
                onClick={() => void handleImportCreate()}
              >
                <FolderPlus size={15} style={{ flexShrink: 0 }} />
                <span>{t.templatesImportCreateBtn}</span>
              </button>
            </div>
          </div>
        )}

        {/* TAB 3: ASANA & MICROSOFT PLANNER IMPORT / EXPORT */}
        {tab === 'external' && (
          <div className="v2-templates-scroll v3-external-stack">
            {/* Import Section */}
            <div className="v3-external-bento-card">
              <div className="v2-section-label" style={{ marginBottom: 0 }}>
                {t.externalImportSection}
              </div>
              <p className="v3-external-desc">{t.externalImportDesc}</p>

              <input
                ref={asanaFileInputRef}
                type="file"
                data-testid="import-asana-file-input"
                accept=".csv,.json,text/csv,application/json"
                style={{ display: 'none' }}
                onChange={(e) => {
                  const file = e.target.files?.[0];
                  e.target.value = '';
                  void handleExternalFileSelected(file);
                }}
              />
              <input
                ref={plannerFileInputRef}
                type="file"
                data-testid="import-planner-file-input"
                accept=".xlsx,.csv,application/vnd.openxmlformats-officedocument.spreadsheetml.sheet,text/csv"
                style={{ display: 'none' }}
                onChange={(e) => {
                  const file = e.target.files?.[0];
                  e.target.value = '';
                  void handleExternalFileSelected(file);
                }}
              />

              <div className="v3-external-btn-grid">
                <button
                  type="button"
                  data-testid="import-asana-btn"
                  className="v3-external-action-btn primary"
                  disabled={busy}
                  onClick={() => asanaFileInputRef.current?.click()}
                >
                  <Upload size={15} style={{ flexShrink: 0 }} />
                  <span>{t.importAsanaBtn}</span>
                </button>
                <button
                  type="button"
                  data-testid="import-planner-btn"
                  className="v3-external-action-btn primary"
                  disabled={busy}
                  onClick={() => plannerFileInputRef.current?.click()}
                >
                  <FileSpreadsheet size={15} style={{ flexShrink: 0 }} />
                  <span>{t.importPlannerBtn}</span>
                </button>
              </div>
            </div>

            {/* Export Section */}
            <div className="v3-external-bento-card">
              <div className="v2-section-label" style={{ marginBottom: 0 }}>
                {t.externalExportSection}
              </div>

              {activeProjects.length === 0 ? (
                <div className="v2-templates-empty" data-testid="external-no-projects">
                  {t.externalNoProjects}
                </div>
              ) : (
                <>
                  <div>
                    <label
                      htmlFor="external-export-project-select"
                      style={{
                        display: 'block',
                        fontSize: 12,
                        fontWeight: 620,
                        color: 'var(--text-secondary)',
                        marginBottom: 5
                      }}
                    >
                      {t.externalSelectProject}
                    </label>
                    <select
                      id="external-export-project-select"
                      data-testid="external-export-project-select"
                      className="text-field"
                      value={effectiveExportProjectId}
                      disabled={busy}
                      onChange={(e) => setSelectedExportProjectId(e.target.value)}
                    >
                      {activeProjects.map((proj) => (
                        <option key={proj.id} value={proj.id}>
                          {proj.title}
                        </option>
                      ))}
                    </select>
                  </div>

                  <div className="v3-external-btn-grid">
                    <button
                      type="button"
                      data-testid="export-asana-csv-btn"
                      className="v3-external-action-btn"
                      disabled={busy || !effectiveExportProjectId}
                      onClick={() => void handleExternalExport('asana-csv')}
                    >
                      <Download size={15} style={{ flexShrink: 0, color: 'var(--things-blue)' }} />
                      <span>{t.exportAsanaCsvBtn}</span>
                    </button>
                    <button
                      type="button"
                      data-testid="export-asana-json-btn"
                      className="v3-external-action-btn"
                      disabled={busy || !effectiveExportProjectId}
                      onClick={() => void handleExternalExport('asana-json')}
                    >
                      <FileCode2 size={15} style={{ flexShrink: 0, color: 'var(--things-blue)' }} />
                      <span>{t.exportAsanaJsonBtn}</span>
                    </button>
                    <button
                      type="button"
                      data-testid="export-planner-xlsx-btn"
                      className="v3-external-action-btn"
                      disabled={busy || !effectiveExportProjectId}
                      onClick={() => void handleExternalExport('planner-xlsx')}
                    >
                      <FileSpreadsheet
                        size={15}
                        style={{ flexShrink: 0, color: 'var(--things-blue)' }}
                      />
                      <span>{t.exportPlannerXlsxBtn}</span>
                    </button>
                    <button
                      type="button"
                      data-testid="export-planner-csv-btn"
                      className="v3-external-action-btn"
                      disabled={busy || !effectiveExportProjectId}
                      onClick={() => void handleExternalExport('planner-csv')}
                    >
                      <Download size={15} style={{ flexShrink: 0, color: 'var(--things-blue)' }} />
                      <span>{t.exportPlannerCsvBtn}</span>
                    </button>
                  </div>
                </>
              )}
            </div>
          </div>
        )}
      </div>
    </div>
  );
};

