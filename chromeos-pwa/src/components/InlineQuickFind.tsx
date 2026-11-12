import React, { useMemo, useState } from 'react';
import {
  Archive,
  Bell,
  BookOpenCheck,
  CalendarDays,
  CheckSquare,
  Flag,
  Folder,
  Inbox,
  Layers,
  Moon,
  Package,
  Repeat,
  Search,
  Star,
  Tag,
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

  const areasMap = useMemo(
    () => new Map(areas.filter((a) => !a.isDeleted).map((a) => [a.id, a])),
    [areas]
  );
  const projectsMap = useMemo(
    () => new Map(projects.filter((p) => !p.isDeleted).map((p) => [p.id, p])),
    [projects]
  );
  const tagsMap = useMemo(
    () => new Map(tags.filter((tg) => !tg.isDeleted).map((tg) => [tg.id, tg])),
    [tags]
  );

  const q = query.trim().toLowerCase();

  const smartViews: Array<{ view: SmartViewType; label: string; icon: React.ReactNode }> = useMemo(
    () => [
      { view: 'INBOX', label: t.viewInbox, icon: <Inbox size={15} style={{ color: 'var(--things-blue)' }} /> },
      { view: 'TODAY', label: t.viewToday, icon: <Star size={15} fill="var(--today-star)" style={{ color: 'var(--today-star)' }} /> },
      { view: 'UPCOMING', label: t.viewUpcoming, icon: <CalendarDays size={15} style={{ color: 'var(--upcoming-coral)' }} /> },
      { view: 'ANYTIME', label: t.viewAnytime, icon: <Layers size={15} style={{ color: 'var(--anytime-teal)' }} /> },
      { view: 'SOMEDAY', label: t.viewSomeday, icon: <Archive size={15} style={{ color: 'var(--someday-amber)' }} /> },
      { view: 'LOGBOOK', label: t.viewLogbook, icon: <BookOpenCheck size={15} style={{ color: 'var(--logbook-green)' }} /> }
    ],
    [t]
  );

  const matchedViews = useMemo(() => {
    if (!q) return smartViews;
    return smartViews.filter((sv) => sv.label.toLowerCase().includes(q));
  }, [q, smartViews]);

  const matchedAreas = useMemo(() => {
    const active = areas.filter((a) => !a.isDeleted);
    if (!q) return active;
    return active.filter((a) => a.title.toLowerCase().includes(q));
  }, [areas, q]);

  const matchedProjects = useMemo(() => {
    const active = projects.filter((p) => !p.isDeleted);
    if (!q) return active.slice(0, 8);
    return active.filter(
      (p) =>
        p.title.toLowerCase().includes(q) ||
        p.notes.toLowerCase().includes(q) ||
        (p.areaId && (areasMap.get(p.areaId)?.title.toLowerCase().includes(q) ?? false))
    );
  }, [projects, areasMap, q]);

  const matchedTasks = useMemo(() => {
    const nonDeleted = tasks.filter((tk) => !tk.isDeleted);
    if (!q) return nonDeleted.filter((tk) => tk.status === 'OPEN').slice(0, 12);
    return nonDeleted
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
      .slice(0, 40);
  }, [tasks, q, tagsMap, projectsMap, areasMap, checklistItems]);

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
