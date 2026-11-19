import SwiftUI
import AppKit
import ThingsbookCore

@MainActor
public final class ThingsbookAppDelegate: NSObject, NSApplicationDelegate {
    weak var store: ThingsbookStore?

    public func applicationDidFinishLaunching(_ notification: Notification) {
        NSApp.servicesProvider = self
        if let icon = ThingsResourceLoader.appIconImage() {
            NSApp.applicationIconImage = icon
            NSApp.dockTile.display()
        }
        refreshBundleIconRegistrationIfNeeded()
    }

    private func refreshBundleIconRegistrationIfNeeded() {
        let bundleURL = Bundle.main.bundleURL
        guard bundleURL.pathExtension.lowercased() == "app" else { return }
        let buildKey = "thingsbook.iconRegisteredBuild"
        let currentBuild = String(AppUpdateService.currentBuildNumber)
        guard UserDefaults.standard.string(forKey: buildKey) != currentBuild else { return }
        UserDefaults.standard.set(currentBuild, forKey: buildKey)

        let bundlePath = bundleURL.path
        DispatchQueue.global(qos: .utility).async {
            if FileManager.default.isWritableFile(atPath: bundlePath) {
                try? FileManager.default.setAttributes([.modificationDate: Date()], ofItemAtPath: bundlePath)
            }
            let lsRegister = "/System/Library/Frameworks/CoreServices.framework/Frameworks/LaunchServices.framework/Support/lsregister"
            guard FileManager.default.isExecutableFile(atPath: lsRegister) else { return }
            let proc = Process()
            proc.executableURL = URL(fileURLWithPath: lsRegister)
            proc.arguments = ["-f", bundlePath]
            proc.standardInput = FileHandle.nullDevice
            proc.standardOutput = FileHandle.nullDevice
            proc.standardError = FileHandle.nullDevice
            try? proc.run()
            proc.waitUntilExit()
        }
    }

    public func application(_ application: NSApplication, open urls: [URL]) {
        guard store?.isLocked != true else { return }
        for url in urls {
            if url.scheme?.lowercased() == "thingsbook" {
                store?.handleDeepLink(url)
            } else if url.pathExtension.lowercased() == "tbbackup" {
                store?.importBackupFromURL(url)
            }
        }
    }

