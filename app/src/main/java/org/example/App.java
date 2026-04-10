package org.example;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.*;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;

import javax.imageio.ImageIO;

public class App {
    private static Process wireproxyProcess = null;

    public static void main(String[] args) {


        if (!FileUtils.checkDirectoryExists()) {
            FileUtils.createDirectory(Path.of("/Users/shepherl/KVN/"));
            if(!Files.exists(Path.of("/Users/shepherl/KVN/AutoStartStatus.json"))){ // Проверка существования файла
                FileUtils.createfiles(Path.of("/Users/shepherl/KVN/AutoStartStatus.json"));
                try{
                System.out.println("Файла нет");
                Files.writeString(Path.of("/Users/shepherl/KVN/AutoStartStatus.json"),"{\n" + //
                                        "\"autoStart\": 0,\n" + //
                                        "\"Lol\": 2\n" + //
                                        "}");
                }catch(IOException e){
                    e.getMessage();
                }
            }
            if(!Files.exists(Path.of("/Users/shepherl/KVN/proxy.conf"))){
                FileUtils.createfiles(Path.of("/Users/shepherl/KVN/proxy.conf"));
                try{
                Files.writeString(Path.of("/Users/shepherl/KVN/proxy.conf"),"WGConfig = WARPw13768.conf\r\n" + //
                                        "\r\n" + //
                                        "[Socks5]\r\n" + //
                                        "BindAddress = 127.0.0.1:1080");
                }catch(IOException t){
                    t.getMessage();
                }
            }

        }else{
            if(!Files.exists(Path.of("/Users/shepherl/KVN/AutoStartStatus.json"))){ // Проверка существования файла

                FileUtils.createfiles(Path.of("/Users/shepherl/KVN/AutoStartStatus.json"));
                try{
                Files.writeString(Path.of("/Users/shepherl/KVN/AutoStartStatus.json"),"{\n" + //
                                        " \"autoStart\": 0,\n" + //
                                        " \"Lol\": 2\n" + //
                                        "}");
                }catch(IOException e){
                    e.getMessage();
                }
            }
            if(!Files.exists(Path.of("/Users/shepherl/KVN/proxy.conf"))){
                FileUtils.createfiles(Path.of("/Users/shepherl/KVN/proxy.conf"));
                try{
                Files.writeString(Path.of("/Users/shepherl/KVN/proxy.conf"),"WGConfig = /Users/shepherl/KVN/AmneziaConfig.conf\r\n" + //
                                        "\r\n" + //
                                        "[Socks5]\r\n" + //
                                        "BindAddress = 127.0.0.1:1080");
                }catch(IOException t){
                    t.getMessage();
                }
            }


        }

        System.setProperty("apple.awt.UIElement", "true");
        Runtime.getRuntime().addShutdownHook(new Thread(App::stopWireproxy));

        if (!SystemTray.isSupported()) return;

        SystemTray tray = SystemTray.getSystemTray();
        Image trayImage = loadIcon();

        TrayIcon trayIcon = new TrayIcon(trayImage, "KVN");
        trayIcon.setImageAutoSize(true);

        PopupMenu menu = new PopupMenu();
        MenuItem statusItem = new MenuItem("Status: Disconnected");
        statusItem.setEnabled(false);

        MenuItem connectItem = new MenuItem("Connect VPN");
        MenuItem disconnectItem = new MenuItem("Disconnect");
        CheckboxMenuItem autoStatrtCheckbox = new CheckboxMenuItem("Автозапуск",SettingsParser.auto_start());
            String AddFileButtonText;
        if(Files.exists(Path.of("/Users/shepherl/KVN/AmneziaConfig.conf"))){
            AddFileButtonText = "Удалить файл...";
        }else{
            AddFileButtonText = "Вставить файл...";
        }
        MenuItem addFileAndRemove = new MenuItem(AddFileButtonText);
        disconnectItem.setEnabled(false);




        autoStatrtCheckbox.addItemListener(e -> {
            boolean status = autoStatrtCheckbox.getState();
            if(status){
                FileUtils.AutoStartStatusrWrite(true);
                //System.out.println("Включено");
            }else{
                FileUtils.AutoStartStatusrWrite(false);
                //System.out.println("Отключено");
            }

        });

       addFileAndRemove.addActionListener(e->{

        if(addFileAndRemove.getLabel().equals("Вставить файл...")){
            String userName = System.getProperty("user.name");
            System.out.println(userName);
            FileDialog fd = new FileDialog((Frame)null,"Выберите файл", FileDialog.LOAD);
            fd.setVisible(true);
            String directory = fd.getDirectory();
            String filename = fd.getFile();
            String fullPath = directory + filename;
            FileUtils.copyFile(Path.of(fullPath),Path.of("/Users/shepherl/KVN/AmneziaConfig.conf"));
            //System.out.println("Выбран файл " + fullPath);
            //FileUtils.copyFile("","");
       addFileAndRemove.setLabel("Удалить файл...");
        }else{
            try{
                Files.delete(Path.of("/Users/shepherl/KVN/AmneziaConfig.conf"));
            }catch(IOException a){
            a.getMessage();

            }

            addFileAndRemove.setLabel("Вставить файл...");
        }
       });






        // Логика работы автозапуска vpn при запуске утилиты
        if(SettingsParser.auto_start()&&Files.exists(Path.of("/Users/shepherl/KVN/AmneziaConfig.conf"))){ // Проверка наличия автозапуска
            if (startWireproxy()) {
                statusItem.setLabel("Status: Connected (Go Active)");
                connectItem.setEnabled(false);
                disconnectItem.setEnabled(true);
            } else {
                System.out.println("Ошибка запуска утилиты");
            }

        }else{
        connectItem.addActionListener(e -> {
            statusItem.setLabel("Status: Connecting...");

            // Сначала пробуем запустить наш Go бинарник
            if (startWireproxy()) {
                statusItem.setLabel("Status: Connected (Go Active)");
                connectItem.setEnabled(false);
                disconnectItem.setEnabled(true);
            } else {
                System.out.println("Ошибка запуска утилиты");
            }
        });
        }
        connectItem.addActionListener(e -> {
            statusItem.setLabel("Status: Connecting...");

            // Сначала пробуем запустить наш Go бинарник
            if (startWireproxy()) {
                statusItem.setLabel("Status: Connected (Go Active)");
                connectItem.setEnabled(false);
                disconnectItem.setEnabled(true);
            } else {
                System.out.println("Ошибка запуска утилиты");
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

    private static boolean startWireproxy() {
        try {
            // 1. Поиск папки (Contents/app)
            String appDir = System.getProperty("user.dir");

            // Если мы внутри .app, user.dir часто указывает на Contents/app.
            // Но если запуск из Терминала, путь может отличаться. Проверим:
            File proxyFile = new File(appDir, "wireproxy");

            if (!proxyFile.exists()) {
                // Резервный поиск через путь к JAR
                String jarPath = App.class.getProtectionDomain().getCodeSource().getLocation().toURI().getPath();
                appDir = new File(jarPath).getParent();
                proxyFile = new File(appDir, "wireproxy");
            }

            System.out.println("DEBUG: Binary Path -> " + proxyFile.getAbsolutePath());

            if (!proxyFile.exists()) {
                System.err.println("CRITICAL: wireproxy NOT FOUND!");
                return false;
            }

            // 2. Снимаем карантин macOS (БЕЗ ЭТОГО НЕ ЗАПУСТИТСЯ)
            // Если файл скачан из GitHub Actions, macOS пометит его как подозрительный.
            try {
                Runtime.getRuntime().exec(new String[]{"xattr", "-d", "com.apple.quarantine", proxyFile.getAbsolutePath()});
            } catch (Exception ignored) {}

            proxyFile.setExecutable(true);

            // 3. Запуск
            // Важно: передаем рабочую директорию, чтобы он нашел proxy.conf рядом
            ProcessBuilder pb = new ProcessBuilder(proxyFile.getAbsolutePath(), "-c", "/Users/shepherl/KVN/proxy.conf");
            pb.directory(new File(appDir));
            pb.redirectErrorStream(true);

            wireproxyProcess = pb.start();

            // 4. Поток чтения логов (чтобы увидеть ошибки Go в Терминале)
            new Thread(() -> {
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(wireproxyProcess.getInputStream()))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        System.out.println("[GO_ENGINE]: " + line);
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
            System.out.println("DEBUG: wireproxy stopped.");
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