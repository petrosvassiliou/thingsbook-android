import React, { useMemo, useState } from 'react';
import {
  AlertTriangle,
  ArrowDown,
  ArrowUp,
  Bell,
  Check,
  Edit3,
  Flag,
  Folder,
  FolderInput,
  GitMerge,
  Inbox,
  LayoutGrid,
  Link2,
  Minus,
  Plus,
  Search,
  SlidersHorizontal,
  Trash2,
  X
} from 'lucide-react';
import {
  MAX_AREAS,
  fromIsoDateInputValue,
  toIsoDateInputValue,
  type AppPreferences,
  type AreaRecord,
  type EffortLevel,
  type HeadingRecord,
  type ProjectRecord,
  type SavedPerspective,
  type TagRecord,
  type TagSortMode,
  type TaskRecord
} from '../domain/types';
import {
  formatStandaloneDuration,
  sortTagsForMode,
  wouldCreateDependencyCycle
} from '../domain/v2Utils';
import { localDb } from '../data/localDb';
import {
  ensureNotificationPermission,
  sendTestChromeOsNotification
} from '../data/reminderRuntime';
import type { TranslationStrings } from '../i18n/translations';

// ==================== 1. AreaManagerModal ====================

interface AreaManagerModalProps {
  areas: AreaRecord[];
  t: TranslationStrings;
  onOpenArea: (areaId: string) => void;
  onClose: () => void;
}

export const AreaManagerModal: React.FC<AreaManagerModalProps> = ({
  areas,
  t,
  onOpenArea,
  onClose
}) => {
  const [newTitle, setNewTitle] = useState('');
  const [editingId, setEditingId] = useState<string | null>(null);
  const [editingText, setEditingText] = useState('');

  const activeAreas = areas.filter((a) => !a.isDeleted);
  const canAddMore = activeAreas.length < MAX_AREAS;
  const existingLower = new Set(activeAreas.map((a) => a.title.toLowerCase()));
  const availableSuggestions = t.areaSuggestions.filter((s) => !existingLower.has(s.toLowerCase()));

  const handleCreate = async (title: string) => {
    const clean = title.trim();
    if (!clean || !canAddMore) return;
    await localDb.upsertArea({ title: clean });
    setNewTitle('');
  };

  return (
    <div className="modal-backdrop" onClick={onClose}>
      <div className="modal-panel" onClick={(e) => e.stopPropagation()}>
        <div className="modal-header">
          <h3 className="modal-title" style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
            <Folder size={18} style={{ color: 'var(--things-blue)' }} />
            <span>
              {t.areasManageTitle} ({activeAreas.length} / {MAX_AREAS})
            </span>
          </h3>
          <button type="button" className="icon-btn" title={t.actionDone} onClick={onClose}>
            <X size={18} />
          </button>
        </div>

        {/* Existing Areas List */}
        <div style={{ display: 'flex', flexDirection: 'column', gap: 6, marginBottom: 14 }}>
          {activeAreas.map((area, idx) => {
            const isEditing = editingId === area.id;
            return (
              <div
                key={area.id}
                style={{
                  display: 'flex',
                  alignItems: 'center',
                  gap: 6,
                  padding: '7px 10px',
                  borderRadius: 'var(--radius-sm)',
                  background: 'var(--bg-subtle)',
                  border: '1px solid var(--border-subtle)'
                }}
              >
                <Folder size={15} style={{ color: 'var(--things-blue)', flexShrink: 0 }} />
                {isEditing ? (
                  <input
                    type="text"
                    className="text-field"
                    style={{ padding: '4px 8px', flex: 1 }}
                    autoFocus
                    value={editingText}
                    onChange={(e) => setEditingText(e.target.value)}
                    onKeyDown={async (e) => {
                      if (e.key === 'Enter' && editingText.trim()) {
                        e.preventDefault();
                        await localDb.upsertArea({ id: area.id, title: editingText.trim() });
                        setEditingId(null);
                      } else if (e.key === 'Escape') {
                        e.stopPropagation();
                        setEditingId(null);
                      }
                    }}
                  />
                ) : (
                  <button
                    type="button"
                    style={{ flex: 1, textAlign: 'left', fontWeight: 600 }}
                    onClick={() => {
                      onOpenArea(area.id);
                      onClose();
                    }}
                  >
                    {area.title}
                  </button>
                )}

                {isEditing ? (
                  <button
                    type="button"
                    className="icon-btn active"
                    title={t.actionSave}
                    aria-label={t.actionSave}
                    onClick={async () => {
                      if (editingText.trim()) {
                        await localDb.upsertArea({ id: area.id, title: editingText.trim() });
                      }
                      setEditingId(null);
                    }}
                  >
                    <Check size={14} />
                  </button>
                ) : (
                  <>
                    <button
                      type="button"
                      className="icon-btn"
                      title={t.actionMove}
                      aria-label={t.actionMove}
                      disabled={idx === 0}
                      onClick={() => localDb.moveAreaOrder(area.id, 'up')}
                    >
                      <ArrowUp size={14} />
                    </button>
                    <button
                      type="button"
                      className="icon-btn"
                      title={t.actionMove}
                      aria-label={t.actionMove}
                      disabled={idx === activeAreas.length - 1}
                      onClick={() => localDb.moveAreaOrder(area.id, 'down')}
                    >
                      <ArrowDown size={14} />
                    </button>
                    <button
                      type="button"
                      className="icon-btn"
                      title={t.actionRename}
                      aria-label={t.actionRename}
                      onClick={() => {
                        setEditingId(area.id);
                        setEditingText(area.title);
                      }}
                    >
                      <Edit3 size={14} />
                    </button>
                    <button
                      type="button"
                      className="icon-btn"
                      title={t.actionDelete}
                      aria-label={t.actionDelete}
                      style={{ color: 'var(--deadline-red)' }}
                      onClick={() => localDb.deleteArea(area.id)}
                    >
                      <Trash2 size={14} />
                    </button>
                  </>
                )}
              </div>
            );
          })}
        </div>

        {/* Create New Area + 1-Tap Suggestion Chips */}
        {canAddMore && (
          <>
            <form
              onSubmit={(e) => {
                e.preventDefault();
                void handleCreate(newTitle);
              }}
              style={{ display: 'flex', gap: 8, marginBottom: 10 }}
            >
              <input
                type="text"
                className="text-field"
                value={newTitle}
                onChange={(e) => setNewTitle(e.target.value)}
                placeholder={t.entityNewArea}
              />
              <button type="submit" className="btn-primary" disabled={!newTitle.trim()}>
                <Plus size={15} />
                <span>{t.actionSave}</span>
              </button>
            </form>

            {availableSuggestions.length > 0 && (
              <div style={{ display: 'flex', flexWrap: 'wrap', gap: 6 }}>
                {availableSuggestions.map((sug) => (
                  <button
                    key={sug}
                    type="button"
                    className="chip-btn"
                    onClick={() => handleCreate(sug)}
                  >
                    <Plus size={12} />
                    <span>{sug}</span>
                  </button>
                ))}
              </div>
            )}
          </>
        )}
      </div>
    </div>
  );
};

// ==================== 2. QuickCreateAreaModal ====================

interface QuickCreateAreaModalProps {
  areasCount: number;
  t: TranslationStrings;
  onCreated: (areaId: string) => void;
  onClose: () => void;
}

