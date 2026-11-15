import {
  endOfDayEpoch,
  type AppPreferences,
  type SupportedLang,
  type TaskRecord
} from '../domain/types';
import { TRANSLATIONS, resolveLanguage } from '../i18n/translations';

const FIRED_REMINDERS_KEY = 'thingsbook_chromeos_fired_reminders_v1';
const LAST_DIGEST_DATE_KEY = 'thingsbook_chromeos_last_digest_date_v1';

interface LocalizedRuntimeCopy {
  testNotificationBody: string;
  fallbackReminderTitle: string;
  fallbackReminderBody: string;
  digestBody: (count: number) => string;
}

const RUNTIME_COPY: Record<SupportedLang, LocalizedRuntimeCopy> = {
  en: {
    testNotificationBody:
      'ChromeOS Actionable Reminders & Today Summary notifications are active.',
    fallbackReminderTitle: 'Thingsbook Reminder',
    fallbackReminderBody: 'Scheduled To-Do reminder',
    digestBody: (n) => `${n} active To-Do${n === 1 ? '' : 's'} scheduled for Today.`
  },
  de: {
    testNotificationBody:
      'Interaktive ChromeOS-Erinnerungen & Heute-Übersicht sind aktiv.',
    fallbackReminderTitle: 'Thingsbook-Erinnerung',
    fallbackReminderBody: 'Geplante Aufgaben-Erinnerung',
    digestBody: (n) => `${n} aktive Aufgabe${n === 1 ? '' : 'n'} für Heute eingeplant.`
  },
  fr: {
    testNotificationBody:
      'Les rappels interactifs ChromeOS et le résumé Aujourd’hui sont actifs.',
    fallbackReminderTitle: 'Rappel Thingsbook',
    fallbackReminderBody: 'Rappel de tâche planifiée',
    digestBody: (n) => `${n} tâche${n === 1 ? '' : 's'} active${n === 1 ? '' : 's'} prévue${n === 1 ? '' : 's'} pour Aujourd’hui.`
  },
  it: {
    testNotificationBody:
      'I promemoria interattivi ChromeOS e il riepilogo di Oggi sono attivi.',
    fallbackReminderTitle: 'Promemoria Thingsbook',
    fallbackReminderBody: 'Promemoria attività pianificata',
    digestBody: (n) => `${n} attività attiv${n === 1 ? 'a' : 'e'} pianificat${n === 1 ? 'a' : 'e'} per Oggi.`
  },
  es: {
    testNotificationBody:
      'Los recordatorios interactivos de ChromeOS y el resumen de Hoy están activos.',
    fallbackReminderTitle: 'Recordatorio de Thingsbook',
    fallbackReminderBody: 'Recordatorio de tarea programada',
    digestBody: (n) => `${n} tarea${n === 1 ? '' : 's'} activa${n === 1 ? '' : 's'} programada${n === 1 ? '' : 's'} para Hoy.`
  }
};

function loadFiredReminders(): Set<string> {
  try {
    const raw = localStorage.getItem(FIRED_REMINDERS_KEY);
    if (!raw) return new Set();
    const arr = JSON.parse(raw);
    return Array.isArray(arr) ? new Set(arr.slice(-500)) : new Set();
  } catch {
    return new Set();
  }
}

function saveFiredReminders(set: Set<string>) {
  localStorage.setItem(FIRED_REMINDERS_KEY, JSON.stringify(Array.from(set).slice(-500)));
}

export async function ensureNotificationPermission(): Promise<boolean> {
  if (typeof window === 'undefined' || !('Notification' in window)) return false;
  if (Notification.permission === 'granted') return true;
  if (Notification.permission === 'denied') return false;
  const result = await Notification.requestPermission();
  return result === 'granted';
}

export async function sendTestChromeOsNotification(
  appName: string,
  langPref: SupportedLang | 'system' = 'system'
): Promise<boolean> {
  const granted = await ensureNotificationPermission();
  if (!granted) return false;
  const lang = resolveLanguage(langPref);
  new Notification(appName, {
    body: RUNTIME_COPY[lang].testNotificationBody,
    icon: '/pwa-192x192.png',
    badge: '/pwa-192x192.png'
  });
  return true;
}

export interface ActionableReminderLabels {
  complete: string;
  snooze1h: string;
  tomorrow: string;
}

