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


        if (!FileUtils.checkDirectoryExists(pathBase)) {
            FileUtils.createDirectory(Path.of(configPath));
            if(!Files.exists(Path.of(configPath + "AutoStartStatus.json"))){ // Проверка существования файла
                FileUtils.createfiles(Path.of(configPath + "AutoStartStatus.json"));
                try{
                System.out.println("Файла нет");
                Files.writeString(Path.of(configPath + "AutoStartStatus.json"),"{\n" + //
                                        "\"autoStart\": 0,\n" + //
                                        "\"Lol\": 2\n" + //
                                        "}");
                }catch(IOException e){
                    e.getMessage();
                }
            }
            if(!Files.exists(Path.of(configPath+ "proxy.conf"))){
                FileUtils.createfiles(Path.of(configPath + "proxy.conf"));
                try{
                Files.writeString(Path.of(configPath + "proxy.conf"),"WGConfig = " + configPath + "AmneziaConfig.conf\r\n" + //
                                        "\r\n" + //
                                        "[Socks5]\r\n" + //
                                        "BindAddress = 127.0.0.1:1080");
                }catch(IOException t){
                    t.getMessage();
                }
            }

        }else{
            if(!Files.exists(Path.of(configPath + "AutoStartStatus.json"))){ // Проверка существования файла

                FileUtils.createfiles(Path.of(configPath + "AutoStartStatus.json"));
                try{
                Files.writeString(Path.of(configPath + "AutoStartStatus.json"),"{\n" + //
                                        " \"autoStart\": 0,\n" + //
                                        " \"Lol\": 2\n" + //
                                        "}");
                }catch(IOException e){
                    e.getMessage();
                }
            }
            if(!Files.exists(Path.of(configPath + "proxy.conf"))){
                FileUtils.createfiles(Path.of(configPath + "proxy.conf"));
                try{
                Files.writeString(Path.of(configPath + "proxy.conf"),"WGConfig = " + configPath +"AmneziaConfig.conf\r\n" + //
                                        "\r\n" + //
                                        "[Socks5]\r\n" + //
                                        "BindAddress = 127.0.0.1:1080");
                }catch(IOException t){
                    t.getMessage();
                }
            }


        }

        System.setProperty("apple.awt.UIElement", "true");
        Runtime.getRuntime().addShutdownHook(new Thread(eWireproxy::stopWireproxy));

        if (!SystemTray.isSupported()) return;

        SystemTray tray = SystemTray.getSystemTray();
        Image trayImage = Icon.loadIcon();

        TrayIcon trayIcon = new TrayIcon(trayImage, "KVN");
        trayIcon.setImageAutoSize(true);

        PopupMenu menu = new PopupMenu();
        MenuItem statusItem = new MenuItem("Status: Disconnected - 🔴");
        statusItem.setEnabled(false);

        MenuItem connectItem = new MenuItem("Connect VPN");
        MenuItem disconnectItem = new MenuItem("Disconnect");
        CheckboxMenuItem autoStatrtCheckbox = new CheckboxMenuItem("Auto Connect",SettingsParser.auto_start());
            String AddFileButtonText;
        if(Files.exists(Path.of(configPath + "AmneziaConfig.conf"))){
            AddFileButtonText = "Remove Config";
        }else{
            AddFileButtonText = "Add Config...";
        }
        MenuItem addFileAndRemove = new MenuItem(AddFileButtonText);
        disconnectItem.setEnabled(false);
        MenuItem exitItem = new MenuItem("Exit");




        autoStatrtCheckbox.addItemListener(e -> {
            boolean status = autoStatrtCheckbox.getState();
            if(status){
                FileUtils.AutoStartStatusrWrite(true,pathBase);
                //System.out.println("Включено");
            }else{
                FileUtils.AutoStartStatusrWrite(false,pathBase);
                //System.out.println("Отключено");
            }

        });

       addFileAndRemove.addActionListener(e->{

        if(addFileAndRemove.getLabel().equals("Add Config...")){

            FileDialog fd = new FileDialog((Frame)null,"Add file", FileDialog.LOAD);
            fd.setVisible(true);
            String directory = fd.getDirectory();
            String filename = fd.getFile();
            String fullPath = directory + filename;
            FileUtils.copyFile(Path.of(fullPath),Path.of(configPath + "AmneziaConfig.conf"));
            //System.out.println("Выбран файл " + fullPath);
            //FileUtils.copyFile("","");
       addFileAndRemove.setLabel("Remove Config");
        }else{
            try{
                Files.delete(Path.of(configPath + "AmneziaConfig.conf"));
            }catch(IOException a){
            a.getMessage();

            }

            addFileAndRemove.setLabel("Add Config...");
        }
       });
       disconnectItem.addActionListener(e -> {
            addFileAndRemove.setEnabled(true); // Делаем кнопку Remove Config активной
            eWireproxy.stopWireproxy();
            statusItem.setLabel("Status: Disconnected 🔴");
            connectItem.setEnabled(true);
            disconnectItem.setEnabled(false);
        });
        exitItem.addActionListener(e -> {
            eWireproxy.stopWireproxy();
            System.exit(0);
        });
        connectItem.addActionListener(e -> {
            statusItem.setLabel("Status: Connecting...");

            // Сначала пробуем запустить наш Go бинарник
            if (eWireproxy.startWireproxy()) {
                addFileAndRemove.setEnabled(false); // Далаем кнопку Remove Config не активной
                statusItem.setLabel("Status: Connected 🟢");
                connectItem.setEnabled(false);
                disconnectItem.setEnabled(true);
            } else {
                System.out.println("Ошибка запуска утилиты");
            }
        });





        // Логика работы автозапуска vpn при запуске утилиты
         if(SettingsParser.auto_start()&&Files.exists(Path.of(configPath + "proxy.conf"))&&Files.exists(Path.of(configPath + "AmneziaConfig.conf"))){ // Проверка наличия автозапуска
            if (eWireproxy.startWireproxy()) {
                addFileAndRemove.setEnabled(false); // Далаем кнопку Remove Config не активной
                statusItem.setLabel("Status: Connected 🟢");
                connectItem.setEnabled(false);
                disconnectItem.setEnabled(true);
            } else {
                System.out.println("Ошибка запуска утилиты");
            }

        }





        menu.add(statusItem); // Список элементов в интерфейсе
        menu.addSeparator();
        menu.add(connectItem);
        menu.add(disconnectItem);
        menu.add(autoStatrtCheckbox); // Чекбокс автозапуска
        menu.add(addFileAndRemove);
        menu.addSeparator();
        menu.add(exitItem);

        trayIcon.setPopupMenu(menu);
        try { tray.add(trayIcon); } catch (AWTException e) { e.printStackTrace(); }
    }



}