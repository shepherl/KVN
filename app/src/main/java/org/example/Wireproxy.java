package org.example;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Path;

public class Wireproxy {
    private static Process wireproxyProcess = null;
    private static Path configPath;

    public Wireproxy(Path configPath){
        this.configPath = configPath;
    }

    public static boolean startWireproxy() {
        try {
            // 1. Поиск папки (Contents/app)
            String appDir = System.getProperty("user.dir");

            // Если мы внутри .app, user.dir часто указывает на Contents/app.
            // Но если запуск из Терминала, путь может отличаться. Проверим:
            File proxyFile = new File(appDir, "wireproxy");

            if (!proxyFile.exists()) {
                // Резервный поиск через путь к JAR
                String jarPath = App.class.getProtectionDomain().getCodeSource().getLocation().toURI().getPath();
                appDir = new File(jarPath).getParent();
                proxyFile = new File(appDir, "wireproxy");
            }

            System.out.println("DEBUG: Binary Path -> " + proxyFile.getAbsolutePath());

            if (!proxyFile.exists()) {
                System.err.println("CRITICAL: wireproxy NOT FOUND!");
                return false;
            }

            // 2. Снимаем карантин macOS (БЕЗ ЭТОГО НЕ ЗАПУСТИТСЯ)
            // Если файл скачан из GitHub Actions, macOS пометит его как подозрительный.
            try {
                Runtime.getRuntime().exec(new String[]{"xattr", "-d", "com.apple.quarantine", proxyFile.getAbsolutePath()});
            } catch (Exception ignored) {}

            proxyFile.setExecutable(true);

            // 3. Запуск
            // Важно: передаем рабочую директорию, чтобы он нашел proxy.conf рядом
            
            System.out.println("Запустился прокси");
            ProcessBuilder pb = new ProcessBuilder(proxyFile.getAbsolutePath(), "-c", configPath + ("/proxy.conf"));
            pb.directory(new File(appDir));
            pb.redirectErrorStream(true);

            wireproxyProcess = pb.start();

            // 4. Поток чтения логов (чтобы увидеть ошибки Go в Терминале)
            new Thread(() -> {
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(wireproxyProcess.getInputStream()))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        System.out.println("[GO_ENGINE]: " + line);
                    }
                } catch (IOException e) { e.printStackTrace(); }
            }).start();

            return true;
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    public static void stopWireproxy() {
        if (wireproxyProcess != null && wireproxyProcess.isAlive()) {
            wireproxyProcess.destroy();
            System.out.println("DEBUG: wireproxy stopped.");
        }
    }

    public void stop() {
        if (wireproxyProcess != null && wireproxyProcess.isAlive()) {
            wireproxyProcess.destroy(); // Вот здесь мы реально убиваем процесс в системе
            System.out.println("Wireproxy успешно остановлен.");
        }
    }
}
