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

        System.setProperty("apple.awt.UIElement", "true");
        Runtime.getRuntime().addShutdownHook(new Thread(eWireproxy::stopWireproxy));

        if (!SystemTray.isSupported()) return;

        SystemTray tray = SystemTray.getSystemTray();
        Image trayImage = Icon.loadIcon();

        TrayIcon trayIcon = new TrayIcon(trayImage, "KVN");
        trayIcon.setImageAutoSize(true);

        statusBarMenu.itemCreate();
        statusBarMenu.runActionListener();
        statusBarMenu.addPopupMenu();
        
        // Логика работы автозапуска vpn при запуске утилиты
         if(SettingsParser.auto_start()&&Files.exists(Path.of(configPath + "proxy.conf"))&&Files.exists(Path.of(configPath + "AmneziaConfig.conf"))){ // Проверка наличия автозапуска
            if (eWireproxy.startWireproxy()) {
                statusBarMenu.addFileAndRemove.setEnabled(false); // Далаем кнопку Remove Config не активной
                statusBarMenu.statusItem.setLabel("Status: Connected 🟢");
                statusBarMenu.connectItem.setEnabled(false);
                statusBarMenu.disconnectItem.setEnabled(true);
            } else {
                System.out.println("Ошибка запуска утилиты");
            }

        }

        trayIcon.setPopupMenu(statusBarMenu.menu);
        try { tray.add(trayIcon); } catch (AWTException e) { e.printStackTrace(); }
    }



}