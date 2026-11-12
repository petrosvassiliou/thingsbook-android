import React, { useState } from 'react';
import {
  ArrowDown,
  ArrowUp,
  Bell,
  Check,
  Edit3,
  Flag,
  Folder,
  FolderInput,
  Inbox,
  Package,
  Plus,
  Tag,
  Trash2,
  X
} from 'lucide-react';
import {
  MAX_AREAS,
  fromIsoDateInputValue,
  toIsoDateInputValue,
  type AppPreferences,
  type AreaRecord,
  type HeadingRecord,
  type ProjectRecord,
  type TagRecord
} from '../domain/types';
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
            <Package size={18} style={{ color: 'var(--anytime-teal)' }} />
            <span>
              {t.areasManageTitle} ({activeAreas.length} / {MAX_AREAS})
            </span>
          </h3>
          <button type="button" className="icon-btn" onClick={onClose}>
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
                <Package size={15} style={{ color: 'var(--anytime-teal)', flexShrink: 0 }} />
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
                      disabled={idx === 0}
                      onClick={() => localDb.moveAreaOrder(area.id, 'up')}
                    >
                      <ArrowUp size={14} />
                    </button>
                    <button
                      type="button"
                      className="icon-btn"
                      disabled={idx === activeAreas.length - 1}
                      onClick={() => localDb.moveAreaOrder(area.id, 'down')}
                    >
                      <ArrowDown size={14} />
                    </button>
                    <button
                      type="button"
                      className="icon-btn"
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
          <button type="button" className="icon-btn" onClick={onClose}>
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
  onClose: () => void;
}

export const CreateOrEditProjectModal: React.FC<CreateOrEditProjectModalProps> = ({
  project,
  defaultAreaId,
  areas,
  t,
  onSaved,
  onClose
}) => {
  const [title, setTitle] = useState(project?.title || '');
  const [notes, setNotes] = useState(project?.notes || '');
  const [areaId, setAreaId] = useState<string | null>(
    project ? project.areaId : (defaultAreaId ?? null)
  );
  const [deadlineStr, setDeadlineStr] = useState(() =>
    toIsoDateInputValue(project?.deadline ?? null)
  );

  const activeAreas = areas.filter((a) => !a.isDeleted);

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
          <button type="button" className="icon-btn" onClick={onClose}>
            <X size={18} />
          </button>
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
                  <Package size={12} />
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
          <button type="button" className="icon-btn" onClick={onClose}>
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
                    <Package size={15} style={{ color: 'var(--anytime-teal)' }} />
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
  t: TranslationStrings;
  onClose: () => void;
}

