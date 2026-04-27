package org.example;

import java.lang.annotation.Native;

public class AutoStart {
    // Описываем "мостик" к нативным библиотекам macOS
    public interface Foundation extends Library {
        Foundation INSTANCE = Native.load("Foundation", Foundation.class);
        Pointer objc_getClass(String className);
    }

    public interface ServiceManagement extends Library {
        ServiceManagement INSTANCE = Native.load("ServiceManagement", ServiceManagement.class);
        // Эти функции появились в macOS 13 Ventura и работают напрямую
        int SMAppServiceRegisterMainApp(Pointer error);
        int SMAppServiceUnregisterMainApp(Pointer error);
    }

    /**
     * Метод для включения/выключения автозагрузки
     */
    public static void setAutoLaunch(boolean enable) {
        try {
            int result;
            if (enable) {
                // Вызываем системный метод регистрации
                result = ServiceManagement.INSTANCE.SMAppServiceRegisterMainApp(null);
            } else {
                // Вызываем системный метод удаления
                result = ServiceManagement.INSTANCE.SMAppServiceUnregisterMainApp(null);
            }

            if (result == 0) {
                System.out.println("✅ Успешно! Статус автозагрузки изменен на: " + enable);
            } else {
                // Если результат не 0, значит система отклонила запрос (например, нет подписи)
                System.out.println("❌ Ошибка macOS. Код ошибки: " + result);
            }
        } catch (Exception e) {
            System.err.println("❌ Не удалось выполнить нативный вызов: " + e.getMessage());
        }
    }
}