async function showActionableOsNotification(
  task: TaskRecord,
  tagKey: string,
  labels: ActionableReminderLabels,
  lang: SupportedLang,
  onSelectTask?: (taskId: string) => void
) {
  if (typeof window === 'undefined' || !('Notification' in window)) return;
  if (Notification.permission !== 'granted') return;

  const copy = RUNTIME_COPY[lang];
  const title = task.title || copy.fallbackReminderTitle;
  const body = task.notes ? task.notes.slice(0, 140) : copy.fallbackReminderBody;

  try {
    if ('serviceWorker' in navigator) {
      const reg = await navigator.serviceWorker.getRegistration();
      if (reg && typeof reg.showNotification === 'function') {
        const swOptions: NotificationOptions & {
          actions?: Array<{ action: string; title: string }>;
        } = {
          body,
          icon: '/pwa-192x192.png',
          badge: '/pwa-192x192.png',
          tag: tagKey,
          data: { taskId: task.id },
          actions: [
            { action: 'complete', title: `✓ ${labels.complete}` },
            { action: 'snooze-1h', title: labels.snooze1h },
            { action: 'tomorrow', title: labels.tomorrow }
          ]
        };
        await reg.showNotification(title, swOptions);
        return;
      }
    }
  } catch {
    // Fall back to standard window Notification below
  }

  const n = new Notification(title, {
    body,
    icon: '/pwa-192x192.png',
    tag: tagKey
  });
  if (onSelectTask) {
    n.onclick = () => {
      window.focus();
      onSelectTask(task.id);
      n.close();
    };
  }
}

export function checkDueRemindersAndDigest(
  tasks: TaskRecord[],
  prefs: AppPreferences,
  onSelectTask?: (taskId: string) => void,
  onFireInAppReminder?: (item: { taskId: string; title: string }) => void,
  actionLabels?: ActionableReminderLabels
) {
  if (typeof window === 'undefined') return;

  const lang = resolveLanguage(prefs.language);
  const t = TRANSLATIONS[lang];
  const copy = RUNTIME_COPY[lang];
  const resolvedLabels: ActionableReminderLabels = actionLabels ?? {
    complete: t.actionComplete,
    snooze1h: t.notificationSnooze1h,
    tomorrow: t.dateTomorrow
  };

  const now = Date.now();
  const fired = loadFiredReminders();
  let changed = false;

  for (const task of tasks) {
    if (task.isDeleted || task.status !== 'OPEN' || task.reminderTime == null) continue;
    const key = `${task.id}:${task.reminderTime}`;
    if (fired.has(key)) continue;

    // Trigger if due within the past 24 hours up to now
    if (task.reminderTime <= now && now - task.reminderTime < 86_400_000) {
      void showActionableOsNotification(task, key, resolvedLabels, lang, onSelectTask);
      onFireInAppReminder?.({
        taskId: task.id,
        title: task.title || copy.fallbackReminderTitle
      });
      fired.add(key);
      changed = true;
    }
  }

  if (changed) {
    saveFiredReminders(fired);
  }

  if (
    prefs.dailyDigestEnabled &&
    'Notification' in window &&
    Notification.permission === 'granted'
  ) {
    const d = new Date(now);
    const todayKey = `${d.getFullYear()}-${d.getMonth() + 1}-${d.getDate()}`;
    const lastDigest = localStorage.getItem(LAST_DIGEST_DATE_KEY);
    if (
      lastDigest !== todayKey &&
      (d.getHours() > prefs.dailyDigestHour ||
        (d.getHours() === prefs.dailyDigestHour && d.getMinutes() >= prefs.dailyDigestMinute))
    ) {
      const todayEnd = endOfDayEpoch(d);
      const activeTodayCount = tasks.filter(
        (tk) =>
          !tk.isDeleted &&
          tk.status === 'OPEN' &&
          (tk.scheduleState === 'TODAY' ||
            tk.scheduleState === 'THIS_EVENING' ||
            (tk.whenDate != null && tk.whenDate <= todayEnd))
      ).length;

      if (activeTodayCount > 0) {
        new Notification(`${t.appName} — ${t.viewToday}`, {
          body: copy.digestBody(activeTodayCount),
          icon: '/pwa-192x192.png',
          tag: `digest-${todayKey}`
        });
      }
      localStorage.setItem(LAST_DIGEST_DATE_KEY, todayKey);
    }
  }
}
