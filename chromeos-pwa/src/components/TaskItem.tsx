import React, { useEffect, useLayoutEffect, useMemo, useRef, useState } from 'react';
import { createPortal } from 'react-dom';
import {
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
  Cloud,
  Compass,
  Copy,
  Diamond,
  Edit3,
  FileText,
  Flag,
  Folder,
  FolderInput,
  GripVertical,
  Hash,
  Hourglass,
  LayoutGrid,
  Link2,
  Maximize2,
  Mic,
  Moon,
  MoreHorizontal,
  Paperclip,
  Pin,
  PinOff,
  Plus,
  Repeat,
  RotateCcw,
  Sparkles,
  Sun,
  Timer,
  Trash2,
  X,
  XCircle,
  Zap
} from 'lucide-react';
import {
  daysBetweenToday,
  formatRecurrenceShort,
  type AreaRecord,
  type AttachmentRecord,
  type ChecklistItemRecord,
  type EffortLevel,
  type ProjectRecord,
  type SupportedLang,
  type TagRecord,
  type TaskRecord,
  type TaskStatus
} from '../domain/types';
import {
  DEFAULT_TIMEBLOCK_DURATION_MINUTES,
  STANDALONE_DURATION_PRESETS,
  TIMEBLOCK_DURATION_PRESETS,
  TIMEBLOCK_QUICK_START_MINUTES,
  computeClampedContextMenuPosition,
  formatMinutesAsClock,
  formatStandaloneDuration,
  formatTimeblockRange,
  isTaskActivelyBlocked,
  parseClockToMinutes,
  parseCustomDurationMinutes,
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
import {
  attachMultiTaskDragPreview,
  extractDraggedTaskIds,
  hasDraggedTaskMime
} from '../domain/v30Roadmap';
import {
  parseSmartTaskSyntax,
  type ParsedSmartSyntax
} from '../domain/smartSyntaxParser';

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
  openTaskIdSet?: ReadonlySet<string>;
  tasksByIdMap?: ReadonlyMap<string, TaskRecord>;
  checklistItems: ChecklistItemRecord[];
  attachments: AttachmentRecord[];
  lang: SupportedLang;
  geminiApiKey: string;
  t: TranslationStrings;
  dragTaskIds?: string[];
  quickEditRequestNonce?: number;
  onSelect: (multiToggle: boolean, shiftRange?: boolean) => void;
  onExpand: () => void;
  onCollapse: () => void;
  onUpdateTitleNotes?: (title: string, notes: string) => void;
  onUpdateEffortLevel?: (effortLevel: EffortLevel | null) => void;
  onRequestEnsureVisible?: (preferBottomVisible: boolean) => void;
  onOpenWhenModal: (taskId: string) => void;
  onOpenDeadlineModal: (taskId: string) => void;
  onOpenRepeatModal: (taskId: string) => void;
  onOpenMoveModal: (taskId: string) => void;
  onOpenTagsModal: (taskId: string) => void;
  onOpenDependenciesModal?: (taskId: string) => void;
  onStartFocusTimer: (
    taskId: string,
    taskTitle: string,
    durationMinutes?: number | null
  ) => void;
  onDuplicateTask: (taskId: string) => void;
  onDropReorder: (draggedTaskId: string, targetTaskId: string) => void;
  onDropReorderTasks?: (draggedTaskIds: string[], targetTaskId: string) => void;
  onToast: (msg: string) => void;
}

const EMPTY_OPEN_ID_SET: ReadonlySet<string> = new Set();
const EMPTY_TASKS_MAP: ReadonlyMap<string, TaskRecord> = new Map();

