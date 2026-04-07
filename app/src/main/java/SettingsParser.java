import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Matcher;
import java.util.regex.Pattern;



public class SettingsParser {

    public static String findValue(String json, String key){
        Pattern pattern = Pattern.compile("\"" + key + "\\\"\\s*:\\s*([^,}\\]]+)");
        Matcher matcher = pattern.matcher(json);

        if(matcher.find()){
            return matcher.group(1).replace("\"", "").trim();
        }
        return "Not found";

    }


    public static void main(String[] args) {
        Path path = Path.of("base.json");
        String content;
        try {
            content = Files.readString(path);
            System.out.println("autoStart: " + findValue(content, "autoStart"));
            System.out.println("Lol: " + findValue(content, "Lol"));

        } catch (IOException e) {
            System.out.println("Ошибка чтения");

            e.printStackTrace();
        }
    }
}