import React, { useMemo, useState } from 'react';
import {
  Check,
  Clock,
  FolderPlus,
  Minus,
  Plus,
  Sparkles,
  Wand2,
  X
} from 'lucide-react';
import {
  addDaysLocal,
  daysBetweenToday,
  startOfDayEpoch,
  type AreaRecord,
  type CalendarEventItem,
  type SupportedLang,
  type TaskRecord
} from '../domain/types';
import {
  DEFAULT_TIMEBLOCK_DURATION_MINUTES,
  DEFAULT_WORKDAY_END_MINUTES,
  DEFAULT_WORKDAY_START_MINUTES,
  formatTimeblockRange,
  normalizeWorkdayBounds
} from '../domain/v2Utils';
import {
  generateDailyTimeblockPlan,
  generateProjectBlueprint,
  parseBrainDumpToTasks,
  type DayTimeblockItemDto
} from '../data/geminiService';
import { localDb } from '../data/localDb';
import type { TranslationStrings } from '../i18n/translations';

export type GeminiModalTab = 'braindump' | 'architect' | 'planday';

interface GeminiModalProps {
  initialTab?: GeminiModalTab;
  areas: AreaRecord[];
  defaultAreaId: string | null;
  todayOpenTasks?: TaskRecord[];
  todayCalendarEvents?: CalendarEventItem[];
  workdayStartMinutes?: number;
  workdayEndMinutes?: number;
  lang: SupportedLang;
  geminiApiKey: string;
  t: TranslationStrings;
  onNavigateProject: (projectId: string) => void;
  onAppliedDayPlan?: () => void;
  onToast: (msg: string) => void;
  onClose: () => void;
}

