package org.example;

import java.io.File;

public class AutoStart {

    public static void setAutoLaunch(boolean enable) {
        try {
            // Находим наш хелпер
            File helper = new File(System.getProperty("user.dir"), "AutoStart");
            
            if (!helper.exists()) {
                helper = new File("/Applications/KVN.app/Contents/MacOS/AutoStart");
            }

            if (helper.exists()) {
                // 1. Снимаем карантин (флаг com.apple.quarantine)
                // -d удаляет конкретный атрибут, -r делает это рекурсивно
                try {
                    new ProcessBuilder("xattr", "-d", "com.apple.quarantine", helper.getAbsolutePath())
                        .start()
                        .waitFor(); // Ждем завершения, чтобы карантин точно снялся
                } catch (Exception e) {
                    // Если атрибута нет, xattr выдаст ошибку, это нормально, просто идем дальше
                }

                // 2. Убеждаемся, что файл исполняемый
                helper.setExecutable(true);

                // 3. Запускаем сам хелпер
                String command = enable ? "enable" : "disable";
                new ProcessBuilder(helper.getAbsolutePath(), command).start();
                
                System.out.println("AutoStart execution triggered: " + command);
            } else {
                System.err.println("AutoStart binary helper not found.");
            }
        } catch (Exception e) {
            System.err.println("Failed to execute AutoStart helper");
            e.printStackTrace();
        }
    }
}
