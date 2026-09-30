package com.ddclash.lite;

import android.content.Intent;
import android.net.VpnService;
import android.os.ParcelFileDescriptor;
import java.io.BufferedReader;
import java.io.File;
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
            Builder builder = new Builder();
            builder.setSession("DDclash Lite");
            builder.setMtu(9000);
            builder.addAddress("172.19.0.1", 28);
            builder.addRoute("0.0.0.0", 0);
            builder.addDnsServer("198.18.0.1");

            vpnInterface = builder.establish();
            broadcastLog("[VPN] Virtual Network Interface tun0 established.");

            File config = new File(getFilesDir(), "config.yaml");
            String nativeDir = getApplicationInfo().nativeLibraryDir;
            File coreBin = new File(nativeDir, "libclash.so");

            if (coreBin.exists() && config.exists()) {
                ProcessBuilder pb = new ProcessBuilder(
                        coreBin.getAbsolutePath(),
                        "-d", getFilesDir().getAbsolutePath(),
                        "-f", config.getAbsolutePath()
                );
                pb.redirectErrorStream(true);
                clashProcess = pb.start();
                broadcastLog("[CORE] Mihomo process spawned successfully.");

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
            } else {
                broadcastLog("[ERROR] Binary libclash.so atau config.yaml tidak ditemukan!");
            }
        } catch (Exception e) {
            broadcastLog("[ERROR] " + e.getMessage());
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
