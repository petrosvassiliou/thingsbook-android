import React, { useState } from 'react';
import {
  BookOpen,
  CalendarDays,
  ChevronDown,
  ChevronRight,
  ClipboardList,
  Cloud,
  Compass,
  Download,
  Edit3,
  Flag,
  Folder,
  FolderKanban,
  History,
  Inbox,
  LayoutGrid,
  ListChecks,
  Plus,
  SlidersHorizontal,
  Sun,
  Trash2,
  X
} from 'lucide-react';
import {
  APP_VERSION,
  MAX_AREAS,
  type ActiveNavigation,
  type AreaRecord,
  type ProjectRecord,
  type SavedPerspective,
  type SmartViewType,
  type TaskRecord
} from '../domain/types';
import { isPrereleaseVersion } from '../data/versionUtils';
import {
  extractDraggedTaskIds,
  hasDraggedTaskMime
} from '../domain/v30Roadmap';
import type { TranslationStrings } from '../i18n/translations';
import { ProjectProgressRing } from './ProjectProgressRing';

export type SidebarDropTarget =
  | { kind: 'smart'; view: SmartViewType }
  | { kind: 'area'; areaId: string }
  | { kind: 'project'; projectId: string };

interface SidebarProps {
  visible: boolean;
  activeNav: ActiveNavigation;
  areas: AreaRecord[];
  projects: ProjectRecord[];
  tasks: TaskRecord[];
  savedPerspectives?: SavedPerspective[];
  perspectiveCounts?: ReadonlyMap<string, number>;
  selectedTaskIds?: string[];
  orderedVisibleTaskIds?: string[];
  counts: {
    inbox: number;
    today: number;
    upcoming: number;
    anytime: number;
    someday: number;
    trash?: number;
  };
  stalledProjectsCount?: number;
  canInstallPwa: boolean;
  isStandalonePwa: boolean;
  t: TranslationStrings;
  onSelectNav: (nav: ActiveNavigation) => void;
  onCloseMobile: () => void;
  onCreateProjectInArea: (areaId: string | null) => void;
  onOpenTemplates: (areaId: string | null) => void;
  onOpenWeeklyReview: () => void;
  onOpenManageAreas: () => void;
  onOpenManageTags?: () => void;
  onCreatePerspective?: () => void;
  onEditPerspective?: (perspective: SavedPerspective) => void;
  onDropTaskOnTarget?: (taskId: string, target: SidebarDropTarget) => void;
  onDropTasksOnTarget?: (taskIds: string[], target: SidebarDropTarget) => void;
  onTriggerInstallPwa: () => void;
}

function renderMonochromeSmartIcon(view: SmartViewType, size: number, isActive: boolean): React.ReactNode {
  const color = isActive ? 'var(--things-blue)' : 'var(--text-secondary)';
  switch (view) {
    case 'INBOX':
      return <Inbox size={size} style={{ color }} />;
    case 'TODAY':
      return <Sun size={size} style={{ color }} />;
    case 'UPCOMING':
      return <CalendarDays size={size} style={{ color }} />;
    case 'ANYTIME':
      return <Compass size={size} style={{ color }} />;
    case 'SOMEDAY':
      return <Cloud size={size} style={{ color }} />;
    case 'LOGBOOK':
      return <History size={size} style={{ color }} />;
    case 'TRASH':
      return <Trash2 size={size} style={{ color }} />;
  }
}