export const TagManagerModal: React.FC<TagManagerModalProps> = ({ tags, t, onClose }) => {
  const [title, setTitle] = useState('');
  const [shortcut, setShortcut] = useState('');
  const [editingId, setEditingId] = useState<string | null>(null);
  const [editTitle, setEditTitle] = useState('');
  const [editShortcut, setEditShortcut] = useState('');

  const activeTags = tags.filter((tg) => !tg.isDeleted);

  const handleCreate = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!title.trim()) return;
    await localDb.upsertTag({
      title: title.trim(),
      shortcut: shortcut.trim().toUpperCase().slice(0, 2) || null
    });
    setTitle('');
    setShortcut('');
  };

  return (
    <div className="modal-backdrop" onClick={onClose}>
      <div className="modal-panel" onClick={(e) => e.stopPropagation()}>
        <div className="modal-header">
          <h3 className="modal-title" style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
            <Tag size={18} style={{ color: 'var(--things-blue)' }} />
            <span>{t.tagsManageTitle}</span>
          </h3>
          <button type="button" className="icon-btn" onClick={onClose}>
            <X size={18} />
          </button>
        </div>

        <form onSubmit={handleCreate} style={{ display: 'flex', gap: 8, marginBottom: 14 }}>
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
            style={{ width: 112 }}
            maxLength={2}
            value={shortcut}
            onChange={(e) => setShortcut(e.target.value.toUpperCase())}
            placeholder={t.tagsShortcutLabel}
          />
          <button type="submit" className="btn-primary" disabled={!title.trim()}>
            {t.tagsAddBtn}
          </button>
        </form>

        <div style={{ display: 'flex', flexDirection: 'column', gap: 6, maxHeight: '54vh', overflowY: 'auto' }}>
          {activeTags.map((tg) => {
            const isEditing = editingId === tg.id;
            return (
              <div
                key={tg.id}
                style={{
                  display: 'flex',
                  alignItems: 'center',
                  gap: 8,
                  padding: '7px 10px',
                  borderRadius: 'var(--radius-sm)',
                  background: 'var(--bg-subtle)',
                  border: '1px solid var(--border-subtle)'
                }}
              >
                <Tag size={14} style={{ color: 'var(--things-blue)' }} />
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
                          if (editTitle.trim()) {
                            await localDb.upsertTag({
                              id: tg.id,
                              title: editTitle.trim(),
                              shortcut: editShortcut.trim().toUpperCase().slice(0, 2) || null
                            });
                          }
                          setEditingId(null);
                        } else if (e.key === 'Escape') {
                          e.stopPropagation();
                          setEditingId(null);
                        }
                      }}
                    />
                    <input
                      type="text"
                      className="text-field"
                      style={{ width: 72, padding: '4px 8px' }}
                      maxLength={2}
                      value={editShortcut}
                      onChange={(e) => setEditShortcut(e.target.value.toUpperCase())}
                      placeholder="Key"
                      onKeyDown={async (e) => {
                        if (e.key === 'Enter') {
                          e.preventDefault();
                          if (editTitle.trim()) {
                            await localDb.upsertTag({
                              id: tg.id,
                              title: editTitle.trim(),
                              shortcut: editShortcut.trim().toUpperCase().slice(0, 2) || null
                            });
                          }
                          setEditingId(null);
                        } else if (e.key === 'Escape') {
                          e.stopPropagation();
                          setEditingId(null);
                        }
                      }}
                    />
                    <button
                      type="button"
                      className="icon-btn active"
                      onClick={async () => {
                        if (editTitle.trim()) {
                          await localDb.upsertTag({
                            id: tg.id,
                            title: editTitle.trim(),
                            shortcut: editShortcut.trim().toUpperCase().slice(0, 2) || null
                          });
                        }
                        setEditingId(null);
                      }}
                    >
                      <Check size={14} />
                    </button>
                  </>
                ) : (
                  <>
                    <span style={{ flex: 1, fontWeight: 560 }}>{tg.title}</span>
                    {tg.shortcut && <kbd>Ctrl+{tg.shortcut}</kbd>}
                    <button
                      type="button"
                      className="icon-btn"
                      onClick={() => {
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
                      onClick={() => localDb.deleteTag(tg.id)}
                    >
                      <Trash2 size={14} />
                    </button>
                  </>
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
  tags: TagRecord[];
  t: TranslationStrings;
  onToggleTag: (tagId: string) => void;
  onOpenManageTags: () => void;
  onClose: () => void;
}

export const TaskTagPickerModal: React.FC<TaskTagPickerModalProps> = ({
  selectedTagIds,
  tags,
  t,
  onToggleTag,
  onOpenManageTags,
  onClose
}) => {
  const [quickTag, setQuickTag] = useState('');
  const activeTags = tags.filter((tg) => !tg.isDeleted);

  const handleQuickCreate = async (e: React.FormEvent) => {
    e.preventDefault();
    const clean = quickTag.trim();
    if (!clean) return;
    const existing = activeTags.find((tg) => tg.title.toLowerCase() === clean.toLowerCase());
    if (existing) {
      if (!selectedTagIds.includes(existing.id)) onToggleTag(existing.id);
    } else {
      const created = await localDb.upsertTag({ title: clean });
      onToggleTag(created.id);
    }
    setQuickTag('');
  };

  return (
    <div className="modal-backdrop" onClick={onClose}>
      <div className="modal-panel" onClick={(e) => e.stopPropagation()}>
        <div className="modal-header">
          <h3 className="modal-title" style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
            <Tag size={18} style={{ color: 'var(--things-blue)' }} />
            <span>{t.ctxAssignTags}</span>
          </h3>
          <button type="button" className="icon-btn" onClick={onClose}>
            <X size={18} />
          </button>
        </div>

        <form onSubmit={handleQuickCreate} style={{ display: 'flex', gap: 8, marginBottom: 12 }}>
          <input
            type="text"
            className="text-field"
            autoFocus
            value={quickTag}
            onChange={(e) => setQuickTag(e.target.value)}
            placeholder={t.tagsNewPlaceholder}
          />
          <button type="submit" className="btn-primary" disabled={!quickTag.trim()}>
            <Plus size={15} />
          </button>
        </form>

        <div style={{ display: 'flex', flexDirection: 'column', gap: 4, maxHeight: '48vh', overflowY: 'auto' }}>
          {activeTags.map((tg) => {
            const checked = selectedTagIds.includes(tg.id);
            return (
              <button
                key={tg.id}
                type="button"
                className="dropdown-item"
                onClick={() => onToggleTag(tg.id)}
              >
                <span style={{ display: 'inline-flex', alignItems: 'center', gap: 9 }}>
                  <span className={`things-checkbox ${checked ? 'completed' : ''}`} style={{ width: 17, height: 17, minWidth: 17 }}>
                    {checked && <Check size={11} strokeWidth={3} />}
                  </span>
                  <span>{tg.title}</span>
                </span>
                {tg.shortcut && <kbd>Ctrl+{tg.shortcut}</kbd>}
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
          <button type="button" className="icon-btn" onClick={onClose}>
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
              const ok = await sendTestChromeOsNotification(t.appName);
              onToast(ok ? '✓ Notification sent' : 'Browser notifications blocked');
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
