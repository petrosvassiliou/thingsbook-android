import React, { useEffect, useMemo, useRef, useState } from 'react';
import {
  AlertTriangle,
  Archive,
  ArrowLeft,
  ArrowRight,
  CalendarDays,
  Check,
  CheckCircle2,
  Flag,
  Folder,
  Inbox,
  Layers,
  ListChecks,
  Moon,
  Plus,
  Sparkles,
  Star,
  Trash2,
  X
} from 'lucide-react';
import {
  addDaysLocal,
  daysBetweenToday,
  startOfDayEpoch,
  type AreaRecord,
  type ProjectRecord,
  type SupportedLang,
  type TaskRecord
} from '../domain/types';
import { detectStalledProjects } from '../domain/v2Utils';
import { localDb } from '../data/localDb';
import type { TranslationStrings } from '../i18n/translations';

export type WeeklyReviewStep = 1 | 2 | 3 | 4;

interface WeeklyReviewModalProps {
  initialStep?: WeeklyReviewStep;
  projects: ProjectRecord[];
  tasks: TaskRecord[];
  areasMap: Map<string, AreaRecord>;
  projectsMap: Map<string, ProjectRecord>;
  lastWeeklyReviewAt?: number;
  lang: SupportedLang;
  t: TranslationStrings;
  onOpenProject: (projectId: string) => void;
  onOpenTask?: (task: TaskRecord) => void;
  onCompleteReview: () => void;
  onClose: () => void;
}

