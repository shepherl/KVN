package org.example; // Проверь свой пакет!

import java.awt.*;
import java.awt.image.BufferedImage;

public class App {
    public static void main(String[] args) {
        if (!SystemTray.isSupported()) {
            System.out.println("Статус-бар не поддерживается");
            return;
        }

        SystemTray tray = SystemTray.getSystemTray();
        
        // Рисуем простую иконку 16x16
        BufferedImage image = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = image.createGraphics();
        g.setColor(Color.RED);
        g.fillRect(0, 0, 16, 16);
        g.dispose();

        TrayIcon trayIcon = new TrayIcon(image, "My Tool");
        trayIcon.setImageAutoSize(true);

        // Добавляем простое меню выхода
        PopupMenu menu = new PopupMenu();
        MenuItem exitItem = new MenuItem("Exit");
        exitItem.addActionListener(e -> System.exit(0));
        menu.add(exitItem);
        trayIcon.setPopupMenu(menu);

        try {
            tray.add(trayIcon);
            System.out.println("Приложение запущено в статус-баре!");
        } catch (AWTException e) {
            e.printStackTrace();
        }
    }
}