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

        connectItem.addActionListener(e -> {
            if (startWireproxy()) {
                statusItem.setLabel("Status: Connected");
                connectItem.setEnabled(false);
                disconnectItem.setEnabled(true);
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
            // 1. Пытаемся найти папку приложения (Contents/app)
            String appDir = System.getProperty("user.dir");
            File proxyFile = new File(appDir, "wireproxy");

            // Резервный поиск, если запуск идет не из-под jpackage
            if (!proxyFile.exists()) {
                String jarPath = App.class.getProtectionDomain().getCodeSource().getLocation().toURI().getPath();
                appDir = new File(jarPath).getParent();
                proxyFile = new File(appDir, "wireproxy");
            }

            System.out.println("DEBUG: Working Dir: " + appDir);
            if (!proxyFile.exists()) {
                System.err.println("CRITICAL: wireproxy not found at " + proxyFile.getAbsolutePath());
                return false;
            }

            proxyFile.setExecutable(true);

            // 2. Запуск процесса с захватом вывода для отладки
            ProcessBuilder pb = new ProcessBuilder(proxyFile.getAbsolutePath(), "-config", "proxy.conf");
            pb.directory(new File(appDir)); 
            pb.redirectErrorStream(true);
            
            wireproxyProcess = pb.start();

            // 3. Поток для чтения логов (чтобы понять, почему не подключается)
            new Thread(() -> {
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(wireproxyProcess.getInputStream()))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        System.out.println("GO_LOG: " + line);
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