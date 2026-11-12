import React, { useEffect, useRef, useState } from 'react';
import {
  ArrowDown,
  ArrowUp,
  Bell,
  Calendar,
  Check,
  CheckCircle2,
  CheckSquare,
  Circle,
  Flag,
  FolderInput,
  GripVertical,
  Moon,
  Plus,
  Repeat,
  RotateCcw,
  Sparkles,
  Star,
  Tag,
  Trash2,
  X,
  XCircle
} from 'lucide-react';
import {
  daysBetweenToday,
  formatRecurrenceShort,
  type AreaRecord,
  type ChecklistItemRecord,
  type ProjectRecord,
  type SupportedLang,
  type TagRecord,
  type TaskRecord,
  type TaskStatus
} from '../domain/types';
import { generateMagicChecklist } from '../data/geminiService';
import { localDb } from '../data/localDb';
import type { TranslationStrings } from '../i18n/translations';

interface TaskItemProps {
  task: TaskRecord;
  isExpanded: boolean;
  isSelected: boolean;
  showContextSubtitle: boolean;
  quickFindOpen: boolean;
  areasMap: Map<string, AreaRecord>;
  projectsMap: Map<string, ProjectRecord>;
  tagsMap: Map<string, TagRecord>;
  checklistItems: ChecklistItemRecord[];
  lang: SupportedLang;
  geminiApiKey: string;
  t: TranslationStrings;
  onSelect: (multiToggle: boolean) => void;
  onExpand: () => void;
  onCollapse: () => void;
  onOpenWhenModal: (task: TaskRecord) => void;
  onOpenDeadlineModal: (task: TaskRecord) => void;
  onOpenRepeatModal: (task: TaskRecord) => void;
  onOpenMoveModal: (task: TaskRecord) => void;
  onOpenTagsModal: (task: TaskRecord) => void;
  onDropReorder: (draggedTaskId: string, targetTaskId: string) => void;
  onToast: (msg: string) => void;
}

