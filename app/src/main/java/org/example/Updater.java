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
import javax.swing.JDialog;
import javax.swing.JProgressBar;
import javax.swing.JLabel;
import javax.swing.JPanel;
import java.awt.BorderLayout;
import javax.swing.SwingUtilities;

public class Updater {

    private static final String GITHUB_REPO = "shepherl/kvnfaq"; 
    public static final String CURRENT_VERSION = "1.4"; // Текущая версия

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

    private static JDialog showProgressDialog(String message) {
        JDialog[] dialogArray = new JDialog[1];
        try {
            SwingUtilities.invokeAndWait(() -> {
                JDialog dialog = new JDialog((JFrame)null, "KVN Updater", false);
                dialog.setAlwaysOnTop(true);
                dialog.setDefaultCloseOperation(JDialog.DO_NOTHING_ON_CLOSE);
                dialog.setSize(300, 100);
                dialog.setLocationRelativeTo(null);
                
                JPanel panel = new JPanel(new BorderLayout(10, 10));
                panel.setBorder(javax.swing.BorderFactory.createEmptyBorder(10, 10, 10, 10));
                
                JLabel label = new JLabel(message, javax.swing.SwingConstants.CENTER);
                
                java.net.URL spinnerUrl = Updater.class.getResource("/spinner.gif");
                if (spinnerUrl != null) {
                    JLabel spinnerLabel = new JLabel(new javax.swing.ImageIcon(spinnerUrl));
                    spinnerLabel.setHorizontalAlignment(javax.swing.SwingConstants.CENTER);
                    panel.add(spinnerLabel, BorderLayout.CENTER);
                } else {
                    JProgressBar progressBar = new JProgressBar();
                    progressBar.setIndeterminate(true);
                    panel.add(progressBar, BorderLayout.CENTER);
                }
                
                panel.add(label, BorderLayout.NORTH);
                
                dialog.add(panel);
                dialog.setVisible(true);
                dialogArray[0] = dialog;
            });
        } catch (Exception e) {}
        return dialogArray[0];
    }

