package org.example;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.*;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;

import javax.imageio.ImageIO;

public class App {


    public static void main(String[] args) {
        String userName = System.getProperty("user.name");
        String configPath = "/Users/" + userName + "/KVN/";
        Path pathBase = Path.of(configPath); // Базовый путь к директории KVN
        Wireproxy eWireproxy = new Wireproxy(pathBase);
        StatusBarMenu statusBarMenu = new StatusBarMenu(pathBase, pathBase, eWireproxy);


        FileCheck fileCheck = new FileCheck(pathBase,configPath);
        fileCheck.startStart();

        // Проверяем, было ли только что установлено обновление
        if (Files.exists(Path.of("/tmp/kvn_updated"))) {
            try {
                Files.delete(Path.of("/tmp/kvn_updated"));
                javax.swing.SwingUtilities.invokeLater(() -> {
                    javax.swing.JFrame topFrame = new javax.swing.JFrame();
                    topFrame.setAlwaysOnTop(true);
                    javax.swing.JOptionPane.showMessageDialog(topFrame, 
                        "Update successfully installed! You are now running version " + Updater.CURRENT_VERSION, 
                        "Update Successful", 
                        javax.swing.JOptionPane.INFORMATION_MESSAGE);
                    topFrame.dispose();
                });
            } catch (Exception ignored) {}
        }

        System.setProperty("apple.awt.UIElement", "true");
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            eWireproxy.stopWireproxy();
            OperaProxy.stopOperaProxy();
        }));

        if (!SystemTray.isSupported()) return;

        SystemTray tray = SystemTray.getSystemTray();
        Image trayImage = Icon.loadIcon();

        TrayIcon trayIcon = new TrayIcon(trayImage, "KVN");
        trayIcon.setImageAutoSize(true);

        statusBarMenu.itemCreate();
        statusBarMenu.runActionListener();
        statusBarMenu.addPopupMenu(); // Это критически важно для отрисовки меню

        AutoConnect.run(eWireproxy, statusBarMenu, configPath);
        
        // После AutoConnect нужно обновить меню, так как статус мог измениться
        statusBarMenu.addPopupMenu(); 

        trayIcon.setPopupMenu(statusBarMenu.menu);
        try { tray.add(trayIcon); } catch (AWTException e) { e.printStackTrace(); }

        // Автоматически проверяем наличие обновлений при старте
        // Передаем true, чтобы не беспокоить пользователя попапами "Нет обновлений" или ошибками сети
        Updater.checkForUpdates(true);
    }



}