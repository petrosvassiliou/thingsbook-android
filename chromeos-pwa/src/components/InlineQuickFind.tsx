import React, { useMemo, useState } from 'react';
import {
  Archive,
  Bell,
  BookOpenCheck,
  CalendarDays,
  CheckSquare,
  EyeOff,
  Flag,
  Folder,
  Inbox,
  Layers,
  Moon,
  Package,
  Repeat,
  RotateCcw,
  Search,
  Star,
  Tag,
  Trash2,
  X
} from 'lucide-react';
import {
  daysBetweenToday,
  formatRecurrenceShort,
  type ActiveNavigation,
  type AreaRecord,
  type ChecklistItemRecord,
  type ProjectRecord,
  type SmartViewType,
  type SupportedLang,
  type TagRecord,
  type TaskRecord
} from '../domain/types';
import {
  DEFAULT_QUICK_FIND_FILTER,
  hasActiveQuickFindFilters,
  matchesQuickFindProject,
  matchesQuickFindTask,
  shouldShowSmartViewsAndAreasInQuickFind,
  type QuickFindDateFilter,
  type QuickFindEntityScope,
  type QuickFindFilter
} from '../domain/v2Utils';
import type { TranslationStrings } from '../i18n/translations';
import { ProjectProgressRing } from './ProjectProgressRing';

interface InlineQuickFindProps {
  areas: AreaRecord[];
  projects: ProjectRecord[];
  tasks: TaskRecord[];
  checklistItems: ChecklistItemRecord[];
  tags: TagRecord[];
  lang: SupportedLang;
  t: TranslationStrings;
  onNavigate: (nav: ActiveNavigation, highlightTaskId?: string) => void;
  onClose: () => void;
}

