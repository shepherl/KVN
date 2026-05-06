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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import org.example.dns.Changedns;
import org.example.dns.DnsProvider;

public class StatusBarMenu {
    public PopupMenu menu = new PopupMenu();
    public MenuItem statusItem;
    public MenuItem toggleConnectItem;
    public CheckboxMenuItem autoStatrtCheckbox;
    public Menu dnsMenu;
    public CheckboxMenuItem[] dnsItems;
    public CheckboxMenuItem customDnsItem;
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
        toggleConnectItem = new MenuItem("Connect VPN");
        autoStatrtCheckbox = new CheckboxMenuItem("Auto Connect",SettingsParser.auto_start());
        
        dnsMenu = new Menu("DNS Settings");
        DnsProvider[] providers = DnsProvider.values();
        dnsItems = new CheckboxMenuItem[providers.length];
        
        int currentDnsId = detectCurrentDns();
        FileUtils.DNStatusWrite(currentDnsId, pathBase);

        for (int i = 0; i < providers.length; i++) {
            DnsProvider provider = providers[i];
            dnsItems[i] = new CheckboxMenuItem(provider.getLabel(), provider.getId() == currentDnsId);
            final int providerId = provider.getId();
            dnsItems[i].addItemListener(e -> {
                handleDnsSelection(providerId, provider);
            });
            dnsMenu.add(dnsItems[i]);
        }
        
        customDnsItem = new CheckboxMenuItem("Custom", currentDnsId == 7);
        customDnsItem.setEnabled(false); // Пользователь не может сам выбрать Custom кнопкой, он ставится при загрузке конфига
        dnsMenu.addSeparator();
        dnsMenu.add(customDnsItem);

        String AddFileButtonText;
        if(Files.exists(configPath.resolve("AmneziaConfig.conf"))){
            AddFileButtonText = "Remove Config";
        }else{
            AddFileButtonText = "Add Config...";
        }
        addFileAndRemove = new MenuItem(AddFileButtonText);
        exitItem = new MenuItem("Exit");
    }

    private void handleDnsSelection(int id, DnsProvider provider) {
        Path fullConfigPath = configPath.resolve("AmneziaConfig.conf");
        if (Files.exists(fullConfigPath)) {
            Changedns.change(fullConfigPath, provider);
            FileUtils.DNStatusWrite(id, pathBase);
            updateDnsUI(id);
            
            if (toggleConnectItem.getLabel().equals("Disconnect")) {
                restartVpn();
            }
        }
    }

    private void updateDnsUI(int id) {
        for (CheckboxMenuItem item : dnsItems) {
            item.setState(false);
        }
        customDnsItem.setState(false);
        
        if (id >= 1 && id <= 6) {
            dnsItems[id - 1].setState(true);
        } else if (id == 7) {
            customDnsItem.setState(true);
        }
    }

    private int detectCurrentDns() {
        Path fullConfigPath = configPath.resolve("AmneziaConfig.conf");
        if (!Files.exists(fullConfigPath)) return 1;

        try {
            List<String> lines = Files.readAllLines(fullConfigPath);
            for (String line : lines) {
                line = line.trim();
                // Игнорируем пустые строки и комментарии (# или ;)
                if (line.isEmpty() || line.startsWith("#") || line.startsWith(";")) continue;
                
                if (line.toLowerCase().startsWith("dns")) {
                    int eqIndex = line.indexOf("=");
                    if (eqIndex == -1) continue;
                    
                    String dnsValue = line.substring(eqIndex + 1).trim();
                    List<String> addresses = Arrays.stream(dnsValue.split(","))
                            .map(String::trim)
                            .filter(s -> !s.isEmpty())
                            .collect(Collectors.toList());
                    
                    if (addresses.isEmpty()) continue;
                    
                    Optional<DnsProvider> provider = DnsProvider.findByAddresses(addresses);
                    return provider.map(DnsProvider::getId).orElse(7);
                }
            }
        } catch (IOException e) {
            System.err.println("Error reading config for DNS detection: " + e.getMessage());
        }
        return 7;
    }

    private void restartVpn() {
        System.out.println("Restarting VPN to apply new DNS...");
        eWireproxy.stopWireproxy();
        statusItem.setLabel("Status: Reconnecting...");
        toggleConnectItem.setLabel("Connect VPN");
        addFileAndRemove.setEnabled(true);

        if (eWireproxy.startWireproxy()) {
            addFileAndRemove.setEnabled(false);
            statusItem.setLabel("Status: Connected 🟢");
            toggleConnectItem.setLabel("Disconnect");
        } else {
            statusItem.setLabel("Status: Error 🔴");
        }
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
            if (directory != null && filename != null) {
                Path sourcePath = Path.of(directory, filename);
                Path targetPath = configPath.resolve("AmneziaConfig.conf");
                
                FileUtils.copyFile(sourcePath, targetPath);
                
                // Детектируем DNS из нового файла
                int detectedDnsId = detectCurrentDns();
                FileUtils.DNStatusWrite(detectedDnsId, pathBase);
                updateDnsUI(detectedDnsId);
                
                addFileAndRemove.setLabel("Remove Config");
            }
        }else{
            try{
                // На всякий случай останавливаем прокси перед удалением конфига
                eWireproxy.stopWireproxy();
                statusItem.setLabel("Status: Disconnected 🔴");
                toggleConnectItem.setLabel("Connect VPN");

                Path fileToDelete = configPath.resolve("AmneziaConfig.conf");
                Files.deleteIfExists(fileToDelete);
                
                // После удаления конфига сбрасываем на Cloudflare (1)
                updateDnsUI(1);
                FileUtils.DNStatusWrite(1, pathBase);
                addFileAndRemove.setLabel("Add Config...");
                System.out.println("Config removed successfully.");
            }catch(IOException a){
                System.err.println("Could not delete config file: " + a.getMessage());
                a.printStackTrace();
            }
        }
       });

       toggleConnectItem.addActionListener(e -> {
            if (toggleConnectItem.getLabel().equals("Connect VPN")) {
                statusItem.setLabel("Status: Connecting...");
                if (eWireproxy.startWireproxy()) {
                    addFileAndRemove.setEnabled(false);
                    statusItem.setLabel("Status: Connected 🟢");
                    toggleConnectItem.setLabel("Disconnect");
                } else {
                    statusItem.setLabel("Status: Error 🔴");
                }
            } else {
                addFileAndRemove.setEnabled(true);
                eWireproxy.stopWireproxy();
                statusItem.setLabel("Status: Disconnected 🔴");
                toggleConnectItem.setLabel("Connect VPN");
            }
        });

        exitItem.addActionListener(e -> {
            eWireproxy.stopWireproxy();
            System.exit(0);
        });
    }

    public void addPopupMenu(){
        menu.add(statusItem); // Список элементов в интерфейсе
        menu.addSeparator();
        menu.add(toggleConnectItem);
        menu.add(autoStatrtCheckbox); // Чекбокс автозапуска
        menu.add(dnsMenu);
        menu.add(addFileAndRemove);
        menu.addSeparator();
        menu.add(exitItem);
    }


}
