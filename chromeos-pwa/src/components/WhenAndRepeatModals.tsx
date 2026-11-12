import React, { useMemo, useState } from 'react';
import {
  Archive,
  Bell,
  Calendar,
  CalendarDays,
  ChevronLeft,
  ChevronRight,
  Flag,
  Inbox,
  Moon,
  Repeat,
  Sparkles,
  Star,
  X
} from 'lucide-react';
import { parseNaturalDate } from '../domain/naturalDateParser';
import {
  addDaysLocal,
  daysBetweenToday,
  endOfDayEpoch,
  formatRecurrenceShort,
  fromIsoDateInputValue,
  isoDayOfWeek,
  resolveWhenPreset,
  startOfDayEpoch,
  toIsoDateInputValue,
  type RecurrenceMode,
  type RecurrenceRule,
  type RecurrenceUnit,
  type ScheduleState,
  type SupportedLang,
  type TaskRecord
} from '../domain/types';
import type { TranslationStrings } from '../i18n/translations';

const REMINDER_PRESETS = ['08:00', '09:00', '12:00', '15:00', '18:00', '20:00'];

interface InteractiveMonthCalendarProps {
  selectedEpoch: number | null;
  lang: SupportedLang;
  accent?: 'blue' | 'deadline';
  onSelectDay: (epochMidnight: number) => void;
}

const InteractiveMonthCalendar: React.FC<InteractiveMonthCalendarProps> = ({
  selectedEpoch,
  lang,
  accent = 'blue',
  onSelectDay
}) => {
  const todayEpoch = startOfDayEpoch();
  const initialRefDate = useMemo(
    () => (selectedEpoch != null ? new Date(selectedEpoch) : new Date(todayEpoch)),
    // eslint-disable-next-line react-hooks/exhaustive-deps
    []
  );
  const [viewYear, setViewYear] = useState(initialRefDate.getFullYear());
  const [viewMonth, setViewMonth] = useState(initialRefDate.getMonth()); // 0-indexed

  const weekdayHeaders = useMemo(() => {
    // 2024-01-01 was a Monday
    const fmt = new Intl.DateTimeFormat(lang, { weekday: 'short' });
    return Array.from({ length: 7 }, (_, i) => {
      const raw = fmt.format(new Date(2024, 0, 1 + i)).replace(/\.$/, '');
      return raw.slice(0, 2);
    });
  }, [lang]);

  const monthTitle = useMemo(() => {
    const raw = new Intl.DateTimeFormat(lang, { month: 'long', year: 'numeric' }).format(
      new Date(viewYear, viewMonth, 1)
    );
    return raw.charAt(0).toUpperCase() + raw.slice(1);
  }, [lang, viewYear, viewMonth]);

  const firstOfMonth = new Date(viewYear, viewMonth, 1, 0, 0, 0, 0);
  const leadingBlanks = isoDayOfWeek(firstOfMonth) - 1; // 0 for Monday .. 6 for Sunday
  const daysInMonth = new Date(viewYear, viewMonth + 1, 0).getDate();
  const selectedDayMidnight = selectedEpoch != null ? startOfDayEpoch(new Date(selectedEpoch)) : null;

  const shiftMonth = (delta: number) => {
    const d = new Date(viewYear, viewMonth + delta, 1);
    setViewYear(d.getFullYear());
    setViewMonth(d.getMonth());
  };

  return (
    <div className={`things-calendar ${accent === 'deadline' ? 'deadline-accent' : ''}`}>
      <div className="things-calendar-header">
        <button
          type="button"
          className="icon-btn"
          style={{ height: 28, minWidth: 28, padding: 0 }}
          onClick={() => shiftMonth(-1)}
          aria-label="Previous month"
        >
          <ChevronLeft size={16} />
        </button>
        <span className="things-calendar-month-label">{monthTitle}</span>
        <button
          type="button"
          className="icon-btn"
          style={{ height: 28, minWidth: 28, padding: 0 }}
          onClick={() => shiftMonth(1)}
          aria-label="Next month"
        >
          <ChevronRight size={16} />
        </button>
      </div>

      <div className="things-calendar-weekdays">
        {weekdayHeaders.map((wd, i) => (
          <span key={i} className="things-calendar-weekday">
            {wd}
          </span>
        ))}
      </div>

      <div className="things-calendar-grid">
        {Array.from({ length: leadingBlanks }, (_, idx) => (
          <span key={`blank-${idx}`} />
        ))}
        {Array.from({ length: daysInMonth }, (_, idx) => {
          const dayNum = idx + 1;
          const cellEpoch = new Date(viewYear, viewMonth, dayNum, 0, 0, 0, 0).getTime();
          const isSelected = selectedDayMidnight === cellEpoch;
          const isToday = todayEpoch === cellEpoch;
          return (
            <button
              key={dayNum}
              type="button"
              className={`things-calendar-day ${isSelected ? 'selected' : ''} ${
                isToday && !isSelected ? 'today' : ''
              }`}
              onClick={() => onSelectDay(cellEpoch)}
            >
              {dayNum}
            </button>
          );
        })}
      </div>
    </div>
  );
};