export const QuickCreateAreaModal: React.FC<QuickCreateAreaModalProps> = ({
  areasCount,
  t,
  onCreated,
  onClose
}) => {
  const [title, setTitle] = useState('');

  const submit = async (val: string) => {
    const clean = val.trim();
    if (!clean || areasCount >= MAX_AREAS) return;
    const created = await localDb.upsertArea({ title: clean });
    if (created) {
      onCreated(created.id);
    }
    onClose();
  };

  return (
    <div className="modal-backdrop" onClick={onClose}>
      <div className="modal-panel" onClick={(e) => e.stopPropagation()}>
        <div className="modal-header">
          <h3 className="modal-title">
            {t.entityNewArea} ({areasCount}/{MAX_AREAS})
          </h3>
          <button type="button" className="icon-btn" title={t.actionDone} onClick={onClose}>
            <X size={18} />
          </button>
        </div>

        <form
          onSubmit={(e) => {
            e.preventDefault();
            void submit(title);
          }}
        >
          <input
            type="text"
            className="text-field"
            autoFocus
            value={title}
            onChange={(e) => setTitle(e.target.value)}
            placeholder={t.entityNewArea}
            style={{ marginBottom: 10 }}
          />

          <div style={{ display: 'flex', flexWrap: 'wrap', gap: 6, marginBottom: 14 }}>
            {t.areaSuggestions.map((s) => (
              <button key={s} type="button" className="chip-btn" onClick={() => submit(s)}>
                + {s}
              </button>
            ))}
          </div>

          <div style={{ display: 'flex', justifyContent: 'flex-end', gap: 8 }}>
            <button type="button" className="btn-secondary" onClick={onClose}>
              {t.actionCancel}
            </button>
            <button type="submit" className="btn-primary" disabled={!title.trim()}>
              {t.actionSave}
            </button>
          </div>
        </form>
      </div>
    </div>
  );
};

// ==================== 3. CreateOrEditProjectModal ====================

interface CreateOrEditProjectModalProps {
  project?: ProjectRecord | null;
  defaultAreaId?: string | null;
  areas: AreaRecord[];
  t: TranslationStrings;
  onSaved: (projectId: string) => void;
  onOpenTemplates?: (areaId: string | null) => void;
  onClose: () => void;
}

export const CreateOrEditProjectModal: React.FC<CreateOrEditProjectModalProps> = ({
  project,
  defaultAreaId,
  areas,
  t,
  onSaved,
  onOpenTemplates,
  onClose
}) => {
  const activeAreas = areas.filter((a) => !a.isDeleted);
  const [title, setTitle] = useState(project?.title || '');
  const [notes, setNotes] = useState(project?.notes || '');
  const [areaId, setAreaId] = useState<string | null>(
    project ? project.areaId : (defaultAreaId ?? activeAreas[0]?.id ?? null)
  );
  const [deadlineStr, setDeadlineStr] = useState(() =>
    toIsoDateInputValue(project?.deadline ?? null)
  );

  const handleSave = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!title.trim()) return;
    const saved = await localDb.upsertProject({
      id: project?.id,
      title: title.trim(),
      notes,
      areaId,
      deadline: deadlineStr ? fromIsoDateInputValue(deadlineStr) : null
    });
    onSaved(saved.id);
    onClose();
  };

  return (
    <div className="modal-backdrop" onClick={onClose}>
      <div className="modal-panel" onClick={(e) => e.stopPropagation()}>
        <div className="modal-header">
          <h3 className="modal-title" style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
            <Folder size={18} style={{ color: 'var(--things-blue)' }} />
            <span>{project ? t.actionEdit : t.entityNewProject}</span>
          </h3>
          <div style={{ display: 'flex', alignItems: 'center', gap: 6 }}>
            {!project && onOpenTemplates && (
              <button
                type="button"
                className="chip-btn active"
                onClick={() => {
                  onClose();
                  onOpenTemplates(areaId);
                }}
              >
                <span>📋 {t.templatesBtn}</span>
              </button>
            )}
            <button type="button" className="icon-btn" title={t.actionDone} onClick={onClose}>
              <X size={18} />
            </button>
          </div>
        </div>

        <form onSubmit={handleSave}>
          <input
            type="text"
            className="text-field"
            autoFocus
            value={title}
            onChange={(e) => setTitle(e.target.value)}
            placeholder={t.entityNewProject}
            style={{ marginBottom: 10, fontWeight: 600 }}
          />
          <textarea
            className="text-field"
            rows={3}
            value={notes}
            onChange={(e) => setNotes(e.target.value)}
            placeholder={t.placeholderNotes}
            style={{ marginBottom: 12 }}
          />

          {/* Area Filter Chips */}
          <div style={{ marginBottom: 12 }}>
            <div style={{ fontSize: 12, fontWeight: 600, color: 'var(--text-secondary)', marginBottom: 6 }}>
              {t.entityArea}
            </div>
            <div style={{ display: 'flex', flexWrap: 'wrap', gap: 6 }}>
              <button
                type="button"
                className={`chip-btn ${areaId === null ? 'active' : ''}`}
                onClick={() => setAreaId(null)}
              >
                —
              </button>
              {activeAreas.map((a) => (
                <button
                  key={a.id}
                  type="button"
                  className={`chip-btn ${areaId === a.id ? 'active' : ''}`}
                  onClick={() => setAreaId(a.id)}
                >
                  <Folder size={12} />
                  <span>{a.title}</span>
                </button>
              ))}
            </div>
          </div>

          {/* Target Date */}
          <div style={{ marginBottom: 16 }}>
            <div style={{ fontSize: 12, fontWeight: 600, color: 'var(--text-secondary)', marginBottom: 6 }}>
              <Flag size={12} style={{ verticalAlign: 'middle', marginRight: 4, color: 'var(--deadline-red)' }} />
              {t.targetDate}
            </div>
            <div style={{ display: 'flex', gap: 8 }}>
              <input
                type="date"
                className="text-field"
                value={deadlineStr}
                onChange={(e) => setDeadlineStr(e.target.value)}
              />
              {deadlineStr && (
                <button
                  type="button"
                  className="btn-secondary"
                  onClick={() => setDeadlineStr('')}
                >
                  {t.actionClear}
                </button>
              )}
            </div>
          </div>

          <div style={{ display: 'flex', justifyContent: 'flex-end', gap: 8 }}>
            <button type="button" className="btn-secondary" onClick={onClose}>
              {t.actionCancel}
            </button>
            <button type="submit" className="btn-primary" disabled={!title.trim()}>
              {t.actionSave}
            </button>
          </div>
        </form>
      </div>
    </div>
  );
};

// ==================== 4. MoveTaskModal (Hierarchical: None -> Areas -> Projects -> ↳ Headings) ====================

interface MoveTaskModalProps {
  taskIds: string[];
  areas: AreaRecord[];
  projects: ProjectRecord[];
  headings: HeadingRecord[];
  t: TranslationStrings;
  onMoved: () => void;
  onClose: () => void;
}

