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
import android.os.AsyncTask;
import android.os.Bundle;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;

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
        activateDropboxBtn.setOnClickListener(v -> AsyncTask.execute(this::activateDropbox));

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

                // Bind process to network
                conMgr.bindProcessToNetwork(network);

                // Call event handler for network connection
                runOnUiThread(MainActivity.this::onNetworkConnection);
            }

            @Override
            public void onUnavailable() {
                super.onUnavailable();

                // Call event handler for failed network connection
                runOnUiThread(MainActivity.this::onFailedNetworkConnection);
            }

            @Override
            public void onLost(@NonNull Network network) {
                super.onLost(network);

                // Call event handler for lost network connection
                runOnUiThread(MainActivity.this::onLostNetworkConnection);
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
                if (r.SSID.startsWith("[EMERG]")) {
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
        // Unregister previous network callback
        conMgr.unregisterNetworkCallback(networkCallback);

        // Create wifi specifier
        final WifiNetworkSpecifier wifiSpecifier = new WifiNetworkSpecifier.Builder()
                .setSsid(ssid)
                .setWpa2Passphrase("Group91EmergDropboxV1")
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

    private void onNetworkConnection() {
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

    private void onLostNetworkConnection() {
        // Set status text
        emergNetworkStatusLab.setText("Network connection closed");

        // Disable activate button
        activateDropboxBtn.setEnabled(false);
    }

    private void activateDropbox() {
        // Create target socket
        // TODO: Send actual command message
        try {
            DatagramSocket s = new DatagramSocket();
            final byte[] buf = "TEST".getBytes();
            DatagramPacket p = new DatagramPacket(buf, buf.length, InetAddress.getByName("192.168.4.1"), 6969);
            s.send(p);
            s.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
