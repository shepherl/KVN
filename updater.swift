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
        NSApp.activate(ignoringOtherApps: true)
        NSWindow.allowsAutomaticWindowTabbing = false
        
        // Гарантированно делаем окно поверх всех с небольшой задержкой (чтобы SwiftUI успел его создать)
        DispatchQueue.main.asyncAfter(deadline: .now() + 0.2) {
            for window in NSApp.windows {
                window.level = .floating
                window.center()
            }
        }
    }
    func applicationShouldTerminateAfterLastWindowClosed(_ sender: NSApplication) -> Bool {
        return true
    }
}

struct UpdaterView: View {
    @State private var statusText = "Инициализация..."
    @State private var showSuccess = false
    @State private var progress: Double = 0.0
    @State private var isIndeterminate = true
    
    let args = CommandLine.arguments
    
    var body: some View {
        VStack(spacing: 20) {
            if showSuccess {
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
                if isIndeterminate {
                    ProgressView()
                        .scaleEffect(1.5)
                } else {
                    ProgressView(value: progress)
                        .frame(width: 200)
                }
                Text(statusText)
                    .font(.system(size: 14, weight: .medium))
            }
        }
        .padding()
        .onAppear {
            if args.count < 3 {
                statusText = "Ошибка: не переданы аргументы"
                return
            }
            performUpdate(downloadUrl: args[1], appPath: args[2])
        }
    }
    
    func performUpdate(downloadUrl: String, appPath: String) {
        DispatchQueue.global(qos: .userInitiated).async {
            DispatchQueue.main.async { statusText = "Ожидание завершения программы..." }
            
            if args.count > 3, let pid = Int32(args[3]) {
                while kill(pid, 0) == 0 {
                    sleep(1)
                }
            } else {
                sleep(3)
            }
            
            let isZip = downloadUrl.lowercased().hasSuffix(".zip")
            let downloadDest = isZip ? "/tmp/KVN_update.zip" : "/tmp/KVN_update.dmg"
            let extractDir = "/tmp/KVN_extracted"
            
            DispatchQueue.main.async { 
                statusText = "Загрузка обновления..."
                isIndeterminate = false 
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
                    statusText = "Ошибка скачивания сети!"
                    isIndeterminate = true
                }
                return
            }
            
            DispatchQueue.main.async {
                statusText = "Установка обновления..."
                isIndeterminate = true
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
            
            // Чтобы понять, почему падает копирование, записываем весь лог (set -x показывает каждую команду)
            let bashScriptWithLog = "(set -x; \(bashScript)) > /tmp/kvn_updater_log.txt 2>&1"
            
            let process = Process()
            process.executableURL = URL(fileURLWithPath: "/bin/bash")
            process.arguments = ["-c", bashScriptWithLog]
            try? process.run()
            process.waitUntilExit()
            
            if process.terminationStatus == 0 {
                DispatchQueue.main.async {
                    showSuccess = true
                }
            } else {
                DispatchQueue.main.async { statusText = "Ошибка при установке! (см. лог)" }
            }
        }
    }
    
    func launchAppAndExit() {
        let process = Process()
        process.executableURL = URL(fileURLWithPath: "/usr/bin/open")
        process.arguments = [args[2]]
        try? process.run()
        NSApplication.shared.terminate(nil)
    }
}
