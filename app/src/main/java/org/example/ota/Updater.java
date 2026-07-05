package org.example.ota;

import java.awt.BorderLayout;
import java.awt.Color;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.PosixFilePermission;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.swing.BorderFactory;
import javax.swing.JDialog;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.SwingUtilities;

/**
 * Класс, отвечающий за автоматическое (OTA) обновление приложения.
 * Связывается с GitHub API, проверяет наличие новых релизов.
 * Скачивание и установка теперь происходит полностью невидимо в фоне,
 * без использования сторонних бинарников.
 */
public class Updater {

    private static final String GITHUB_REPO = "shepherl/kvnfaq"; 
    public static final String CURRENT_VERSION = "1.7"; 

    private static String runAppleScript(String script) throws Exception {
        Process process = new ProcessBuilder("osascript", "-").start();
        process.getOutputStream().write(script.getBytes("UTF-8"));
        process.getOutputStream().close();
        process.waitFor();
        
        if (process.exitValue() != 0) {
            try (java.util.Scanner s = new java.util.Scanner(process.getErrorStream()).useDelimiter("\\A")) {
                String error = s.hasNext() ? s.next() : "Unknown error";
                throw new Exception("AppleScript error: " + error);
            }
        }
        
        try (java.util.Scanner s = new java.util.Scanner(process.getInputStream()).useDelimiter("\\A")) {
            return s.hasNext() ? s.next() : "";
        }
    }

    private static void showMessageBlocking(String message, String title, int messageType) {
        try {
            StringBuilder sb = new StringBuilder();
            sb.append("activate\n"); 
            
            // Выбираем встроенную иконку AppleScript в зависимости от типа сообщения
            String iconType = "note";
            if (messageType == JOptionPane.ERROR_MESSAGE) {
                iconType = "stop";
            } else if (messageType == JOptionPane.WARNING_MESSAGE) {
                iconType = "caution";
            }
            
            sb.append("display dialog \"").append(message).append("\" with title \"").append(title)
              .append("\" buttons {\"OK\"} default button \"OK\" with icon ").append(iconType);
              
            runAppleScript(sb.toString());
        } catch (Exception e) {
            System.err.println("AppleScript failed, falling back to Java: " + e.getMessage());
            JFrame topFrame = new JFrame();
            topFrame.setAlwaysOnTop(true);
            JOptionPane.showMessageDialog(topFrame, message, title, messageType);
            topFrame.dispose();
        }
    }

    private static int showConfirmBlocking(String message, String title) {
        try {
            StringBuilder sb = new StringBuilder();
            sb.append("activate\n");
            
            sb.append("display dialog \"").append(message).append("\" with title \"").append(title)
              .append("\" buttons {\"Нет\", \"Да\"} default button \"Да\" with icon note");
            
            String result = runAppleScript(sb.toString());
            if (result.contains("Да")) {
                return JOptionPane.YES_OPTION;
            } else {
                return JOptionPane.NO_OPTION;
            }
        } catch (Exception e) {
            System.err.println("AppleScript failed, falling back to Java: " + e.getMessage());
            JFrame topFrame = new JFrame();
            topFrame.setAlwaysOnTop(true);
            int result = JOptionPane.showConfirmDialog(topFrame, message, title, JOptionPane.YES_NO_OPTION);
            topFrame.dispose();
            return result;
        }
    }

