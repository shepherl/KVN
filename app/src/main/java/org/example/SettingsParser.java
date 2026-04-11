package org.example;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Matcher;
import java.util.regex.Pattern;



public class SettingsParser { // Парсер значений из json

    public static String findValue(String json, String key){ // Парсинг json
        Pattern pattern = Pattern.compile("\"" + key + "\\\"\\s*:\\s*([^,}\\]]+)");
        Matcher matcher = pattern.matcher(json);

        if(matcher.find()){
            return matcher.group(1).replace("\"", "").trim();
        }
        return "Not found";

    }


    public static boolean auto_start(){ // Проверка значения автозапуска в файле
        String userName = System.getProperty("user.name");
        String configPath = "/Users/" + userName + "/KVN/";

        Path path = Path.of(configPath + "AutoStartStatus.json");
        String content;
        try{
            content = Files.readString(path);
            if(findValue(content, "autoStart").equals("1")){
                return true;
            }else{
                return false;
            }

        }catch(IOException e){
            System.out.println("Ошибка чтения файла конфига(При проверке статуса автозапуска)");
            e.printStackTrace();
            return false;
        }


    }

}