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
            FileUtils.createDirectory(Path.of(configPath));
            if(!Files.exists(Path.of(configPath + "AutoStartStatus.json"))){ // Проверка существования файла
                FileUtils.createfiles(Path.of(configPath + "AutoStartStatus.json"));
                try{
                System.out.println("Файла нет");
                Files.writeString(Path.of(configPath + "AutoStartStatus.json"),"{\n" + //
                                        "\"autoStart\": 0,\n" + //
                                        "\"DNStatus\": 1\n" + //
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
                                        "\"autoStart\": 0,\n" + //
                                        "\"DNStatus\": 1\n" + //
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
    }



}