package org.example;

import java.awt.CheckboxMenuItem;
import java.awt.FileDialog;
import java.awt.Frame;
import java.awt.Menu;
import java.awt.MenuItem;
import java.awt.PopupMenu;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.example.dns.Changedns;
import org.example.dns.DnsProvider;

public class StatusBarMenu {
    public PopupMenu menu = new PopupMenu();
    public MenuItem statusItem;
    public MenuItem connectItem;
    public MenuItem disconnectItem;
    public CheckboxMenuItem autoStatrtCheckbox;
    public Menu dnsMenu;
    public MenuItem addFileAndRemove;
    public MenuItem exitItem;
    private Path pathBase;
    private Path configPath;
    private Wireproxy eWireproxy;

    public StatusBarMenu(Path pathBase, Path configPath, Wireproxy eWireproxy){
        this.pathBase = pathBase;
        this.configPath = configPath;
        this.eWireproxy = eWireproxy;
    }


    public void itemCreate(){
        statusItem = new MenuItem("Status: Disconnected - 🔴");
        statusItem.setEnabled(false);
        connectItem = new MenuItem("Connect VPN");
        disconnectItem = new MenuItem("Disconnect");
        autoStatrtCheckbox = new CheckboxMenuItem("Auto Connect",SettingsParser.auto_start());
        
        dnsMenu = new Menu("DNS Settings");
        for (DnsProvider provider : DnsProvider.values()) {
            MenuItem item = new MenuItem(provider.getLabel());
            item.addActionListener(e -> {
                Path fullConfigPath = configPath.resolve("AmneziaConfig.conf");
                if (Files.exists(fullConfigPath)) {
                    Changedns.change(fullConfigPath, provider);
                    
                    // Если VPN сейчас подключен, перезапускаем его для применения настроек
                    if (disconnectItem.isEnabled()) {
                        System.out.println("Restarting VPN to apply new DNS...");
                        
                        // Имитируем нажатие кнопки Disconnect
                        eWireproxy.stopWireproxy();
                        statusItem.setLabel("Status: Reconnecting...");
                        connectItem.setEnabled(true);
                        disconnectItem.setEnabled(false);
                        addFileAndRemove.setEnabled(true);

                        // Имитируем нажатие кнопки Connect
                        if (eWireproxy.startWireproxy()) {
                            addFileAndRemove.setEnabled(false);
                            statusItem.setLabel("Status: Connected 🟢");
                            connectItem.setEnabled(false);
                            disconnectItem.setEnabled(true);
                        } else {
                            statusItem.setLabel("Status: Error 🔴");
                        }
                    }
                } else {
                    System.err.println("Config file not found, cannot change DNS");
                }
            });
            dnsMenu.add(item);
        }

        String AddFileButtonText;
        if(Files.exists(configPath.resolve("AmneziaConfig.conf"))){
            AddFileButtonText = "Remove Config";
        }else{
            AddFileButtonText = "Add Config...";
        }
        addFileAndRemove = new MenuItem(AddFileButtonText);
        disconnectItem.setEnabled(false);
        exitItem = new MenuItem("Exit");


    }

    public void runActionListener(){
        autoStatrtCheckbox.addItemListener(e -> {
            boolean status = autoStatrtCheckbox.getState();
            if(status){
                AutoStart.setAutoLaunch(true);
                FileUtils.AutoStartStatusrWrite(true,pathBase);
                //System.out.println("Включено");
            }else{
                AutoStart.setAutoLaunch(false);
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

    }

    public void addPopupMenu(){
        menu.add(statusItem); // Список элементов в интерфейсе
        menu.addSeparator();
        menu.add(connectItem);
        menu.add(disconnectItem);
        menu.add(autoStatrtCheckbox); // Чекбокс автозапуска
        menu.add(dnsMenu);
        menu.add(addFileAndRemove);
        menu.addSeparator();
        menu.add(exitItem);
    }


}
