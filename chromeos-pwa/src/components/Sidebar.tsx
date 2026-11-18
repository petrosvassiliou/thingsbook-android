import React, { useState } from 'react';
import {
  Archive,
  BookOpen,
  BookOpenCheck,
  CalendarDays,
  ChevronDown,
  ChevronRight,
  ClipboardList,
  Download,
  Flag,
  Inbox,
  Layers,
  ListChecks,
  Package,
  Plus,
  Star,
  Trash2,
  X
} from 'lucide-react';
import {
  APP_VERSION,
  MAX_AREAS,
  type ActiveNavigation,
  type AreaRecord,
  type ProjectRecord,
  type SmartViewType,
  type TaskRecord
} from '../domain/types';
import { isPrereleaseVersion } from '../data/versionUtils';
import type { TranslationStrings } from '../i18n/translations';
import { ProjectProgressRing } from './ProjectProgressRing';

interface SidebarProps {
  visible: boolean;
  activeNav: ActiveNavigation;
  areas: AreaRecord[];
  projects: ProjectRecord[];
  tasks: TaskRecord[];
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
  onDropTaskOnTarget: (
    taskId: string,
    target:
      | { kind: 'smart'; view: SmartViewType }
      | { kind: 'area'; areaId: string }
      | { kind: 'project'; projectId: string }
  ) => void;
  onTriggerInstallPwa: () => void;
}

