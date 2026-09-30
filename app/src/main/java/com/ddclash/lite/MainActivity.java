package com.ddclash.lite;

import android.content.Intent;
import android.net.VpnService;
import android.os.Bundle;
import android.widget.Button;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;

public class MainActivity extends AppCompatActivity {
    private boolean isRunning = false;
    private Button btnToggle;
    private TextView tvStatus;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        btnToggle = findViewById(R.id.btnToggle);
        tvStatus = findViewById(R.id.tvStatus);

        btnToggle.setOnClickListener(v -> {
            if (!isRunning) {
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
            }
        });
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == 1 && resultCode == RESULT_OK) {
            Intent startIntent = new Intent(this, ClashVpnService.class);
            startService(startIntent);
            isRunning = true;
            btnToggle.setText("STOP VPN");
            tvStatus.setText("Status: Connected");
        }
    }
}
