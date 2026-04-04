package org.example;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.net.URL;
import javax.imageio.ImageIO;

public class App {
    private static Process wireproxyProcess = null;

    public static void main(String[] args) {
        // Скрываем иконку из Дока
        System.setProperty("apple.awt.UIElement", "true");

        // Убиваем VPN при выходе
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
            // В macOS .app пакете файлы лежат в Contents/app
            String appDir = System.getProperty("user.dir");
            File proxyFile = new File(appDir, "wireproxy");

            // Если запускаем не из .app (например, в IDE), ищем в корне
            if (!proxyFile.exists()) {
                proxyFile = new File("input_libs/wireproxy/wireproxy");
                appDir = proxyFile.getParent();
            }

            if (!proxyFile.exists()) {
                System.err.println("Binary not found at: " + proxyFile.getAbsolutePath());
                return false;
            }

            // Даем права на запуск
            proxyFile.setExecutable(true);

            // Запускаем wireproxy с конфигом proxy.conf
            // Рабочая директория (directory) важна, чтобы он увидел второй конфиг WARP...
            ProcessBuilder pb = new ProcessBuilder(proxyFile.getAbsolutePath(), "-config", "proxy.conf");
            pb.directory(new File(appDir)); 
            pb.redirectErrorStream(true);
            
            wireproxyProcess = pb.start();
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