    public static void showSuccessDialog() {
        showMessageBlocking("Обновление до новой версии прошло успешно!", "KVN Обновлён", JOptionPane.INFORMATION_MESSAGE);
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
                                    downloadAndInstallInvisible(downloadUrl);
                                } else {
                                    showMessageBlocking("Не удалось найти файл в релизе.", "Ошибка", JOptionPane.ERROR_MESSAGE);
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
                        showMessageBlocking("Не удалось проверить обновления. Статус: " + response.statusCode(), "Ошибка", JOptionPane.ERROR_MESSAGE);
                    }
                }
            } catch (Exception e) {
                if (!silentIfUpToDate) {
                    showMessageBlocking("Ошибка при проверке обновлений: " + e.getMessage(), "Ошибка", JOptionPane.ERROR_MESSAGE);
                }
            }
        }).start();
    }

    private static void downloadAndInstallInvisible(String downloadUrl) {
        String appPath = getAppPath();
        if (appPath == null) {
            showMessageBlocking("Не удалось определить путь к приложению.", "Ошибка", JOptionPane.ERROR_MESSAGE);
            return;
        }

        JDialog dialog = new JDialog();
        dialog.setAlwaysOnTop(true);
        dialog.setUndecorated(true);
        dialog.setSize(400, 110);
        dialog.setLocationRelativeTo(null);
        dialog.setDefaultCloseOperation(JDialog.DO_NOTHING_ON_CLOSE);
        
        // Скругленные углы
        try {
            dialog.setShape(new java.awt.geom.RoundRectangle2D.Double(0, 0, 400, 110, 20, 20));
        } catch (Exception ignored) {}
        
        JPanel panel = new JPanel(new BorderLayout(15, 15));
        panel.setBorder(BorderFactory.createEmptyBorder(25, 25, 25, 25));
        panel.setBackground(new Color(35, 35, 35));
        
        JLabel label = new JLabel("Скачивание обновления KVN...");
        label.setFont(new java.awt.Font("SansSerif", java.awt.Font.BOLD, 15));
        label.setForeground(new Color(230, 230, 230));
        label.setHorizontalAlignment(JLabel.CENTER);
        panel.add(label, BorderLayout.NORTH);
        
        JProgressBar pb = new JProgressBar(0, 100);
        pb.setStringPainted(true);
        pb.setFont(new java.awt.Font("SansSerif", java.awt.Font.PLAIN, 12));
        pb.setUI(new javax.swing.plaf.basic.BasicProgressBarUI() {
            protected Color getSelectionBackground() { return Color.WHITE; }
            protected Color getSelectionForeground() { return Color.WHITE; }
        });
        pb.setForeground(new Color(10, 132, 255)); // macOS Blue
        pb.setBackground(new Color(60, 60, 60));
        pb.setBorderPainted(false);
        panel.add(pb, BorderLayout.CENTER);
        
        dialog.add(panel);
        dialog.setVisible(true);

        new Thread(() -> {
            try {
                boolean isZip = downloadUrl.toLowerCase().endsWith(".zip");
                Path downloadDest = Paths.get(isZip ? "/tmp/KVN_update.zip" : "/tmp/KVN_update.dmg");
                
                URL url = new URL(downloadUrl);
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestProperty("User-Agent", "KVN-Updater");
                int fileSize = conn.getContentLength();
                
                try (InputStream in = conn.getInputStream(); 
                     OutputStream out = new FileOutputStream(downloadDest.toFile())) {
                    byte[] buffer = new byte[8192];
                    int bytesRead;
                    long totalRead = 0;
                    while ((bytesRead = in.read(buffer)) != -1) {
                        out.write(buffer, 0, bytesRead);
                        totalRead += bytesRead;
                        if (fileSize > 0) {
                            int percent = (int) ((totalRead * 100) / fileSize);
                            SwingUtilities.invokeLater(() -> pb.setValue(percent));
                        }
                    }
                }
                
                SwingUtilities.invokeLater(() -> {
                    label.setText("Перезапуск...");
                    pb.setIndeterminate(true);
                });
                
                // Создаем невидимый bash-скрипт для установки
                String extractDir = "/tmp/KVN_extracted";
                String scriptContent;
                if (isZip) {
                    scriptContent = "#!/bin/bash\n"
                        + "exec > /tmp/kvn_update_log.txt 2>&1\n" // Записываем весь лог
                        + "set -x\n"
                        + "PID=" + ProcessHandle.current().pid() + "\n"
                        + "while kill -0 $PID 2>/dev/null; do sleep 0.5; done\n"
                        + "rm -rf \"" + extractDir + "\"\n"
                        + "mkdir -p \"" + extractDir + "\"\n"
                        + "unzip -q \"" + downloadDest + "\" -d \"" + extractDir + "\"\n"
                        + "DMG_FILE=$(find \"" + extractDir + "\" -name \"*.dmg\" | head -n 1)\n"
                        + "hdiutil detach \"/Volumes/KVN Installation\" -force 2>/dev/null\n"
                        + "hdiutil attach \"$DMG_FILE\" -mountpoint \"/Volumes/KVN Installation\" -nobrowse\n"
                        + "if [ -d \"/Volumes/KVN Installation/KVN.app\" ]; then\n"
                        + "    rm -rf \"" + appPath + "\"\n"
                        + "    cp -R \"/Volumes/KVN Installation/KVN.app\" \"" + appPath + "\"\n"
                        + "    xattr -dr com.apple.quarantine \"" + appPath + "\" 2>/dev/null\n"
                        + "fi\n"
                        + "hdiutil detach \"/Volumes/KVN Installation\" -force 2>/dev/null\n"
                        + "rm -rf \"" + extractDir + "\" \"" + downloadDest + "\"\n"
                        + "open \"" + appPath + "\" --args --update-success\n";
                } else {
                    scriptContent = "#!/bin/bash\n"
                        + "exec > /tmp/kvn_update_log.txt 2>&1\n" // Записываем весь лог
                        + "set -x\n"
                        + "PID=" + ProcessHandle.current().pid() + "\n"
                        + "while kill -0 $PID 2>/dev/null; do sleep 0.5; done\n"
                        + "hdiutil detach \"/Volumes/KVN Installation\" -force 2>/dev/null\n"
                        + "hdiutil attach \"" + downloadDest + "\" -mountpoint \"/Volumes/KVN Installation\" -nobrowse\n"
                        + "if [ -d \"/Volumes/KVN Installation/KVN.app\" ]; then\n"
                        + "    rm -rf \"" + appPath + "\"\n"
                        + "    cp -R \"/Volumes/KVN Installation/KVN.app\" \"" + appPath + "\"\n"
                        + "    xattr -dr com.apple.quarantine \"" + appPath + "\" 2>/dev/null\n"
                        + "fi\n"
                        + "hdiutil detach \"/Volumes/KVN Installation\" -force 2>/dev/null\n"
                        + "rm -rf \"" + downloadDest + "\"\n"
                        + "open \"" + appPath + "\" --args --update-success\n";
                }
                
                Path scriptPath = Paths.get("/tmp/kvn_update.sh");
                Files.writeString(scriptPath, scriptContent);
                Files.setPosixFilePermissions(scriptPath, Set.of(
                    PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE, PosixFilePermission.OWNER_EXECUTE
                ));
                
                // Запускаем скрытый скрипт в фоне и моментально завершаем приложение!
                new ProcessBuilder(scriptPath.toString()).start();
                System.exit(0);
                
            } catch (Exception e) {
                SwingUtilities.invokeLater(() -> {
                    dialog.dispose();
                    showMessageBlocking("Ошибка при скачивании/установке: " + e.getMessage(), "Ошибка", JOptionPane.ERROR_MESSAGE);
                });
            }
        }).start();
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