    public func applicationDockMenu(_ sender: NSApplication) -> NSMenu? {
        guard store?.isLocked != true else { return nil }
        let lang = store?.preferences.language ?? .system
        let strings = ThingsStrings.shared
        let menu = NSMenu(title: "Thingsbook")

        let newTaskItem = NSMenuItem(title: strings.text("entity_new_todo", lang: lang), action: #selector(dockNewTodo), keyEquivalent: "")
        newTaskItem.target = self
        menu.addItem(newTaskItem)

        let voiceTaskItem = NSMenuItem(title: strings.text("shortcut_voice_capture_short", lang: lang), action: #selector(dockVoiceTodo), keyEquivalent: "")
        voiceTaskItem.target = self
        menu.addItem(voiceTaskItem)

        let todayItem = NSMenuItem(title: strings.text("view_today", lang: lang), action: #selector(dockShowToday), keyEquivalent: "")
        todayItem.target = self
        menu.addItem(todayItem)

        let inboxItem = NSMenuItem(title: strings.text("view_inbox", lang: lang), action: #selector(dockShowInbox), keyEquivalent: "")
        inboxItem.target = self
        menu.addItem(inboxItem)
        return menu
    }

    @objc private func dockNewTodo() {
        guard store?.isLocked != true else { return }
        NSApp.activate(ignoringOtherApps: true)
        store?.createNewTaskInCurrentContext()
    }

    @objc private func dockVoiceTodo() {
        guard store?.isLocked != true else { return }
        NSApp.activate(ignoringOtherApps: true)
        store?.quickEntryPrefillTitle = ""
        store?.quickEntryPrefillNotes = ""
        store?.quickEntryAutoStartVoice = true
        store?.isQuickEntryPresented = true
    }

    @objc private func dockShowToday() {
        guard store?.isLocked != true else { return }
        NSApp.activate(ignoringOtherApps: true)
        store?.selectedDestination = .smartView(.today)
    }

    @objc private func dockShowInbox() {
        guard store?.isLocked != true else { return }
        NSApp.activate(ignoringOtherApps: true)
        store?.selectedDestination = .smartView(.inbox)
    }

    /// macOS Services handler ("Save to Thingsbook") matching Info.plist `NSServices`
    @objc public func saveToThingsbookService(
        _ pboard: NSPasteboard,
        userData: String?,
        error: AutoreleasingUnsafeMutablePointer<NSString?>
    ) {
        guard store?.isLocked != true else { return }
        guard let text = pboard.string(forType: .string)?.trimmingCharacters(in: .whitespacesAndNewlines),
              !text.isEmpty else { return }
        let lines = text.components(separatedBy: .newlines)
        let firstLine = String((lines.first ?? text).prefix(240))
        let rest = lines.dropFirst().joined(separator: "\n")

        NSApp.activate(ignoringOtherApps: true)
        store?.quickEntryPrefillTitle = firstLine
        store?.quickEntryPrefillNotes = rest
        store?.isQuickEntryPresented = true
    }
}

@main
public struct ThingsbookMacApp: App {
    @NSApplicationDelegateAdaptor(ThingsbookAppDelegate.self) private var appDelegate
    @StateObject private var store = ThingsbookStore()

    public init() {}

    public var body: some Scene {
        WindowGroup("Thingsbook", id: "main") {
            MainWorkspaceView(store: store)
                .frame(minWidth: 846, minHeight: 560)
                .onAppear {
                    appDelegate.store = store
                }
                .onOpenURL { url in
                    guard !store.isLocked else { return }
                    if url.scheme?.lowercased() == "thingsbook" {
                        store.handleDeepLink(url)
                    } else if url.pathExtension.lowercased() == "tbbackup" {
                        store.importBackupFromURL(url)
                    }
                }
        }
        .windowStyle(.automatic)
        .windowToolbarStyle(.unified(showsTitle: false))
        .defaultSize(width: 1140, height: 760)
        .commands {
            ThingsCommandMenus(store: store)
        }

        Settings {
            SettingsWindowView(store: store, showCloseButton: false)
        }
        .windowResizability(.contentSize)

        MenuBarExtra {
            if store.isLocked {
                MenuBarLockedPopoverView(store: store)
            } else {
                QuickEntrySheetView(store: store, isMenuBarPopover: true)
            }
        } label: {
            if let img = ThingsResourceLoader.menuBarIconImage() {
                Image(nsImage: img)
            } else {
                Image(systemName: "book.pages.fill")
            }
        }
        .menuBarExtraStyle(.window)
    }
}

private struct MenuBarLockedPopoverView: View {
    @ObservedObject var store: ThingsbookStore
    @Environment(\.openWindow) private var openWindow

    var body: some View {
        VStack(spacing: 14) {
            Image(systemName: "lock.shield.fill")
                .font(.system(size: 30, weight: .semibold))
                .foregroundStyle(ThingsMacPalette.accentBlue)

            Text(store.tr("app_lock_title"))
                .font(.system(size: 14.5, weight: .bold))

            Text(store.tr("macos_app_lock_subtitle"))
                .font(.system(size: 12))
                .foregroundStyle(.secondary)
                .multilineTextAlignment(.center)

            Button {
                store.unlockWithBiometrics()
            } label: {
                Label(store.tr("app_lock_unlock_btn"), systemImage: "touchid")
                    .font(.system(size: 12.5, weight: .semibold))
                    .padding(.horizontal, 12)
                    .padding(.vertical, 4)
            }
            .buttonStyle(.borderedProminent)

            Divider()

            HStack {
                Button(store.tr("macos_menubar_open_app")) {
                    openWindow(id: "main")
                    NSApplication.shared.activate(ignoringOtherApps: true)
                }
                .font(.system(size: 12))

                Spacer()

                Button(store.tr("macos_menubar_quit")) {
                    NSApplication.shared.terminate(nil)
                }
                .font(.system(size: 12))
                .foregroundStyle(.secondary)
            }
        }
        .padding(18)
        .frame(width: 320)
    }
}