function renderMonochromeSmartIcon(view: SmartViewType, size: number, isActive: boolean): React.ReactNode {
  const color = isActive ? 'var(--things-blue)' : 'var(--text-secondary)';
  switch (view) {
    case 'INBOX':
      return <Inbox size={size} style={{ color }} />;
    case 'TODAY':
      return (
        <Star
          size={size}
          fill={isActive ? 'var(--things-blue)' : 'none'}
          style={{ color }}
        />
      );
    case 'UPCOMING':
      return <CalendarDays size={size} style={{ color }} />;
    case 'ANYTIME':
      return <Layers size={size} style={{ color }} />;
    case 'SOMEDAY':
      return <Archive size={size} style={{ color }} />;
    case 'LOGBOOK':
      return <BookOpenCheck size={size} style={{ color }} />;
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
  onDropTaskOnTarget,
  onTriggerInstallPwa
}) => {
  const [dragTargetKey, setDragTargetKey] = useState<string | null>(null);
  const [collapsedAreas, setCollapsedAreas] = useState<Record<string, boolean>>({});

  const activeAreas = areas.filter((a) => !a.isDeleted);
  const activeProjects = projects.filter((p) => !p.isDeleted && p.status === 'ACTIVE');
  const unassignedProjects = activeProjects.filter(
    (p) => !p.areaId || !activeAreas.some((a) => a.id === p.areaId)
  );
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

  const makeDropHandlers = (
    key: string,
    target:
      | { kind: 'smart'; view: SmartViewType }
      | { kind: 'area'; areaId: string }
      | { kind: 'project'; projectId: string }
  ) => ({
    onDragOver: (e: React.DragEvent) => {
      if (e.dataTransfer.types.includes('application/x-thingsbook-task')) {
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
      const taskId = e.dataTransfer.getData('application/x-thingsbook-task');
      if (taskId) {
        onDropTaskOnTarget(taskId, target);
      }
    }
  });

  const smartItems: Array<{
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
          {smartItems.slice(0, 5).map((item, idx) => {
            const isActive = activeNav.kind === 'smart' && activeNav.view === item.view;
            const dKey = `smart-${item.view}`;
            return (
              <button
                key={item.view}
                type="button"
                className={`nav-item ${isActive ? 'active' : ''} ${
                  dragTargetKey === dKey ? 'drag-over' : ''
                }`}
                title={`${item.label} (Ctrl+${idx + 1})`}
                onClick={() => onSelectNav({ kind: 'smart', view: item.view })}
                {...makeDropHandlers(dKey, { kind: 'smart', view: item.view })}
              >
                <span className="nav-item-icon">
                  {renderMonochromeSmartIcon(item.view, 17, isActive)}
                </span>
                <span className="nav-item-label">{item.label}</span>
                {item.count != null && item.count > 0 && (
                  <span className="nav-item-count">{item.count}</span>
                )}
              </button>
            );
          })}

          <div style={{ height: 6 }} />
          {(() => {
            const logItem = smartItems[5];
            const isActive = activeNav.kind === 'smart' && activeNav.view === 'LOGBOOK';
            return (
              <button
                type="button"
                className={`nav-item ${isActive ? 'active' : ''}`}
                title={`${logItem.label} (Ctrl+6)`}
                onClick={() => onSelectNav({ kind: 'smart', view: 'LOGBOOK' })}
              >
                <span className="nav-item-icon">
                  {renderMonochromeSmartIcon('LOGBOOK', 17, isActive)}
                </span>
                <span className="nav-item-label">{logItem.label}</span>
              </button>
            );
          })()}
          {(() => {
            const trashItem = smartItems[6];
            const isActive = activeNav.kind === 'smart' && activeNav.view === 'TRASH';
            const dKey = 'smart-TRASH';
            return (
              <button
                type="button"
                className={`nav-item ${isActive ? 'active' : ''} ${
                  dragTargetKey === dKey ? 'drag-over' : ''
                }`}
                title={`${trashItem.label} (Ctrl+7)`}
                onClick={() => onSelectNav({ kind: 'smart', view: 'TRASH' })}
                {...makeDropHandlers(dKey, { kind: 'smart', view: 'TRASH' })}
              >
                <span className="nav-item-icon">
                  {renderMonochromeSmartIcon('TRASH', 17, isActive)}
                </span>
                <span className="nav-item-label">{trashItem.label}</span>
                {trashItem.count != null && trashItem.count > 0 && (
                  <span className="nav-item-count">{trashItem.count}</span>
                )}
              </button>
            );
          })()}

          {/* Guided GTD Weekly Review & Templates Launchers (monochrome M3 Expressive style) */}
          <div style={{ height: 6 }} />
          <button
            type="button"
            className="nav-item"
            data-testid="sidebar-weekly-review-btn"
            title={`${t.weeklyReviewTitle} (Ctrl+Shift+R)`}
            onClick={() => {
              onCloseMobile();
              onOpenWeeklyReview();
            }}
          >
            <span className="nav-item-icon">
              <ListChecks size={16.5} style={{ color: 'var(--text-secondary)' }} />
            </span>
            <span className="nav-item-label">{t.weeklyReviewBtn}</span>
            {stalledProjectsCount > 0 && (
              <span className="nav-item-count">{stalledProjectsCount}</span>
            )}
          </button>

          <button
            type="button"
            className="nav-item"
            data-testid="sidebar-templates-btn"
            title={t.templatesModalTitle}
            onClick={() => {
              onCloseMobile();
              onOpenTemplates(resolveCurrentAreaId());
            }}
          >
            <span className="nav-item-icon">
              <ClipboardList size={16.5} style={{ color: 'var(--text-secondary)' }} />
            </span>
            <span className="nav-item-label">{t.templatesBtn}</span>
          </button>

          {(activeAreas.length > 0 || unassignedProjects.length > 0) && (
            <div className="sidebar-divider" />
          )}

          {/* Areas -> Projects Collapsible Hierarchy */}
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
                      <Package
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
                        className={`nav-item indented ${isProjActive ? 'active' : ''} ${
                          dragTargetKey === projKey ? 'drag-over' : ''
                        }`}
                        title={`${proj.title} (${prog.done}/${prog.total})`}
                        onClick={() => onSelectNav({ kind: 'project', projectId: proj.id })}
                        {...makeDropHandlers(projKey, { kind: 'project', projectId: proj.id })}
                      >
                        <ProjectProgressRing completed={prog.done} total={prog.total} size={15} />
                        <span className="nav-item-label">{proj.title}</span>
                        {proj.deadline != null && (
                          <Flag
                            size={12.5}
                            style={{ color: 'var(--deadline-red)', flexShrink: 0 }}
                          />
                        )}
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
                    className={`nav-item ${isProjActive ? 'active' : ''} ${
                      dragTargetKey === projKey ? 'drag-over' : ''
                    }`}
                    title={`${proj.title} (${prog.done}/${prog.total})`}
                    onClick={() => onSelectNav({ kind: 'project', projectId: proj.id })}
                    {...makeDropHandlers(projKey, { kind: 'project', projectId: proj.id })}
                  >
                    <ProjectProgressRing completed={prog.done} total={prog.total} size={15} />
                    <span className="nav-item-label">{proj.title}</span>
                    {proj.deadline != null && (
                      <Flag
                        size={12.5}
                        style={{ color: 'var(--deadline-red)', flexShrink: 0 }}
                      />
                    )}
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

        {/* Sidebar Footer: + New Project (defaults into active/first Area) & Areas (X/8) */}
        <div className="sidebar-footer">
          <button
            type="button"
            className="sidebar-footer-btn"
            title={`${t.entityNewProject} (Ctrl+Shift+N)`}
            onClick={() => onCreateProjectInArea(resolveCurrentAreaId())}
          >
            <Plus size={14} style={{ color: 'var(--things-blue)' }} />
            <span style={{ color: 'var(--things-blue)', fontWeight: 600 }}>
              {t.entityNewProject}
            </span>
          </button>
          <button
            type="button"
            className="sidebar-footer-btn"
            title={t.areasManageTitle}
            onClick={onOpenManageAreas}
          >
            <Package size={13.5} />
            <span>
              {t.entityAreas} ({activeAreas.length}/{MAX_AREAS})
            </span>
          </button>
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
  onCreateProject: () => void;
  onOpenTemplates: () => void;
  onOpenWeeklyReview: () => void;
  onOpenManageAreas: () => void;
}

export const SmartViewNavigationStrip: React.FC<SmartViewNavigationStripProps> = ({
  activeNav,
  areas,
  projects,
  tasks,
  counts,
  t,
  isSoftKeyboardOpen = false,
  onSelectNav,
  onCreateProject,
  onOpenTemplates,
  onOpenWeeklyReview,
  onOpenManageAreas
}) => {
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
          return (
            <button
              key={sv.view}
              type="button"
              className={`strip-pill ${isActive ? 'active' : ''}`}
              title={`${sv.label} (Ctrl+${idx + 1})`}
              onClick={() => onSelectNav({ kind: 'smart', view: sv.view })}
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

        {/* Areas with their nested Projects grouped immediately beside each Area */}
        {activeAreas.length > 0 && <span className="strip-divider" />}
        {activeAreas.map((area) => {
          const isActive = activeNav.kind === 'area' && activeNav.areaId === area.id;
          const areaProjects = activeProjects.filter((p) => p.areaId === area.id);
          return (
            <React.Fragment key={area.id}>
              <button
                type="button"
                className={`strip-pill ${isActive ? 'active' : ''}`}
                title={`${t.entityArea}: ${area.title}`}
                onClick={() => onSelectNav({ kind: 'area', areaId: area.id })}
              >
                <Package
                  size={13}
                  style={{ color: isActive ? 'var(--things-blue)' : 'var(--text-secondary)' }}
                />
                <span>{area.title}</span>
              </button>
              {areaProjects.map((proj) => {
                const isProjActive =
                  activeNav.kind === 'project' && activeNav.projectId === proj.id;
                const prog = projectProgress(proj.id);
                return (
                  <button
                    key={proj.id}
                    type="button"
                    className={`strip-pill ${isProjActive ? 'active' : ''}`}
                    title={`${area.title} › ${proj.title} (${prog.done}/${prog.total})`}
                    onClick={() => onSelectNav({ kind: 'project', projectId: proj.id })}
                  >
                    <ProjectProgressRing completed={prog.done} total={prog.total} size={14} />
                    <span>{proj.title}</span>
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
          return (
            <button
              key={proj.id}
              type="button"
              className={`strip-pill ${isActive ? 'active' : ''}`}
              title={`${proj.title} (${prog.done}/${prog.total})`}
              onClick={() => onSelectNav({ kind: 'project', projectId: proj.id })}
            >
              <ProjectProgressRing completed={prog.done} total={prog.total} size={14} />
              <span>{proj.title}</span>
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
          <Package size={13} />
          <span>
            {t.entityAreas} ({activeAreas.length}/{MAX_AREAS})
          </span>
        </button>
      </div>
      {!isSoftKeyboardOpen && (
        <div className="floating-version-pill">Thingsbook v{APP_VERSION}</div>
      )}
    </>
  );
};
