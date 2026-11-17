import React, { useRef, useState } from 'react';
import {
  CheckCircle2,
  FolderPlus,
  Heading,
  Plus
} from 'lucide-react';
import type {
  ActiveNavigation,
  HeadingRecord,
  TaskRecord
} from '../domain/types';
import {
  resolveMagicPlusDrop,
  type MagicPlusHeadingBox,
  type MagicPlusResolvedDrop,
  type MagicPlusTaskBox
} from '../domain/magicPlusDrop';
import type { TranslationStrings } from '../i18n/translations';

interface MagicPlusFabProps {
  open: boolean;
  activeNav: ActiveNavigation;
  currentViewTitle: string;
  tasks: TaskRecord[];
  headings: HeadingRecord[];
  scrollContainerRef: React.RefObject<HTMLDivElement | null>;
  t: TranslationStrings;
  onToggleOpen: () => void;
  onClose: () => void;
  onNewTodo: () => void;
  onNewHeading: () => void;
  onNewProject: () => void;
  onDropInsert: (drop: MagicPlusResolvedDrop) => void;
}

interface PointerSession {
  pointerId: number;
  startX: number;
  startY: number;
  fabCenterX: number;
  fabCenterY: number;
  hasDragged: boolean;
}

export const MagicPlusFab: React.FC<MagicPlusFabProps> = ({
  open,
  activeNav,
  currentViewTitle,
  tasks,
  headings,
  scrollContainerRef,
  t,
  onToggleOpen,
  onClose,
  onNewTodo,
  onNewHeading,
  onNewProject,
  onDropInsert
}) => {
  const isProjectView = activeNav.kind === 'project';
  const canDragInsert = !(
    activeNav.kind === 'smart' &&
    (activeNav.view === 'LOGBOOK' || activeNav.view === 'TRASH')
  );

  const sessionRef = useRef<PointerSession | null>(null);
  const suppressNextClickRef = useRef(false);
  const activeDropRef = useRef<MagicPlusResolvedDrop | null>(null);

  const [dragOffset, setDragOffset] = useState<{ dx: number; dy: number } | null>(null);
  const [activeDrop, setActiveDrop] = useState<MagicPlusResolvedDrop | null>(null);

  const computeDropFromDom = (
    clientX: number,
    clientY: number
  ): MagicPlusResolvedDrop | null => {
    const scrollEl = scrollContainerRef.current;
    if (!scrollEl) return null;

    const scrollRect = scrollEl.getBoundingClientRect();
    if (
      clientX < scrollRect.left - 24 ||
      clientX > scrollRect.right + 24 ||
      clientY < scrollRect.top - 12 ||
      clientY > scrollRect.bottom + 24
    ) {
      return null;
    }

    const containerEl =
      scrollEl.querySelector<HTMLElement>('.workspace-container') ?? scrollEl;
    const containerRect = containerEl.getBoundingClientRect();

    const tasksById = new Map<string, TaskRecord>();
    for (const tk of tasks) {
      tasksById.set(tk.id, tk);
    }

    const headingsById = new Map<string, HeadingRecord>();
    for (const hd of headings) {
      headingsById.set(hd.id, hd);
    }

    const taskNodes = containerEl.querySelectorAll<HTMLElement>('[data-task-id]');
    const taskBoxes: MagicPlusTaskBox[] = [];
    taskNodes.forEach((node) => {
      const id = node.getAttribute('data-task-id');
      if (!id) return;
      const tk = tasksById.get(id);
      if (!tk) return;
      const rect = node.getBoundingClientRect();
      if (rect.height <= 0) return;

      const sectionEl = node.closest<HTMLElement>('.group-section');
      const sectionEvening = sectionEl?.getAttribute('data-magic-evening') === 'true';
      const sectionWhenAttr = sectionEl?.getAttribute('data-magic-when-date');
      const sectionWhenDate =
        sectionWhenAttr != null && sectionWhenAttr !== ''
          ? Number(sectionWhenAttr)
          : null;
      const sectionHeadingId = sectionEl?.getAttribute('data-magic-heading-id') ?? null;

      taskBoxes.push({
        taskId: id,
        sortOrder: tk.sortOrder,
        headingId: isProjectView ? sectionHeadingId ?? tk.headingId : null,
        evening:
          activeNav.kind === 'smart' && activeNav.view === 'TODAY'
            ? sectionEvening || tk.scheduleState === 'THIS_EVENING'
            : false,
        whenDate:
          activeNav.kind === 'smart' && activeNav.view === 'UPCOMING'
            ? sectionWhenDate ?? tk.whenDate
            : null,
        top: rect.top,
        bottom: rect.bottom
      });
    });

    const headingBoxes: MagicPlusHeadingBox[] = [];
    if (isProjectView) {
      const headingNodes = containerEl.querySelectorAll<HTMLElement>('[data-heading-id]');
      headingNodes.forEach((node) => {
        const id = node.getAttribute('data-heading-id');
        if (!id) return;
        const hd = headingsById.get(id);
        if (!hd) return;
        const rect = node.getBoundingClientRect();
        if (rect.height <= 0) return;
        const sectionEl = node.closest<HTMLElement>('.group-section');
        const sectionRect = sectionEl?.getBoundingClientRect();
        headingBoxes.push({
          headingId: id,
          sortOrder: hd.sortOrder,
          top: rect.top,
          bottom: rect.bottom,
          sectionBottom: sectionRect ? sectionRect.bottom : rect.bottom
        });
      });
    }

    return resolveMagicPlusDrop({
      clientX,
      clientY,
      isProjectView,
      containerLeft: containerRect.left,
      containerWidth: containerRect.width,
      containerTop: containerRect.top,
      taskBoxes,
      headingBoxes
    });
  };

  const resetDrag = () => {
    sessionRef.current = null;
    activeDropRef.current = null;
    setDragOffset(null);
    setActiveDrop(null);
  };

  const handlePointerDown = (e: React.PointerEvent<HTMLButtonElement>) => {
    if (e.pointerType === 'mouse' && e.button !== 0) return;
    const rect = e.currentTarget.getBoundingClientRect();
    sessionRef.current = {
      pointerId: e.pointerId,
      startX: e.clientX,
      startY: e.clientY,
      fabCenterX: rect.left + rect.width / 2,
      fabCenterY: rect.top + rect.height / 2,
      hasDragged: false
    };
    try {
      e.currentTarget.setPointerCapture(e.pointerId);
    } catch {
      // Synthetic events in headless environments may not support pointer capture
    }
  };

  const handlePointerMove = (e: React.PointerEvent<HTMLButtonElement>) => {
    const session = sessionRef.current;
    if (!session || session.pointerId !== e.pointerId) return;

    const dx = e.clientX - session.startX;
    const dy = e.clientY - session.startY;
    const dist = Math.hypot(dx, dy);

    if (!session.hasDragged) {
      if (!canDragInsert || dist < 6) return;
      session.hasDragged = true;
      if (open) {
        onClose();
      }
    }

    setDragOffset({ dx, dy });

    const distFromFabOrigin = Math.hypot(
      e.clientX - session.fabCenterX,
      e.clientY - session.fabCenterY
    );
    if (distFromFabOrigin <= 36) {
      activeDropRef.current = null;
      setActiveDrop(null);
      return;
    }

    const resolved = computeDropFromDom(e.clientX, e.clientY);
    activeDropRef.current = resolved;
    setActiveDrop(resolved);
  };

  const handlePointerUp = (e: React.PointerEvent<HTMLButtonElement>) => {
    const session = sessionRef.current;
    if (!session || session.pointerId !== e.pointerId) return;

    try {
      if (e.currentTarget.hasPointerCapture(e.pointerId)) {
        e.currentTarget.releasePointerCapture(e.pointerId);
      }
    } catch {
      // Ignore release errors
    }

    if (session.hasDragged) {
      suppressNextClickRef.current = true;
      window.setTimeout(() => {
        suppressNextClickRef.current = false;
      }, 60);
      const committedDrop = activeDropRef.current;
      resetDrag();
      if (committedDrop) {
        onDropInsert(committedDrop);
      }
      return;
    }

    resetDrag();
  };

  const handlePointerCancel = () => {
    resetDrag();
  };

  const isDragging = dragOffset !== null;

  return (
    <>
      {open && !isDragging && (
        <>
          <div className="magic-fab-backdrop" onClick={onClose} />
          <div className="magic-fab-popover" role="menu">
            {/* 1. New To-Do */}
            <button
              type="button"
              className="magic-fab-option"
              onClick={() => {
                onClose();
                onNewTodo();
              }}
            >
              <span className="magic-fab-opt-icon">
                <CheckCircle2 size={17} />
              </span>
              <div style={{ flex: 1, minWidth: 0 }}>
                <div className="magic-fab-opt-title-row">
                  <span className="magic-fab-opt-title">{t.entityNewTodo}</span>
                  <span className="magic-fab-shortcut-badge">Ctrl+N</span>
                </div>
                <div className="magic-fab-opt-sub">
                  {currentViewTitle
                    ? `${t.magicTodoSubDefault} • ${currentViewTitle}`
                    : t.magicTodoSubDefault}
                </div>
              </div>
            </button>

            {/* 2. New Heading (only in ProjectDetail) */}
            {isProjectView && (
              <button
                type="button"
                className="magic-fab-option"
                onClick={() => {
                  onClose();
                  onNewHeading();
                }}
              >
                <span className="magic-fab-opt-icon">
                  <Heading size={17} />
                </span>
                <div>
                  <div className="magic-fab-opt-title">{t.entityNewHeading}</div>
                  <div className="magic-fab-opt-sub">{t.magicHeadingSub}</div>
                </div>
              </button>
            )}

            {/* 3. New Project */}
            <button
              type="button"
              className="magic-fab-option"
              onClick={() => {
                onClose();
                onNewProject();
              }}
            >
              <span className="magic-fab-opt-icon">
                <FolderPlus size={17} />
              </span>
              <div style={{ flex: 1, minWidth: 0 }}>
                <div className="magic-fab-opt-title-row">
                  <span className="magic-fab-opt-title">{t.entityNewProject}</span>
                  <span className="magic-fab-shortcut-badge">Ctrl+Shift+N</span>
                </div>
                <div className="magic-fab-opt-sub">{t.magicProjectSub}</div>
              </div>
            </button>
          </div>
        </>
      )}

      {activeDrop && (
        <div
          className={`magic-plus-drop-indicator ${
            activeDrop.mode === 'heading' ? 'heading-mode' : 'todo-mode'
          }`}
          style={{
            top: `${activeDrop.indicatorTop}px`,
            left: `${activeDrop.indicatorLeft}px`,
            width: `${activeDrop.indicatorWidth}px`
          }}
          aria-hidden="true"
        >
          <span className="magic-plus-drop-dot" />
          <span className="magic-plus-drop-line" />
          <span className="magic-plus-drop-pill">
            {activeDrop.mode === 'heading' ? t.entityNewHeading : t.entityNewTodo}
          </span>
        </div>
      )}

      <button
        type="button"
        className={`magic-fab-circle ${open && !isDragging ? 'open' : ''} ${
          isDragging ? 'dragging' : ''
        }`}
        style={
          dragOffset
            ? {
                transform: `translate3d(${dragOffset.dx}px, ${dragOffset.dy}px, 0) scale(1.08)`,
                transition: 'none'
              }
            : undefined
        }
        onPointerDown={handlePointerDown}
        onPointerMove={handlePointerMove}
        onPointerUp={handlePointerUp}
        onPointerCancel={handlePointerCancel}
        onClick={() => {
          if (suppressNextClickRef.current) {
            suppressNextClickRef.current = false;
            return;
          }
          onToggleOpen();
        }}
        aria-label={t.entityNewTodo}
      >
        {isDragging && activeDrop && (
          <span className="magic-fab-drag-badge">
            {activeDrop.mode === 'heading' ? t.entityNewHeading : t.entityNewTodo}
          </span>
        )}
        <Plus size={26} strokeWidth={2.4} />
      </button>
    </>
  );
};