export const MoveTaskModal: React.FC<MoveTaskModalProps> = ({
  taskIds,
  areas,
  projects,
  headings,
  t,
  onMoved,
  onClose
}) => {
  const activeAreas = areas.filter((a) => !a.isDeleted);
  const activeProjects = projects.filter((p) => !p.isDeleted && p.status === 'ACTIVE');
  const activeHeadings = headings.filter((h) => !h.isDeleted && !h.isArchived);

  const moveTo = async (target: {
    projectId: string | null;
    headingId: string | null;
    areaId: string | null;
  }) => {
    await localDb.batchUpdateTasks(taskIds, target);
    onMoved();
    onClose();
  };

  const renderProjectTree = (proj: ProjectRecord, indentPx: number) => {
    const projHeadings = activeHeadings.filter((h) => h.projectId === proj.id);
    return (
      <div key={proj.id}>
        <button
          type="button"
          className="dropdown-item"
          style={{ paddingLeft: indentPx, fontWeight: 560 }}
          onClick={() => moveTo({ projectId: proj.id, headingId: null, areaId: null })}
        >
          <span style={{ display: 'inline-flex', alignItems: 'center', gap: 8 }}>
            <Folder size={14.5} style={{ color: 'var(--things-blue)' }} />
            <span>{proj.title}</span>
          </span>
        </button>
        {projHeadings.map((hd) => (
          <button
            key={hd.id}
            type="button"
            className="dropdown-item"
            style={{ paddingLeft: indentPx + 20, fontSize: 12.6, color: 'var(--text-secondary)' }}
            onClick={() => moveTo({ projectId: proj.id, headingId: hd.id, areaId: null })}
          >
            <span>↳ {hd.title || t.entityHeading}</span>
          </button>
        ))}
      </div>
    );
  };

  const unassignedProjects = activeProjects.filter(
    (p) => !p.areaId || !activeAreas.some((a) => a.id === p.areaId)
  );

  return (
    <div className="modal-backdrop" onClick={onClose}>
      <div className="modal-panel" onClick={(e) => e.stopPropagation()}>
        <div className="modal-header">
          <h3 className="modal-title" style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
            <FolderInput size={18} style={{ color: 'var(--things-blue)' }} />
            <span>{t.ctxMoveContainer}</span>
          </h3>
          <button type="button" className="icon-btn" title={t.actionDone} onClick={onClose}>
            <X size={18} />
          </button>
        </div>

        <div style={{ display: 'flex', flexDirection: 'column', gap: 2, maxHeight: '62vh', overflowY: 'auto' }}>
          {/* No Project / No Area */}
          <button
            type="button"
            className="dropdown-item"
            onClick={() => moveTo({ projectId: null, headingId: null, areaId: null })}
          >
            <span style={{ display: 'inline-flex', alignItems: 'center', gap: 8 }}>
              <Inbox size={15} style={{ color: 'var(--text-secondary)' }} />
              <span>{t.noProjectNoArea}</span>
            </span>
          </button>

          <div className="context-menu-divider" />

          {activeAreas.map((area) => {
            const areaProjs = activeProjects.filter((p) => p.areaId === area.id);
            return (
              <div key={area.id} style={{ marginBottom: 4 }}>
                <button
                  type="button"
                  className="dropdown-item"
                  style={{ fontWeight: 650 }}
                  onClick={() => moveTo({ projectId: null, headingId: null, areaId: area.id })}
                >
                  <span style={{ display: 'inline-flex', alignItems: 'center', gap: 8 }}>
                    <Folder size={15} style={{ color: 'var(--things-blue)' }} />
                    <span>{area.title}</span>
                  </span>
                </button>
                {areaProjs.map((proj) => renderProjectTree(proj, 26))}
              </div>
            );
          })}

          {unassignedProjects.map((proj) => renderProjectTree(proj, 10))}
        </div>
      </div>
    </div>
  );
};

// ==================== 5. TagManagerModal ====================

interface TagManagerModalProps {
  tags: TagRecord[];
  usageCounts: ReadonlyMap<string, number>;
  tagSortMode: TagSortMode;
  t: TranslationStrings;
  onChangeTagSortMode: (mode: TagSortMode) => void;
  onToast: (msg: string) => void;
  onClose: () => void;
}

