package org.example;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.net.URL;
import javax.imageio.ImageIO;

public class App {
    public static void main(String[] args) {
        // 1. Скрываем иконку из Дока (только статус-бар)
        System.setProperty("apple.awt.UIElement", "true");

        if (!SystemTray.isSupported()) {
            System.out.println("Статус-бар не поддерживается на этой системе");
            return;
        }

        SystemTray tray = SystemTray.getSystemTray();
        Image trayImage = null;

        try {
            // 2. Пытаемся загрузить твою неоновую иконку из ресурсов
            URL imageURL = App.class.getResource("/kvn_logo.png");
            
            if (imageURL != null) {
                trayImage = ImageIO.read(imageURL);
            } else {
                // 3. Запасной вариант: если файла нет, рисуем красный круг с прозрачностью
                System.out.println("Файл kvn_logo.png не найден в resources. Рисую временный маркер.");
                BufferedImage temp = new BufferedImage(18, 18, BufferedImage.TYPE_INT_ARGB);
                Graphics2D g = temp.createGraphics();
                
                // Включаем сглаживание, чтобы круг был ровным
                g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g.setColor(Color.RED);
                g.fillOval(2, 2, 14, 14); 
                g.dispose();
                trayImage = temp;
            }
        } catch (Exception e) {
            e.printStackTrace();
            return;
        }

        // 4. Создаем иконку в трее
        TrayIcon trayIcon = new TrayIcon(trayImage, "KVN Status Tool");
        trayIcon.setImageAutoSize(true); // Масштабирует картинку под размер панели Mac

        // 5. Создаем меню
        PopupMenu menu = new PopupMenu();
        
        MenuItem infoItem = new MenuItem("KVN Project: Active");
        infoItem.setEnabled(false); // Просто текст, нажать нельзя
        
        MenuItem exitItem = new MenuItem("Exit");
        exitItem.addActionListener(e -> System.exit(0));

        menu.add(infoItem);
        menu.addSeparator();
        menu.add(exitItem);

        trayIcon.setPopupMenu(menu);

        try {
            tray.add(trayIcon);
            System.out.println("Утилита успешно запущена!");
        } catch (AWTException e) {
            System.err.println("Не удалось добавить иконку в трей.");
        }
    }
}