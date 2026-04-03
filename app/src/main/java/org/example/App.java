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
        // 1. Скрываем иконку из Дока
        System.setProperty("apple.awt.UIElement", "true");

        if (!SystemTray.isSupported()) {
            return;
        }

        SystemTray tray = SystemTray.getSystemTray();
        Image trayImage = loadIcon();

        // 4. Создаем иконку в трее
        TrayIcon trayIcon = new TrayIcon(trayImage, "KVN Status Tool");
        trayIcon.setImageAutoSize(true);

        // 5. Создаем меню
        PopupMenu menu = new PopupMenu();
        
        MenuItem statusItem = new MenuItem("Status: Disconnected");
        statusItem.setEnabled(false);
        
        MenuItem connectItem = new MenuItem("Connect VPN");
        MenuItem disconnectItem = new MenuItem("Disconnect");
        disconnectItem.setEnabled(false);

        // Логика кнопки Connect
        connectItem.addActionListener(e -> {
            if (startWireproxy()) {
                statusItem.setLabel("Status: Connected (Go Active)");
                connectItem.setEnabled(false);
                disconnectItem.setEnabled(true);
            }
        });

        // Логика кнопки Disconnect
        disconnectItem.addActionListener(e -> {
            stopWireproxy();
            statusItem.setLabel("Status: Disconnected");
            connectItem.setEnabled(true);
            disconnectItem.setEnabled(false);
        });
        
        MenuItem exitItem = new MenuItem("Exit");
        exitItem.addActionListener(e -> {
            stopWireproxy(); // Важно убить Go перед выходом!
            System.exit(0);
        });

        menu.add(statusItem);
        menu.addSeparator();
        menu.add(connectItem);
        menu.add(disconnectItem);
        menu.addSeparator();
        menu.add(exitItem);

        trayIcon.setPopupMenu(menu);

        try {
            tray.add(trayIcon);
        } catch (AWTException e) {
            e.printStackTrace();
        }
    }

    // Метод для запуска Go бинарника
    private static boolean startWireproxy() {
        try {
            // Ищем бинарник там, куда его положил jpackage (Contents/Java/)
            String jarDir = new File(App.class.getProtectionDomain().getCodeSource().getLocation().toURI()).getParent();
            File proxyFile = new File(jarDir, "wireproxy");

            if (!proxyFile.exists()) {
                System.err.println("Бинарник wireproxy не найден по пути: " + proxyFile.getAbsolutePath());
                return false;
            }

            // Запускаем. Убедись, что конфиг vpn.conf лежит в той же папке или укажи путь.
            ProcessBuilder pb = new ProcessBuilder(proxyFile.getAbsolutePath(), "-config", "vpn.conf");
            pb.directory(new File(jarDir)); // Устанавливаем рабочую папку
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
            
            // Рисуем заглушку, если картинки нет
            BufferedImage temp = new BufferedImage(18, 18, BufferedImage.TYPE_INT_ARGB);
            Graphics2D g = temp.createGraphics();
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setColor(Color.RED);
            g.fillOval(2, 2, 14, 14);
            g.dispose();
            return temp;
        } catch (IOException e) {
            return null;
        }
    }
}