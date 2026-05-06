package org.example;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URISyntaxException;
import java.nio.file.Path;
import java.util.Optional;

public class OperaProxy {
    private static Process operaProcess = null;

    public static boolean startOperaProxy(int port) {
        try {
            Optional<File> optionalFile = proxyFileSearch();
            if (optionalFile.isEmpty()) {
                System.err.println("CRITICAL: opera-proxy NOT FOUND!");
                return false;
            }
            File proxyFile = optionalFile.get();

            // Снимаем карантин
            rmQuarantine(proxyFile);

            System.out.println("Запустился Opera Proxy (SOCKS5 mode on " + port + ")");
            // Команда: ./opera-proxy.darwin-amd64 -country EU -bind-address 127.0.0.1:ПОРТ -socks-mode
            ProcessBuilder pb = new ProcessBuilder(
                proxyFile.getAbsolutePath(), 
                "-country", "EU", 
                "-bind-address", "127.0.0.1:" + port,
                "-socks-mode"
            );
            pb.directory(proxyFile.getParentFile());
            pb.redirectErrorStream(true);

            operaProcess = pb.start();
            readeLog();

            return true;
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    public static void stopOperaProxy() {
        if (operaProcess != null && operaProcess.isAlive()) {
            operaProcess.destroyForcibly();
            operaProcess = null;
            System.out.println("DEBUG: opera-proxy stopped.");
        }
    }

    private static Optional<File> proxyFileSearch() {
        String appDir = System.getProperty("user.dir");
        File proxyFile = new File(appDir, "opera-proxy.darwin-amd64");

        if (!proxyFile.exists()) {
            try {
                String jarPath = App.class.getProtectionDomain().getCodeSource().getLocation().toURI().getPath();
                appDir = new File(jarPath).getParent();
                proxyFile = new File(appDir, "opera-proxy.darwin-amd64");
            } catch (URISyntaxException e) {
                e.printStackTrace();
            }
        }

        return proxyFile.exists() ? Optional.of(proxyFile) : Optional.empty();
    }

    private static void rmQuarantine(File file) {
        try {
            Runtime.getRuntime().exec(new String[]{"xattr", "-d", "com.apple.quarantine", file.getAbsolutePath()});
        } catch (Exception ignored) {}
        file.setExecutable(true);
    }

    private static void readeLog() {
        new Thread(() -> {
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(operaProcess.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    System.out.println("[OPERA_ENGINE]: " + line);
                }
            } catch (IOException e) { e.printStackTrace(); }
        }).start();
    }
}
