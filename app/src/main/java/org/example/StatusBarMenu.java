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
import javax.swing.JOptionPane;
import org.example.dns.Changedns;
import org.example.dns.DnsProvider;

import org.example.ota.Updater;

public class StatusBarMenu {
    public PopupMenu menu = new PopupMenu();
    public MenuItem statusItem;
    public MenuItem toggleConnectItem;
    public CheckboxMenuItem autoStatrtCheckbox;
    public Menu dnsMenu;
    public Menu engineMenu;
    public Menu profilesMenu;
    public MenuItem updateItem;
    public MenuItem portItem;
    public CheckboxMenuItem wireproxyItem;
    public CheckboxMenuItem operaItem;
    public CheckboxMenuItem[] dnsItems;
    public CheckboxMenuItem customDnsItem;
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
        
        engineMenu = new Menu("Proxy Engine");
        int currentEngine = SettingsParser.getProxyEngine();
        wireproxyItem = new CheckboxMenuItem("Wireproxy (AWG)", currentEngine == 0);
        operaItem = new CheckboxMenuItem("Opera Proxy", currentEngine == 1);
        
        wireproxyItem.addItemListener(e -> handleEngineSelection(0));
        operaItem.addItemListener(e -> handleEngineSelection(1));
        
        engineMenu.add(wireproxyItem);
        engineMenu.add(operaItem);

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

        profilesMenu = new Menu("Profiles");
        refreshProfilesMenu();

        updateItem = new MenuItem("Check for updates...");
        portItem = new MenuItem("Proxy Port: " + SettingsParser.getProxyPort());
        
        // Обновляем proxy.conf актуальным профилем при старте
        updateProxyConf(SettingsParser.getActiveProfile());
        
        // Принудительно останавливаем все процессы при старте утилиты
        stopCurrentProxy(0);
        stopCurrentProxy(1);