export const WeeklyReviewModal: React.FC<WeeklyReviewModalProps> = ({
  initialStep = 1,
  projects,
  tasks,
  areasMap,
  projectsMap,
  lastWeeklyReviewAt,
  lang,
  t,
  onOpenProject,
  onOpenTask,
  onCompleteReview,
  onClose
}) => {
  const [step, setStep] = useState<WeeklyReviewStep>(initialStep);
  const [nextActionDrafts, setNextActionDrafts] = useState<Record<string, string>>({});
  const [feedbackText, setFeedbackText] = useState<string | null>(null);
  const feedbackTimerRef = useRef<number | null>(null);

  const showFeedback = (label: string) => {
    if (feedbackTimerRef.current != null) {
      window.clearTimeout(feedbackTimerRef.current);
    }
    setFeedbackText(label);
    feedbackTimerRef.current = window.setTimeout(() => {
      setFeedbackText(null);
      feedbackTimerRef.current = null;
    }, 2800);
  };

  useEffect(() => {
    return () => {
      if (feedbackTimerRef.current != null) {
        window.clearTimeout(feedbackTimerRef.current);
      }
    };
  }, []);

  const todayEpoch = startOfDayEpoch();
  const tomorrowEpoch = addDaysLocal(todayEpoch, 1);
  const nextWeekEpoch = addDaysLocal(todayEpoch, 7);

  const activeProjectIds = useMemo(
    () =>
      new Set(
        projects.filter((p) => !p.isDeleted && p.status === 'ACTIVE').map((p) => p.id)
      ),
    [projects]
  );

  // Step 1: Unprocessed Inbox Tasks
  const inboxTasks = useMemo(
    () =>
      tasks
        .filter(
          (tk) =>
            !tk.isDeleted &&
            tk.status === 'OPEN' &&
            tk.scheduleState === 'INBOX' &&
            tk.projectId == null &&
            tk.areaId == null
        )
        .sort((a, b) => a.sortOrder - b.sortOrder),
    [tasks]
  );

  // Step 2: Today, Overdue, or Due-Soon Tasks
  const urgentTasks = useMemo(
    () =>
      tasks
        .filter((tk) => {
          if (tk.isDeleted || tk.status !== 'OPEN') return false;
          if (tk.projectId && !activeProjectIds.has(tk.projectId)) return false;
          const inToday =
            tk.scheduleState === 'TODAY' || tk.scheduleState === 'THIS_EVENING';
          const whenOverdue = tk.whenDate != null && daysBetweenToday(tk.whenDate) < 0;
          const deadlineDueSoonOrOverdue =
            tk.deadline != null && daysBetweenToday(tk.deadline) <= 3;
          return inToday || whenOverdue || deadlineDueSoonOrOverdue;
        })
        .sort((a, b) => {
          const aKey = a.deadline ?? a.whenDate ?? Number.MAX_SAFE_INTEGER;
          const bKey = b.deadline ?? b.whenDate ?? Number.MAX_SAFE_INTEGER;
          return aKey - bKey;
        }),
    [tasks, activeProjectIds]
  );

  // Step 3: Stalled Projects
  const stalledProjects = useMemo(
    () => detectStalledProjects(projects, tasks),
    [projects, tasks]
  );

  // Step 4: Someday items + 7-day completed count
  const somedayTasks = useMemo(
    () =>
      tasks
        .filter(
          (tk) =>
            !tk.isDeleted &&
            tk.status === 'OPEN' &&
            tk.scheduleState === 'SOMEDAY' &&
            (!tk.projectId || activeProjectIds.has(tk.projectId))
        )
        .sort((a, b) => a.sortOrder - b.sortOrder),
    [tasks, activeProjectIds]
  );

  const somedayProjects = useMemo(
    () =>
      projects
        .filter(
          (p) => !p.isDeleted && p.status === 'ACTIVE' && p.scheduleState === 'SOMEDAY'
        )
        .sort((a, b) => a.sortOrder - b.sortOrder),
    [projects]
  );

  const [reviewOpenedAt] = useState(() => Date.now());
  const completed7dCount = useMemo(() => {
    const sevenDaysAgo = reviewOpenedAt - 7 * 86_400_000;
    return tasks.filter(
      (tk) =>
        !tk.isDeleted &&
        tk.status === 'COMPLETED' &&
        (tk.completedAt ?? tk.updatedAt) >= sevenDaysAgo
    ).length;
  }, [tasks, reviewOpenedAt]);

  const handleAddNextAction = async (projectId: string, projectTitle: string) => {
    const raw = (nextActionDrafts[projectId] ?? '').trim();
    if (!raw) return;
    setNextActionDrafts((prev) => ({ ...prev, [projectId]: '' }));
    await localDb.upsertTask({
      title: raw,
      projectId,
      scheduleState: 'TODAY',
      whenDate: todayEpoch
    });
    await localDb.markProjectReviewed(projectId);
    showFeedback(`${projectTitle} → ${t.viewToday}`);
  };

  const stepsMeta: Array<{
    num: WeeklyReviewStep;
    title: string;
    desc: string;
    badgeCount: number;
  }> = [
    {
      num: 1,
      title: t.weeklyReviewStep1Title,
      desc: t.weeklyReviewStep1Desc,
      badgeCount: inboxTasks.length
    },
    {
      num: 2,
      title: t.weeklyReviewStep2Title,
      desc: t.weeklyReviewStep2Desc,
      badgeCount: urgentTasks.length
    },
    {
      num: 3,
      title: t.weeklyReviewStep3Title,
      desc: t.weeklyReviewStep3Desc,
      badgeCount: stalledProjects.length
    },
    {
      num: 4,
      title: t.weeklyReviewStep4Title,
      desc: t.weeklyReviewStep4Desc,
      badgeCount: somedayTasks.length + somedayProjects.length
    }
  ];

  const activeMeta = stepsMeta[step - 1];

  const formatShortDate = (ms: number) =>
    new Intl.DateTimeFormat(lang, { month: 'short', day: 'numeric' }).format(new Date(ms));

  const plus7Label =
    lang === 'de'
      ? '+7 T.'
      : lang === 'fr'
        ? '+7 j'
        : lang === 'it'
          ? '+7 gg'
          : lang === 'es'
            ? '+7 d'
            : '+7d';

  const renderScheduleBadge = (tk: TaskRecord) => {
    const whenDiff = tk.whenDate != null ? daysBetweenToday(tk.whenDate) : null;
    if (tk.scheduleState === 'TODAY') {
      return (
        <span className="meta-badge">
          <Star size={10} fill="var(--today-star)" style={{ color: 'var(--today-star)' }} />
          <span>{t.viewToday}</span>
        </span>
      );
    }
    if (tk.scheduleState === 'THIS_EVENING') {
      return (
        <span className="meta-badge">
          <Moon size={10} />
          <span>{t.viewThisEvening}</span>
        </span>
      );
    }
    if (tk.scheduleState === 'UPCOMING') {
      return (
        <span className="meta-badge">
          <CalendarDays size={10} />
          <span>
            {whenDiff === 1
              ? t.dateTomorrow
              : tk.whenDate != null
                ? formatShortDate(tk.whenDate)
                : t.viewUpcoming}
          </span>
        </span>
      );
    }
    if (tk.scheduleState === 'SOMEDAY') {
      return (
        <span className="meta-badge">
          <Archive size={10} />
          <span>{t.viewSomeday}</span>
        </span>
      );
    }
    if (tk.scheduleState === 'ANYTIME') {
      return (
        <span className="meta-badge">
          <Layers size={10} />
          <span>{t.viewAnytime}</span>
        </span>
      );
    }
    return null;
  };

  return (
    <div className="modal-backdrop" onClick={onClose}>
      <div
        className="modal-panel wide v2-review-modal"
        data-testid="weekly-review-modal"
        onClick={(e) => e.stopPropagation()}
      >
        <div className="modal-header">
          <div>
            <h3 className="modal-title" style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
              <ListChecks size={19} style={{ color: 'var(--things-blue)' }} />
              <span>{t.weeklyReviewTitle}</span>
            </h3>
            <div style={{ fontSize: 12.2, color: 'var(--text-secondary)', marginTop: 2 }}>
              {t.weeklyReviewSubtitle}
            </div>
          </div>
          <button type="button" className="icon-btn" title={t.actionDone} onClick={onClose}>
            <X size={18} />
          </button>
        </div>

        {/* 4-Step Stepper Tabs */}
        <div className="v2-review-stepper" role="tablist">
          {stepsMeta.map((sm) => {
            const isActive = step === sm.num;
            const isClear = sm.num <= 3 && sm.badgeCount === 0;
            return (
              <button
                key={sm.num}
                type="button"
                role="tab"
                aria-selected={isActive}
                data-testid={`weekly-review-step-${sm.num}`}
                className={`v2-review-step-tab ${isActive ? 'active' : ''}`}
                onClick={() => setStep(sm.num)}
              >
                <span className="v2-review-step-label">{sm.title}</span>
                <span className={`v2-review-step-count ${isClear ? 'clear' : ''}`}>
                  {isClear ? '✓' : sm.badgeCount}
                </span>
              </button>
            );
          })}
        </div>

        <div
          className="v2-review-step-banner"
          style={{
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'space-between',
            gap: 12,
            flexWrap: 'wrap'
          }}
        >
          <div style={{ minWidth: 0, flex: 1 }}>
            <div style={{ fontWeight: 650, fontSize: 13.8 }}>{activeMeta.title}</div>
            <div style={{ fontSize: 12.2, color: 'var(--text-secondary)', marginTop: 2 }}>
              {activeMeta.desc}
            </div>
          </div>
          {feedbackText && (
            <span
              data-testid="weekly-review-feedback-pill"
              style={{
                display: 'inline-flex',
                alignItems: 'center',
                gap: 5,
                padding: '4px 10px',
                borderRadius: 999,
                fontSize: 11.8,
                fontWeight: 650,
                color: 'var(--logbook-green)',
                background: 'rgba(46, 160, 67, 0.14)',
                maxWidth: '100%'
              }}
            >
              <Check size={12} />
              <span style={{ overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' }}>
                {feedbackText}
              </span>
            </span>
          )}
        </div>

        {/* Step Body */}
        <div className="v2-review-body">
          {/* STEP 1: CLEAR INBOX */}
          {step === 1 && (
            <>
              {inboxTasks.length === 0 ? (
                <div className="v2-review-empty-card">
                  <Inbox size={26} style={{ color: 'var(--logbook-green)' }} />
                  <div>{t.weeklyReviewInboxZero}</div>
                </div>
              ) : (
                <div className="v2-review-list">
                  {inboxTasks.map((tk) => {
                    const displayTitle = tk.title || t.placeholderTodoTitle;
                    return (
                      <div key={tk.id} className="v2-review-item-card">
                        <button
                          type="button"
                          onClick={() => {
                            if (onOpenTask) {
                              onClose();
                              onOpenTask(tk);
                            }
                          }}
                          style={{
                            flex: 1,
                            minWidth: 0,
                            background: 'transparent',
                            border: 'none',
                            padding: 0,
                            textAlign: 'left',
                            color: 'inherit',
                            cursor: onOpenTask ? 'pointer' : 'default'
                          }}
                        >
                          <div style={{ fontWeight: 600, fontSize: 13.8 }}>{displayTitle}</div>
                          {tk.notes.trim() && (
                            <div className="v2-review-item-notes">
                              {tk.notes.trim().split('\n')[0]}
                            </div>
                          )}
                        </button>
                        <div className="v2-review-actions-wrap">
                          <button
                            type="button"
                            className="chip-btn"
                            onClick={() => {
                              void localDb.upsertTask({
                                id: tk.id,
                                scheduleState: 'TODAY',
                                whenDate: todayEpoch
                              });
                              showFeedback(`${displayTitle} → ${t.viewToday}`);
                            }}
                          >
                            <Star
                              size={11.5}
                              fill="var(--today-star)"
                              style={{ color: 'var(--today-star)' }}
                            />
                            <span>{t.viewToday}</span>
                          </button>
                          <button
                            type="button"
                            className="chip-btn"
                            onClick={() => {
                              void localDb.upsertTask({
                                id: tk.id,
                                scheduleState: 'THIS_EVENING',
                                whenDate: todayEpoch
                              });
                              showFeedback(`${displayTitle} → ${t.viewThisEvening}`);
                            }}
                          >
                            <Moon size={11.5} />
                            <span>{t.viewThisEvening}</span>
                          </button>
                          <button
                            type="button"
                            className="chip-btn"
                            onClick={() => {
                              void localDb.upsertTask({
                                id: tk.id,
                                scheduleState: 'UPCOMING',
                                whenDate: tomorrowEpoch
                              });
                              showFeedback(`${displayTitle} → ${t.dateTomorrow}`);
                            }}
                          >
                            <CalendarDays size={11.5} />
                            <span>{t.dateTomorrow}</span>
                          </button>
                          <button
                            type="button"
                            className="chip-btn"
                            onClick={() => {
                              void localDb.upsertTask({
                                id: tk.id,
                                scheduleState: 'SOMEDAY',
                                whenDate: null
                              });
                              showFeedback(`${displayTitle} → ${t.viewSomeday}`);
                            }}
                          >
                            <Archive size={11.5} />
                            <span>{t.viewSomeday}</span>
                          </button>
                          <button
                            type="button"
                            className="chip-btn active"
                            title={t.actionComplete}
                            onClick={() => {
                              void localDb.upsertTask({ id: tk.id, status: 'COMPLETED' });
                              showFeedback(`${displayTitle} → ${t.actionComplete}`);
                            }}
                          >
                            <Check size={12} />
                          </button>
                          <button
                            type="button"
                            className="chip-btn urgent"
                            title={t.actionDelete}
                            onClick={() => {
                              void localDb.softDeleteTasks([tk.id]);
                              showFeedback(`${displayTitle} → ${t.actionDelete}`);
                            }}
                          >
                            <Trash2 size={12} />
                          </button>
                        </div>
                      </div>
                    );
                  })}
                </div>
              )}
            </>
          )}

          {/* STEP 2: TODAY & DEADLINES */}
          {step === 2 && (
            <>
              {urgentTasks.length === 0 ? (
                <div className="v2-review-empty-card">
                  <CheckCircle2 size={26} style={{ color: 'var(--logbook-green)' }} />
                  <div>{t.weeklyReviewNoUrgentDeadlines}</div>
                </div>
              ) : (
                <div className="v2-review-list">
                  {urgentTasks.map((tk) => {
                    const displayTitle = tk.title || t.placeholderTodoTitle;
                    const proj = tk.projectId ? projectsMap.get(tk.projectId) : undefined;
                    const area = proj?.areaId
                      ? areasMap.get(proj.areaId)
                      : tk.areaId
                        ? areasMap.get(tk.areaId)
                        : undefined;
                    const ctxLabel = proj ? proj.title : area ? area.title : '';
                    const dlDiff =
                      tk.deadline != null ? daysBetweenToday(tk.deadline) : null;
                    const whenDiff =
                      tk.whenDate != null ? daysBetweenToday(tk.whenDate) : null;
                    const isTodaySelected =
                      tk.scheduleState === 'TODAY' ||
                      tk.scheduleState === 'THIS_EVENING' ||
                      whenDiff === 0;
                    const isTomorrowSelected =
                      tk.scheduleState === 'UPCOMING' && whenDiff === 1;
                    const isPlus7Selected =
                      tk.scheduleState === 'UPCOMING' && whenDiff === 7;

                    return (
                      <div key={tk.id} className="v2-review-item-card">
                        <button
                          type="button"
                          onClick={() => {
                            if (onOpenTask) {
                              onClose();
                              onOpenTask(tk);
                            }
                          }}
                          style={{
                            flex: 1,
                            minWidth: 0,
                            background: 'transparent',
                            border: 'none',
                            padding: 0,
                            textAlign: 'left',
                            color: 'inherit',
                            cursor: onOpenTask ? 'pointer' : 'default'
                          }}
                        >
                          <div style={{ fontWeight: 600, fontSize: 13.8 }}>{displayTitle}</div>
                          <div
                            style={{
                              display: 'flex',
                              alignItems: 'center',
                              gap: 6,
                              flexWrap: 'wrap',
                              marginTop: 4
                            }}
                          >
                            {ctxLabel && (
                              <span style={{ fontSize: 11.6, color: 'var(--text-secondary)' }}>
                                {ctxLabel}
                              </span>
                            )}
                            {renderScheduleBadge(tk)}
                            {tk.deadline != null && dlDiff != null && (
                              <span className={`meta-badge ${dlDiff <= 2 ? 'urgent' : ''}`}>
                                <Flag size={10} />
                                <span>
                                  {formatShortDate(tk.deadline)} ·{' '}
                                  {dlDiff === 0
                                    ? t.deadlineToday
                                    : dlDiff > 0
                                      ? t.deadlineDaysLeft(dlDiff)
                                      : t.deadlineDaysOverdue(Math.abs(dlDiff))}
                                </span>
                              </span>
                            )}
                          </div>
                        </button>

                        <div className="v2-review-actions-wrap">
                          <button
                            type="button"
                            className={`chip-btn ${isTodaySelected ? 'active' : ''}`}
                            aria-pressed={isTodaySelected}
                            onClick={() => {
                              void localDb.upsertTask({
                                id: tk.id,
                                scheduleState: 'TODAY',
                                whenDate: todayEpoch
                              });
                              showFeedback(`${displayTitle} → ${t.viewToday}`);
                            }}
                          >
                            {isTodaySelected ? (
                              <Check size={11.5} />
                            ) : (
                              <Star
                                size={11.5}
                                fill="var(--today-star)"
                                style={{ color: 'var(--today-star)' }}
                              />
                            )}
                            <span>{t.viewToday}</span>
                          </button>
                          <button
                            type="button"
                            className={`chip-btn ${isTomorrowSelected ? 'active' : ''}`}
                            aria-pressed={isTomorrowSelected}
                            onClick={() => {
                              void localDb.upsertTask({
                                id: tk.id,
                                scheduleState: 'UPCOMING',
                                whenDate: tomorrowEpoch,
                                ...(tk.deadline != null && tk.deadline < tomorrowEpoch
                                  ? { deadline: tomorrowEpoch }
                                  : {})
                              });
                              showFeedback(`${displayTitle} → ${t.dateTomorrow}`);
                            }}
                          >
                            {isTomorrowSelected ? <Check size={11.5} /> : <CalendarDays size={11.5} />}
                            <span>{t.dateTomorrow}</span>
                          </button>
                          <button
                            type="button"
                            className={`chip-btn ${isPlus7Selected ? 'active' : ''}`}
                            aria-pressed={isPlus7Selected}
                            onClick={() => {
                              void localDb.upsertTask({
                                id: tk.id,
                                scheduleState: 'UPCOMING',
                                whenDate: nextWeekEpoch,
                                ...(tk.deadline != null && tk.deadline < nextWeekEpoch
                                  ? { deadline: nextWeekEpoch }
                                  : {})
                              });
                              showFeedback(`${displayTitle} → ${plus7Label}`);
                            }}
                          >
                            {isPlus7Selected && <Check size={11.5} />}
                            <span>{plus7Label}</span>
                          </button>
                          <button
                            type="button"
                            className="chip-btn active"
                            onClick={() => {
                              void localDb.upsertTask({ id: tk.id, status: 'COMPLETED' });
                              showFeedback(`${displayTitle} → ${t.actionComplete}`);
                            }}
                          >
                            <Check size={12} />
                            <span>{t.actionComplete}</span>
                          </button>
                        </div>
                      </div>
                    );
                  })}
                </div>
              )}
            </>
          )}

          {/* STEP 3: PROJECTS REQUIRING ATTENTION / REVIEW */}
          {step === 3 && (
            <>
              {stalledProjects.length === 0 ? (
                <div className="v2-review-empty-card">
                  <Sparkles size={26} style={{ color: 'var(--logbook-green)' }} />
                  <div>{t.weeklyReviewNoStalledProjects}</div>
                </div>
              ) : (
                <div className="v2-review-list">
                  {stalledProjects.map((st) => {
                    const area = st.project.areaId
                      ? areasMap.get(st.project.areaId)
                      : undefined;
                    const draftVal = nextActionDrafts[st.project.id] ?? '';
                    const badgeLabel =
                      st.status.reason === 'no_next_action'
                        ? t.projectStalledBadge
                        : t.projectRequiresReviewBadge;
                    const detailHint =
                      st.status.reason === 'no_next_action'
                        ? t.projectStalledNoActionHint
                        : t.projectStalledInactiveHint(st.status.daysSinceActivity);

                    return (
                      <div key={st.project.id} className="v2-stalled-project-card">
                        <div className="v2-stalled-project-top">
                          <div style={{ minWidth: 0, flex: 1 }}>
                            <div
                              style={{
                                display: 'flex',
                                alignItems: 'center',
                                gap: 8,
                                flexWrap: 'wrap'
                              }}
                            >
                              <button
                                type="button"
                                className="v2-stalled-project-link"
                                onClick={() => {
                                  onClose();
                                  onOpenProject(st.project.id);
                                }}
                              >
                                <Folder size={15} style={{ color: 'var(--things-blue)' }} />
                                <span>{st.project.title}</span>
                              </button>
                              <span className="v2-stalled-pill" title={detailHint}>
                                <AlertTriangle size={11} />
                                <span>{badgeLabel}</span>
                              </span>
                            </div>
                            <div
                              style={{
                                fontSize: 11.6,
                                color: 'var(--text-secondary)',
                                marginTop: 2
                              }}
                            >
                              {area ? `${area.title} · ${detailHint}` : detailHint}
                            </div>
                          </div>

                          <div className="v2-review-actions-wrap">
                            <button
                              type="button"
                              className="chip-btn active"
                              data-testid={`mark-reviewed-${st.project.id}`}
                              onClick={() => {
                                void localDb.markProjectReviewed(st.project.id);
                                showFeedback(`${st.project.title} → ${t.weeklyReviewMarkReviewed}`);
                              }}
                            >
                              <span>{t.weeklyReviewMarkReviewed}</span>
                            </button>
                            <button
                              type="button"
                              className="chip-btn"
                              onClick={() => {
                                void localDb.upsertProject({
                                  id: st.project.id,
                                  scheduleState: 'SOMEDAY',
                                  whenDate: null,
                                  lastReviewedAt: Date.now(),
                                  immediateSync: true
                                });
                                showFeedback(`${st.project.title} → ${t.viewSomeday}`);
                              }}
                            >
                              <Archive size={11.5} />
                              <span>{t.weeklyReviewMoveSomeday}</span>
                            </button>
                          </div>
                        </div>

                        <form
                          className="v2-stalled-inline-form"
                          onSubmit={(e) => {
                            e.preventDefault();
                            void handleAddNextAction(st.project.id, st.project.title);
                          }}
                        >
                          <input
                            type="text"
                            className="text-field"
                            value={draftVal}
                            onChange={(e) =>
                              setNextActionDrafts((prev) => ({
                                ...prev,
                                [st.project.id]: e.target.value
                              }))
                            }
                            placeholder={t.weeklyReviewAddNextActionPlaceholder}
                          />
                          <button
                            type="submit"
                            className="btn-primary"
                            disabled={!draftVal.trim()}
                          >
                            <Plus size={14} />
                            <span>{t.weeklyReviewAddBtn}</span>
                          </button>
                        </form>
                      </div>
                    );
                  })}
                </div>
              )}
            </>
          )}

          {/* STEP 4: SOMEDAY & WRAP-UP */}
          {step === 4 && (
            <>
              <div className="v2-review-summary-card">
                <div style={{ display: 'flex', alignItems: 'center', gap: 10 }}>
                  <CheckCircle2 size={22} style={{ color: 'var(--logbook-green)', flexShrink: 0 }} />
                  <div>
                    <div style={{ fontWeight: 700, fontSize: 14 }}>
                      {t.weeklyReviewCompleted7dSummary(completed7dCount)}
                    </div>
                    {lastWeeklyReviewAt != null && lastWeeklyReviewAt > 0 && (
                      <div style={{ fontSize: 12, color: 'var(--text-secondary)', marginTop: 2 }}>
                        {t.weeklyReviewLastCompleted(
                          new Date(lastWeeklyReviewAt).toLocaleDateString(lang, {
                            month: 'short',
                            day: 'numeric',
                            year: 'numeric'
                          })
                        )}
                      </div>
                    )}
                  </div>
                </div>
              </div>

              {somedayProjects.length === 0 && somedayTasks.length === 0 ? (
                <div className="v2-review-empty-card">
                  <Archive size={24} style={{ color: 'var(--someday-amber)' }} />
                  <div>{t.weeklyReviewNoSomedayItems}</div>
                </div>
              ) : (
                <div className="v2-review-list">
                  {somedayProjects.map((proj) => (
                    <div key={`sd-proj-${proj.id}`} className="v2-review-item-card">
                      <button
                        type="button"
                        onClick={() => {
                          onClose();
                          onOpenProject(proj.id);
                        }}
                        style={{
                          display: 'flex',
                          alignItems: 'center',
                          gap: 8,
                          flex: 1,
                          minWidth: 0,
                          background: 'transparent',
                          border: 'none',
                          padding: 0,
                          textAlign: 'left',
                          color: 'inherit',
                          cursor: 'pointer'
                        }}
                      >
                        <Folder size={15} style={{ color: 'var(--someday-amber)', flexShrink: 0 }} />
                        <span style={{ fontWeight: 650, fontSize: 13.6 }}>{proj.title}</span>
                      </button>
                      <div className="v2-review-actions-wrap">
                        <button
                          type="button"
                          className="chip-btn active"
                          onClick={() => {
                            void localDb.upsertProject({
                              id: proj.id,
                              scheduleState: 'ANYTIME',
                              lastReviewedAt: Date.now()
                            });
                            showFeedback(`${proj.title} → ${t.viewAnytime}`);
                          }}
                        >
                          <Layers size={11.5} />
                          <span>{t.weeklyReviewActivateAnytime}</span>
                        </button>
                      </div>
                    </div>
                  ))}

                  {somedayTasks.map((tk) => {
                    const displayTitle = tk.title || t.placeholderTodoTitle;
                    return (
                      <div key={tk.id} className="v2-review-item-card">
                        <button
                          type="button"
                          onClick={() => {
                            if (onOpenTask) {
                              onClose();
                              onOpenTask(tk);
                            }
                          }}
                          style={{
                            flex: 1,
                            minWidth: 0,
                            fontWeight: 560,
                            fontSize: 13.6,
                            background: 'transparent',
                            border: 'none',
                            padding: 0,
                            textAlign: 'left',
                            color: 'inherit',
                            cursor: onOpenTask ? 'pointer' : 'default'
                          }}
                        >
                          {displayTitle}
                        </button>
                        <div className="v2-review-actions-wrap">
                          <button
                            type="button"
                            className="chip-btn"
                            onClick={() => {
                              void localDb.upsertTask({
                                id: tk.id,
                                scheduleState: 'TODAY',
                                whenDate: todayEpoch
                              });
                              showFeedback(`${displayTitle} → ${t.viewToday}`);
                            }}
                          >
                            <Star
                              size={11.5}
                              fill="var(--today-star)"
                              style={{ color: 'var(--today-star)' }}
                            />
                            <span>{t.weeklyReviewActivateToday}</span>
                          </button>
                          <button
                            type="button"
                            className="chip-btn"
                            onClick={() => {
                              void localDb.upsertTask({
                                id: tk.id,
                                scheduleState: 'ANYTIME',
                                whenDate: null
                              });
                              showFeedback(`${displayTitle} → ${t.viewAnytime}`);
                            }}
                          >
                            <Layers size={11.5} />
                            <span>{t.weeklyReviewActivateAnytime}</span>
                          </button>
                        </div>
                      </div>
                    );
                  })}
                </div>
              )}
            </>
          )}
        </div>

        {/* Footer Navigation */}
        <div className="v2-review-footer">
          <button
            type="button"
            className="btn-secondary"
            disabled={step === 1}
            onClick={() => setStep((s) => Math.max(1, s - 1) as WeeklyReviewStep)}
          >
            <ArrowLeft size={14} />
            <span>{t.weeklyReviewPrevStep}</span>
          </button>

          {step < 4 ? (
            <button
              type="button"
              className="btn-primary"
              onClick={() => setStep((s) => Math.min(4, s + 1) as WeeklyReviewStep)}
            >
              <span>{t.weeklyReviewNextStep}</span>
              <ArrowRight size={14} />
            </button>
          ) : (
            <button
              type="button"
              className="btn-primary"
              data-testid="complete-weekly-review-btn"
              onClick={onCompleteReview}
            >
              <CheckCircle2 size={15} />
              <span>{t.weeklyReviewCompleteBtn}</span>
            </button>
          )}
        </div>
      </div>
    </div>
  );
};
