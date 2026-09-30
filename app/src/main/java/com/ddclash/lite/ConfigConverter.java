package com.ddclash.lite;

import android.net.Uri;
import android.util.Base64;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ConfigConverter {

    public static String convertLinkToProxy(String link) {
        try {
            if (link.startsWith("vmess://")) {
                String base64Str = link.substring(8);
                String jsonStr = new String(Base64.decode(base64Str, Base64.DEFAULT));
                JSONObject json = new JSONObject(jsonStr);

                String name = json.optString("ps", "Vmess-Node");
                String server = json.optString("add");
                int port = json.optInt("port", 443);
                String uuid = json.optString("id");
                int alterId = json.optInt("aid", 0);
                String net = json.optString("net", "tcp");
                String tls = json.optString("tls", "none");
                String path = json.optString("path", "/");
                String host = json.optString("host", server);

                StringBuilder sb = new StringBuilder();
                sb.append("  - name: \"").append(name).append("\"\n");
                sb.append("    type: vmess\n");
                sb.append("    server: ").append(server).append("\n");
                sb.append("    port: ").append(port).append("\n");
                sb.append("    uuid: ").append(uuid).append("\n");
                sb.append("    alterId: ").append(alterId).append("\n");
                sb.append("    cipher: auto\n");
                sb.append("    udp: true\n");
                if ("tls".equalsIgnoreCase(tls)) {
                    sb.append("    tls: true\n");
                    sb.append("    servername: ").append(host).append("\n");
                }
                if ("ws".equalsIgnoreCase(net)) {
                    sb.append("    network: ws\n");
                    sb.append("    ws-opts:\n");
                    sb.append("      path: \"").append(path).append("\"\n");
                    sb.append("      headers:\n");
                    sb.append("        Host: ").append(host).append("\n");
                }
                return sb.toString();

            } else if (link.startsWith("vless://")) {
                Uri uri = Uri.parse(link);
                String name = uri.getFragment() != null ? uri.getFragment() : "Vless-Node";
                String server = uri.getHost();
                int port = uri.getPort();
                String uuid = uri.getUserInfo();
                String security = uri.getQueryParameter("security");
                String type = uri.getQueryParameter("type");
                String path = uri.getQueryParameter("path");
                String host = uri.getQueryParameter("host");
                String sni = uri.getQueryParameter("sni");

                StringBuilder sb = new StringBuilder();
                sb.append("  - name: \"").append(name).append("\"\n");
                sb.append("    type: vless\n");
                sb.append("    server: ").append(server).append("\n");
                sb.append("    port: ").append(port).append("\n");
                sb.append("    uuid: ").append(uuid).append("\n");
                sb.append("    udp: true\n");
                if ("tls".equalsIgnoreCase(security) || "reality".equalsIgnoreCase(security)) {
                    sb.append("    tls: true\n");
                    sb.append("    servername: ").append(sni != null ? sni : (host != null ? host : server)).append("\n");
                }
                if ("ws".equalsIgnoreCase(type)) {
                    sb.append("    network: ws\n");
                    sb.append("    ws-opts:\n");
                    sb.append("      path: \"").append(path != null ? path : "/").append("\"\n");
                    if (host != null) {
                        sb.append("      headers:\n");
                        sb.append("        Host: ").append(host).append("\n");
                    }
                }
                return sb.toString();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }

    public static String buildFullYaml(String proxyItemYaml, String dnsList) {
        String nodeName = "PROXY";
        if (proxyItemYaml != null && proxyItemYaml.contains("name: \"")) {
            int start = proxyItemYaml.indexOf("name: \"") + 7;
            int end = proxyItemYaml.indexOf("\"", start);
            if (start > 6 && end > start) {
                nodeName = proxyItemYaml.substring(start, end);
            }
        }

        return "mode: rule\n" +
                "port: 7890\n" +
                "socks-port: 7891\n" +
                "allow-lan: false\n" +
                "log-level: info\n" +
                "ipv6: false\n\n" +
                "tun:\n" +
                "  enable: true\n" +
                "  stack: gvisor\n" +
                "  dns-hijack:\n" +
                "    - any:53\n" +
                "  auto-route: true\n" +
                "  auto-detect-interface: true\n" +
                "  udp: true\n\n" +
                "dns:\n" +
                "  enable: true\n" +
                "  ipv6: false\n" +
                "  default-nameserver:\n" +
                "    - 1.1.1.1\n" +
                "    - 8.8.8.8\n" +
                "  enhanced-mode: fake-ip\n" +
                "  fake-ip-range: 198.18.0.1/16\n" +
                "  nameserver:\n" +
                "    - " + (dnsList.contains(",") ? dnsList.replace(",", "\n    - ") : dnsList) + "\n\n" +
                "proxies:\n" +
                (proxyItemYaml != null ? proxyItemYaml : "") + "\n\n" +
                "proxy-groups:\n" +
                "  - name: PROXY\n" +
                "    type: select\n" +
                "    udp: true\n" +
                "    proxies:\n" +
                "      - \"" + nodeName + "\"\n" +
                "      - DIRECT\n\n" +
                "rules:\n" +
                "  - MATCH,PROXY\n";
    }

    // FUNGSI 1-KLIK PERBAIKI CONFIG
    public static String autoRepairYaml(String rawYaml, String dnsList) {
        if (rawYaml == null || rawYaml.trim().isEmpty()) {
            return "";
        }

        // Ekstraksi bagian proxies: dari raw text
        String proxiesBlock = "";
        int pIndex = rawYaml.indexOf("proxies:");
        if (pIndex != -1) {
            int pgIndex = rawYaml.indexOf("proxy-groups:", pIndex);
            int rIndex = rawYaml.indexOf("rules:", pIndex);
            int endIndex = rawYaml.length();
            if (pgIndex != -1) endIndex = Math.min(endIndex, pgIndex);
            if (rIndex != -1) endIndex = Math.min(endIndex, rIndex);
            proxiesBlock = rawYaml.substring(pIndex + 8, endIndex).trim();
        }

        // Ambil semua nama node yang ada untuk dimasukkan ke proxy-groups
        List<String> nodeNames = new ArrayList<>();
        Pattern pattern = Pattern.compile("name:\\s*[\"']?([^\"'\n\r]+)[\"']?");
        Matcher matcher = pattern.matcher(proxiesBlock);
        while (matcher.find()) {
            String name = matcher.group(1).trim();
            if (!nodeNames.contains(name)) {
                nodeNames.add(name);
            }
        }

        StringBuilder proxyGroupsSb = new StringBuilder();
        proxyGroupsSb.append("proxy-groups:\n");
        proxyGroupsSb.append("  - name: PROXY\n");
        proxyGroupsSb.append("    type: select\n");
        proxyGroupsSb.append("    udp: true\n");
        proxyGroupsSb.append("    proxies:\n");
        if (nodeNames.isEmpty()) {
            proxyGroupsSb.append("      - DIRECT\n");
        } else {
            for (String node : nodeNames) {
                proxyGroupsSb.append("      - \"").append(node).append("\"\n");
            }
            proxyGroupsSb.append("      - DIRECT\n");
        }

        // Susun ulang struktur TUN & Core yang valid
        return "mode: rule\n" +
                "port: 7890\n" +
                "socks-port: 7891\n" +
                "allow-lan: false\n" +
                "log-level: info\n" +
                "ipv6: false\n\n" +
                "tun:\n" +
                "  enable: true\n" +
                "  stack: gvisor\n" +
                "  dns-hijack:\n" +
                "    - any:53\n" +
                "  auto-route: true\n" +
                "  auto-detect-interface: true\n" +
                "  udp: true\n\n" +
                "dns:\n" +
                "  enable: true\n" +
                "  ipv6: false\n" +
                "  default-nameserver:\n" +
                "    - 1.1.1.1\n" +
                "    - 8.8.8.8\n" +
                "  enhanced-mode: fake-ip\n" +
                "  fake-ip-range: 198.18.0.1/16\n" +
                "  nameserver:\n" +
                "    - " + (dnsList.contains(",") ? dnsList.replace(",", "\n    - ") : dnsList) + "\n\n" +
                "proxies:\n" +
                (proxiesBlock.isEmpty() ? "  # Masukkan proxies di sini\n" : proxiesBlock + "\n\n") +
                proxyGroupsSb.toString() + "\n" +
                "rules:\n" +
                "  - MATCH,PROXY\n";
    }
}