        exitItem = new MenuItem("Exit");
        updateEngineUI(currentEngine);
        updateDnsUI(currentDnsId);
        updateConnectionButtonState();
    }

    private void refreshProfilesMenu() {
        profilesMenu.removeAll();
        
        MenuItem addProfileItem = new MenuItem("Add New Profile...");
        addProfileItem.addActionListener(e -> {
            FileDialog fd = new FileDialog((Frame)null,"Add Config", FileDialog.LOAD);
            fd.setVisible(true);
            if (fd.getDirectory() != null && fd.getFile() != null) {
                Path source = Path.of(fd.getDirectory(), fd.getFile());
                Path target = pathBase.resolve("configs").resolve(fd.getFile());
                FileUtils.copyFile(source, target);
                
                // Если это первый профиль, делаем его активным
                if (SettingsParser.getActiveProfile().isEmpty()) {
                    selectProfile(fd.getFile());
                }
                refreshProfilesMenu();
                addPopupMenu();
            }
        });
        profilesMenu.add(addProfileItem);
        profilesMenu.addSeparator();

        Path configsDir = pathBase.resolve("configs");
        try {
            String activeProfile = SettingsParser.getActiveProfile();
            if (Files.exists(configsDir)) {
                Files.list(configsDir)
                    .filter(p -> p.toString().endsWith(".conf"))
                    .forEach(p -> {
                        String fileName = p.getFileName().toString();
                        Menu profileSubMenu = new Menu(fileName + (fileName.equals(activeProfile) ? " 🟢" : ""));
                        
                        MenuItem selectItem = new MenuItem("Select");
                        selectItem.addActionListener(e -> selectProfile(fileName));
                        
                        MenuItem deleteItem = new MenuItem("Delete");
                        deleteItem.addActionListener(e -> {
                            try {
                                Files.delete(p);
                                if (fileName.equals(activeProfile)) {
                                    FileUtils.ActiveProfileWrite("", pathBase);
                                    updateProxyConf("");
                                }
                                refreshProfilesMenu();
                                updateConnectionButtonState();
                                addPopupMenu();

                            } catch (IOException ex) { ex.printStackTrace(); }
                        });

                        profileSubMenu.add(selectItem);
                        profileSubMenu.add(deleteItem);
                        profilesMenu.add(profileSubMenu);
                    });
            }
        } catch (IOException e) { e.printStackTrace(); }
    }

    private void selectProfile(String profileName) {
        FileUtils.ActiveProfileWrite(profileName, pathBase);
        updateProxyConf(profileName);
        
        // Детектируем DNS из нового файла
        int detectedDnsId = detectCurrentDns();
        FileUtils.DNStatusWrite(detectedDnsId, pathBase);
        updateDnsUI(detectedDnsId);
        
        refreshProfilesMenu();
        updateConnectionButtonState();

        if (toggleConnectItem.getLabel().equals("Disconnect")) {
            restartVpn();
        } else {
            addPopupMenu();
        }
    }

    private void updateProxyConf(String profileName) {
        try {
            int port = SettingsParser.getProxyPort();
            Path proxyConfPath = pathBase.resolve("proxy.conf");
            String configPathStr = profileName.isEmpty() ? "" : pathBase.resolve("configs").resolve(profileName).toString();
            String content = "WGConfig = " + configPathStr + "\r\n" +
                             "\r\n" +
                             "[Socks5]\r\n" +
                             "BindAddress = 127.0.0.1:" + port;
            Files.writeString(proxyConfPath, content);
        } catch (IOException e) { e.printStackTrace(); }
    }

    private void updateConnectionButtonState() {
        int engine = SettingsParser.getProxyEngine();
        if (engine == 1) { // Opera Proxy не требует конфига в нашей реализации
            toggleConnectItem.setEnabled(true);
        } else { // Wireproxy
            String activeProfile = SettingsParser.getActiveProfile();
            boolean hasActiveProfile = !activeProfile.isEmpty() && 
                Files.exists(pathBase.resolve("configs").resolve(activeProfile));

            // Если мы уже подключены, кнопку Disconnect нельзя блокировать
            if (toggleConnectItem.getLabel().equals("Disconnect")) {
                toggleConnectItem.setEnabled(true);
            } else {
                toggleConnectItem.setEnabled(hasActiveProfile);
            }
        }
    }

    private void handleEngineSelection(int engine) {
        // Мы НЕ выходим, если старый движок совпадает с новым, на случай рассинхрона UI

        boolean wasRunning = toggleConnectItem.getLabel().equals("Disconnect");

        // Всегда останавливаем всё перед переключением
        stopCurrentProxy(0); // Wireproxy
        stopCurrentProxy(1); // Opera

        FileUtils.ProxyEngineWrite(engine, pathBase);
        updateEngineUI(engine);
        updateConnectionButtonState(); // Проверяем состояние кнопки
        addPopupMenu(); // Перестраиваем структуру меню

        if (wasRunning) {
            statusItem.setLabel("Status: Connecting...");
            if (startCurrentProxy(engine)) {
                statusItem.setLabel("Status: Connected 🟢");
                toggleConnectItem.setLabel("Disconnect");
            } else {
                statusItem.setLabel("Status: Error 🔴");
                toggleConnectItem.setLabel("Connect VPN");
            }
        } else {
            statusItem.setLabel("Status: Disconnected 🔴");
            toggleConnectItem.setLabel("Connect VPN");
        }
    }

    private void updateEngineUI(int engine) {
        wireproxyItem.setState(engine == 0);
        operaItem.setState(engine == 1);
        
        String label = (engine == 0) ? "Wireproxy (AWG)" : "Opera Proxy";
        engineMenu.setLabel("Engine: " + label);
    }

    private boolean startCurrentProxy(int engine) {
        int port = SettingsParser.getProxyPort();
        if (engine == 0) {
            return eWireproxy.startWireproxy();
        } else {
            return OperaProxy.startOperaProxy(port);
        }
    }

    private void stopCurrentProxy(int engine) {
        if (engine == 0) {
            eWireproxy.stopWireproxy();
        } else {
            OperaProxy.stopOperaProxy();
        }
    }

    private void handleDnsSelection(int id, DnsProvider provider) {
        String activeProfile = SettingsParser.getActiveProfile();
        if (activeProfile.isEmpty()) {
            System.err.println("No active profile selected to change DNS");
            updateDnsUI(detectCurrentDns()); // Сбрасываем галочки к текущему состоянию
            return;
        }

        Path fullConfigPath = pathBase.resolve("configs").resolve(activeProfile);
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
        
        String dnsLabel = "Custom";
        if (id >= 1 && id <= 6) {
            dnsItems[id - 1].setState(true);
            dnsLabel = DnsProvider.findById(id).map(DnsProvider::getLabel).orElse("Custom");
        } else if (id == 7) {
            customDnsItem.setState(true);
        }
        dnsMenu.setLabel("DNS: " + dnsLabel);
    }

    private int detectCurrentDns() {
        String activeProfile = SettingsParser.getActiveProfile();
        if (activeProfile.isEmpty()) return 1;
        Path fullConfigPath = pathBase.resolve("configs").resolve(activeProfile);
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
        int engine = SettingsParser.getProxyEngine();
        int port = SettingsParser.getProxyPort();
        System.out.println("Restarting VPN to apply new settings...");
        stopCurrentProxy(engine);
        statusItem.setLabel("Status: Reconnecting...");
        toggleConnectItem.setLabel("Connect VPN");

        if (startCurrentProxy(engine)) {
            statusItem.setLabel("Status: Connected 🟢");
            toggleConnectItem.setLabel("Disconnect");
            restartBrowser(true, port);
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
            }else{
                AutoStart.setAutoLaunch(false);
                FileUtils.AutoStartStatusrWrite(false,pathBase);
            }
        });

        toggleConnectItem.addActionListener(e -> {
            int engine = SettingsParser.getProxyEngine();
            int port = SettingsParser.getProxyPort();
            if (toggleConnectItem.getLabel().equals("Connect VPN")) {
                statusItem.setLabel("Status: Connecting...");
                if (startCurrentProxy(engine)) {
                    statusItem.setLabel("Status: Connected 🟢");
                    toggleConnectItem.setLabel("Disconnect");
                    restartBrowser(true, port);
                } else {
                    statusItem.setLabel("Status: Error 🔴");
                }
            } else {
                stopCurrentProxy(engine);
                statusItem.setLabel("Status: Disconnected 🔴");
                toggleConnectItem.setLabel("Connect VPN");
                restartBrowser(false, port);
            }
        });

        portItem.addActionListener(e -> {
            // Создаем временное окно-заглушку, чтобы диалог был поверх всех окон
            javax.swing.JFrame topFrame = new javax.swing.JFrame();
            topFrame.setAlwaysOnTop(true);
            
            String input = JOptionPane.showInputDialog(topFrame, 
                "Enter Proxy Port (1024-65535):", 
                "Settings", 
                JOptionPane.QUESTION_MESSAGE);
            
            if (input != null && !input.isEmpty()) {
                try {
                    int newPort = Integer.parseInt(input.trim());
                    if (newPort >= 1024 && newPort <= 65535) {
                        FileUtils.ProxyPortWrite(newPort, pathBase);
                        portItem.setLabel("Proxy Port: " + newPort);
                        updateProxyConf(SettingsParser.getActiveProfile());
                        
                        if (toggleConnectItem.getLabel().equals("Disconnect")) {
                            restartVpn();
                        }
                    } else {
                        JOptionPane.showMessageDialog(topFrame, "Port must be between 1024 and 65535", "Error", JOptionPane.ERROR_MESSAGE);
                    }
                } catch (NumberFormatException ex) {
                    JOptionPane.showMessageDialog(topFrame, "Invalid port number", "Error", JOptionPane.ERROR_MESSAGE);
                }
            }
            topFrame.dispose(); // Освобождаем ресурсы
        });

        updateItem.addActionListener(e -> {
            Updater.checkForUpdates(false);
        });

        exitItem.addActionListener(e -> {
            int engine = SettingsParser.getProxyEngine();
            int port = SettingsParser.getProxyPort();
            stopCurrentProxy(engine);
            
            // Запускаем отдельный поток для ожидания перезапуска браузера перед выходом,
            // чтобы System.exit(0) не убил программу раньше времени
            new Thread(() -> {
                Thread browserThread = restartBrowser(false, port); // Передаем актуальный порт для проверки "наших" флагов
                try {
                    if (browserThread != null) browserThread.join();
                } catch (InterruptedException ex) {}
                System.exit(0);
            }).start();
        });
    }

    private Thread restartBrowser(boolean useProxy, int port) {
        Thread t = new Thread(() -> {
            try {
                System.out.println("Restarting Google Chrome. useProxy=" + useProxy + ", port=" + port);
                
                // Записываем скрипт в файл, чтобы избежать любых проблем с экранированием кавычек
                StringBuilder script = new StringBuilder();
                script.append("#!/bin/bash\n\n");
                
                if (useProxy) {
                    // Если нужно включить прокси - проверяем, вдруг Chrome УЖЕ запущен с точно такими же параметрами
                    // Используем ps xww, чтобы macOS не обрезала длинные строки запуска (что и ломало проверку раньше)
                    script.append("if ps xww | grep \"[G]oogle Chrome\" | grep -qF \"--proxy-server=socks5://127.0.0.1:").append(port).append("\" && ");
                    script.append("ps xww | grep \"[G]oogle Chrome\" | grep -qF \"--proxy-bypass-list=*.ru\"; then\n");
                    script.append("    echo \"Chrome is already running with the correct proxy settings. Skipping restart.\"\n");
                    script.append("    exit 0\n");
                    script.append("fi\n\n");
                } else {
                    // Если нужно отключить прокси (или выйти из программы) - проверяем, запущен ли Chrome с НАШИМИ прокси-флагами
                    script.append("if ! ps xww | grep \"[G]oogle Chrome\" | grep -q \"--proxy-server=socks5://127.0.0.1:").append(port).append("\"; then\n");
                    script.append("    echo \"Chrome is not running or already running without our proxy. Skipping restart.\"\n");
                    script.append("    exit 0\n");
                    script.append("fi\n\n");
                }
                
                script.append("osascript -e 'quit app \"Google Chrome\"' 2>/dev/null\n");
                script.append("while pgrep -x \"Google Chrome\" > /dev/null; do sleep 0.5; done\n");
                script.append("sleep 1\n");
                
                if (useProxy) {
                    script.append("open -a \"Google Chrome\" --args --proxy-server=\"socks5://127.0.0.1:").append(port).append("\" --proxy-bypass-list=\"*.ru\"\n");
                } else {
                    script.append("open -a \"Google Chrome\"\n");
                }
                
                java.nio.file.Path scriptPath = java.nio.file.Path.of("/tmp/kvn_chrome_restart.sh");
                java.nio.file.Files.writeString(scriptPath, script.toString());
                scriptPath.toFile().setExecutable(true);
                
                // Для отладки: выводим содержимое скрипта
                System.out.println("DEBUG: Script content:\n" + script.toString());
                
                new ProcessBuilder("bash", "/tmp/kvn_chrome_restart.sh").start().waitFor();
                
            } catch (Exception ex) {
                System.err.println("Failed to restart browser: " + ex.getMessage());
            }
        });
        t.start();
        return t;
    }

    public void addPopupMenu(){
        menu.removeAll(); // Полная очистка перед сборкой
        int engine = SettingsParser.getProxyEngine();

        menu.add(statusItem);
        menu.addSeparator();
        menu.add(toggleConnectItem);
        menu.add(autoStatrtCheckbox);
        menu.addSeparator();
        menu.add(engineMenu);

        // Эти пункты добавляем ТОЛЬКО для Wireproxy (движок 0)
        if (engine == 0) {
            menu.add(dnsMenu);
            menu.add(profilesMenu);
        }

        menu.addSeparator();
        menu.add(portItem);
        menu.add(updateItem);
        menu.add(exitItem);
    }


}