export const Sidebar: React.FC<SidebarProps> = ({
  visible,
  activeNav,
  areas,
  projects,
  tasks,
  savedPerspectives = [],
  perspectiveCounts,
  selectedTaskIds = [],
  orderedVisibleTaskIds = [],
  counts,
  stalledProjectsCount = 0,
  canInstallPwa,
  isStandalonePwa,
  t,
  onSelectNav,
  onCloseMobile,
  onCreateProjectInArea,
  onOpenTemplates,
  onOpenWeeklyReview,
  onOpenManageAreas,
  onOpenManageTags,
  onCreatePerspective,
  onEditPerspective,
  onDropTaskOnTarget,
  onDropTasksOnTarget,
  onTriggerInstallPwa
}) => {
  const [dragTargetKey, setDragTargetKey] = useState<string | null>(null);
  const [collapsedAreas, setCollapsedAreas] = useState<Record<string, boolean>>({});
  const [newProjectMenuOpen, setNewProjectMenuOpen] = useState(false);

  const activeAreas = areas.filter((a) => !a.isDeleted);
  const activeProjects = projects.filter((p) => !p.isDeleted && p.status === 'ACTIVE');
  const unassignedProjects = activeProjects.filter(
    (p) => !p.areaId || !activeAreas.some((a) => a.id === p.areaId)
  );
  const sortedPerspectives = [...savedPerspectives].sort((a, b) => a.sortOrder - b.sortOrder);
  const isBetaBuild = isPrereleaseVersion(APP_VERSION);

  const projectProgress = (projectId: string) => {
    const projTasks = tasks.filter(
      (tk) => !tk.isDeleted && tk.projectId === projectId && tk.status !== 'CANCELED'
    );
    const done = projTasks.filter((tk) => tk.status === 'COMPLETED').length;
    return { done, total: projTasks.length };
  };

  const resolveCurrentAreaId = (): string | null =>
    activeNav.kind === 'area'
      ? activeNav.areaId
      : activeNav.kind === 'project'
        ? (projects.find((p) => p.id === activeNav.projectId)?.areaId ?? activeAreas[0]?.id ?? null)
        : (activeAreas[0]?.id ?? null);

  const makeDropHandlers = (key: string, target: SidebarDropTarget) => ({
    onDragOver: (e: React.DragEvent) => {
      if (hasDraggedTaskMime(e.dataTransfer)) {
        e.preventDefault();
        e.dataTransfer.dropEffect = 'move';
        if (dragTargetKey !== key) setDragTargetKey(key);
      }
    },
    onDragLeave: () => {
      if (dragTargetKey === key) setDragTargetKey(null);
    },
    onDrop: (e: React.DragEvent) => {
      e.preventDefault();
      setDragTargetKey(null);
      const taskIds = extractDraggedTaskIds(
        e.dataTransfer,
        selectedTaskIds,
        orderedVisibleTaskIds
      );
      if (taskIds.length === 0) return;
      if (onDropTasksOnTarget) {
        onDropTasksOnTarget(taskIds, target);
      } else if (onDropTaskOnTarget) {
        for (const tid of taskIds) {
          onDropTaskOnTarget(tid, target);
        }
      }
    }
  });

  const bentoPrimaryItems: Array<{
    view: SmartViewType;
    label: string;
    count?: number;
    shortcutIdx: number;
  }> = [
    { view: 'INBOX', label: t.viewInbox, count: counts.inbox, shortcutIdx: 1 },
    { view: 'TODAY', label: t.viewToday, count: counts.today, shortcutIdx: 2 },
    { view: 'UPCOMING', label: t.viewUpcoming, count: counts.upcoming, shortcutIdx: 3 },
    { view: 'ANYTIME', label: t.viewAnytime, count: counts.anytime, shortcutIdx: 4 }
  ];

  const isSomedayActive = activeNav.kind === 'smart' && activeNav.view === 'SOMEDAY';
  const isLogbookActive = activeNav.kind === 'smart' && activeNav.view === 'LOGBOOK';
  const isTrashActive = activeNav.kind === 'smart' && activeNav.view === 'TRASH';

  return (
    <>
      <div
        className={`sidebar-backdrop ${visible ? 'visible' : ''}`}
        onClick={onCloseMobile}
      />
      <aside className={`sidebar ${visible ? '' : 'collapsed'}`} aria-label="Thingsbook Navigation">
        <div className="sidebar-brand-bar">
          <button
            type="button"
            className="brand-title-group"
            onClick={() => onSelectNav({ kind: 'smart', view: 'TODAY' })}
            title={t.viewToday}
          >
            <span className="brand-app-icon">
              <BookOpen size={14} strokeWidth={2.4} />
            </span>
            <span className="brand-name">{t.appName}</span>
            {isBetaBuild && <span className="v2-beta-pill">{t.betaChannelBadge}</span>}
          </button>
          <button
            type="button"
            className="icon-btn sidebar-close-mobile"
            onClick={onCloseMobile}
            aria-label={t.actionDone}
            title={t.sidebarHide}
          >
            <X size={17} />
          </button>
        </div>

        <div className="sidebar-scroll">
          {/* 1. 2x2 Bento Focus Grid (Inbox, Today, Upcoming, Anytime — never truncated) */}
          <div className="bento-focus-grid">
            {bentoPrimaryItems.map((item) => {
              const isActive = activeNav.kind === 'smart' && activeNav.view === item.view;
              const dKey = `smart-${item.view}`;
              const labelStyle =
                item.label.length > 14 ? { fontSize: '11.4px' } : undefined;
              return (
                <button
                  key={item.view}
                  type="button"
                  className={`bento-tile ${isActive ? 'active' : ''} ${
                    dragTargetKey === dKey ? 'drag-over' : ''
                  }`}
                  title={`${item.label} (Ctrl+${item.shortcutIdx})`}
                  onClick={() => onSelectNav({ kind: 'smart', view: item.view })}
                  {...makeDropHandlers(dKey, { kind: 'smart', view: item.view })}
                >
                  <div className="bento-tile-top">
                    <span className="bento-tile-icon">
                      {renderMonochromeSmartIcon(item.view, 16.5, isActive)}
                    </span>
                    <span className="bento-tile-count">
                      {item.count != null && item.count > 0 ? item.count : ''}
                    </span>
                  </div>
                  <span className="bento-tile-label" style={labelStyle}>
                    {item.label}
                  </span>
                </button>
              );
            })}
          </div>

          {/* 2. Compact 2-Row Micro-Bento Cards (Someday & Weekly Review — full-width labels, zero truncation) */}
          <div className="bento-mini-grid">
            <button
              type="button"
              className={`bento-mini-card ${isSomedayActive ? 'active' : ''} ${
                dragTargetKey === 'smart-SOMEDAY' ? 'drag-over' : ''
              }`}
              title={`${t.viewSomeday} (Ctrl+5)`}
              onClick={() => onSelectNav({ kind: 'smart', view: 'SOMEDAY' })}
              {...makeDropHandlers('smart-SOMEDAY', { kind: 'smart', view: 'SOMEDAY' })}
            >
              <div className="bento-mini-top">
                <span className="bento-tile-icon">
                  {renderMonochromeSmartIcon('SOMEDAY', 14, isSomedayActive)}
                </span>
                <span className="bento-mini-count">
                  {counts.someday > 0 ? counts.someday : ''}
                </span>
              </div>
              <span
                className="bento-mini-label"
                style={t.viewSomeday.length > 13 ? { fontSize: '11.1px' } : undefined}
              >
                {t.viewSomeday}
              </span>
            </button>

            <button
              type="button"
              className="bento-mini-card"
              data-testid="sidebar-weekly-review-btn"
              title={`${t.weeklyReviewTitle} (Ctrl+Shift+R)`}
              onClick={() => {
                onCloseMobile();
                onOpenWeeklyReview();
              }}
            >
              <div className="bento-mini-top">
                <ListChecks
                  size={14}
                  style={{
                    color: stalledProjectsCount > 0 ? 'var(--things-blue)' : 'var(--text-secondary)',
                    flexShrink: 0
                  }}
                />
                <span
                  className="bento-mini-count"
                  style={stalledProjectsCount > 0 ? { color: 'var(--things-blue)' } : undefined}
                >
                  {stalledProjectsCount > 0 ? stalledProjectsCount : ''}
                </span>
              </div>
              <span
                className="bento-mini-label"
                style={t.sidebarReviewShort.length > 13 ? { fontSize: '11.1px' } : undefined}
              >
                {t.sidebarReviewShort}
              </span>
            </button>
          </div>

          {/* 3. Custom Perspectives Section */}
          {onCreatePerspective && (
            <div className="sidebar-perspectives-section" data-testid="sidebar-perspectives-section">
              <div className="sidebar-section-header">
                <span className="sidebar-section-title">{t.perspectivesSectionTitle}</span>
                <button
                  type="button"
                  className="sidebar-section-plus"
                  data-testid="sidebar-add-perspective-btn"
                  title={t.perspectiveNewTitle}
                  aria-label={t.perspectiveNewTitle}
                  onClick={() => {
                    onCloseMobile();
                    onCreatePerspective();
                  }}
                >
                  <Plus size={13.5} />
                </button>
              </div>

              {sortedPerspectives.map((persp) => {
                const isPerspActive =
                  activeNav.kind === 'perspective' && activeNav.perspectiveId === persp.id;
                const matchCount = perspectiveCounts?.get(persp.id) ?? 0;
                return (
                  <div key={persp.id} className="nav-row-wrap">
                    <button
                      type="button"
                      className={`nav-item ${isPerspActive ? 'active' : ''}`}
                      data-testid={`sidebar-perspective-${persp.id}`}
                      title={persp.title}
                      onClick={() =>
                        onSelectNav({ kind: 'perspective', perspectiveId: persp.id })
                      }
                    >
                      <SlidersHorizontal
                        size={14}
                        style={{
                          color: isPerspActive ? 'var(--things-blue)' : 'var(--text-secondary)',
                          flexShrink: 0
                        }}
                      />
                      <span className="nav-item-label">{persp.title}</span>
                      {matchCount > 0 && (
                        <span
                          style={{
                            fontSize: 11.5,
                            fontWeight: 600,
                            color: isPerspActive ? 'var(--things-blue)' : 'var(--text-tertiary)',
                            fontVariantNumeric: 'tabular-nums'
                          }}
                        >
                          {matchCount}
                        </span>
                      )}
                    </button>
                    {onEditPerspective && (
                      <button
                        type="button"
                        className="nav-inline-plus"
                        title={t.perspectiveEditTitle}
                        aria-label={t.perspectiveEditTitle}
                        onClick={() => {
                          onCloseMobile();
                          onEditPerspective(persp);
                        }}
                      >
                        <Edit3 size={12.5} />
                      </button>
                    )}
                  </div>
                );
              })}
            </div>
          )}

          {(activeAreas.length > 0 || unassignedProjects.length > 0) && (
            <div className="sidebar-divider" />
          )}

          {/* Areas -> Projects Collapsible Hierarchy with Linear Progress Bars */}
          {activeAreas.map((area) => {
            const isAreaActive = activeNav.kind === 'area' && activeNav.areaId === area.id;
            const areaProjects = activeProjects.filter((p) => p.areaId === area.id);
            const areaKey = `area-${area.id}`;
            const isCollapsed = Boolean(collapsedAreas[area.id]);

            return (
              <div key={area.id} style={{ marginBottom: 4 }}>
                <div className="nav-row-wrap">
                  <button
                    type="button"
                    className="area-chevron-btn"
                    aria-label={area.title}
                    onClick={() =>
                      setCollapsedAreas((prev) => ({
                        ...prev,
                        [area.id]: !prev[area.id]
                      }))
                    }
                  >
                    {isCollapsed ? <ChevronRight size={13.5} /> : <ChevronDown size={13.5} />}
                  </button>
                  <button
                    type="button"
                    className={`nav-item area-row ${isAreaActive ? 'active' : ''} ${
                      dragTargetKey === areaKey ? 'drag-over' : ''
                    }`}
                    title={`${t.entityArea}: ${area.title}`}
                    onClick={() => onSelectNav({ kind: 'area', areaId: area.id })}
                    {...makeDropHandlers(areaKey, { kind: 'area', areaId: area.id })}
                  >
                    <span className="nav-item-icon">
                      <Folder
                        size={15.5}
                        style={{
                          color: isAreaActive ? 'var(--things-blue)' : 'var(--text-secondary)'
                        }}
                      />
                    </span>
                    <span className="nav-item-label">{area.title}</span>
                  </button>
                  <button
                    type="button"
                    className="nav-inline-plus"
                    title={`${t.entityNewProject} (${area.title})`}
                    onClick={() => onCreateProjectInArea(area.id)}
                  >
                    <Plus size={14} />
                  </button>
                </div>

                {!isCollapsed &&
                  areaProjects.map((proj) => {
                    const isProjActive =
                      activeNav.kind === 'project' && activeNav.projectId === proj.id;
                    const prog = projectProgress(proj.id);
                    const projKey = `proj-${proj.id}`;
                    return (
                      <button
                        key={proj.id}
                        type="button"
                        className={`nav-item indented project-nav-item ${
                          isProjActive ? 'active' : ''
                        } ${dragTargetKey === projKey ? 'drag-over' : ''}`}
                        title={proj.title}
                        onClick={() => onSelectNav({ kind: 'project', projectId: proj.id })}
                        {...makeDropHandlers(projKey, { kind: 'project', projectId: proj.id })}
                      >
                        <FolderKanban
                          size={13.5}
                          style={{
                            color: isProjActive ? 'var(--things-blue)' : 'var(--text-muted)',
                            flexShrink: 0
                          }}
                        />
                        <span className="nav-item-label">{proj.title}</span>
                        {proj.deadline != null && (
                          <Flag
                            size={11.5}
                            style={{ color: 'var(--deadline-red)', flexShrink: 0 }}
                          />
                        )}
                        <ProjectProgressRing
                          completed={prog.done}
                          total={prog.total}
                          size={15}
                          showCount={prog.total > 0}
                        />
                      </button>
                    );
                  })}
              </div>
            );
          })}

          {/* Legacy / Unassigned Projects */}
          {unassignedProjects.length > 0 && (
            <div style={{ marginTop: activeAreas.length > 0 ? 6 : 0 }}>
              {unassignedProjects.map((proj) => {
                const isProjActive =
                  activeNav.kind === 'project' && activeNav.projectId === proj.id;
                const prog = projectProgress(proj.id);
                const projKey = `proj-${proj.id}`;
                return (
                  <button
                    key={proj.id}
                    type="button"
                    className={`nav-item project-nav-item ${isProjActive ? 'active' : ''} ${
                      dragTargetKey === projKey ? 'drag-over' : ''
                    }`}
                    title={proj.title}
                    onClick={() => onSelectNav({ kind: 'project', projectId: proj.id })}
                    {...makeDropHandlers(projKey, { kind: 'project', projectId: proj.id })}
                  >
                    <FolderKanban
                      size={14}
                      style={{
                        color: isProjActive ? 'var(--things-blue)' : 'var(--text-muted)',
                        flexShrink: 0
                      }}
                    />
                    <span className="nav-item-label">{proj.title}</span>
                    {proj.deadline != null && (
                      <Flag
                        size={11.5}
                        style={{ color: 'var(--deadline-red)', flexShrink: 0 }}
                      />
                    )}
                    <ProjectProgressRing
                      completed={prog.done}
                      total={prog.total}
                      size={15}
                      showCount={prog.total > 0}
                    />
                  </button>
                );
              })}
            </div>
          )}
        </div>

        {/* ChromeOS Launcher Install Banner */}
        {!isStandalonePwa && (
          <button
            type="button"
            className="sidebar-install-card"
            onClick={onTriggerInstallPwa}
            title={t.installChromeOsHint}
          >
            <Download size={15} style={{ flexShrink: 0 }} />
            <span>{canInstallPwa ? t.installChromeOsBtn : t.installChromeOsBtn}</span>
          </button>
        )}

        {/* Option A Quiet Bottom Footer Dock: "+ New Project ▾" (with Templates) + Logbook / Trash / Areas */}
        <div className="sidebar-footer">
          <div className="sidebar-new-project-anchor">
            <button
              type="button"
              className="sidebar-footer-btn"
              title={`${t.entityNewProject} (Ctrl+Shift+N)`}
              onClick={() => setNewProjectMenuOpen((v) => !v)}
            >
              <Plus size={14} style={{ color: 'var(--things-blue)', flexShrink: 0 }} />
              <span
                style={{
                  color: 'var(--things-blue)',
                  fontWeight: 600,
                  overflow: 'hidden',
                  textOverflow: 'ellipsis',
                  whiteSpace: 'nowrap'
                }}
              >
                {t.entityNewProject}
              </span>
              <ChevronDown
                size={12}
                style={{ color: 'var(--things-blue)', opacity: 0.78, flexShrink: 0 }}
              />
            </button>

            {newProjectMenuOpen && (
              <>
                <div
                  style={{ position: 'fixed', inset: 0, zIndex: 80 }}
                  onClick={() => setNewProjectMenuOpen(false)}
                />
                <div className="sidebar-new-project-menu">
                  <button
                    type="button"
                    className="dropdown-item"
                    onClick={() => {
                      setNewProjectMenuOpen(false);
                      onCloseMobile();
                      onCreateProjectInArea(resolveCurrentAreaId());
                    }}
                  >
                    <span style={{ display: 'inline-flex', alignItems: 'center', gap: 8 }}>
                      <Plus size={14} style={{ color: 'var(--things-blue)' }} />
                      <span>{t.entityNewProject}</span>
                    </span>
                  </button>
                  <button
                    type="button"
                    className="dropdown-item"
                    data-testid="sidebar-templates-btn"
                    onClick={() => {
                      setNewProjectMenuOpen(false);
                      onCloseMobile();
                      onOpenTemplates(resolveCurrentAreaId());
                    }}
                  >
                    <span style={{ display: 'inline-flex', alignItems: 'center', gap: 8 }}>
                      <ClipboardList size={14} style={{ color: 'var(--things-blue)' }} />
                      <span>{t.templatesBtn}</span>
                    </span>
                  </button>
                </div>
              </>
            )}
          </div>

          <div className="sidebar-footer-utilities">
            <button
              type="button"
              className={`sidebar-footer-icon-btn ${isLogbookActive ? 'active' : ''} ${
                dragTargetKey === 'smart-LOGBOOK' ? 'drag-over' : ''
              }`}
              title={`${t.viewLogbook} (Ctrl+6)`}
              aria-label={t.viewLogbook}
              onClick={() => onSelectNav({ kind: 'smart', view: 'LOGBOOK' })}
              {...makeDropHandlers('smart-LOGBOOK', { kind: 'smart', view: 'LOGBOOK' })}
            >
              <History size={14.5} />
            </button>

            <button
              type="button"
              className={`sidebar-footer-icon-btn ${isTrashActive ? 'active' : ''} ${
                dragTargetKey === 'smart-TRASH' ? 'drag-over' : ''
              }`}
              title={`${t.viewTrash}${counts.trash ? ` (${counts.trash})` : ''} (Ctrl+7)`}
              aria-label={t.viewTrash}
              onClick={() => onSelectNav({ kind: 'smart', view: 'TRASH' })}
              {...makeDropHandlers('smart-TRASH', { kind: 'smart', view: 'TRASH' })}
            >
              <Trash2 size={14.5} />
            </button>

            <button
              type="button"
              className="sidebar-footer-icon-btn"
              data-testid="sidebar-manage-areas-btn"
              title={`${t.areasManageTitle} (${activeAreas.length}/${MAX_AREAS})`}
              aria-label={t.areasManageTitle}
              onClick={() => {
                onCloseMobile();
                onOpenManageAreas();
              }}
            >
              <Folder size={13.5} />
              <span>
                {activeAreas.length}/{MAX_AREAS}
              </span>
            </button>

            {onOpenManageTags && (
              <button
                type="button"
                className="sidebar-footer-icon-btn"
                data-testid="sidebar-manage-categories-btn"
                title={t.tagsManageTitle}
                aria-label={t.tagsManageTitle}
                onClick={() => {
                  onCloseMobile();
                  onOpenManageTags();
                }}
              >
                <LayoutGrid size={13.5} />
              </button>
            )}
          </div>
        </div>

        <div className="sidebar-version-row">
          <span>Thingsbook v{APP_VERSION}</span>
          {isBetaBuild && <span className="v2-beta-pill">{t.betaChannelBadge}</span>}
        </div>
      </aside>
    </>
  );
};

