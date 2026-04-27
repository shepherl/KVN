package org.example;

import com.sun.jna.Library;
import com.sun.jna.Pointer;
// Оставляем только JNA импорт для Native
import com.sun.jna.Native;

public class AutoStart {
    
    public interface Foundation extends Library {
        // Указываем полный путь com.sun.jna.Native, чтобы Java не путалась
        Foundation INSTANCE = com.sun.jna.Native.load("Foundation", Foundation.class);
        Pointer objc_getClass(String className);
    }

    public interface ServiceManagement extends Library {
        // Здесь тоже указываем полный путь
        ServiceManagement INSTANCE = com.sun.jna.Native.load("ServiceManagement", ServiceManagement.class);
        
        int SMAppServiceRegisterMainApp(Pointer error);
        int SMAppServiceUnregisterMainApp(Pointer error);
    }

    public static void setAutoLaunch(boolean enable) {
        try {
            int result;
            if (enable) {
                result = ServiceManagement.INSTANCE.SMAppServiceRegisterMainApp(null);
            } else {
                result = ServiceManagement.INSTANCE.SMAppServiceUnregisterMainApp(null);
            }

            if (result == 0) {
                System.out.println("✅ Успешно! Статус автозагрузки изменен на: " + enable);
            } else {
                // Если код ошибки 4, это часто значит "Background items managed by policy"
                // Если 1, значит приложение не упаковано в .app или не подписано
                System.out.println("❌ Ошибка macOS. Код ошибки: " + result);
            }
        } catch (Exception e) {
            System.err.println("❌ Ошибка: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
