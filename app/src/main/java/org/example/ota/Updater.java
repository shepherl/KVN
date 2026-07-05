package org.example.ota;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.PosixFilePermission;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.HashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.swing.JOptionPane;
import javax.swing.JFrame;

/**
 * Класс, отвечающий за автоматическое (OTA) обновление приложения.
 * Связывается с GitHub API, проверяет наличие новых релизов и запускает 
 * нативный Swift-апдейтер для бесшовной загрузки и подмены файлов.
 */
public class Updater {

    private static final String GITHUB_REPO = "shepherl/kvnfaq"; 
    public static final String CURRENT_VERSION = "1.2"; 

    private static void showMessageBlocking(String message, String title, int messageType) {
        try {
            // Используем нативный AppleScript для идеального отображения в macOS (включая темную тему)
            String script = String.format(
                "display alert \"%s\" message \"%s\" buttons {\"OK\"} default button \"OK\"",
                title.replace("\"", "\\\""),
                message.replace("\n", "\\r").replace("\"", "\\\"")
            );
            Process process = new ProcessBuilder("osascript", "-e", script).start();
            process.waitFor();
        } catch (Exception e) {
            // Фолбэк на старое Java-окно, если AppleScript почему-то не сработал
            JFrame topFrame = new JFrame();
            topFrame.setAlwaysOnTop(true);
            JOptionPane.showMessageDialog(topFrame, message, title, messageType);
            topFrame.dispose();
        }
    }

    private static int showConfirmBlocking(String message, String title) {
        try {
            // Нативное окно macOS с поддержкой темной темы
            String script = String.format(
                "display alert \"%s\" message \"%s\" buttons {\"Нет\", \"Да\"} default button \"Да\"",
                title.replace("\"", "\\\""),
                message.replace("\n", "\\r").replace("\"", "\\\"")
            );
            Process process = new ProcessBuilder("osascript", "-e", script).start();
            process.waitFor();
            
            // Читаем ответ от AppleScript
            try (java.util.Scanner s = new java.util.Scanner(process.getInputStream()).useDelimiter("\\A")) {
                String result = s.hasNext() ? s.next() : "";
                if (result.contains("Да")) {
                    return JOptionPane.YES_OPTION;
                } else {
                    return JOptionPane.NO_OPTION;
                }
            }
        } catch (Exception e) {
            // Фолбэк на старое Java-окно
            JFrame topFrame = new JFrame();
            topFrame.setAlwaysOnTop(true);
            int result = JOptionPane.showConfirmDialog(topFrame, message, title, JOptionPane.YES_NO_OPTION);
            topFrame.dispose();
            return result;
        }
    }

    public static void checkForUpdates(boolean silentIfUpToDate) {
        new Thread(() -> {
            try {
                System.out.println("Checking for updates (silentIfUpToDate=" + silentIfUpToDate + ")...");
                
                HttpClient client = HttpClient.newBuilder()
                        .version(HttpClient.Version.HTTP_1_1)
                        .build();
                        
                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create("https://api.github.com/repos/" + GITHUB_REPO + "/releases/latest"))
                        .header("Accept", "application/vnd.github.v3+json")
                        .header("User-Agent", "KVN-Updater-App")
                        .GET()
                        .build();

                HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

                if (response.statusCode() == 200) {
                    String json = response.body();
                    
                    Matcher versionMatcher = Pattern.compile("\"tag_name\"\\s*:\\s*\"v?([^\"]+)\"").matcher(json);
                    if (versionMatcher.find()) {
                        String latestVersion = versionMatcher.group(1);
                        
                        if (!CURRENT_VERSION.equals(latestVersion)) {
                            int choice = showConfirmBlocking("Найдена новая версия: " + latestVersion + "\nСкачать и установить обновление?", "Доступно обновление");
                            if (choice == JOptionPane.YES_OPTION) {
                                Matcher assetMatcher = Pattern.compile("\"browser_download_url\"\\s*:\\s*\"([^\"]+)\"").matcher(json);
                                
                                if (assetMatcher.find()) {
                                    String downloadUrl = assetMatcher.group(1);
                                    launchSwiftUpdater(downloadUrl);
                                } else {
                                    showMessageBlocking("Could not find any asset in the latest release.", "Error", JOptionPane.ERROR_MESSAGE);
                                }
                            }
                        } else {
                            if (!silentIfUpToDate) {
                                showMessageBlocking("Вы используете самую последнюю версию (" + CURRENT_VERSION + ").", "Обновление не требуется", JOptionPane.INFORMATION_MESSAGE);
                            }
                        }
                    }
                } else {
                    if (!silentIfUpToDate) {
                        showMessageBlocking("Failed to check updates. HTTP Status: " + response.statusCode(), "Error", JOptionPane.ERROR_MESSAGE);
                    }
                }
            } catch (Exception e) {
                if (!silentIfUpToDate) {
                    showMessageBlocking("Update check failed: " + e.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
                }
            }
        }).start();
    }

