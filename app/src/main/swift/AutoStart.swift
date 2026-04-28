import Foundation
import ServiceManagement

// Проверяем, передал ли пользователь аргумент (enable или disable)
guard CommandLine.arguments.count > 1 else {
    print("Usage: KVNHelper <enable|disable>")
    exit(1)
}

let command = CommandLine.arguments[1]

// SMAppService работает только на macOS 13 и новее
if #available(macOS 13.0, *) {
    do {
        if command == "enable" {
            try SMAppService.mainApp.register()
            print("Successfully registered for autostart")
        } else if command == "disable" {
            try SMAppService.mainApp.unregister()
            print("Successfully unregistered from autostart")
        } else {
            print("Unknown command: \(command)")
            exit(1)
        }
    } catch {
        print("SMAppService Error: \(error.localizedDescription)")
        exit(1)
    }
} else {
    print("macOS version too old for SMAppService")
    exit(0)
}