package com.ddclash.lite;

import android.net.Uri;
import android.util.Base64;
import org.json.JSONObject;

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

        // Konfigurasi murni non-root: Port SOCKS5 & Mixed Proxy lokal
        return "mode: rule\n" +
                "mixed-port: 7890\n" +
                "socks-port: 7891\n" +
                "allow-lan: false\n" +
                "log-level: info\n" +
                "ipv6: false\n" +
                "external-controller: 127.0.0.1:9090\n\n" +
                "dns:\n" +
                "  enable: true\n" +
                "  ipv6: false\n" +
                "  listen: 127.0.0.1:1053\n" +
                "  enhanced-mode: fake-ip\n" +
                "  fake-ip-range: 198.18.0.1/16\n" +
                "  nameserver:\n" +
                "    - 1.1.1.1\n" +
                "    - 8.8.8.8\n\n" +
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

    public static String autoRepairYaml(String rawYaml, String dnsList) {
        if (rawYaml == null || rawYaml.trim().isEmpty()) return "";
        // Bersihkan blok tun yang bikin crash di Android non-root
        String cleaned = rawYaml.replaceAll("(?s)tun:.*?(?=(dns:|proxies:|proxy-groups:|rules:|$))", "");
        return cleaned;
    }
}