interface WhenModalProps {
  task: TaskRecord;
  lang: SupportedLang;
  t: TranslationStrings;
  onApply: (patch: {
    scheduleState: ScheduleState;
    whenDate: number | null;
    reminderTime: number | null;
  }) => void;
  onOpenDeadline?: () => void;
  onClose: () => void;
}

export const WhenModal: React.FC<WhenModalProps> = ({
  task,
  lang,
  t,
  onApply,
  onOpenDeadline,
  onClose
}) => {
  const [nlpText, setNlpText] = useState('');
  const [dateVal, setDateVal] = useState(() => toIsoDateInputValue(task.whenDate));
  const [reminderClock, setReminderClock] = useState(() => {
    if (!task.reminderTime) return '';
    const d = new Date(task.reminderTime);
    return `${String(d.getHours()).padStart(2, '0')}:${String(d.getMinutes()).padStart(2, '0')}`;
  });

  const parsedNlp = nlpText.trim() ? parseNaturalDate(nlpText, lang) : null;

  const combineReminderWithDay = (dayEpoch: number | null): number | null => {
    if (!reminderClock || dayEpoch == null) return null;
    const [hh, mm] = reminderClock.split(':').map(Number);
    if (Number.isNaN(hh) || Number.isNaN(mm)) return null;
    const d = new Date(dayEpoch);
    return new Date(d.getFullYear(), d.getMonth(), d.getDate(), hh, mm, 0, 0).getTime();
  };

  const applyPreset = (preset: ScheduleState) => {
    const resolved = resolveWhenPreset(preset, reminderClock);
    onApply(resolved);
    onClose();
  };

  const applyEpochDate = (epochMidnight: number) => {
    const todayEnd = endOfDayEpoch();
    const state: ScheduleState = epochMidnight <= todayEnd ? 'TODAY' : 'UPCOMING';
    onApply({
      scheduleState: state,
      whenDate: epochMidnight,
      reminderTime: combineReminderWithDay(epochMidnight)
    });
    onClose();
  };

  const handleNlpEnter = (e: React.FormEvent) => {
    e.preventDefault();
    if (!parsedNlp) return;
    onApply({
      scheduleState: parsedNlp.scheduleState,
      whenDate: parsedNlp.whenDateEpochMillis,
      reminderTime: combineReminderWithDay(parsedNlp.whenDateEpochMillis ?? startOfDayEpoch())
    });
    onClose();
  };

  const handleCalendarApply = () => {
    const epoch = fromIsoDateInputValue(dateVal);
    if (epoch == null) return;
    applyEpochDate(epoch);
  };

  const deadlineSummary = useMemo(() => {
    if (task.deadline == null) return null;
    const shortDate = new Intl.DateTimeFormat(lang, { month: 'short', day: 'numeric' }).format(
      new Date(task.deadline)
    );
    const diff = daysBetweenToday(task.deadline);
    const rel =
      diff === 0
        ? t.deadlineToday
        : diff > 0
          ? t.deadlineDaysLeft(diff)
          : t.deadlineDaysOverdue(Math.abs(diff));
    return `${shortDate} · ${rel}`;
  }, [task.deadline, lang, t]);

  return (
    <div className="modal-backdrop" onClick={onClose}>
      <div className="modal-panel" onClick={(e) => e.stopPropagation()}>
        <div className="modal-header">
          <h3 className="modal-title" style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
            <Calendar size={18} style={{ color: 'var(--things-blue)' }} />
            <span>{t.ctxWhenReminder}</span>
          </h3>
          <button type="button" className="icon-btn" onClick={onClose}>
            <X size={18} />
          </button>
        </div>

        {/* Multilingual Natural Language Input */}
        <form onSubmit={handleNlpEnter} style={{ marginBottom: 12 }}>
          <input
            type="text"
            className="text-field"
            autoFocus
            value={nlpText}
            onChange={(e) => setNlpText(e.target.value)}
            placeholder={t.whenNaturalHint}
          />
          {parsedNlp && (
            <button
              type="submit"
              className="chip-btn active"
              style={{ marginTop: 8, width: '100%', justifyContent: 'center', padding: '7px 10px' }}
            >
              <Sparkles size={14} />
              <span>
                {t.actionSave}: <strong>{parsedNlp.formattedPreview}</strong>
              </span>
            </button>
          )}
        </form>

        {/* Quick Schedule Presets */}
        <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 8, marginBottom: 12 }}>
          <button
            type="button"
            className="btn-secondary"
            style={{ justifyContent: 'flex-start' }}
            onClick={() => applyPreset('TODAY')}
          >
            <Star size={15} fill="var(--today-star)" style={{ color: 'var(--today-star)' }} />
            <span>{t.viewToday}</span>
          </button>
          <button
            type="button"
            className="btn-secondary"
            style={{ justifyContent: 'flex-start' }}
            onClick={() => applyPreset('THIS_EVENING')}
          >
            <Moon size={15} style={{ color: 'var(--evening-moon)' }} />
            <span>{t.viewThisEvening}</span>
          </button>
          <button
            type="button"
            className="btn-secondary"
            style={{ justifyContent: 'flex-start' }}
            onClick={() => applyPreset('UPCOMING')}
          >
            <CalendarDays size={15} style={{ color: 'var(--upcoming-coral)' }} />
            <span>{t.dateTomorrow}</span>
          </button>
          <button
            type="button"
            className="btn-secondary"
            style={{ justifyContent: 'flex-start' }}
            onClick={() => applyEpochDate(addDaysLocal(startOfDayEpoch(), 7))}
          >
            <Calendar size={15} style={{ color: 'var(--things-blue)' }} />
            <span>{t.dateNextWeek}</span>
          </button>
          <button
            type="button"
            className="btn-secondary"
            style={{ justifyContent: 'flex-start' }}
            onClick={() => applyPreset('SOMEDAY')}
          >
            <Archive size={15} style={{ color: 'var(--someday-amber)' }} />
            <span>{t.viewSomeday}</span>
          </button>
          <button
            type="button"
            className="btn-secondary"
            style={{ justifyContent: 'flex-start' }}
            onClick={() => applyPreset(task.projectId || task.areaId ? 'ANYTIME' : 'INBOX')}
          >
            <Inbox size={15} style={{ color: 'var(--text-secondary)' }} />
            <span>
              {task.projectId || task.areaId ? t.viewAnytime : t.viewInbox} / {t.actionClear}
            </span>
          </button>
        </div>

        {/* Interactive Monday-First Calendar Grid (1-Click Apply!) */}
        <InteractiveMonthCalendar
          selectedEpoch={task.whenDate}
          lang={lang}
          onSelectDay={(epoch) => applyEpochDate(epoch)}
        />

        {/* Manual Date Input Fallback for Distant Dates */}
        <div style={{ display: 'flex', gap: 8, marginBottom: 12 }}>
          <input
            type="date"
            className="text-field"
            value={dateVal}
            onChange={(e) => setDateVal(e.target.value)}
          />
          <button
            type="button"
            className="btn-primary"
            disabled={!dateVal}
            onClick={handleCalendarApply}
          >
            {t.actionSave}
          </button>
        </div>

        {/* Reminder Time Presets + 24h Clock */}
        <div
          style={{
            padding: 12,
            borderRadius: 'var(--radius-sm)',
            background: 'var(--bg-subtle)',
            border: '1px solid var(--border-subtle)'
          }}
        >
          <div
            style={{
              display: 'flex',
              alignItems: 'center',
              justifyContent: 'space-between',
              marginBottom: 8
            }}
          >
            <span style={{ display: 'inline-flex', alignItems: 'center', gap: 6, fontSize: 12.5, fontWeight: 600 }}>
              <Bell size={14} style={{ color: 'var(--things-blue)' }} />
              <span>{t.actionReminder}</span>
            </span>
            {reminderClock && (
              <button
                type="button"
                className="chip-btn"
                onClick={() => setReminderClock('')}
              >
                {t.actionClear}
              </button>
            )}
          </div>

          <div style={{ display: 'flex', flexWrap: 'wrap', gap: 6, marginBottom: 8 }}>
            {REMINDER_PRESETS.map((preset) => (
              <button
                key={preset}
                type="button"
                className={`chip-btn ${reminderClock === preset ? 'active' : ''}`}
                onClick={() => setReminderClock(preset)}
              >
                {preset}
              </button>
            ))}
          </div>

          <div style={{ display: 'flex', gap: 8 }}>
            <input
              type="time"
              className="text-field"
              value={reminderClock}
              onChange={(e) => setReminderClock(e.target.value)}
            />
            <button
              type="button"
              className="btn-primary"
              onClick={() => {
                const baseDay =
                  fromIsoDateInputValue(dateVal) ?? task.whenDate ?? startOfDayEpoch();
                const rem = combineReminderWithDay(baseDay);
                const todayEnd = endOfDayEpoch();
                const nextState: ScheduleState =
                  task.scheduleState === 'THIS_EVENING'
                    ? 'THIS_EVENING'
                    : baseDay <= todayEnd
                      ? 'TODAY'
                      : 'UPCOMING';
                onApply({
                  scheduleState: rem != null ? nextState : task.scheduleState,
                  whenDate: rem != null ? baseDay : task.whenDate,
                  reminderTime: rem
                });
                onClose();
              }}
            >
              {t.actionSave}
            </button>
          </div>
        </div>

        {/* Direct Jump to Deadline / Due Date Picker */}
        {onOpenDeadline && (
          <div
            style={{
              marginTop: 12,
              paddingTop: 10,
              borderTop: '1px solid var(--border-subtle)',
              display: 'flex',
              alignItems: 'center',
              justifyContent: 'space-between',
              gap: 8
            }}
          >
            <span style={{ fontSize: 12.2, color: 'var(--text-secondary)', fontWeight: 550 }}>
              {t.actionDeadline}
            </span>
            <button
              type="button"
              className={`chip-btn ${task.deadline != null ? 'urgent' : ''}`}
              onClick={() => {
                onClose();
                onOpenDeadline();
              }}
            >
              <Flag size={12.5} style={{ color: 'var(--deadline-red)' }} />
              <span>{deadlineSummary || t.actionDeadline}</span>
            </button>
          </div>
        )}
      </div>
    </div>
  );
};