function nextCycledEffortLevel(cur: EffortLevel | null | undefined): EffortLevel | null {
  if (!cur) return 'QUICK_WIN';
  if (cur === 'QUICK_WIN') return 'MEDIUM';
  if (cur === 'MEDIUM') return 'DEEP_FOCUS';
  return null;
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
  openTaskIdSet = EMPTY_OPEN_ID_SET,
  tasksByIdMap = EMPTY_TASKS_MAP,
  checklistItems,
  attachments,
  lang,
  geminiApiKey,
  t,
  dragTaskIds,
  quickEditRequestNonce = 0,
  onSelect,
  onExpand,
  onCollapse,
  onUpdateTitleNotes,
  onUpdateEffortLevel,
  onRequestEnsureVisible,
  onOpenWhenModal,
  onOpenDeadlineModal,
  onOpenRepeatModal,
  onOpenMoveModal,
  onOpenTagsModal,
  onOpenDependenciesModal,
  onStartFocusTimer,
  onDuplicateTask,
  onDropReorder,
  onDropReorderTasks,
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
  const [effortDurationOpen, setEffortDurationOpen] = useState(false);
  const [inlineMoreMenuOpen, setInlineMoreMenuOpen] = useState(false);
  const [isInlineQuickEditing, setIsInlineQuickEditing] = useState(false);
  const [inlineTitleDraft, setInlineTitleDraft] = useState(task.title);
  const [prevQuickEditNonce, setPrevQuickEditNonce] = useState(quickEditRequestNonce);
  const [collapsedBadgePopover, setCollapsedBadgePopover] = useState<
    'timeblock' | 'duration' | 'effort' | null
  >(null);
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
  const [customDurationDraft, setCustomDurationDraft] = useState('');

  const strikeTimerRef = useRef<number | null>(null);
  const glideTimerRef = useRef<number | null>(null);
  const lastPointerTypeRef = useRef<string>('mouse');
  const collapsedRowRef = useRef<HTMLDivElement | null>(null);
  const inlineTitleInputRef = useRef<HTMLInputElement | null>(null);
  const ctxMenuRef = useRef<HTMLDivElement | null>(null);
  const titleInputRef = useRef<HTMLInputElement | null>(null);
  const subtaskRefs = useRef<Record<string, HTMLInputElement | null>>({});
  const addSubtaskInputRef = useRef<HTMLInputElement | null>(null);
  const attachmentFileInputRef = useRef<HTMLInputElement | null>(null);
  const cameraCaptureInputRef = useRef<HTMLInputElement | null>(null);
  const newSubtaskRef = useRef(newSubtask);
  newSubtaskRef.current = newSubtask;
  const wasExpandedRef = useRef(isExpanded);
  const isExpandedLatestRef = useRef(isExpanded);
  isExpandedLatestRef.current = isExpanded;
  const titleDraftLatestRef = useRef(titleDraft);
  titleDraftLatestRef.current = titleDraft;
  const notesDraftLatestRef = useRef(notesDraft);
  notesDraftLatestRef.current = notesDraft;
  const taskLatestRef = useRef(task);
  taskLatestRef.current = task;

  if (prevTaskId !== task.id) {
    setPrevTaskId(task.id);
    setPrevServerTitle(task.title);
    setPrevServerNotes(task.notes);
    setTitleDraft(task.title);
    setNotesDraft(task.notes);
    setInlineTitleDraft(task.title);
    setIsInlineQuickEditing(false);
    setCollapsedBadgePopover(null);
    setTimeblockOpen(false);
    setEffortDurationOpen(false);
    setInlineMoreMenuOpen(false);
    setCustomDurationDraft('');
  } else {
    if (prevServerTitle !== task.title) {
      setPrevServerTitle(task.title);
      if (!isTitleFocused) {
        setTitleDraft(task.title);
      }
      if (!isInlineQuickEditing) {
        setInlineTitleDraft(task.title);
      }
    }
    if (prevServerNotes !== task.notes) {
      setPrevServerNotes(task.notes);
      if (!isNotesFocused && notesDraft !== task.notes) {
        setNotesDraft(task.notes);
      }
    }
  }

  if (quickEditRequestNonce !== prevQuickEditNonce) {
    setPrevQuickEditNonce(quickEditRequestNonce);
    if (quickEditRequestNonce > 0 && !isExpanded) {
      setInlineTitleDraft(task.title);
      setCollapsedBadgePopover(null);
      setIsInlineQuickEditing(true);
    }
  }

  const flushPendingSubtaskIfNeeded = () => {
    const pending = newSubtaskRef.current.trim();
    if (!pending) return;
    newSubtaskRef.current = '';
    setNewSubtask('');
    void localDb.addChecklistItems(taskLatestRef.current.id, [pending]);
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
      setEffortDurationOpen(false);
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
      if (isExpandedLatestRef.current) {
        const pendingSub = newSubtaskRef.current.trim();
        if (pendingSub) {
          newSubtaskRef.current = '';
          void localDb.addChecklistItems(taskLatestRef.current.id, [pendingSub]);
        }
        const curTask = taskLatestRef.current;
        const cleanTitle = titleDraftLatestRef.current.trim();
        const curNotes = notesDraftLatestRef.current;
        if (cleanTitle !== curTask.title || curNotes !== curTask.notes) {
          void localDb.upsertTask({
            id: curTask.id,
            title: cleanTitle,
            notes: curNotes
          });
        }
      }
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

  const persistTitleNotes = (nextTitle: string, nextNotes: string) => {
    if (onUpdateTitleNotes) {
      onUpdateTitleNotes(nextTitle, nextNotes);
    } else {
      void localDb.upsertTask({ id: task.id, title: nextTitle, notes: nextNotes });
    }
  };

  const persistEffortLevel = (nextEffort: EffortLevel | null) => {
    if (onUpdateEffortLevel) {
      onUpdateEffortLevel(nextEffort);
    } else {
      void localDb.upsertTask({ id: task.id, effortLevel: nextEffort });
    }
  };

  const allTagsList = useMemo(() => Array.from(tagsMap.values()), [tagsMap]);
  const allProjectsList = useMemo(() => Array.from(projectsMap.values()), [projectsMap]);
  const allAreasList = useMemo(() => Array.from(areasMap.values()), [areasMap]);
  const smartSyntaxEntitiesRef = useRef({
    tags: allTagsList,
    projects: allProjectsList,
    areas: allAreasList,
    lang
  });
  smartSyntaxEntitiesRef.current = {
    tags: allTagsList,
    projects: allProjectsList,
    areas: allAreasList,
    lang
  };

  const buildSmartSyntaxPatch = (parsed: ParsedSmartSyntax, baseTask: TaskRecord) => ({
    id: baseTask.id,
    title: parsed.cleanTitle,
    ...(parsed.scheduleState != null
      ? { scheduleState: parsed.scheduleState, whenDate: parsed.whenDate }
      : {}),
    ...(parsed.reminderTime != null ? { reminderTime: parsed.reminderTime } : {}),
    ...(parsed.deadline != null ? { deadline: parsed.deadline } : {}),
    ...(parsed.durationMinutes != null ? { durationMinutes: parsed.durationMinutes } : {}),
    ...(parsed.matchedTagIds.length > 0 ? { addTagIds: parsed.matchedTagIds } : {}),
    ...(parsed.matchedProject
      ? {
          projectId: parsed.matchedProject.id,
          headingId:
            baseTask.projectId === parsed.matchedProject.id ? baseTask.headingId : null,
          areaId: null
        }
      : parsed.matchedArea
        ? {
            projectId: null,
            headingId: null,
            areaId: parsed.matchedArea.id
          }
        : {}),
    ...(parsed.isMilestone ? { isMilestone: true } : {})
  });

  const expandedParsedSmart = useMemo(
    () =>
      isExpanded
        ? parseSmartTaskSyntax(titleDraft, {
            tags: allTagsList,
            projects: allProjectsList,
            areas: allAreasList,
            lang
          })
        : null,
    [isExpanded, titleDraft, allTagsList, allProjectsList, allAreasList, lang]
  );

  const inlineParsedSmart = useMemo(
    () =>
      isInlineQuickEditing && !isExpanded
        ? parseSmartTaskSyntax(inlineTitleDraft, {
            tags: allTagsList,
            projects: allProjectsList,
            areas: allAreasList,
            lang
          })
        : null,
    [isInlineQuickEditing, isExpanded, inlineTitleDraft, allTagsList, allProjectsList, allAreasList, lang]
  );

  const startInlineQuickEdit = () => {
    setInlineTitleDraft(task.title);
    setCollapsedBadgePopover(null);
    setIsInlineQuickEditing(true);
  };

  const commitInlineQuickTitle = (exitAfterCommit: boolean) => {
    const clean = inlineTitleDraft.trim();
    const parsed = parseSmartTaskSyntax(clean, {
      tags: allTagsList,
      projects: allProjectsList,
      areas: allAreasList,
      lang
    });
    if (parsed.hasAnyMatch) {
      setInlineTitleDraft(parsed.cleanTitle);
      void localDb.upsertTask({
        ...buildSmartSyntaxPatch(parsed, task),
        notes: task.notes
      });
    } else if (clean !== task.title) {
      persistTitleNotes(clean, task.notes);
    }
    if (exitAfterCommit) {
      setIsInlineQuickEditing(false);
    }
  };

  useEffect(() => {
    if (!isInlineQuickEditing || isExpanded) return;
    if (inlineTitleInputRef.current) {
      inlineTitleInputRef.current.focus();
      const len = inlineTitleInputRef.current.value.length;
      inlineTitleInputRef.current.setSelectionRange(len, len);
    }
    const handleOutsidePointerDown = (ev: PointerEvent) => {
      const target = ev.target as HTMLElement | null;
      if (!target) return;
      if (collapsedRowRef.current?.contains(target)) return;
      if (target.closest('.modal-backdrop, .context-menu')) return;
      const clean = inlineTitleInputRef.current?.value.trim() ?? taskLatestRef.current.title;
      const parsed = parseSmartTaskSyntax(clean, smartSyntaxEntitiesRef.current);
      if (parsed.hasAnyMatch) {
        void localDb.upsertTask({
          ...buildSmartSyntaxPatch(parsed, taskLatestRef.current),
          notes: taskLatestRef.current.notes
        });
      } else if (clean !== taskLatestRef.current.title) {
        if (onUpdateTitleNotes) {
          onUpdateTitleNotes(clean, taskLatestRef.current.notes);
        } else {
          void localDb.upsertTask({
            id: taskLatestRef.current.id,
            title: clean,
            notes: taskLatestRef.current.notes
          });
        }
      }
      setIsInlineQuickEditing(false);
    };
    window.addEventListener('pointerdown', handleOutsidePointerDown);
    return () => window.removeEventListener('pointerdown', handleOutsidePointerDown);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [isInlineQuickEditing, isExpanded, onUpdateTitleNotes]);

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
  const standaloneDurationLabel =
    task.scheduledStartMinutes == null &&
    task.durationMinutes != null &&
    Number.isFinite(task.durationMinutes) &&
    task.durationMinutes > 0
      ? formatStandaloneDuration(task.durationMinutes)
      : '';
  const anyDurationLabel =
    task.durationMinutes != null && Number.isFinite(task.durationMinutes) && task.durationMinutes > 0
      ? formatStandaloneDuration(task.durationMinutes)
      : '';
  const actualMinutesValue =
    task.actualMinutes != null && Number.isFinite(task.actualMinutes) && task.actualMinutes > 0
      ? Math.trunc(task.actualMinutes)
      : 0;
  const actualFocusedBadgeLabel =
    actualMinutesValue > 0
      ? anyDurationLabel
        ? t.v32ActualVsEstimated(formatStandaloneDuration(actualMinutesValue), anyDurationLabel)
        : t.v32ActualFocusedShort(formatStandaloneDuration(actualMinutesValue))
      : '';
  const effortDisplayLabel =
    task.effortLevel === 'QUICK_WIN'
      ? `⚡ ${t.effortQuickWin}`
      : task.effortLevel === 'MEDIUM'
        ? t.effortMedium
        : task.effortLevel === 'DEEP_FOCUS'
          ? `🧠 ${t.effortDeepFocus}`
          : '';
  const activeBlockerIds = (task.blockedByTaskIds || []).filter(
    (bid) => bid && bid !== task.id && openTaskIdSet.has(bid)
  );
  const isActivelyBlocked = isTaskActivelyBlocked(task, openTaskIdSet);
  const firstBlockerTitle =
    activeBlockerIds.length === 1
      ? (tasksByIdMap.get(activeBlockerIds[0])?.title?.trim() ?? '')
      : '';
  const blockedSummaryLabel = isActivelyBlocked
    ? activeBlockerIds.length === 1 && firstBlockerTitle
      ? t.waitingOnNamedBadge(firstBlockerTitle)
      : t.waitingOnBadge(activeBlockerIds.length)
    : '';
  const totalBlockerCount = (task.blockedByTaskIds || []).filter(
    (bid) => bid && bid !== task.id
  ).length;
  const focusDurationMins =
    task.durationMinutes != null && task.durationMinutes > 0
      ? Math.min(720, task.durationMinutes)
      : 25;

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
    <div className="checkbox-anchor" onDoubleClick={(e) => e.stopPropagation()}>
      <button
        type="button"
        className={`things-checkbox ${task.isMilestone ? 'milestone' : ''} ${
          isSomedayDashed ? 'someday-dashed' : ''
        } ${animStage !== 'idle' ? 'pop' : ''} ${
          isVisualCompleted ? 'completed checked' : ''
        } ${isCanceled ? 'canceled' : ''}`}
        onClick={handlePrimaryCheckboxClick}
        onDoubleClick={(e) => e.stopPropagation()}
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
              zIndex: 90,
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

  // ==================== Unified Soft Bento Task Modal View ====================
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
        className="task-card-expanded task-bento-modal-card"
        data-testid="soft-bento-task-modal"
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
            if (inlineMoreMenuOpen) {
              setInlineMoreMenuOpen(false);
              return;
            }
            if (statusMenuOpen) {
              setStatusMenuOpen(false);
              return;
            }
            if (timeblockOpen) {
              setTimeblockOpen(false);
              return;
            }
            if (effortDurationOpen) {
              setEffortDurationOpen(false);
              return;
            }
            handleDoneCollapse();
          }
        }}
      >
        {/* Modal Header: Checkbox + Status Dropdown, Spacious Title Input, Pin, Focus Timer, Close ✕ */}
        <div className="task-card-top task-bento-modal-header">
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

          <div className="task-bento-header-actions">
            <button
              type="button"
              className={`icon-btn ${task.isMilestone ? 'milestone-active' : ''}`}
              data-testid="task-milestone-btn"
              title={task.isMilestone ? t.removeMilestone : t.markAsMilestone}
              aria-label={task.isMilestone ? t.removeMilestone : t.markAsMilestone}
              onClick={() => {
                flushDraftsIfNeeded();
                void localDb.upsertTask({ id: task.id, isMilestone: !task.isMilestone });
              }}
            >
              <Diamond size={15} />
            </button>

            <button
              type="button"
              className={`icon-btn ${task.isPinned ? 'active' : ''}`}
              data-testid="task-pin-btn"
              title={task.isPinned ? t.unpinTask : t.pinTask}
              aria-label={task.isPinned ? t.unpinTask : t.pinTask}
              onClick={() => {
                flushDraftsIfNeeded();
                void localDb.upsertTask({ id: task.id, isPinned: !task.isPinned });
              }}
            >
              {task.isPinned ? <PinOff size={15.5} /> : <Pin size={15.5} />}
            </button>

            <button
              type="button"
              className="icon-btn task-modal-close-btn"
              title={t.actionDone}
              aria-label={t.actionDone}
              data-testid="task-modal-close-btn"
              onClick={handleDoneCollapse}
            >
              <X size={17} />
            </button>
          </div>
        </div>

        {/* v3.2 Live Smart-Syntax Preview Strip with 1-Click Apply */}
        {expandedParsedSmart?.hasAnyMatch && (
          <div
            className="v32-smart-syntax-strip task-modal-smart-strip"
            data-testid="task-smart-syntax-strip"
          >
            <span className="v32-smart-syntax-label">{t.v32SmartSyntaxDetected}</span>
            <div className="v32-smart-syntax-pills">
              {expandedParsedSmart.scheduleState != null && (
                <span className="meta-badge today">
                  <Calendar size={10.5} />
                  <span>{expandedParsedSmart.whenLabel || expandedParsedSmart.scheduleState}</span>
                </span>
              )}
              {expandedParsedSmart.deadline != null && (
                <span className="meta-badge urgent">
                  <Flag size={10.5} />
                  <span>{expandedParsedSmart.deadlineLabel}</span>
                </span>
              )}
              {expandedParsedSmart.durationMinutes != null && (
                <span className="meta-badge duration-badge">
                  <Hourglass size={10} />
                  <span>{formatStandaloneDuration(expandedParsedSmart.durationMinutes)}</span>
                </span>
              )}
              {expandedParsedSmart.isMilestone && (
                <span className="meta-badge milestone-badge">
                  <Diamond size={10} />
                  <span>{t.milestoneLabel}</span>
                </span>
              )}
              {expandedParsedSmart.matchedProject && (
                <span className="meta-badge">
                  <Folder size={10.5} />
                  <span>{expandedParsedSmart.matchedProject.title}</span>
                </span>
              )}
              {!expandedParsedSmart.matchedProject && expandedParsedSmart.matchedArea && (
                <span className="meta-badge">
                  <Folder size={10.5} />
                  <span>{expandedParsedSmart.matchedArea.title}</span>
                </span>
              )}
              {expandedParsedSmart.matchedTags.map((tg) => (
                <span key={tg.id} className="meta-badge">
                  <LayoutGrid size={9.5} />
                  <span>#{tg.title}</span>
                </span>
              ))}
            </div>
            <button
              type="button"
              className="chip-btn active v32-smart-apply-btn"
              data-testid="task-smart-syntax-apply-btn"
              onClick={() => {
                setTitleDraft(expandedParsedSmart.cleanTitle);
                void localDb.upsertTask({
                  ...buildSmartSyntaxPatch(expandedParsedSmart, task),
                  notes: notesDraft
                });
              }}
            >
              <Check size={12} />
              <span>{t.v32SmartSyntaxApply}</span>
            </button>
          </div>
        )}

        {/* Internal Scrollable 2-Column Soft Bento Body */}
        <div className="task-bento-modal-body">
          <div className="task-bento-modal-grid">
            {/* Left Content Column: Notes, Checklist, Attachments */}
            <div className="task-bento-main-col">
              {/* 1. Notes Soft Bento Card */}
              <section className="task-bento-card" data-testid="task-bento-notes-card">
                <div className="task-bento-card-header">
                  <FileText size={12.5} />
                  <span>{t.placeholderNotes}</span>
                </div>
                <MarkdownNotesField
                  value={notesDraft}
                  rows={4}
                  t={t}
                  onChange={(nextNotes) => {
                    setNotesDraft(nextNotes);
                    void localDb.upsertTask({ id: task.id, notes: nextNotes });
                  }}
                  onFocusChange={setIsNotesFocused}
                  placeholder={t.placeholderNotes}
                  className="task-card-notes"
                />
              </section>

              {/* 2. Checklist Soft Bento Card */}
              <section
                className="task-bento-card checklist-section"
                data-testid="task-bento-checklist-card"
              >
                <div className="checklist-header task-bento-card-header">
                  <CheckSquare size={12.5} />
                  <span>
                    {t.actionChecklist} ({doneChecklistsCount}/{activeChecklists.length})
                  </span>
                </div>

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
                      const other =
                        dir === 'up' ? activeChecklists[idx - 1] : activeChecklists[idx + 1];
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
              </section>

              {/* 3. Attachments Soft Bento Card (slim single row when empty, full thumbnail strip when populated) */}
              <section
                className={`task-bento-card ${
                  taskAttachments.length === 0 ? 'attachments-empty' : ''
                }`}
                data-testid="task-bento-attachments-card"
              >
                <div className="task-bento-card-header">
                  <Paperclip size={12.5} />
                  <span>
                    {t.attachmentsTitle}
                    {taskAttachments.length > 0 ? ` (${taskAttachments.length})` : ''}
                  </span>
                </div>

                <AttachmentsStrip
                  taskId={task.id}
                  attachments={taskAttachments}
                  t={t}
                  onToast={onToast}
                  showAddButton
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
              </section>
            </div>

            {/* Right Properties Column: 3 Balanced Soft Bento Property Cards (Schedule, Focus & Dependencies, Organization) */}
            <div className="task-bento-props-col">
              {/* 1. Schedule Card: When, Timeblock, Deadline & Repeat */}
              <div className="task-bento-prop-tile" data-testid="task-bento-tile-when">
                <span className="task-bento-prop-label">
                  {t.actionWhen} · {t.actionDeadline}
                </span>
                <div className="task-bento-prop-pills">
                  <button
                    type="button"
                    data-testid="task-when-chip"
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
                      <Sun size={12} style={{ color: 'var(--today-star)' }} />
                    ) : task.scheduleState === 'THIS_EVENING' ? (
                      <Moon size={12} />
                    ) : task.scheduleState === 'SOMEDAY' ? (
                      <Cloud size={12} />
                    ) : task.scheduleState === 'ANYTIME' && task.whenDate == null ? (
                      <Compass size={12} />
                    ) : (
                      <Calendar size={12} />
                    )}
                    <span>{whenChipText}</span>
                  </button>

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

                  <span className="task-bento-inline-sub" data-testid="task-bento-tile-deadline-repeat">
                    <button
                      type="button"
                      data-testid="task-deadline-chip"
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
                          dlMeta == null
                            ? { color: 'var(--deadline-red)', opacity: 0.85 }
                            : undefined
                        }
                      />
                      <span>{dlMeta ? dlMeta.label : t.actionDeadline}</span>
                    </button>

                    <button
                      type="button"
                      data-testid="task-repeat-chip"
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

                    <button
                      type="button"
                      data-testid="task-milestone-chip"
                      className={`chip-btn ${task.isMilestone ? 'milestone-active' : ''}`}
                      title={task.isMilestone ? t.removeMilestone : t.markAsMilestone}
                      onClick={() => {
                        flushDraftsIfNeeded();
                        void localDb.upsertTask({ id: task.id, isMilestone: !task.isMilestone });
                      }}
                    >
                      <Diamond
                        size={12}
                        style={
                          !task.isMilestone
                            ? { color: 'var(--milestone-violet)', opacity: 0.85 }
                            : undefined
                        }
                      />
                      <span>{t.milestoneLabel}</span>
                    </button>
                  </span>
                </div>

                {timeblockOpen && (
                  <div className="v2-timeblock-panel" data-testid="task-timeblock-panel">
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
                              durationMinutes:
                                task.durationMinutes ?? DEFAULT_TIMEBLOCK_DURATION_MINUTES
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
                        {TIMEBLOCK_DURATION_PRESETS.map((dur) => {
                          const label = formatStandaloneDuration(dur);
                          return (
                            <button
                              key={dur}
                              type="button"
                              className={`chip-btn ${currentDuration === dur ? 'active' : ''}`}
                              title={`${t.timeblockDuration}: ${label}`}
                              onClick={() =>
                                void localDb.upsertTask({
                                  id: task.id,
                                  scheduledStartMinutes: task.scheduledStartMinutes ?? 9 * 60,
                                  durationMinutes: dur
                                })
                              }
                            >
                              {label}
                            </button>
                          );
                        })}
                      </div>

                      {task.scheduledStartMinutes != null && (
                        <button
                          type="button"
                          className="chip-btn urgent"
                          data-testid="task-timeblock-clear-btn"
                          style={{ marginLeft: 'auto' }}
                          title={t.timeblockClear}
                          onClick={() =>
                            void localDb.upsertTask({
                              id: task.id,
                              scheduledStartMinutes: null
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
              </div>

              {/* 2. Focus & Dependencies Card: Duration, Effort & Waiting On */}
              <div className="task-bento-prop-tile" data-testid="task-bento-tile-duration-effort">
                <span className="task-bento-prop-label">
                  {t.durationLabel} · {t.effortLabel}
                </span>
                <div className="task-bento-prop-pills">
                  <button
                    type="button"
                    data-testid="task-effort-duration-chip"
                    className={`chip-btn ${
                      Boolean(anyDurationLabel) || task.effortLevel != null || effortDurationOpen
                        ? 'active'
                        : ''
                    }`}
                    title={`${t.durationLabel} / ${t.effortLabel}`}
                    onClick={() => {
                      flushDraftsIfNeeded();
                      setEffortDurationOpen((v) => !v);
                    }}
                  >
                    {task.effortLevel != null ? <Zap size={12} /> : <Hourglass size={12} />}
                    <span>
                      {anyDurationLabel && effortDisplayLabel
                        ? `${anyDurationLabel} · ${effortDisplayLabel}`
                        : anyDurationLabel
                          ? anyDurationLabel
                          : effortDisplayLabel
                            ? effortDisplayLabel
                            : `${t.durationLabel} · ${t.effortLabel}`}
                    </span>
                  </button>

                  {onOpenDependenciesModal && (
                    <span className="task-bento-inline-sub" data-testid="task-bento-tile-waiting-on">
                      <button
                        type="button"
                        data-testid="task-waiting-on-chip"
                        className={`chip-btn ${
                          isActivelyBlocked ? 'urgent' : totalBlockerCount > 0 ? 'active' : ''
                        }`}
                        title={t.waitingOnLabel}
                        onClick={() => {
                          flushDraftsIfNeeded();
                          onOpenDependenciesModal(task.id);
                        }}
                      >
                        <Link2 size={12} />
                        <span>
                          {blockedSummaryLabel
                            ? blockedSummaryLabel
                            : totalBlockerCount > 0
                              ? `${t.dependencyModalTitle} (${totalBlockerCount})`
                              : t.waitingOnLabel}
                        </span>
                      </button>
                    </span>
                  )}

                  {actualMinutesValue > 0 && (
                    <span
                      className="task-bento-inline-sub"
                      data-testid="task-actual-minutes-group"
                    >
                      <span
                        className="meta-badge duration-badge"
                        data-testid="task-actual-minutes-badge"
                      >
                        <Timer size={11} />
                        <span>{actualFocusedBadgeLabel}</span>
                      </span>
                      <button
                        type="button"
                        className="chip-btn"
                        data-testid="task-reset-actual-minutes-btn"
                        title={t.v32ResetActualTime}
                        onClick={() =>
                          void localDb.upsertTask({
                            id: task.id,
                            actualMinutes: null
                          })
                        }
                      >
                        <RotateCcw size={11} />
                        <span>{t.v32ResetActualTime}</span>
                      </button>
                    </span>
                  )}
                </div>

                {effortDurationOpen && (
                  <div className="v2-timeblock-panel" data-testid="task-effort-duration-panel">
                    <div className="v2-timeblock-row">
                      <span className="v2-timeblock-label">{t.durationLabel}</span>
                      <div className="v2-timeblock-slots">
                        {STANDALONE_DURATION_PRESETS.map((dur) => {
                          const label = formatStandaloneDuration(dur);
                          return (
                            <button
                              key={dur}
                              type="button"
                              className={`chip-btn ${task.durationMinutes === dur ? 'active' : ''}`}
                              data-testid={`task-duration-chip-${dur}`}
                              title={`${t.durationLabel}: ${label}`}
                              onClick={() =>
                                void localDb.upsertTask({
                                  id: task.id,
                                  durationMinutes: task.durationMinutes === dur ? null : dur
                                })
                              }
                            >
                              {label}
                            </button>
                          );
                        })}
                      </div>
                      {task.durationMinutes != null && (
                        <button
                          type="button"
                          className="chip-btn urgent"
                          style={{ marginLeft: 'auto' }}
                          title={t.durationClear}
                          onClick={() =>
                            void localDb.upsertTask({
                              id: task.id,
                              durationMinutes: null
                            })
                          }
                        >
                          <X size={11.5} />
                          <span>{t.actionClear}</span>
                        </button>
                      )}
                    </div>

                    <div className="v3-custom-duration-row">
                      <span className="v2-timeblock-label">{t.durationCustom}</span>
                      <input
                        type="text"
                        className="v3-custom-duration-input"
                        data-testid="task-custom-duration-input"
                        value={customDurationDraft}
                        placeholder={t.durationCustomPlaceholder}
                        onChange={(e) => setCustomDurationDraft(e.target.value)}
                        onKeyDown={(e) => {
                          if (e.key === 'Enter') {
                            e.preventDefault();
                            e.stopPropagation();
                            const mins = parseCustomDurationMinutes(customDurationDraft);
                            if (mins != null) {
                              setCustomDurationDraft('');
                              void localDb.upsertTask({ id: task.id, durationMinutes: mins });
                            }
                          }
                        }}
                      />
                      <button
                        type="button"
                        className={`chip-btn ${parseCustomDurationMinutes(customDurationDraft) != null ? 'active' : ''}`}
                        data-testid="task-custom-duration-apply"
                        disabled={parseCustomDurationMinutes(customDurationDraft) == null}
                        onClick={() => {
                          const mins = parseCustomDurationMinutes(customDurationDraft);
                          if (mins != null) {
                            setCustomDurationDraft('');
                            void localDb.upsertTask({ id: task.id, durationMinutes: mins });
                          }
                        }}
                      >
                        <Check size={11.5} />
                        <span>
                          {(() => {
                            const mins = parseCustomDurationMinutes(customDurationDraft);
                            return mins != null ? formatStandaloneDuration(mins) : t.actionDone;
                          })()}
                        </span>
                      </button>
                    </div>

                    <div className="v2-timeblock-row" style={{ marginTop: 8 }}>
                      <span className="v2-timeblock-label">{t.effortLabel}</span>
                      <div className="v2-timeblock-slots">
                        {(
                          [
                            [null, t.effortNone],
                            ['QUICK_WIN', `⚡ ${t.effortQuickWin}`],
                            ['MEDIUM', t.effortMedium],
                            ['DEEP_FOCUS', `🧠 ${t.effortDeepFocus}`]
                          ] as Array<[EffortLevel | null, string]>
                        ).map(([lvl, label]) => (
                          <button
                            key={lvl ?? 'none'}
                            type="button"
                            className={`chip-btn ${(task.effortLevel ?? null) === lvl ? 'active' : ''}`}
                            data-testid={`task-effort-chip-${lvl ?? 'none'}`}
                            onClick={() =>
                              void localDb.upsertTask({
                                id: task.id,
                                effortLevel: lvl
                              })
                            }
                          >
                            {label}
                          </button>
                        ))}
                      </div>
                    </div>
                  </div>
                )}
              </div>

              {/* 3. Organization Card: Project / Area & Categories */}
              <div className="task-bento-prop-tile" data-testid="task-bento-tile-container">
                <span className="task-bento-prop-label">
                  {t.entityProject} / {t.entityArea} · {t.entityTags}
                </span>
                <div className="task-bento-prop-pills">
                  <button
                    type="button"
                    data-testid="task-move-chip"
                    className={`chip-btn ${containerLabel ? 'active' : ''}`}
                    title={t.ctxMoveContainer}
                    onClick={() => {
                      flushDraftsIfNeeded();
                      onOpenMoveModal(task.id);
                    }}
                  >
                    <FolderInput size={12} />
                    <span>{containerLabel || t.actionMove}</span>
                  </button>

                  <span className="task-bento-inline-sub" data-testid="task-bento-tile-categories">
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
                        <LayoutGrid size={11.5} />
                        <span>{tg.title}</span>
                      </button>
                    ))}

                    <button
                      type="button"
                      data-testid="task-tags-chip"
                      className="chip-btn"
                      title={t.ctxAssignTags}
                      onClick={() => {
                        flushDraftsIfNeeded();
                        onOpenTagsModal(task.id);
                      }}
                    >
                      <Plus size={11.5} />
                      <span>{t.actionTags}</span>
                    </button>
                  </span>
                </div>
              </div>
            </div>
          </div>
        </div>

        {/* Unified Sticky Footer: Focus Timer + ⋯ More Actions Menu + Primary Done Button */}
        <div className="task-card-unified-footer" data-testid="task-unified-footer">
          <div className="task-footer-left-actions">
            {task.status === 'OPEN' && (
              <button
                type="button"
                className="chip-btn task-more-menu-trigger"
                title={t.actionFocusTimerMinutes(focusDurationMins)}
                aria-label={t.actionFocusTimerMinutes(focusDurationMins)}
                data-testid="task-start-focus-btn"
                onClick={(e) => {
                  e.stopPropagation();
                  flushDraftsIfNeeded();
                  onStartFocusTimer(
                    task.id,
                    titleDraft || task.title,
                    focusDurationMins
                  );
                }}
              >
                <Timer size={14.5} style={{ color: 'var(--things-blue)' }} />
                <span>{t.actionFocusTimerMinutes(focusDurationMins)}</span>
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
                <span>{t.actionMoreOptions}</span>
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
                      left: 0,
                      right: 'auto',
                      top: 'auto',
                      bottom: 'calc(100% + 8px)',
                      minWidth: 224,
                      maxHeight: 'min(290px, calc(100dvh - 120px))',
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
                      data-testid="task-more-milestone-btn"
                      onClick={() => {
                        setInlineMoreMenuOpen(false);
                        flushDraftsIfNeeded();
                        void localDb.upsertTask({ id: task.id, isMilestone: !task.isMilestone });
                      }}
                    >
                      <span style={{ display: 'inline-flex', alignItems: 'center', gap: 8 }}>
                        <Diamond size={14} style={{ color: 'var(--milestone-violet)' }} />
                        <span>{task.isMilestone ? t.removeMilestone : t.markAsMilestone}</span>
                      </span>
                    </button>
                    <button
                      type="button"
                      className="dropdown-item"
                      data-testid="task-more-pin-btn"
                      onClick={() => {
                        setInlineMoreMenuOpen(false);
                        flushDraftsIfNeeded();
                        void localDb.upsertTask({ id: task.id, isPinned: !task.isPinned });
                      }}
                    >
                      <span style={{ display: 'inline-flex', alignItems: 'center', gap: 8 }}>
                        {task.isPinned ? (
                          <PinOff size={14} style={{ color: 'var(--things-blue)' }} />
                        ) : (
                          <Pin size={14} style={{ color: 'var(--things-blue)' }} />
                        )}
                        <span>{task.isPinned ? t.unpinTask : t.pinTask}</span>
                      </span>
                    </button>
                    {onOpenDependenciesModal && (
                      <button
                        type="button"
                        className="dropdown-item"
                        data-testid="task-more-waiting-on-btn"
                        onClick={() => {
                          setInlineMoreMenuOpen(false);
                          flushDraftsIfNeeded();
                          onOpenDependenciesModal(task.id);
                        }}
                      >
                        <span style={{ display: 'inline-flex', alignItems: 'center', gap: 8 }}>
                          <Link2 size={14} style={{ color: 'var(--things-blue)' }} />
                          <span>{t.waitingOnLabel}</span>
                        </span>
                      </button>
                    )}
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
                      data-testid="task-delete-btn"
                      style={{ color: 'var(--deadline-red)' }}
                      onClick={() => {
                        setInlineMoreMenuOpen(false);
                        void localDb.softDeleteTasks([task.id]);
                        onCollapse();
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
          </div>

          <div className="task-card-actions">
            <button
              type="button"
              className="btn-primary task-modal-done-btn"
              data-testid="task-modal-done-btn"
              title={t.actionDone}
              onClick={handleDoneCollapse}
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
  const showCollapsedSchedulePill =
    showScheduleBadgeInCollapsed &&
    task.status === 'OPEN' &&
    (task.scheduleState === 'TODAY' ||
      task.scheduleState === 'SOMEDAY' ||
      (task.scheduleState === 'UPCOMING' && task.whenDate != null) ||
      (task.scheduleState !== 'THIS_EVENING' && task.whenDate != null));

  const hasSecondRowBadges =
    Boolean(task.isMilestone) ||
    task.scheduleState === 'THIS_EVENING' ||
    showCollapsedSchedulePill ||
    Boolean(timeblockRangeLabel) ||
    Boolean(standaloneDurationLabel) ||
    actualMinutesValue > 0 ||
    Boolean(effortDisplayLabel) ||
    isActivelyBlocked ||
    task.recurrenceRule != null ||
    task.reminderTime != null ||
    activeChecklists.length > 0 ||
    taskAttachments.length > 0 ||
    taskTags.length > 0 ||
    task.deadline != null;

  const stopRowPropagation = (e: React.SyntheticEvent) => {
    e.stopPropagation();
  };

  return (
    <>
      <div
        ref={collapsedRowRef}
        className={`task-row ${isSelected ? 'selected' : ''} ${
          isInlineQuickEditing ? 'quick-editing' : ''
        } ${isActivelyBlocked ? 'blocked' : ''} ${
          animStage === 'glide' ? 'stage-glide' : ''
        } ${dragOver ? 'drag-over-row' : ''}`}
        data-task-id={task.id}
        draggable={!isInlineQuickEditing}
        onDragStart={(e) => {
          if (isInlineQuickEditing) {
            e.preventDefault();
            return;
          }
          const payloadIds =
            isSelected && dragTaskIds && dragTaskIds.length > 1 && dragTaskIds.includes(task.id)
              ? dragTaskIds
              : [task.id];
          attachMultiTaskDragPreview(
            e.dataTransfer,
            payloadIds,
            t.dragTasksCount(payloadIds.length)
          );
        }}
        onDragOver={(e) => {
          if (hasDraggedTaskMime(e.dataTransfer)) {
            e.preventDefault();
            e.dataTransfer.dropEffect = 'move';
            setDragOver(true);
          }
        }}
        onDragLeave={() => setDragOver(false)}
        onDrop={(e) => {
          e.preventDefault();
          setDragOver(false);
          const draggedIds = extractDraggedTaskIds(e.dataTransfer);
          if (draggedIds.length === 0) return;
          if (draggedIds.length === 1 && draggedIds[0] === task.id) return;
          if (onDropReorderTasks) {
            onDropReorderTasks(draggedIds, task.id);
          } else {
            onDropReorder(draggedIds[0], task.id);
          }
        }}
        onPointerDown={(e) => {
          lastPointerTypeRef.current = e.pointerType || 'mouse';
        }}
        onClick={(e) => {
          e.stopPropagation();
          if (isInlineQuickEditing) return;
          if (e.shiftKey) {
            onSelect(true, true);
          } else if (e.ctrlKey || e.metaKey || isMultiSelectMode) {
            onSelect(true, false);
          } else if (lastPointerTypeRef.current === 'touch') {
            onExpand();
          } else {
            onSelect(false, false);
          }
        }}
        onDoubleClick={(e) => {
          e.stopPropagation();
          if (isInlineQuickEditing) return;
          onExpand();
        }}
        onContextMenu={(e) => {
          if (quickFindOpen) return;
          e.preventDefault();
          e.stopPropagation();
          const initial = computeClampedContextMenuPosition(
            e.clientX,
            e.clientY,
            264,
            640,
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
          {isInlineQuickEditing ? (
            <div
              className="task-row-inline-editor"
              onClick={stopRowPropagation}
              onDoubleClick={stopRowPropagation}
              onPointerDown={stopRowPropagation}
            >
              <div className="task-row-inline-edit-wrap">
                {task.isPinned && (
                  <span
                    className="task-pin-indicator"
                    title={t.pinnedBadge}
                    data-testid="task-pin-indicator"
                  >
                    <Pin size={12.5} />
                  </span>
                )}
                <input
                  ref={inlineTitleInputRef}
                  type="text"
                  className="task-row-inline-title-input"
                  data-testid="task-row-inline-title-input"
                  value={inlineTitleDraft}
                  placeholder={t.placeholderTodoTitle}
                  onChange={(e) => setInlineTitleDraft(e.target.value)}
                  onKeyDown={(e) => {
                    if (e.key === 'Enter') {
                      e.preventDefault();
                      e.stopPropagation();
                      commitInlineQuickTitle(true);
                    } else if (e.key === 'Escape') {
                      e.preventDefault();
                      e.stopPropagation();
                      setInlineTitleDraft(task.title);
                      setIsInlineQuickEditing(false);
                    }
                  }}
                />
                <button
                  type="button"
                  className="task-row-inline-save-btn"
                  data-testid="task-row-inline-save-btn"
                  title={t.actionSave}
                  aria-label={t.actionSave}
                  onClick={(e) => {
                    e.stopPropagation();
                    commitInlineQuickTitle(true);
                  }}
                >
                  <Check size={13} strokeWidth={2.6} />
                </button>
              </div>

              {inlineParsedSmart?.hasAnyMatch && (
                <div
                  className="v32-smart-syntax-strip compact"
                  data-testid="task-inline-smart-syntax-strip"
                >
                  <span className="v32-smart-syntax-label">{t.v32SmartSyntaxDetected}</span>
                  <div className="v32-smart-syntax-pills">
                    {inlineParsedSmart.scheduleState != null && (
                      <span className="meta-badge today">
                        <span>{inlineParsedSmart.whenLabel || inlineParsedSmart.scheduleState}</span>
                      </span>
                    )}
                    {inlineParsedSmart.deadline != null && (
                      <span className="meta-badge urgent">
                        <span>{inlineParsedSmart.deadlineLabel}</span>
                      </span>
                    )}
                    {inlineParsedSmart.durationMinutes != null && (
                      <span className="meta-badge duration-badge">
                        <span>{formatStandaloneDuration(inlineParsedSmart.durationMinutes)}</span>
                      </span>
                    )}
                    {inlineParsedSmart.isMilestone && (
                      <span className="meta-badge milestone-badge">
                        <span>◆ {t.milestoneLabel}</span>
                      </span>
                    )}
                    {inlineParsedSmart.matchedProject && (
                      <span className="meta-badge">
                        <span>{inlineParsedSmart.matchedProject.title}</span>
                      </span>
                    )}
                    {!inlineParsedSmart.matchedProject && inlineParsedSmart.matchedArea && (
                      <span className="meta-badge">
                        <span>{inlineParsedSmart.matchedArea.title}</span>
                      </span>
                    )}
                    {inlineParsedSmart.matchedTags.map((tg) => (
                      <span key={tg.id} className="meta-badge">
                        <span>#{tg.title}</span>
                      </span>
                    ))}
                  </div>
                </div>
              )}

              <div
                className="task-row-quick-strip"
                data-testid="task-row-quick-strip"
                onClick={stopRowPropagation}
                onDoubleClick={stopRowPropagation}
              >
                <button
                  type="button"
                  className="chip-btn task-quick-strip-chip"
                  data-testid="task-quick-strip-when"
                  title={t.actionWhen}
                  onClick={(e) => {
                    e.stopPropagation();
                    commitInlineQuickTitle(false);
                    onOpenWhenModal(task.id);
                  }}
                >
                  <Calendar size={11.5} />
                  <span>{t.actionWhen}</span>
                </button>

                <button
                  type="button"
                  className="chip-btn task-quick-strip-chip"
                  data-testid="task-quick-strip-deadline"
                  title={t.actionDeadline}
                  onClick={(e) => {
                    e.stopPropagation();
                    commitInlineQuickTitle(false);
                    onOpenDeadlineModal(task.id);
                  }}
                >
                  <Flag size={11.5} />
                  <span>{t.actionDeadline}</span>
                </button>

                <button
                  type="button"
                  className={`chip-btn task-quick-strip-chip ${task.effortLevel ? 'active' : ''}`}
                  data-testid="task-quick-strip-effort"
                  title={t.effortLabel}
                  onClick={(e) => {
                    e.stopPropagation();
                    persistEffortLevel(nextCycledEffortLevel(task.effortLevel));
                  }}
                >
                  <Zap size={11.5} />
                  <span>{effortDisplayLabel || t.effortLabel}</span>
                </button>

                <button
                  type="button"
                  className={`chip-btn task-quick-strip-chip ${taskTags.length > 0 ? 'active' : ''}`}
                  data-testid="task-quick-strip-tags"
                  title={t.actionTags}
                  onClick={(e) => {
                    e.stopPropagation();
                    commitInlineQuickTitle(false);
                    onOpenTagsModal(task.id);
                  }}
                >
                  <Hash size={11.5} />
                  <span>{t.actionTags}</span>
                </button>

                <button
                  type="button"
                  className="chip-btn task-quick-strip-chip active"
                  data-testid="task-quick-strip-open-details"
                  title={t.openDetailsAction}
                  onClick={(e) => {
                    e.stopPropagation();
                    commitInlineQuickTitle(true);
                    onExpand();
                  }}
                >
                  <Maximize2 size={11.5} />
                  <span>{t.openDetailsAction}</span>
                </button>
              </div>
            </div>
          ) : (
            <div className="task-row-title-line">
              {task.isPinned && (
                <span
                  className="task-pin-indicator"
                  title={t.pinnedBadge}
                  data-testid="task-pin-indicator"
                >
                  <Pin size={12.5} />
                </span>
              )}
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
          )}

          {task.notes.trim() && (
            <>
              <div className="task-row-notes-preview">{task.notes.trim().split('\n')[0]}</div>
              <DetectedNotesLinksRow
                links={extractDetectedLinksFromNotes(task.notes)}
                compact
              />
            </>
          )}

          {/* Ordered Second-Row Interactive Badges */}
          {hasSecondRowBadges && (
            <div className="task-row-badges-second">
              {/* 0. Milestone Badge */}
              {task.isMilestone && (
                <span
                  className="meta-badge milestone-badge"
                  data-testid="task-row-milestone-pill"
                  title={t.milestoneLabel}
                >
                  <span>◆ {t.milestoneLabel}</span>
                </span>
              )}

              {/* 0b. Active Blocker ("Waiting on…") Badge */}
              {isActivelyBlocked && (
                <button
                  type="button"
                  className="meta-badge interactive-badge blocked-badge"
                  data-testid="task-row-waiting-badge"
                  title={blockedSummaryLabel}
                  onPointerDown={stopRowPropagation}
                  onDoubleClick={stopRowPropagation}
                  onClick={(e) => {
                    e.stopPropagation();
                    onOpenDependenciesModal?.(task.id);
                  }}
                >
                  <Link2 size={10.5} />
                  <span>{blockedSummaryLabel}</span>
                </button>
              )}

              {/* 1. Schedule State Badge (This Evening everywhere; Today / Upcoming / Someday in Project & Area views) */}
              {task.scheduleState === 'THIS_EVENING' ? (
                <button
                  type="button"
                  className="meta-badge interactive-badge evening"
                  data-testid="task-row-when-badge"
                  title={t.viewThisEvening}
                  onPointerDown={stopRowPropagation}
                  onDoubleClick={stopRowPropagation}
                  onClick={(e) => {
                    e.stopPropagation();
                    onOpenWhenModal(task.id);
                  }}
                >
                  <Moon size={10.5} />
                  <span>{t.viewThisEvening}</span>
                </button>
              ) : showCollapsedSchedulePill ? (
                task.scheduleState === 'TODAY' ? (
                  <button
                    type="button"
                    className="meta-badge interactive-badge today"
                    data-testid="task-row-when-badge"
                    title={t.viewToday}
                    onPointerDown={stopRowPropagation}
                    onDoubleClick={stopRowPropagation}
                    onClick={(e) => {
                      e.stopPropagation();
                      onOpenWhenModal(task.id);
                    }}
                  >
                    <Sun size={10.5} style={{ color: 'var(--today-star)' }} />
                    <span>{t.viewToday}</span>
                  </button>
                ) : task.scheduleState === 'SOMEDAY' ? (
                  <button
                    type="button"
                    className="meta-badge interactive-badge"
                    data-testid="task-row-when-badge"
                    title={t.viewSomeday}
                    onPointerDown={stopRowPropagation}
                    onDoubleClick={stopRowPropagation}
                    onClick={(e) => {
                      e.stopPropagation();
                      onOpenWhenModal(task.id);
                    }}
                  >
                    <Cloud size={10.5} />
                    <span>{t.viewSomeday}</span>
                  </button>
                ) : task.whenDate != null ? (
                  <button
                    type="button"
                    className="meta-badge interactive-badge"
                    data-testid="task-row-when-badge"
                    title={`${t.actionWhen}: ${formatShortDate(task.whenDate)}`}
                    onPointerDown={stopRowPropagation}
                    onDoubleClick={stopRowPropagation}
                    onClick={(e) => {
                      e.stopPropagation();
                      onOpenWhenModal(task.id);
                    }}
                  >
                    <Calendar size={10.5} />
                    <span>{formatShortDate(task.whenDate)}</span>
                  </button>
                ) : null
              ) : null}

              {/* 1b. v2.0 Timeblock Range Pill OR v3.0 Standalone Duration Pill */}
              {timeblockRangeLabel ? (
                <div
                  className="dropdown-anchor"
                  onPointerDown={stopRowPropagation}
                  onDoubleClick={stopRowPropagation}
                  onClick={stopRowPropagation}
                >
                  <button
                    type="button"
                    className="meta-badge interactive-badge v2-timeblock-pill"
                    data-testid="task-row-timeblock-pill"
                    title={`${t.actionTimeblock}: ${timeblockRangeLabel}`}
                    onClick={(e) => {
                      e.stopPropagation();
                      setCollapsedBadgePopover((cur) =>
                        cur === 'timeblock' ? null : 'timeblock'
                      );
                    }}
                  >
                    <span>🕐 {timeblockRangeLabel}</span>
                  </button>
                  {collapsedBadgePopover === 'timeblock' && (
                    <>
                      <div
                        style={{ position: 'fixed', inset: 0, zIndex: 82 }}
                        onClick={(e) => {
                          e.stopPropagation();
                          setCollapsedBadgePopover(null);
                        }}
                      />
                      <div
                        className="dropdown-popover task-row-badge-popover"
                        data-testid="task-row-timeblock-popover"
                        style={{ left: 0, right: 'auto', minWidth: 256, zIndex: 88 }}
                        onClick={(e) => e.stopPropagation()}
                      >
                        <div className="v2-timeblock-row">
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
                                  durationMinutes:
                                    task.durationMinutes ?? DEFAULT_TIMEBLOCK_DURATION_MINUTES
                                });
                              }
                            }}
                          />
                          <div className="v2-timeblock-slots">
                            {TIMEBLOCK_QUICK_START_MINUTES.map((mins) => (
                              <button
                                key={mins}
                                type="button"
                                className={`chip-btn ${
                                  task.scheduledStartMinutes === mins ? 'active' : ''
                                }`}
                                onClick={() => {
                                  void localDb.upsertTask({
                                    id: task.id,
                                    scheduledStartMinutes: mins,
                                    durationMinutes:
                                      task.durationMinutes ?? DEFAULT_TIMEBLOCK_DURATION_MINUTES
                                  });
                                }}
                              >
                                {formatMinutesAsClock(mins)}
                              </button>
                            ))}
                          </div>
                        </div>
                        <div className="v2-timeblock-row" style={{ marginTop: 6 }}>
                          <div className="v2-timeblock-slots">
                            {TIMEBLOCK_DURATION_PRESETS.map((dur) => (
                              <button
                                key={dur}
                                type="button"
                                className={`chip-btn ${
                                  (task.durationMinutes ?? DEFAULT_TIMEBLOCK_DURATION_MINUTES) ===
                                  dur
                                    ? 'active'
                                    : ''
                                }`}
                                onClick={() => {
                                  void localDb.upsertTask({
                                    id: task.id,
                                    scheduledStartMinutes:
                                      task.scheduledStartMinutes ?? 9 * 60,
                                    durationMinutes: dur
                                  });
                                }}
                              >
                                {formatStandaloneDuration(dur)}
                              </button>
                            ))}
                            <button
                              type="button"
                              className="chip-btn urgent"
                              title={t.timeblockClear}
                              onClick={() => {
                                setCollapsedBadgePopover(null);
                                void localDb.upsertTask({
                                  id: task.id,
                                  scheduledStartMinutes: null
                                });
                              }}
                            >
                              <X size={11} />
                              <span>{t.actionClear}</span>
                            </button>
                          </div>
                        </div>
                      </div>
                    </>
                  )}
                </div>
              ) : standaloneDurationLabel ? (
                <div
                  className="dropdown-anchor"
                  onPointerDown={stopRowPropagation}
                  onDoubleClick={stopRowPropagation}
                  onClick={stopRowPropagation}
                >
                  <button
                    type="button"
                    className="meta-badge interactive-badge duration-badge"
                    data-testid="task-row-duration-pill"
                    title={`${t.durationLabel}: ${
                      actualMinutesValue > 0 ? actualFocusedBadgeLabel : standaloneDurationLabel
                    }`}
                    onClick={(e) => {
                      e.stopPropagation();
                      setCollapsedBadgePopover((cur) =>
                        cur === 'duration' ? null : 'duration'
                      );
                    }}
                  >
                    <Hourglass size={10} />
                    <span>
                      {actualMinutesValue > 0 ? actualFocusedBadgeLabel : standaloneDurationLabel}
                    </span>
                  </button>
                  {collapsedBadgePopover === 'duration' && (
                    <>
                      <div
                        style={{ position: 'fixed', inset: 0, zIndex: 82 }}
                        onClick={(e) => {
                          e.stopPropagation();
                          setCollapsedBadgePopover(null);
                        }}
                      />
                      <div
                        className="dropdown-popover task-row-badge-popover"
                        data-testid="task-row-duration-popover"
                        style={{ left: 0, right: 'auto', minWidth: 220, zIndex: 88 }}
                        onClick={(e) => e.stopPropagation()}
                      >
                        <div className="v2-timeblock-slots">
                          {STANDALONE_DURATION_PRESETS.map((dur) => (
                            <button
                              key={dur}
                              type="button"
                              className={`chip-btn ${task.durationMinutes === dur ? 'active' : ''}`}
                              onClick={() => {
                                setCollapsedBadgePopover(null);
                                void localDb.upsertTask({
                                  id: task.id,
                                  durationMinutes: task.durationMinutes === dur ? null : dur
                                });
                              }}
                            >
                              {formatStandaloneDuration(dur)}
                            </button>
                          ))}
                          <button
                            type="button"
                            className="chip-btn urgent"
                            title={t.durationClear}
                            onClick={() => {
                              setCollapsedBadgePopover(null);
                              void localDb.upsertTask({ id: task.id, durationMinutes: null });
                            }}
                          >
                            <X size={11} />
                          </button>
                        </div>
                        <div className="v3-custom-duration-row compact">
                          <input
                            type="text"
                            className="v3-custom-duration-input"
                            value={customDurationDraft}
                            placeholder={t.durationCustomPlaceholder}
                            onChange={(e) => setCustomDurationDraft(e.target.value)}
                            onKeyDown={(e) => {
                              if (e.key === 'Enter') {
                                e.preventDefault();
                                e.stopPropagation();
                                const mins = parseCustomDurationMinutes(customDurationDraft);
                                if (mins != null) {
                                  setCustomDurationDraft('');
                                  setCollapsedBadgePopover(null);
                                  void localDb.upsertTask({ id: task.id, durationMinutes: mins });
                                }
                              }
                            }}
                          />
                          <button
                            type="button"
                            className="chip-btn active"
                            disabled={parseCustomDurationMinutes(customDurationDraft) == null}
                            onClick={() => {
                              const mins = parseCustomDurationMinutes(customDurationDraft);
                              if (mins != null) {
                                setCustomDurationDraft('');
                                setCollapsedBadgePopover(null);
                                void localDb.upsertTask({ id: task.id, durationMinutes: mins });
                              }
                            }}
                          >
                            <Check size={11} />
                          </button>
                        </div>
                      </div>
                    </>
                  )}
                </div>
              ) : null}

              {/* 1b-actual. Logged actualMinutes pill when no standalone duration pill is shown */}
              {actualMinutesValue > 0 && !standaloneDurationLabel && (
                <span
                  className="meta-badge duration-badge"
                  data-testid="task-row-actual-minutes-pill"
                  title={actualFocusedBadgeLabel}
                >
                  <Timer size={10} />
                  <span>{actualFocusedBadgeLabel}</span>
                </span>
              )}

              {/* 1c. v3.0 Effort Pill */}
              {effortDisplayLabel && (
                <div
                  className="dropdown-anchor"
                  onPointerDown={stopRowPropagation}
                  onDoubleClick={stopRowPropagation}
                  onClick={stopRowPropagation}
                >
                  <button
                    type="button"
                    className={`meta-badge interactive-badge effort-badge effort-${(
                      task.effortLevel || ''
                    ).toLowerCase()}`}
                    data-testid="task-row-effort-pill"
                    title={`${t.effortLabel}: ${effortDisplayLabel}`}
                    onClick={(e) => {
                      e.stopPropagation();
                      setCollapsedBadgePopover((cur) => (cur === 'effort' ? null : 'effort'));
                    }}
                  >
                    <span>{effortDisplayLabel}</span>
                  </button>
                  {collapsedBadgePopover === 'effort' && (
                    <>
                      <div
                        style={{ position: 'fixed', inset: 0, zIndex: 82 }}
                        onClick={(e) => {
                          e.stopPropagation();
                          setCollapsedBadgePopover(null);
                        }}
                      />
                      <div
                        className="dropdown-popover task-row-badge-popover"
                        data-testid="task-row-effort-popover"
                        style={{ left: 0, right: 'auto', minWidth: 180, zIndex: 88 }}
                        onClick={(e) => e.stopPropagation()}
                      >
                        {(
                          [
                            ['QUICK_WIN', `⚡ ${t.effortQuickWin}`],
                            ['MEDIUM', t.effortMedium],
                            ['DEEP_FOCUS', `🧠 ${t.effortDeepFocus}`],
                            [null, t.effortNone]
                          ] as Array<[EffortLevel | null, string]>
                        ).map(([lvl, label]) => (
                          <button
                            key={lvl ?? 'none'}
                            type="button"
                            className="dropdown-item"
                            onClick={() => {
                              setCollapsedBadgePopover(null);
                              persistEffortLevel(lvl);
                            }}
                          >
                            <span>{label}</span>
                            {(task.effortLevel ?? null) === lvl && <Check size={13.5} />}
                          </button>
                        ))}
                      </div>
                    </>
                  )}
                </div>
              )}

              {/* 2. Repeat */}
              {task.recurrenceRule != null && (
                <button
                  type="button"
                  className="meta-badge interactive-badge"
                  data-testid="task-row-repeat-badge"
                  title={`${t.actionRepeat}: ${formatRecurrenceShort(task.recurrenceRule, lang)}`}
                  onPointerDown={stopRowPropagation}
                  onDoubleClick={stopRowPropagation}
                  onClick={(e) => {
                    e.stopPropagation();
                    onOpenRepeatModal(task.id);
                  }}
                >
                  <Repeat size={10.5} />
                  <span>{formatRecurrenceShort(task.recurrenceRule, lang)}</span>
                </button>
              )}

              {/* 3. Reminder */}
              {task.reminderTime != null && (
                <button
                  type="button"
                  className="meta-badge interactive-badge"
                  data-testid="task-row-reminder-badge"
                  title={`${t.actionReminder}: ${formatReminderClock(task.reminderTime)}`}
                  onPointerDown={stopRowPropagation}
                  onDoubleClick={stopRowPropagation}
                  onClick={(e) => {
                    e.stopPropagation();
                    onOpenWhenModal(task.id);
                  }}
                >
                  <Bell size={10.5} />
                  <span>{formatReminderClock(task.reminderTime)}</span>
                </button>
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
                <button
                  key={tg.id}
                  type="button"
                  className="meta-badge interactive-badge"
                  data-testid={`task-row-tag-badge-${tg.id}`}
                  title={`${t.entityTag}: ${tg.title}`}
                  onPointerDown={stopRowPropagation}
                  onDoubleClick={stopRowPropagation}
                  onClick={(e) => {
                    e.stopPropagation();
                    onOpenTagsModal(task.id);
                  }}
                >
                  <LayoutGrid size={9.5} />
                  <span>{tg.title}</span>
                </button>
              ))}

              {/* 6. Deadline */}
              {task.deadline != null && (() => {
                const dl = renderDeadlineMeta(task.deadline);
                return (
                  <button
                    type="button"
                    className={`meta-badge interactive-badge ${dl.urgent ? 'urgent' : ''}`}
                    data-testid="task-row-deadline-badge"
                    title={`${t.actionDeadline}: ${dl.label}`}
                    onPointerDown={stopRowPropagation}
                    onDoubleClick={stopRowPropagation}
                    onClick={(e) => {
                      e.stopPropagation();
                      onOpenDeadlineModal(task.id);
                    }}
                  >
                    <Flag size={10.5} />
                    <span>{dl.label}</span>
                  </button>
                );
              })()}
            </div>
          )}
        </div>

        <div className="task-row-trailing-actions">
          <button
            type="button"
            className={`task-row-quick-edit-btn ${isInlineQuickEditing ? 'active' : ''}`}
            title={t.quickEditAction}
            aria-label={t.quickEditAction}
            data-testid="task-row-quick-edit-btn"
            onPointerDown={stopRowPropagation}
            onClick={(e) => {
              e.stopPropagation();
              if (isInlineQuickEditing) {
                commitInlineQuickTitle(true);
              } else {
                startInlineQuickEdit();
              }
            }}
            onDoubleClick={stopRowPropagation}
          >
            <Edit3 size={13} />
          </button>

          <button
            type="button"
            className="task-row-open-btn"
            title={t.openDetailsAction}
            aria-label={t.openDetailsAction}
            data-testid={`task-row-open-modal-${task.id}`}
            onPointerDown={stopRowPropagation}
            onClick={(e) => {
              e.stopPropagation();
              if (isInlineQuickEditing) {
                commitInlineQuickTitle(true);
              }
              onExpand();
            }}
            onDoubleClick={stopRowPropagation}
          >
            <Maximize2 size={12.5} />
          </button>
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

              {/* 1. Open Details (Full Two-Zone Soft Bento Modal) */}
              <button
                type="button"
                className="context-menu-item"
                data-testid="task-ctx-edit-btn"
                onClick={() => {
                  setCtxMenu(null);
                  onExpand();
                }}
              >
                <Maximize2 size={14} />
                <span>{t.openDetailsAction}</span>
              </button>

              {/* 2. Complete / Reopen */}
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

              <div className="context-menu-divider" />

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

              {/* 5. Move (Project / Area) */}
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

              {/* 6. Assign Categories */}
              <button
                type="button"
                className="context-menu-item"
                onClick={() => {
                  setCtxMenu(null);
                  onOpenTagsModal(task.id);
                }}
              >
                <LayoutGrid size={14} />
                <span>{t.ctxAssignTags}</span>
              </button>

              <div className="context-menu-divider" />

              {/* 7. Pin to Top / Unpin */}
              <button
                type="button"
                className="context-menu-item"
                onClick={() => {
                  setCtxMenu(null);
                  void localDb.upsertTask({ id: task.id, isPinned: !task.isPinned });
                }}
              >
                {task.isPinned ? <PinOff size={14} /> : <Pin size={14} />}
                <span>{task.isPinned ? t.unpinTask : t.pinTask}</span>
              </button>

              {/* 8. Mark as Milestone / Remove Milestone */}
              <button
                type="button"
                className="context-menu-item"
                data-testid="task-ctx-milestone-btn"
                onClick={() => {
                  setCtxMenu(null);
                  void localDb.upsertTask({ id: task.id, isMilestone: !task.isMilestone });
                }}
              >
                <Diamond size={14} style={{ color: 'var(--milestone-violet)' }} />
                <span>{task.isMilestone ? t.removeMilestone : t.markAsMilestone}</span>
              </button>

              {/* 9. Duplicate To-Do */}
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

              {/* 10. Mark as Canceled */}
              {task.status !== 'CANCELED' && (
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
              )}

              {/* 11. Delete To-Do */}
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
