package org.example;

import java.nio.file.Files;
import java.nio.file.Path;

public class AutoConnect {
    public static void run(Wireproxy eWireproxy,StatusBarMenu statusBarMenu,String configPath){
    // Логика работы автозапуска vpn при запуске утилиты
         int engine = SettingsParser.getProxyEngine();
         boolean autoStart = SettingsParser.auto_start();
         
         boolean canStart = false;
         if (engine == 0) {
             // Для Wireproxy нужны файлы конфигурации
             if (Files.exists(Path.of(configPath + "proxy.conf")) && Files.exists(Path.of(configPath + "AmneziaConfig.conf"))) {
                 canStart = true;
             }
         } else {
             // Для Opera Proxy файлы не обязательны (запуск с параметром -country EU)
             canStart = true;
         }

         if(autoStart && canStart){
            if (engine == 0 ? eWireproxy.startWireproxy() : OperaProxy.startOperaProxy()) {
                AutoStart.setAutoLaunch(true);
                if (engine == 0) statusBarMenu.addFileAndRemove.setEnabled(false);
                statusBarMenu.statusItem.setLabel("Status: Connected 🟢");
                statusBarMenu.toggleConnectItem.setLabel("Disconnect");
            } else {
                System.out.println("Ошибка запуска утилиты");
            }
        }
    }
}
