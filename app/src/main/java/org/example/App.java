package org.example;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.*;
import java.net.URL;
import javax.imageio.ImageIO;

public class App {
    private static Process wireproxyProcess = null;

    public static void main(String[] args) {
        System.setProperty("apple.awt.UIElement", "true");
        Runtime.getRuntime().addShutdownHook(new Thread(App::stopWireproxy));

        if (!SystemTray.isSupported()) return;

        SystemTray tray = SystemTray.getSystemTray();
        Image trayImage = loadIcon();

        TrayIcon trayIcon = new TrayIcon(trayImage, "KVN Status Tool");
        trayIcon.setImageAutoSize(true);

        PopupMenu menu = new PopupMenu();
        MenuItem statusItem = new MenuItem("Status: Disconnected");
        statusItem.setEnabled(false);
        
        MenuItem connectItem = new MenuItem("Connect VPN");
        MenuItem disconnectItem = new MenuItem("Disconnect");
        disconnectItem.setEnabled(false);

        // --- ИСПРАВЛЕННАЯ ЛОГИКА ТУТ ---
        connectItem.addActionListener(e -> {
            statusItem.setLabel("Status: Connecting...");
            
            // Сначала пробуем запустить наш Go бинарник
            if (startWireproxy()) {
                statusItem.setLabel("Status: Connected (Go Active)");
                connectItem.setEnabled(false);
                disconnectItem.setEnabled(true);
            } else {
                // Если не вышло, пробуем открыть калькулятор как запасной тест
                statusItem.setLabel("Status: Error! Opening Calc...");
                try {
                    Runtime.getRuntime().exec("open -a Calculator");
                } catch (IOException ex) {
                    ex.printStackTrace();
                }
            }
        });

        disconnectItem.addActionListener(e -> {
            stopWireproxy();
            statusItem.setLabel("Status: Disconnected");
            connectItem.setEnabled(true);
            disconnectItem.setEnabled(false);
        });
        
        MenuItem exitItem = new MenuItem("Exit");
        exitItem.addActionListener(e -> {
            stopWireproxy();
            System.exit(0);
        });

        menu.add(statusItem);
        menu.addSeparator();
        menu.add(connectItem);
        menu.add(disconnectItem);
        menu.addSeparator();
        menu.add(exitItem);

        trayIcon.setPopupMenu(menu);
        try { tray.add(trayIcon); } catch (AWTException e) { e.printStackTrace(); }
    }

    private static boolean startWireproxy() {
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
            ProcessBuilder pb = new ProcessBuilder(proxyFile.getAbsolutePath(), "-c", "proxy.conf");
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

    private static void stopWireproxy() {
        if (wireproxyProcess != null && wireproxyProcess.isAlive()) {
            wireproxyProcess.destroy();
            System.out.println("DEBUG: wireproxy stopped.");
        }
    }

    private static Image loadIcon() {
        try {
            URL imageURL = App.class.getResource("/kvn_logo.png");
            if (imageURL != null) return ImageIO.read(imageURL);
            BufferedImage temp = new BufferedImage(18, 18, BufferedImage.TYPE_INT_ARGB);
            Graphics2D g = temp.createGraphics();
            g.setColor(Color.RED);
            g.fillOval(2, 2, 14, 14);
            g.dispose();
            return temp;
        } catch (Exception e) { return null; }
    }
}