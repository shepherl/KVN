package org.example;

import java.nio.file.Files;
import java.nio.file.Path;

public class AutoConnect {
    public static void run(Wireproxy eWireproxy,StatusBarMenu statusBarMenu,String configPath){
    // Логика работы автозапуска vpn при запуске утилиты
         if(SettingsParser.auto_start()&&Files.exists(Path.of(configPath + "proxy.conf"))&&Files.exists(Path.of(configPath + "AmneziaConfig.conf"))){ // Проверка наличия автозапуска
            if (eWireproxy.startWireproxy()) {
                AutoStart.setAutoLaunch(true);
                statusBarMenu.addFileAndRemove.setEnabled(false); // Далаем кнопку Remove Config не активной
                statusBarMenu.statusItem.setLabel("Status: Connected 🟢");
                statusBarMenu.toggleConnectItem.setLabel("Disconnect");
            } else {
                System.out.println("Ошибка запуска утилиты");
            }

        }
    }
}