export const InlineQuickFind: React.FC<InlineQuickFindProps> = ({
  areas,
  projects,
  tasks,
  checklistItems,
  tags,
  lang,
  t,
  onNavigate,
  onClose
}) => {
  const [query, setQuery] = useState('');
  const [filter, setFilter] = useState<QuickFindFilter>(DEFAULT_QUICK_FIND_FILTER);
  const [referenceNowMs] = useState(() => Date.now());

  const activeTags = useMemo(() => tags.filter((tg) => !tg.isDeleted), [tags]);
  const areasMap = useMemo(
    () => new Map(areas.filter((a) => !a.isDeleted).map((a) => [a.id, a])),
    [areas]
  );
  const projectsMap = useMemo(
    () => new Map(projects.filter((p) => !p.isDeleted).map((p) => [p.id, p])),
    [projects]
  );
  const tagsMap = useMemo(
    () => new Map(activeTags.map((tg) => [tg.id, tg])),
    [activeTags]
  );
  const taskHasChecklistSet = useMemo(() => {
    const s = new Set<string>();
    for (const chk of checklistItems) {
      if (!chk.isDeleted) s.add(chk.taskId);
    }
    return s;
  }, [checklistItems]);

  const q = query.trim().toLowerCase();
  const hasFilters = hasActiveQuickFindFilters(filter);
  const showViewsAndAreas = shouldShowSmartViewsAndAreasInQuickFind(q, filter);

  const smartViews: Array<{ view: SmartViewType; label: string; icon: React.ReactNode }> = useMemo(
    () => [
      { view: 'INBOX', label: t.viewInbox, icon: <Inbox size={15} style={{ color: 'var(--things-blue)' }} /> },
      { view: 'TODAY', label: t.viewToday, icon: <Star size={15} fill="var(--today-star)" style={{ color: 'var(--today-star)' }} /> },
      { view: 'UPCOMING', label: t.viewUpcoming, icon: <CalendarDays size={15} style={{ color: 'var(--upcoming-coral)' }} /> },
      { view: 'ANYTIME', label: t.viewAnytime, icon: <Layers size={15} style={{ color: 'var(--anytime-teal)' }} /> },
      { view: 'SOMEDAY', label: t.viewSomeday, icon: <Archive size={15} style={{ color: 'var(--someday-amber)' }} /> },
      { view: 'LOGBOOK', label: t.viewLogbook, icon: <BookOpenCheck size={15} style={{ color: 'var(--logbook-green)' }} /> },
      { view: 'TRASH', label: t.viewTrash, icon: <Trash2 size={15} style={{ color: 'var(--text-muted)' }} /> }
    ],
    [t]
  );

  const matchedViews = useMemo(() => {
    if (!showViewsAndAreas) return [];
    if (!q) return smartViews;
    return smartViews.filter((sv) => sv.label.toLowerCase().includes(q));
  }, [q, smartViews, showViewsAndAreas]);

  const matchedAreas = useMemo(() => {
    if (!showViewsAndAreas) return [];
    const active = areas.filter((a) => !a.isDeleted);
    if (!q) return active;
    return active.filter((a) => a.title.toLowerCase().includes(q));
  }, [areas, q, showViewsAndAreas]);

  const matchedProjects = useMemo(() => {
    const filtered = projects.filter((p) => matchesQuickFindProject(p, filter, referenceNowMs));
    if (!q) return hasFilters ? filtered.slice(0, 25) : filtered.slice(0, 8);
    return filtered
      .filter(
        (p) =>
          p.title.toLowerCase().includes(q) ||
          p.notes.toLowerCase().includes(q) ||
          (p.areaId && (areasMap.get(p.areaId)?.title.toLowerCase().includes(q) ?? false))
      )
      .slice(0, 25);
  }, [projects, areasMap, q, filter, hasFilters, referenceNowMs]);

  const matchedTasks = useMemo(() => {
    const filtered = tasks.filter((tk) =>
      matchesQuickFindTask(tk, filter, taskHasChecklistSet.has(tk.id), referenceNowMs)
    );
    if (!q) {
      if (hasFilters) return filtered.slice(0, 50);
      return filtered.filter((tk) => tk.status === 'OPEN').slice(0, 12);
    }
    return filtered
      .filter((tk) => {
        const tagMatch = tk.tagIds.some((id) => tagsMap.get(id)?.title.toLowerCase().includes(q));
        const proj = tk.projectId ? projectsMap.get(tk.projectId) : undefined;
        const area =
          proj?.areaId != null
            ? areasMap.get(proj.areaId)
            : tk.areaId
              ? areasMap.get(tk.areaId)
              : undefined;
        const chkMatch = checklistItems.some(
          (c) => !c.isDeleted && c.taskId === tk.id && c.title.toLowerCase().includes(q)
        );
        return (
          tk.title.toLowerCase().includes(q) ||
          tk.notes.toLowerCase().includes(q) ||
          tagMatch ||
          chkMatch ||
          (proj?.title.toLowerCase().includes(q) ?? false) ||
          (area?.title.toLowerCase().includes(q) ?? false)
        );
      })
      .slice(0, 50);
  }, [tasks, q, filter, hasFilters, taskHasChecklistSet, tagsMap, projectsMap, areasMap, checklistItems, referenceNowMs]);

  const totalHits =
    matchedViews.length + matchedAreas.length + matchedProjects.length + matchedTasks.length;

  const resolveTaskDestination = (task: TaskRecord): ActiveNavigation => {
    if (task.projectId && projectsMap.has(task.projectId)) {
      return { kind: 'project', projectId: task.projectId };
    }
    if (task.status !== 'OPEN') {
      return { kind: 'smart', view: 'LOGBOOK' };
    }
    if (task.areaId && areasMap.has(task.areaId) && task.scheduleState === 'ANYTIME') {
      return { kind: 'area', areaId: task.areaId };
    }
    switch (task.scheduleState) {
      case 'INBOX':
        return { kind: 'smart', view: 'INBOX' };
      case 'TODAY':
      case 'THIS_EVENING':
        return { kind: 'smart', view: 'TODAY' };
      case 'UPCOMING':
        return { kind: 'smart', view: 'UPCOMING' };
      case 'SOMEDAY':
        return { kind: 'smart', view: 'SOMEDAY' };
      default:
        return task.areaId && areasMap.has(task.areaId)
          ? { kind: 'area', areaId: task.areaId }
          : { kind: 'smart', view: 'ANYTIME' };
    }
  };

  const formatShortDate = (ms: number) =>
    new Intl.DateTimeFormat(lang, { month: 'short', day: 'numeric' }).format(new Date(ms));

  const formatClock = (ms: number) => {
    const d = new Date(ms);
    return `${String(d.getHours()).padStart(2, '0')}:${String(d.getMinutes()).padStart(2, '0')}`;
  };

  const renderDeadlineBadge = (deadlineMs: number) => {
    const diff = daysBetweenToday(deadlineMs);
    const label =
      diff === 0
        ? t.deadlineToday
        : diff > 0
          ? t.deadlineDaysLeft(diff)
          : t.deadlineDaysOverdue(Math.abs(diff));
    const urgent = diff <= 2;
    return (
      <span className={`meta-badge ${urgent ? 'urgent' : ''}`}>
        <Flag size={10.5} />
        <span>{label}</span>
      </span>
    );
  };

  return (
    <section className="inline-quickfind" aria-label={t.quickFindTitle}>
      <div className="inline-qf-bar">
        <div className="inline-qf-input-wrap">
          <Search size={17} style={{ color: 'var(--things-blue)', flexShrink: 0 }} />
          <input
            type="text"
            className="inline-qf-input"
            autoFocus
            value={query}
            onChange={(e) => setQuery(e.target.value)}
            onKeyDown={(e) => {
              if (e.key === 'Escape') {
                e.stopPropagation();
                if (query) setQuery('');
                else if (hasFilters) setFilter(DEFAULT_QUICK_FIND_FILTER);
                else onClose();
              }
            }}
            placeholder={t.quickFindPlaceholder}
          />
          <span style={{ fontSize: 11.5, color: 'var(--text-muted)', whiteSpace: 'nowrap' }}>
            {t.quickFindHits(totalHits)}
          </span>
          {query && (
            <button
              type="button"
              className="icon-btn"
              style={{ height: 24, minWidth: 24, padding: 0 }}
              onClick={() => setQuery('')}
              title={t.actionClear}
            >
              <X size={14} />
            </button>
          )}
          <button
            type="button"
            className="btn-secondary"
            style={{ padding: '4px 9px', fontSize: 12 }}
            onClick={onClose}
          >
            {t.actionDone}
          </button>
        </div>

        {/* Search Filter Strip */}
        <div className="inline-qf-filters">
          <button
            type="button"
            className={`inline-qf-chip ${filter.hideCompleted ? 'active' : ''}`}
            onClick={() => setFilter((f) => ({ ...f, hideCompleted: !f.hideCompleted }))}
          >
            <EyeOff size={12.5} />
            <span>{t.searchFilterHideCompleted}</span>
          </button>

          <select
            className={`inline-qf-select ${filter.dateFilter !== 'ALL' ? 'active' : ''}`}
            value={filter.dateFilter}
            aria-label={t.searchFilterDateAll}
            onChange={(e) =>
              setFilter((f) => ({ ...f, dateFilter: e.target.value as QuickFindDateFilter }))
            }
          >
            <option value="ALL">📅 {t.searchFilterDateAll}</option>
            <option value="TODAY">⭐ {t.searchFilterDateToday}</option>
            <option value="OVERDUE">⚠️ {t.searchFilterDateOverdue}</option>
            <option value="NEXT_7_DAYS">🗓️ {t.searchFilterDateNext7Days}</option>
            <option value="THIS_MONTH">📆 {t.searchFilterDateThisMonth}</option>
            <option value="NO_DATE">⏳ {t.searchFilterDateNone}</option>
          </select>

          <button
            type="button"
            className={`inline-qf-chip ${filter.hasDeadlineOnly ? 'active' : ''}`}
            onClick={() => setFilter((f) => ({ ...f, hasDeadlineOnly: !f.hasDeadlineOnly }))}
          >
            <Flag size={12} />
            <span>{t.searchFilterHasDeadline}</span>
          </button>

          <button
            type="button"
            className={`inline-qf-chip ${filter.hasChecklistOnly ? 'active' : ''}`}
            onClick={() => setFilter((f) => ({ ...f, hasChecklistOnly: !f.hasChecklistOnly }))}
          >
            <CheckSquare size={12} />
            <span>{t.searchFilterHasChecklist}</span>
          </button>

          {activeTags.length > 0 && (
            <select
              className={`inline-qf-select ${filter.tagId !== null ? 'active' : ''}`}
              value={filter.tagId ?? ''}
              aria-label={t.searchFilterAllTags}
              onChange={(e) =>
                setFilter((f) => ({ ...f, tagId: e.target.value || null }))
              }
            >
              <option value="">🏷️ {t.searchFilterAllTags}</option>
              {activeTags.map((tg) => (
                <option key={tg.id} value={tg.id}>
                  #{tg.title}
                </option>
              ))}
            </select>
          )}

          <select
            className={`inline-qf-select ${filter.scope !== 'ALL' ? 'active' : ''}`}
            value={filter.scope}
            aria-label={t.searchFilterScopeAll}
            onChange={(e) =>
              setFilter((f) => ({ ...f, scope: e.target.value as QuickFindEntityScope }))
            }
          >
            <option value="ALL">{t.searchFilterScopeAll}</option>
            <option value="TASKS">{t.searchFilterScopeTasks}</option>
            <option value="PROJECTS">{t.searchFilterScopeProjects}</option>
          </select>

          {hasFilters && (
            <button
              type="button"
              className="inline-qf-chip reset"
              onClick={() => setFilter(DEFAULT_QUICK_FIND_FILTER)}
            >
              <RotateCcw size={11.5} />
              <span>{t.searchFilterReset}</span>
            </button>
          )}
        </div>

        <div className="inline-qf-results">
          {totalHits === 0 && (
            <div style={{ padding: '22px 12px', textAlign: 'center', color: 'var(--text-muted)' }}>
              {t.quickFindNoResults}
            </div>
          )}

          {/* 1. Smart Views */}
          {matchedViews.length > 0 && (
            <div>
              <div className="inline-qf-section-label">{t.quickFindSmartViews}</div>
              {matchedViews.map((sv) => (
                <button
                  key={sv.view}
                  type="button"
                  className="inline-qf-row"
                  onClick={() => {
                    onNavigate({ kind: 'smart', view: sv.view });
                    onClose();
                  }}
                >
                  <div className="inline-qf-row-top">
                    <span style={{ display: 'inline-flex', alignItems: 'center', gap: 8, fontWeight: 600 }}>
                      {sv.icon}
                      <span>{sv.label}</span>
                    </span>
                  </div>
                </button>
              ))}
            </div>
          )}

          {/* 2. Areas */}
          {matchedAreas.length > 0 && (
            <div>
              <div className="inline-qf-section-label">{t.entityAreas}</div>
              {matchedAreas.map((area) => (
                <button
                  key={area.id}
                  type="button"
                  className="inline-qf-row"
                  onClick={() => {
                    onNavigate({ kind: 'area', areaId: area.id });
                    onClose();
                  }}
                >
                  <div className="inline-qf-row-top">
                    <span style={{ display: 'inline-flex', alignItems: 'center', gap: 8, fontWeight: 600 }}>
                      <Package size={15} style={{ color: 'var(--anytime-teal)' }} />
                      <span>{area.title}</span>
                    </span>
                  </div>
                </button>
              ))}
            </div>
          )}

          {/* 3. Projects */}
          {matchedProjects.length > 0 && (
            <div>
              <div className="inline-qf-section-label">{t.entityProjects}</div>
              {matchedProjects.map((proj) => {
                const projTasks = tasks.filter(
                  (tk) => !tk.isDeleted && tk.projectId === proj.id && tk.status !== 'CANCELED'
                );
                const done = projTasks.filter((tk) => tk.status === 'COMPLETED').length;
                const area = proj.areaId ? areasMap.get(proj.areaId) : undefined;
                return (
                  <button
                    key={proj.id}
                    type="button"
                    className="inline-qf-row"
                    onClick={() => {
                      onNavigate({ kind: 'project', projectId: proj.id });
                      onClose();
                    }}
                  >
                    <div className="inline-qf-row-top">
                      <span
                        style={{
                          display: 'inline-flex',
                          alignItems: 'center',
                          gap: 8,
                          fontWeight: 650
                        }}
                      >
                        <ProjectProgressRing
                          completed={done}
                          total={projTasks.length}
                          size={16}
                          isProjectCompleted={proj.status === 'COMPLETED'}
                        />
                        <span>{proj.title}</span>
                      </span>
                      <span style={{ display: 'inline-flex', alignItems: 'center', gap: 6 }}>
                        <span className="meta-badge">
                          <Folder size={10.5} />
                          <span>
                            {done}/{projTasks.length}
                          </span>
                        </span>
                        {proj.deadline != null && renderDeadlineBadge(proj.deadline)}
                      </span>
                    </div>
                    {(area || proj.notes.trim()) && (
                      <div className="inline-qf-notes">
                        {area ? `📦 ${area.title}` : ''}
                        {area && proj.notes.trim() ? ' • ' : ''}
                        {proj.notes.trim()}
                      </div>
                    )}
                  </button>
                );
              })}
            </div>
          )}

          {/* 4. Tasks (3-row rich metadata) */}
          {matchedTasks.length > 0 && (
            <div>
              <div className="inline-qf-section-label">{t.entityTodos}</div>
              {matchedTasks.map((tk) => {
                const proj = tk.projectId ? projectsMap.get(tk.projectId) : undefined;
                const area =
                  proj?.areaId != null
                    ? areasMap.get(proj.areaId)
                    : tk.areaId
                      ? areasMap.get(tk.areaId)
                      : undefined;
                const containerText =
                  area && proj
                    ? `📦 ${area.title} › 📁 ${proj.title}`
                    : proj
                      ? `📁 ${proj.title}`
                      : area
                        ? `📦 ${area.title}`
                        : '';
                const chks = checklistItems.filter((c) => !c.isDeleted && c.taskId === tk.id);
                const chkDone = chks.filter((c) => c.isCompleted).length;
                const taskTags = tk.tagIds
                  .map((id) => tagsMap.get(id))
                  .filter((x): x is TagRecord => Boolean(x));

                const statusLabel =
                  tk.status === 'COMPLETED'
                    ? `✓ ${t.statCompleted}`
                    : tk.status === 'CANCELED'
                      ? `✕ ${t.statCanceled}`
                      : tk.scheduleState === 'THIS_EVENING'
                        ? t.viewThisEvening
                        : tk.scheduleState === 'TODAY'
                          ? t.viewToday
                          : tk.scheduleState === 'UPCOMING'
                            ? t.viewUpcoming
                            : tk.scheduleState === 'SOMEDAY'
                              ? t.viewSomeday
                              : tk.scheduleState === 'ANYTIME'
                                ? t.viewAnytime
                                : t.viewInbox;

                return (
                  <button
                    key={tk.id}
                    type="button"
                    className="inline-qf-row"
                    onClick={() => {
                      onNavigate(resolveTaskDestination(tk), tk.id);
                      onClose();
                    }}
                  >
                    {/* Row 1: Title + Status/Schedule Badge */}
                    <div className="inline-qf-row-top">
                      <span
                        style={{
                          fontWeight: 560,
                          textDecoration: tk.status !== 'OPEN' ? 'line-through' : undefined,
                          color: tk.status !== 'OPEN' ? 'var(--text-muted)' : 'var(--text-primary)'
                        }}
                      >
                        {tk.title || t.placeholderTodoTitle}
                      </span>
                      <span
                        className={`meta-badge ${
                          tk.scheduleState === 'TODAY'
                            ? 'today'
                            : tk.scheduleState === 'THIS_EVENING'
                              ? 'evening'
                              : ''
                        }`}
                      >
                        {tk.scheduleState === 'THIS_EVENING' && <Moon size={10.5} />}
                        <span>{statusLabel}</span>
                      </span>
                    </div>

                    {/* Row 2: Container, When, Reminder, Repeat, Deadline, Checklist, Tags */}
                    <div className="inline-qf-row-meta">
                      {containerText && <span className="meta-badge">{containerText}</span>}
                      {tk.whenDate != null && (
                        <span className="meta-badge">
                          <CalendarDays size={10.5} />
                          <span>{formatShortDate(tk.whenDate)}</span>
                        </span>
                      )}
                      {tk.reminderTime != null && (
                        <span className="meta-badge">
                          <Bell size={10.5} />
                          <span>{formatClock(tk.reminderTime)}</span>
                        </span>
                      )}
                      {tk.recurrenceRule != null && (
                        <span className="meta-badge">
                          <Repeat size={10.5} />
                          <span>{formatRecurrenceShort(tk.recurrenceRule, lang)}</span>
                        </span>
                      )}
                      {tk.deadline != null && renderDeadlineBadge(tk.deadline)}
                      {chks.length > 0 && (
                        <span className="meta-badge">
                          <CheckSquare size={10.5} />
                          <span>
                            {chkDone}/{chks.length}
                          </span>
                        </span>
                      )}
                      {taskTags.map((tg) => (
                        <span key={tg.id} className="meta-badge">
                          <Tag size={9.5} />
                          <span>{tg.title}</span>
                        </span>
                      ))}
                    </div>

                    {/* Row 3: 2-line Notes Preview */}
                    {tk.notes.trim() && <div className="inline-qf-notes">{tk.notes.trim()}</div>}
                  </button>
                );
              })}
            </div>
          )}
        </div>
      </div>
    </section>
  );
};
