package org.example;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.*;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;

import javax.imageio.ImageIO;

import org.example.ota.Updater;

public class App {


    public static void main(String[] args) {
        String userName = System.getProperty("user.name");
        String configPath = "/Users/" + userName + "/KVN/";
        Path pathBase = Path.of(configPath); // Базовый путь к директории KVN
        Wireproxy eWireproxy = new Wireproxy(pathBase);
        StatusBarMenu statusBarMenu = new StatusBarMenu(pathBase, pathBase, eWireproxy);


        FileCheck fileCheck = new FileCheck(pathBase,configPath);
        fileCheck.startStart();

        // Проверяем, не запустились ли мы сразу после успешного обновления
        for (String arg : args) {
            if ("--update-success".equals(arg)) {
                Updater.showSuccessDialog();
                break;
            }
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