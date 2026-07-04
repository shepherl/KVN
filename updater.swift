import SwiftUI
import AppKit
import Foundation

@main
struct UpdaterApp: App {
    @NSApplicationDelegateAdaptor(AppDelegate.self) var appDelegate
    
    var body: some Scene {
        WindowGroup {
            UpdaterView()
                .frame(width: 350, height: 200)
                .fixedSize()
        }
        .windowStyle(HiddenTitleBarWindowStyle())
        .windowResizability(.contentSize)
    }
}

class AppDelegate: NSObject, NSApplicationDelegate {
    func applicationDidFinishLaunching(_ notification: Notification) {
        NSApp.setActivationPolicy(.regular)
        
        let args = CommandLine.arguments
        if args.count > 2 {
            let icon = NSWorkspace.shared.icon(forFile: args[2])
            NSApp.applicationIconImage = icon
        }
        
        NSWindow.allowsAutomaticWindowTabbing = false
        UpdaterState.shared.start()
    }
    func applicationShouldTerminateAfterLastWindowClosed(_ sender: NSApplication) -> Bool {
        return true
    }
}

class UpdaterState: ObservableObject {
    static let shared = UpdaterState()
    
    @Published var statusText = "Ожидание закрытия KVN..."
    @Published var showSuccess = false
    @Published var progress: Double = 0.0
    @Published var isIndeterminate = true
    
    let args = CommandLine.arguments
    
    func start() {
        if args.count < 3 {
            DispatchQueue.main.async { self.statusText = "Ошибка: не переданы аргументы" }
            return
        }
        let downloadUrl = args[1]
        let appPath = args[2]
        
        DispatchQueue.global(qos: .userInitiated).async {
            // Ждем завершения Java процесса по переданному PID
            if self.args.count > 3, let pid = Int32(self.args[3]) {
                while kill(pid, 0) == 0 {
                    sleep(1)
                }
            } else {
                sleep(3)
            }
            
            // Важно: мы делаем окно активным ТОЛЬКО после того, как Java умерла. 
            // Иначе macOS отберет фокус при смерти Java-процесса.
            DispatchQueue.main.async {
                NSApp.activate(ignoringOtherApps: true)
                for window in NSApp.windows {
                    window.level = .screenSaver
                    window.makeKeyAndOrderFront(nil)
                    window.center()
                }
            }
            
            let isZip = downloadUrl.lowercased().hasSuffix(".zip")
            let downloadDest = isZip ? "/tmp/KVN_update.zip" : "/tmp/KVN_update.dmg"
            let extractDir = "/tmp/KVN_extracted"
            
            DispatchQueue.main.async { 
                self.statusText = "Загрузка обновления..."
                self.isIndeterminate = false 
            }
            
            let semaphore = DispatchSemaphore(value: 0)
            var downloadError = false
            
            guard let url = URL(string: downloadUrl) else { return }
            let task = URLSession.shared.downloadTask(with: url) { localURL, response, error in
                if let localURL = localURL {
                    try? FileManager.default.removeItem(atPath: downloadDest)
                    try? FileManager.default.moveItem(at: localURL, to: URL(fileURLWithPath: downloadDest))
                } else {
                    downloadError = true
                }
                semaphore.signal()
            }
            
            var observation: NSKeyValueObservation?
            observation = task.progress.observe(\.fractionCompleted) { progressObj, _ in
                DispatchQueue.main.async { self.progress = progressObj.fractionCompleted }
            }
            
            task.resume()
            semaphore.wait()
            observation?.invalidate()
            
            if downloadError {
                DispatchQueue.main.async { 
                    self.statusText = "Ошибка скачивания сети!"
                    self.isIndeterminate = true
                }
                return
            }
            
            DispatchQueue.main.async {
                self.statusText = "Установка обновления..."
                self.isIndeterminate = true
            }
            
            let bashScript: String
            if isZip {
                bashScript = """
                rm -rf "\(extractDir)"
                mkdir -p "\(extractDir)"
                unzip -q "\(downloadDest)" -d "\(extractDir)"
                
                DMG_FILE=$(find "\(extractDir)" -name "*.dmg" | head -n 1)
                if [ -z "$DMG_FILE" ]; then exit 1; fi
                
                hdiutil detach "/Volumes/KVN Installation" -force 2>/dev/null
                hdiutil attach "$DMG_FILE" -nobrowse
                if [ $? -ne 0 ]; then exit 1; fi
                
                rm -rf "\(appPath)"
                cp -R "/Volumes/KVN Installation/KVN.app" "\(appPath)"
                CP_STATUS=$?
                xattr -dr com.apple.quarantine "\(appPath)" 2>/dev/null
                
                hdiutil detach "/Volumes/KVN Installation" -force 2>/dev/null
                rm -rf "\(extractDir)" "\(downloadDest)"
                
                if [ $CP_STATUS -ne 0 ]; then exit 1; fi
                exit 0
                """
            } else {
                bashScript = """
                hdiutil detach "/Volumes/KVN Installation" -force 2>/dev/null
                hdiutil attach "\(downloadDest)" -nobrowse
                if [ $? -ne 0 ]; then exit 1; fi
                
                rm -rf "\(appPath)"
                cp -R "/Volumes/KVN Installation/KVN.app" "\(appPath)"
                CP_STATUS=$?
                xattr -dr com.apple.quarantine "\(appPath)" 2>/dev/null
                
                hdiutil detach "/Volumes/KVN Installation" -force 2>/dev/null
                rm -rf "\(downloadDest)"
                
                if [ $CP_STATUS -ne 0 ]; then exit 1; fi
                exit 0
                """
            }
            
            let bashScriptWithLog = "(set -x; \(bashScript)) > /tmp/kvn_updater_log.txt 2>&1"
            
            let process = Process()
            process.executableURL = URL(fileURLWithPath: "/bin/bash")
            process.arguments = ["-c", bashScriptWithLog]
            try? process.run()
            process.waitUntilExit()
            
            if process.terminationStatus == 0 {
                DispatchQueue.main.async {
                    self.showSuccess = true
                }
            } else {
                DispatchQueue.main.async { self.statusText = "Ошибка при установке! (см. лог)" }
            }
        }
    }
}

struct UpdaterView: View {
    @ObservedObject var state = UpdaterState.shared
    
    var body: some View {
        VStack(spacing: 20) {
            if state.showSuccess {
                Image(nsImage: NSImage(named: NSImage.Name("NSMenuOnStateTemplate")) ?? NSImage())
                    .resizable()
                    .frame(width: 40, height: 40)
                    .foregroundColor(.green)
                Text("Успешно обновлено!")
                    .font(.headline)
                Button("ОК") {
                    launchAppAndExit()
                }
                .keyboardShortcut(.defaultAction)
            } else {
                if state.isIndeterminate {
                    ProgressView()
                        .scaleEffect(1.5)
                } else {
                    ProgressView(value: state.progress)
                        .frame(width: 200)
                }
                Text(state.statusText)
                    .font(.system(size: 14, weight: .medium))
            }
        }
        .padding()
    }
    
    func launchAppAndExit() {
        let process = Process()
        process.executableURL = URL(fileURLWithPath: "/usr/bin/open")
        process.arguments = [state.args[2]]
        try? process.run()
        NSApplication.shared.terminate(nil)
    }
}