export const TaskItem: React.FC<TaskItemProps> = ({
  task,
  isExpanded,
  isSelected,
  showContextSubtitle,
  quickFindOpen,
  areasMap,
  projectsMap,
  tagsMap,
  checklistItems,
  lang,
  geminiApiKey,
  t,
  onSelect,
  onExpand,
  onCollapse,
  onOpenWhenModal,
  onOpenDeadlineModal,
  onOpenRepeatModal,
  onOpenMoveModal,
  onOpenTagsModal,
  onDropReorder,
  onToast
}) => {
  const [titleDraft, setTitleDraft] = useState(task.title);
  const [notesDraft, setNotesDraft] = useState(task.notes);
  const [newSubtask, setNewSubtask] = useState('');
  const [animStage, setAnimStage] = useState<'idle' | 'strike' | 'glide'>('idle');
  const [statusMenuOpen, setStatusMenuOpen] = useState(false);
  const [ctxMenu, setCtxMenu] = useState<{ x: number; y: number } | null>(null);
  const [dragOver, setDragOver] = useState(false);
  const [loadingAi, setLoadingAi] = useState(false);
  const [focusSubtaskId, setFocusSubtaskId] = useState<string | null>(null);

  const strikeTimerRef = useRef<number | null>(null);
  const glideTimerRef = useRef<number | null>(null);
  const subtaskRefs = useRef<Record<string, HTMLInputElement | null>>({});
  const addSubtaskInputRef = useRef<HTMLInputElement | null>(null);

  const [prevSynced, setPrevSynced] = useState({ title: task.title, notes: task.notes });
  if (prevSynced.title !== task.title || prevSynced.notes !== task.notes) {
    setPrevSynced({ title: task.title, notes: task.notes });
    setTitleDraft(task.title);
    setNotesDraft(task.notes);
  }

  useEffect(() => {
    if (focusSubtaskId && subtaskRefs.current[focusSubtaskId]) {
      const el = subtaskRefs.current[focusSubtaskId]!;
      el.focus();
      el.setSelectionRange(0, 0);
      setFocusSubtaskId(null);
    }
  }, [focusSubtaskId, checklistItems]);

  useEffect(() => {
    return () => {
      if (strikeTimerRef.current) window.clearTimeout(strikeTimerRef.current);
      if (glideTimerRef.current) window.clearTimeout(glideTimerRef.current);
    };
  }, []);

  const clearAnimTimers = () => {
    if (strikeTimerRef.current) {
      window.clearTimeout(strikeTimerRef.current);
      strikeTimerRef.current = null;
    }
    if (glideTimerRef.current) {
      window.clearTimeout(glideTimerRef.current);
      glideTimerRef.current = null;
    }
  };

  const commitStatus = async (nextStatus: TaskStatus) => {
    await localDb.upsertTask({
      id: task.id,
      title: titleDraft.trim() || task.title || t.placeholderTodoTitle,
      notes: notesDraft,
      status: nextStatus
    });
  };

  const handlePrimaryCheckboxClick = (e: React.MouseEvent) => {
    e.stopPropagation();
    if (e.altKey) {
      setStatusMenuOpen((v) => !v);
      return;
    }

    // Mid-animation click cancels completion before DB commit!
    if (animStage !== 'idle') {
      clearAnimTimers();
      setAnimStage('idle');
      return;
    }

    if (task.status !== 'OPEN') {
      void commitStatus('OPEN');
      return;
    }

    // 3-Stage Completion Animation: Pop + Strike (210ms) -> Glide-out (160ms) -> Commit
    setAnimStage('strike');
    strikeTimerRef.current = window.setTimeout(() => {
      setAnimStage('glide');
      glideTimerRef.current = window.setTimeout(() => {
        setAnimStage('idle');
        void commitStatus('COMPLETED');
      }, 160);
    }, 220);
  };

  const handleCheckboxContextMenu = (e: React.MouseEvent) => {
    e.preventDefault();
    e.stopPropagation();
    setStatusMenuOpen(true);
  };

  const saveDrafts = async () => {
    const cleanTitle = titleDraft.trim();
    if (cleanTitle !== task.title || notesDraft !== task.notes) {
      await localDb.upsertTask({
        id: task.id,
        title: cleanTitle || t.placeholderTodoTitle,
        notes: notesDraft
      });
    }
  };

  const handleDoneCollapse = async () => {
    await saveDrafts();
    onCollapse();
  };

  const runMagicBreakdown = async () => {
    if (loadingAi) return;
    setLoadingAi(true);
    try {
      const steps = await generateMagicChecklist(
        titleDraft || task.title,
        notesDraft || task.notes,
        lang,
        geminiApiKey
      );
      await localDb.addChecklistItems(task.id, steps);
      onToast(`✨ +${steps.length} ${t.actionChecklist}`);
    } catch (err) {
      onToast(err instanceof Error ? err.message : 'Gemini error');
    } finally {
      setLoadingAi(false);
    }
  };

  const proj = task.projectId ? projectsMap.get(task.projectId) : undefined;
  const area =
    proj?.areaId != null
      ? areasMap.get(proj.areaId)
      : task.areaId
        ? areasMap.get(task.areaId)
        : undefined;
  const containerLabel =
    area && proj ? `${area.title} › ${proj.title}` : proj ? proj.title : area ? area.title : '';

  const activeChecklists = checklistItems
    .filter((c) => !c.isDeleted && c.taskId === task.id)
    .sort((a, b) => a.sortOrder - b.sortOrder);
  const doneChecklistsCount = activeChecklists.filter((c) => c.isCompleted).length;

  const taskTags = task.tagIds
    .map((id) => tagsMap.get(id))
    .filter((tg): tg is TagRecord => Boolean(tg));

  const formatReminderClock = (ms: number) => {
    const d = new Date(ms);
    return `${String(d.getHours()).padStart(2, '0')}:${String(d.getMinutes()).padStart(2, '0')}`;
  };

  const formatShortDate = (ms: number) =>
    new Intl.DateTimeFormat(lang, { month: 'short', day: 'numeric' }).format(new Date(ms));

  const renderDeadlineMeta = (deadlineMs: number) => {
    const diff = daysBetweenToday(deadlineMs);
    const label =
      diff === 0
        ? t.deadlineToday
        : diff > 0
          ? t.deadlineDaysLeft(diff)
          : t.deadlineDaysOverdue(Math.abs(diff));
    const urgent = diff <= 2 && task.status === 'OPEN';
    return { label, urgent };
  };

  const isVisualCompleted = task.status === 'COMPLETED' || animStage !== 'idle';
  const isCanceled = task.status === 'CANCELED';
  const isSomedayDashed = task.status === 'OPEN' && task.scheduleState === 'SOMEDAY' && animStage === 'idle';

  const renderCheckboxWithStatusMenu = () => (
    <div className="checkbox-anchor">
      <button
        type="button"
        className={`things-checkbox ${isSomedayDashed ? 'someday-dashed' : ''} ${
          animStage !== 'idle' ? 'pop' : ''
        } ${isVisualCompleted ? 'completed' : ''} ${isCanceled ? 'canceled' : ''}`}
        onClick={handlePrimaryCheckboxClick}
        onContextMenu={handleCheckboxContextMenu}
        title={t.shortcutCancelHint}
      >
        {isVisualCompleted ? (
          <Check size={12.5} strokeWidth={3} />
        ) : isCanceled ? (
          <X size={12.5} strokeWidth={3} />
        ) : null}
      </button>

      {statusMenuOpen && (
        <>
          <div
            style={{ position: 'fixed', inset: 0, zIndex: 80 }}
            onClick={(e) => {
              e.stopPropagation();
              setStatusMenuOpen(false);
            }}
          />
          <div
            className="dropdown-popover"
            style={{ left: 0, right: 'auto', minWidth: 185 }}
            onClick={(e) => e.stopPropagation()}
          >
            {task.status !== 'COMPLETED' && (
              <button
                type="button"
                className="dropdown-item"
                onClick={() => {
                  setStatusMenuOpen(false);
                  void commitStatus('COMPLETED');
                }}
              >
                <span style={{ display: 'inline-flex', alignItems: 'center', gap: 8 }}>
                  <CheckCircle2 size={14} style={{ color: 'var(--things-blue)' }} />
                  <span>{t.actionComplete}</span>
                </span>
              </button>
            )}
            {task.status !== 'CANCELED' && (
              <button
                type="button"
                className="dropdown-item"
                onClick={() => {
                  setStatusMenuOpen(false);
                  void commitStatus('CANCELED');
                }}
              >
                <span style={{ display: 'inline-flex', alignItems: 'center', gap: 8 }}>
                  <XCircle size={14} style={{ color: 'var(--text-secondary)' }} />
                  <span>{t.actionCancelTodo}</span>
                </span>
              </button>
            )}
            {task.status !== 'OPEN' && (
              <button
                type="button"
                className="dropdown-item"
                onClick={() => {
                  setStatusMenuOpen(false);
                  void commitStatus('OPEN');
                }}
              >
                <span style={{ display: 'inline-flex', alignItems: 'center', gap: 8 }}>
                  <RotateCcw size={14} style={{ color: 'var(--things-blue)' }} />
                  <span>{t.actionReopenTodo}</span>
                </span>
              </button>
            )}
          </div>
        </>
      )}
    </div>
  );

  // ==================== Expanded Card View ====================
  if (isExpanded) {
    const whenChipText =
      task.scheduleState === 'TODAY'
        ? t.viewToday
        : task.scheduleState === 'THIS_EVENING'
          ? t.viewThisEvening
          : task.scheduleState === 'SOMEDAY'
            ? t.viewSomeday
            : task.whenDate != null
              ? formatShortDate(task.whenDate)
              : t.actionWhen;

    return (
      <div
        className="task-card-expanded"
        onClick={(e) => e.stopPropagation()}
        onKeyDown={(e) => {
          if (e.key === 'Escape') {
            e.stopPropagation();
            void handleDoneCollapse();
          }
        }}
      >
        <div className="task-card-top">
          {renderCheckboxWithStatusMenu()}
          <input
            type="text"
            className="task-card-title-input"
            autoFocus
            value={titleDraft}
            onChange={(e) => setTitleDraft(e.target.value)}
            onBlur={() => void saveDrafts()}
            onKeyDown={(e) => {
              if (e.key === 'Enter') {
                e.preventDefault();
                void handleDoneCollapse();
              }
            }}
            placeholder={t.placeholderTodoTitle}
          />
        </div>

        <textarea
          className="task-card-notes"
          value={notesDraft}
          onChange={(e) => setNotesDraft(e.target.value)}
          onBlur={() => void saveDrafts()}
          placeholder={t.placeholderNotes}
          rows={Math.max(2, Math.min(8, notesDraft.split('\n').length))}
        />

        {/* Checklist with Enter Cursor Split / Insert Below + Up/Down/Delete */}
        <div className="checklist-section">
          {activeChecklists.length > 0 && (
            <div className="checklist-header">
              {t.actionChecklist} ({doneChecklistsCount}/{activeChecklists.length})
            </div>
          )}

          {activeChecklists.map((item, idx) => (
            <div key={item.id} className="checklist-row">
              <button
                type="button"
                className={`checklist-circle ${item.isCompleted ? 'done' : ''}`}
                onClick={() =>
                  localDb.updateChecklistItem(item.id, { isCompleted: !item.isCompleted })
                }
              >
                {item.isCompleted && <Check size={10} strokeWidth={3} />}
              </button>

              <input
                ref={(el) => {
                  subtaskRefs.current[item.id] = el;
                }}
                type="text"
                className={`checklist-input ${item.isCompleted ? 'done' : ''}`}
                value={item.title}
                onChange={(e) =>
                  localDb.updateChecklistItem(item.id, { title: e.target.value })
                }
                onKeyDown={async (e) => {
                  if (e.key === 'Enter') {
                    e.preventDefault();
                    const inputEl = e.currentTarget;
                    const val = inputEl.value;
                    if (!val.trim()) {
                      // Blank subtask -> delete and focus bottom add input
                      await localDb.deleteChecklistItem(item.id);
                      addSubtaskInputRef.current?.focus();
                      return;
                    }
                    const cursor = inputEl.selectionStart ?? val.length;
                    if (cursor < val.length) {
                      // Split subtask at cursor position
                      const leftPart = val.slice(0, cursor).trimEnd();
                      const rightPart = val.slice(cursor).trimStart();
                      const newId = await localDb.insertChecklistItemAfter(
                        task.id,
                        item.id,
                        rightPart,
                        leftPart
                      );
                      setFocusSubtaskId(newId);
                    } else {
                      // Cursor at end -> insert empty subtask directly below
                      const newId = await localDb.insertChecklistItemAfter(task.id, item.id, '');
                      setFocusSubtaskId(newId);
                    }
                  }
                }}
              />

              <div className="checklist-row-btns">
                <button
                  type="button"
                  className="icon-btn"
                  style={{ height: 22, minWidth: 22, padding: 0 }}
                  disabled={idx === 0}
                  onClick={() =>
                    localDb.swapChecklistOrder(item.id, activeChecklists[idx - 1].id)
                  }
                >
                  <ArrowUp size={12.5} />
                </button>
                <button
                  type="button"
                  className="icon-btn"
                  style={{ height: 22, minWidth: 22, padding: 0 }}
                  disabled={idx === activeChecklists.length - 1}
                  onClick={() =>
                    localDb.swapChecklistOrder(item.id, activeChecklists[idx + 1].id)
                  }
                >
                  <ArrowDown size={12.5} />
                </button>
                <button
                  type="button"
                  className="icon-btn"
                  style={{ height: 22, minWidth: 22, padding: 0 }}
                  onClick={() => localDb.deleteChecklistItem(item.id)}
                >
                  <X size={13} />
                </button>
              </div>
            </div>
          ))}

          <div className="checklist-add-row">
            <Circle size={13.5} style={{ color: 'var(--text-muted)', flexShrink: 0 }} />
            <input
              ref={addSubtaskInputRef}
              type="text"
              className="checklist-input"
              value={newSubtask}
              onChange={(e) => setNewSubtask(e.target.value)}
              placeholder={t.placeholderAddChecklist}
              onKeyDown={async (e) => {
                if (e.key === 'Enter' && newSubtask.trim()) {
                  e.preventDefault();
                  const txt = newSubtask.trim();
                  setNewSubtask('');
                  await localDb.addChecklistItems(task.id, [txt]);
                }
              }}
            />
            {newSubtask.trim() && (
              <button
                type="button"
                className="chip-btn active"
                onClick={async () => {
                  const txt = newSubtask.trim();
                  setNewSubtask('');
                  await localDb.addChecklistItems(task.id, [txt]);
                }}
              >
                <Plus size={12} />
              </button>
            )}
          </div>
        </div>

        {/* Interactive Metadata Chips Row */}
        <div className="task-card-chips-row">
          <button
            type="button"
            className={`chip-btn ${
              task.scheduleState === 'TODAY' ||
              task.scheduleState === 'THIS_EVENING' ||
              task.whenDate != null
                ? 'active'
                : ''
            }`}
            onClick={() => onOpenWhenModal(task)}
          >
            {task.scheduleState === 'TODAY' ? (
              <Star size={12} fill="var(--today-star)" style={{ color: 'var(--today-star)' }} />
            ) : task.scheduleState === 'THIS_EVENING' ? (
              <Moon size={12} />
            ) : (
              <Calendar size={12} />
            )}
            <span>{whenChipText}</span>
          </button>

          {task.reminderTime != null && (
            <button
              type="button"
              className="chip-btn active"
              onClick={() => onOpenWhenModal(task)}
            >
              <Bell size={12} />
              <span>{formatReminderClock(task.reminderTime)}</span>
            </button>
          )}

          {task.recurrenceRule != null && (
            <button
              type="button"
              className="chip-btn active"
              onClick={() => onOpenRepeatModal(task)}
            >
              <Repeat size={12} />
              <span>{formatRecurrenceShort(task.recurrenceRule, lang)}</span>
            </button>
          )}

          {task.deadline != null && (() => {
            const dl = renderDeadlineMeta(task.deadline);
            return (
              <button
                type="button"
                className={`chip-btn ${dl.urgent ? 'urgent' : 'active'}`}
                onClick={() => onOpenDeadlineModal(task)}
              >
                <Flag size={12} />
                <span>{dl.label}</span>
              </button>
            );
          })()}

          {containerLabel && (
            <button type="button" className="chip-btn" onClick={() => onOpenMoveModal(task)}>
              <FolderInput size={12} />
              <span>{containerLabel}</span>
            </button>
          )}

          {taskTags.map((tg) => (
            <button
              key={tg.id}
              type="button"
              className="chip-btn active"
              onClick={() => onOpenTagsModal(task)}
            >
              <Tag size={11.5} />
              <span>{tg.title}</span>
            </button>
          ))}

          <button type="button" className="chip-btn" onClick={() => onOpenTagsModal(task)}>
            <Plus size={11.5} />
            <span>{t.entityTags}</span>
          </button>
        </div>

        {/* Bottom Action Toolbar: Left = Magic Breakdown, Right = 7 Icons */}
        <div className="task-card-toolbar">
          <button
            type="button"
            className="chip-btn gemini"
            disabled={loadingAi}
            onClick={runMagicBreakdown}
          >
            <Sparkles size={13.5} />
            <span>{loadingAi ? t.geminiGenerating : t.geminiMagicBreakdown}</span>
          </button>

          <div className="task-card-actions">
            <button
              type="button"
              className="icon-btn"
              title={t.ctxWhenReminder}
              onClick={() => onOpenWhenModal(task)}
            >
              <Calendar size={15.5} />
            </button>
            <button
              type="button"
              className="icon-btn"
              title={t.actionRepeat}
              onClick={() => onOpenRepeatModal(task)}
            >
              <Repeat size={15.5} />
            </button>
            <button
              type="button"
              className="icon-btn"
              title={t.ctxMoveContainer}
              onClick={() => onOpenMoveModal(task)}
            >
              <FolderInput size={15.5} />
            </button>
            <button
              type="button"
              className="icon-btn"
              title={t.ctxAssignTags}
              onClick={() => onOpenTagsModal(task)}
            >
              <Tag size={15.5} />
            </button>
            <button
              type="button"
              className="icon-btn"
              title={t.actionDeadline}
              onClick={() => onOpenDeadlineModal(task)}
            >
              <Flag size={15.5} />
            </button>
            <button
              type="button"
              className="icon-btn"
              style={{ color: 'var(--deadline-red)' }}
              title={t.actionDelete}
              onClick={() => localDb.softDeleteTasks([task.id])}
            >
              <Trash2 size={15.5} />
            </button>
            <button
              type="button"
              className="btn-primary"
              style={{ padding: '5px 11px', fontSize: 12.2 }}
              onClick={() => void handleDoneCollapse()}
            >
              <Check size={14} />
              <span>{t.actionDone}</span>
            </button>
          </div>
        </div>
      </div>
    );
  }

  // ==================== Collapsed Row View ====================
  const hasSecondRowBadges =
    task.scheduleState === 'THIS_EVENING' ||
    task.recurrenceRule != null ||
    task.reminderTime != null ||
    activeChecklists.length > 0 ||
    taskTags.length > 0 ||
    task.deadline != null;

  return (
    <>
      <div
        className={`task-row ${isSelected ? 'selected' : ''} ${
          animStage === 'glide' ? 'stage-glide' : ''
        } ${dragOver ? 'drag-over-row' : ''}`}
        draggable
        onDragStart={(e) => {
          e.dataTransfer.setData('application/x-thingsbook-task', task.id);
          e.dataTransfer.effectAllowed = 'move';
        }}
        onDragOver={(e) => {
          if (e.dataTransfer.types.includes('application/x-thingsbook-task')) {
            e.preventDefault();
            setDragOver(true);
          }
        }}
        onDragLeave={() => setDragOver(false)}
        onDrop={(e) => {
          e.preventDefault();
          setDragOver(false);
          const draggedId = e.dataTransfer.getData('application/x-thingsbook-task');
          if (draggedId && draggedId !== task.id) {
            onDropReorder(draggedId, task.id);
          }
        }}
        onClick={(e) => {
          e.stopPropagation();
          if (e.ctrlKey || e.metaKey || e.shiftKey) {
            onSelect(true);
          } else if (isSelected) {
            onExpand();
          } else {
            onSelect(false);
          }
        }}
        onDoubleClick={(e) => {
          e.stopPropagation();
          onExpand();
        }}
        onContextMenu={(e) => {
          if (quickFindOpen) return;
          e.preventDefault();
          e.stopPropagation();
          const x = Math.min(e.clientX, window.innerWidth - 264);
          const y = Math.min(e.clientY, window.innerHeight - 360);
          setCtxMenu({ x: Math.max(8, x), y: Math.max(8, y) });
        }}
      >
        {renderCheckboxWithStatusMenu()}

        <div className="task-row-body">
          <div className="task-row-title-line">
            <span
              className={`task-row-title ${
                animStage === 'strike' || animStage === 'glide'
                  ? 'strike-sweep'
                  : task.status !== 'OPEN'
                    ? 'done'
                    : ''
              }`}
            >
              {task.title || t.placeholderTodoTitle}
            </span>

            {showContextSubtitle && containerLabel && (
              <span className="task-row-subtitle">{containerLabel}</span>
            )}
          </div>

          {task.notes.trim() && (
            <div className="task-row-notes-preview">{task.notes.trim().split('\n')[0]}</div>
          )}

          {/* Exact 6-item Ordered Second-Row Badges */}
          {hasSecondRowBadges && (
            <div className="task-row-badges-second">
              {/* 1. This Evening (ONLY when THIS_EVENING) */}
              {task.scheduleState === 'THIS_EVENING' && (
                <span className="meta-badge evening">
                  <Moon size={10.5} />
                  <span>{t.viewThisEvening}</span>
                </span>
              )}

              {/* 2. Repeat */}
              {task.recurrenceRule != null && (
                <span className="meta-badge">
                  <Repeat size={10.5} />
                  <span>{formatRecurrenceShort(task.recurrenceRule, lang)}</span>
                </span>
              )}

              {/* 3. Reminder */}
              {task.reminderTime != null && (
                <span className="meta-badge">
                  <Bell size={10.5} />
                  <span>{formatReminderClock(task.reminderTime)}</span>
                </span>
              )}

              {/* 4. Checklist */}
              {activeChecklists.length > 0 && (
                <span className="meta-badge">
                  <CheckSquare size={10.5} />
                  <span>
                    {doneChecklistsCount}/{activeChecklists.length}
                  </span>
                </span>
              )}

              {/* 5. Tag Pills */}
              {taskTags.map((tg) => (
                <span key={tg.id} className="meta-badge">
                  <Tag size={9.5} />
                  <span>{tg.title}</span>
                </span>
              ))}

              {/* 6. Deadline */}
              {task.deadline != null && (() => {
                const dl = renderDeadlineMeta(task.deadline);
                return (
                  <span className={`meta-badge ${dl.urgent ? 'urgent' : ''}`}>
                    <Flag size={10.5} />
                    <span>{dl.label}</span>
                  </span>
                );
              })()}
            </div>
          )}
        </div>

        <span className="drag-grip" title="Drag">
          <GripVertical size={15} />
        </span>
      </div>

      {/* Right-Click TaskContextDropdownMenu (Exact v1.6.16 10-Item Menu) */}
      {ctxMenu && !quickFindOpen && (
        <>
          <div
            style={{ position: 'fixed', inset: 0, zIndex: 84 }}
            onClick={() => setCtxMenu(null)}
            onContextMenu={(e) => {
              e.preventDefault();
              setCtxMenu(null);
            }}
          />
          <div
            className="context-menu"
            style={{ left: ctxMenu.x, top: ctxMenu.y }}
            onClick={(e) => e.stopPropagation()}
          >
            {/* Header Banner with Blue Dot + Task Title */}
            <div className="context-menu-banner">
              <span className="context-menu-dot" />
              <span className="context-menu-task-title">
                {task.title || t.placeholderTodoTitle}
              </span>
            </div>
            <div className="context-menu-divider" />

            {/* 1. Select / Deselect */}
            <button
              type="button"
              className="context-menu-item"
              onClick={() => {
                setCtxMenu(null);
                onSelect(true);
              }}
            >
              <CheckSquare size={14} />
              <span>{isSelected ? t.ctxDeselect : t.ctxSelect}</span>
            </button>

            {/* 2. Mark Completed / Reopen */}
            <button
              type="button"
              className="context-menu-item"
              onClick={() => {
                setCtxMenu(null);
                void commitStatus(task.status === 'OPEN' ? 'COMPLETED' : 'OPEN');
              }}
            >
              {task.status === 'OPEN' ? <CheckCircle2 size={14} /> : <RotateCcw size={14} />}
              <span>{task.status === 'OPEN' ? t.actionComplete : t.actionReopenTodo}</span>
            </button>

            {/* 3. When? (Date & Reminder) */}
            <button
              type="button"
              className="context-menu-item"
              onClick={() => {
                setCtxMenu(null);
                onOpenWhenModal(task);
              }}
            >
              <Calendar size={14} />
              <span>{t.ctxWhenReminder}</span>
            </button>

            {/* 4. Deadline */}
            <button
              type="button"
              className="context-menu-item"
              onClick={() => {
                setCtxMenu(null);
                onOpenDeadlineModal(task);
              }}
            >
              <Flag size={14} />
              <span>{t.actionDeadline}</span>
            </button>

            {/* 5. Repeat */}
            <button
              type="button"
              className="context-menu-item"
              onClick={() => {
                setCtxMenu(null);
                onOpenRepeatModal(task);
              }}
            >
              <Repeat size={14} />
              <span>{t.actionRepeat}</span>
            </button>

            {/* 6. Move (Area / Project) */}
            <button
              type="button"
              className="context-menu-item"
              onClick={() => {
                setCtxMenu(null);
                onOpenMoveModal(task);
              }}
            >
              <FolderInput size={14} />
              <span>{t.ctxMoveContainer}</span>
            </button>

            {/* 7. Assign Tags */}
            <button
              type="button"
              className="context-menu-item"
              onClick={() => {
                setCtxMenu(null);
                onOpenTagsModal(task);
              }}
            >
              <Tag size={14} />
              <span>{t.ctxAssignTags}</span>
            </button>

            {/* 8. Magic Breakdown (Gemini ✨) */}
            <button
              type="button"
              className="context-menu-item"
              onClick={() => {
                setCtxMenu(null);
                onExpand();
                void runMagicBreakdown();
              }}
            >
              <Sparkles size={14} style={{ color: 'var(--gemini-violet)' }} />
              <span>{t.geminiMagicBreakdown} ✨</span>
            </button>

            <div className="context-menu-divider" />

            {/* 9. Mark as Canceled */}
            <button
              type="button"
              className="context-menu-item"
              onClick={() => {
                setCtxMenu(null);
                void commitStatus('CANCELED');
              }}
            >
              <XCircle size={14} />
              <span>{t.actionCancelTodo}</span>
            </button>

            {/* 10. Delete To-Do */}
            <button
              type="button"
              className="context-menu-item danger"
              onClick={() => {
                setCtxMenu(null);
                void localDb.softDeleteTasks([task.id]);
              }}
            >
              <Trash2 size={14} />
              <span>{t.actionDelete}</span>
            </button>
          </div>
        </>
      )}
    </>
  );
};
