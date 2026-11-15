import Foundation
import UserNotifications

public enum NotificationActionIdentifier {
    public static let categoryTaskReminder = "THINGSBOOK_TASK_REMINDER"
    public static let complete = "COMPLETE_ACTION"
    public static let snoozeOneHour = "SNOOZE_1H_ACTION"
    public static let moveToTomorrow = "TOMORROW_ACTION"
}

public final class NotificationReminderService: NSObject, UNUserNotificationCenterDelegate, @unchecked Sendable {
    public static let shared = NotificationReminderService()

    public var onActionReceived: (@Sendable (_ taskId: String, _ actionIdentifier: String) -> Void)?

    private var isAppBundleAvailable: Bool {
        Bundle.main.bundleIdentifier != nil && Bundle.main.bundlePath.hasSuffix(".app")
    }

    private override init() {
        super.init()
    }

    public func configureCategories(lang: AppLanguage) {
        guard isAppBundleAvailable else { return }
        let center = UNUserNotificationCenter.current()
        center.delegate = self

        let strings = ThingsStrings.shared
        let completeAction = UNNotificationAction(
            identifier: NotificationActionIdentifier.complete,
            title: strings.text("action_complete", lang: lang),
            options: []
        )
        let snoozeAction = UNNotificationAction(
            identifier: NotificationActionIdentifier.snoozeOneHour,
            title: strings.text("notification_snooze_1h", lang: lang),
            options: []
        )
        let tomorrowAction = UNNotificationAction(
            identifier: NotificationActionIdentifier.moveToTomorrow,
            title: strings.text("date_tomorrow", lang: lang),
            options: []
        )

        let category = UNNotificationCategory(
            identifier: NotificationActionIdentifier.categoryTaskReminder,
            actions: [completeAction, snoozeAction, tomorrowAction],
            intentIdentifiers: [],
            options: []
        )
        center.setNotificationCategories([category])
    }

    public func requestPermissionIfNeeded() async -> Bool {
        guard isAppBundleAvailable else { return false }
        let center = UNUserNotificationCenter.current()
        do {
            return try await center.requestAuthorization(options: [.alert, .sound, .badge])
        } catch {
            return false
        }
    }

    public func cancelTaskReminder(taskId: String) {
        cancelTaskReminders(taskIds: [taskId])
    }

    public func cancelTaskReminders(taskIds: [String]) {
        guard isAppBundleAvailable, !taskIds.isEmpty else { return }
        let center = UNUserNotificationCenter.current()
        let ids = taskIds.map { "tb_rem_\($0)" }
        center.removePendingNotificationRequests(withIdentifiers: ids)
        center.removeDeliveredNotifications(withIdentifiers: ids)
    }

    public func syncAllTaskReminders(openTasks: [TaskItem], lang: AppLanguage) {
        guard isAppBundleAvailable else { return }
        let center = UNUserNotificationCenter.current()
        let validIds = Set(
            openTasks
                .filter { $0.status == .open && !$0.isDeleted && $0.reminderTime != nil }
                .map { "tb_rem_\($0.id)" }
        )
        center.getPendingNotificationRequests { requests in
            let staleIds = requests
                .map(\.identifier)
                .filter { $0.hasPrefix("tb_rem_") && !validIds.contains($0) }
            if !staleIds.isEmpty {
                center.removePendingNotificationRequests(withIdentifiers: staleIds)
            }
        }
        for task in openTasks where task.status == .open && !task.isDeleted && task.reminderTime != nil {
            syncTaskReminder(task, lang: lang)
        }
    }

