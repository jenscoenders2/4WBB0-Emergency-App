package tue4wbb0.group91.emergencyapp;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import android.Manifest;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.net.wifi.ScanResult;
import android.net.wifi.WifiManager;
import android.os.Bundle;
import android.widget.TextView;
import android.widget.Toast;

import java.util.List;

public class MainActivity extends AppCompatActivity {
    private TextView curNetworkLab;
    private TextView emergNetworkStatusLab;

    private WifiManager wifiManager;
    private BroadcastReceiver wifiScanReceiver;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Get views
        curNetworkLab = findViewById(R.id.curNetworkLab);
        emergNetworkStatusLab = findViewById(R.id.emergNetworkStatusLab);

        // Add click handlers
        findViewById(R.id.scanBtn).setOnClickListener(v -> scanNetworks());
        findViewById(R.id.connectBtn).setOnClickListener(v -> connectEmergencyNetwork());

        // Get wifi manager service
        wifiManager = (WifiManager) getSystemService(WIFI_SERVICE);

        // Request required permissions
        requestPermissions(new String[]{
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_WIFI_STATE,
                Manifest.permission.CHANGE_WIFI_STATE
        }, 1);
    }

    @Override
    protected void onStart() {
        super.onStart();

        // Register wifi network scan result handler
        wifiScanReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                scanDone(intent);
            }
        };
        registerReceiver(wifiScanReceiver, new IntentFilter(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION));
    }

    @Override
    protected void onStop() {
        super.onStop();

        // Unregister wifi network scan result handler
        unregisterReceiver(wifiScanReceiver);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        if (requestCode == 1) {
            if (grantResults.length == 0) {
                finish();
            } else {
                for (int r : grantResults) {
                    if (r != PackageManager.PERMISSION_GRANTED) {
                        finish();
                        break;
                    }
                }
            }
        } else {
            super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        }
    }

    private void scanNetworks() {
        // Start wifi scan
        final boolean result = wifiManager.startScan();

        // Handle result
        if (result) {
            emergNetworkStatusLab.setText("Scanning for emergency networks...");
        }
    }

    private void scanDone(Intent resultIntent) {
        boolean success = resultIntent.getBooleanExtra(WifiManager.EXTRA_RESULTS_UPDATED, false);
        if (success) {
            final List<ScanResult> scanResults = wifiManager.getScanResults();
        } else {
            emergNetworkStatusLab.setText("Could not scan for networks!");
        }
    }

    private void connectEmergencyNetwork() {

    }
}