export const TagManagerModal: React.FC<TagManagerModalProps> = ({
  tags,
  usageCounts,
  tagSortMode,
  t,
  onChangeTagSortMode,
  onToast,
  onClose
}) => {
  const [title, setTitle] = useState('');
  const [shortcut, setShortcut] = useState('');
  const [filterQuery, setFilterQuery] = useState('');
  const [editingId, setEditingId] = useState<string | null>(null);
  const [editTitle, setEditTitle] = useState('');
  const [editShortcut, setEditShortcut] = useState('');
  const [mergingId, setMergingId] = useState<string | null>(null);
  const [mergeTargetId, setMergeTargetId] = useState<string>('');

  const activeTags = tags.filter((tg) => !tg.isDeleted);
  const sortedTags = sortTagsForMode(activeTags, tagSortMode, usageCounts);
  const normalizedFilter = filterQuery.trim().toLowerCase();
  const filteredTags = normalizedFilter
    ? sortedTags.filter(
        (tg) =>
          tg.title.toLowerCase().includes(normalizedFilter) ||
          (tg.shortcut || '').toLowerCase().includes(normalizedFilter)
      )
    : sortedTags;

  const handleCreate = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!title.trim()) return;
    await localDb.upsertTag({
      title: title.trim(),
      shortcut: shortcut.trim().toUpperCase().slice(0, 1) || null
    });
    setTitle('');
    setShortcut('');
  };

  const commitEdit = async (tg: TagRecord) => {
    const trimmed = editTitle.trim();
    if (trimmed) {
      const updated = await localDb.upsertTag({
        id: tg.id,
        title: trimmed,
        shortcut: editShortcut.trim().toUpperCase().slice(0, 1) || null
      });
      if (updated.id !== tg.id) {
        onToast(t.tagsMergedToast(tg.title, updated.title));
      }
    }
    setEditingId(null);
  };

  const commitMerge = async (sourceTag: TagRecord) => {
    if (!mergeTargetId || mergeTargetId === sourceTag.id) return;
    const targetTag = activeTags.find((tg) => tg.id === mergeTargetId);
    if (!targetTag) return;
    const ok = await localDb.mergeTags(sourceTag.id, targetTag.id);
    if (ok) {
      onToast(t.tagsMergedToast(sourceTag.title, targetTag.title));
    }
    setMergingId(null);
    setMergeTargetId('');
  };

  return (
    <div className="modal-backdrop" onClick={onClose}>
      <div className="modal-panel" onClick={(e) => e.stopPropagation()}>
        <div className="modal-header">
          <h3 className="modal-title" style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
            <LayoutGrid size={18} style={{ color: 'var(--things-blue)' }} />
            <span>{t.tagsManageTitle}</span>
            {activeTags.length > 0 && (
              <span
                style={{
                  fontSize: 12,
                  fontWeight: 600,
                  color: 'var(--text-tertiary)',
                  padding: '2px 7px',
                  borderRadius: 999,
                  background: 'var(--bg-subtle)'
                }}
              >
                {normalizedFilter ? `${filteredTags.length}/${activeTags.length}` : activeTags.length}
              </span>
            )}
          </h3>
          <button type="button" className="icon-btn" title={t.actionDone} onClick={onClose}>
            <X size={18} />
          </button>
        </div>

        <form onSubmit={handleCreate} style={{ display: 'flex', gap: 8, marginBottom: 10 }}>
          <input
            type="text"
            className="text-field"
            style={{ flex: 1 }}
            value={title}
            onChange={(e) => setTitle(e.target.value)}
            placeholder={t.tagsNewPlaceholder}
          />
          <input
            type="text"
            className="text-field"
            style={{ width: 104 }}
            maxLength={1}
            value={shortcut}
            onChange={(e) => setShortcut(e.target.value.toUpperCase().slice(0, 1))}
            placeholder={t.tagsShortcutLabel}
          />
          <button type="submit" className="btn-primary" disabled={!title.trim()}>
            {t.tagsAddBtn}
          </button>
        </form>

        <div
          style={{
            display: 'flex',
            alignItems: 'center',
            gap: 8,
            marginBottom: 12,
            flexWrap: 'wrap',
            opacity: activeTags.length === 0 ? 0.55 : 1
          }}
        >
          <div style={{ position: 'relative', flex: '1 1 160px', display: 'flex', alignItems: 'center' }}>
            <Search
              size={14}
              style={{
                position: 'absolute',
                left: 11,
                color: 'var(--text-tertiary)',
                pointerEvents: 'none'
              }}
            />
            <input
              type="text"
              className="text-field"
              style={{ width: '100%', paddingLeft: 32, paddingRight: filterQuery ? 30 : 10 }}
              value={filterQuery}
              disabled={activeTags.length === 0}
              onChange={(e) => setFilterQuery(e.target.value)}
              placeholder={t.tagsSearchPlaceholder}
            />
            {filterQuery && (
              <button
                type="button"
                className="icon-btn"
                style={{ position: 'absolute', right: 4, width: 24, height: 24 }}
                onClick={() => setFilterQuery('')}
                title={t.actionClear}
                aria-label={t.actionClear}
              >
                <X size={13} />
              </button>
            )}
          </div>

          <div
            className="v2-segmented-toggle"
            role="group"
            aria-label={t.viewSortLabel}
            data-testid="tags-sort-segmented"
          >
            {(
              [
                ['alpha', t.tagsSortAlpha],
                ['most_used', t.tagsSortMostUsed],
                ['manual', t.tagsSortManual]
              ] as Array<[TagSortMode, string]>
            ).map(([mode, label]) => (
              <button
                key={mode}
                type="button"
                className={`v2-segmented-btn ${tagSortMode === mode ? 'active' : ''}`}
                onClick={() => onChangeTagSortMode(mode)}
              >
                <span>{label}</span>
              </button>
            ))}
          </div>
        </div>

        <div
          style={{
            display: 'flex',
            flexDirection: 'column',
            gap: 6,
            height: 'min(250px, 38vh)',
            minHeight: 'min(250px, 38vh)',
            maxHeight: 'min(250px, 38vh)',
            overflowY: 'auto'
          }}
        >
          {activeTags.length === 0 && (
            <div
              style={{
                padding: '18px 10px',
                textAlign: 'center',
                fontSize: 13,
                color: 'var(--text-tertiary)'
              }}
            >
              {t.emptyView}
            </div>
          )}
          {activeTags.length > 0 && filteredTags.length === 0 && (
            <div
              style={{
                padding: '14px 10px',
                textAlign: 'center',
                fontSize: 13,
                color: 'var(--text-tertiary)'
              }}
            >
              {t.tagsNoMatches}
            </div>
          )}
          {filteredTags.map((tg, idx) => {
            const isEditing = editingId === tg.id;
            const isMerging = mergingId === tg.id;
            const usageCount = usageCounts.get(tg.id) ?? 0;
            const mergeCandidates = sortedTags.filter((other) => other.id !== tg.id);

            return (
              <div
                key={tg.id}
                style={{
                  display: 'flex',
                  flexDirection: 'column',
                  gap: 6,
                  padding: '7px 10px',
                  borderRadius: 'var(--radius-sm)',
                  background: 'var(--bg-subtle)',
                  border: '1px solid var(--border-subtle)'
                }}
              >
                <div style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
                  <LayoutGrid size={14} style={{ color: 'var(--things-blue)', flexShrink: 0 }} />
                  {isEditing ? (
                    <>
                      <input
                        type="text"
                        className="text-field"
                        style={{ flex: 1, padding: '4px 8px' }}
                        autoFocus
                        value={editTitle}
                        onChange={(e) => setEditTitle(e.target.value)}
                        onKeyDown={async (e) => {
                          if (e.key === 'Enter') {
                            e.preventDefault();
                            await commitEdit(tg);
                          } else if (e.key === 'Escape') {
                            e.stopPropagation();
                            setEditingId(null);
                          }
                        }}
                      />
                      <input
                        type="text"
                        className="text-field"
                        style={{ width: 64, padding: '4px 8px' }}
                        maxLength={1}
                        value={editShortcut}
                        onChange={(e) => setEditShortcut(e.target.value.toUpperCase().slice(0, 1))}
                        placeholder={t.tagsShortcutLabel}
                        onKeyDown={async (e) => {
                          if (e.key === 'Enter') {
                            e.preventDefault();
                            await commitEdit(tg);
                          } else if (e.key === 'Escape') {
                            e.stopPropagation();
                            setEditingId(null);
                          }
                        }}
                      />
                      <button
                        type="button"
                        className="icon-btn active"
                        title={t.actionSave}
                        onClick={() => void commitEdit(tg)}
                      >
                        <Check size={14} />
                      </button>
                    </>
                  ) : (
                    <>
                      <span
                        style={{
                          flex: 1,
                          fontWeight: 560,
                          minWidth: 0,
                          overflow: 'hidden',
                          textOverflow: 'ellipsis',
                          whiteSpace: 'nowrap'
                        }}
                      >
                        {tg.title}
                      </span>

                      <span
                        className="category-usage-badge"
                        data-testid={`tag-usage-badge-${tg.id}`}
                        title={`${usageCount}`}
                      >
                        {usageCount}
                      </span>

                      {tg.shortcut && <kbd>Ctrl+{tg.shortcut}</kbd>}

                      {tagSortMode === 'manual' && !normalizedFilter && (
                        <>
                          <button
                            type="button"
                            className="icon-btn"
                            style={{ width: 24, height: 24 }}
                            disabled={idx === 0}
                            title={t.actionMove}
                            onClick={() => void localDb.moveTagOrder(tg.id, 'up')}
                          >
                            <ArrowUp size={13.5} />
                          </button>
                          <button
                            type="button"
                            className="icon-btn"
                            style={{ width: 24, height: 24 }}
                            disabled={idx >= filteredTags.length - 1}
                            title={t.actionMove}
                            onClick={() => void localDb.moveTagOrder(tg.id, 'down')}
                          >
                            <ArrowDown size={13.5} />
                          </button>
                        </>
                      )}

                      {mergeCandidates.length > 0 && (
                        <button
                          type="button"
                          className={`icon-btn ${isMerging ? 'active' : ''}`}
                          title={t.tagsMergeAction}
                          data-testid={`tag-merge-btn-${tg.id}`}
                          onClick={() => {
                            if (isMerging) {
                              setMergingId(null);
                              setMergeTargetId('');
                            } else {
                              setEditingId(null);
                              setMergingId(tg.id);
                              setMergeTargetId(mergeCandidates[0]?.id ?? '');
                            }
                          }}
                        >
                          <GitMerge size={13.5} />
                        </button>
                      )}

                      <button
                        type="button"
                        className="icon-btn"
                        title={t.actionEdit}
                        onClick={() => {
                          setMergingId(null);
                          setEditingId(tg.id);
                          setEditTitle(tg.title);
                          setEditShortcut(tg.shortcut || '');
                        }}
                      >
                        <Edit3 size={14} />
                      </button>
                      <button
                        type="button"
                        className="icon-btn"
                        style={{ color: 'var(--deadline-red)' }}
                        title={t.actionDelete}
                        onClick={() => void localDb.deleteTag(tg.id)}
                      >
                        <Trash2 size={14} />
                      </button>
                    </>
                  )}
                </div>

                {isMerging && mergeCandidates.length > 0 && (
                  <div
                    style={{
                      display: 'flex',
                      alignItems: 'center',
                      gap: 8,
                      paddingTop: 6,
                      borderTop: '1px solid var(--border-subtle)'
                    }}
                  >
                    <span style={{ fontSize: 12, color: 'var(--text-secondary)', fontWeight: 560 }}>
                      {t.tagsMergeTargetPrompt(tg.title)}
                    </span>
                    <select
                      className="text-field"
                      style={{ flex: 1, padding: '4px 8px', fontSize: 12.5 }}
                      value={mergeTargetId}
                      onChange={(e) => setMergeTargetId(e.target.value)}
                    >
                      {mergeCandidates.map((cand) => (
                        <option key={cand.id} value={cand.id}>
                          {cand.title}
                        </option>
                      ))}
                    </select>
                    <button
                      type="button"
                      className="btn-primary"
                      style={{ padding: '4px 10px', fontSize: 12 }}
                      disabled={!mergeTargetId}
                      onClick={() => void commitMerge(tg)}
                    >
                      <Check size={13} />
                    </button>
                    <button
                      type="button"
                      className="icon-btn"
                      style={{ width: 24, height: 24 }}
                      title={t.actionCancel}
                      aria-label={t.actionCancel}
                      onClick={() => {
                        setMergingId(null);
                        setMergeTargetId('');
                      }}
                    >
                      <X size={13} />
                    </button>
                  </div>
                )}
              </div>
            );
          })}
        </div>
      </div>
    </div>
  );
};

