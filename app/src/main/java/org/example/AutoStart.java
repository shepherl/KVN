package org.example;

import java.io.File;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;

public class AutoStart {

    public static void setAutoLaunch(boolean enable) {
        try {
            // 1. Получаем путь к запущенному JAR-файлу
            String jarPath = AutoStart.class.getProtectionDomain().getCodeSource().getLocation().getPath();
            // Декодируем путь (на случай пробелов или кириллицы в пути)
            String decodedPath = URLDecoder.decode(jarPath, StandardCharsets.UTF_8);
            
            File currentFile = new File(decodedPath);
            // 2. Поднимаемся выше до папки Contents/MacOS
            // Обычно JAR лежит в Contents/Java или Contents/app, а нам нужно в MacOS
            File macosDir = null;
            
            // Ищем папку MacOS, поднимаясь по дереву каталогов
            File parent = currentFile.getParentFile();
            while (parent != null) {
                File check = new File(parent, "MacOS");
                if (check.exists() && check.isDirectory()) {
                    macosDir = check;
                    break;
                }
                parent = parent.getParentFile();
            }

            if (macosDir != null) {
                File helper = new File(macosDir, "AutoStart");
                
                if (helper.exists()) {
                    // Снимаем карантин
                    new ProcessBuilder("xattr", "-d", "com.apple.quarantine", helper.getAbsolutePath())
                        .start().waitFor();
                    
                    helper.setExecutable(true);

                    String command = enable ? "enable" : "disable";
                    new ProcessBuilder(helper.getAbsolutePath(), command).start();
                    System.out.println("AutoStart helper executed from: " + helper.getAbsolutePath());
                } else {
                    System.err.println("AutoStart binary not found at: " + helper.getAbsolutePath());
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
