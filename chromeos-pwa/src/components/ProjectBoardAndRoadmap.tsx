import React, { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import { createPortal } from 'react-dom';
import {
  Calendar,
  CalendarDays,
  Check,
  CheckCircle2,
  CheckSquare,
  ChevronLeft,
  ChevronRight,
  Clock,
  Cloud,
  Copy,
  Download,
  Edit3,
  FileImage,
  FileText,
  Flag,
  FolderInput,
  Hourglass,
  LayoutGrid,
  Link2,
  Moon,
  Pin,
  PinOff,
  Plus,
  Repeat,
  RotateCcw,
  Sun,
  Timer,
  Trash2,
  X,
  XCircle
} from 'lucide-react';
import {
  addDaysLocal,
  daysBetweenToday,
  startOfDayEpoch,
  type ChecklistItemRecord,
  type HeadingRecord,
  type ProjectRecord,
  type SupportedLang,
  type TagRecord,
  type TaskRecord,
  type TaskStatus,
  type ViewSortMode
} from '../domain/types';
import {
  computeClampedContextMenuPosition,
  formatStandaloneDuration,
  formatTimeblockRange,
  isTaskActivelyBlocked,
  sortTasksForView
} from '../domain/v2Utils';
import {
  attachMultiTaskDragPreview,
  downloadBlob,
  extractDraggedTaskIds,
  generateRoadmapPdfBytes,
  hasDraggedTaskMime,
  localDaysBetween,
  orderTaskIdsByVisualOrder,
  renderRoadmapToPngDataUrl,
  shiftPhaseDatesByDays,
  shiftTaskDatesByDays,
  slugifyFilename,
  type ProjectRoadmapLayout
} from '../domain/v30Roadmap';
import { localDb } from '../data/localDb';
import type { TranslationStrings } from '../i18n/translations';
import { ProjectProgressRing } from './ProjectProgressRing';

// ==================== Shared Helpers ====================

function formatShortMonthDay(epochMillis: number, lang: SupportedLang): string {
  return new Intl.DateTimeFormat(lang, { month: 'short', day: 'numeric' }).format(
    new Date(epochMillis)
  );
}

// ==================== 1. Kanban Board View (ProjectBoardWorkspace) ====================

export interface ProjectBoardWorkspaceProps {
  project: ProjectRecord;
  headings: HeadingRecord[];
  visibleTasks: TaskRecord[];
  allProjectTasks: TaskRecord[];
  checklistItems: ChecklistItemRecord[];
  tagsMap: ReadonlyMap<string, TagRecord>;
  openTaskIdSet: ReadonlySet<string>;
  tasksByIdMap: ReadonlyMap<string, TaskRecord>;
  selectedTaskIds: string[];
  orderedVisibleTaskIds: string[];
  viewSortMode: ViewSortMode;
  lang: SupportedLang;
  t: TranslationStrings;
  onSelectTask: (taskId: string, multiToggle: boolean, shiftRange: boolean) => void;
  onOpenTaskEditor: (taskId: string) => void;
  onCreateTodoInPhase: (headingId: string | null) => void;
  onCreatePhase: () => void;
  onOpenWhenModal: (taskId: string) => void;
  onOpenDeadlineModal: (taskId: string) => void;
  onOpenRepeatModal: (taskId: string) => void;
  onOpenMoveModal: (taskId: string) => void;
  onOpenTagsModal: (taskId: string) => void;
  onOpenDependenciesModal: (taskId: string) => void;
  onOpenHeadingStartModal: (headingId: string) => void;
  onOpenHeadingEndModal: (headingId: string) => void;
  onStartFocusTimer: (
    taskId: string,
    taskTitle: string,
    durationMinutes?: number | null
  ) => void;
  onDuplicateTask: (taskId: string) => void;
  onToast: (msg: string) => void;
}

interface BoardCardContextMenuState {
  task: TaskRecord;
  x: number;
  y: number;
}

export const ProjectBoardWorkspace: React.FC<ProjectBoardWorkspaceProps> = ({
  project,
  headings,
  visibleTasks,
  allProjectTasks,
  checklistItems,
  tagsMap,
  openTaskIdSet,
  tasksByIdMap,
  selectedTaskIds,
  orderedVisibleTaskIds,
  viewSortMode,
  lang,
  t,
  onSelectTask,
  onOpenTaskEditor,
  onCreateTodoInPhase,
  onCreatePhase,
  onOpenWhenModal,
  onOpenDeadlineModal,
  onOpenRepeatModal,
  onOpenMoveModal,
  onOpenTagsModal,
  onOpenDependenciesModal,
  onOpenHeadingStartModal,
  onOpenHeadingEndModal,
  onStartFocusTimer,
  onDuplicateTask,
  onToast
}) => {
  const [editingPhaseId, setEditingPhaseId] = useState<string | null>(null);
  const [editingPhaseTitle, setEditingPhaseTitle] = useState('');
  const [dragOverColumnKey, setDragOverColumnKey] = useState<string | null>(null);
  const [dragOverCardInfo, setDragOverCardInfo] = useState<{
    taskId: string;
    half: 'before' | 'after';
  } | null>(null);
  const [statusMenuTaskId, setStatusMenuTaskId] = useState<string | null>(null);
  const [cardCtxMenu, setCardCtxMenu] = useState<BoardCardContextMenuState | null>(null);

  const validHeadingIdSet = useMemo(() => new Set(headings.map((h) => h.id)), [headings]);

  const checklistByTaskId = useMemo(() => {
    const map = new Map<string, { done: number; total: number }>();
    for (const ci of checklistItems) {
      if (ci.isDeleted) continue;
      const cur = map.get(ci.taskId) ?? { done: 0, total: 0 };
      cur.total += 1;
      if (ci.isCompleted) cur.done += 1;
      map.set(ci.taskId, cur);
    }
    return map;
  }, [checklistItems]);

  const ungroupedTasks = useMemo(
    () =>
      sortTasksForView(
        visibleTasks.filter((tk) => !tk.headingId || !validHeadingIdSet.has(tk.headingId)),
        viewSortMode
      ),
    [visibleTasks, validHeadingIdSet, viewSortMode]
  );

  const showGeneralColumn = ungroupedTasks.length > 0 || headings.length === 0;

  const commitPhaseRename = async (hd: HeadingRecord) => {
    const clean = editingPhaseTitle.trim();
    if (clean && clean !== hd.title) {
      await localDb.upsertHeading({
        id: hd.id,
        projectId: project.id,
        title: clean
      });
    } else if (!clean && !hd.title.trim()) {
      await localDb.deleteHeading(hd.id);
    }
    setEditingPhaseId(null);
  };

  const commitTaskStatus = async (tk: TaskRecord, status: TaskStatus) => {
    await localDb.upsertTask({ id: tk.id, status });
  };

  const handleColumnDrop = async (
    e: React.DragEvent,
    targetHeadingId: string | null,
    columnTasks: TaskRecord[]
  ) => {
    e.preventDefault();
    e.stopPropagation();
    setDragOverColumnKey(null);
    setDragOverCardInfo(null);

    const draggedIds = extractDraggedTaskIds(
      e.dataTransfer,
      selectedTaskIds,
      orderedVisibleTaskIds
    );
    if (draggedIds.length === 0) return;

    const draggedSet = new Set(draggedIds);
    const remaining = columnTasks.filter((tItem) => !draggedSet.has(tItem.id));
    const lastOrder =
      remaining.length > 0 ? remaining[remaining.length - 1].sortOrder : null;

    await localDb.batchMoveOrReorderTasksInContainer(draggedIds, {
      projectId: project.id,
      headingId: targetHeadingId,
      areaId: null,
      afterSortOrder: lastOrder
    });

    if (draggedIds.length > 1) {
      onToast(t.dropMovedFeedback(draggedIds.length));
    }
  };

  const handleCardDrop = async (
    e: React.DragEvent,
    targetTask: TaskRecord,
    targetHeadingId: string | null,
    columnTasks: TaskRecord[],
    half: 'before' | 'after'
  ) => {
    e.preventDefault();
    e.stopPropagation();
    setDragOverCardInfo(null);
    setDragOverColumnKey(null);

    const draggedIds = extractDraggedTaskIds(
      e.dataTransfer,
      selectedTaskIds,
      orderedVisibleTaskIds
    );
    if (draggedIds.length === 0) return;
    if (draggedIds.length === 1 && draggedIds[0] === targetTask.id) return;

    const idx = columnTasks.findIndex((x) => x.id === targetTask.id);
    let afterSortOrder: number | null = null;
    let beforeSortOrder: number | null = null;

    if (half === 'before') {
      afterSortOrder = idx > 0 ? columnTasks[idx - 1].sortOrder : null;
      beforeSortOrder = targetTask.sortOrder;
    } else {
      afterSortOrder = targetTask.sortOrder;
      beforeSortOrder =
        idx >= 0 && idx + 1 < columnTasks.length ? columnTasks[idx + 1].sortOrder : null;
    }

    await localDb.batchMoveOrReorderTasksInContainer(draggedIds, {
      projectId: project.id,
      headingId: targetHeadingId,
      areaId: null,
      afterSortOrder,
      beforeSortOrder
    });

    if (draggedIds.length > 1) {
      onToast(t.dropMovedFeedback(draggedIds.length));
    }
  };

  const renderBoardCard = (
    tk: TaskRecord,
    colHeadingId: string | null,
    colTasks: TaskRecord[]
  ) => {
    const isSelected = selectedTaskIds.includes(tk.id);
    const isBlocked = isTaskActivelyBlocked(tk, openTaskIdSet);
    const openBlockers = (tk.blockedByTaskIds ?? [])
      .filter((bid) => bid !== tk.id && openTaskIdSet.has(bid))
      .map((bid) => tasksByIdMap.get(bid)?.title || '')
      .filter(Boolean);
    const blockedLabel =
      openBlockers.length === 1
        ? t.waitingOnNamedBadge(openBlockers[0])
        : t.waitingOnBadge(Math.max(1, openBlockers.length));

    const timeblockRange =
      tk.scheduledStartMinutes != null
        ? formatTimeblockRange(tk.scheduledStartMinutes, tk.durationMinutes ?? 30)
        : null;
    const standaloneDur =
      !timeblockRange && tk.durationMinutes != null && tk.durationMinutes > 0
        ? formatStandaloneDuration(tk.durationMinutes)
        : null;

    const effortLabel =
      tk.effortLevel === 'QUICK_WIN'
        ? `⚡ ${t.effortQuickWin}`
        : tk.effortLevel === 'MEDIUM'
          ? t.effortMedium
          : tk.effortLevel === 'DEEP_FOCUS'
            ? `🧠 ${t.effortDeepFocus}`
            : null;

    const chk = checklistByTaskId.get(tk.id);
    const taskTags = tk.tagIds
      .map((id) => tagsMap.get(id))
      .filter((tg): tg is TagRecord => tg != null);

    const dlDiff = tk.deadline != null ? daysBetweenToday(tk.deadline) : null;
    const dlUrgent = dlDiff != null && dlDiff <= 2 && tk.status === 'OPEN';

    const dropPos =
      dragOverCardInfo?.taskId === tk.id ? dragOverCardInfo.half : null;

    const dragIdsForCard =
      isSelected && selectedTaskIds.length > 1
        ? orderTaskIdsByVisualOrder(selectedTaskIds, orderedVisibleTaskIds)
        : [tk.id];

    return (
      <div
        key={tk.id}
        className={`board-task-card ${isSelected ? 'selected' : ''} ${
          isBlocked ? 'blocked' : ''
        } ${dropPos === 'before' ? 'drop-before' : ''} ${
          dropPos === 'after' ? 'drop-after' : ''
        }`}
        data-testid={`board-card-${tk.id}`}
        data-task-id={tk.id}
        draggable
        onDragStart={(e) => {
          attachMultiTaskDragPreview(
            e.dataTransfer,
            dragIdsForCard,
            t.dragTasksCount(dragIdsForCard.length)
          );
        }}
        onDragOver={(e) => {
          if (!hasDraggedTaskMime(e.dataTransfer)) return;
          e.preventDefault();
          e.stopPropagation();
          e.dataTransfer.dropEffect = 'move';
          const rect = e.currentTarget.getBoundingClientRect();
          const half: 'before' | 'after' =
            e.clientY < rect.top + rect.height / 2 ? 'before' : 'after';
          if (
            dragOverCardInfo?.taskId !== tk.id ||
            dragOverCardInfo?.half !== half
          ) {
            setDragOverCardInfo({ taskId: tk.id, half });
          }
        }}
        onDragLeave={() => {
          if (dragOverCardInfo?.taskId === tk.id) {
            setDragOverCardInfo(null);
          }
        }}
        onDrop={(e) => {
          const rect = e.currentTarget.getBoundingClientRect();
          const half: 'before' | 'after' =
            e.clientY < rect.top + rect.height / 2 ? 'before' : 'after';
          void handleCardDrop(e, tk, colHeadingId, colTasks, half);
        }}
        onClick={(e) => {
          e.stopPropagation();
          if (e.shiftKey) {
            onSelectTask(tk.id, true, true);
          } else if (e.ctrlKey || e.metaKey || selectedTaskIds.length > 0) {
            onSelectTask(tk.id, true, false);
          } else {
            onSelectTask(tk.id, false, false);
          }
        }}
        onDoubleClick={(e) => {
          e.stopPropagation();
          onOpenTaskEditor(tk.id);
        }}
        onContextMenu={(e) => {
          e.preventDefault();
          e.stopPropagation();
          const pos = computeClampedContextMenuPosition(
            e.clientX,
            e.clientY,
            250,
            460,
            window.innerWidth,
            window.innerHeight,
            12
          );
          setCardCtxMenu({ task: tk, x: pos.left, y: pos.top });
        }}
      >
        <div className="board-card-top">
          <div className="checkbox-anchor" onClick={(e) => e.stopPropagation()}>
            <button
              type="button"
              className={`things-checkbox ${
                tk.scheduleState === 'SOMEDAY' && tk.status === 'OPEN'
                  ? 'someday-dashed'
                  : ''
              } ${tk.status === 'COMPLETED' ? 'completed' : ''} ${
                tk.status === 'CANCELED' ? 'canceled' : ''
              }`}
              title={t.shortcutCancelHint}
              onClick={(e) => {
                e.stopPropagation();
                if (e.altKey) {
                  setStatusMenuTaskId((cur) => (cur === tk.id ? null : tk.id));
                  return;
                }
                void commitTaskStatus(
                  tk,
                  tk.status === 'COMPLETED' ? 'OPEN' : 'COMPLETED'
                );
              }}
              onContextMenu={(e) => {
                e.preventDefault();
                e.stopPropagation();
                setStatusMenuTaskId((cur) => (cur === tk.id ? null : tk.id));
              }}
            >
              {tk.status === 'COMPLETED' ? (
                <Check size={12} strokeWidth={3} />
              ) : tk.status === 'CANCELED' ? (
                <X size={12} strokeWidth={3} />
              ) : null}
            </button>

            {statusMenuTaskId === tk.id && (
              <>
                <div
                  style={{ position: 'fixed', inset: 0, zIndex: 80 }}
                  onClick={() => setStatusMenuTaskId(null)}
                />
                <div className="dropdown-popover" style={{ left: 0, right: 'auto' }}>
                  {tk.status !== 'COMPLETED' && (
                    <button
                      type="button"
                      className="dropdown-item"
                      onClick={() => {
                        setStatusMenuTaskId(null);
                        void commitTaskStatus(tk, 'COMPLETED');
                      }}
                    >
                      <span style={{ display: 'inline-flex', alignItems: 'center', gap: 8 }}>
                        <CheckCircle2 size={14} style={{ color: 'var(--things-blue)' }} />
                        <span>{t.actionComplete}</span>
                      </span>
                    </button>
                  )}
                  {tk.status !== 'CANCELED' && (
                    <button
                      type="button"
                      className="dropdown-item"
                      onClick={() => {
                        setStatusMenuTaskId(null);
                        void commitTaskStatus(tk, 'CANCELED');
                      }}
                    >
                      <span style={{ display: 'inline-flex', alignItems: 'center', gap: 8 }}>
                        <XCircle size={14} />
                        <span>{t.actionCancelTodo}</span>
                      </span>
                    </button>
                  )}
                  {tk.status !== 'OPEN' && (
                    <button
                      type="button"
                      className="dropdown-item"
                      onClick={() => {
                        setStatusMenuTaskId(null);
                        void commitTaskStatus(tk, 'OPEN');
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

          <div className="board-card-title-wrap">
            {tk.isPinned && (
              <span className="task-pin-indicator" title={t.pinnedBadge}>
                <Pin size={12} />
              </span>
            )}
            <span className="board-card-title">
              {tk.title || t.placeholderTodoTitle}
            </span>
          </div>

          <button
            type="button"
            className="board-card-edit-btn"
            title={t.actionEdit}
            aria-label={t.actionEdit}
            onClick={(e) => {
              e.stopPropagation();
              onOpenTaskEditor(tk.id);
            }}
          >
            <Edit3 size={12.5} />
          </button>
        </div>

        {tk.notes.trim() && (
          <div className="board-card-notes-snippet">
            {tk.notes.trim().split('\n')[0]}
          </div>
        )}

        <div className="board-card-badges">
          {isBlocked && (
            <span className="meta-badge blocked-badge" title={blockedLabel}>
              <Link2 size={10} />
              <span>{blockedLabel}</span>
            </span>
          )}

          {tk.scheduleState === 'TODAY' && (
            <span className="meta-badge today" title={t.viewToday}>
              <Sun size={10} style={{ color: 'var(--today-star)' }} />
              <span>{t.viewToday}</span>
            </span>
          )}
          {tk.scheduleState === 'THIS_EVENING' && (
            <span className="meta-badge evening" title={t.viewThisEvening}>
              <Moon size={10} />
              <span>{t.viewThisEvening}</span>
            </span>
          )}
          {tk.scheduleState === 'SOMEDAY' && (
            <span className="meta-badge" title={t.viewSomeday}>
              <Cloud size={10} />
              <span>{t.viewSomeday}</span>
            </span>
          )}
          {tk.scheduleState !== 'TODAY' &&
            tk.scheduleState !== 'THIS_EVENING' &&
            tk.scheduleState !== 'SOMEDAY' &&
            tk.whenDate != null && (
              <span className="meta-badge" title={formatShortMonthDay(tk.whenDate, lang)}>
                <Calendar size={10} />
                <span>{formatShortMonthDay(tk.whenDate, lang)}</span>
              </span>
            )}

          {timeblockRange ? (
            <span className="meta-badge v2-timeblock-pill">
              <Clock size={10} />
              <span>{timeblockRange}</span>
            </span>
          ) : standaloneDur ? (
            <span className="meta-badge duration-badge">
              <Hourglass size={10} />
              <span>{standaloneDur}</span>
            </span>
          ) : null}

          {effortLabel && (
            <span
              className={`meta-badge effort-badge effort-${(tk.effortLevel || '').toLowerCase()}`}
            >
              <span>{effortLabel}</span>
            </span>
          )}

          {chk && chk.total > 0 && (
            <span className="meta-badge">
              <CheckSquare size={10} />
              <span>
                {chk.done}/{chk.total}
              </span>
            </span>
          )}

          {tk.deadline != null && dlDiff != null && (
            <span className={`meta-badge ${dlUrgent ? 'urgent' : ''}`}>
              <Flag size={10} />
              <span>
                {formatShortMonthDay(tk.deadline, lang)} ·{' '}
                {dlDiff === 0
                  ? t.deadlineToday
                  : dlDiff > 0
                    ? t.deadlineDaysLeft(dlDiff)
                    : t.deadlineDaysOverdue(Math.abs(dlDiff))}
              </span>
            </span>
          )}

          {taskTags.map((tg) => (
            <span key={tg.id} className="meta-badge">
              <LayoutGrid size={9.5} />
              <span>{tg.title}</span>
            </span>
          ))}
        </div>
      </div>
    );
  };

  const renderColumn = (
    heading: HeadingRecord | null,
    colTasks: TaskRecord[]
  ) => {
    const colKey = heading ? heading.id : '__general__';
    const isGeneral = heading === null;
    const allColTasks = isGeneral
      ? allProjectTasks.filter(
          (tItem) => !tItem.headingId || !validHeadingIdSet.has(tItem.headingId)
        )
      : allProjectTasks.filter((tItem) => tItem.headingId === heading.id);
    const doneColCount = allColTasks.filter((tItem) => tItem.status === 'COMPLETED').length;

    const isEditing = heading != null && editingPhaseId === heading.id;
    const formattedStart =
      heading?.startDate != null ? formatShortMonthDay(heading.startDate, lang) : null;
    const formattedEnd =
      heading?.endDate != null ? formatShortMonthDay(heading.endDate, lang) : null;
    const endDiff = heading?.endDate != null ? daysBetweenToday(heading.endDate) : null;
    const isPhaseOverdue =
      endDiff != null && endDiff < 0 && doneColCount < allColTasks.length;

    return (
      <section
        key={colKey}
        className={`board-column ${dragOverColumnKey === colKey ? 'drag-over' : ''}`}
        data-testid={`board-column-${colKey}`}
        onDragOver={(e) => {
          if (hasDraggedTaskMime(e.dataTransfer)) {
            e.preventDefault();
            e.dataTransfer.dropEffect = 'move';
            if (dragOverColumnKey !== colKey) setDragOverColumnKey(colKey);
          }
        }}
        onDragLeave={(e) => {
          if (
            dragOverColumnKey === colKey &&
            !e.currentTarget.contains(e.relatedTarget as Node | null)
          ) {
            setDragOverColumnKey(null);
          }
        }}
        onDrop={(e) => void handleColumnDrop(e, heading ? heading.id : null, colTasks)}
      >
        <header className="board-column-header">
          <div className="board-column-title-row">
            <div className="board-column-title-left">
              <span
                className={`board-column-accent ${isGeneral ? 'general' : ''}`}
              />
              {isEditing && heading ? (
                <input
                  type="text"
                  className="text-field board-phase-rename-input"
                  autoFocus
                  value={editingPhaseTitle}
                  placeholder={t.boardAddPhase}
                  onChange={(e) => setEditingPhaseTitle(e.target.value)}
                  onBlur={() => void commitPhaseRename(heading)}
                  onKeyDown={(e) => {
                    if (e.key === 'Enter') {
                      e.preventDefault();
                      void commitPhaseRename(heading);
                    } else if (e.key === 'Escape') {
                      e.stopPropagation();
                      setEditingPhaseId(null);
                    }
                  }}
                />
              ) : (
                <span
                  className="board-column-title"
                  onDoubleClick={() => {
                    if (heading) {
                      setEditingPhaseId(heading.id);
                      setEditingPhaseTitle(heading.title);
                    }
                  }}
                >
                  {isGeneral ? t.projectUngroupedColumn : heading.title || t.entityHeading}
                </span>
              )}
              <span className="board-column-count">{colTasks.length}</span>
              {allColTasks.length > 0 && (
                <ProjectProgressRing
                  completed={doneColCount}
                  total={allColTasks.length}
                  size={14}
                />
              )}
            </div>

            <div className="board-column-actions">
              <button
                type="button"
                className="icon-btn"
                style={{ height: 25, minWidth: 25, padding: 0 }}
                title={t.entityNewTodo}
                onClick={() => onCreateTodoInPhase(heading ? heading.id : null)}
              >
                <Plus size={14} />
              </button>
              {heading && (
                <>
                  <button
                    type="button"
                    className="icon-btn"
                    style={{ height: 25, minWidth: 25, padding: 0 }}
                    title={t.actionRename}
                    onClick={() => {
                      setEditingPhaseId(heading.id);
                      setEditingPhaseTitle(heading.title);
                    }}
                  >
                    <Edit3 size={12.5} />
                  </button>
                  <button
                    type="button"
                    className="icon-btn"
                    style={{
                      height: 25,
                      minWidth: 25,
                      padding: 0,
                      color: 'var(--deadline-red)'
                    }}
                    title={t.actionDelete}
                    onClick={() => void localDb.deleteHeading(heading.id)}
                  >
                    <Trash2 size={12.5} />
                  </button>
                </>
              )}
            </div>
          </div>

          {heading && (
            <div className="board-column-dates-row">
              <span className="phase-date-chip-group">
                <button
                  type="button"
                  className={`phase-end-pill ${formattedStart != null ? 'has-date' : ''}`}
                  title={t.phaseSetStartDate}
                  onClick={() => onOpenHeadingStartModal(heading.id)}
                >
                  <Calendar size={11} />
                  <span>
                    {formattedStart != null
                      ? t.phaseStartsOn(formattedStart)
                      : `+ ${t.phaseSetStartDate}`}
                  </span>
                </button>
                {heading.startDate != null && (
                  <button
                    type="button"
                    className="phase-date-clear-btn"
                    title={t.phaseClearStartDate}
                    aria-label={t.phaseClearStartDate}
                    onClick={() => void localDb.updateHeadingStartDate(heading.id, null)}
                  >
                    <X size={10.5} />
                  </button>
                )}
              </span>

              <span className="phase-date-chip-group">
                <button
                  type="button"
                  className={`phase-end-pill ${
                    isPhaseOverdue
                      ? 'overdue'
                      : formattedEnd != null
                        ? 'has-date'
                        : ''
                  }`}
                  title={t.phaseSetEndDate}
                  onClick={() => onOpenHeadingEndModal(heading.id)}
                >
                  <CalendarDays size={11} />
                  <span>
                    {formattedEnd != null
                      ? t.phaseEndsOn(formattedEnd)
                      : `+ ${t.phaseSetEndDate}`}
                  </span>
                </button>
                {heading.endDate != null && (
                  <button
                    type="button"
                    className="phase-date-clear-btn"
                    title={t.phaseClearEndDate}
                    aria-label={t.phaseClearEndDate}
                    onClick={() => void localDb.updateHeadingEndDate(heading.id, null)}
                  >
                    <X size={10.5} />
                  </button>
                )}
              </span>
            </div>
          )}
        </header>

        <div className="board-column-cards">
          {colTasks.map((tk) => renderBoardCard(tk, heading ? heading.id : null, colTasks))}
        </div>

        <footer className="board-column-footer">
          <button
            type="button"
            className="board-add-todo-btn"
            onClick={() => onCreateTodoInPhase(heading ? heading.id : null)}
          >
            <Plus size={13.5} />
            <span>{t.entityNewTodo}</span>
          </button>
        </footer>
      </section>
    );
  };

  return (
    <div
      className="project-board-scroll"
      data-testid="project-board-workspace"
      onClick={(e) => e.stopPropagation()}
    >
      <div className="project-board-columns">
        {showGeneralColumn && renderColumn(null, ungroupedTasks)}

        {headings.map((hd) => {
          const hdTasks = sortTasksForView(
            visibleTasks.filter((tk) => tk.headingId === hd.id),
            viewSortMode
          );
          return renderColumn(hd, hdTasks);
        })}

        <button
          type="button"
          className="board-new-phase-card"
          data-testid="board-add-phase-card"
          onClick={onCreatePhase}
        >
          <Plus size={16} />
          <span>{t.boardAddPhase}</span>
        </button>
      </div>

      {cardCtxMenu &&
        typeof document !== 'undefined' &&
        createPortal(
          <>
            <div
              style={{ position: 'fixed', inset: 0, zIndex: 199 }}
              onClick={() => setCardCtxMenu(null)}
              onContextMenu={(e) => {
                e.preventDefault();
                setCardCtxMenu(null);
              }}
            />
            <div
              className="context-menu"
              style={{ left: cardCtxMenu.x, top: cardCtxMenu.y }}
              onClick={(e) => e.stopPropagation()}
            >
              <div className="context-menu-banner">
                <span className="context-menu-dot" />
                <span className="context-menu-task-title">
                  {cardCtxMenu.task.title || t.placeholderTodoTitle}
                </span>
              </div>
              <div className="context-menu-divider" />

              <button
                type="button"
                className="context-menu-item"
                onClick={() => {
                  const id = cardCtxMenu.task.id;
                  setCardCtxMenu(null);
                  onOpenTaskEditor(id);
                }}
              >
                <Edit3 size={14} />
                <span>{t.actionEdit}</span>
              </button>

              <button
                type="button"
                className="context-menu-item"
                onClick={() => {
                  const id = cardCtxMenu.task.id;
                  setCardCtxMenu(null);
                  onSelectTask(id, true, false);
                }}
              >
                <CheckSquare size={14} />
                <span>
                  {selectedTaskIds.includes(cardCtxMenu.task.id)
                    ? t.ctxDeselect
                    : t.ctxSelect}
                </span>
              </button>

              <button
                type="button"
                className="context-menu-item"
                onClick={() => {
                  const tk = cardCtxMenu.task;
                  setCardCtxMenu(null);
                  void commitTaskStatus(
                    tk,
                    tk.status === 'OPEN' ? 'COMPLETED' : 'OPEN'
                  );
                }}
              >
                <CheckCircle2 size={14} />
                <span>
                  {cardCtxMenu.task.status === 'OPEN'
                    ? t.actionComplete
                    : t.actionReopenTodo}
                </span>
              </button>

              <button
                type="button"
                className="context-menu-item"
                onClick={() => {
                  const id = cardCtxMenu.task.id;
                  setCardCtxMenu(null);
                  onOpenWhenModal(id);
                }}
              >
                <Calendar size={14} />
                <span>{t.ctxWhenReminder}</span>
              </button>

              <button
                type="button"
                className="context-menu-item"
                onClick={() => {
                  const id = cardCtxMenu.task.id;
                  setCardCtxMenu(null);
                  onOpenDeadlineModal(id);
                }}
              >
                <Flag size={14} />
                <span>{t.actionDeadline}</span>
              </button>

              <button
                type="button"
                className="context-menu-item"
                onClick={() => {
                  const id = cardCtxMenu.task.id;
                  setCardCtxMenu(null);
                  onOpenRepeatModal(id);
                }}
              >
                <Repeat size={14} />
                <span>{t.actionRepeat}</span>
              </button>

              <button
                type="button"
                className="context-menu-item"
                onClick={() => {
                  const id = cardCtxMenu.task.id;
                  setCardCtxMenu(null);
                  onOpenMoveModal(id);
                }}
              >
                <FolderInput size={14} />
                <span>{t.ctxMoveContainer}</span>
              </button>

              <button
                type="button"
                className="context-menu-item"
                onClick={() => {
                  const id = cardCtxMenu.task.id;
                  setCardCtxMenu(null);
                  onOpenTagsModal(id);
                }}
              >
                <LayoutGrid size={14} />
                <span>{t.ctxAssignTags}</span>
              </button>

              <button
                type="button"
                className="context-menu-item"
                onClick={() => {
                  const id = cardCtxMenu.task.id;
                  setCardCtxMenu(null);
                  onOpenDependenciesModal(id);
                }}
              >
                <Link2 size={14} />
                <span>{t.waitingOnLabel}</span>
              </button>

              <button
                type="button"
                className="context-menu-item"
                onClick={() => {
                  const tk = cardCtxMenu.task;
                  setCardCtxMenu(null);
                  void localDb.upsertTask({ id: tk.id, isPinned: !tk.isPinned });
                }}
              >
                {cardCtxMenu.task.isPinned ? <PinOff size={14} /> : <Pin size={14} />}
                <span>{cardCtxMenu.task.isPinned ? t.unpinTask : t.pinTask}</span>
              </button>

              {cardCtxMenu.task.status === 'OPEN' && (
                <button
                  type="button"
                  className="context-menu-item"
                  onClick={() => {
                    const tk = cardCtxMenu.task;
                    setCardCtxMenu(null);
                    onStartFocusTimer(tk.id, tk.title, tk.durationMinutes ?? 25);
                  }}
                >
                  <Timer size={14} style={{ color: 'var(--things-blue)' }} />
                  <span>
                    {t.actionFocusTimerMinutes(cardCtxMenu.task.durationMinutes ?? 25)}
                  </span>
                </button>
              )}

              <button
                type="button"
                className="context-menu-item"
                onClick={() => {
                  const id = cardCtxMenu.task.id;
                  setCardCtxMenu(null);
                  onDuplicateTask(id);
                }}
              >
                <Copy size={14} />
                <span>{t.actionDuplicateTodo}</span>
              </button>

              <div className="context-menu-divider" />

              <button
                type="button"
                className="context-menu-item danger"
                onClick={() => {
                  const id = cardCtxMenu.task.id;
                  setCardCtxMenu(null);
                  void localDb.softDeleteTasks([id]);
                }}
              >
                <Trash2 size={14} />
                <span>{t.actionDelete}</span>
              </button>
            </div>
          </>,
          document.body
        )}
    </div>
  );
};

// ==================== 2. Roadmap Gantt View (ProjectRoadmapWorkspace) ====================

type RoadmapZoom = 'compact' | 'standard' | 'wide';

const ZOOM_DAY_WIDTH_PX: Record<RoadmapZoom, number> = {
  compact: 28,
  standard: 40,
  wide: 56
};

const LABEL_COL_WIDTH_PX = 246;
const LANE_ROW_HEIGHT_PX = 40;
const TASK_ROW_HEIGHT_PX = 38;

interface BarDragState {
  kind: 'phase' | 'task';
  id: string;
  startClientX: number;
  deltaDays: number;
  moved: boolean;
}

export interface ProjectRoadmapWorkspaceProps {
  project: ProjectRecord;
  layout: ProjectRoadmapLayout;
  selectedTaskIds: string[];
  orderedVisibleTaskIds: string[];
  lang: SupportedLang;
  isDark: boolean;
  t: TranslationStrings;
  onSelectTask: (taskId: string, multiToggle: boolean, shiftRange: boolean) => void;
  onOpenTaskEditor: (taskId: string) => void;
  onCreateTodoInPhase: (headingId: string | null) => void;
  onCreatePhase: () => void;
  onOpenWhenModal: (taskId: string) => void;
  onOpenHeadingStartModal: (headingId: string) => void;
  onOpenHeadingEndModal: (headingId: string) => void;
  onToast: (msg: string) => void;
}

export const ProjectRoadmapWorkspace: React.FC<ProjectRoadmapWorkspaceProps> = ({
  project,
  layout,
  selectedTaskIds,
  orderedVisibleTaskIds,
  lang,
  isDark,
  t,
  onSelectTask,
  onOpenTaskEditor,
  onCreateTodoInPhase,
  onCreatePhase,
  onOpenWhenModal,
  onOpenHeadingStartModal,
  onOpenHeadingEndModal,
  onToast
}) => {
  const [zoom, setZoom] = useState<RoadmapZoom>('standard');
  const [exportMenuOpen, setExportMenuOpen] = useState(false);
  const [barDrag, setBarDrag] = useState<BarDragState | null>(null);
  const [dragOverLaneId, setDragOverLaneId] = useState<string | null>(null);
  const [editingPhaseId, setEditingPhaseId] = useState<string | null>(null);
  const [editingPhaseTitle, setEditingPhaseTitle] = useState('');

  const scrollRef = useRef<HTMLDivElement | null>(null);
  const dayWidth = ZOOM_DAY_WIDTH_PX[zoom];

  const jumpToToday = useCallback(
    (smooth: boolean = true) => {
      const el = scrollRef.current;
      if (!el) return;
      const targetLeft = Math.max(
        0,
        layout.todayDayIndex * dayWidth - Math.max(80, (el.clientWidth - LABEL_COL_WIDTH_PX) * 0.22)
      );
      el.scrollTo({
        left: targetLeft,
        behavior: smooth ? 'smooth' : 'auto'
      });
    },
    [layout.todayDayIndex, dayWidth]
  );

  useEffect(() => {
    jumpToToday(false);
  }, [project.id]); // eslint-disable-line react-hooks/exhaustive-deps

  // Group contiguous days by month for top timeline header
  const monthSpans = useMemo(() => {
    const groups: Array<{ key: string; label: string; count: number }> = [];
    const fmt = new Intl.DateTimeFormat(lang, { month: 'short', year: 'numeric' });
    for (const d of layout.days) {
      const key = `${d.year}-${d.monthIndex}`;
      const prev = groups[groups.length - 1];
      if (prev && prev.key === key) {
        prev.count += 1;
      } else {
        groups.push({
          key,
          label: fmt.format(new Date(d.epoch)),
          count: 1
        });
      }
    }
    return groups;
  }, [layout.days, lang]);

  const weekdayNarrow = useMemo(() => {
    const fmt = new Intl.DateTimeFormat(lang, { weekday: 'narrow' });
    return layout.days.map((d) => fmt.format(new Date(d.epoch)));
  }, [layout.days, lang]);

  // Pointer drag lifecycle for Phase bars & Task bars
  useEffect(() => {
    if (!barDrag) return;

    const onPointerMove = (ev: PointerEvent) => {
      const dx = ev.clientX - barDrag.startClientX;
      const delta = Math.round(dx / dayWidth);
      const moved = barDrag.moved || Math.abs(dx) >= 4;
      setBarDrag((prev) =>
        prev && (prev.deltaDays !== delta || prev.moved !== moved)
          ? { ...prev, deltaDays: delta, moved }
          : prev
      );
    };

    const onPointerUp = () => {
      const finished = barDrag;
      setBarDrag(null);
      if (!finished) return;

      if (finished.deltaDays !== 0) {
        if (finished.kind === 'phase') {
          const lane = layout.lanes.find((l) => l.heading?.id === finished.id);
          if (lane && lane.heading) {
            const shifted = shiftPhaseDatesByDays(lane.heading, finished.deltaDays, {
              startEpoch: lane.startEpoch,
              endEpoch: lane.endEpoch
            });
            void localDb.updateHeadingDates(
              lane.heading.id,
              shifted.startDate,
              shifted.endDate
            );
          }
        } else {
          const plotted = layout.plottedTasks.find((p) => p.task.id === finished.id);
          if (plotted) {
            const shifted = shiftTaskDatesByDays(
              plotted.task,
              finished.deltaDays,
              layout.todayEpoch
            );
            void localDb.upsertTask({
              id: plotted.task.id,
              whenDate: shifted.whenDate,
              deadline: shifted.deadline,
              scheduleState: shifted.scheduleState
            });
          }
        }
      }
    };

    window.addEventListener('pointermove', onPointerMove);
    window.addEventListener('pointerup', onPointerUp);
    window.addEventListener('pointercancel', onPointerUp);
    return () => {
      window.removeEventListener('pointermove', onPointerMove);
      window.removeEventListener('pointerup', onPointerUp);
      window.removeEventListener('pointercancel', onPointerUp);
    };
  }, [barDrag, dayWidth, layout]);

  const stepPhaseByDays = (lane: ProjectRoadmapLayout['lanes'][number], delta: number) => {
    if (!lane.heading) return;
    const shifted = shiftPhaseDatesByDays(lane.heading, delta, {
      startEpoch: lane.startEpoch,
      endEpoch: lane.endEpoch
    });
    void localDb.updateHeadingDates(lane.heading.id, shifted.startDate, shifted.endDate);
  };

  const stepTaskByDays = (task: TaskRecord, delta: number) => {
    const shifted = shiftTaskDatesByDays(task, delta, layout.todayEpoch);
    void localDb.upsertTask({
      id: task.id,
      whenDate: shifted.whenDate,
      deadline: shifted.deadline,
      scheduleState: shifted.scheduleState
    });
  };

  // Precompute exact Y-coordinates and bar X-coordinates for SVG cubic-Bezier dependency connectors
  const { totalBodyHeight, svgConnectorPaths } = useMemo(() => {
    const coords = new Map<string, { xLeft: number; xRight: number; yMid: number }>();
    let yCursor = 0;

    for (const lane of layout.lanes) {
      yCursor += LANE_ROW_HEIGHT_PX;
      for (const pt of lane.plottedTasks) {
        const offsetDays =
          barDrag && barDrag.kind === 'task' && barDrag.id === pt.task.id
            ? barDrag.deltaDays
            : 0;
        const sIdx = pt.startDayIndex + offsetDays;
        const eIdx = pt.endDayIndex + offsetDays;
        const barLeft = sIdx * dayWidth + 4;
        const barWidth = Math.max(18, (eIdx - sIdx + 1) * dayWidth - 8);
        coords.set(pt.task.id, {
          xLeft: barLeft,
          xRight: barLeft + barWidth,
          yMid: yCursor + TASK_ROW_HEIGHT_PX / 2
        });
        yCursor += TASK_ROW_HEIGHT_PX;
      }
    }

    const paths = layout.dependencyEdges
      .map((edge) => {
        const src = coords.get(edge.fromTaskId);
        const dst = coords.get(edge.toTaskId);
        if (!src || !dst) return null;
        const x1 = src.xRight;
        const y1 = src.yMid;
        const x2 = dst.xLeft;
        const y2 = dst.yMid;
        const dx = x2 - x1;
        const cOffset = Math.max(22, Math.abs(dx) * 0.45);
        const d = `M ${x1} ${y1} C ${x1 + cOffset} ${y1}, ${x2 - cOffset} ${y2}, ${x2} ${y2}`;
        return {
          key: `${edge.fromTaskId}->${edge.toTaskId}`,
          d,
          isActivelyBlocking: edge.isActivelyBlocking
        };
      })
      .filter(
        (
          item
        ): item is { key: string; d: string; isActivelyBlocking: boolean } =>
          item !== null
      );

    return {
      totalBodyHeight: Math.max(LANE_ROW_HEIGHT_PX, yCursor),
      svgConnectorPaths: paths
    };
  }, [layout.lanes, layout.dependencyEdges, dayWidth, barDrag]);

  const timelineWidthPx = layout.totalDays * dayWidth;
  const todayRawOffset = localDaysBetween(layout.windowStartEpoch, layout.todayEpoch);
  const showTodayLine = todayRawOffset >= 0 && todayRawOffset < layout.totalDays;

  const rangeSummaryLabel = useMemo(() => {
    const first = layout.days[0];
    const last = layout.days[layout.days.length - 1];
    if (!first || !last) return '';
    return `${formatShortMonthDay(first.epoch, lang)} – ${formatShortMonthDay(last.epoch, lang)}`;
  }, [layout.days, lang]);

  const handleExportPdf = () => {
    setExportMenuOpen(false);
    const bytes = generateRoadmapPdfBytes(layout, project.title, {
      ungroupedTitle: t.projectUngroupedColumn,
      todayLabel: t.viewToday,
      unscheduledLabel: t.roadmapUnscheduledSection,
      subtitle: rangeSummaryLabel
    });
    downloadBlob(
      bytes,
      `${slugifyFilename(project.title)}-roadmap.pdf`,
      'application/pdf'
    );
    onToast(`✓ ${t.roadmapExportPdf}`);
  };

  const handleExportPng = () => {
    setExportMenuOpen(false);
    const dataUrl = renderRoadmapToPngDataUrl(
      layout,
      project.title,
      {
        ungroupedTitle: t.projectUngroupedColumn,
        todayLabel: t.viewToday,
        unscheduledLabel: t.roadmapUnscheduledSection,
        subtitle: rangeSummaryLabel
      },
      isDark
    );
    downloadBlob(
      dataUrl,
      `${slugifyFilename(project.title)}-roadmap.png`,
      'image/png'
    );
    onToast(`✓ ${t.roadmapExportPng}`);
  };

  const handleDropOnLaneHeader = async (
    e: React.DragEvent,
    headingId: string | null
  ) => {
    e.preventDefault();
    setDragOverLaneId(null);
    const ids = extractDraggedTaskIds(
      e.dataTransfer,
      selectedTaskIds,
      orderedVisibleTaskIds
    );
    if (ids.length === 0) return;
    await localDb.batchMoveOrReorderTasksInContainer(ids, {
      projectId: project.id,
      headingId,
      areaId: null
    });
    if (ids.length > 1) {
      onToast(t.dropMovedFeedback(ids.length));
    }
  };

  const commitPhaseRename = async (hd: HeadingRecord) => {
    const clean = editingPhaseTitle.trim();
    if (clean && clean !== hd.title) {
      await localDb.upsertHeading({
        id: hd.id,
        projectId: project.id,
        title: clean
      });
    }
    setEditingPhaseId(null);
  };

  return (
    <div
      className="project-roadmap-workspace"
      data-testid="project-roadmap-workspace"
      onClick={(e) => e.stopPropagation()}
    >
      {/* Calm Soft Bento Roadmap Toolbar */}
      <div className="roadmap-toolbar">
        <div className="roadmap-toolbar-left">
          <span className="roadmap-range-pill">{rangeSummaryLabel}</span>
          <button
            type="button"
            className="chip-btn"
            data-testid="roadmap-jump-today-btn"
            title={t.roadmapJumpToday}
            onClick={() => jumpToToday(true)}
          >
            <Sun size={12.5} style={{ color: 'var(--today-star)' }} />
            <span>{t.roadmapJumpToday}</span>
          </button>

          <div className="v2-segmented-toggle roadmap-zoom-toggle">
            {(['compact', 'standard', 'wide'] as RoadmapZoom[]).map((z) => (
              <button
                key={z}
                type="button"
                className={`v2-segmented-btn ${zoom === z ? 'active' : ''}`}
                onClick={() => setZoom(z)}
              >
                <span>{z === 'compact' ? 'S' : z === 'standard' ? 'M' : 'L'}</span>
              </button>
            ))}
          </div>
        </div>

        <div className="roadmap-toolbar-right">
          <button
            type="button"
            className="chip-btn"
            onClick={() => onCreateTodoInPhase(null)}
          >
            <Plus size={12.5} />
            <span>{t.entityTodo}</span>
          </button>

          <button
            type="button"
            className="chip-btn"
            onClick={onCreatePhase}
          >
            <Plus size={12.5} />
            <span>{t.boardAddPhase}</span>
          </button>

          <div className="dropdown-anchor">
            <button
              type="button"
              className="chip-btn active"
              data-testid="roadmap-export-btn"
              title={t.roadmapExport}
              onClick={() => setExportMenuOpen((v) => !v)}
            >
              <Download size={12.5} />
              <span>{t.roadmapExport}</span>
            </button>

            {exportMenuOpen && (
              <>
                <div
                  style={{ position: 'fixed', inset: 0, zIndex: 75 }}
                  onClick={() => setExportMenuOpen(false)}
                />
                <div className="dropdown-popover" data-testid="roadmap-export-menu">
                  <button
                    type="button"
                    className="dropdown-item"
                    data-testid="roadmap-export-pdf-btn"
                    onClick={handleExportPdf}
                  >
                    <span style={{ display: 'inline-flex', alignItems: 'center', gap: 8 }}>
                      <FileText size={14} style={{ color: 'var(--things-blue)' }} />
                      <span>{t.roadmapExportPdf}</span>
                    </span>
                  </button>
                  <button
                    type="button"
                    className="dropdown-item"
                    data-testid="roadmap-export-png-btn"
                    onClick={handleExportPng}
                  >
                    <span style={{ display: 'inline-flex', alignItems: 'center', gap: 8 }}>
                      <FileImage size={14} style={{ color: 'var(--things-blue)' }} />
                      <span>{t.roadmapExportPng}</span>
                    </span>
                  </button>
                </div>
              </>
            )}
          </div>
        </div>
      </div>

      {/* Unified Horizontal + Vertical Timeline Scroll Canvas */}
      <div ref={scrollRef} className="roadmap-canvas-scroll">
        <div
          className="roadmap-grid-inner"
          style={{ width: LABEL_COL_WIDTH_PX + timelineWidthPx }}
        >
          {/* Sticky Top Timeline Header */}
          <div className="roadmap-header-row">
            <div
              className="roadmap-sticky-label-header"
              style={{ width: LABEL_COL_WIDTH_PX }}
            >
              <span>
                {t.entityHeading} / {t.entityTodo}
              </span>
            </div>

            <div className="roadmap-timeline-header" style={{ width: timelineWidthPx }}>
              <div className="roadmap-months-row">
                {monthSpans.map((ms) => (
                  <div
                    key={ms.key}
                    className="roadmap-month-cell"
                    style={{ width: ms.count * dayWidth }}
                  >
                    {ms.label}
                  </div>
                ))}
              </div>

              <div className="roadmap-days-row">
                {layout.days.map((cell, idx) => (
                  <div
                    key={cell.isoDate}
                    className={`roadmap-day-cell ${cell.isToday ? 'today' : ''} ${
                      cell.isWeekend ? 'weekend' : ''
                    }`}
                    style={{ width: dayWidth }}
                  >
                    <span className="roadmap-day-dow">{weekdayNarrow[idx]}</span>
                    <span className="roadmap-day-num">{cell.dayOfMonth}</span>
                  </div>
                ))}
              </div>
            </div>
          </div>

          {/* Body Lanes + Background Grid + SVG Dependency Connectors */}
          <div className="roadmap-body-wrap" style={{ minHeight: totalBodyHeight }}>
            {/* Background Weekend Shading, Vertical Day Gridlines, Today Accent Line, and Dependency SVG */}
            <div
              className="roadmap-timeline-backdrop"
              style={{
                left: LABEL_COL_WIDTH_PX,
                width: timelineWidthPx,
                height: totalBodyHeight
              }}
            >
              {layout.days.map((cell) => (
                <div
                  key={`bg-${cell.isoDate}`}
                  className={`roadmap-bg-col ${cell.isWeekend ? 'weekend' : ''}`}
                  style={{ left: cell.dayIndex * dayWidth, width: dayWidth }}
                />
              ))}

              {showTodayLine && (
                <div
                  className="roadmap-today-line"
                  data-testid="roadmap-today-line"
                  style={{ left: todayRawOffset * dayWidth + dayWidth / 2 }}
                >
                  <span className="roadmap-today-dot" />
                </div>
              )}

              {svgConnectorPaths.length > 0 && (
                <svg
                  className="roadmap-dependency-svg"
                  width={timelineWidthPx}
                  height={totalBodyHeight}
                  data-testid="roadmap-dependency-svg"
                >
                  <defs>
                    <marker
                      id="roadmap-arrow-blocking"
                      viewBox="0 0 8 8"
                      refX="7"
                      refY="4"
                      markerWidth="6"
                      markerHeight="6"
                      orient="auto-start-reverse"
                    >
                      <path d="M 0 1 L 7 4 L 0 7 z" fill="#EA580C" />
                    </marker>
                    <marker
                      id="roadmap-arrow-resolved"
                      viewBox="0 0 8 8"
                      refX="7"
                      refY="4"
                      markerWidth="6"
                      markerHeight="6"
                      orient="auto-start-reverse"
                    >
                      <path d="M 0 1 L 7 4 L 0 7 z" fill="#64748B" />
                    </marker>
                  </defs>
                  {svgConnectorPaths.map((edge) => (
                    <path
                      key={edge.key}
                      d={edge.d}
                      fill="none"
                      stroke={edge.isActivelyBlocking ? '#EA580C' : '#64748B'}
                      strokeWidth={edge.isActivelyBlocking ? 1.75 : 1.35}
                      strokeDasharray={edge.isActivelyBlocking ? undefined : '4 3'}
                      markerEnd={
                        edge.isActivelyBlocking
                          ? 'url(#roadmap-arrow-blocking)'
                          : 'url(#roadmap-arrow-resolved)'
                      }
                    />
                  ))}
                </svg>
              )}
            </div>

            {/* Foreground Interactive Lane & Task Rows */}
            {layout.lanes.map((lane) => {
              const hd = lane.heading;
              const isGeneral = hd === null;
              const phaseDragDelta =
                hd && barDrag?.kind === 'phase' && barDrag.id === hd.id
                  ? barDrag.deltaDays
                  : 0;
              const isEditing = hd != null && editingPhaseId === hd.id;

              return (
                <React.Fragment key={lane.laneId}>
                  {/* Phase Lane Header Row */}
                  <div
                    className={`roadmap-lane-row ${
                      dragOverLaneId === lane.laneId ? 'drag-over' : ''
                    }`}
                    style={{ height: LANE_ROW_HEIGHT_PX }}
                    onDragOver={(e) => {
                      if (hasDraggedTaskMime(e.dataTransfer)) {
                        e.preventDefault();
                        e.dataTransfer.dropEffect = 'move';
                        if (dragOverLaneId !== lane.laneId) setDragOverLaneId(lane.laneId);
                      }
                    }}
                    onDragLeave={() => {
                      if (dragOverLaneId === lane.laneId) setDragOverLaneId(null);
                    }}
                    onDrop={(e) => void handleDropOnLaneHeader(e, hd ? hd.id : null)}
                  >
                    <div
                      className="roadmap-sticky-cell lane-cell"
                      style={{ width: LABEL_COL_WIDTH_PX }}
                    >
                      <span
                        className={`board-column-accent ${isGeneral ? 'general' : ''}`}
                      />
                      {isEditing && hd ? (
                        <input
                          type="text"
                          className="text-field board-phase-rename-input"
                          autoFocus
                          value={editingPhaseTitle}
                          onChange={(e) => setEditingPhaseTitle(e.target.value)}
                          onBlur={() => void commitPhaseRename(hd)}
                          onKeyDown={(e) => {
                            if (e.key === 'Enter') {
                              e.preventDefault();
                              void commitPhaseRename(hd);
                            } else if (e.key === 'Escape') {
                              setEditingPhaseId(null);
                            }
                          }}
                        />
                      ) : (
                        <span
                          className="roadmap-lane-title"
                          onDoubleClick={() => {
                            if (hd) {
                              setEditingPhaseId(hd.id);
                              setEditingPhaseTitle(hd.title);
                            }
                          }}
                        >
                          {isGeneral
                            ? t.projectUngroupedColumn
                            : hd.title || t.entityHeading}
                        </span>
                      )}

                      {lane.totalCount > 0 && (
                        <ProjectProgressRing
                          completed={lane.completedCount}
                          total={lane.totalCount}
                          size={13}
                        />
                      )}

                      <div className="roadmap-cell-quick-btns">
                        {hd && (
                          <>
                            <button
                              type="button"
                              className="roadmap-step-btn"
                              title={t.phaseSetStartDate}
                              onClick={() => onOpenHeadingStartModal(hd.id)}
                            >
                              <Calendar size={11.5} />
                            </button>
                            <button
                              type="button"
                              className="roadmap-step-btn"
                              title={t.phaseSetEndDate}
                              onClick={() => onOpenHeadingEndModal(hd.id)}
                            >
                              <Flag size={11.5} />
                            </button>
                          </>
                        )}
                        <button
                          type="button"
                          className="roadmap-step-btn"
                          title={t.entityNewTodo}
                          onClick={() => onCreateTodoInPhase(hd ? hd.id : null)}
                        >
                          <Plus size={12.5} />
                        </button>
                      </div>
                    </div>

                    <div
                      className="roadmap-track-cell"
                      style={{ width: timelineWidthPx }}
                    >
                      {hd && lane.startDayIndex != null && lane.endDayIndex != null ? (
                        <div
                          className={`roadmap-phase-bar ${
                            lane.isInferred ? 'inferred' : ''
                          } ${phaseDragDelta !== 0 ? 'dragging' : ''}`}
                          data-testid={`roadmap-phase-bar-${hd.id}`}
                          style={{
                            left: (lane.startDayIndex + phaseDragDelta) * dayWidth + 3,
                            width: Math.max(22, lane.spanDays * dayWidth - 6)
                          }}
                          onPointerDown={(e) => {
                            if ((e.target as HTMLElement).closest('button')) return;
                            e.stopPropagation();
                            setBarDrag({
                              kind: 'phase',
                              id: hd.id,
                              startClientX: e.clientX,
                              deltaDays: 0,
                              moved: false
                            });
                          }}
                        >
                          <span className="roadmap-phase-bar-label">
                            {hd.title || t.entityHeading}
                          </span>
                          {phaseDragDelta !== 0 && (
                            <span className="roadmap-drag-delta-pill">
                              {phaseDragDelta > 0
                                ? `+${phaseDragDelta}d`
                                : `${phaseDragDelta}d`}
                            </span>
                          )}
                          <span className="roadmap-bar-step-controls">
                            <button
                              type="button"
                              className="roadmap-bar-step-btn"
                              title={t.roadmapShiftEarlier}
                              aria-label={t.roadmapShiftEarlier}
                              onClick={(e) => {
                                e.stopPropagation();
                                stepPhaseByDays(lane, -1);
                              }}
                            >
                              <ChevronLeft size={11} />
                            </button>
                            <button
                              type="button"
                              className="roadmap-bar-step-btn"
                              title={t.roadmapShiftLater}
                              aria-label={t.roadmapShiftLater}
                              onClick={(e) => {
                                e.stopPropagation();
                                stepPhaseByDays(lane, 1);
                              }}
                            >
                              <ChevronRight size={11} />
                            </button>
                          </span>
                        </div>
                      ) : hd ? (
                        <button
                          type="button"
                          className="roadmap-set-phase-dates-inline"
                          style={{ left: layout.todayDayIndex * dayWidth + 4 }}
                          onClick={() => onOpenHeadingStartModal(hd.id)}
                        >
                          <Calendar size={11} />
                          <span>+ {t.phaseSetStartDate}</span>
                        </button>
                      ) : null}
                    </div>
                  </div>

                  {/* Plotted Task Rows inside Lane */}
                  {lane.plottedTasks.map((pt) => {
                    const tk = pt.task;
                    const isSelected = selectedTaskIds.includes(tk.id);
                    const taskDragDelta =
                      barDrag?.kind === 'task' && barDrag.id === tk.id
                        ? barDrag.deltaDays
                        : 0;
                    const barLeft = (pt.startDayIndex + taskDragDelta) * dayWidth + 4;
                    const barWidth = Math.max(22, pt.spanDays * dayWidth - 8);
                    const dlDiff =
                      tk.deadline != null ? daysBetweenToday(tk.deadline) : null;
                    const dlUrgent = dlDiff != null && dlDiff <= 2 && tk.status === 'OPEN';
                    const durBadge =
                      tk.durationMinutes != null && tk.durationMinutes > 0
                        ? formatStandaloneDuration(tk.durationMinutes)
                        : null;
                    const dragIdsForRow =
                      isSelected && selectedTaskIds.length > 1
                        ? orderTaskIdsByVisualOrder(
                            selectedTaskIds,
                            orderedVisibleTaskIds
                          )
                        : [tk.id];

                    return (
                      <div
                        key={tk.id}
                        className={`roadmap-task-row ${isSelected ? 'selected' : ''}`}
                        style={{ height: TASK_ROW_HEIGHT_PX }}
                        data-testid={`roadmap-task-row-${tk.id}`}
                      >
                        <div
                          className="roadmap-sticky-cell task-cell"
                          style={{ width: LABEL_COL_WIDTH_PX }}
                          draggable
                          onDragStart={(e) => {
                            attachMultiTaskDragPreview(
                              e.dataTransfer,
                              dragIdsForRow,
                              t.dragTasksCount(dragIdsForRow.length)
                            );
                          }}
                          onClick={(e) => {
                            e.stopPropagation();
                            if (e.shiftKey) {
                              onSelectTask(tk.id, true, true);
                            } else if (e.ctrlKey || e.metaKey || selectedTaskIds.length > 0) {
                              onSelectTask(tk.id, true, false);
                            } else {
                              onSelectTask(tk.id, false, false);
                            }
                          }}
                          onDoubleClick={() => onOpenTaskEditor(tk.id)}
                        >
                          <button
                            type="button"
                            className={`things-checkbox ${
                              tk.status === 'COMPLETED' ? 'completed' : ''
                            }`}
                            onClick={(e) => {
                              e.stopPropagation();
                              void localDb.upsertTask({
                                id: tk.id,
                                status: tk.status === 'COMPLETED' ? 'OPEN' : 'COMPLETED'
                              });
                            }}
                          >
                            {tk.status === 'COMPLETED' && (
                              <Check size={11.5} strokeWidth={3} />
                            )}
                          </button>

                          {tk.isPinned && (
                            <span className="task-pin-indicator">
                              <Pin size={11} />
                            </span>
                          )}

                          <span
                            className={`roadmap-task-label ${
                              tk.status === 'COMPLETED' ? 'done' : ''
                            }`}
                          >
                            {tk.title || t.placeholderTodoTitle}
                          </span>

                          <div className="roadmap-cell-quick-btns">
                            <button
                              type="button"
                              className="roadmap-step-btn"
                              title={t.roadmapShiftEarlier}
                              onClick={(e) => {
                                e.stopPropagation();
                                stepTaskByDays(tk, -1);
                              }}
                            >
                              <ChevronLeft size={11.5} />
                            </button>
                            <button
                              type="button"
                              className="roadmap-step-btn"
                              title={t.roadmapShiftLater}
                              onClick={(e) => {
                                e.stopPropagation();
                                stepTaskByDays(tk, 1);
                              }}
                            >
                              <ChevronRight size={11.5} />
                            </button>
                            <button
                              type="button"
                              className="roadmap-step-btn"
                              title={t.actionEdit}
                              onClick={(e) => {
                                e.stopPropagation();
                                onOpenTaskEditor(tk.id);
                              }}
                            >
                              <Edit3 size={11.5} />
                            </button>
                          </div>
                        </div>

                        <div
                          className="roadmap-track-cell"
                          style={{ width: timelineWidthPx }}
                        >
                          <div
                            className={`roadmap-task-bar ${
                              pt.isActivelyBlocked ? 'blocked' : ''
                            } ${
                              tk.scheduleState === 'TODAY' ||
                              tk.scheduleState === 'THIS_EVENING'
                                ? 'today-active'
                                : ''
                            } ${dlUrgent ? 'urgent' : ''} ${
                              isSelected ? 'selected' : ''
                            } ${taskDragDelta !== 0 ? 'dragging' : ''}`}
                            data-testid={`roadmap-task-bar-${tk.id}`}
                            style={{ left: barLeft, width: barWidth }}
                            onPointerDown={(e) => {
                              if ((e.target as HTMLElement).closest('button')) return;
                              e.stopPropagation();
                              setBarDrag({
                                kind: 'task',
                                id: tk.id,
                                startClientX: e.clientX,
                                deltaDays: 0,
                                moved: false
                              });
                            }}
                            onDoubleClick={(e) => {
                              e.stopPropagation();
                              onOpenTaskEditor(tk.id);
                            }}
                          >
                            {tk.scheduleState === 'TODAY' && (
                              <Sun
                                size={11}
                                style={{ color: 'var(--today-star)', flexShrink: 0 }}
                              />
                            )}
                            {pt.isActivelyBlocked && (
                              <Link2 size={10.5} style={{ flexShrink: 0 }} />
                            )}
                            {tk.effortLevel === 'QUICK_WIN' && <span>⚡</span>}
                            {tk.effortLevel === 'DEEP_FOCUS' && <span>🧠</span>}

                            <span className="roadmap-task-bar-title">
                              {tk.title || t.placeholderTodoTitle}
                            </span>

                            {durBadge && barWidth >= 86 && (
                              <span className="roadmap-task-bar-pill">{durBadge}</span>
                            )}
                            {tk.deadline != null && barWidth >= 70 && (
                              <Flag
                                size={10}
                                style={{
                                  color: dlUrgent
                                    ? 'var(--deadline-red)'
                                    : 'var(--things-blue)',
                                  flexShrink: 0
                                }}
                              />
                            )}

                            {taskDragDelta !== 0 && (
                              <span className="roadmap-drag-delta-pill">
                                {taskDragDelta > 0
                                  ? `+${taskDragDelta}d`
                                  : `${taskDragDelta}d`}
                              </span>
                            )}
                          </div>
                        </div>
                      </div>
                    );
                  })}
                </React.Fragment>
              );
            })}
          </div>
        </div>
      </div>

      {/* Compact Unscheduled Tray Below Timeline */}
      {(layout.unscheduledTasks.length > 0 || layout.unscheduledPhases.length > 0) && (
        <section
          className="roadmap-unscheduled-tray"
          data-testid="roadmap-unscheduled-tray"
        >
          <div className="roadmap-unscheduled-header">
            <div className="roadmap-unscheduled-title-group">
              <span className="roadmap-unscheduled-title">
                {t.roadmapUnscheduledSection} (
                {layout.unscheduledTasks.length + layout.unscheduledPhases.length})
              </span>
              <span className="roadmap-unscheduled-hint">
                {t.roadmapUnscheduledHint}
              </span>
            </div>
          </div>

          <div className="roadmap-unscheduled-list">
            {layout.unscheduledPhases.map((hd) => (
              <div key={`unsch-phase-${hd.id}`} className="roadmap-unscheduled-item">
                <div className="roadmap-unscheduled-item-left">
                  <span className="board-column-accent" />
                  <span className="roadmap-unscheduled-name">
                    {hd.title || t.entityHeading}
                  </span>
                  <span className="meta-badge">{t.entityHeading}</span>
                </div>
                <div className="roadmap-unscheduled-actions">
                  <button
                    type="button"
                    className="chip-btn"
                    onClick={() => {
                      const today = startOfDayEpoch();
                      void localDb.updateHeadingDates(
                        hd.id,
                        today,
                        addDaysLocal(today, 6)
                      );
                    }}
                  >
                    <Sun size={11.5} style={{ color: 'var(--today-star)' }} />
                    <span>{t.viewToday}</span>
                  </button>
                  <button
                    type="button"
                    className="chip-btn"
                    onClick={() => onOpenHeadingStartModal(hd.id)}
                  >
                    <Calendar size={11.5} />
                    <span>{t.phaseSetStartDate}</span>
                  </button>
                </div>
              </div>
            ))}

            {layout.unscheduledTasks.map((tk) => {
              const isSelected = selectedTaskIds.includes(tk.id);
              const dragIds =
                isSelected && selectedTaskIds.length > 1
                  ? orderTaskIdsByVisualOrder(selectedTaskIds, orderedVisibleTaskIds)
                  : [tk.id];
              return (
                <div
                  key={`unsch-task-${tk.id}`}
                  className={`roadmap-unscheduled-item ${isSelected ? 'selected' : ''}`}
                  draggable
                  onDragStart={(e) => {
                    attachMultiTaskDragPreview(
                      e.dataTransfer,
                      dragIds,
                      t.dragTasksCount(dragIds.length)
                    );
                  }}
                >
                  <div className="roadmap-unscheduled-item-left">
                    <button
                      type="button"
                      className="things-checkbox"
                      onClick={() =>
                        void localDb.upsertTask({ id: tk.id, status: 'COMPLETED' })
                      }
                    />
                    <button
                      type="button"
                      className="roadmap-unscheduled-task-btn"
                      onClick={() => onOpenTaskEditor(tk.id)}
                    >
                      {tk.title || t.placeholderTodoTitle}
                    </button>
                  </div>

                  <div className="roadmap-unscheduled-actions">
                    <button
                      type="button"
                      className="chip-btn"
                      onClick={() =>
                        void localDb.upsertTask({
                          id: tk.id,
                          scheduleState: 'TODAY',
                          whenDate: startOfDayEpoch()
                        })
                      }
                    >
                      <Sun size={11.5} style={{ color: 'var(--today-star)' }} />
                      <span>{t.viewToday}</span>
                    </button>
                    <button
                      type="button"
                      className="chip-btn"
                      onClick={() =>
                        void localDb.upsertTask({
                          id: tk.id,
                          scheduleState: 'UPCOMING',
                          whenDate: addDaysLocal(startOfDayEpoch(), 1)
                        })
                      }
                    >
                      <CalendarDays size={11.5} />
                      <span>{t.dateTomorrow}</span>
                    </button>
                    <button
                      type="button"
                      className="chip-btn"
                      onClick={() => onOpenWhenModal(tk.id)}
                    >
                      <Calendar size={11.5} />
                      <span>{t.actionWhen}</span>
                    </button>
                  </div>
                </div>
              );
            })}
          </div>
        </section>
      )}
    </div>
  );
};
