/*
Работа с файлами , которые хранятся в Mac.
 */

package org.example;

import java.io.File;

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
        File files = new File("base.json");
        if(files.exists()&& files.isDirectory()){ // Проверка существования директории и проверка это папка или файл
            return true;
        }else{
            return false;
        }
    }

}
