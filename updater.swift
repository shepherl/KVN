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
        // Регистрируемся как обычное приложение с иконкой в Dock
        NSApp.setActivationPolicy(.regular)
        
        // Берём иконку у KVN.app
        let args = CommandLine.arguments
        if args.count > 2 {
            let icon = NSWorkspace.shared.icon(forFile: args[2])
            NSApp.applicationIconImage = icon
        }
        
        NSWindow.allowsAutomaticWindowTabbing = false
        
        // Запускаем рабочий процесс сразу
        UpdaterState.shared.start()
    }
    
    func applicationShouldTerminateAfterLastWindowClosed(_ sender: NSApplication) -> Bool {
        return true
    }
}

class UpdaterState: ObservableObject {
    static let shared = UpdaterState()
    
    @Published var statusText = "Ожидание закрытия KVN..."
    @Published var progress: Double = 0.0
    @Published var isIndeterminate = true
    @Published var isDone = false
    @Published var isError = false
    
    let args = CommandLine.arguments
    
    func activateWindow() {
        NSApp.activate(ignoringOtherApps: true)
        for window in NSApp.windows {
            window.level = .floating
            window.orderFrontRegardless()
            window.center()
        }
    }
    
    func start() {
        if args.count < 3 {
            DispatchQueue.main.async {
                self.statusText = "Ошибка: не переданы аргументы"
                self.isError = true
            }
            return
        }
        let downloadUrl = args[1]
        let appPath = args[2]
        
        DispatchQueue.global(qos: .userInitiated).async {
            // Ждём завершения Java-процесса по PID
            if self.args.count > 3, let pid = Int32(self.args[3]) {
                while kill(pid, 0) == 0 {
                    usleep(500_000) // 0.5 сек
                }
            } else {
                sleep(3)
            }
            
            // Java умерла — теперь активируем окно
            DispatchQueue.main.async { self.activateWindow() }
            
            // --- Скачивание ---
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
            let task = URLSession.shared.downloadTask(with: url) { localURL, _, error in
                if let localURL = localURL {
                    try? FileManager.default.removeItem(atPath: downloadDest)
                    try? FileManager.default.moveItem(at: localURL, to: URL(fileURLWithPath: downloadDest))
                } else {
                    downloadError = true
                }
                semaphore.signal()
            }
            
            var observation: NSKeyValueObservation?
            observation = task.progress.observe(\.fractionCompleted) { p, _ in
                DispatchQueue.main.async { self.progress = p.fractionCompleted }
            }
            
            task.resume()
            semaphore.wait()
            observation?.invalidate()
            
            if downloadError {
                DispatchQueue.main.async {
                    self.statusText = "Ошибка загрузки!"
                    self.isError = true
                }
                return
            }
            
            // --- Установка ---
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
            
            let proc = Process()
            proc.executableURL = URL(fileURLWithPath: "/bin/bash")
            proc.arguments = ["-c", bashScriptWithLog]
            try? proc.run()
            proc.waitUntilExit()
            
            if proc.terminationStatus == 0 {
                DispatchQueue.main.async {
                    self.statusText = "Успешно! Запуск KVN..."
                    self.isDone = true
                    self.activateWindow()
                    
                    // Автозапуск через 1 секунду
                    DispatchQueue.main.asyncAfter(deadline: .now() + 1.0) {
                        self.launchAppAndExit()
                    }
                }
            } else {
                DispatchQueue.main.async {
                    self.statusText = "Ошибка при установке!"
                    self.isError = true
                    self.activateWindow()
                }
            }
        }
    }
    
    func launchAppAndExit() {
        let proc = Process()
        proc.executableURL = URL(fileURLWithPath: "/usr/bin/open")
        proc.arguments = [args[2]]
        try? proc.run()
        DispatchQueue.main.asyncAfter(deadline: .now() + 0.5) {
            NSApplication.shared.terminate(nil)
        }
    }
}

struct UpdaterView: View {
    @ObservedObject var state = UpdaterState.shared
    
    var body: some View {
        VStack(spacing: 16) {
            if state.isDone {
                Image(systemName: "checkmark.circle.fill")
                    .resizable()
                    .frame(width: 40, height: 40)
                    .foregroundColor(.green)
                Text(state.statusText)
                    .font(.headline)
            } else if state.isError {
                Image(systemName: "xmark.circle.fill")
                    .resizable()
                    .frame(width: 40, height: 40)
                    .foregroundColor(.red)
                Text(state.statusText)
                    .font(.system(size: 14, weight: .medium))
                    .foregroundColor(.red)
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
}