    private static void launchSwiftUpdater(String downloadUrl) {
        try {
            String appPath = getAppPath();
            if (appPath == null) {
                showMessageBlocking("Could not determine .app path. Aborting update.", "Error", JOptionPane.ERROR_MESSAGE);
                return;
            }

            // Создаём временный .app бандл — это единственный способ получить
            // полноценный фокус окна на macOS Ventura+.
            // Структура: /tmp/KvnUpdater.app/Contents/MacOS/KvnUpdater
            //            /tmp/KvnUpdater.app/Contents/Info.plist
            Path appBundlePath = Paths.get("/tmp/KvnUpdater.app");
            Path macosDir = appBundlePath.resolve("Contents/MacOS");
            Path plistPath = appBundlePath.resolve("Contents/Info.plist");
            Path binaryPath = macosDir.resolve("KvnUpdater");

            // Удаляем старый бандл, если есть
            new ProcessBuilder("rm", "-rf", appBundlePath.toString()).start().waitFor();

            // Создаём структуру папок
            Files.createDirectories(macosDir);

            // Извлекаем KvnUpdater из ресурсов в Contents/MacOS/
            try (InputStream is = Updater.class.getResourceAsStream("/KvnUpdater")) {
                if (is == null) {
                    showMessageBlocking("Failed to locate KvnUpdater in resources.", "Error", JOptionPane.ERROR_MESSAGE);
                    return;
                }
                Files.copy(is, binaryPath, StandardCopyOption.REPLACE_EXISTING);
            }

            // Делаем бинарник исполняемым
            Set<PosixFilePermission> perms = new HashSet<>();
            perms.add(PosixFilePermission.OWNER_READ);
            perms.add(PosixFilePermission.OWNER_WRITE);
            perms.add(PosixFilePermission.OWNER_EXECUTE);
            Files.setPosixFilePermissions(binaryPath, perms);

            // Создаём минимальный Info.plist
            String plist = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
                + "<!DOCTYPE plist PUBLIC \"-//Apple//DTD PLIST 1.0//EN\" \"http://www.apple.com/DTDs/PropertyList-1.0.dtd\">\n"
                + "<plist version=\"1.0\">\n<dict>\n"
                + "  <key>CFBundleExecutable</key>\n  <string>KvnUpdater</string>\n"
                + "  <key>CFBundleIdentifier</key>\n  <string>com.kvn.updater</string>\n"
                + "  <key>CFBundleName</key>\n  <string>KVN Updater</string>\n"
                + "  <key>CFBundlePackageType</key>\n  <string>APPL</string>\n"
                + "  <key>CFBundleVersion</key>\n  <string>1.0</string>\n"
                + "  <key>LSUIElement</key>\n  <false/>\n"
                + "</dict>\n</plist>";
            Files.writeString(plistPath, plist);

            // Снимаем карантин с бандла
            new ProcessBuilder("xattr", "-dr", "com.apple.quarantine", appBundlePath.toString()).start().waitFor();

            // Запускаем через `open` — macOS воспринимает это как полноценное приложение
            // и даёт ему право на фокус окна!
            new ProcessBuilder(
                "open", appBundlePath.toString(),
                "--args", downloadUrl, appPath, String.valueOf(ProcessHandle.current().pid())
            ).start();
            
            // Моментально завершаем Java-программу
            System.exit(0);
        } catch (Exception e) {
            showMessageBlocking("Failed to launch native updater: " + e.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    private static String getAppPath() {
        try {
            String path = Updater.class.getProtectionDomain().getCodeSource().getLocation().toURI().getPath();
            while (path != null && !path.isEmpty() && !path.equals("/")) {
                if (path.endsWith(".app") || path.endsWith(".app/")) {
                    return path;
                }
                path = path.substring(0, path.lastIndexOf('/'));
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }
}
