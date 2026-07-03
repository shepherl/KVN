package org.example;

import java.io.BufferedInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.PosixFilePermission;
import java.util.HashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.swing.JOptionPane;
import javax.swing.JFrame;

public class Updater {

    private static final String GITHUB_REPO = "shepherl/kvnfaq"; 
    public static final String CURRENT_VERSION = "1.0"; // Текущая версия

    private static void showMessageBlocking(String message, String title, int messageType) {
        JFrame topFrame = new JFrame();
        topFrame.setAlwaysOnTop(true);
        JOptionPane.showMessageDialog(topFrame, message, title, messageType);
        topFrame.dispose();
    }

    private static int showConfirmBlocking(String message, String title) {
        JFrame topFrame = new JFrame();
        topFrame.setAlwaysOnTop(true);
        int result = JOptionPane.showConfirmDialog(topFrame, message, title, JOptionPane.YES_NO_OPTION);
        topFrame.dispose();
        return result;
    }

    public static void checkForUpdates() {
        new Thread(() -> {
            try {
                System.out.println("Checking for updates...");
                HttpClient client = HttpClient.newHttpClient();
                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create("https://api.github.com/repos/" + GITHUB_REPO + "/releases/latest"))
                        .header("Accept", "application/vnd.github.v3+json")
                        .GET()
                        .build();

                HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
                if (response.statusCode() == 200) {
                    String json = response.body();
                    
                    Pattern tagPattern = Pattern.compile("\"tag_name\"\\s*:\\s*\"([^\"]+)\"");
                    Matcher tagMatcher = tagPattern.matcher(json);
                    
                    if (tagMatcher.find()) {
                        String latestVersion = tagMatcher.group(1);
                        
                        if (!CURRENT_VERSION.equals(latestVersion)) {
                            int choice = showConfirmBlocking("New version found: " + latestVersion + "\nDo you want to download and install it?", "Update Available");
                            
                            if (choice == JOptionPane.YES_OPTION) {
                                Pattern assetPattern = Pattern.compile("\"browser_download_url\"\\s*:\\s*\"([^\"]+\\.dmg)\"");
                                Matcher assetMatcher = assetPattern.matcher(json);
                                
                                if (assetMatcher.find()) {
                                    String downloadUrl = assetMatcher.group(1);
                                    downloadAndInstall(downloadUrl);
                                } else {
                                    showMessageBlocking("Could not find the DMG asset in the latest release.", "Error", JOptionPane.ERROR_MESSAGE);
                                }
                            }
                        } else {
                            showMessageBlocking("You are using the latest version (" + CURRENT_VERSION + ").", "Up to Date", JOptionPane.INFORMATION_MESSAGE);
                        }
                    }
                } else {
                    showMessageBlocking("Failed to check updates. HTTP Status: " + response.statusCode(), "Error", JOptionPane.ERROR_MESSAGE);
                }
            } catch (Exception e) {
                showMessageBlocking("Update check failed: " + e.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
            }
        }).start();
    }

    private static void downloadAndInstall(String downloadUrl) {
        try {
            Path dmgPath = Paths.get("/tmp/KVN_update.dmg");
            System.out.println("Downloading update from: " + downloadUrl);

            // 1. Скачиваем DMG
            HttpClient client = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NORMAL).build();
            HttpRequest request = HttpRequest.newBuilder().uri(URI.create(downloadUrl)).GET().build();
            HttpResponse<Path> response = client.send(request, HttpResponse.BodyHandlers.ofFile(dmgPath));

            if (response.statusCode() == 200) {
                System.out.println("Download complete. Preparing to install...");
                
                // 2. Определяем путь к текущему приложению (ищем .app)
                String appPath = getAppPath();
                if (appPath == null) {
                    System.err.println("Could not determine .app path. Aborting update.");
                    return;
                }

                // 3. Создаем Bash скрипт для обновления
                String volumeName = "KVN Installation"; // Имя тома DMG (зависит от настроек jpackage)
                String scriptContent = "#!/bin/bash\n" +
                        "sleep 2\n" + // Ждем пока основное приложение завершится
                        "hdiutil attach /tmp/KVN_update.dmg -nobrowse\n" +
                        "rm -rf \"" + appPath + "\"\n" +
                        "cp -R \"/Volumes/" + volumeName + "/KVN.app\" \"" + appPath + "\"\n" +
                        "hdiutil detach \"/Volumes/" + volumeName + "\" -force\n" +
                        "rm /tmp/KVN_update.dmg\n" +
                        "open \"" + appPath + "\"\n";

                Path scriptPath = Paths.get("/tmp/kvn_update.sh");
                Files.writeString(scriptPath, scriptContent);
                
                // Делаем скрипт исполняемым
                Set<PosixFilePermission> perms = new HashSet<>();
                perms.add(PosixFilePermission.OWNER_READ);
                perms.add(PosixFilePermission.OWNER_WRITE);
                perms.add(PosixFilePermission.OWNER_EXECUTE);
                Files.setPosixFilePermissions(scriptPath, perms);

                // Предупреждаем пользователя и ждем нажатия OK
                showMessageBlocking("Update downloaded successfully.\nThe application will now restart to install the update.", "Update Ready", JOptionPane.INFORMATION_MESSAGE);

                // 4. Запускаем скрипт и выходим
                new ProcessBuilder(scriptPath.toString()).start();
                System.exit(0);
            } else {
                showMessageBlocking("Failed to download update. HTTP Status: " + response.statusCode(), "Download Error", JOptionPane.ERROR_MESSAGE);
            }
        } catch (Exception e) {
            showMessageBlocking("Failed to download or install update: " + e.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    private static String getAppPath() {
        try {
            String path = Updater.class.getProtectionDomain().getCodeSource().getLocation().toURI().getPath();
            // Поднимаемся вверх, пока не найдем папку, оканчивающуюся на .app
            while (path != null && !path.isEmpty() && !path.equals("/")) {
                if (path.endsWith(".app") || path.endsWith(".app/")) {
                    return path;
                }
                path = path.substring(0, path.lastIndexOf('/'));
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null; // Если запущен не из бандла .app
    }
}
