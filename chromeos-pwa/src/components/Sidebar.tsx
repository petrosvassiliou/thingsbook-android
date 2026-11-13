import React, { useState } from 'react';
import {
  Archive,
  BookOpenCheck,
  CalendarDays,
  CheckSquare,
  Download,
  Flag,
  Inbox,
  Layers,
  Package,
  Plus,
  Star,
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
  };
  canInstallPwa: boolean;
  isStandalonePwa: boolean;
  t: TranslationStrings;
  onSelectNav: (nav: ActiveNavigation) => void;
  onCloseMobile: () => void;
  onCreateProjectInArea: (areaId: string | null) => void;
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

export const Sidebar: React.FC<SidebarProps> = ({
  visible,
  activeNav,
  areas,
  projects,
  tasks,
  counts,
  canInstallPwa,
  isStandalonePwa,
  t,
  onSelectNav,
  onCloseMobile,
  onCreateProjectInArea,
  onOpenManageAreas,
  onDropTaskOnTarget,
  onTriggerInstallPwa
}) => {
  const [dragTargetKey, setDragTargetKey] = useState<string | null>(null);

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
    icon: React.ReactNode;
    count?: number;
  }> = [
    {
      view: 'INBOX',
      label: t.viewInbox,
      icon: <Inbox size={17} style={{ color: 'var(--things-blue)' }} />,
      count: counts.inbox
    },
    {
      view: 'TODAY',
      label: t.viewToday,
      icon: <Star size={17} fill="var(--today-star)" style={{ color: 'var(--today-star)' }} />,
      count: counts.today
    },
    {
      view: 'UPCOMING',
      label: t.viewUpcoming,
      icon: <CalendarDays size={17} style={{ color: 'var(--upcoming-coral)' }} />,
      count: counts.upcoming
    },
    {
      view: 'ANYTIME',
      label: t.viewAnytime,
      icon: <Layers size={17} style={{ color: 'var(--anytime-teal)' }} />,
      count: counts.anytime
    },
    {
      view: 'SOMEDAY',
      label: t.viewSomeday,
      icon: <Archive size={17} style={{ color: 'var(--someday-amber)' }} />,
      count: counts.someday
    },
    {
      view: 'LOGBOOK',
      label: t.viewLogbook,
      icon: <BookOpenCheck size={17} style={{ color: 'var(--logbook-green)' }} />
      // Never show count badge on Logbook (exact v1.6.16 rule)
    }
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
              <CheckSquare size={14} strokeWidth={2.6} />
            </span>
            <span className="brand-name">{t.appName}</span>
          </button>
          <button
            type="button"
            className="icon-btn sidebar-close-mobile"
            onClick={onCloseMobile}
            aria-label={t.actionDone}
          >
            <X size={17} />
          </button>
        </div>

        <div className="sidebar-scroll">
          {smartItems.slice(0, 5).map((item) => {
            const isActive = activeNav.kind === 'smart' && activeNav.view === item.view;
            const dKey = `smart-${item.view}`;
            return (
              <button
                key={item.view}
                type="button"
                className={`nav-item ${isActive ? 'active' : ''} ${
                  dragTargetKey === dKey ? 'drag-over' : ''
                }`}
                onClick={() => onSelectNav({ kind: 'smart', view: item.view })}
                {...makeDropHandlers(dKey, { kind: 'smart', view: item.view })}
              >
                <span className="nav-item-icon">{item.icon}</span>
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
                onClick={() => onSelectNav({ kind: 'smart', view: 'LOGBOOK' })}
              >
                <span className="nav-item-icon">{logItem.icon}</span>
                <span className="nav-item-label">{logItem.label}</span>
              </button>
            );
          })()}

          {(activeAreas.length > 0 || unassignedProjects.length > 0) && (
            <div className="sidebar-divider" />
          )}

          {/* Areas + Indented Projects */}
          {activeAreas.map((area) => {
            const isAreaActive = activeNav.kind === 'area' && activeNav.areaId === area.id;
            const areaProjects = activeProjects.filter((p) => p.areaId === area.id);
            const areaKey = `area-${area.id}`;

            return (
              <div key={area.id} style={{ marginBottom: 4 }}>
                <div className="nav-row-wrap">
                  <button
                    type="button"
                    className={`nav-item area-row ${isAreaActive ? 'active' : ''} ${
                      dragTargetKey === areaKey ? 'drag-over' : ''
                    }`}
                    onClick={() => onSelectNav({ kind: 'area', areaId: area.id })}
                    {...makeDropHandlers(areaKey, { kind: 'area', areaId: area.id })}
                  >
                    <span className="nav-item-icon">
                      <Package size={15.5} style={{ color: 'var(--anytime-teal)' }} />
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

                {areaProjects.map((proj) => {
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

          {/* Unassigned Projects */}
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

        {/* Sidebar Footer: + New Project (left) & Areas (X/8) (right) */}
        <div className="sidebar-footer">
          <button
            type="button"
            className="sidebar-footer-btn"
            title={`${t.entityNewProject} (Ctrl+Shift+N)`}
            onClick={() => {
              const preselectedAreaId =
                activeNav.kind === 'area'
                  ? activeNav.areaId
                  : activeNav.kind === 'project'
                    ? (projects.find((p) => p.id === activeNav.projectId)?.areaId ?? null)
                    : null;
              onCreateProjectInArea(preselectedAreaId);
            }}
          >
            <Plus size={14} />
            <span>{t.entityNewProject}</span>
          </button>
          <button type="button" className="sidebar-footer-btn" onClick={onOpenManageAreas}>
            <Package size={13.5} />
            <span>
              {t.entityAreas} ({activeAreas.length}/{MAX_AREAS})
            </span>
          </button>
        </div>

        <div className="sidebar-version-row">Thingsbook v{APP_VERSION}</div>
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
  };
  t: TranslationStrings;
  isSoftKeyboardOpen?: boolean;
  onSelectNav: (nav: ActiveNavigation) => void;
  onCreateProject: () => void;
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
  onOpenManageAreas
}) => {
  const activeAreas = areas.filter((a) => !a.isDeleted);
  const activeProjects = projects.filter((p) => !p.isDeleted && p.status === 'ACTIVE');

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
    icon: React.ReactNode;
    count?: number;
  }> = [
    {
      view: 'INBOX',
      label: t.viewInbox,
      icon: <Inbox size={13.5} style={{ color: 'var(--things-blue)' }} />,
      count: counts.inbox
    },
    {
      view: 'TODAY',
      label: t.viewToday,
      icon: <Star size={13.5} fill="var(--today-star)" style={{ color: 'var(--today-star)' }} />,
      count: counts.today
    },
    {
      view: 'UPCOMING',
      label: t.viewUpcoming,
      icon: <CalendarDays size={13.5} style={{ color: 'var(--upcoming-coral)' }} />,
      count: counts.upcoming
    },
    {
      view: 'ANYTIME',
      label: t.viewAnytime,
      icon: <Layers size={13.5} style={{ color: 'var(--anytime-teal)' }} />,
      count: counts.anytime
    },
    {
      view: 'SOMEDAY',
      label: t.viewSomeday,
      icon: <Archive size={13.5} style={{ color: 'var(--someday-amber)' }} />,
      count: counts.someday
    },
    {
      view: 'LOGBOOK',
      label: t.viewLogbook,
      icon: <BookOpenCheck size={13.5} style={{ color: 'var(--logbook-green)' }} />
    }
  ];

  return (
    <>
      <div className="smart-nav-strip" role="navigation" aria-label="Smart View Strip">
        {smartViews.map((sv) => {
          const isActive = activeNav.kind === 'smart' && activeNav.view === sv.view;
          return (
            <button
              key={sv.view}
              type="button"
              className={`strip-pill ${isActive ? 'active' : ''}`}
              onClick={() => onSelectNav({ kind: 'smart', view: sv.view })}
            >
              {sv.icon}
              <span>{sv.label}</span>
              {sv.count != null && sv.count > 0 && (
                <span style={{ opacity: 0.78, fontSize: 11.5 }}>{sv.count}</span>
              )}
            </button>
          );
        })}

        {activeAreas.length > 0 && <span className="strip-divider" />}
        {activeAreas.map((area) => {
          const isActive = activeNav.kind === 'area' && activeNav.areaId === area.id;
          return (
            <button
              key={area.id}
              type="button"
              className={`strip-pill ${isActive ? 'active' : ''}`}
              onClick={() => onSelectNav({ kind: 'area', areaId: area.id })}
            >
              <Package size={13} style={{ color: 'var(--anytime-teal)' }} />
              <span>{area.title}</span>
            </button>
          );
        })}

        {activeProjects.length > 0 && <span className="strip-divider" />}
        {activeProjects.map((proj) => {
          const isActive = activeNav.kind === 'project' && activeNav.projectId === proj.id;
          const prog = projectProgress(proj.id);
          return (
            <button
              key={proj.id}
              type="button"
              className={`strip-pill ${isActive ? 'active' : ''}`}
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
        <button type="button" className="strip-pill" onClick={onCreateProject}>
          <Plus size={13} />
          <span>{t.entityProject}</span>
        </button>
        <button type="button" className="strip-pill" onClick={onOpenManageAreas}>
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
