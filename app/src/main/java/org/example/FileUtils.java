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

/**
     * Проверяет, существует ли директория по указанному пути, и является ли она директорией (а не файлом).
     * @param path путь к директории, которую необходимо проверить
     * @return true, если директория существует и это действительно директория; иначе false
     */
    public static boolean checkDirectoryExists(Path path) {
        // Проверяем, существует ли путь и является ли он директорией
        return Files.exists(path) && Files.isDirectory(path);
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

/**
     * Обновляет статус автозапуска в файле AutoStartStatus.json.
     * Заменяет значение поля "autoStart": 0 или 1 в зависимости от переданного параметра.
     * Если файл не существует или возникла ошибка чтения/записи, выбрасывается исключение.
     *
     * @param start_status true для установки autoStart = 1, false для установки autoStart = 0
     */
public static void AutoStartStatusrWrite(boolean start_status, Path basePath) {
    Path path = basePath.resolve("AutoStartStatus.json");

    try {
        String content = Files.readString(path); // Читаем содержимое файла

        if (start_status) {
            content = content.replace("\"autoStart\": 0", "\"autoStart\": 1");
        } else {
            content = content.replace("\"autoStart\": 1", "\"autoStart\": 0");
        }

        Files.writeString(path, content); // Записываем обновлённое содержимое

    } catch (IOException e) {
        System.err.println("Ошибка при работе с файлом AutoStartStatus.json: " + e.getMessage());
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

    public static void DNStatusWrite(int status, Path basePath) {
        updateJsonField("DNStatus", String.valueOf(status), basePath);
    }

    public static void ProxyEngineWrite(int engine, Path basePath) {
        updateJsonField("ProxyEngine", String.valueOf(engine), basePath);
    }

    private static void updateJsonField(String key, String value, Path basePath) {
        Path path = basePath.resolve("AutoStartStatus.json");
        try {
            String content = Files.readString(path).trim();
            String pattern = "\"" + key + "\"\\s*:\\s*\\d+";
            if (content.matches("(?s).*" + pattern + ".*")) {
                content = content.replaceAll(pattern, "\"" + key + "\": " + value);
            } else {
                // Если поля нет, добавляем его перед последней закрывающей скобкой
                int lastBrace = content.lastIndexOf("}");
                if (lastBrace != -1) {
                    String prefix = content.substring(0, lastBrace).trim();
                    if (prefix.endsWith("{")) {
                        content = prefix + "\"" + key + "\": " + value + "}";
                    } else {
                        content = prefix + ",\n\"" + key + "\": " + value + "}";
                    }
                }
            }
            Files.writeString(path, content);
        } catch (IOException e) {
            System.err.println("Ошибка при записи поля " + key + ": " + e.getMessage());
        }
    }

}