    public func syncTaskReminder(_ task: TaskItem, lang: AppLanguage) {
        guard isAppBundleAvailable else { return }
        let center = UNUserNotificationCenter.current()
        let reqId = "tb_rem_\(task.id)"
        center.removePendingNotificationRequests(withIdentifiers: [reqId])

        guard task.status == .open,
              !task.isDeleted,
              let remMillis = task.reminderTime else {
            center.removeDeliveredNotifications(withIdentifiers: [reqId])
            return
        }

        let fireDate = DateEpochConverter.date(fromMillis: remMillis)
        guard fireDate.timeIntervalSinceNow > 2 else { return }

        let content = UNMutableNotificationContent()
        content.title = task.title
        if !task.notes.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty {
            content.body = String(task.notes.prefix(160))
        } else {
            content.body = ThingsStrings.shared.text("notifications_title", lang: lang)
        }
        content.sound = .default
        content.categoryIdentifier = NotificationActionIdentifier.categoryTaskReminder
        content.userInfo = ["taskId": task.id]

        let comps = Calendar.current.dateComponents([.year, .month, .day, .hour, .minute, .second], from: fireDate)
        let trigger = UNCalendarNotificationTrigger(dateMatching: comps, repeats: false)
        let req = UNNotificationRequest(identifier: reqId, content: content, trigger: trigger)
        center.add(req)
    }

    public func syncDailyDigest(preferences: AppPreferences, todayTaskCount: Int) {
        guard isAppBundleAvailable else { return }
        let center = UNUserNotificationCenter.current()
        let digestId = "tb_daily_digest"
        center.removePendingNotificationRequests(withIdentifiers: [digestId])

        guard preferences.dailyDigestEnabled else { return }

        let content = UNMutableNotificationContent()
        content.title = ThingsStrings.shared.text("notifications_daily_digest_channel", lang: preferences.language)
        if todayTaskCount > 0 {
            content.body = ThingsStrings.shared.format("notifications_daily_digest_header", lang: preferences.language, todayTaskCount)
        } else {
            content.body = ThingsStrings.shared.text("notifications_daily_digest_empty", lang: preferences.language)
        }
        content.sound = .default

        var comps = DateComponents()
        comps.hour = min(23, max(0, preferences.dailyDigestHour))
        comps.minute = min(59, max(0, preferences.dailyDigestMinute))
        let trigger = UNCalendarNotificationTrigger(dateMatching: comps, repeats: true)
        let req = UNNotificationRequest(identifier: digestId, content: content, trigger: trigger)
        center.add(req)
    }

    public func postFocusTimerCompleted(taskTitle: String, lang: AppLanguage) {
        guard isAppBundleAvailable else { return }
        let content = UNMutableNotificationContent()
        content.title = ThingsStrings.shared.text("focus_timer_finished_title", lang: lang)
        content.body = taskTitle
        content.sound = .default
        let req = UNNotificationRequest(identifier: "tb_focus_done_\(UUID().uuidString)", content: content, trigger: nil)
        UNUserNotificationCenter.current().add(req)
    }

    public func postTestNotification(lang: AppLanguage) {
        guard isAppBundleAvailable else { return }
        let content = UNMutableNotificationContent()
        content.title = ThingsStrings.shared.text("macos_test_notif_title", lang: lang)
        content.body = ThingsStrings.shared.text("macos_test_notif_body", lang: lang)
        content.sound = .default
        let req = UNNotificationRequest(identifier: "tb_test_\(UUID().uuidString)", content: content, trigger: nil)
        UNUserNotificationCenter.current().add(req)
    }

    public func userNotificationCenter(
        _ center: UNUserNotificationCenter,
        didReceive response: UNNotificationResponse,
        withCompletionHandler completionHandler: @escaping () -> Void
    ) {
        if let taskId = response.notification.request.content.userInfo["taskId"] as? String {
            onActionReceived?(taskId, response.actionIdentifier)
        }
        completionHandler()
    }

    public func userNotificationCenter(
        _ center: UNUserNotificationCenter,
        willPresent notification: UNNotification,
        withCompletionHandler completionHandler: @escaping (UNNotificationPresentationOptions) -> Void
    ) {
        completionHandler([.banner, .sound])
    }
}