export const GeminiModal: React.FC<GeminiModalProps> = ({
  initialTab = 'braindump',
  areas,
  defaultAreaId,
  todayOpenTasks = [],
  todayCalendarEvents = [],
  workdayStartMinutes = DEFAULT_WORKDAY_START_MINUTES,
  workdayEndMinutes = DEFAULT_WORKDAY_END_MINUTES,
  lang,
  geminiApiKey,
  t,
  onNavigateProject,
  onAppliedDayPlan,
  onToast,
  onClose
}) => {
  const [tab, setTab] = useState<GeminiModalTab>(initialTab);
  const [brainDumpText, setBrainDumpText] = useState('');
  const [projectGoal, setProjectGoal] = useState('');
  const [selectedAreaId, setSelectedAreaId] = useState<string>(defaultAreaId || '');
  const [loading, setLoading] = useState(false);

  // v3.1 Plan My Day state
  const [includeScheduled, setIncludeScheduled] = useState(false);
  const [planSummary, setPlanSummary] = useState('');
  const [proposedBlocks, setProposedBlocks] = useState<DayTimeblockItemDto[]>([]);
  const [checkedTaskIds, setCheckedTaskIds] = useState<Set<string>>(() => new Set());

  const workdayBounds = useMemo(
    () => normalizeWorkdayBounds(workdayStartMinutes, workdayEndMinutes),
    [workdayStartMinutes, workdayEndMinutes]
  );

  const openTodayTasksById = useMemo(() => {
    const map = new Map<string, TaskRecord>();
    for (const tk of todayOpenTasks) {
      if (!tk.isDeleted && tk.status === 'OPEN') {
        map.set(tk.id, tk);
      }
    }
    return map;
  }, [todayOpenTasks]);

  const schedulableTodayTasks = useMemo(
    () =>
      todayOpenTasks.filter(
        (tk) =>
          !tk.isDeleted &&
          tk.status === 'OPEN' &&
          (includeScheduled || tk.scheduledStartMinutes == null)
      ),
    [todayOpenTasks, includeScheduled]
  );

  const handleBrainDump = async () => {
    if (!brainDumpText.trim() || loading) return;
    setLoading(true);
    try {
      const parsed = await parseBrainDumpToTasks(brainDumpText, lang, geminiApiKey);
      const todayEpoch = startOfDayEpoch();
      for (let i = 0; i < parsed.length; i++) {
        const item = parsed[i];
        const whenDate =
          item.whenDaysOffset != null ? addDaysLocal(todayEpoch, item.whenDaysOffset) : null;
        const deadline =
          item.deadlineDaysOffset != null ? addDaysLocal(todayEpoch, item.deadlineDaysOffset) : null;
        const created = await localDb.upsertTask({
          title: item.title,
          notes: item.notes,
          scheduleState: item.scheduleState,
          whenDate,
          deadline,
          areaId: selectedAreaId || null,
          sortOrder: (i + 1) * 100
        });
        if (item.checklist.length > 0) {
          await localDb.addChecklistItems(created.id, item.checklist);
        }
      }
      onToast(`${parsed.length} ${t.entityTodos}`);
      onClose();
    } catch (err) {
      onToast(err instanceof Error ? err.message : t.geminiErrorFallback);
    } finally {
      setLoading(false);
    }
  };

  const handleProjectArchitect = async () => {
    if (!projectGoal.trim() || loading) return;
    setLoading(true);
    try {
      const bp = await generateProjectBlueprint(projectGoal, lang, geminiApiKey);
      const projectId = await localDb.createV3RoadmapProjectWithBlueprint(
        bp,
        selectedAreaId || null
      );
      onNavigateProject(projectId);
      onClose();
    } catch (err) {
      onToast(err instanceof Error ? err.message : t.geminiErrorFallback);
    } finally {
      setLoading(false);
    }
  };

  const handleGenerateDayPlan = async () => {
    if (schedulableTodayTasks.length === 0 || loading) return;
    setLoading(true);
    try {
      const dayStartMs = startOfDayEpoch();
      const dayEndMs = dayStartMs + 86_400_000;
      const busyIntervals: Array<[number, number]> = [];

      for (const ev of todayCalendarEvents) {
        if (ev.isAllDay) continue;
        const sMs = Math.max(dayStartMs, ev.startMillis);
        const eMs = Math.min(dayEndMs, Math.max(ev.startMillis + 60_000, ev.endMillis));
        if (eMs <= dayStartMs || sMs >= dayEndMs) continue;
        const sD = new Date(sMs);
        const sMin = sD.getHours() * 60 + sD.getMinutes();
        const durMin = Math.max(5, Math.round((eMs - sMs) / 60_000));
        busyIntervals.push([sMin, Math.min(1440, sMin + durMin)]);
      }

      if (!includeScheduled) {
        for (const tk of todayOpenTasks) {
          if (tk.isDeleted || tk.status !== 'OPEN' || tk.scheduledStartMinutes == null) continue;
          const sMin = Math.max(0, Math.min(1439, Math.trunc(tk.scheduledStartMinutes)));
          const durMin =
            tk.durationMinutes != null && tk.durationMinutes > 0
              ? Math.min(480, Math.trunc(tk.durationMinutes))
              : DEFAULT_TIMEBLOCK_DURATION_MINUTES;
          busyIntervals.push([sMin, Math.min(1440, sMin + durMin)]);
        }
      }

      const candidates = schedulableTodayTasks.map((tk) => ({
        taskId: tk.id,
        title: tk.title || t.placeholderTodoTitle,
        durationMinutes:
          tk.durationMinutes != null && tk.durationMinutes > 0
            ? Math.min(240, Math.trunc(tk.durationMinutes))
            : DEFAULT_TIMEBLOCK_DURATION_MINUTES,
        effortLevel: tk.effortLevel ?? null,
        isPinned: Boolean(tk.isPinned),
        hasTodayDeadline: tk.deadline != null && daysBetweenToday(tk.deadline) <= 0
      }));

      const plan = await generateDailyTimeblockPlan({
        candidates,
        busyIntervalsMinutes: busyIntervals,
        workdayStartMinutes: workdayBounds.startMinutes,
        workdayEndMinutes: workdayBounds.endMinutes,
        earliestStartMinutes: workdayBounds.startMinutes,
        lang,
        customApiKey: geminiApiKey
      });

      setPlanSummary(plan.summary);
      setProposedBlocks(plan.blocks);
      setCheckedTaskIds(new Set(plan.blocks.map((b) => b.taskId)));
    } catch (err) {
      onToast(err instanceof Error ? err.message : t.geminiErrorFallback);
    } finally {
      setLoading(false);
    }
  };

  const shiftProposedBlock = (taskId: string, deltaMinutes: number) => {
    setProposedBlocks((prev) =>
      prev.map((item) => {
        if (item.taskId !== taskId) return item;
        const maxStart = Math.max(0, 1440 - Math.max(15, item.durationMinutes));
        const nextStart = Math.max(0, Math.min(maxStart, item.startMinutes + deltaMinutes));
        return { ...item, startMinutes: nextStart };
      })
    );
  };

  const toggleBlockChecked = (taskId: string) => {
    setCheckedTaskIds((prev) => {
      const next = new Set(prev);
      if (next.has(taskId)) next.delete(taskId);
      else next.add(taskId);
      return next;
    });
  };

  const selectedBlocks = useMemo(
    () => proposedBlocks.filter((b) => checkedTaskIds.has(b.taskId)),
    [proposedBlocks, checkedTaskIds]
  );

  const handleApplyDayPlan = async () => {
    if (selectedBlocks.length === 0 || loading) return;
    setLoading(true);
    try {
      await localDb.applyBatchTaskTimeblocks(
        selectedBlocks.map((b) => ({
          taskId: b.taskId,
          scheduledStartMinutes: b.startMinutes,
          durationMinutes: b.durationMinutes
        }))
      );
      onAppliedDayPlan?.();
      onClose();
    } catch (err) {
      onToast(err instanceof Error ? err.message : t.geminiErrorFallback);
    } finally {
      setLoading(false);
    }
  };

  const activeAreas = areas.filter((a) => !a.isDeleted);

  return (
    <div className="modal-backdrop" onClick={onClose}>
      <div className="modal-panel wide" onClick={(e) => e.stopPropagation()}>
        <div className="modal-header">
          <h3 className="modal-title" style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
            <Sparkles size={19} style={{ color: 'var(--gemini-violet)' }} />
            <span>{t.geminiHubTitle}</span>
          </h3>
          <button type="button" className="icon-btn" title={t.actionDone} onClick={onClose}>
            <X size={18} />
          </button>
        </div>

        <div style={{ display: 'flex', gap: 8, marginBottom: 14, flexWrap: 'wrap' }}>
          <button
            type="button"
            data-testid="gemini-tab-braindump"
            className={`btn-secondary ${tab === 'braindump' ? 'active' : ''}`}
            style={{
              flex: 1,
              borderColor: tab === 'braindump' ? 'var(--gemini-violet)' : undefined,
              color: tab === 'braindump' ? 'var(--gemini-violet)' : undefined
            }}
            onClick={() => setTab('braindump')}
          >
            <Wand2 size={15} />
            <span>{t.geminiBrainDumpTitle}</span>
          </button>
          <button
            type="button"
            data-testid="gemini-tab-architect"
            className={`btn-secondary ${tab === 'architect' ? 'active' : ''}`}
            style={{
              flex: 1,
              borderColor: tab === 'architect' ? 'var(--gemini-violet)' : undefined,
              color: tab === 'architect' ? 'var(--gemini-violet)' : undefined
            }}
            onClick={() => setTab('architect')}
          >
            <FolderPlus size={15} />
            <span>{t.geminiArchitectTitle}</span>
          </button>
          <button
            type="button"
            data-testid="gemini-tab-planday"
            className={`btn-secondary ${tab === 'planday' ? 'active' : ''}`}
            style={{
              flex: 1,
              borderColor: tab === 'planday' ? 'var(--gemini-violet)' : undefined,
              color: tab === 'planday' ? 'var(--gemini-violet)' : undefined
            }}
            onClick={() => setTab('planday')}
          >
            <Clock size={15} />
            <span>{t.v31GeminiPlanDayTab}</span>
          </button>
        </div>

        {tab !== 'planday' && activeAreas.length > 0 && (
          <div style={{ marginBottom: 12 }}>
            <label
              style={{
                display: 'block',
                fontSize: 12,
                color: 'var(--text-secondary)',
                marginBottom: 4
              }}
            >
              {t.geminiAreaOptionalLabel}
            </label>
            <select
              className="text-field"
              value={selectedAreaId}
              onChange={(e) => setSelectedAreaId(e.target.value)}
            >
              <option value="">
                — {t.viewInbox} / {t.noProjectNoArea} —
              </option>
              {activeAreas.map((a) => (
                <option key={a.id} value={a.id}>
                  {a.title}
                </option>
              ))}
            </select>
          </div>
        )}

        {tab === 'braindump' && (
          <div>
            <textarea
              className="text-field"
              rows={5}
              autoFocus
              value={brainDumpText}
              onChange={(e) => setBrainDumpText(e.target.value)}
              placeholder={t.geminiBrainDumpPlaceholder}
            />
            <div style={{ display: 'flex', justifyContent: 'flex-end', gap: 8, marginTop: 14 }}>
              <button type="button" className="btn-secondary" onClick={onClose}>
                {t.actionCancel}
              </button>
              <button
                type="button"
                className="btn-primary"
                style={{ background: 'var(--gemini-violet)' }}
                disabled={!brainDumpText.trim() || loading}
                onClick={handleBrainDump}
              >
                <Sparkles size={15} />
                <span>{loading ? t.geminiGenerating : t.geminiBrainDumpAction}</span>
              </button>
            </div>
          </div>
        )}

        {tab === 'architect' && (
          <div>
            <div
              className="v31-gemini-hint-banner"
              data-testid="gemini-architect-v3-hint"
            >
              <Sparkles size={13.5} style={{ color: 'var(--gemini-violet)', flexShrink: 0 }} />
              <span>{t.v31GeminiArchitectV3Hint}</span>
            </div>
            <textarea
              className="text-field"
              rows={4}
              autoFocus
              value={projectGoal}
              onChange={(e) => setProjectGoal(e.target.value)}
              placeholder={t.geminiArchitectPlaceholder}
            />
            <div style={{ display: 'flex', justifyContent: 'flex-end', gap: 8, marginTop: 14 }}>
              <button type="button" className="btn-secondary" onClick={onClose}>
                {t.actionCancel}
              </button>
              <button
                type="button"
                className="btn-primary"
                style={{ background: 'var(--gemini-violet)' }}
                disabled={!projectGoal.trim() || loading}
                onClick={handleProjectArchitect}
              >
                <FolderPlus size={15} />
                <span>{loading ? t.geminiGenerating : t.geminiArchitectAction}</span>
              </button>
            </div>
          </div>
        )}

        {tab === 'planday' && (
          <div data-testid="gemini-planday-panel">
            <p
              style={{
                margin: '0 0 10px',
                fontSize: 12.6,
                lineHeight: 1.45,
                color: 'var(--text-secondary)'
              }}
            >
              {t.v31GeminiPlanDayDesc}
            </p>

            <div
              style={{
                display: 'flex',
                alignItems: 'center',
                justifyContent: 'space-between',
                gap: 10,
                padding: '8px 11px',
                borderRadius: 'var(--radius-sm)',
                background: 'var(--bg-subtle)',
                border: '1px solid var(--border-subtle)',
                marginBottom: 12
              }}
            >
              <span style={{ fontSize: 12.8, fontWeight: 560 }}>{t.v31GeminiPlanDayReplan}</span>
              <button
                type="button"
                data-testid="gemini-planday-replan-toggle"
                aria-label={t.v31GeminiPlanDayReplan}
                aria-pressed={includeScheduled}
                className={`toggle-switch ${includeScheduled ? 'on' : ''}`}
                onClick={() => {
                  setIncludeScheduled((v) => !v);
                  setProposedBlocks([]);
                  setPlanSummary('');
                }}
              >
                <span className="toggle-switch-knob" />
              </button>
            </div>

            {schedulableTodayTasks.length === 0 ? (
              <div
                className="v2-templates-empty"
                data-testid="gemini-planday-empty"
                style={{ marginBottom: 12 }}
              >
                {t.v31GeminiPlanDayEmpty}
              </div>
            ) : (
              <div style={{ display: 'flex', justifyContent: 'flex-start', marginBottom: 12 }}>
                <button
                  type="button"
                  className="btn-secondary"
                  data-testid="gemini-planday-generate-btn"
                  disabled={loading}
                  onClick={handleGenerateDayPlan}
                >
                  <Sparkles size={14} style={{ color: 'var(--gemini-violet)' }} />
                  <span>{loading ? t.geminiGenerating : t.v31GeminiPlanDayGenerate}</span>
                </button>
              </div>
            )}

            {proposedBlocks.length > 0 && (
              <div className="v31-planday-preview-wrap" data-testid="gemini-planday-preview-list">
                {planSummary && (
                  <div className="v31-planday-summary">{planSummary}</div>
                )}

                <div className="v31-planday-blocks-scroll">
                  {proposedBlocks.map((block) => {
                    const task = openTodayTasksById.get(block.taskId);
                    const checked = checkedTaskIds.has(block.taskId);
                    const rangeLabel = formatTimeblockRange(
                      block.startMinutes,
                      block.durationMinutes
                    );
                    return (
                      <div
                        key={block.taskId}
                        className={`v31-planday-row ${checked ? 'checked' : 'unchecked'}`}
                        data-testid={`gemini-planday-row-${block.taskId}`}
                      >
                        <button
                          type="button"
                          className={`things-checkbox ${checked ? 'completed checked' : ''}`}
                          aria-pressed={checked}
                          onClick={() => toggleBlockChecked(block.taskId)}
                        >
                          {checked && <Check size={11.5} strokeWidth={3} />}
                        </button>

                        <div className="v31-planday-row-body">
                          <div className="v31-planday-row-top">
                            <span className="meta-badge v2-timeblock-pill">{rangeLabel}</span>
                            <span className="v31-planday-row-title">
                              {task?.title || t.placeholderTodoTitle}
                            </span>
                          </div>
                          {block.reason && (
                            <div className="v31-planday-row-reason">{block.reason}</div>
                          )}
                        </div>

                        <div className="v31-planday-shift-btns">
                          <button
                            type="button"
                            className="chip-btn"
                            data-testid={`gemini-planday-minus15-${block.taskId}`}
                            onClick={() => shiftProposedBlock(block.taskId, -15)}
                          >
                            <Minus size={11} />
                            <span>15m</span>
                          </button>
                          <button
                            type="button"
                            className="chip-btn"
                            data-testid={`gemini-planday-plus15-${block.taskId}`}
                            onClick={() => shiftProposedBlock(block.taskId, 15)}
                          >
                            <Plus size={11} />
                            <span>15m</span>
                          </button>
                        </div>
                      </div>
                    );
                  })}
                </div>
              </div>
            )}

            <div style={{ display: 'flex', justifyContent: 'flex-end', gap: 8, marginTop: 14 }}>
              <button type="button" className="btn-secondary" onClick={onClose}>
                {t.actionCancel}
              </button>
              {proposedBlocks.length > 0 && (
                <button
                  type="button"
                  className="btn-primary"
                  data-testid="gemini-planday-apply-btn"
                  style={{ background: 'var(--gemini-violet)' }}
                  disabled={selectedBlocks.length === 0 || loading}
                  onClick={handleApplyDayPlan}
                >
                  <Sparkles size={15} />
                  <span>{t.v31GeminiPlanDayApply(selectedBlocks.length)}</span>
                </button>
              )}
            </div>
          </div>
        )}
      </div>
    </div>
  );
};