interface DeadlineModalProps {
  titleLabel?: string;
  currentDeadline: number | null;
  lang?: SupportedLang;
  t: TranslationStrings;
  onApply: (deadline: number | null) => void;
  onClose: () => void;
}

export const DeadlineModal: React.FC<DeadlineModalProps> = ({
  titleLabel,
  currentDeadline,
  lang = 'en',
  t,
  onApply,
  onClose
}) => {
  const [dateVal, setDateVal] = useState(() => toIsoDateInputValue(currentDeadline));
  const today = startOfDayEpoch();

  const pick = (ms: number | null) => {
    onApply(ms);
    onClose();
  };

  return (
    <div className="modal-backdrop" onClick={onClose}>
      <div className="modal-panel" onClick={(e) => e.stopPropagation()}>
        <div className="modal-header">
          <h3 className="modal-title" style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
            <Flag size={18} style={{ color: 'var(--deadline-red)' }} />
            <span>{titleLabel || t.actionDeadline}</span>
          </h3>
          <button type="button" className="icon-btn" onClick={onClose}>
            <X size={18} />
          </button>
        </div>

        <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 8, marginBottom: 12 }}>
          <button type="button" className="btn-secondary" onClick={() => pick(today)}>
            {t.viewToday}
          </button>
          <button
            type="button"
            className="btn-secondary"
            onClick={() => pick(addDaysLocal(today, 1))}
          >
            {t.dateTomorrow}
          </button>
          <button
            type="button"
            className="btn-secondary"
            onClick={() => pick(addDaysLocal(today, 7))}
          >
            {t.dateNextWeek}
          </button>
          <button
            type="button"
            className="btn-secondary"
            onClick={() => pick(addDaysLocal(today, 14))}
          >
            {t.dateInTwoWeeks}
          </button>
        </div>

        {/* Interactive Monday-First Calendar Grid (1-Click Due Date Selection!) */}
        <InteractiveMonthCalendar
          selectedEpoch={currentDeadline}
          lang={lang}
          accent="deadline"
          onSelectDay={(epoch) => pick(epoch)}
        />

        <div style={{ display: 'flex', gap: 8, marginBottom: 14 }}>
          <input
            type="date"
            className="text-field"
            value={dateVal}
            onChange={(e) => setDateVal(e.target.value)}
          />
          <button
            type="button"
            className="btn-primary"
            disabled={!dateVal}
            onClick={() => pick(fromIsoDateInputValue(dateVal))}
          >
            {t.actionSave}
          </button>
        </div>

        <div style={{ display: 'flex', justifyContent: 'space-between' }}>
          <button type="button" className="btn-danger" onClick={() => pick(null)}>
            {t.actionClear}
          </button>
          <button type="button" className="btn-secondary" onClick={onClose}>
            {t.actionCancel}
          </button>
        </div>
      </div>
    </div>
  );
};

