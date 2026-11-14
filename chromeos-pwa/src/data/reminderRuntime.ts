import { endOfDayEpoch, type AppPreferences, type TaskRecord } from '../domain/types';

const FIRED_REMINDERS_KEY = 'thingsbook_chromeos_fired_reminders_v1';
const LAST_DIGEST_DATE_KEY = 'thingsbook_chromeos_last_digest_date_v1';

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

export async function sendTestChromeOsNotification(appName: string): Promise<boolean> {
  const granted = await ensureNotificationPermission();
  if (!granted) return false;
  new Notification(appName, {
    body: 'ChromeOS Actionable Reminders & Today Summary notifications are active.',
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
  onSelectTask?: (taskId: string) => void
) {
  if (typeof window === 'undefined' || !('Notification' in window)) return;
  if (Notification.permission !== 'granted') return;

  const title = task.title || 'Thingsbook Reminder';
  const body = task.notes ? task.notes.slice(0, 140) : 'Scheduled To-Do reminder';

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
  actionLabels: ActionableReminderLabels = {
    complete: 'Complete',
    snooze1h: '+1h',
    tomorrow: 'Tomorrow'
  }
) {
  if (typeof window === 'undefined') return;

  const now = Date.now();
  const fired = loadFiredReminders();
  let changed = false;

  for (const task of tasks) {
    if (task.isDeleted || task.status !== 'OPEN' || task.reminderTime == null) continue;
    const key = `${task.id}:${task.reminderTime}`;
    if (fired.has(key)) continue;

    // Trigger if due within the past 24 hours up to now
    if (task.reminderTime <= now && now - task.reminderTime < 86_400_000) {
      void showActionableOsNotification(task, key, actionLabels, onSelectTask);
      onFireInAppReminder?.({
        taskId: task.id,
        title: task.title || 'Thingsbook Reminder'
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
        (t) =>
          !t.isDeleted &&
          t.status === 'OPEN' &&
          (t.scheduleState === 'TODAY' ||
            t.scheduleState === 'THIS_EVENING' ||
            (t.whenDate != null && t.whenDate <= todayEnd))
      ).length;

      if (activeTodayCount > 0) {
        new Notification('Thingsbook — Today', {
          body: `${activeTodayCount} active To-Do${activeTodayCount === 1 ? '' : 's'} scheduled for Today.`,
          icon: '/pwa-192x192.png',
          tag: `digest-${todayKey}`
        });
      }
      localStorage.setItem(LAST_DIGEST_DATE_KEY, todayKey);
    }
  }
}
