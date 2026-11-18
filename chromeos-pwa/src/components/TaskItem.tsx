import React, { useEffect, useLayoutEffect, useRef, useState } from 'react';
import { createPortal } from 'react-dom';
import {
  Archive,
  ArrowDown,
  ArrowUp,
  Bell,
  Calendar,
  Camera,
  Check,
  CheckCircle2,
  CheckSquare,
  Circle,
  Clock,
  Copy,
  Flag,
  FolderInput,
  GripVertical,
  Layers,
  Mic,
  Moon,
  MoreHorizontal,
  Paperclip,
  Plus,
  Repeat,
  RotateCcw,
  Sparkles,
  Star,
  Tag,
  Timer,
  Trash2,
  X,
  XCircle
} from 'lucide-react';
import {
  daysBetweenToday,
  formatRecurrenceShort,
  type AreaRecord,
  type AttachmentRecord,
  type ChecklistItemRecord,
  type ProjectRecord,
  type SupportedLang,
  type TagRecord,
  type TaskRecord,
  type TaskStatus
} from '../domain/types';
import {
  DEFAULT_TIMEBLOCK_DURATION_MINUTES,
  TIMEBLOCK_DURATION_PRESETS,
  TIMEBLOCK_QUICK_START_MINUTES,
  computeClampedContextMenuPosition,
  formatMinutesAsClock,
  formatTimeblockRange,
  parseClockToMinutes,
  shouldAutoFocusTitleOnExpand,
  shouldShowQuickThisEveningPill
} from '../domain/v2Utils';
import { generateMagicChecklist } from '../data/geminiService';
import { localDb } from '../data/localDb';
import type { TranslationStrings } from '../i18n/translations';
import { extractDetectedLinksFromNotes } from '../domain/powerUtils';
import { DetectedNotesLinksRow, MarkdownNotesField } from './MarkdownNotesField';
import { AttachmentsStrip } from './AttachmentStrip';
import { processAndAddAttachmentFile } from '../data/attachmentHelpers';

interface ChecklistSubtaskRowProps {
  item: ChecklistItemRecord;
  idx: number;
  totalCount: number;
  taskId: string;
  t: TranslationStrings;
  registerRef: (el: HTMLInputElement | null) => void;
  onFocusAddBottom: () => void;
  onFocusNewSubtask: (newId: string) => void;
  onSwapWith: (otherItemId: string) => void;
  onRequestEnsureVisible?: (preferBottomVisible: boolean) => void;
}

