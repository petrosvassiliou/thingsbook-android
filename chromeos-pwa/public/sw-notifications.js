// Service Worker Actionable Reminder Click Handler (Thingsbook ChromeOS v1.7.0)
self.addEventListener('notificationclick', (event) => {
  const notification = event.notification;
  const data = notification.data || {};
  const taskId = data.taskId;
  const action = event.action || 'open';

  notification.close();

  event.waitUntil(
    self.clients.matchAll({ type: 'window', includeUncontrolled: true }).then((clientList) => {
      for (const client of clientList) {
        if ('focus' in client) {
          if (taskId) {
            client.postMessage({
              type: 'THINGSBOOK_REMINDER_ACTION',
              action,
              taskId
            });
          }
          return client.focus();
        }
      }
      if (self.clients.openWindow) {
        const targetUrl = taskId
          ? `/?reminder_action=${encodeURIComponent(action)}&task_id=${encodeURIComponent(taskId)}`
          : '/';
        return self.clients.openWindow(targetUrl);
      }
      return undefined;
    })
  );
});
