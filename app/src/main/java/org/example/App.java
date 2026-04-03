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
        // 1. Скрываем иконку из Дока (только статус-бар)
        System.setProperty("apple.awt.UIElement", "true");

        // 2. Гарантированно убиваем Go-процесс при выходе из системы или Cmd+Q
        Runtime.getRuntime().addShutdownHook(new Thread(App::stopWireproxy));

        if (!SystemTray.isSupported()) {
            System.err.println("SystemTray is not supported on this system.");
            return;
        }

        SystemTray tray = SystemTray.getSystemTray();
        Image trayImage = loadIcon();

        TrayIcon trayIcon = new TrayIcon(trayImage, "KVN Status Tool");
        trayIcon.setImageAutoSize(true);

        // 3. Создаем меню
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

        try {
            tray.add(trayIcon);
        } catch (AWTException e) {
            e.printStackTrace();
        }
    }

    private static boolean startWireproxy() {
        try {
            // Динамически находим папку, где лежит JAR (Contents/Java/ в .app пакете)
            String jarDir = new File(App.class.getProtectionDomain().getCodeSource().getLocation().toURI()).getParent();
            File proxyFile = new File(jarDir, "wireproxy");

            if (!proxyFile.exists()) {
                System.err.println("Бинарник не найден: " + proxyFile.getAbsolutePath());
                return false;
            }

            // ПРАВА ДОСТУПА: Даем права на запуск, если они слетели при упаковке
            proxyFile.setExecutable(true);

            // ЗАПУСК: vpn.conf должен лежать в той же папке (мы это прописали в YAML)
            ProcessBuilder pb = new ProcessBuilder(proxyFile.getAbsolutePath(), "-config", "proxy.conf");
            pb.directory(new File(jarDir)); 
            
            // Перенаправляем вывод ошибок в консоль для отладки
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
            System.out.println("Wireproxy process terminated.");
        }
    }

    private static Image loadIcon() {
        try {
            URL imageURL = App.class.getResource("/kvn_logo.png");
            if (imageURL != null) return ImageIO.read(imageURL);
            
            // Заглушка (Красный круг), если картинка не найдена
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