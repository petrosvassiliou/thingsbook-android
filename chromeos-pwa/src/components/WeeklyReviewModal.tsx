import React, { useMemo, useState } from 'react';
import {
  AlertTriangle,
  Archive,
  ArrowLeft,
  ArrowRight,
  CalendarDays,
  Check,
  CheckCircle2,
  Compass,
  Flag,
  Folder,
  Inbox,
  Layers,
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
  onCompleteReview,
  onClose
}) => {
  const [step, setStep] = useState<WeeklyReviewStep>(initialStep);
  const [nextActionDrafts, setNextActionDrafts] = useState<Record<string, string>>({});

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

  // Step 2: Overdue or Due-Soon Tasks
  const urgentTasks = useMemo(
    () =>
      tasks
        .filter((tk) => {
          if (tk.isDeleted || tk.status !== 'OPEN') return false;
          if (tk.projectId && !activeProjectIds.has(tk.projectId)) return false;
          const whenOverdue = tk.whenDate != null && daysBetweenToday(tk.whenDate) < 0;
          const deadlineDueSoonOrOverdue =
            tk.deadline != null && daysBetweenToday(tk.deadline) <= 3;
          return whenOverdue || deadlineDueSoonOrOverdue;
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

  const handleAddNextAction = async (projectId: string) => {
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
              <Compass size={19} style={{ color: 'var(--things-blue)' }} />
              <span>{t.weeklyReviewTitle}</span>
            </h3>
            <div style={{ fontSize: 12.2, color: 'var(--text-secondary)', marginTop: 2 }}>
              {t.weeklyReviewSubtitle}
            </div>
          </div>
          <button type="button" className="icon-btn" onClick={onClose}>
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

        <div className="v2-review-step-banner">
          <div style={{ fontWeight: 650, fontSize: 13.8 }}>{activeMeta.title}</div>
          <div style={{ fontSize: 12.2, color: 'var(--text-secondary)', marginTop: 2 }}>
            {activeMeta.desc}
          </div>
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
                  {inboxTasks.map((tk) => (
                    <div key={tk.id} className="v2-review-item-card">
                      <div style={{ flex: 1, minWidth: 0 }}>
                        <div style={{ fontWeight: 600, fontSize: 13.8 }}>
                          {tk.title || t.placeholderTodoTitle}
                        </div>
                        {tk.notes.trim() && (
                          <div className="v2-review-item-notes">
                            {tk.notes.trim().split('\n')[0]}
                          </div>
                        )}
                      </div>
                      <div className="v2-review-actions-wrap">
                        <button
                          type="button"
                          className="chip-btn"
                          onClick={() =>
                            void localDb.upsertTask({
                              id: tk.id,
                              scheduleState: 'TODAY',
                              whenDate: todayEpoch
                            })
                          }
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
                          onClick={() =>
                            void localDb.upsertTask({
                              id: tk.id,
                              scheduleState: 'THIS_EVENING',
                              whenDate: todayEpoch
                            })
                          }
                        >
                          <Moon size={11.5} />
                          <span>{t.viewThisEvening}</span>
                        </button>
                        <button
                          type="button"
                          className="chip-btn"
                          onClick={() =>
                            void localDb.upsertTask({
                              id: tk.id,
                              scheduleState: 'UPCOMING',
                              whenDate: tomorrowEpoch
                            })
                          }
                        >
                          <CalendarDays size={11.5} />
                          <span>{t.dateTomorrow}</span>
                        </button>
                        <button
                          type="button"
                          className="chip-btn"
                          onClick={() =>
                            void localDb.upsertTask({
                              id: tk.id,
                              scheduleState: 'SOMEDAY',
                              whenDate: null
                            })
                          }
                        >
                          <Archive size={11.5} />
                          <span>{t.viewSomeday}</span>
                        </button>
                        <button
                          type="button"
                          className="chip-btn active"
                          title={t.actionComplete}
                          onClick={() =>
                            void localDb.upsertTask({ id: tk.id, status: 'COMPLETED' })
                          }
                        >
                          <Check size={12} />
                        </button>
                        <button
                          type="button"
                          className="chip-btn urgent"
                          title={t.actionDelete}
                          onClick={() => void localDb.softDeleteTasks([tk.id])}
                        >
                          <Trash2 size={12} />
                        </button>
                      </div>
                    </div>
                  ))}
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
                    const proj = tk.projectId ? projectsMap.get(tk.projectId) : undefined;
                    const area = proj?.areaId
                      ? areasMap.get(proj.areaId)
                      : tk.areaId
                        ? areasMap.get(tk.areaId)
                        : undefined;
                    const ctxLabel = proj ? proj.title : area ? area.title : '';
                    const dlDiff =
                      tk.deadline != null ? daysBetweenToday(tk.deadline) : null;

                    return (
                      <div key={tk.id} className="v2-review-item-card">
                        <div style={{ flex: 1, minWidth: 0 }}>
                          <div style={{ fontWeight: 600, fontSize: 13.8 }}>
                            {tk.title || t.placeholderTodoTitle}
                          </div>
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
                            {tk.whenDate != null && daysBetweenToday(tk.whenDate) < 0 && (
                              <span className="meta-badge">
                                <CalendarDays size={10} />
                                <span>{formatShortDate(tk.whenDate)}</span>
                              </span>
                            )}
                          </div>
                        </div>

                        <div className="v2-review-actions-wrap">
                          <button
                            type="button"
                            className="chip-btn"
                            onClick={() =>
                              void localDb.upsertTask({
                                id: tk.id,
                                scheduleState: 'TODAY',
                                whenDate: todayEpoch
                              })
                            }
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
                            onClick={() =>
                              void localDb.upsertTask({
                                id: tk.id,
                                scheduleState: 'UPCOMING',
                                whenDate: tomorrowEpoch,
                                ...(tk.deadline != null && daysBetweenToday(tk.deadline) < 0
                                  ? { deadline: tomorrowEpoch }
                                  : {})
                              })
                            }
                          >
                            <CalendarDays size={11.5} />
                            <span>{t.dateTomorrow}</span>
                          </button>
                          <button
                            type="button"
                            className="chip-btn"
                            onClick={() =>
                              void localDb.upsertTask({
                                id: tk.id,
                                scheduleState: 'UPCOMING',
                                whenDate: nextWeekEpoch,
                                ...(tk.deadline != null && daysBetweenToday(tk.deadline) < 0
                                  ? { deadline: nextWeekEpoch }
                                  : {})
                              })
                            }
                          >
                            <span>+7d</span>
                          </button>
                          <button
                            type="button"
                            className="chip-btn active"
                            onClick={() =>
                              void localDb.upsertTask({ id: tk.id, status: 'COMPLETED' })
                            }
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

          {/* STEP 3: STALLED PROJECTS */}
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
                              <span className="v2-stalled-pill">
                                <AlertTriangle size={11} />
                                <span>
                                  {st.status.reason === 'no_next_action'
                                    ? t.projectStalledNoActionHint
                                    : t.projectStalledInactiveHint(st.status.daysSinceActivity)}
                                </span>
                              </span>
                            </div>
                            {area && (
                              <div
                                style={{
                                  fontSize: 11.6,
                                  color: 'var(--text-secondary)',
                                  marginTop: 2
                                }}
                              >
                                {area.title}
                              </div>
                            )}
                          </div>

                          <div className="v2-review-actions-wrap">
                            <button
                              type="button"
                              className="chip-btn active"
                              data-testid={`mark-reviewed-${st.project.id}`}
                              onClick={() =>
                                void localDb.markProjectReviewed(st.project.id)
                              }
                            >
                              <span>{t.weeklyReviewMarkReviewed}</span>
                            </button>
                            <button
                              type="button"
                              className="chip-btn"
                              onClick={() =>
                                void localDb.upsertProject({
                                  id: st.project.id,
                                  scheduleState: 'SOMEDAY',
                                  whenDate: null
                                })
                              }
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
                            void handleAddNextAction(st.project.id);
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
                      <div style={{ display: 'flex', alignItems: 'center', gap: 8, flex: 1, minWidth: 0 }}>
                        <Folder size={15} style={{ color: 'var(--someday-amber)', flexShrink: 0 }} />
                        <span style={{ fontWeight: 650, fontSize: 13.6 }}>{proj.title}</span>
                      </div>
                      <div className="v2-review-actions-wrap">
                        <button
                          type="button"
                          className="chip-btn active"
                          onClick={() =>
                            void localDb.upsertProject({
                              id: proj.id,
                              scheduleState: 'ANYTIME',
                              lastReviewedAt: Date.now()
                            })
                          }
                        >
                          <Layers size={11.5} />
                          <span>{t.weeklyReviewActivateAnytime}</span>
                        </button>
                      </div>
                    </div>
                  ))}

                  {somedayTasks.map((tk) => (
                    <div key={tk.id} className="v2-review-item-card">
                      <div style={{ flex: 1, minWidth: 0, fontWeight: 560, fontSize: 13.6 }}>
                        {tk.title || t.placeholderTodoTitle}
                      </div>
                      <div className="v2-review-actions-wrap">
                        <button
                          type="button"
                          className="chip-btn"
                          onClick={() =>
                            void localDb.upsertTask({
                              id: tk.id,
                              scheduleState: 'TODAY',
                              whenDate: todayEpoch
                            })
                          }
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
                          onClick={() =>
                            void localDb.upsertTask({
                              id: tk.id,
                              scheduleState: 'ANYTIME',
                              whenDate: null
                            })
                          }
                        >
                          <Layers size={11.5} />
                          <span>{t.weeklyReviewActivateAnytime}</span>
                        </button>
                      </div>
                    </div>
                  ))}
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
