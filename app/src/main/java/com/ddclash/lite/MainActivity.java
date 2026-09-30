package com.ddclash.lite;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Color;
import android.net.VpnService;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.net.HttpURLConnection;
import java.net.URL;

public class MainActivity extends AppCompatActivity {
    private boolean isRunning = false;
    private Button btnToggle, btnAddConfig, btnSaveConfig, btnPing;
    private TextView tvStatus, tvPing, tvLogs;
    private EditText etConfigYaml, etDns;
    private ScrollView svLogs;
    private File configFile;
    private Handler mainHandler;

    private final BroadcastReceiver logReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (ClashVpnService.ACTION_CORE_LOG.equals(intent.getAction())) {
                String log = intent.getStringExtra(ClashVpnService.EXTRA_LOG_MSG);
                if (log != null) {
                    appendLog(log);
                }
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        mainHandler = new Handler(Looper.getMainLooper());

        btnToggle = findViewById(R.id.btnToggle);
        btnAddConfig = findViewById(R.id.btnAddConfig);
        btnSaveConfig = findViewById(R.id.btnSaveConfig);
        btnPing = findViewById(R.id.btnPing);
        tvStatus = findViewById(R.id.tvStatus);
        tvPing = findViewById(R.id.tvPing);
        tvLogs = findViewById(R.id.tvLogs);
        svLogs = findViewById(R.id.svLogs);
        etConfigYaml = findViewById(R.id.etConfigYaml);
        etDns = findViewById(R.id.etDns);

        configFile = new File(getFilesDir(), "config.yaml");
        loadSavedConfig();

        // Daftarkan receiver log core
        registerReceiver(logReceiver, new IntentFilter(ClashVpnService.ACTION_CORE_LOG), RECEIVER_EXPORTED);

        btnAddConfig.setOnClickListener(v -> showAddLinkDialog());

        btnSaveConfig.setOnClickListener(v -> {
            saveConfig(etConfigYaml.getText().toString());
            Toast.makeText(this, "Config tersimpan!", Toast.LENGTH_SHORT).show();
        });

        btnToggle.setOnClickListener(v -> {
            if (!isRunning) {
                saveConfig(etConfigYaml.getText().toString());
                Intent intent = VpnService.prepare(this);
                if (intent != null) {
                    startActivityForResult(intent, 1);
                } else {
                    onActivityResult(1, RESULT_OK, null);
                }
            } else {
                Intent stopIntent = new Intent(this, ClashVpnService.class);
                stopIntent.setAction("STOP");
                startService(stopIntent);
                isRunning = false;
                btnToggle.setText("START VPN");
                tvStatus.setText("Status: Disconnected");
                tvPing.setText("Ping: -- ms");
                tvPing.setTextColor(Color.parseColor("#495057"));
            }
        });

        // Fitur Tes Ping ke dns.google
        btnPing.setOnClickListener(v -> runPingTest());
    }

    private void runPingTest() {
        tvPing.setText("Testing...");
        tvPing.setTextColor(Color.parseColor("#E67E22"));
        appendLog("[Ping] Melakukan request ke http://dns.google...");

        new Thread(() -> {
            long start = System.currentTimeMillis();
            try {
                URL url = new URL("http://dns.google");
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setConnectTimeout(3500);
                conn.setReadTimeout(3500);
                conn.setRequestMethod("GET");
                conn.setInstanceFollowRedirects(false);

                int responseCode = conn.getResponseCode();
                long latency = System.currentTimeMillis() - start;
                conn.disconnect();

                mainHandler.post(() -> {
                    if (responseCode > 0) {
                        tvPing.setText(latency + " ms");
                        tvPing.setTextColor(Color.parseColor("#198754"));
                        appendLog("[Ping Result] Koneksi Terhubung! (" + responseCode + " OK) - " + latency + "ms");
                    } else {
                        tvPing.setText("Error");
                        tvPing.setTextColor(Color.RED);
                        appendLog("[Ping Result] Respon kosong / RTO.");
                    }
                });
            } catch (Exception e) {
                mainHandler.post(() -> {
                    tvPing.setText("RTO");
                    tvPing.setTextColor(Color.RED);
                    appendLog("[Ping Result] Gagal menjangkau server: " + e.getMessage());
                });
            }
        }).start();
    }

    private void appendLog(String log) {
        tvLogs.append(log + "\n");
        svLogs.post(() -> svLogs.fullScroll(ScrollView.FOCUS_DOWN));
    }

    private void showAddLinkDialog() {
        EditText input = new EditText(this);
        input.setHint("Paste vless:// atau vmess://");
        new AlertDialog.Builder(this)
                .setTitle("Import Node Proxy")
                .setView(input)
                .setPositiveButton("Convert", (dialog, which) -> {
                    String link = input.getText().toString().trim();
                    String proxyBlock = ConfigConverter.convertLinkToProxy(link);
                    if (proxyBlock != null) {
                        String fullYaml = ConfigConverter.buildFullYaml(proxyBlock, etDns.getText().toString().trim());
                        etConfigYaml.setText(fullYaml);
                        saveConfig(fullYaml);
                        Toast.makeText(this, "Node berhasil di-convert!", Toast.LENGTH_SHORT).show();
                    } else {
                        Toast.makeText(this, "Link salah atau belum didukung!", Toast.LENGTH_LONG).show();
                    }
                })
                .setNegativeButton("Batal", null)
                .show();
    }

    private void saveConfig(String yaml) {
        try (FileOutputStream fos = new FileOutputStream(configFile)) {
            fos.write(yaml.getBytes());
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void loadSavedConfig() {
        if (configFile.exists()) {
            try (FileInputStream fis = new FileInputStream(configFile)) {
                byte[] data = new byte[(int) configFile.length()];
                fis.read(data);
                etConfigYaml.setText(new String(data));
            } catch (Exception ignored) {}
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == 1 && resultCode == RESULT_OK) {
            Intent startIntent = new Intent(this, ClashVpnService.class);
            startService(startIntent);
            isRunning = true;
            btnToggle.setText("STOP VPN");
            tvStatus.setText("Status: Connected (TUN Mode)");
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        try {
            unregisterReceiver(logReceiver);
        } catch (Exception ignored) {}
    }
}
