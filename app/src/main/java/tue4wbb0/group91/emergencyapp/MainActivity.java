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

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintStream;
import java.net.Socket;
import java.util.concurrent.TimeoutException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class MainActivity extends AppCompatActivity {
    private static final String EMERGENCY_NETWORK_PREFIX = "[EMERG]";
    private static final String EMERGENCY_NETWORK_PASSWORD = "Group91EmergDropboxV1";

    private static final int WAIT_TIME = 100;

    private TextView emergNetworkStatusLab;
    private Button scanBtn;
    private Button activateDropboxBtn;

    private WifiManager wifiManager;
    private ConnectivityManager conMgr;

    private BroadcastReceiver wifiScanReceiver;
    private ConnectivityManager.NetworkCallback networkCallback;

    private boolean dropboxIsOpen = false;
    private boolean networkCallbackIsRegistered = false;

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
                Manifest.permission.ACCESS_COARSE_LOCATION,
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

                // Unbind process from network
                conMgr.bindProcessToNetwork(null);

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
        }

        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
    }

    private void scanNetworks() {
        // Register wifi network scan result handler
        registerReceiver(wifiScanReceiver, new IntentFilter(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION));

        // Start wifi scan
        if (wifiManager.startScan()) {
            scanBtn.setEnabled(false);
            activateDropboxBtn.setEnabled(false);
            emergNetworkStatusLab.setText(R.string.status_scanning);
        }
    }

    private void scanDone(final Intent rIntent) {
        // Check for successful scan
        boolean success = rIntent.getBooleanExtra(WifiManager.EXTRA_RESULTS_UPDATED, false);
        if (success) {
            // Find strongest emergency network (NOTE: level is expressed in dBm)
            int strongestLevel = 0;
            String ssid = null;
            for (ScanResult r : wifiManager.getScanResults()) {
                if (r.SSID.startsWith(EMERGENCY_NETWORK_PREFIX)) {
                    if (r.level < strongestLevel) {
                        strongestLevel = r.level;
                        ssid = r.SSID;
                    }
                }
            }

            if (ssid != null) {
                emergNetworkStatusLab.setText(getString(R.string.status_connecting, ssid));

                // Connect to emergency network
                connectEmergencyNetwork(ssid);
            } else {
                emergNetworkStatusLab.setText(R.string.status_none_found);
                scanBtn.setEnabled(true);
            }
        } else {
            emergNetworkStatusLab.setText(R.string.status_scan_failed);
            scanBtn.setEnabled(true);
        }
    }

    private void connectEmergencyNetwork(final String ssid) {
        // Unregister previous network callback
        if (networkCallbackIsRegistered) {
            conMgr.unregisterNetworkCallback(networkCallback);
            networkCallbackIsRegistered = false;
        }

        // Create wifi specifier
        final WifiNetworkSpecifier wifiSpecifier = new WifiNetworkSpecifier.Builder()
                .setSsid(ssid)
                .setWpa2Passphrase(EMERGENCY_NETWORK_PASSWORD)
                .build();

        // Create network request
        final NetworkRequest networkRequest = new NetworkRequest.Builder()
                .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
                .setNetworkSpecifier(wifiSpecifier)
                .build();

        // Request network connection
        networkCallbackIsRegistered = true;
        conMgr.requestNetwork(networkRequest, networkCallback);

        // Re-enable scan button
        scanBtn.setEnabled(true);
    }

    private void onNetworkConnection() {
        // Set status text
        emergNetworkStatusLab.setText(R.string.status_connected);

        // Retrieve current dropbox state
        AsyncTask.execute(this::updateDropboxState);
    }

    private void onFailedNetworkConnection() {
        // Show message
        Toast.makeText(MainActivity.this, R.string.toast_connect_failed, Toast.LENGTH_LONG).show();

        // Set status text
        emergNetworkStatusLab.setText(R.string.status_connect_failed);
    }

    private void onLostNetworkConnection() {
        // Set status text
        emergNetworkStatusLab.setText(R.string.status_network_lost);

        // Disable activate button
        activateDropboxBtn.setEnabled(false);
    }

    private void updateDropboxState() {
        sendDropboxCommand("STATUS", false);
    }

    private void activateDropbox() {
        runOnUiThread(() -> activateDropboxBtn.setEnabled(false));

        if (dropboxIsOpen) {
            sendDropboxCommand("CLOSE", true);
        } else {
            sendDropboxCommand("OPEN", true);
        }
    }

    /**
     * Sends a command to the drop box and updates the dropboxIsOpen parameter based on the response.
     *
     * @param command The command to send
     * @param alwaysEnableActionButton Whether to enable the action button, regardless of command success
     */
    private void sendDropboxCommand(final String command, final boolean alwaysEnableActionButton) {
        try {
            // Open socket to dropbox
            Socket s = new Socket("192.168.4.1", 6969);
            BufferedInputStream is = new BufferedInputStream(s.getInputStream());
            PrintStream ps = new PrintStream(s.getOutputStream());

            // Request dropbox status and read response
            ps.print(command);
            String response = readResponse(is);

            // Process answer
            Matcher m = Pattern.compile("^OPEN=(\\d)$").matcher(response);
            if (m.find()) {
                dropboxIsOpen = m.group(1).equals("1");
            } else {
                throw new Exception("Invalid response");
            }

            // Close socket
            ps.close();
            is.close();
            s.close();
        } catch (Exception e) {
            e.printStackTrace();

            if (!alwaysEnableActionButton) {
                return;
            }
        }

        // Update activate dropbox button
        runOnUiThread(() -> {
            activateDropboxBtn.setEnabled(true);
            if (dropboxIsOpen) {
                activateDropboxBtn.setText(R.string.action_close);
            } else {
                activateDropboxBtn.setText(R.string.action_open);
            }
        });
    }

    /**
     * Reads a linefeed-terminated response from the input stream.
     *
     * @param is The input stream to read from
     * @return The response string without the linefeed character
     * @throws TimeoutException When response takes longer than WAIT_TIME milliseconds
     * @throws IOException When an IOException occurs when reading from the input stream
     * @throws InterruptedException When the executing thread is interrupted
     */
    private String readResponse(BufferedInputStream is) throws TimeoutException, IOException, InterruptedException {
        int timeout = 5000;
        StringBuilder response = new StringBuilder();
        while (timeout > 0) {
            if (is.available() > 0) {
                char c = (char) is.read();
                if (c == '\n') {
                    break;
                } else {
                    response.append(c);
                }
            } else {
                Thread.sleep(WAIT_TIME);
                timeout -= WAIT_TIME;
            }
        }
        if (timeout == 0) {
            throw new TimeoutException("Request timed out");
        }

        return response.toString();
    }
}
