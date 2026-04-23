package org.example; // Замени на свой пакет, если нужно

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

public enum DnsProvider {
    CLOUDFLARE("Cloudflare", 
        List.of("1.1.1.1", "1.0.0.1"), 
        List.of("2606:4700:4700::1111", "2606:4700:4700::1001")),

    GOOGLE("Google", 
        List.of("8.8.8.8", "8.8.4.4"), 
        List.of("2001:4860:4860::8888", "2001:4860:4860::8844")),
    
    MALW("MALW", 
        List.of("84.21.189.133", "64.188.98.242"), 
        List.of("2a01:ecc0:2c1:2::2", "2a12:bec4:1460:d5::2")),
        
    XBOX("Xbox DNS", 
        List.of("111.88.96.50", "111.88.96.51"), 
        List.of("2a00:ab00:1233:26::50", "2a00:ab00:1233:26::51")),
        
    GEOHIDE("GeoHide", 
        List.of("45.155.204.190", "95.182.120.241"), 
        List.of("2a0c:9300:0:54::1")),
        
    COMSS("COMSS", 
        List.of("83.220.169.155", "212.109.195.93", "195.133.25.16"), 
        List.of("2a01:230:4:915::2", "2a01:230:4:306::2"));

    private final String label;
    private final List<String> ipv4;
    private final List<String> ipv6;

    // Конструктор (всегда private в enum)
    DnsProvider(String label, List<String> ipv4, List<String> ipv6) {
        this.label = label;
        this.ipv4 = ipv4;
        this.ipv6 = ipv6;
    }

    // Геттеры
    public String getLabel() { return label; }
    public List<String> getIpv4() { return ipv4; }
    public List<String> getIpv6() { return ipv6; }

    /**
     * Поиск провайдера по текстовому названию (например, из конфига)
     */
    public static Optional<DnsProvider> findByLabel(String label) {
        return Arrays.stream(values())
                .filter(p -> p.label.equalsIgnoreCase(label))
                .findFirst();
    }

    /**
     * Возвращает первый доступный IPv4 адрес
     */
    public String getPrimaryIpv4() {
        return ipv4.get(0);
    }
}