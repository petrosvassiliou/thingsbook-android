import React, { useMemo, useState } from 'react';
import {
  AlertTriangle,
  Calendar,
  Check,
  Clock,
  MapPin,
  Moon,
  Timer,
  X
} from 'lucide-react';
import {
  startOfDayEpoch,
  type AreaRecord,
  type CalendarEventItem,
  type ProjectRecord,
  type TaskRecord
} from '../domain/types';
import {
  DEFAULT_TIMEBLOCK_DURATION_MINUTES,
  TIMEBLOCK_DURATION_PRESETS,
  buildDailyTimeline,
  formatMinutesAsClock,
  formatStandaloneDuration,
  parseClockToMinutes
} from '../domain/v2Utils';
import { localDb } from '../data/localDb';
import type { TranslationStrings } from '../i18n/translations';

const QUICK_TRAY_SLOTS = [9 * 60, 11 * 60, 14 * 60, 16 * 60, 19 * 60] as const;

interface TodayTimelineViewProps {
  todayTasks: TaskRecord[];
  calendarEvents: CalendarEventItem[];
  areasMap: Map<string, AreaRecord>;
  projectsMap: Map<string, ProjectRecord>;
  t: TranslationStrings;
  isFullScreen?: boolean;
  onExpandTask: (taskId: string) => void;
  onStartFocusTimer: (taskId: string, taskTitle: string, durationMinutes?: number | null) => void;
}

