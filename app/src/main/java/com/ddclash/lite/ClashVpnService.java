package com.ddclash.lite;

import android.content.Intent;
import android.net.VpnService;
import android.os.ParcelFileDescriptor;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;

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

    private void prepareCoreBinary(File targetBin) {
        try {
            String nativeDir = getApplicationInfo().nativeLibraryDir;
            File originalSo = new File(nativeDir, "libclash.so");

            if (originalSo.exists() && (!targetBin.exists() || targetBin.length() != originalSo.length())) {
                broadcastLog("[SETUP] Menyalin core binary ke internal...");
                try (InputStream in = new FileInputStream(originalSo);
                     OutputStream out = new FileOutputStream(targetBin)) {
                    byte[] buf = new byte[8192];
                    int len;
                    while ((len = in.read(buf)) > 0) {
                        out.write(buf, 0, len);
                    }
                }
            }
            targetBin.setExecutable(true, false);
            targetBin.setReadable(true, false);
        } catch (Exception e) {
            broadcastLog("[ERROR Setup] " + e.getMessage());
        }
    }

    private void startVpn() {
        try {
            File configFile = new File(getFilesDir(), "config.yaml");
            if (!configFile.exists() || configFile.length() == 0) {
                broadcastLog("[ERROR] config.yaml belum disimpan atau masih kosong!");
                return;
            }

            File coreBin = new File(getFilesDir(), "clash_core");
            prepareCoreBinary(coreBin);

            if (!coreBin.exists()) {
                broadcastLog("[ERROR] Binary clash_core tidak ditemukan!");
                return;
            }

            // Inisialisasi VPN Android
            Builder builder = new Builder();
            builder.setSession("DDclash Lite");
            builder.setMtu(9000);
            builder.addAddress("172.19.0.1", 28);
            builder.addRoute("0.0.0.0", 0);
            builder.addDnsServer("198.18.0.1");

            // Bypass proses sendiri dari VPN agar koneksi outbound ke VPS tidak loop
            try {
                builder.addDisallowedApplication(getPackageName());
            } catch (Exception ignored) {}

            vpnInterface = builder.establish();
            broadcastLog("[VPN] tun0 interface established & package bypassed.");

            // Jalankan Core Mihomo
            ProcessBuilder pb = new ProcessBuilder(
                    coreBin.getAbsolutePath(),
                    "-d", getFilesDir().getAbsolutePath(),
                    "-f", configFile.getAbsolutePath()
            );
            pb.redirectErrorStream(true);
            clashProcess = pb.start();
            broadcastLog("[CORE] Mihomo process started.");

            // Thread pembaca log terminal core
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

    private void broadcastLog(String msg) {
        Intent intent = new Intent(ACTION_CORE_LOG);
        intent.putExtra(EXTRA_LOG_MSG, msg);
        sendBroadcast(intent);
    }

    private void stopVpn() {
        broadcastLog("[VPN] Menghentikan koneksi...");
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
