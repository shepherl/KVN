/*
Работа с файлами , которые хранятся в Mac.
 */

package org.example;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;

public class FileUtils {

    public static boolean checkDirectoryExists(){ // Метод проверки существования директории
        Path path = Path.of("/Users/shepherl/KVN");
        if(Files.exists(path)&& Files.isDirectory(path)){ // Проверка существования директораии и проверка это папка или файл
            return true;
        }else{
            return false;
        }

    }



    public static void createfiles(Path path){ // Тут path требует указание пути + название файла
        try{
            Files.createFile(path);
        }catch(IOException e){
            e.getMessage();
        }

    }

    public static void createDirectory(Path path){ // Создание директории. Тут достаточно указать путь
            try{
            Files.createDirectory(path);
            }catch(IOException e){
                e.getMessage();
            }
    }

    public static void AutoStartStatusrWrite(boolean start_status){ // Метод для изменения  AutoStartFile
        Path path = Path.of("/Users/shepherl/KVN/AutoStartStatus.json");
        String content;
        try{
            if(start_status){
            content = Files.readString(path);
            content = content.replace("\"autoStart\": 0", "\"autoStart\": 1");
            Files.writeString(path,content);
            }else{
                content = Files.readString(path);
                content = content.replace("\"autoStart\": 1", "\"autoStart\": 0");
                Files.writeString(path,content);
                System.out.println("Работай !!!!!");
            }


        }catch(IOException e){
            e.printStackTrace();

        }

    }
     public static void copyFile(Path path,Path pathCopy){
        try{
        Files.copy(path, pathCopy, StandardCopyOption.REPLACE_EXISTING);
        }catch(IOException e){
            e.getMessage();
        }

    }

}
