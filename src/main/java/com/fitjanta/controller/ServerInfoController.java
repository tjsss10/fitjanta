package com.fitjanta.controller;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Tells the QR page (qr.html) which Wi-Fi address a phone should open,
 * e.g. http://192.168.31.236:8080. The address is detected each time,
 * so the QR code stays correct even if the laptop's IP changes.
 */
@RestController
@RequestMapping("/api/server-info")
public class ServerInfoController {

    @Value("${server.port:8080}")
    private int port;

    @GetMapping
    public Map<String, Object> serverInfo() throws Exception {

        List<String> preferred = new ArrayList<>();
        List<String> others = new ArrayList<>();

        for (NetworkInterface nic : Collections.list(NetworkInterface.getNetworkInterfaces())) {

            if (!nic.isUp() || nic.isLoopback() || nic.isVirtual()) {
                continue;
            }

            String name = (nic.getDisplayName() + " " + nic.getName()).toLowerCase(Locale.ROOT);

            // Skip adapters a phone cannot reach (VMs, WSL, Bluetooth, VPN tunnels)
            if (name.matches(".*(virtual|vmware|vbox|hyper-v|wsl|loopback|bluetooth|tap|tunnel|vpn).*")) {
                continue;
            }

            for (InetAddress address : Collections.list(nic.getInetAddresses())) {

                if (address instanceof Inet4Address && address.isSiteLocalAddress()) {

                    String url = "http://" + address.getHostAddress() + ":" + port;

                    if (name.contains("wi-fi") || name.contains("wireless") || name.contains("wlan")) {
                        preferred.add(url);
                    } else {
                        others.add(url);
                    }
                }
            }
        }

        List<String> urls = new ArrayList<>(preferred);
        urls.addAll(others);

        // Cloud hosting: the permanent public address
        String cloudUrl = cloudUrl();
        if (cloudUrl != null) {
            Map<String, Object> info = new LinkedHashMap<>();
            info.put("urls", List.of(cloudUrl));
            info.put("publicUrl", cloudUrl);
            info.put("cloud", true);
            info.put("port", port);
            return info;
        }

        // Public https link from start-public-link.ps1 (Pinggy / Cloudflare tunnel), if it's running
        String publicUrl = null;
        java.nio.file.Path urlFile = java.nio.file.Path.of("tools", "public-url.txt");
        boolean tunnelRunning = ProcessHandle.allProcesses()
                .anyMatch(ph -> ph.info().command().map(c -> {
                    String cmd = c.toLowerCase(Locale.ROOT);
                    return cmd.endsWith("cloudflared.exe") || cmd.endsWith("ssh.exe");
                }).orElse(false));
        if (tunnelRunning && java.nio.file.Files.exists(urlFile)) {
            String u = java.nio.file.Files.readString(urlFile).trim();
            if (u.matches("https://[a-z0-9-]+\\.(trycloudflare\\.com|free\\.pinggy\\.net|run\\.pinggy-free\\.link|a\\.free\\.pinggy\\.link)")) {
                publicUrl = u;
                urls.add(0, u);
            }
        }

        Map<String, Object> info = new LinkedHashMap<>();
        info.put("urls", urls);
        info.put("publicUrl", publicUrl);
        info.put("port", port);
        return info;
    }

    /** Public URL when running on a cloud platform (set by the platform or by APP_PUBLIC_URL). */
    private static String cloudUrl() {
        String explicit = System.getenv("APP_PUBLIC_URL");
        if (explicit != null && explicit.startsWith("https://")) return explicit.replaceAll("/+$", "");
        String railway = System.getenv("RAILWAY_PUBLIC_DOMAIN");
        if (railway != null && !railway.isBlank()) return "https://" + railway;
        String render = System.getenv("RENDER_EXTERNAL_URL");
        if (render != null && render.startsWith("https://")) return render.replaceAll("/+$", "");
        return null;
    }
}
