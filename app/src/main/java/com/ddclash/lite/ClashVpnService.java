package com.ddclash.lite;

import android.content.Intent;
import android.net.VpnService;
import android.os.ParcelFileDescriptor;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStreamReader;

public class ClashVpnService extends VpnService {
    public static final String ACTION_CORE_LOG = "com.ddclash.lite.CORE_LOG";
    public static final String EXTRA_LOG_MSG = "log_msg";

    private ParcelFileDescriptor vpnInterface = null;
    private Process clashProcess = null;
    private Thread logThread = null;

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        String action = intent != null ? intent.getAction() : "";
        if ("STOP".equals(action)) {
            stopVpn();
            return START_NOT_STICKY;
        }

        startVpn();
        return START_STICKY;
    }

    private void startVpn() {
        try {
            File configFile = new File(getFilesDir(), "config.yaml");
            if (!configFile.exists() || configFile.length() == 0) {
                broadcastLog("[ERROR] config.yaml belum ada / kosong!");
                return;
            }

            // Binary dieksekusi langsung dari native library dir
            String nativeDir = getApplicationInfo().nativeLibraryDir;
            File coreBin = new File(nativeDir, "libclash.so");

            if (!coreBin.exists()) {
                broadcastLog("[ERROR] libclash.so tidak ditemukan di native dir!");
                return;
            }

            // 1. Bangun Virtual TUN Android
            Builder builder = new Builder();
            builder.setSession("DDclash Lite");
            builder.setMtu(9000);
            builder.addAddress("172.19.0.1", 28);
            builder.addRoute("0.0.0.0", 0);
            builder.addDnsServer("198.18.0.1");

            try {
                builder.addDisallowedApplication(getPackageName());
            } catch (Exception ignored) {}

            vpnInterface = builder.establish();
            int fd = vpnInterface.getFd();
            broadcastLog("[VPN] Virtual tun0 interface siap. FD: " + fd);

            // 2. Suntikkan File Descriptor langsung ke config.yaml
            injectFdToConfig(configFile, fd);

            // 3. Jalankan binary Mihomo
            ProcessBuilder pb = new ProcessBuilder(
                    coreBin.getAbsolutePath(),
                    "-d", getFilesDir().getAbsolutePath(),
                    "-f", configFile.getAbsolutePath()
            );
            pb.redirectErrorStream(true);
            clashProcess = pb.start();
            broadcastLog("[CORE] Mihomo aktif dengan TUN FD " + fd);

            logThread = new Thread(() -> {
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(clashProcess.getInputStream()))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        broadcastLog(line);
                    }
                } catch (Exception ignored) {}
            });
            logThread.start();

        } catch (Exception e) {
            broadcastLog("[ERROR] Gagal menyalakan core: " + e.getMessage());
        }
    }

    private void injectFdToConfig(File file, int fd) {
        try {
            FileInputStream fis = new FileInputStream(file);
            byte[] b = new byte[(int) file.length()];
            fis.read(b);
            fis.close();
            String content = new String(b);

            // Tambahkan / ganti baris file-descriptor pada blok tun
            if (content.contains("file-descriptor:")) {
                content = content.replaceAll("file-descriptor:\\s*\\d+", "file-descriptor: " + fd);
            } else if (content.contains("tun:")) {
                content = content.replace("tun:", "tun:\n  file-descriptor: " + fd);
            }

            FileOutputStream fos = new FileOutputStream(file);
            fos.write(content.getBytes());
            fos.close();
        } catch (Exception e) {
            broadcastLog("[ERROR Inject FD] " + e.getMessage());
        }
    }

    private void broadcastLog(String msg) {
        Intent intent = new Intent(ACTION_CORE_LOG);
        intent.putExtra(EXTRA_LOG_MSG, msg);
        sendBroadcast(intent);
    }

    private void stopVpn() {
        broadcastLog("[VPN] Menghentikan service...");
        if (clashProcess != null) {
            clashProcess.destroy();
            clashProcess = null;
        }
        if (logThread != null) {
            logThread.interrupt();
            logThread = null;
        }
        try {
            if (vpnInterface != null) {
                vpnInterface.close();
                vpnInterface = null;
            }
        } catch (Exception ignored) {}
        stopSelf();
    }

    @Override
    public void onDestroy() {
        stopVpn();
        super.onDestroy();
    }
}
