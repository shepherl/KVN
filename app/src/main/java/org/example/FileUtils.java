/*
Работа с файлами , которые хранятся в Mac.
 */

package org.example;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public class FileUtils {

    public static boolean checkDirectoryExists(){ // Метод проверки существования директории
        File files = new File("base.json");
        if(files.exists()&& files.isDirectory()){ // Проверка существования директораии и проверка это папка или файл
            return true;
        }else{
            return false;
        }

    }

    public static boolean checkFileExists(){ // Метод проверки файла
        File files = new File("");
        if(files.exists()&& files.isDirectory()){ // Проверка существования директории и проверка это папка или файл
            return true;
        }else{
            return false;
        }
    }

    public static void AutoStartStatusrWrite(boolean start_status){ // Метод для изменения  AutoStartFile
        Path path = Path.of("/Users/shepherl/KVN/AutoStartStatus.json");
        String content;
        try{
            if(start_status){
            content = Files.readString(path);
            content = content.replace("\"autoStatrt\": 0", "\"autoStart\": 1");
            Files.writeString(path,content);
            }else{
                content = Files.readString(path);
                System.out.println("Работай !!!!!");
                content = content.replace("\"autoStatrt\": 1", "\"autoStart\": 0");
                Files.writeString(path,content);
            }


        }catch(IOException e){
            e.printStackTrace();

        }

    }

}