// ==================== 6. TaskTagPickerModal ====================

interface TaskTagPickerModalProps {
  selectedTagIds: string[];
  indeterminateTagIds?: string[];
  tags: TagRecord[];
  usageCounts: ReadonlyMap<string, number>;
  tagSortMode: TagSortMode;
  t: TranslationStrings;
  onToggleTag: (tagId: string) => void;
  onOpenManageTags: () => void;
  onClose: () => void;
}

export const TaskTagPickerModal: React.FC<TaskTagPickerModalProps> = ({
  selectedTagIds,
  indeterminateTagIds = [],
  tags,
  usageCounts,
  tagSortMode,
  t,
  onToggleTag,
  onOpenManageTags,
  onClose
}) => {
  const [searchQuery, setSearchQuery] = useState('');
  const activeTags = tags.filter((tg) => !tg.isDeleted);
  const sortedTags = sortTagsForMode(activeTags, tagSortMode, usageCounts);
  const cleanQuery = searchQuery.trim();
  const normalizedQuery = cleanQuery.toLowerCase();
  const filteredTags = normalizedQuery
    ? sortedTags.filter(
        (tg) =>
          tg.title.toLowerCase().includes(normalizedQuery) ||
          (tg.shortcut || '').toLowerCase().includes(normalizedQuery)
      )
    : sortedTags;
  const exactMatch = cleanQuery
    ? activeTags.find((tg) => tg.title.toLowerCase() === normalizedQuery)
    : undefined;

  const createAndAssignFromQuery = async () => {
    if (!cleanQuery) return;
    if (exactMatch) {
      if (!selectedTagIds.includes(exactMatch.id)) {
        onToggleTag(exactMatch.id);
      }
    } else {
      const created = await localDb.upsertTag({ title: cleanQuery });
      onToggleTag(created.id);
    }
    setSearchQuery('');
  };

  const handleSearchSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    await createAndAssignFromQuery();
  };

  return (
    <div className="modal-backdrop" onClick={onClose}>
      <div className="modal-panel" onClick={(e) => e.stopPropagation()}>
        <div className="modal-header">
          <h3 className="modal-title" style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
            <LayoutGrid size={18} style={{ color: 'var(--things-blue)' }} />
            <span>{t.ctxAssignTags}</span>
          </h3>
          <button type="button" className="icon-btn" title={t.actionDone} onClick={onClose}>
            <X size={18} />
          </button>
        </div>

        <form onSubmit={handleSearchSubmit} style={{ display: 'flex', gap: 8, marginBottom: 12 }}>
          <div style={{ position: 'relative', flex: 1, display: 'flex', alignItems: 'center' }}>
            <Search
              size={15}
              style={{
                position: 'absolute',
                left: 11,
                color: 'var(--text-tertiary)',
                pointerEvents: 'none'
              }}
            />
            <input
              type="text"
              className="text-field"
              style={{ width: '100%', paddingLeft: 34, paddingRight: searchQuery ? 30 : 10 }}
              autoFocus
              value={searchQuery}
              onChange={(e) => setSearchQuery(e.target.value)}
              placeholder={t.tagsAssignSearchPlaceholder}
            />
            {searchQuery && (
              <button
                type="button"
                className="icon-btn"
                style={{ position: 'absolute', right: 4, width: 24, height: 24 }}
                onClick={() => setSearchQuery('')}
                title={t.actionClear}
                aria-label={t.actionClear}
              >
                <X size={14} />
              </button>
            )}
          </div>
          <button type="submit" className="btn-primary" title={t.tagsAddBtn} aria-label={t.tagsAddBtn} disabled={!cleanQuery}>
            <Plus size={15} />
          </button>
        </form>

        <div
          style={{
            display: 'flex',
            flexDirection: 'column',
            gap: 4,
            height: 'min(240px, 40vh)',
            minHeight: 'min(240px, 40vh)',
            maxHeight: 'min(240px, 40vh)',
            overflowY: 'auto'
          }}
        >
          {activeTags.length === 0 && !cleanQuery && (
            <div
              style={{
                padding: '16px 10px',
                textAlign: 'center',
                fontSize: 13,
                color: 'var(--text-tertiary)'
              }}
            >
              {t.emptyView}
            </div>
          )}
          {cleanQuery && !exactMatch && (
            <button
              type="button"
              className="dropdown-item"
              style={{
                color: 'var(--things-blue)',
                fontWeight: 600,
                background: 'var(--things-blue-soft)',
                borderRadius: 'var(--radius-sm)'
              }}
              onClick={() => void createAndAssignFromQuery()}
            >
              <span style={{ display: 'inline-flex', alignItems: 'center', gap: 8 }}>
                <Plus size={14} />
                <span>{t.tagsCreateFromSearch(cleanQuery)}</span>
              </span>
            </button>
          )}
          {filteredTags.length === 0 && activeTags.length > 0 && (
            <div
              style={{
                padding: '12px 10px',
                textAlign: 'center',
                fontSize: 13,
                color: 'var(--text-tertiary)'
              }}
            >
              {t.tagsNoMatches}
            </div>
          )}
          {filteredTags.map((tg) => {
            const checked = selectedTagIds.includes(tg.id);
            const indeterminate = !checked && indeterminateTagIds.includes(tg.id);
            const usageCount = usageCounts.get(tg.id) ?? 0;
            return (
              <button
                key={tg.id}
                type="button"
                className="dropdown-item"
                onClick={() => onToggleTag(tg.id)}
              >
                <span style={{ display: 'inline-flex', alignItems: 'center', gap: 9 }}>
                  <span
                    className={`things-checkbox ${
                      checked ? 'completed' : indeterminate ? 'indeterminate' : ''
                    }`}
                    style={{ width: 17, height: 17, minWidth: 17 }}
                  >
                    {checked && <Check size={11} strokeWidth={3} />}
                    {indeterminate && <Minus size={11} strokeWidth={3} />}
                  </span>
                  <span>{tg.title}</span>
                </span>
                <span style={{ display: 'inline-flex', alignItems: 'center', gap: 6 }}>
                  {usageCount > 0 && (
                    <span className="category-usage-badge">{usageCount}</span>
                  )}
                  {tg.shortcut && <kbd>Ctrl+{tg.shortcut}</kbd>}
                </span>
              </button>
            );
          })}
        </div>

        <div
          style={{
            display: 'flex',
            justifyContent: 'space-between',
            marginTop: 14,
            paddingTop: 10,
            borderTop: '1px solid var(--border-subtle)'
          }}
        >
          <button
            type="button"
            className="btn-secondary"
            onClick={() => {
              onClose();
              onOpenManageTags();
            }}
          >
            {t.tagsManageTitle}
          </button>
          <button type="button" className="btn-primary" onClick={onClose}>
            {t.actionDone}
          </button>
        </div>
      </div>
    </div>
  );
};

