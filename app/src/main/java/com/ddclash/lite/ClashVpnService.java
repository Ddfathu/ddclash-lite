package com.ddclash.lite;

import android.content.Intent;
import android.net.VpnService;
import android.os.ParcelFileDescriptor;
import java.io.File;

public class ClashVpnService extends VpnService {
    private ParcelFileDescriptor vpnInterface = null;
    private Process clashProcess = null;

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
            builder.setSession("DDclash");
            builder.addAddress("172.19.0.1", 30);
            builder.addRoute("0.0.0.0", 0);
            vpnInterface = builder.establish();

            // Menjalankan binary core native yang di-inject di jniLibs
            String nativeDir = getApplicationInfo().nativeLibraryDir;
            File coreBin = new File(nativeDir, "libclash.so");

            if (coreBin.exists()) {
                clashProcess = new ProcessBuilder(coreBin.getAbsolutePath(), "-d", getFilesDir().getAbsolutePath()).start();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void stopVpn() {
        if (clashProcess != null) {
            clashProcess.destroy();
            clashProcess = null;
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