export const TodayTimelineView: React.FC<TodayTimelineViewProps> = ({
  todayTasks,
  calendarEvents,
  areasMap,
  projectsMap,
  t,
  isFullScreen = false,
  onExpandTask,
  onStartFocusTimer
}) => {
  const [defaultTrayDuration, setDefaultTrayDuration] = useState<number>(
    DEFAULT_TIMEBLOCK_DURATION_MINUTES
  );

  const dayStart = startOfDayEpoch();
  const timeline = useMemo(
    () => buildDailyTimeline(todayTasks, calendarEvents, dayStart),
    [todayTasks, calendarEvents, dayStart]
  );

  const getContainerSubtitle = (tk: TaskRecord): string => {
    const proj = tk.projectId ? projectsMap.get(tk.projectId) : undefined;
    const area = proj?.areaId
      ? areasMap.get(proj.areaId)
      : tk.areaId
        ? areasMap.get(tk.areaId)
        : undefined;
    if (area && proj) return `${area.title} › ${proj.title}`;
    if (proj) return proj.title;
    if (area) return area.title;
    return '';
  };

  return (
    <div
      className={`v2-timeline-root ${isFullScreen ? 'fullscreen' : ''}`}
      data-testid="today-timeline-view"
      onClick={(e) => e.stopPropagation()}
    >
      {/* All-Day Calendar Events Strip */}
      {timeline.allDayEvents.length > 0 && (
        <div className="v2-timeline-allday-bar">
          {timeline.allDayEvents.map((ev) => (
            <div key={`allday-${ev.calendarId}-${ev.eventId}`} className="v2-timeline-allday-chip">
              <span
                className="v2-timeline-cal-dot"
                style={{ backgroundColor: ev.calendarColorCss }}
              />
              <span style={{ fontWeight: 600 }}>{ev.title}</span>
              <span style={{ color: 'var(--text-muted)', fontSize: 11.2 }}>
                · {t.calendarAllDay}
              </span>
            </div>
          ))}
        </div>
      )}

      {/* Chronological Interleaved Schedule */}
      {timeline.timedItems.length === 0 ? (
        <div className="v2-timeline-empty">{t.timelineEmptyScheduled}</div>
      ) : (
        <div className="v2-timeline-list">
          {timeline.timedItems.map((item) => {
            const startClock = formatMinutesAsClock(item.startMinutes);

            if (item.kind === 'calendar' && item.calendarEvent) {
              const ev = item.calendarEvent;
              return (
                <div
                  key={item.id}
                  className={`v2-timeline-row calendar ${item.hasConflict ? 'conflict' : ''}`}
                >
                  <div className="v2-timeline-gutter">
                    <span className="v2-timeline-clock">{startClock}</span>
                    <span className="v2-timeline-line" />
                  </div>

                  <div
                    className="v2-timeline-card calendar"
                    style={{ borderLeftColor: ev.calendarColorCss }}
                  >
                    <div className="v2-timeline-card-main">
                      <div style={{ display: 'flex', alignItems: 'center', gap: 7, flexWrap: 'wrap' }}>
                        <Calendar size={13.5} style={{ color: ev.calendarColorCss, flexShrink: 0 }} />
                        <span style={{ fontWeight: 650, fontSize: 13.8 }}>{ev.title}</span>
                        <span className="meta-badge">🕐 {item.rangeLabel}</span>
                        {item.hasConflict && (
                          <span
                            className="v2-conflict-badge"
                            title={item.conflictTitles.join(', ')}
                          >
                            <AlertTriangle size={11} />
                            <span>{t.timelineConflictBadge}</span>
                          </span>
                        )}
                      </div>
                      <div className="v2-timeline-sub-row">
                        <span>{ev.calendarName}</span>
                        {ev.location && (
                          <span style={{ display: 'inline-flex', alignItems: 'center', gap: 3 }}>
                            <MapPin size={11} />
                            <span>{ev.location}</span>
                          </span>
                        )}
                      </div>
                    </div>
                  </div>
                </div>
              );
            }

            const tk = item.task!;
            const containerLabel = getContainerSubtitle(tk);
            const taskTitle = tk.title.trim() || t.placeholderTodoTitle;
            const timeSpanLabel = `${formatMinutesAsClock(item.startMinutes)} – ${formatMinutesAsClock(item.endMinutes)}`;
            const durLabel = formatStandaloneDuration(
              tk.durationMinutes ?? item.durationMinutes
            );
            const scheduledTipParts: string[] = [
              `${tk.isMilestone ? '◆ ' : ''}${taskTitle}`,
              timeSpanLabel,
              durLabel
            ];
            if (tk.effortLevel === 'QUICK_WIN') {
              scheduledTipParts.push(t.effortQuickWin);
            } else if (tk.effortLevel === 'MEDIUM') {
              scheduledTipParts.push(t.effortMedium);
            } else if (tk.effortLevel === 'DEEP_FOCUS') {
              scheduledTipParts.push(t.effortDeepFocus);
            }
            if (containerLabel) {
              scheduledTipParts.push(containerLabel);
            }
            const scheduledMouseTip = scheduledTipParts.join(' · ');

            return (
              <div
                key={item.id}
                className={`v2-timeline-row task ${item.hasConflict ? 'conflict' : ''}`}
                data-testid={`timeline-task-${tk.id}`}
              >
                <div className="v2-timeline-gutter">
                  <span className="v2-timeline-clock">{startClock}</span>
                  <span className="v2-timeline-line" />
                </div>

                <div
                  className={`v2-timeline-card task ${tk.isMilestone ? 'milestone-item' : ''}`}
                  data-timeline-mouse-tip={scheduledMouseTip}
                >
                  <button
                    type="button"
                    className={`things-checkbox ${tk.isMilestone ? 'milestone' : ''} ${
                      tk.status === 'OPEN' && tk.isInProgress ? 'in-progress' : ''
                    } ${tk.status === 'COMPLETED' ? 'completed checked' : ''}`}
                    disabled={isFullScreen}
                    aria-disabled={isFullScreen || undefined}
                    style={
                      isFullScreen
                        ? { pointerEvents: 'none', opacity: 0.55, cursor: 'default' }
                        : undefined
                    }
                    title={isFullScreen ? undefined : t.actionComplete}
                    aria-label={t.actionComplete}
                    onClick={(e) => {
                      if (isFullScreen) return;
                      if (e.shiftKey && tk.status === 'OPEN') {
                        void localDb.toggleTaskInProgress(tk.id);
                        return;
                      }
                      void localDb.upsertTask({ id: tk.id, status: 'COMPLETED' });
                    }}
                  >
                    {tk.status === 'COMPLETED' && <Check size={12} strokeWidth={3} />}
                  </button>

                  <div
                    className="v2-timeline-card-main"
                    style={{ cursor: isFullScreen ? 'default' : 'pointer' }}
                    onClick={isFullScreen ? undefined : () => onExpandTask(tk.id)}
                  >
                    <div style={{ display: 'flex', alignItems: 'center', gap: 7, flexWrap: 'wrap' }}>
                      <span style={{ fontWeight: 620, fontSize: 14 }}>
                        {tk.title || t.placeholderTodoTitle}
                      </span>
                      {tk.status === 'OPEN' && tk.isInProgress && (
                        <span
                          className="meta-badge in-progress-badge"
                          data-testid="timeline-task-in-progress-pill"
                        >
                          ◐ {t.v33InProgressBadge}
                        </span>
                      )}
                      {tk.isMilestone && (
                        <span
                          className="meta-badge milestone-badge"
                          data-testid="task-row-milestone-pill"
                        >
                          ◆ {t.milestoneLabel}
                        </span>
                      )}
                      <span className="meta-badge active">🕐 {item.rangeLabel}</span>
                      {tk.scheduleState === 'THIS_EVENING' && (
                        <span className="meta-badge evening">
                          <Moon size={10.5} />
                          <span>{t.viewThisEvening}</span>
                        </span>
                      )}
                      {item.hasConflict && (
                        <span
                          className="v2-conflict-badge"
                          title={item.conflictTitles.join(', ')}
                        >
                          <AlertTriangle size={11} />
                          <span>{t.timelineConflictBadge}</span>
                        </span>
                      )}
                    </div>
                    {containerLabel && (
                      <div className="v2-timeline-sub-row">{containerLabel}</div>
                    )}
                  </div>

                  {!isFullScreen && (
                    <div className="v2-timeline-card-actions">
                      <button
                        type="button"
                        className="chip-btn"
                        title={t.timelineShiftPlus15m}
                        onClick={() =>
                          void localDb.upsertTask({
                            id: tk.id,
                            scheduledStartMinutes: Math.min(1425, item.startMinutes + 15),
                            durationMinutes: tk.durationMinutes ?? item.durationMinutes
                          })
                        }
                      >
                        <span>{t.timelineShiftPlus15m}</span>
                      </button>
                      <button
                        type="button"
                        className="chip-btn active"
                        title={t.actionFocusTimerMinutes(
                          Math.min(720, tk.durationMinutes ?? item.durationMinutes)
                        )}
                        onClick={() =>
                          onStartFocusTimer(
                            tk.id,
                            tk.title || t.placeholderTodoTitle,
                            Math.min(720, tk.durationMinutes ?? item.durationMinutes)
                          )
                        }
                      >
                        <Timer size={12.5} />
                        <span>
                          {formatStandaloneDuration(tk.durationMinutes ?? item.durationMinutes)}
                        </span>
                      </button>
                      <button
                        type="button"
                        className="icon-btn"
                        title={t.timeblockClear}
                        aria-label={t.timeblockClear}
                        onClick={() =>
                          void localDb.upsertTask({
                            id: tk.id,
                            scheduledStartMinutes: null
                          })
                        }
                      >
                        <X size={14} />
                      </button>
                    </div>
                  )}
                </div>
              </div>
            );
          })}
        </div>
      )}

      {/* Unscheduled Today Tray (Hidden in Full Screen so 100% of screen is pure hourly Timeline) */}
      {!isFullScreen && timeline.unscheduledTasks.length > 0 && (
        <div className="v2-unscheduled-tray" data-testid="unscheduled-today-tray">
          <div className="v2-unscheduled-header">
            <div>
              <div style={{ fontWeight: 700, fontSize: 13.6, display: 'flex', alignItems: 'center', gap: 6 }}>
                <Clock size={14.5} style={{ color: 'var(--things-blue)' }} />
                <span>
                  {t.timelineUnscheduledTitle} ({timeline.unscheduledTasks.length})
                </span>
              </div>
              <div style={{ fontSize: 11.8, color: 'var(--text-secondary)', marginTop: 2 }}>
                {t.timelineUnscheduledDesc}
              </div>
            </div>

            <div style={{ display: 'flex', alignItems: 'center', gap: 4, flexWrap: 'wrap' }}>
              <span style={{ fontSize: 11.5, color: 'var(--text-muted)', marginRight: 2 }}>
                {t.timeblockDuration}:
              </span>
              {TIMEBLOCK_DURATION_PRESETS.slice(0, 5).map((dur) => (
                <button
                  key={dur}
                  type="button"
                  className={`chip-btn ${defaultTrayDuration === dur ? 'active' : ''}`}
                  onClick={() => setDefaultTrayDuration(dur)}
                >
                  {dur}m
                </button>
              ))}
            </div>
          </div>

          <div className="v2-unscheduled-list">
            {timeline.unscheduledTasks.map((tk) => {
              const containerLabel = getContainerSubtitle(tk);
              const unscheduledParts: string[] = [
                `${tk.isMilestone ? '◆ ' : ''}${tk.title.trim() || t.placeholderTodoTitle}`
              ];
              if (tk.durationMinutes != null && tk.durationMinutes > 0) {
                unscheduledParts.push(formatStandaloneDuration(tk.durationMinutes));
              }
              if (tk.effortLevel === 'QUICK_WIN') {
                unscheduledParts.push(t.effortQuickWin);
              } else if (tk.effortLevel === 'MEDIUM') {
                unscheduledParts.push(t.effortMedium);
              } else if (tk.effortLevel === 'DEEP_FOCUS') {
                unscheduledParts.push(t.effortDeepFocus);
              }
              if (containerLabel) {
                unscheduledParts.push(containerLabel);
              }
              if (unscheduledParts.length === 1) {
                unscheduledParts.push(t.timelineUnscheduledTitle);
              }
              const unscheduledMouseTip = unscheduledParts.join(' · ');

              return (
                <div
                  key={tk.id}
                  className={`v2-unscheduled-row ${tk.isMilestone ? 'milestone-item' : ''}`}
                  data-timeline-mouse-tip={unscheduledMouseTip}
                >
                  <button
                    type="button"
                    className={`things-checkbox ${tk.isMilestone ? 'milestone' : ''} ${
                      tk.status === 'OPEN' && tk.isInProgress ? 'in-progress' : ''
                    }`}
                    aria-label={t.actionComplete}
                    title={t.actionComplete}
                    onClick={(e) => {
                      if (e.shiftKey && tk.status === 'OPEN') {
                        void localDb.toggleTaskInProgress(tk.id);
                        return;
                      }
                      void localDb.upsertTask({ id: tk.id, status: 'COMPLETED' });
                    }}
                  />
                  <div
                    style={{ flex: 1, minWidth: 0, cursor: 'pointer' }}
                    onClick={() => onExpandTask(tk.id)}
                  >
                    <div style={{ display: 'flex', alignItems: 'center', gap: 6, flexWrap: 'wrap' }}>
                      <span style={{ fontWeight: 560, fontSize: 13.6 }}>
                        {tk.title || t.placeholderTodoTitle}
                      </span>
                      {tk.status === 'OPEN' && tk.isInProgress && (
                        <span
                          className="meta-badge in-progress-badge"
                          data-testid="timeline-task-in-progress-pill"
                        >
                          ◐ {t.v33InProgressBadge}
                        </span>
                      )}
                      {tk.isMilestone && (
                        <span
                          className="meta-badge milestone-badge"
                          data-testid="task-row-milestone-pill"
                        >
                          ◆ {t.milestoneLabel}
                        </span>
                      )}
                    </div>
                    {containerLabel && (
                      <div style={{ fontSize: 11.4, color: 'var(--text-muted)' }}>
                        {containerLabel}
                      </div>
                    )}
                  </div>

                  <div className="v2-unscheduled-slots">
                    {QUICK_TRAY_SLOTS.map((mins) => (
                      <button
                        key={mins}
                        type="button"
                        className="chip-btn"
                        onClick={() =>
                          void localDb.upsertTask({
                            id: tk.id,
                            scheduledStartMinutes: mins,
                            durationMinutes: tk.durationMinutes ?? defaultTrayDuration
                          })
                        }
                      >
                        {formatMinutesAsClock(mins)}
                      </button>
                    ))}
                    <input
                      type="time"
                      className="v2-inline-time-input"
                      aria-label={t.timeblockStartTime}
                      value=""
                      onChange={(e) => {
                        const parsed = parseClockToMinutes(e.target.value);
                        if (parsed != null) {
                          void localDb.upsertTask({
                            id: tk.id,
                            scheduledStartMinutes: parsed,
                            durationMinutes: tk.durationMinutes ?? defaultTrayDuration
                          });
                        }
                      }}
                    />
                  </div>
                </div>
              );
            })}
          </div>
        </div>
      )}
    </div>
  );
};
