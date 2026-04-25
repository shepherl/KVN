package org.example;

import java.awt.CheckboxMenuItem;
import java.awt.MenuItem;
import java.awt.PopupMenu;
import java.nio.file.Path;

public class StatusBarMenu {
    PopupMenu menu = new PopupMenu();
    MenuItem statusItem;
    MenuItem connectItem;
    MenuItem disconnectItem;
    CheckboxMenuItem autoStatrtCheckbox;
    MenuItem addFileAndRemove;
    MenuItem exitItem;


    private void itemСreate(){
        statusItem = new MenuItem("Status: Disconnected - 🔴");
        statusItem.setEnabled(false);
        connectItem = new MenuItem("Connect VPN");
        disconnectItem = new MenuItem("Disconnect");
        autoStatrtCheckbox = new CheckboxMenuItem("Auto Connect",SettingsParser.auto_start());
        String AddFileButtonText;
        if(Files.exists(Path.of(configPath + "AmneziaConfig.conf"))){
            AddFileButtonText = "Remove Config";
        }else{
            AddFileButtonText = "Add Config...";
        }
        addFileAndRemove = new MenuItem(AddFileButtonText);
        disconnectItem.setEnabled(false);
        exitItem = new MenuItem("Exit");


    }
}
