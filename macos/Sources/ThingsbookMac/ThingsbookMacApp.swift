import SwiftUI
import AppKit
import ThingsbookCore

@MainActor
public final class ThingsbookAppDelegate: NSObject, NSApplicationDelegate {
    weak var store: ThingsbookStore?

    public func applicationDidFinishLaunching(_ notification: Notification) {
        NSApp.servicesProvider = self
    }

    public func application(_ application: NSApplication, open urls: [URL]) {
        for url in urls {
            if url.scheme?.lowercased() == "thingsbook" {
                store?.handleDeepLink(url)
            } else if url.pathExtension.lowercased() == "tbbackup" {
                store?.importBackupFromURL(url)
            }
        }
    }

    public func applicationDockMenu(_ sender: NSApplication) -> NSMenu? {
        let lang = store?.preferences.language ?? .system
        let strings = ThingsStrings.shared
        let menu = NSMenu(title: "Thingsbook")

        let newTaskItem = NSMenuItem(title: strings.text("entity_new_todo", lang: lang), action: #selector(dockNewTodo), keyEquivalent: "")
        newTaskItem.target = self
        menu.addItem(newTaskItem)

        let todayItem = NSMenuItem(title: strings.text("view_today", lang: lang), action: #selector(dockShowToday), keyEquivalent: "")
        todayItem.target = self
        menu.addItem(todayItem)

        let inboxItem = NSMenuItem(title: strings.text("view_inbox", lang: lang), action: #selector(dockShowInbox), keyEquivalent: "")
        inboxItem.target = self
        menu.addItem(inboxItem)
        return menu
    }

    @objc private func dockNewTodo() {
        NSApp.activate(ignoringOtherApps: true)
        store?.createNewTaskInCurrentContext()
    }

    @objc private func dockShowToday() {
        NSApp.activate(ignoringOtherApps: true)
        store?.selectedDestination = .smartView(.today)
    }

    @objc private func dockShowInbox() {
        NSApp.activate(ignoringOtherApps: true)
        store?.selectedDestination = .smartView(.inbox)
    }

    /// macOS Services handler ("Save to Thingsbook") matching Info.plist `NSServices`
    @objc public func saveToThingsbookService(
        _ pboard: NSPasteboard,
        userData: String?,
        error: AutoreleasingUnsafeMutablePointer<NSString?>
    ) {
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
        WindowGroup("Thingsbook") {
            MainWorkspaceView(store: store)
                .frame(minWidth: 880, minHeight: 580)
                .onAppear {
                    appDelegate.store = store
                }
                .onOpenURL { url in
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

        MenuBarExtra {
            QuickEntrySheetView(store: store, isMenuBarPopover: true)
        } label: {
            if let img = ThingsResourceLoader.menuBarIconImage() {
                Image(nsImage: img)
            } else {
                Image(systemName: "checkmark.circle.fill")
            }
        }
        .menuBarExtraStyle(.window)
    }
}
