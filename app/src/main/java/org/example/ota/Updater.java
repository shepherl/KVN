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

            // Извлекаем KvnUpdater из ресурсов
            Path updaterTempPath = Paths.get("/tmp/KvnUpdater");
            try (InputStream is = Updater.class.getResourceAsStream("/KvnUpdater")) {
                if (is == null) {
                    showMessageBlocking("Failed to locate KvnUpdater in resources.", "Error", JOptionPane.ERROR_MESSAGE);
                    return;
                }
                Files.copy(is, updaterTempPath, StandardCopyOption.REPLACE_EXISTING);
            }

            // Делаем KvnUpdater исполняемым
            Set<PosixFilePermission> perms = new HashSet<>();
            perms.add(PosixFilePermission.OWNER_READ);
            perms.add(PosixFilePermission.OWNER_WRITE);
            perms.add(PosixFilePermission.OWNER_EXECUTE);
            Files.setPosixFilePermissions(updaterTempPath, perms);

            // Запускаем апдейтер, передавая ему URL, путь к приложению и PID текущего процесса
            new ProcessBuilder(
                updaterTempPath.toString(), 
                downloadUrl, 
                appPath, 
                String.valueOf(ProcessHandle.current().pid())
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