// ==================== 7. NotificationSettingsModal ====================

interface NotificationSettingsModalProps {
  prefs: AppPreferences;
  t: TranslationStrings;
  onUpdatePrefs: (patch: Partial<AppPreferences>) => void;
  onToast: (msg: string) => void;
  onClose: () => void;
}

const DIGEST_PRESETS: Array<[number, number, string]> = [
  [7, 0, '07:00'],
  [8, 0, '08:00'],
  [8, 30, '08:30'],
  [9, 0, '09:00'],
  [12, 0, '12:00'],
  [18, 0, '18:00']
];

export const NotificationSettingsModal: React.FC<NotificationSettingsModalProps> = ({
  prefs,
  t,
  onUpdatePrefs,
  onToast,
  onClose
}) => {
  const clockStr = `${String(prefs.dailyDigestHour).padStart(2, '0')}:${String(
    prefs.dailyDigestMinute
  ).padStart(2, '0')}`;

  const toggleDigest = async () => {
    const next = !prefs.dailyDigestEnabled;
    if (next) {
      await ensureNotificationPermission();
    }
    onUpdatePrefs({ dailyDigestEnabled: next });
  };

  return (
    <div className="modal-backdrop" onClick={onClose}>
      <div className="modal-panel" onClick={(e) => e.stopPropagation()}>
        <div className="modal-header">
          <h3 className="modal-title" style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
            <Bell size={18} style={{ color: 'var(--things-blue)' }} />
            <span>{t.notificationsTitle}</span>
          </h3>
          <button type="button" className="icon-btn" title={t.actionDone} onClick={onClose}>
            <X size={18} />
          </button>
        </div>

        <div
          style={{
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'space-between',
            padding: '12px 14px',
            borderRadius: 'var(--radius-sm)',
            background: 'var(--bg-subtle)',
            border: '1px solid var(--border-subtle)',
            marginBottom: 14
          }}
        >
          <span style={{ fontWeight: 600, fontSize: 13.8 }}>{t.notificationsDailyTitle}</span>
          <button
            type="button"
            className={`toggle-switch ${prefs.dailyDigestEnabled ? 'on' : ''}`}
            aria-label={t.notificationsDailyTitle}
            onClick={toggleDigest}
            aria-pressed={prefs.dailyDigestEnabled}
          >
            <span className="toggle-switch-knob" />
          </button>
        </div>

        <div style={{ display: 'flex', flexWrap: 'wrap', gap: 6, marginBottom: 12 }}>
          {DIGEST_PRESETS.map(([h, m, label]) => (
            <button
              key={label}
              type="button"
              className={`chip-btn ${
                prefs.dailyDigestHour === h && prefs.dailyDigestMinute === m ? 'active' : ''
              }`}
              onClick={() => onUpdatePrefs({ dailyDigestHour: h, dailyDigestMinute: m })}
            >
              {label}
            </button>
          ))}
        </div>

        <div style={{ marginBottom: 16 }}>
          <input
            type="time"
            className="text-field"
            value={clockStr}
            onChange={(e) => {
              const [hh, mm] = e.target.value.split(':').map(Number);
              if (!Number.isNaN(hh) && !Number.isNaN(mm)) {
                onUpdatePrefs({ dailyDigestHour: hh, dailyDigestMinute: mm });
              }
            }}
          />
        </div>

        <div style={{ display: 'flex', justifyContent: 'space-between', gap: 8 }}>
          <button
            type="button"
            className="btn-secondary"
            onClick={async () => {
              const ok = await sendTestChromeOsNotification(t.appName, prefs.language);
              onToast(ok ? t.notificationsTestSentToast : t.notificationsBlockedToast);
            }}
          >
            <Bell size={14} />
            <span>{t.notificationsTestBtn}</span>
          </button>
          <button type="button" className="btn-primary" onClick={onClose}>
            {t.actionDone}
          </button>
        </div>
      </div>
    </div>
  );
};

// ==================== 8. TaskDependencyModal (Waiting On…) ====================

interface TaskDependencyModalProps {
  task: TaskRecord;
  allTasks: TaskRecord[];
  projectsMap: ReadonlyMap<string, ProjectRecord>;
  t: TranslationStrings;
  onToast: (msg: string) => void;
  onClose: () => void;
}