    public static void checkForUpdates(boolean silentIfUpToDate) {
        new Thread(() -> {
            try {
                System.out.println("Checking for updates (silentIfUpToDate=" + silentIfUpToDate + ")...");
                HttpClient client = HttpClient.newBuilder()
                        .version(HttpClient.Version.HTTP_1_1) // Решает проблемы с TLS handshake
                        .build();
                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create("https://api.github.com/repos/" + GITHUB_REPO + "/releases/latest"))
                        .header("Accept", "application/vnd.github.v3+json")
                        .header("User-Agent", "KVN-Updater-App") // GitHub API требует User-Agent
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
                                // Ищем ссылку на скачивание (.zip или .dmg)
                                Pattern assetPattern = Pattern.compile("\"browser_download_url\"\\s*:\\s*\"([^\"]+)\"");
                                Matcher assetMatcher = assetPattern.matcher(json);
                                
                                if (assetMatcher.find()) {
                                    String downloadUrl = assetMatcher.group(1);
                                    JDialog progressDialog = showProgressDialog("Downloading and installing update...");
                                    downloadAndInstall(downloadUrl, progressDialog);
                                } else {
                                    showMessageBlocking("Could not find any asset in the latest release.", "Error", JOptionPane.ERROR_MESSAGE);
                                }
                            }
                        } else {
                            if (!silentIfUpToDate) {
                                showMessageBlocking("You are using the latest version (" + CURRENT_VERSION + ").", "Up to Date", JOptionPane.INFORMATION_MESSAGE);
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

    private static void downloadAndInstall(String downloadUrl, JDialog progressDialog) {
        try {
            Path downloadPath = Paths.get("/tmp/KVN_update_asset");
            boolean isZip = downloadUrl.endsWith(".zip");
            
            if (isZip) {
                downloadPath = Paths.get("/tmp/KVN_update.zip");
            } else {
                downloadPath = Paths.get("/tmp/KVN_update.dmg");
            }

            System.out.println("Downloading update from: " + downloadUrl);

            // 1. Скачиваем файл
            HttpClient client = HttpClient.newBuilder()
                    .version(HttpClient.Version.HTTP_1_1)
                    .followRedirects(HttpClient.Redirect.NORMAL)
                    .build();
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(downloadUrl))
                    .header("User-Agent", "KVN-Updater-App")
                    .GET()
                    .build();
            HttpResponse<Path> response = client.send(request, HttpResponse.BodyHandlers.ofFile(downloadPath));

            if (response.statusCode() != 200) {
                if (progressDialog != null) progressDialog.dispose();
                showMessageBlocking("Failed to download update. HTTP Status: " + response.statusCode(), "Download Error", JOptionPane.ERROR_MESSAGE);
                return;
            }

            System.out.println("Download complete. Preparing to install...");

            Path dmgPath;
            if (isZip) {
                // 2a. Распаковываем ZIP и ищем .dmg внутри
                Path extractDir = Paths.get("/tmp/KVN_update_extracted");
                // Удаляем старую папку если есть
                if (Files.exists(extractDir)) {
                    new ProcessBuilder("rm", "-rf", extractDir.toString()).start().waitFor();
                }
                Files.createDirectories(extractDir);

                ProcessBuilder unzip = new ProcessBuilder("unzip", "-o", downloadPath.toString(), "-d", extractDir.toString());
                unzip.start().waitFor();

                // Ищем .dmg файл внутри распакованной папки
                dmgPath = Files.walk(extractDir)
                        .filter(p -> p.toString().endsWith(".dmg"))
                        .findFirst()
                        .orElse(null);

                if (dmgPath == null) {
                    if (progressDialog != null) progressDialog.dispose();
                    showMessageBlocking("Could not find DMG file inside the downloaded archive.", "Error", JOptionPane.ERROR_MESSAGE);
                    return;
                }
            } else {
                dmgPath = downloadPath;
            }

            // 3. Определяем путь к текущему приложению (ищем .app)
            String appPath = getAppPath();
            if (appPath == null) {
                if (progressDialog != null) progressDialog.dispose();
                showMessageBlocking("Could not determine .app path. Aborting update.", "Error", JOptionPane.ERROR_MESSAGE);
                return;
            }

            // 4. Создаем Bash скрипт для обновления
            String volumeName = "KVN Installation";
            String logFile = "/tmp/kvn_update.log";
            long currentPid = ProcessHandle.current().pid(); // Получаем PID текущего процесса
            
            String scriptContent = "#!/bin/bash\n" +
                    "exec > \"" + logFile + "\" 2>&1\n" +  // Логируем всё в файл
                    "echo \"=== KVN Update Script ===\"\n" +
                    "echo \"Waiting for Java process " + currentPid + " to exit...\"\n" +
                    "while kill -0 " + currentPid + " 2>/dev/null; do\n" +
                    "    sleep 1\n" +
                    "done\n" +
                    "echo \"Process exited.\"\n" +
                    "\n" +
                    "# Снимаем карантин с DMG\n" +
                    "xattr -d com.apple.quarantine \"" + dmgPath + "\" 2>/dev/null\n" +
                    "\n" +
                    "# Отмонтируем том, если он завис от прошлой попытки\n" +
                    "hdiutil detach \"/Volumes/" + volumeName + "\" -force 2>/dev/null\n" +
                    "\n" +
                    "# Монтируем DMG\n" +
                    "echo \"Mounting DMG: " + dmgPath + "\"\n" +
                    "hdiutil attach \"" + dmgPath + "\" -nobrowse\n" +
                    "if [ $? -ne 0 ]; then\n" +
                    "    echo \"ERROR: Failed to mount DMG\"\n" +
                    "    open \"" + appPath + "\"\n" + // Запускаем старое, раз не вышло
                    "    exit 1\n" +
                    "fi\n" +
                    "\n" +
                    "# Проверяем что .app есть на смонтированном томе\n" +
                    "echo \"Looking for app in /Volumes/" + volumeName + "/\"\n" +
                    "if [ ! -d \"/Volumes/" + volumeName + "/KVN.app\" ]; then\n" +
                    "    echo \"ERROR: KVN.app not found on mounted volume\"\n" +
                    "    hdiutil detach \"/Volumes/" + volumeName + "\" -force 2>/dev/null\n" +
                    "    open \"" + appPath + "\"\n" +
                    "    exit 1\n" +
                    "fi\n" +
                    "\n" +
                    "# Удаляем старое приложение\n" +
                    "echo \"Removing old app: " + appPath + "\"\n" +
                    "rm -rf \"" + appPath + "\"\n" +
                    "if [ -d \"" + appPath + "\" ]; then\n" +
                    "    echo \"ERROR: Failed to delete old app (Permission denied or locked).\"\n" +
                    "    hdiutil detach \"/Volumes/" + volumeName + "\" -force 2>/dev/null\n" +
                    "    open \"" + appPath + "\"\n" +
                    "    exit 1\n" +
                    "fi\n" +
                    "\n" +
                    "# Копируем новое приложение\n" +
                    "echo \"Copying new app...\"\n" +
                    "cp -R \"/Volumes/" + volumeName + "/KVN.app\" \"" + appPath + "\"\n" +
                    "\n" +
                    "# Снимаем карантин с нового приложения\n" +
                    "xattr -dr com.apple.quarantine \"" + appPath + "\" 2>/dev/null\n" +
                    "\n" +
                    "# Отмонтируем и чистим\n" +
                    "echo \"Cleaning up...\"\n" +
                    "hdiutil detach \"/Volumes/" + volumeName + "\" -force 2>/dev/null\n" +
                    "rm -rf /tmp/KVN_update.zip /tmp/KVN_update.dmg /tmp/KVN_update_extracted\n" +
                    "\n" +
                    "# Создаем флаг успешного обновления\n" +
                    "touch /tmp/kvn_updated\n" +
                    "\n" +
                    "# Запускаем новое приложение\n" +
                    "echo \"Launching updated app...\"\n" +
                    "open \"" + appPath + "\"\n" +
                    "echo \"Done!\"\n";

            Path scriptPath = Paths.get("/tmp/kvn_update.sh");
            Files.writeString(scriptPath, scriptContent);

            // Делаем скрипт исполняемым
            Set<PosixFilePermission> perms = new HashSet<>();
            perms.add(PosixFilePermission.OWNER_READ);
            perms.add(PosixFilePermission.OWNER_WRITE);
            perms.add(PosixFilePermission.OWNER_EXECUTE);
            Files.setPosixFilePermissions(scriptPath, perms);

            // 5. Запускаем скрипт и выходим (прогресс-бар закроется вместе с приложением)
            new ProcessBuilder(scriptPath.toString()).start();
            System.exit(0);
        } catch (Exception e) {
            if (progressDialog != null) progressDialog.dispose();
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