interface RepeatModalProps {
  task: TaskRecord;
  lang: SupportedLang;
  t: TranslationStrings;
  onApply: (rule: RecurrenceRule | null) => void;
  onClose: () => void;
}

export const RepeatModal: React.FC<RepeatModalProps> = ({ task, lang, t, onApply, onClose }) => {
  const existing = task.recurrenceRule;
  const [mode, setMode] = useState<RecurrenceMode>(existing?.mode || 'FIXED_SCHEDULE');
  const [interval, setIntervalVal] = useState<number>(existing?.interval || 1);
  const [unit, setUnit] = useState<RecurrenceUnit>(existing?.unit || 'WEEKS');
  const [daysOfWeek, setDaysOfWeek] = useState<number[]>(() =>
    existing?.daysOfWeek?.length ? existing.daysOfWeek : [isoDayOfWeek(new Date())]
  );
  const [dayOfMonth, setDayOfMonth] = useState<number | null>(
    () => existing?.dayOfMonth ?? new Date().getDate()
  );
  const [hasEndDate, setHasEndDate] = useState<boolean>(existing?.endDateEpochMillis != null);
  const [endDateStr, setEndDateStr] = useState<string>(() =>
    toIsoDateInputValue(existing?.endDateEpochMillis ?? addDaysLocal(startOfDayEpoch(), 90))
  );

  const unitLabels: Record<SupportedLang, Record<RecurrenceUnit, string>> = {
    en: { DAYS: 'Days', WEEKS: 'Weeks', MONTHS: 'Months', YEARS: 'Years' },
    de: { DAYS: 'Tage', WEEKS: 'Wochen', MONTHS: 'Monate', YEARS: 'Jahre' },
    fr: { DAYS: 'Jours', WEEKS: 'Semaines', MONTHS: 'Mois', YEARS: 'Années' },
    it: { DAYS: 'Giorni', WEEKS: 'Settimane', MONTHS: 'Mesi', YEARS: 'Anni' },
    es: { DAYS: 'Días', WEEKS: 'Semanas', MONTHS: 'Meses', YEARS: 'Años' }
  };

  const weekdayLabels: Record<SupportedLang, string[]> = {
    en: ['M', 'T', 'W', 'T', 'F', 'S', 'S'],
    de: ['Mo', 'Di', 'Mi', 'Do', 'Fr', 'Sa', 'So'],
    fr: ['L', 'M', 'M', 'J', 'V', 'S', 'D'],
    it: ['L', 'M', 'M', 'G', 'V', 'S', 'D'],
    es: ['L', 'M', 'X', 'J', 'V', 'S', 'D']
  };

  const toggleDow = (dow: number) => {
    setDaysOfWeek((prev) =>
      prev.includes(dow)
        ? prev.length > 1
          ? prev.filter((x) => x !== dow)
          : prev
        : [...prev, dow].sort((a, b) => a - b)
    );
  };

  const candidateRule: RecurrenceRule = {
    mode,
    interval: Math.max(1, Math.min(99, interval || 1)),
    unit,
    daysOfWeek: mode === 'FIXED_SCHEDULE' && unit === 'WEEKS' ? daysOfWeek : [],
    dayOfMonth: mode === 'FIXED_SCHEDULE' && unit === 'MONTHS' ? dayOfMonth : null,
    endDateEpochMillis: hasEndDate ? fromIsoDateInputValue(endDateStr) : null
  };

  const previewText = formatRecurrenceShort(candidateRule, lang);
  const domChips = [1, 5, 10, 15, 20, 25, -1];

  return (
    <div className="modal-backdrop" onClick={onClose}>
      <div className="modal-panel" onClick={(e) => e.stopPropagation()}>
        <div className="modal-header">
          <h3 className="modal-title" style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
            <Repeat size={18} style={{ color: 'var(--things-blue)' }} />
            <span>{t.repeatDialogTitle}</span>
          </h3>
          <button type="button" className="icon-btn" onClick={onClose}>
            <X size={18} />
          </button>
        </div>

        {/* Mode Segmented Toggle + Mode Description */}
        <div style={{ display: 'flex', gap: 6, marginBottom: 6 }}>
          <button
            type="button"
            className={`btn-secondary ${mode === 'FIXED_SCHEDULE' ? 'active' : ''}`}
            style={{ flex: 1 }}
            onClick={() => setMode('FIXED_SCHEDULE')}
          >
            {t.repeatFixedSchedule}
          </button>
          <button
            type="button"
            className={`btn-secondary ${mode === 'AFTER_COMPLETION' ? 'active' : ''}`}
            style={{ flex: 1 }}
            onClick={() => setMode('AFTER_COMPLETION')}
          >
            {t.repeatAfterCompletion}
          </button>
        </div>
        <div style={{ fontSize: 11.5, color: 'var(--text-secondary)', marginBottom: 12 }}>
          {mode === 'FIXED_SCHEDULE' ? t.repeatModeFixedDesc : t.repeatModeAfterDesc}
        </div>

        {/* 4 Unit Pills */}
        <div style={{ display: 'grid', gridTemplateColumns: 'repeat(4, 1fr)', gap: 6, marginBottom: 14 }}>
          {(['DAYS', 'WEEKS', 'MONTHS', 'YEARS'] as RecurrenceUnit[]).map((u) => (
            <button
              key={u}
              type="button"
              className={`btn-secondary ${unit === u ? 'active' : ''}`}
              onClick={() => setUnit(u)}
            >
              {(unitLabels[lang] || unitLabels.en)[u]}
            </button>
          ))}
        </div>

        {/* (-) / (+) Interval Stepper 1..99 */}
        <div
          style={{
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'space-between',
            padding: '10px 12px',
            borderRadius: 'var(--radius-sm)',
            background: 'var(--bg-subtle)',
            border: '1px solid var(--border-subtle)',
            marginBottom: 14
          }}
        >
          <span style={{ fontSize: 13.5, fontWeight: 600 }}>{previewText}</span>
          <div style={{ display: 'inline-flex', alignItems: 'center', gap: 8 }}>
            <button
              type="button"
              className="stepper-btn"
              onClick={() => setIntervalVal((v) => Math.max(1, v - 1))}
            >
              −
            </button>
            <span style={{ minWidth: 24, textAlign: 'center', fontWeight: 700 }}>{interval}</span>
            <button
              type="button"
              className="stepper-btn"
              onClick={() => setIntervalVal((v) => Math.min(99, v + 1))}
            >
              +
            </button>
          </div>
        </div>

        {/* 7 Circular Weekday Pills (Weeks + Fixed) */}
        {mode === 'FIXED_SCHEDULE' && unit === 'WEEKS' && (
          <div style={{ marginBottom: 14 }}>
            <div style={{ fontSize: 11.5, color: 'var(--text-secondary)', marginBottom: 6 }}>
              {t.repeatWeekdaysLabel}
            </div>
            <div style={{ display: 'flex', justifyContent: 'space-between', gap: 6 }}>
              {(weekdayLabels[lang] || weekdayLabels.en).map((lbl, idx) => {
                const dow = idx + 1;
                const selected = daysOfWeek.includes(dow);
                return (
                  <button
                    key={dow}
                    type="button"
                    className={`weekday-circle ${selected ? 'active' : ''}`}
                    onClick={() => toggleDow(dow)}
                  >
                    {lbl}
                  </button>
                );
              })}
            </div>
          </div>
        )}

        {/* Day-of-Month Chips [1., 5., 10., 15., 20., 25., Last day (-1)] (Months + Fixed) */}
        {mode === 'FIXED_SCHEDULE' && unit === 'MONTHS' && (
          <div style={{ marginBottom: 14 }}>
            <div style={{ fontSize: 11.5, color: 'var(--text-secondary)', marginBottom: 6 }}>
              {t.repeatMonthDayLabel}
            </div>
            <div style={{ display: 'flex', flexWrap: 'wrap', gap: 6 }}>
              {domChips.map((dom) => (
                <button
                  key={dom}
                  type="button"
                  className={`chip-btn ${dayOfMonth === dom ? 'active' : ''}`}
                  onClick={() => setDayOfMonth(dom)}
                >
                  {dom === -1 ? t.repeatLastDay : `${dom}.`}
                </button>
              ))}
            </div>
          </div>
        )}

        {/* End Date: Never vs On Date */}
        <div
          style={{
            padding: '10px 12px',
            borderRadius: 'var(--radius-sm)',
            background: 'var(--bg-subtle)',
            border: '1px solid var(--border-subtle)',
            marginBottom: 16
          }}
        >
          <div
            style={{
              display: 'flex',
              alignItems: 'center',
              justifyContent: 'space-between',
              gap: 6,
              marginBottom: hasEndDate ? 8 : 0
            }}
          >
            <span style={{ fontSize: 11.5, color: 'var(--text-secondary)', fontWeight: 550 }}>
              {t.repeatEndsLabel}
            </span>
            <div style={{ display: 'flex', gap: 6 }}>
              <button
                type="button"
                className={`chip-btn ${!hasEndDate ? 'active' : ''}`}
                onClick={() => setHasEndDate(false)}
              >
                {t.repeatEndsNever}
              </button>
              <button
                type="button"
                className={`chip-btn ${hasEndDate ? 'active' : ''}`}
                onClick={() => setHasEndDate(true)}
              >
                {t.repeatEndsOn}
              </button>
            </div>
          </div>
          {hasEndDate && (
            <input
              type="date"
              className="text-field"
              value={endDateStr}
              onChange={(e) => setEndDateStr(e.target.value)}
            />
          )}
        </div>

        <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', gap: 8 }}>
          <button
            type="button"
            className="btn-danger"
            onClick={() => {
              onApply(null);
              onClose();
            }}
          >
            {t.repeatClearBtn}
          </button>

          <div style={{ display: 'flex', gap: 8 }}>
            <button type="button" className="btn-secondary" onClick={onClose}>
              {t.actionCancel}
            </button>
            <button
              type="button"
              className="btn-primary"
              onClick={() => {
                onApply(candidateRule);
                onClose();
              }}
            >
              {t.actionSave} ({previewText})
            </button>
          </div>
        </div>
      </div>
    </div>
  );
};