export const TaskDependencyModal: React.FC<TaskDependencyModalProps> = ({
  task,
  allTasks,
  projectsMap,
  t,
  onToast,
  onClose
}) => {
  const [searchQuery, setSearchQuery] = useState('');
  const currentBlockers = useMemo(
    () => (task.blockedByTaskIds || []).filter((id) => id && id !== task.id),
    [task.blockedByTaskIds, task.id]
  );

  const blockedByMap = useMemo(() => {
    const map = new Map<string, readonly string[]>();
    for (const tk of allTasks) {
      if (tk.isDeleted) continue;
      if (Array.isArray(tk.blockedByTaskIds) && tk.blockedByTaskIds.length > 0) {
        map.set(tk.id, tk.blockedByTaskIds);
      }
    }
    return map;
  }, [allTasks]);

  const candidates = useMemo(() => {
    const selectedSet = new Set(currentBlockers);
    const norm = searchQuery.trim().toLowerCase();
    return allTasks
      .filter((tk) => {
        if (tk.isDeleted || tk.id === task.id) return false;
        if (tk.status !== 'OPEN' && !selectedSet.has(tk.id)) return false;
        if (!norm) return true;
        const projTitle = tk.projectId ? (projectsMap.get(tk.projectId)?.title ?? '') : '';
        return (
          tk.title.toLowerCase().includes(norm) || projTitle.toLowerCase().includes(norm)
        );
      })
      .sort((a, b) => {
        const aSel = selectedSet.has(a.id);
        const bSel = selectedSet.has(b.id);
        if (aSel !== bSel) return aSel ? -1 : 1;
        return a.sortOrder - b.sortOrder || a.createdAt - b.createdAt;
      });
  }, [allTasks, currentBlockers, projectsMap, searchQuery, task.id]);

  const handleToggleBlocker = async (candidateId: string, wouldCycle: boolean) => {
    const exists = currentBlockers.includes(candidateId);
    if (exists) {
      const next = currentBlockers.filter((id) => id !== candidateId);
      await localDb.upsertTask({ id: task.id, blockedByTaskIds: next });
      return;
    }
    if (wouldCycle) {
      onToast(t.dependencyCycleWarning);
      return;
    }
    const next = [...currentBlockers, candidateId];
    await localDb.upsertTask({ id: task.id, blockedByTaskIds: next });
  };

  return (
    <div className="modal-backdrop" onClick={onClose}>
      <div className="modal-panel" onClick={(e) => e.stopPropagation()} data-testid="task-dependency-modal">
        <div className="modal-header">
          <h3 className="modal-title" style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
            <Link2 size={18} style={{ color: 'var(--things-blue)' }} />
            <span>{t.dependencyModalTitle}</span>
            {currentBlockers.length > 0 && (
              <span className="category-usage-badge">{currentBlockers.length}</span>
            )}
          </h3>
          <button type="button" className="icon-btn" title={t.actionDone} onClick={onClose}>
            <X size={18} />
          </button>
        </div>

        <div
          style={{
            fontSize: 12.6,
            color: 'var(--text-secondary)',
            marginBottom: 10,
            overflow: 'hidden',
            textOverflow: 'ellipsis',
            whiteSpace: 'nowrap'
          }}
        >
          {task.title || t.placeholderTodoTitle}
        </div>

        <div style={{ position: 'relative', display: 'flex', alignItems: 'center', marginBottom: 12 }}>
          <Search
            size={15}
            style={{
              position: 'absolute',
              left: 11,
              color: 'var(--text-tertiary)',
              pointerEvents: 'none'
            }}
          />
          <input
            type="text"
            className="text-field"
            style={{ width: '100%', paddingLeft: 34, paddingRight: searchQuery ? 30 : 10 }}
            autoFocus
            value={searchQuery}
            onChange={(e) => setSearchQuery(e.target.value)}
            placeholder={t.dependencySearchPlaceholder}
          />
          {searchQuery && (
            <button
              type="button"
              className="icon-btn"
              style={{ position: 'absolute', right: 4, width: 24, height: 24 }}
              onClick={() => setSearchQuery('')}
              title={t.actionClear}
              aria-label={t.actionClear}
            >
              <X size={14} />
            </button>
          )}
        </div>

        <div
          style={{
            display: 'flex',
            flexDirection: 'column',
            gap: 4,
            height: 'min(260px, 42vh)',
            minHeight: 'min(260px, 42vh)',
            maxHeight: 'min(260px, 42vh)',
            overflowY: 'auto'
          }}
        >
          {candidates.length === 0 ? (
            <div
              style={{
                padding: '18px 10px',
                textAlign: 'center',
                fontSize: 13,
                color: 'var(--text-tertiary)'
              }}
            >
              {t.dependencyEmpty}
            </div>
          ) : (
            candidates.map((cand) => {
              const checked = currentBlockers.includes(cand.id);
              const wouldCycle =
                !checked && wouldCreateDependencyCycle(task.id, cand.id, blockedByMap);
              const projTitle = cand.projectId
                ? (projectsMap.get(cand.projectId)?.title ?? '')
                : '';

              return (
                <button
                  key={cand.id}
                  type="button"
                  className="dropdown-item"
                  disabled={wouldCycle}
                  style={wouldCycle ? { opacity: 0.48, cursor: 'not-allowed' } : undefined}
                  title={wouldCycle ? t.dependencyCycleWarning : cand.title}
                  onClick={() => void handleToggleBlocker(cand.id, wouldCycle)}
                >
                  <span
                    style={{
                      display: 'inline-flex',
                      alignItems: 'center',
                      gap: 9,
                      minWidth: 0,
                      flex: 1
                    }}
                  >
                    <span
                      className={`things-checkbox ${checked ? 'completed' : ''}`}
                      style={{ width: 17, height: 17, minWidth: 17 }}
                    >
                      {checked && <Check size={11} strokeWidth={3} />}
                    </span>
                    <span
                      style={{
                        overflow: 'hidden',
                        textOverflow: 'ellipsis',
                        whiteSpace: 'nowrap',
                        textDecoration: cand.status !== 'OPEN' ? 'line-through' : undefined
                      }}
                    >
                      {cand.title || t.placeholderTodoTitle}
                    </span>
                  </span>
                  <span style={{ display: 'inline-flex', alignItems: 'center', gap: 6, flexShrink: 0 }}>
                    {wouldCycle && (
                      <AlertTriangle size={13} style={{ color: 'var(--deadline-red)' }} />
                    )}
                    {projTitle && (
                      <span
                        style={{
                          fontSize: 11.5,
                          color: 'var(--text-muted)',
                          maxWidth: 120,
                          overflow: 'hidden',
                          textOverflow: 'ellipsis',
                          whiteSpace: 'nowrap'
                        }}
                      >
                        {projTitle}
                      </span>
                    )}
                  </span>
                </button>
              );
            })
          )}
        </div>

        <div
          style={{
            display: 'flex',
            justifyContent: 'space-between',
            alignItems: 'center',
            marginTop: 14,
            paddingTop: 10,
            borderTop: '1px solid var(--border-subtle)'
          }}
        >
          {currentBlockers.length > 0 ? (
            <button
              type="button"
              className="btn-secondary"
              onClick={() => void localDb.upsertTask({ id: task.id, blockedByTaskIds: [] })}
            >
              {t.dependencyClearAll}
            </button>
          ) : (
            <span />
          )}
          <button type="button" className="btn-primary" onClick={onClose}>
            {t.actionDone}
          </button>
        </div>
      </div>
    </div>
  );
};

// ==================== 9. PerspectiveModal ====================

interface PerspectiveModalProps {
  perspective: SavedPerspective | null;
  areas: AreaRecord[];
  tags: TagRecord[];
  nextSortOrder: number;
  t: TranslationStrings;
  onSave: (perspective: SavedPerspective) => void;
  onDelete?: (perspectiveId: string) => void;
  onClose: () => void;
}

const PERSPECTIVE_MAX_DURATION_PRESETS = [5, 15, 30, 60, 120, 240, 480] as const;

