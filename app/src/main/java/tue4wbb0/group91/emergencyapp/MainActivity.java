package tue4wbb0.group91.emergencyapp;

import android.Manifest;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.NetworkRequest;
import android.net.wifi.ScanResult;
import android.net.wifi.WifiManager;
import android.net.wifi.WifiNetworkSpecifier;
import android.os.Bundle;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

public class MainActivity extends AppCompatActivity {
    private TextView emergNetworkStatusLab;
    private Button scanBtn;
    private Button activateDropboxBtn;

    private WifiManager wifiManager;
    private ConnectivityManager conMgr;

    private BroadcastReceiver wifiScanReceiver;
    private ConnectivityManager.NetworkCallback networkCallback;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Get UI elements
        emergNetworkStatusLab = findViewById(R.id.emergNetworkStatusLab);
        scanBtn = findViewById(R.id.scanBtn);
        activateDropboxBtn = findViewById(R.id.activateDropboxBtn);

        // Add click handlers
        scanBtn.setOnClickListener(v -> scanNetworks());
        activateDropboxBtn.setOnClickListener(v -> activateDropbox());

        // Get system services
        wifiManager = (WifiManager) getSystemService(WIFI_SERVICE);
        conMgr = (ConnectivityManager) getSystemService(CONNECTIVITY_SERVICE);

        // Request required permissions
        requestPermissions(new String[]{
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_WIFI_STATE,
                Manifest.permission.CHANGE_WIFI_STATE,
                Manifest.permission.ACCESS_NETWORK_STATE,
                Manifest.permission.CHANGE_NETWORK_STATE
        }, 1);

        // Create wifi scan result broadcast receiver
        wifiScanReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                // Unregister wifi network scan result handler
                unregisterReceiver(wifiScanReceiver);

                // Call event handler
                scanDone(intent);
            }
        };

        // Create network callback
        networkCallback = new ConnectivityManager.NetworkCallback() {
            @Override
            public void onAvailable(@NonNull Network network) {
                super.onAvailable(network);

                // Unregister network callback
                conMgr.unregisterNetworkCallback(networkCallback);

                // Call event handler for network connection
                runOnUiThread(() -> onNetworkConnection(network));
            }

            @Override
            public void onUnavailable() {
                super.onUnavailable();

                // Unregister network callback
                conMgr.unregisterNetworkCallback(networkCallback);

                // Call event handler for failed network connection
                runOnUiThread(() -> onFailedNetworkConnection());
            }
        };
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
        // Register wifi network scan result handler
        registerReceiver(wifiScanReceiver, new IntentFilter(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION));

        // Start wifi scan
        if (wifiManager.startScan()) {
            scanBtn.setEnabled(false);
            activateDropboxBtn.setEnabled(false);
            emergNetworkStatusLab.setText("Scanning for emergency networks...");
        }
    }

    private void scanDone(final Intent rIntent) {
        // Check for successful scan
        boolean success = rIntent.getBooleanExtra(WifiManager.EXTRA_RESULTS_UPDATED, false);
        if (success) {
            // Find strongest emergency network (NOTE: level is expressed in dBm)
            int maxLevel = 0;
            String ssid = null;
            for (ScanResult r : wifiManager.getScanResults()) {
                if (r.SSID.startsWith("[EMERG]]")) {
                    if (r.level < maxLevel) {
                        maxLevel = r.level;
                        ssid = r.SSID;
                    }
                }
            }

            if (ssid != null) {
                // Set status label
                emergNetworkStatusLab.setText("Connecting to strongest emergency network: " + ssid);

                // Connect to emergency network
                connectEmergencyNetwork(ssid);
            } else {
                emergNetworkStatusLab.setText("No emergency networks found");
                scanBtn.setEnabled(true);
            }
        } else {
            emergNetworkStatusLab.setText("Could not scan for networks! Please try again later.");
            scanBtn.setEnabled(true);
        }
    }

    private void connectEmergencyNetwork(final String ssid) {
        // Create wifi specifier
        final WifiNetworkSpecifier wifiSpecifier = new WifiNetworkSpecifier.Builder()
                .setSsid(ssid)
                .setIsEnhancedOpen(true)
                .build();

        // Create network request
        final NetworkRequest networkRequest = new NetworkRequest.Builder()
                .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
                .setNetworkSpecifier(wifiSpecifier)
                .build();

        // Request network connection
        conMgr.requestNetwork(networkRequest, networkCallback);

        // Re-enable scan button
        scanBtn.setEnabled(true);
    }

    private void onNetworkConnection(final Network network) {
        Toast.makeText(this, "Connected!", Toast.LENGTH_LONG).show();

        // Enable activate button
        activateDropboxBtn.setEnabled(true);

        // Set status text
        emergNetworkStatusLab.setText("Connected to emergency network");
    }

    private void onFailedNetworkConnection() {
        // Show message
        Toast.makeText(MainActivity.this, "Could not connect!", Toast.LENGTH_LONG).show();

        // Set status text
        emergNetworkStatusLab.setText("Could not connect to emergency network");
    }

    private void activateDropbox() {
        // TODO: Send signal to open / close dropbox
    }
}
