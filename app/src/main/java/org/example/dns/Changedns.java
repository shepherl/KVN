package org.example.dns;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public class Changedns {
    /**
     * Updates the DNS setting in the WireGuard configuration file.
     * 
     * @param configPath Path to the .conf file
     * @param provider The DnsProvider to use
     */
    public static void change(Path configPath, DnsProvider provider) {
        if (configPath == null || !Files.exists(configPath)) {
            System.err.println("Config file not found: " + configPath);
            return;
        }

        try {
            List<String> lines = Files.readAllLines(configPath);
            List<String> newLines = new ArrayList<>();
            
            boolean inInterface = false;
            boolean dnsUpdated = false;
            
            // Collect all DNS addresses from provider
            List<String> dnsAddresses = new ArrayList<>();
            dnsAddresses.addAll(provider.getIpv4());
            dnsAddresses.addAll(provider.getIpv6());
            String dnsValue = String.join(", ", dnsAddresses);
            String dnsLine = "DNS = " + dnsValue;

            for (int i = 0; i < lines.size(); i++) {
                String line = lines.get(i).trim();
                
                if (line.startsWith("[") && line.endsWith("]")) {
                    if (inInterface && !dnsUpdated) {
                        newLines.add(dnsLine);
                        dnsUpdated = true;
                    }
                    inInterface = line.equalsIgnoreCase("[Interface]");
                    newLines.add(lines.get(i));
                    continue;
                }
                
                if (inInterface && line.toLowerCase().startsWith("dns")) {
                    if (!dnsUpdated) {
                        newLines.add(dnsLine);
                        dnsUpdated = true;
                    }
                    // Skip other DNS lines in the same [Interface] section
                    continue;
                }
                
                newLines.add(lines.get(i));
            }
            
            // If we reached the end of file while in [Interface] and didn't update DNS
            if (inInterface && !dnsUpdated) {
                newLines.add(dnsLine);
            }

            Files.write(configPath, newLines);
            System.out.println("DNS updated to " + provider.getLabel() + " in " + configPath);

        } catch (IOException e) {
            System.err.println("Error updating DNS in config: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