const ChecklistSubtaskRow: React.FC<ChecklistSubtaskRowProps> = ({
  item,
  idx,
  totalCount,
  taskId,
  t,
  registerRef,
  onFocusAddBottom,
  onFocusNewSubtask,
  onSwapWith,
  onRequestEnsureVisible
}) => {
  const [subTitle, setSubTitle] = useState(item.title);
  const [prevItemTitle, setPrevItemTitle] = useState(item.title);
  const [isFocused, setIsFocused] = useState(false);

  if (prevItemTitle !== item.title) {
    setPrevItemTitle(item.title);
    if (!isFocused) {
      setSubTitle(item.title);
    }
  }

  return (
    <div className="checklist-row">
      <button
        type="button"
        className={`checklist-circle ${item.isCompleted ? 'done' : ''}`}
        title={item.isCompleted ? t.actionReopenTodo : t.actionComplete}
        onClick={() =>
          void localDb.updateChecklistItem(item.id, { isCompleted: !item.isCompleted })
        }
      >
        {item.isCompleted && <Check size={10} strokeWidth={3} />}
      </button>

      <input
        ref={registerRef}
        type="text"
        className={`checklist-input ${item.isCompleted ? 'done' : ''}`}
        value={subTitle}
        onPointerDown={() => onRequestEnsureVisible?.(true)}
        onFocus={() => {
          setIsFocused(true);
          onRequestEnsureVisible?.(true);
        }}
        onBlur={() => {
          setIsFocused(false);
          if (subTitle !== item.title) {
            void localDb.updateChecklistItem(item.id, { title: subTitle });
          }
        }}
        onChange={(e) => {
          const next = e.target.value;
          setSubTitle(next);
          void localDb.updateChecklistItem(item.id, { title: next });
        }}
        onKeyDown={async (e) => {
          if (e.key === 'Enter') {
            e.preventDefault();
            const inputEl = e.currentTarget;
            const val = subTitle;
            if (!val.trim()) {
              await localDb.deleteChecklistItem(item.id);
              onFocusAddBottom();
              return;
            }
            const cursor = inputEl.selectionStart ?? val.length;
            if (cursor < val.length) {
              const leftPart = val.slice(0, cursor).trimEnd();
              const rightPart = val.slice(cursor).trimStart();
              setSubTitle(leftPart);
              const newId = await localDb.insertChecklistItemAfter(
                taskId,
                item.id,
                rightPart,
                leftPart
              );
              onFocusNewSubtask(newId);
            } else {
              const newId = await localDb.insertChecklistItemAfter(taskId, item.id, '', val);
              onFocusNewSubtask(newId);
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
          title={t.actionMove}
          onClick={() => onSwapWith('up')}
        >
          <ArrowUp size={12.5} />
        </button>
        <button
          type="button"
          className="icon-btn"
          style={{ height: 22, minWidth: 22, padding: 0 }}
          disabled={idx === totalCount - 1}
          title={t.actionMove}
          onClick={() => onSwapWith('down')}
        >
          <ArrowDown size={12.5} />
        </button>
        <button
          type="button"
          className="icon-btn"
          style={{ height: 22, minWidth: 22, padding: 0 }}
          title={t.actionDelete}
          onClick={() => void localDb.deleteChecklistItem(item.id)}
        >
          <X size={13} />
        </button>
      </div>
    </div>
  );
};

interface TaskItemProps {
  task: TaskRecord;
  isExpanded: boolean;
  isTodayView?: boolean;
  isSelected: boolean;
  isMultiSelectMode: boolean;
  showContextSubtitle: boolean;
  showScheduleBadgeInCollapsed: boolean;
  quickFindOpen: boolean;
  currentDayMarker?: number;
  areasMap: Map<string, AreaRecord>;
  projectsMap: Map<string, ProjectRecord>;
  tagsMap: Map<string, TagRecord>;
  checklistItems: ChecklistItemRecord[];
  attachments: AttachmentRecord[];
  lang: SupportedLang;
  geminiApiKey: string;
  t: TranslationStrings;
  onSelect: (multiToggle: boolean) => void;
  onExpand: () => void;
  onCollapse: () => void;
  onRequestEnsureVisible?: (preferBottomVisible: boolean) => void;
  onOpenWhenModal: (taskId: string) => void;
  onOpenDeadlineModal: (taskId: string) => void;
  onOpenRepeatModal: (taskId: string) => void;
  onOpenMoveModal: (taskId: string) => void;
  onOpenTagsModal: (taskId: string) => void;
  onStartFocusTimer: (
    taskId: string,
    taskTitle: string,
    durationMinutes?: number | null
  ) => void;
  onDuplicateTask: (taskId: string) => void;
  onDropReorder: (draggedTaskId: string, targetTaskId: string) => void;
  onToast: (msg: string) => void;
}

export const TaskItem: React.FC<TaskItemProps> = ({
  task,
  isExpanded,
  isTodayView = false,
  isSelected,
  isMultiSelectMode,
  showContextSubtitle,
  showScheduleBadgeInCollapsed,
  quickFindOpen,
  areasMap,
  projectsMap,
  tagsMap,
  checklistItems,
  attachments,
  lang,
  geminiApiKey,
  t,
  onSelect,
  onExpand,
  onCollapse,
  onRequestEnsureVisible,
  onOpenWhenModal,
  onOpenDeadlineModal,
  onOpenRepeatModal,
  onOpenMoveModal,
  onOpenTagsModal,
  onStartFocusTimer,
  onDuplicateTask,
  onDropReorder,
  onToast
}) => {
  const [prevTaskId, setPrevTaskId] = useState(task.id);
  const [prevServerTitle, setPrevServerTitle] = useState(task.title);
  const [prevServerNotes, setPrevServerNotes] = useState(task.notes);
  const [titleDraft, setTitleDraft] = useState(task.title);
  const [notesDraft, setNotesDraft] = useState(task.notes);
  const [isTitleFocused, setIsTitleFocused] = useState(false);
  const [isNotesFocused, setIsNotesFocused] = useState(false);
  const [newSubtask, setNewSubtask] = useState('');
  const [animStage, setAnimStage] = useState<'idle' | 'strike' | 'glide'>('idle');
  const [statusMenuOpen, setStatusMenuOpen] = useState(false);
  const [statusMenuFlipUp, setStatusMenuFlipUp] = useState(false);
  const [timeblockOpen, setTimeblockOpen] = useState(false);
  const [inlineMoreMenuOpen, setInlineMoreMenuOpen] = useState(false);
  const [ctxMenu, setCtxMenu] = useState<{
    anchorX: number;
    anchorY: number;
    x: number;
    y: number;
  } | null>(null);
  const [dragOver, setDragOver] = useState(false);
  const [fileDropOver, setFileDropOver] = useState(false);
  const [loadingAi, setLoadingAi] = useState(false);
  const [focusSubtaskId, setFocusSubtaskId] = useState<string | null>(null);

  const strikeTimerRef = useRef<number | null>(null);
  const glideTimerRef = useRef<number | null>(null);
  const ctxMenuRef = useRef<HTMLDivElement | null>(null);
  const titleInputRef = useRef<HTMLInputElement | null>(null);
  const subtaskRefs = useRef<Record<string, HTMLInputElement | null>>({});
  const addSubtaskInputRef = useRef<HTMLInputElement | null>(null);
  const attachmentFileInputRef = useRef<HTMLInputElement | null>(null);
  const cameraCaptureInputRef = useRef<HTMLInputElement | null>(null);
  const newSubtaskRef = useRef(newSubtask);
  newSubtaskRef.current = newSubtask;
  const wasExpandedRef = useRef(isExpanded);

  if (prevTaskId !== task.id) {
    setPrevTaskId(task.id);
    setPrevServerTitle(task.title);
    setPrevServerNotes(task.notes);
    setTitleDraft(task.title);
    setNotesDraft(task.notes);
    setTimeblockOpen(false);
    setInlineMoreMenuOpen(false);
  } else {
    if (prevServerTitle !== task.title) {
      setPrevServerTitle(task.title);
      if (!isTitleFocused) {
        setTitleDraft(task.title);
      }
    }
    if (prevServerNotes !== task.notes) {
      setPrevServerNotes(task.notes);
      if (!isNotesFocused && notesDraft !== task.notes) {
        setNotesDraft(task.notes);
      }
    }
  }

  const flushPendingSubtaskIfNeeded = () => {
    const pending = newSubtaskRef.current.trim();
    if (!pending) return;
    newSubtaskRef.current = '';
    setNewSubtask('');
    void localDb.addChecklistItems(task.id, [pending]);
  };

  const triggerInlineVoiceDictation = () => {
    const w = window as unknown as {
      SpeechRecognition?: new () => {
        lang: string;
        interimResults: boolean;
        onresult: ((ev: { results: ArrayLike<ArrayLike<{ transcript: string }>> }) => void) | null;
        start: () => void;
      };
      webkitSpeechRecognition?: new () => {
        lang: string;
        interimResults: boolean;
        onresult: ((ev: { results: ArrayLike<ArrayLike<{ transcript: string }>> }) => void) | null;
        start: () => void;
      };
    };
    const Ctor = w.SpeechRecognition ?? w.webkitSpeechRecognition;
    if (!Ctor) {
      onToast(t.voiceDictationLabel);
      return;
    }
    try {
      const rec = new Ctor();
      rec.lang = lang === 'de' ? 'de-DE' : lang === 'fr' ? 'fr-FR' : lang === 'it' ? 'it-IT' : lang === 'es' ? 'es-ES' : 'en-US';
      rec.interimResults = false;
      rec.onresult = (ev) => {
        const spoken = ev.results?.[0]?.[0]?.transcript?.trim() ?? '';
        if (!spoken) return;
        if (!titleDraft.trim()) {
          setTitleDraft(spoken);
          void localDb.upsertTask({ id: task.id, title: spoken });
        } else {
          const merged = notesDraft.trim() ? `${notesDraft.trim()}\n${spoken}` : spoken;
          setNotesDraft(merged);
          void localDb.upsertTask({ id: task.id, notes: merged });
        }
      };
      onToast(t.voiceListeningHint);
      rec.start();
    } catch {
      onToast(t.voiceDictationLabel);
    }
  };

  useEffect(() => {
    if (isExpanded && !wasExpandedRef.current) {
      if (shouldAutoFocusTitleOnExpand(task.title) && titleInputRef.current) {
        titleInputRef.current.focus();
        titleInputRef.current.setSelectionRange(0, 0);
      }
    } else if (wasExpandedRef.current && !isExpanded) {
      flushPendingSubtaskIfNeeded();
      setIsNotesFocused(false);
      setIsTitleFocused(false);
      setTimeblockOpen(false);
      setInlineMoreMenuOpen(false);
    }
    wasExpandedRef.current = isExpanded;
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [isExpanded, task.id]);

  useEffect(() => {
    if (isExpanded && shouldAutoFocusTitleOnExpand(task.title) && titleInputRef.current) {
      titleInputRef.current.focus();
      titleInputRef.current.setSelectionRange(0, 0);
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

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

  useLayoutEffect(() => {
    if (!ctxMenu) return;
    const reclampMenu = () => {
      const el = ctxMenuRef.current;
      if (!el) return;
      const rect = el.getBoundingClientRect();
      const clamped = computeClampedContextMenuPosition(
        ctxMenu.anchorX,
        ctxMenu.anchorY,
        rect.width,
        rect.height,
        window.innerWidth,
        window.innerHeight,
        12
      );
      setCtxMenu((prev) => {
        if (!prev) return null;
        if (prev.x === clamped.left && prev.y === clamped.top) return prev;
        return { ...prev, x: clamped.left, y: clamped.top };
      });
    };

    reclampMenu();
    const handleKeyDown = (ev: KeyboardEvent) => {
      if (ev.key === 'Escape') {
        setCtxMenu(null);
      }
    };
    window.addEventListener('resize', reclampMenu);
    window.addEventListener('keydown', handleKeyDown);
    return () => {
      window.removeEventListener('resize', reclampMenu);
      window.removeEventListener('keydown', handleKeyDown);
    };
  }, [ctxMenu, lang, isSelected, task.status, task.durationMinutes]);

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

  const flushDraftsIfNeeded = () => {
    if (!isExpanded) return;
    flushPendingSubtaskIfNeeded();
    const patch: { id: string; title?: string; notes?: string } = { id: task.id };
    let changed = false;
    if (titleDraft !== task.title) {
      patch.title = titleDraft;
      changed = true;
    }
    if (notesDraft !== task.notes) {
      patch.notes = notesDraft;
      changed = true;
    }
    if (changed) {
      void localDb.upsertTask(patch);
    }
  };

  const commitStatus = async (nextStatus: TaskStatus) => {
    flushDraftsIfNeeded();
    await localDb.upsertTask({
      id: task.id,
      status: nextStatus
    });
  };

  const updateCheckboxMenuPlacement = (target: EventTarget | null) => {
    if (target instanceof HTMLElement) {
      const rect = target.getBoundingClientRect();
      setStatusMenuFlipUp(rect.bottom + 115 > window.innerHeight - 12);
    }
  };

  const handlePrimaryCheckboxClick = (e: React.MouseEvent) => {
    e.stopPropagation();
    if (e.altKey) {
      updateCheckboxMenuPlacement(e.currentTarget);
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
    updateCheckboxMenuPlacement(e.currentTarget);
    setStatusMenuOpen(true);
  };

  const handleDoneCollapse = () => {
    flushPendingSubtaskIfNeeded();
    const cleanTitle = titleDraft.trim();
    if (cleanTitle !== task.title || notesDraft !== task.notes) {
      void localDb.upsertTask({
        id: task.id,
        title: cleanTitle,
        notes: notesDraft
      });
    }
    onCollapse();
  };

  const runMagicBreakdown = async () => {
    if (loadingAi) return;
    flushDraftsIfNeeded();
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

  const taskAttachments = attachments
    .filter((a) => !a.isDeleted && a.taskId === task.id)
    .sort((a, b) => a.createdAt - b.createdAt);

  const taskTags = task.tagIds
    .map((id) => tagsMap.get(id))
    .filter((tg): tg is TagRecord => Boolean(tg));

  const timeblockRangeLabel =
    task.scheduledStartMinutes != null
      ? formatTimeblockRange(task.scheduledStartMinutes, task.durationMinutes)
      : '';
  const focusDurationMins =
    task.durationMinutes != null && task.durationMinutes > 0 ? task.durationMinutes : 25;

  const formatReminderClock = (ms: number) => {
    const d = new Date(ms);
    return `${String(d.getHours()).padStart(2, '0')}:${String(d.getMinutes()).padStart(2, '0')}`;
  };

  const formatShortDate = (ms: number) =>
    new Intl.DateTimeFormat(lang, { month: 'short', day: 'numeric' }).format(new Date(ms));

  const renderDeadlineMeta = (deadlineMs: number) => {
    const diff = daysBetweenToday(deadlineMs);
    const shortDate = formatShortDate(deadlineMs);
    const relative =
      diff === 0
        ? t.deadlineToday
        : diff > 0
          ? t.deadlineDaysLeft(diff)
          : t.deadlineDaysOverdue(Math.abs(diff));
    const label = `${shortDate} · ${relative}`;
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
            style={{
              left: 0,
              right: 'auto',
              minWidth: 185,
              ...(statusMenuFlipUp ? { top: 'auto', bottom: 'calc(100% + 6px)' } : {})
            }}
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
              : task.scheduleState === 'ANYTIME'
                ? t.viewAnytime
                : t.actionWhen;

    const dlMeta = task.deadline != null ? renderDeadlineMeta(task.deadline) : null;
    const currentDuration = task.durationMinutes ?? DEFAULT_TIMEBLOCK_DURATION_MINUTES;

    return (
      <div
        className="task-card-expanded"
        data-task-id={task.id}
        style={
          fileDropOver
            ? { outline: '2px dashed var(--things-blue)', outlineOffset: -2 }
            : undefined
        }
        onClick={(e) => e.stopPropagation()}
        onDragOver={(e) => {
          if (e.dataTransfer.types.includes('Files')) {
            e.preventDefault();
            setFileDropOver(true);
          }
        }}
        onDragLeave={() => setFileDropOver(false)}
        onDrop={async (e) => {
          if (e.dataTransfer.files && e.dataTransfer.files.length > 0) {
            e.preventDefault();
            setFileDropOver(false);
            const files = Array.from(e.dataTransfer.files);
            for (const f of files) {
              await processAndAddAttachmentFile(f, { taskId: task.id }, t, onToast);
            }
          }
        }}
        onKeyDown={(e) => {
          if (e.key === 'Escape') {
            e.stopPropagation();
            if (timeblockOpen) {
              setTimeblockOpen(false);
              return;
            }
            handleDoneCollapse();
          }
        }}
      >
        <div className="task-card-top">
          {renderCheckboxWithStatusMenu()}
          <input
            ref={titleInputRef}
            type="text"
            className="task-card-title-input"
            value={titleDraft}
            onPointerDown={() => onRequestEnsureVisible?.(false)}
            onFocus={() => {
              setIsTitleFocused(true);
              onRequestEnsureVisible?.(false);
            }}
            onBlur={() => {
              setIsTitleFocused(false);
              if (titleDraft !== task.title) {
                void localDb.upsertTask({ id: task.id, title: titleDraft });
              }
            }}
            onChange={(e) => {
              const nextTitle = e.target.value;
              setTitleDraft(nextTitle);
              void localDb.upsertTask({ id: task.id, title: nextTitle });
            }}
            onKeyDown={(e) => {
              if (e.key === 'Enter') {
                e.preventDefault();
                handleDoneCollapse();
              }
            }}
            placeholder={t.placeholderTodoTitle}
          />
        </div>

        <MarkdownNotesField
          value={notesDraft}
          onChange={(nextNotes) => {
            setNotesDraft(nextNotes);
            void localDb.upsertTask({ id: task.id, notes: nextNotes });
          }}
          onFocusChange={setIsNotesFocused}
          placeholder={t.placeholderNotes}
          className="task-card-notes"
        />

        {/* Attachments Strip (v1.8.0) */}
        <AttachmentsStrip
          taskId={task.id}
          attachments={taskAttachments}
          t={t}
          onToast={onToast}
          showAddButton={taskAttachments.length > 0}
          fileInputRef={attachmentFileInputRef}
        />

        <input
          ref={cameraCaptureInputRef}
          type="file"
          accept="image/*"
          capture="environment"
          style={{ display: 'none' }}
          onChange={async (e) => {
            const files = Array.from(e.target.files ?? []);
            e.target.value = '';
            for (const f of files) {
              await processAndAddAttachmentFile(f, { taskId: task.id }, t, onToast);
            }
          }}
        />

        {/* Checklist with Enter Cursor Split / Insert Below + Multiline Paste + Inline Magic Breakdown */}
        <div className="checklist-section">
          {activeChecklists.length > 0 && (
            <div className="checklist-header">
              {t.actionChecklist} ({doneChecklistsCount}/{activeChecklists.length})
            </div>
          )}

          {activeChecklists.map((item, idx) => (
            <ChecklistSubtaskRow
              key={item.id}
              item={item}
              idx={idx}
              totalCount={activeChecklists.length}
              taskId={task.id}
              t={t}
              registerRef={(el) => {
                subtaskRefs.current[item.id] = el;
              }}
              onFocusAddBottom={() => addSubtaskInputRef.current?.focus()}
              onFocusNewSubtask={(newId) => setFocusSubtaskId(newId)}
              onRequestEnsureVisible={onRequestEnsureVisible}
              onSwapWith={(dir) => {
                const other = dir === 'up' ? activeChecklists[idx - 1] : activeChecklists[idx + 1];
                if (other) {
                  void localDb.swapChecklistOrder(item.id, other.id);
                }
              }}
            />
          ))}

          <div className="checklist-add-row checklist-add-boxed">
            <Circle size={13.5} style={{ color: 'var(--things-blue)', flexShrink: 0 }} />
            <input
              ref={addSubtaskInputRef}
              type="text"
              className="checklist-input"
              value={newSubtask}
              onPointerDown={() => onRequestEnsureVisible?.(true)}
              onFocus={() => onRequestEnsureVisible?.(true)}
              onChange={(e) => setNewSubtask(e.target.value)}
              onPaste={(e) => {
                const pasted = e.clipboardData.getData('text');
                if (pasted && /\r?\n/.test(pasted)) {
                  e.preventDefault();
                  const lines = pasted
                    .split(/\r?\n/)
                    .map((line) => line.replace(/^[-*•[\]xX\s]+/, '').trim())
                    .filter(Boolean);
                  if (lines.length > 0) {
                    setNewSubtask('');
                    void localDb.addChecklistItems(task.id, lines);
                  }
                }
              }}
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
                title={t.actionChecklist}
                onClick={async () => {
                  const txt = newSubtask.trim();
                  setNewSubtask('');
                  await localDb.addChecklistItems(task.id, [txt]);
                }}
              >
                <Plus size={12} />
              </button>
            )}
            <button
              type="button"
              className="chip-btn gemini checklist-magic-inline-btn"
              disabled={loadingAi}
              title={t.geminiMagicBreakdown}
              onClick={() => void runMagicBreakdown()}
            >
              <Sparkles size={12.5} />
              <span>{loadingAi ? t.geminiGenerating : t.geminiMagicBreakdown}</span>
            </button>
          </div>
        </div>

        {/* v2.0 Inline Interactive Timeblock Panel (opens right above the unified footer when toggled) */}
        {timeblockOpen && (
          <div className="v2-timeblock-panel" data-testid="task-timeblock-panel" style={{ marginLeft: 31 }}>
            <div className="v2-timeblock-row">
              <span className="v2-timeblock-label">{t.timeblockStartTime}</span>
              <input
                type="time"
                className="v2-inline-time-input"
                aria-label={t.timeblockStartTime}
                value={
                  task.scheduledStartMinutes != null
                    ? formatMinutesAsClock(task.scheduledStartMinutes)
                    : ''
                }
                onChange={(e) => {
                  const mins = parseClockToMinutes(e.target.value);
                  if (mins != null) {
                    void localDb.upsertTask({
                      id: task.id,
                      scheduledStartMinutes: mins,
                      durationMinutes: task.durationMinutes ?? DEFAULT_TIMEBLOCK_DURATION_MINUTES
                    });
                  }
                }}
              />
              <div className="v2-timeblock-slots">
                {TIMEBLOCK_QUICK_START_MINUTES.map((mins) => (
                  <button
                    key={mins}
                    type="button"
                    className={`chip-btn ${task.scheduledStartMinutes === mins ? 'active' : ''}`}
                    title={`${t.timeblockStartTime}: ${formatMinutesAsClock(mins)}`}
                    onClick={() =>
                      void localDb.upsertTask({
                        id: task.id,
                        scheduledStartMinutes: mins,
                        durationMinutes:
                          task.durationMinutes ?? DEFAULT_TIMEBLOCK_DURATION_MINUTES
                      })
                    }
                  >
                    {formatMinutesAsClock(mins)}
                  </button>
                ))}
              </div>
            </div>

            <div className="v2-timeblock-row" style={{ marginTop: 8 }}>
              <span className="v2-timeblock-label">{t.timeblockDuration}</span>
              <div className="v2-timeblock-slots">
                {TIMEBLOCK_DURATION_PRESETS.map((dur) => (
                  <button
                    key={dur}
                    type="button"
                    className={`chip-btn ${currentDuration === dur ? 'active' : ''}`}
                    title={`${t.timeblockDuration}: ${dur}m`}
                    onClick={() =>
                      void localDb.upsertTask({
                        id: task.id,
                        scheduledStartMinutes: task.scheduledStartMinutes ?? 9 * 60,
                        durationMinutes: dur
                      })
                    }
                  >
                    {dur}m
                  </button>
                ))}
              </div>

              {(task.scheduledStartMinutes != null || task.durationMinutes != null) && (
                <button
                  type="button"
                  className="chip-btn urgent"
                  style={{ marginLeft: 'auto' }}
                  title={t.timeblockClear}
                  onClick={() =>
                    void localDb.upsertTask({
                      id: task.id,
                      scheduledStartMinutes: null,
                      durationMinutes: null
                    })
                  }
                >
                  <X size={11.5} />
                  <span>{t.timeblockClear}</span>
                </button>
              )}
            </div>
          </div>
        )}

        {/* v2.1 Unified Single Footer Row: Labeled Metadata Pills on Left + [Focus, ⋯ Menu, ✓ Done] on Right */}
        <div className="task-card-unified-footer" data-testid="task-unified-footer">
          <div className="task-card-chips-row">
            {/* 1. When Pill */}
            <button
              type="button"
              className={`chip-btn ${
                task.scheduleState === 'TODAY' ||
                task.scheduleState === 'THIS_EVENING' ||
                task.whenDate != null
                  ? 'active'
                  : ''
              }`}
              title={t.ctxWhenReminder}
              onClick={() => {
                flushDraftsIfNeeded();
                onOpenWhenModal(task.id);
              }}
            >
              {task.scheduleState === 'TODAY' ? (
                <Star size={12} fill="var(--today-star)" style={{ color: 'var(--today-star)' }} />
              ) : task.scheduleState === 'THIS_EVENING' ? (
                <Moon size={12} />
              ) : task.scheduleState === 'SOMEDAY' ? (
                <Archive size={12} />
              ) : task.scheduleState === 'ANYTIME' && task.whenDate == null ? (
                <Layers size={12} />
              ) : (
                <Calendar size={12} />
              )}
              <span>{whenChipText}</span>
            </button>

            {/* 1a. Today-View Exclusive 1-Click "This Evening" + Immediate Close Pill */}
            {shouldShowQuickThisEveningPill({
              isTodayView,
              status: task.status,
              scheduleState: task.scheduleState
            }) && (
              <button
                type="button"
                data-testid="task-quick-this-evening-chip"
                className="chip-btn evening-quick"
                title={t.viewThisEvening}
                onClick={() => {
                  flushPendingSubtaskIfNeeded();
                  const now = new Date();
                  const todayMidnight = new Date(
                    now.getFullYear(),
                    now.getMonth(),
                    now.getDate(),
                    0,
                    0,
                    0,
                    0
                  ).getTime();
                  void localDb.upsertTask({
                    id: task.id,
                    title: titleDraft,
                    notes: notesDraft,
                    scheduleState: 'THIS_EVENING',
                    whenDate: todayMidnight
                  });
                  onCollapse();
                }}
              >
                <Moon size={12} />
                <span>{t.viewThisEvening}</span>
              </button>
            )}

            {/* 1b. v2.0 Timeblock Chip */}
            <button
              type="button"
              data-testid="task-timeblock-chip"
              className={`chip-btn ${
                task.scheduledStartMinutes != null || timeblockOpen ? 'active' : ''
              }`}
              title={t.actionTimeblock}
              onClick={() => {
                flushDraftsIfNeeded();
                setTimeblockOpen((v) => !v);
              }}
            >
              <Clock size={12} />
              <span>
                {timeblockRangeLabel ? `🕐 ${timeblockRangeLabel}` : `⏱️ ${t.actionTimeblock}`}
              </span>
            </button>

            {/* 2. Reminder Pill (when set) */}
            {task.reminderTime != null && (
              <button
                type="button"
                className="chip-btn active"
                title={`${t.actionReminder}: ${formatReminderClock(task.reminderTime)}`}
                onClick={() => {
                  flushDraftsIfNeeded();
                  onOpenWhenModal(task.id);
                }}
              >
                <Bell size={12} />
                <span>{formatReminderClock(task.reminderTime)}</span>
              </button>
            )}

            {/* 3. Repeat Pill */}
            <button
              type="button"
              className={`chip-btn ${task.recurrenceRule != null ? 'active' : ''}`}
              title={t.actionRepeat}
              onClick={() => {
                flushDraftsIfNeeded();
                onOpenRepeatModal(task.id);
              }}
            >
              <Repeat size={12} />
              <span>
                {task.recurrenceRule != null
                  ? formatRecurrenceShort(task.recurrenceRule, lang)
                  : t.actionRepeat}
              </span>
            </button>

            {/* 4. Deadline / Due Date Pill */}
            <button
              type="button"
              className={`chip-btn ${
                dlMeta ? (dlMeta.urgent ? 'urgent' : 'active') : ''
              }`}
              title={dlMeta ? `${t.actionDeadline}: ${dlMeta.label}` : t.actionDeadline}
              onClick={() => {
                flushDraftsIfNeeded();
                onOpenDeadlineModal(task.id);
              }}
            >
              <Flag
                size={12}
                style={
                  dlMeta == null ? { color: 'var(--deadline-red)', opacity: 0.85 } : undefined
                }
              />
              <span>{dlMeta ? dlMeta.label : t.actionDeadline}</span>
            </button>

            {/* 5. Project / Area Container Pill */}
            <button
              type="button"
              className={`chip-btn ${containerLabel ? 'active' : ''}`}
              title={t.ctxMoveContainer}
              onClick={() => {
                flushDraftsIfNeeded();
                onOpenMoveModal(task.id);
              }}
            >
              <FolderInput size={12} />
              <span>{containerLabel || `${t.entityProject} / ${t.entityArea}`}</span>
            </button>

            {/* 6. Assigned Tag Pills + "+ Tags" */}
            {taskTags.map((tg) => (
              <button
                key={tg.id}
                type="button"
                className="chip-btn active"
                title={`${t.entityTag}: ${tg.title}`}
                onClick={() => {
                  flushDraftsIfNeeded();
                  onOpenTagsModal(task.id);
                }}
              >
                <Tag size={11.5} />
                <span>{tg.title}</span>
              </button>
            ))}

            <button
              type="button"
              className="chip-btn"
              title={t.ctxAssignTags}
              onClick={() => {
                flushDraftsIfNeeded();
                onOpenTagsModal(task.id);
              }}
            >
              <Plus size={11.5} />
              <span>{t.entityTags}</span>
            </button>
          </div>

          <div className="task-card-actions">
            {task.status === 'OPEN' && (
              <button
                type="button"
                className="icon-btn"
                title={t.actionFocusTimerMinutes(focusDurationMins)}
                data-testid="task-start-focus-btn"
                onClick={() => {
                  flushDraftsIfNeeded();
                  onStartFocusTimer(
                    task.id,
                    titleDraft || task.title,
                    task.durationMinutes ?? 25
                  );
                }}
              >
                <Timer size={15.5} />
              </button>
            )}

            <div style={{ position: 'relative', display: 'inline-flex' }}>
              <button
                type="button"
                className="chip-btn task-more-menu-trigger"
                title={t.actionMoreOptions}
                data-testid="task-more-menu-btn"
                onClick={(e) => {
                  e.stopPropagation();
                  setInlineMoreMenuOpen((v) => !v);
                }}
              >
                <MoreHorizontal size={15.5} />
              </button>

              {inlineMoreMenuOpen && (
                <>
                  <div
                    style={{ position: 'fixed', inset: 0, zIndex: 85 }}
                    onClick={(e) => {
                      e.stopPropagation();
                      setInlineMoreMenuOpen(false);
                    }}
                  />
                  <div
                    className="dropdown-popover"
                    style={{
                      right: 0,
                      left: 'auto',
                      top: 'auto',
                      bottom: 'calc(100% + 6px)',
                      minWidth: 215,
                      zIndex: 90
                    }}
                    onClick={(e) => e.stopPropagation()}
                  >
                    <button
                      type="button"
                      className="dropdown-item"
                      onClick={() => {
                        setInlineMoreMenuOpen(false);
                        attachmentFileInputRef.current?.click();
                      }}
                    >
                      <span style={{ display: 'inline-flex', alignItems: 'center', gap: 8 }}>
                        <Paperclip size={14} style={{ color: 'var(--things-blue)' }} />
                        <span>{t.attachmentsAddPhotoBtn}</span>
                      </span>
                    </button>
                    <button
                      type="button"
                      className="dropdown-item"
                      onClick={() => {
                        setInlineMoreMenuOpen(false);
                        cameraCaptureInputRef.current?.click();
                      }}
                    >
                      <span style={{ display: 'inline-flex', alignItems: 'center', gap: 8 }}>
                        <Camera size={14} style={{ color: 'var(--things-blue)' }} />
                        <span>{t.actionTakePhoto}</span>
                      </span>
                    </button>
                    <button
                      type="button"
                      className="dropdown-item"
                      onClick={() => {
                        setInlineMoreMenuOpen(false);
                        triggerInlineVoiceDictation();
                      }}
                    >
                      <span style={{ display: 'inline-flex', alignItems: 'center', gap: 8 }}>
                        <Mic size={14} style={{ color: 'var(--things-blue)' }} />
                        <span>{t.voiceDictationLabel}</span>
                      </span>
                    </button>
                    <button
                      type="button"
                      className="dropdown-item"
                      data-testid="task-duplicate-btn"
                      onClick={() => {
                        setInlineMoreMenuOpen(false);
                        flushDraftsIfNeeded();
                        onDuplicateTask(task.id);
                      }}
                    >
                      <span style={{ display: 'inline-flex', alignItems: 'center', gap: 8 }}>
                        <Copy size={14} style={{ color: 'var(--things-blue)' }} />
                        <span>{t.actionDuplicateTodo}</span>
                      </span>
                    </button>
                    <div className="dropdown-divider" />
                    <button
                      type="button"
                      className="dropdown-item"
                      style={{ color: 'var(--deadline-red)' }}
                      onClick={() => {
                        setInlineMoreMenuOpen(false);
                        void localDb.softDeleteTasks([task.id]);
                      }}
                    >
                      <span style={{ display: 'inline-flex', alignItems: 'center', gap: 8 }}>
                        <Trash2 size={14} style={{ color: 'var(--deadline-red)' }} />
                        <span>{t.actionDelete}</span>
                      </span>
                    </button>
                  </div>
                </>
              )}
            </div>

            <button
              type="button"
              className="btn-primary"
              style={{ padding: '5px 10px', fontSize: 12.2 }}
              title={t.actionDone}
              onClick={handleDoneCollapse}
            >
              <Check size={14} />
            </button>
          </div>
        </div>
      </div>
    );
  }

  // ==================== Collapsed Row View ====================
  const showCollapsedSchedulePill =
    showScheduleBadgeInCollapsed &&
    task.status === 'OPEN' &&
    (task.scheduleState === 'TODAY' ||
      task.scheduleState === 'SOMEDAY' ||
      (task.scheduleState === 'UPCOMING' && task.whenDate != null) ||
      (task.scheduleState !== 'THIS_EVENING' && task.whenDate != null));

  const hasSecondRowBadges =
    task.scheduleState === 'THIS_EVENING' ||
    showCollapsedSchedulePill ||
    Boolean(timeblockRangeLabel) ||
    task.recurrenceRule != null ||
    task.reminderTime != null ||
    activeChecklists.length > 0 ||
    taskAttachments.length > 0 ||
    taskTags.length > 0 ||
    task.deadline != null;

  return (
    <>
      <div
        className={`task-row ${isSelected ? 'selected' : ''} ${
          animStage === 'glide' ? 'stage-glide' : ''
        } ${dragOver ? 'drag-over-row' : ''}`}
        data-task-id={task.id}
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
          if (e.ctrlKey || e.metaKey || e.shiftKey || isMultiSelectMode) {
            onSelect(true);
          } else {
            onExpand();
          }
        }}
        onContextMenu={(e) => {
          if (quickFindOpen) return;
          e.preventDefault();
          e.stopPropagation();
          const initial = computeClampedContextMenuPosition(
            e.clientX,
            e.clientY,
            256,
            510,
            window.innerWidth,
            window.innerHeight,
            12
          );
          setCtxMenu({
            anchorX: e.clientX,
            anchorY: e.clientY,
            x: initial.left,
            y: initial.top
          });
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
            <>
              <div className="task-row-notes-preview">{task.notes.trim().split('\n')[0]}</div>
              <DetectedNotesLinksRow
                links={extractDetectedLinksFromNotes(task.notes)}
                compact
              />
            </>
          )}

          {/* Ordered Second-Row Badges */}
          {hasSecondRowBadges && (
            <div className="task-row-badges-second">
              {/* 1. Schedule State Badge (This Evening everywhere; Today / Upcoming / Someday in Project & Area views) */}
              {task.scheduleState === 'THIS_EVENING' ? (
                <span className="meta-badge evening" title={t.viewThisEvening}>
                  <Moon size={10.5} />
                  <span>{t.viewThisEvening}</span>
                </span>
              ) : showCollapsedSchedulePill ? (
                task.scheduleState === 'TODAY' ? (
                  <span className="meta-badge today" title={t.viewToday}>
                    <Star size={10.5} fill="var(--today-star)" style={{ color: 'var(--today-star)' }} />
                    <span>{t.viewToday}</span>
                  </span>
                ) : task.scheduleState === 'SOMEDAY' ? (
                  <span className="meta-badge" title={t.viewSomeday}>
                    <Archive size={10.5} />
                    <span>{t.viewSomeday}</span>
                  </span>
                ) : task.whenDate != null ? (
                  <span className="meta-badge" title={`${t.actionWhen}: ${formatShortDate(task.whenDate)}`}>
                    <Calendar size={10.5} />
                    <span>{formatShortDate(task.whenDate)}</span>
                  </span>
                ) : null
              ) : null}

              {/* 1b. v2.0 Timeblock Range Pill */}
              {timeblockRangeLabel && (
                <span
                  className="meta-badge v2-timeblock-pill"
                  data-testid="task-row-timeblock-pill"
                  title={`${t.actionTimeblock}: ${timeblockRangeLabel}`}
                >
                  <span>🕐 {timeblockRangeLabel}</span>
                </span>
              )}

              {/* 2. Repeat */}
              {task.recurrenceRule != null && (
                <span
                  className="meta-badge"
                  title={`${t.actionRepeat}: ${formatRecurrenceShort(task.recurrenceRule, lang)}`}
                >
                  <Repeat size={10.5} />
                  <span>{formatRecurrenceShort(task.recurrenceRule, lang)}</span>
                </span>
              )}

              {/* 3. Reminder */}
              {task.reminderTime != null && (
                <span
                  className="meta-badge"
                  title={`${t.actionReminder}: ${formatReminderClock(task.reminderTime)}`}
                >
                  <Bell size={10.5} />
                  <span>{formatReminderClock(task.reminderTime)}</span>
                </span>
              )}

              {/* 4. Checklist */}
              {activeChecklists.length > 0 && (
                <span
                  className="meta-badge"
                  title={`${t.actionChecklist}: ${doneChecklistsCount}/${activeChecklists.length}`}
                >
                  <CheckSquare size={10.5} />
                  <span>
                    {doneChecklistsCount}/{activeChecklists.length}
                  </span>
                </span>
              )}

              {/* 4b. Attachments Count Badge (v1.8.0) */}
              {taskAttachments.length > 0 && (
                <span className="meta-badge" title={`${t.attachmentsTitle}: ${taskAttachments.length}`}>
                  <Paperclip size={10.5} />
                  <span>{taskAttachments.length}</span>
                </span>
              )}

              {/* 5. Tag Pills */}
              {taskTags.map((tg) => (
                <span key={tg.id} className="meta-badge" title={`${t.entityTag}: ${tg.title}`}>
                  <Tag size={9.5} />
                  <span>{tg.title}</span>
                </span>
              ))}

              {/* 6. Deadline */}
              {task.deadline != null && (() => {
                const dl = renderDeadlineMeta(task.deadline);
                return (
                  <span
                    className={`meta-badge ${dl.urgent ? 'urgent' : ''}`}
                    title={`${t.actionDeadline}: ${dl.label}`}
                  >
                    <Flag size={10.5} />
                    <span>{dl.label}</span>
                  </span>
                );
              })()}
            </div>
          )}
        </div>

        <span className="drag-grip" title={t.actionMove}>
          <GripVertical size={15} />
        </span>
      </div>

      {/* Right-Click TaskContextDropdownMenu (portaled to document.body & clamped to viewport) */}
      {ctxMenu &&
        !quickFindOpen &&
        typeof document !== 'undefined' &&
        createPortal(
          <>
            <div
              style={{ position: 'fixed', inset: 0, zIndex: 199 }}
              onClick={() => setCtxMenu(null)}
              onContextMenu={(e) => {
                e.preventDefault();
                setCtxMenu(null);
              }}
            />
            <div
              ref={ctxMenuRef}
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
                  onOpenWhenModal(task.id);
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
                  onOpenDeadlineModal(task.id);
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
                  onOpenRepeatModal(task.id);
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
                  onOpenMoveModal(task.id);
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
                  onOpenTagsModal(task.id);
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

              {/* 9. Start Focus Timer (task.durationMinutes ?? 25m) */}
              {task.status === 'OPEN' && (
                <button
                  type="button"
                  className="context-menu-item"
                  onClick={() => {
                    setCtxMenu(null);
                    onStartFocusTimer(task.id, task.title, task.durationMinutes ?? 25);
                  }}
                >
                  <Timer size={14} style={{ color: 'var(--things-blue)' }} />
                  <span>{t.actionFocusTimerMinutes(focusDurationMins)}</span>
                </button>
              )}

              {/* 10. Duplicate To-Do */}
              <button
                type="button"
                className="context-menu-item"
                onClick={() => {
                  setCtxMenu(null);
                  onDuplicateTask(task.id);
                }}
              >
                <Copy size={14} />
                <span>{t.actionDuplicateTodo}</span>
              </button>

              <div className="context-menu-divider" />

              {/* 11. Mark as Canceled */}
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

              {/* 12. Delete To-Do */}
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
          </>,
          document.body
        )}
    </>
  );
};