interface SmartViewNavigationStripProps {
  activeNav: ActiveNavigation;
  areas: AreaRecord[];
  projects: ProjectRecord[];
  tasks: TaskRecord[];
  selectedTaskIds?: string[];
  orderedVisibleTaskIds?: string[];
  counts: {
    inbox: number;
    today: number;
    upcoming: number;
    anytime: number;
    someday: number;
    trash?: number;
  };
  t: TranslationStrings;
  isSoftKeyboardOpen?: boolean;
  onSelectNav: (nav: ActiveNavigation) => void;
  onDropTasksOnTarget?: (taskIds: string[], target: SidebarDropTarget) => void;
  onCreateProject: () => void;
  onOpenTemplates: () => void;
  onOpenWeeklyReview: () => void;
  onOpenManageAreas: () => void;
  onOpenManageTags?: () => void;
}

export const SmartViewNavigationStrip: React.FC<SmartViewNavigationStripProps> = ({
  activeNav,
  areas,
  projects,
  tasks,
  selectedTaskIds = [],
  orderedVisibleTaskIds = [],
  counts,
  t,
  isSoftKeyboardOpen = false,
  onSelectNav,
  onDropTasksOnTarget,
  onCreateProject,
  onOpenTemplates,
  onOpenWeeklyReview,
  onOpenManageAreas,
  onOpenManageTags
}) => {
  const [dragStripKey, setDragStripKey] = useState<string | null>(null);
  const activeAreas = areas.filter((a) => !a.isDeleted);
  const activeProjects = projects.filter((p) => !p.isDeleted && p.status === 'ACTIVE');
  const unassignedProjects = activeProjects.filter(
    (p) => !p.areaId || !activeAreas.some((a) => a.id === p.areaId)
  );

  const projectProgress = (projectId: string) => {
    const projTasks = tasks.filter(
      (tk) => !tk.isDeleted && tk.projectId === projectId && tk.status !== 'CANCELED'
    );
    const done = projTasks.filter((tk) => tk.status === 'COMPLETED').length;
    return { done, total: projTasks.length };
  };

  const makeStripDropHandlers = (key: string, target: SidebarDropTarget) => ({
    onDragOver: (e: React.DragEvent) => {
      if (hasDraggedTaskMime(e.dataTransfer)) {
        e.preventDefault();
        e.dataTransfer.dropEffect = 'move';
        if (dragStripKey !== key) setDragStripKey(key);
      }
    },
    onDragLeave: () => {
      if (dragStripKey === key) setDragStripKey(null);
    },
    onDrop: (e: React.DragEvent) => {
      e.preventDefault();
      setDragStripKey(null);
      const ids = extractDraggedTaskIds(
        e.dataTransfer,
        selectedTaskIds,
        orderedVisibleTaskIds
      );
      if (ids.length > 0 && onDropTasksOnTarget) {
        onDropTasksOnTarget(ids, target);
      }
    }
  });

  const smartViews: Array<{
    view: SmartViewType;
    label: string;
    count?: number;
  }> = [
    { view: 'INBOX', label: t.viewInbox, count: counts.inbox },
    { view: 'TODAY', label: t.viewToday, count: counts.today },
    { view: 'UPCOMING', label: t.viewUpcoming, count: counts.upcoming },
    { view: 'ANYTIME', label: t.viewAnytime, count: counts.anytime },
    { view: 'SOMEDAY', label: t.viewSomeday, count: counts.someday },
    { view: 'LOGBOOK', label: t.viewLogbook },
    { view: 'TRASH', label: t.viewTrash, count: counts.trash }
  ];

  return (
    <>
      <div className="smart-nav-strip" role="navigation" aria-label="Smart View Strip">
        {smartViews.map((sv, idx) => {
          const isActive = activeNav.kind === 'smart' && activeNav.view === sv.view;
          const dKey = `strip-smart-${sv.view}`;
          return (
            <button
              key={sv.view}
              type="button"
              className={`strip-pill ${isActive ? 'active' : ''} ${
                dragStripKey === dKey ? 'drag-over' : ''
              }`}
              title={`${sv.label} (Ctrl+${idx + 1})`}
              onClick={() => onSelectNav({ kind: 'smart', view: sv.view })}
              {...makeStripDropHandlers(dKey, { kind: 'smart', view: sv.view })}
            >
              {renderMonochromeSmartIcon(sv.view, 13.5, isActive)}
              <span>{sv.label}</span>
              {sv.count != null && sv.count > 0 && (
                <span style={{ opacity: 0.78, fontSize: 11.5 }}>{sv.count}</span>
              )}
            </button>
          );
        })}

        <span className="strip-divider" />
        <button
          type="button"
          className="strip-pill"
          title={`${t.weeklyReviewTitle} (Ctrl+Shift+R)`}
          onClick={onOpenWeeklyReview}
        >
          <ListChecks size={13.5} style={{ color: 'var(--text-secondary)' }} />
          <span>{t.weeklyReviewBtn}</span>
        </button>
        <button
          type="button"
          className="strip-pill"
          title={t.templatesModalTitle}
          onClick={onOpenTemplates}
        >
          <ClipboardList size={13.5} style={{ color: 'var(--text-secondary)' }} />
          <span>{t.templatesBtn}</span>
        </button>

        {activeAreas.length > 0 && <span className="strip-divider" />}
        {activeAreas.map((area) => {
          const isActive = activeNav.kind === 'area' && activeNav.areaId === area.id;
          const areaProjects = activeProjects.filter((p) => p.areaId === area.id);
          const areaKey = `strip-area-${area.id}`;
          return (
            <React.Fragment key={area.id}>
              <button
                type="button"
                className={`strip-pill ${isActive ? 'active' : ''} ${
                  dragStripKey === areaKey ? 'drag-over' : ''
                }`}
                title={`${t.entityArea}: ${area.title}`}
                onClick={() => onSelectNav({ kind: 'area', areaId: area.id })}
                {...makeStripDropHandlers(areaKey, { kind: 'area', areaId: area.id })}
              >
                <Folder
                  size={13}
                  style={{ color: isActive ? 'var(--things-blue)' : 'var(--text-secondary)' }}
                />
                <span>{area.title}</span>
              </button>
              {areaProjects.map((proj) => {
                const isProjActive =
                  activeNav.kind === 'project' && activeNav.projectId === proj.id;
                const prog = projectProgress(proj.id);
                const projKey = `strip-proj-${proj.id}`;
                return (
                  <button
                    key={proj.id}
                    type="button"
                    className={`strip-pill ${isProjActive ? 'active' : ''} ${
                      dragStripKey === projKey ? 'drag-over' : ''
                    }`}
                    title={proj.title}
                    onClick={() => onSelectNav({ kind: 'project', projectId: proj.id })}
                    {...makeStripDropHandlers(projKey, {
                      kind: 'project',
                      projectId: proj.id
                    })}
                  >
                    <span>{proj.title}</span>
                    <ProjectProgressRing completed={prog.done} total={prog.total} size={13} />
                    {proj.deadline != null && (
                      <Flag size={11.5} style={{ color: 'var(--deadline-red)' }} />
                    )}
                  </button>
                );
              })}
            </React.Fragment>
          );
        })}

        {unassignedProjects.length > 0 && <span className="strip-divider" />}
        {unassignedProjects.map((proj) => {
          const isActive = activeNav.kind === 'project' && activeNav.projectId === proj.id;
          const prog = projectProgress(proj.id);
          const projKey = `strip-proj-${proj.id}`;
          return (
            <button
              key={proj.id}
              type="button"
              className={`strip-pill ${isActive ? 'active' : ''} ${
                dragStripKey === projKey ? 'drag-over' : ''
              }`}
              title={proj.title}
              onClick={() => onSelectNav({ kind: 'project', projectId: proj.id })}
              {...makeStripDropHandlers(projKey, { kind: 'project', projectId: proj.id })}
            >
              <span>{proj.title}</span>
              <ProjectProgressRing completed={prog.done} total={prog.total} size={13} />
              {proj.deadline != null && (
                <Flag size={11.5} style={{ color: 'var(--deadline-red)' }} />
              )}
            </button>
          );
        })}

        <span className="strip-divider" />
        <button
          type="button"
          className="strip-pill"
          title={`${t.entityNewProject} (Ctrl+Shift+N)`}
          onClick={onCreateProject}
        >
          <Plus size={13} style={{ color: 'var(--things-blue)' }} />
          <span>{t.entityProject}</span>
        </button>
        <button
          type="button"
          className="strip-pill"
          title={t.areasManageTitle}
          onClick={onOpenManageAreas}
        >
          <Folder size={13} />
          <span>
            {t.entityAreas} ({activeAreas.length}/{MAX_AREAS})
          </span>
        </button>
        {onOpenManageTags && (
          <button
            type="button"
            className="strip-pill"
            title={t.tagsManageTitle}
            onClick={onOpenManageTags}
          >
            <LayoutGrid size={13} />
            <span>{t.entityTags}</span>
          </button>
        )}
      </div>
      {!isSoftKeyboardOpen && (
        <div className="floating-version-pill">Thingsbook v{APP_VERSION}</div>
      )}
    </>
  );
};