export const PerspectiveModal: React.FC<PerspectiveModalProps> = ({
  perspective,
  areas,
  tags,
  nextSortOrder,
  t,
  onSave,
  onDelete,
  onClose
}) => {
  const [title, setTitle] = useState(perspective?.title ?? '');
  const [categoryIds, setCategoryIds] = useState<string[]>(perspective?.categoryIds ?? []);
  const [effortLevel, setEffortLevel] = useState<EffortLevel | null>(
    perspective?.effortLevel ?? null
  );
  const [maxDurationMinutes, setMaxDurationMinutes] = useState<number | null>(
    perspective?.maxDurationMinutes ?? null
  );
  const [dueWindow, setDueWindow] = useState<SavedPerspective['dueWindow']>(
    perspective?.dueWindow ?? 'ANY'
  );
  const [areaId, setAreaId] = useState<string | null>(perspective?.areaId ?? null);
  const [actionableOnly, setActionableOnly] = useState<boolean>(
    perspective ? perspective.actionableOnly : true
  );

  const activeAreas = areas.filter((a) => !a.isDeleted);
  const activeTags = tags.filter((tg) => !tg.isDeleted);

  const toggleCategory = (id: string) => {
    setCategoryIds((prev) =>
      prev.includes(id) ? prev.filter((x) => x !== id) : [...prev, id]
    );
  };

  const handleSubmit = (e: React.FormEvent) => {
    e.preventDefault();
    const trimmed = title.trim();
    if (!trimmed) return;
    onSave({
      id: perspective?.id ?? crypto.randomUUID(),
      title: trimmed,
      categoryIds,
      effortLevel,
      maxDurationMinutes,
      dueWindow,
      areaId,
      actionableOnly,
      sortOrder: perspective?.sortOrder ?? nextSortOrder
    });
  };

  return (
    <div className="modal-backdrop" onClick={onClose}>
      <div
        className="modal-panel"
        style={{ maxWidth: 520 }}
        onClick={(e) => e.stopPropagation()}
        data-testid="perspective-modal"
      >
        <div className="modal-header">
          <h3 className="modal-title" style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
            <SlidersHorizontal size={18} style={{ color: 'var(--things-blue)' }} />
            <span>{perspective ? t.perspectiveEditTitle : t.perspectiveNewTitle}</span>
          </h3>
          <button type="button" className="icon-btn" title={t.actionCancel} onClick={onClose}>
            <X size={18} />
          </button>
        </div>

        <form onSubmit={handleSubmit} style={{ display: 'flex', flexDirection: 'column', gap: 12 }}>
          <input
            type="text"
            className="text-field"
            autoFocus
            value={title}
            onChange={(e) => setTitle(e.target.value)}
            placeholder={t.perspectiveNamePlaceholder}
          />

          {/* Effort Filter */}
          <div>
            <div style={{ fontSize: 12, fontWeight: 600, color: 'var(--text-secondary)', marginBottom: 6 }}>
              {t.effortLabel}
            </div>
            <div style={{ display: 'flex', flexWrap: 'wrap', gap: 6 }}>
              <button
                type="button"
                className={`chip-btn ${effortLevel === null ? 'active' : ''}`}
                onClick={() => setEffortLevel(null)}
              >
                {t.effortNone}
              </button>
              <button
                type="button"
                className={`chip-btn ${effortLevel === 'QUICK_WIN' ? 'active' : ''}`}
                onClick={() => setEffortLevel('QUICK_WIN')}
              >
                ⚡ {t.effortQuickWin}
              </button>
              <button
                type="button"
                className={`chip-btn ${effortLevel === 'MEDIUM' ? 'active' : ''}`}
                onClick={() => setEffortLevel('MEDIUM')}
              >
                {t.effortMedium}
              </button>
              <button
                type="button"
                className={`chip-btn ${effortLevel === 'DEEP_FOCUS' ? 'active' : ''}`}
                onClick={() => setEffortLevel('DEEP_FOCUS')}
              >
                🧠 {t.effortDeepFocus}
              </button>
            </div>
          </div>

          {/* Max Duration Filter */}
          <div>
            <div style={{ fontSize: 12, fontWeight: 600, color: 'var(--text-secondary)', marginBottom: 6 }}>
              {t.perspectiveMaxDurationLabel}
            </div>
            <div style={{ display: 'flex', flexWrap: 'wrap', gap: 6 }}>
              <button
                type="button"
                className={`chip-btn ${maxDurationMinutes === null ? 'active' : ''}`}
                onClick={() => setMaxDurationMinutes(null)}
              >
                {t.perspectiveMaxDurationAny}
              </button>
              {PERSPECTIVE_MAX_DURATION_PRESETS.map((mins) => (
                <button
                  key={mins}
                  type="button"
                  className={`chip-btn ${maxDurationMinutes === mins ? 'active' : ''}`}
                  onClick={() => setMaxDurationMinutes(mins)}
                >
                  ≤ {formatStandaloneDuration(mins)}
                </button>
              ))}
            </div>
          </div>

          {/* Due Window Filter */}
          <div>
            <div style={{ fontSize: 12, fontWeight: 600, color: 'var(--text-secondary)', marginBottom: 6 }}>
              {t.perspectiveDueWindowLabel}
            </div>
            <div style={{ display: 'flex', flexWrap: 'wrap', gap: 6 }}>
              {(
                [
                  ['ANY', t.perspectiveDueAny],
                  ['TODAY', t.perspectiveDueToday],
                  ['NEXT_7_DAYS', t.perspectiveDueNext7Days],
                  ['OVERDUE', t.perspectiveDueOverdue]
                ] as Array<[SavedPerspective['dueWindow'], string]>
              ).map(([win, label]) => (
                <button
                  key={win}
                  type="button"
                  className={`chip-btn ${dueWindow === win ? 'active' : ''}`}
                  onClick={() => setDueWindow(win)}
                >
                  {label}
                </button>
              ))}
            </div>
          </div>

          {/* Area Filter */}
          {activeAreas.length > 0 && (
            <div>
              <div style={{ fontSize: 12, fontWeight: 600, color: 'var(--text-secondary)', marginBottom: 6 }}>
                {t.entityArea}
              </div>
              <div style={{ display: 'flex', flexWrap: 'wrap', gap: 6 }}>
                <button
                  type="button"
                  className={`chip-btn ${areaId === null ? 'active' : ''}`}
                  onClick={() => setAreaId(null)}
                >
                  {t.perspectiveAreaAll}
                </button>
                {activeAreas.map((a) => (
                  <button
                    key={a.id}
                    type="button"
                    className={`chip-btn ${areaId === a.id ? 'active' : ''}`}
                    onClick={() => setAreaId(a.id)}
                  >
                    <Folder size={12} />
                    <span>{a.title}</span>
                  </button>
                ))}
              </div>
            </div>
          )}

          {/* Categories Multi-Select */}
          {activeTags.length > 0 && (
            <div>
              <div style={{ fontSize: 12, fontWeight: 600, color: 'var(--text-secondary)', marginBottom: 6 }}>
                {t.entityTags}
              </div>
              <div style={{ display: 'flex', flexWrap: 'wrap', gap: 6, maxHeight: 92, overflowY: 'auto' }}>
                {activeTags.map((tg) => {
                  const selected = categoryIds.includes(tg.id);
                  return (
                    <button
                      key={tg.id}
                      type="button"
                      className={`chip-btn ${selected ? 'active' : ''}`}
                      onClick={() => toggleCategory(tg.id)}
                    >
                      <LayoutGrid size={11.5} />
                      <span>{tg.title}</span>
                    </button>
                  );
                })}
              </div>
            </div>
          )}

          {/* Actionable Only Toggle */}
          <div
            style={{
              display: 'flex',
              alignItems: 'center',
              justifyContent: 'space-between',
              gap: 10,
              padding: '9px 12px',
              borderRadius: 'var(--radius-sm)',
              background: 'var(--bg-subtle)',
              border: '1px solid var(--border-subtle)'
            }}
          >
            <span style={{ fontSize: 13, fontWeight: 560 }}>
              {t.perspectiveActionableOnlyLabel}
            </span>
            <button
              type="button"
              className={`toggle-switch ${actionableOnly ? 'on' : ''}`}
              aria-label={t.perspectiveActionableOnlyLabel}
              aria-pressed={actionableOnly}
              onClick={() => setActionableOnly((v) => !v)}
            >
              <span className="toggle-switch-knob" />
            </button>
          </div>

          {/* Footer Actions */}
          <div
            style={{
              display: 'flex',
              justifyContent: 'space-between',
              alignItems: 'center',
              gap: 8,
              paddingTop: 6
            }}
          >
            {perspective && onDelete ? (
              <button
                type="button"
                className="chip-btn urgent"
                onClick={() => onDelete(perspective.id)}
              >
                <Trash2 size={13} />
                <span>{t.actionDelete}</span>
              </button>
            ) : (
              <span />
            )}

            <div style={{ display: 'flex', gap: 8 }}>
              <button type="button" className="btn-secondary" onClick={onClose}>
                {t.actionCancel}
              </button>
              <button type="submit" className="btn-primary" disabled={!title.trim()}>
                {t.actionSave}
              </button>
            </div>
          </div>
        </form>
      </div>
    </div>
  );
};

