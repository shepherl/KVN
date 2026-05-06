package org.example;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public class FileCheck{
    private Path pathBase;
    private String configPath;

    public FileCheck(Path pathBase, String configPath){
        this.pathBase = pathBase;
        this.configPath = configPath;

    }

    public void startStart(){
    if (!FileUtils.checkDirectoryExists(pathBase)) {
            FileUtils.createDirectory(pathBase);
            FileUtils.createDirectory(pathBase.resolve("configs")); // Создаем папку для профилей
            if(!Files.exists(pathBase.resolve("AutoStartStatus.json"))){ 
                FileUtils.createfiles(pathBase.resolve("AutoStartStatus.json"));
                try{
                Files.writeString(pathBase.resolve("AutoStartStatus.json"),"{\n" + 
                                        "\"autoStart\": 0,\n" + 
                                        "\"DNStatus\": 1,\n" + 
                                        "\"ProxyEngine\": 0,\n" +
                                        "\"activeProfile\": \"\"\n" +
                                        "}");
                }catch(IOException e){ e.printStackTrace(); }
            }
            // ... остальной код создания proxy.conf
            if(!Files.exists(pathBase.resolve("proxy.conf"))){
                FileUtils.createfiles(pathBase.resolve("proxy.conf"));
                try{
                Files.writeString(pathBase.resolve("proxy.conf"),"WGConfig = " + pathBase.resolve("AmneziaConfig.conf").toString() + "\r\n" + //
                                        "\r\n" + //
                                        "[Socks5]\r\n" + //
                                        "BindAddress = 127.0.0.1:1080");
                }catch(IOException t){
                    t.getMessage();
                }
            }

        }else{
            if (!FileUtils.checkDirectoryExists(pathBase.resolve("configs"))) {
                FileUtils.createDirectory(pathBase.resolve("configs"));
            }
            if(!Files.exists(pathBase.resolve("AutoStartStatus.json"))){ // Проверка существования файла

                FileUtils.createfiles(pathBase.resolve("AutoStartStatus.json"));
                try{
                Files.writeString(pathBase.resolve("AutoStartStatus.json"),"{\n" + 
                                        "\"autoStart\": 0,\n" + 
                                        "\"DNStatus\": 1,\n" + 
                                        "\"ProxyEngine\": 0,\n" +
                                        "\"activeProfile\": \"\"\n" +
                                        "}");
                }catch(IOException e){ e.printStackTrace(); }
            }
            if(!Files.exists(pathBase.resolve("proxy.conf"))){
                FileUtils.createfiles(pathBase.resolve("proxy.conf"));
                try{
                Files.writeString(pathBase.resolve("proxy.conf"),"WGConfig = " + pathBase.resolve("AmneziaConfig.conf").toString() + "\r\n" + //
                                        "\r\n" + //
                                        "[Socks5]\r\n" + //
                                        "BindAddress = 127.0.0.1:1080");
                }catch(IOException t){
                    t.getMessage();
                }
            }


        }
    }



}