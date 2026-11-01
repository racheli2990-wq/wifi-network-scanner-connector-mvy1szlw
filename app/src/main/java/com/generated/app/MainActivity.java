package com.generated.app;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.wifi.ScanResult;
import android.net.wifi.WifiConfiguration;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.AlphaAnimation;
import android.view.animation.Animation;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class MainActivity extends Activity {

    private static final int PERMISSIONS_REQUEST_CODE = 1001;
    private static final String[] REQUIRED_PERMISSIONS = new String[]{
            android.Manifest.permission.ACCESS_FINE_LOCATION,
            android.Manifest.permission.ACCESS_WIFI_STATE,
            android.Manifest.permission.CHANGE_WIFI_STATE
    };
    private static final int SCAN_INTERVAL_MS = 10000; // Scan every 10 seconds

    private WifiManager wifiManager;
    private WifiListAdapter wifiListAdapter;
    private List<ScanResult> wifiList = new ArrayList<>();
    private ListView wifiListView;
    private TextView emptyView;
    private Button scanButton;
    private Handler handler = new Handler();

    private BroadcastReceiver wifiScanReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context c, Intent intent) {
            boolean success = intent.getBooleanExtra(WifiManager.EXTRA_RESULTS_UPDATED, false);
            if (success) {
                scanSuccess();
            } else {
                scanFailure();
            }
            // Schedule next scan even if failed, to retry
            handler.postDelayed(scanRunnable, SCAN_INTERVAL_MS);
        }
    };

    private Runnable scanRunnable = new Runnable() {
        @Override
        public void run() {
            startWifiScan();
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        wifiManager = (WifiManager) getApplicationContext().getSystemService(Context.WIFI_SERVICE);

        if (wifiManager == null) {
            Toast.makeText(this, R.string.no_wifi_support, Toast.LENGTH_LONG).show();
            finish(); // Exit if Wi-Fi manager is not available
            return;
        }

        wifiListView = (ListView) findViewById(R.id.wifi_list_view);
        emptyView = (TextView) findViewById(R.id.empty_view);
        scanButton = (Button) findViewById(R.id.scan_button);

        wifiListAdapter = new WifiListAdapter(this, wifiList);
        wifiListView.setAdapter(wifiListAdapter);
        wifiListView.setEmptyView(emptyView);

        scanButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                // Apply a subtle animation for button press
                Animation fadeOut = new AlphaAnimation(1.0f, 0.5f);
                fadeOut.setDuration(100);
                fadeOut.setFillAfter(true);
                v.startAnimation(fadeOut);

                v.postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        Animation fadeIn = new AlphaAnimation(0.5f, 1.0f);
                        fadeIn.setDuration(100);
                        fadeIn.setFillAfter(true);
                        v.startAnimation(fadeIn);
                        checkPermissionsAndScan();
                    }
                }, 100);
            }
        });

        wifiListView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                ScanResult selectedNetwork = (ScanResult) parent.getItemAtPosition(position);
                showPasswordDialog(selectedNetwork);
            }
        });

        // Initial permission check
        checkPermissionsAndScan();
    }

    @Override
    protected void onResume() {
        super.onResume();
        registerReceiver(wifiScanReceiver, new IntentFilter(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION));
        handler.post(scanRunnable); // Start periodic scanning
    }

    @Override
    protected void onPause() {
        super.onPause();
        unregisterReceiver(wifiScanReceiver);
        handler.removeCallbacks(scanRunnable); // Stop periodic scanning
    }

    private void checkPermissionsAndScan() {
        boolean allGranted = true;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            for (String permission : REQUIRED_PERMISSIONS) {
                if (checkSelfPermission(permission) != PackageManager.PERMISSION_GRANTED) {
                    allGranted = false;
                    break;
                }
            }

            if (!allGranted) {
                requestPermissions(REQUIRED_PERMISSIONS, PERMISSIONS_REQUEST_CODE);
                return;
            }
        }
        startWifiScan();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        if (requestCode == PERMISSIONS_REQUEST_CODE) {
            boolean allGranted = true;
            for (int result : grantResults) {
                if (result != PackageManager.PERMISSION_GRANTED) {
                    allGranted = false;
                    break;
                }
            }
            if (allGranted) {
                startWifiScan();
            } else {
                Toast.makeText(this, R.string.permissions_required_toast, Toast.LENGTH_LONG).show();
                // Optionally disable scan button or finish activity
                scanButton.setEnabled(false);
            }
        }
    }

    private void startWifiScan() {
        if (!wifiManager.isWifiEnabled()) {
            Toast.makeText(this, R.string.wifi_disabled_message, Toast.LENGTH_SHORT).show();
            // Attempt to enable Wi-Fi
            boolean enabled = wifiManager.setWifiEnabled(true);
            if (!enabled) {
                Toast.makeText(this, R.string.wifi_enable_failed, Toast.LENGTH_LONG).show();
            }
            return; // Wait for Wi-Fi to be enabled before scanning
        }

        boolean scanStarted = wifiManager.startScan();
        if (!scanStarted) {
            Toast.makeText(this, R.string.wifi_scan_failed_retry, Toast.LENGTH_SHORT).show();
            scanFailure();
        }
    }

    private void scanSuccess() {
        List<ScanResult> results = wifiManager.getScanResults();

        // Filter out duplicate SSIDs and sort by signal strength
        Set<String> uniqueSsids = new HashSet<>();
        List<ScanResult> filteredResults = new ArrayList<>();
        for (ScanResult result : results) {
            if (result.SSID != null && !result.SSID.isEmpty() && uniqueSsids.add(result.SSID)) {
                filteredResults.add(result);
            }
        }

        Collections.sort(filteredResults, new Comparator<ScanResult>() {
            @Override
            public int compare(ScanResult o1, ScanResult o2) {
                return WifiManager.compareSignalLevel(o2.level, o1.level); // Descending order
            }
        });

        wifiList.clear();
        wifiList.addAll(filteredResults);
        wifiListAdapter.notifyDataSetChanged();
    }

    private void scanFailure() {
        // Handle scan failure if necessary
        // For now, just rely on the empty view if list remains empty
    }

    private void showPasswordDialog(final ScanResult scanResult) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(String.format(getString(R.string.password_dialog_title), scanResult.SSID));

        // Set up the input
        final EditText input = new EditText(this);
        input.setLayoutDirection(View.LAYOUT_DIRECTION_RTL); // Ensure RTL for input
        input.setHint(R.string.password_hint);
        input.setSingleLine(true);
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        input.setBackgroundResource(R.drawable.rounded_input_bg); // Custom background
        input.setPadding(30, 20, 30, 20); // Add padding

        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(50, 20, 50, 20); // Padding for the dialog content
        layout.addView(input);

        builder.setView(layout);

        // Set up the buttons
        builder.setPositiveButton(R.string.connect_button, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                String password = input.getText().toString();
                connectToWifi(scanResult, password);
            }
        });
        builder.setNegativeButton(R.string.cancel_button, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                dialog.cancel();
            }
        });

        AlertDialog dialog = builder.create();
        dialog.show();

        // Customizing button styles after show()
        Button positiveButton = dialog.getButton(DialogInterface.BUTTON_POSITIVE);
        if (positiveButton != null) {
            positiveButton.setBackgroundResource(R.drawable.rounded_button_bg);
            positiveButton.setTextColor(getResources().getColor(android.R.color.white));
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
            );
            params.setMargins(10, 0, 10, 0); // Spacing between buttons
            positiveButton.setLayoutParams(params);
        }
        Button negativeButton = dialog.getButton(DialogInterface.BUTTON_NEGATIVE);
        if (negativeButton != null) {
            negativeButton.setBackgroundResource(android.R.color.transparent); // No background
            negativeButton.setTextColor(getResources().getColor(R.color.colorPrimary));
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
            );
            params.setMargins(10, 0, 10, 0);
            negativeButton.setLayoutParams(params);
        }
    }

    private void connectToWifi(ScanResult scanResult, String password) {
        Toast.makeText(this, String.format(getString(R.string.connecting_to_network), scanResult.SSID), Toast.LENGTH_SHORT).show();

        WifiConfiguration wifiConfig = new WifiConfiguration();
        wifiConfig.SSID = String.format("\"%s\"", scanResult.SSID);

        String securityType = getSecurityType(scanResult.capabilities);

        if (securityType.equals(getString(R.string.open_network))) {
            wifiConfig.allowedKeyManagement.set(WifiConfiguration.KEY_MGMT_NONE);
        } else if (securityType.equals("WEP")) {
            if (password.length() == 0) {
                Toast.makeText(this, R.string.invalid_password, Toast.LENGTH_SHORT).show();
                return;
            }
            wifiConfig.wepKeys[0] = String.format("\"%s\"", password);
            wifiConfig.wepTxKeyIndex = 0;
            wifiConfig.allowedKeyManagement.set(WifiConfiguration.KEY_MGMT_NONE);
            wifiConfig.allowedGroupCiphers.set(WifiConfiguration.GROUP_CIPHER_WEP40);
            wifiConfig.allowedGroupCiphers.set(WifiConfiguration.GROUP_CIPHER_WEP104);
            wifiConfig.allowedAuthAlgorithms.set(WifiConfiguration.AUTH_ALGORITHM_OPEN);
            wifiConfig.allowedAuthAlgorithms.set(WifiConfiguration.AUTH_ALGORITHM_SHARED);
        } else { // WPA/WPA2/WPA3 (Pre-Shared Key)
            if (password.length() < 8) { // WPA/WPA2/WPA3 minimum password length
                Toast.makeText(this, R.string.invalid_password, Toast.LENGTH_SHORT).show();
                return;
            }
            wifiConfig.preSharedKey = String.format("\"%s\"", password);
            wifiConfig.allowedKeyManagement.set(WifiConfiguration.KEY_MGMT_WPA_PSK);
            wifiConfig.allowedGroupCiphers.set(WifiConfiguration.GROUP_CIPHER_CCMP);
            wifiConfig.allowedGroupCiphers.set(WifiConfiguration.GROUP_CIPHER_TKIP);
            wifiConfig.allowedAuthAlgorithms.set(WifiConfiguration.AUTH_ALGORITHM_OPEN);
            wifiConfig.allowedPairwiseCiphers.set(WifiConfiguration.PAIRWISE_CIPHER_CCMP);
            wifiConfig.allowedPairwiseCiphers.set(WifiConfiguration.PAIRWISE_CIPHER_TKIP);
            wifiConfig.status = WifiConfiguration.Status.ENABLED;
        }

        int netId = wifiManager.addNetwork(wifiConfig);
        if (netId != -1) {
            wifiManager.disconnect();
            boolean enableNetwork = wifiManager.enableNetwork(netId, true);
            boolean reconnect = wifiManager.reconnect();

            if (enableNetwork && reconnect) {
                // Connection status will be checked via BroadcastReceiver if needed, or by polling
                // For this example, we assume it's successful shortly after
                // A more robust app would listen for WifiManager.NETWORK_STATE_CHANGED_ACTION
                Toast.makeText(this, String.format(getString(R.string.connection_success), scanResult.SSID), Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(this, String.format(getString(R.string.connection_failed), scanResult.SSID), Toast.LENGTH_SHORT).show();
            }
        } else {
            Toast.makeText(this, String.format(getString(R.string.connection_failed), scanResult.SSID), Toast.LENGTH_SHORT).show();
        }
    }

    private String getSecurityType(String capabilities) {
        if (capabilities.contains("WPA3")) {
            return "WPA3";
        } else if (capabilities.contains("WPA2")) {
            return "WPA2";
        } else if (capabilities.contains("WPA")) {
            return "WPA";
        } else if (capabilities.contains("WEP")) {
            return "WEP";
        } else if (capabilities.contains("EAP")) { // EAP for enterprise networks
            return "EAP";
        }
        return getString(R.string.open_network); // Assume open if no specific security mentioned
    }

    // Custom Adapter for the Wi-Fi list
    private class WifiListAdapter extends BaseAdapter {
        private Context context;
        private List<ScanResult> data;

        public WifiListAdapter(Context context, List<ScanResult> data) {
            this.context = context;
            this.data = data;
        }

        @Override
        public int getCount() {
            return data.size();
        }

        @Override
        public Object getItem(int position) {
            return data.get(position);
        }

        @Override
        public long getItemId(int position) {
            return position;
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            if (convertView == null) {
                convertView = getLayoutInflater().inflate(R.layout.list_item_wifi, parent, false);
            }

            TextView ssidTextView = (TextView) convertView.findViewById(R.id.wifi_ssid);
            TextView securityTypeTextView = (TextView) convertView.findViewById(R.id.wifi_security_type);
            TextView signalStrengthTextView = (TextView) convertView.findViewById(R.id.wifi_signal_strength_text);
            ImageView signalStrengthIcon = (ImageView) convertView.findViewById(R.id.wifi_signal_strength_icon);
            ImageView securityIcon = (ImageView) convertView.findViewById(R.id.wifi_security_icon);

            ScanResult scanResult = data.get(position);

            ssidTextView.setText(scanResult.SSID);

            String security = getSecurityType(scanResult.capabilities);
            securityTypeTextView.setText(String.format(getString(R.string.network_security), security));

            // Calculate signal level (0-4)
            int level = WifiManager.calculateSignalLevel(scanResult.level, 5); // 0-4 levels
            int signalDrawableId;
            switch (level) {
                case 0:
                    signalDrawableId = R.drawable.ic_wifi_signal_0;
                    break;
                case 1:
                    signalDrawableId = R.drawable.ic_wifi_signal_1;
                    break;
                case 2:
                    signalDrawableId = R.drawable.ic_wifi_signal_2;
                    break;
                case 3:
                    signalDrawableId = R.drawable.ic_wifi_signal_3;
                    break;
                case 4:
                default:
                    signalDrawableId = R.drawable.ic_wifi_signal_4;
                    break;
            }
            signalStrengthIcon.setImageResource(signalDrawableId);

            int signalPercentage = (int) (WifiManager.calculateSignalLevel(scanResult.level, 100) / 100.0 * 100);
            signalStrengthTextView.setText(String.format(getString(R.string.signal_strength), signalPercentage));

            if (security.equals(getString(R.string.open_network))) {
                securityIcon.setVisibility(View.GONE);
            } else {
                securityIcon.setVisibility(View.VISIBLE);
                securityIcon.setImageResource(R.drawable.ic_wifi_lock);
            }

            // Optional: Indicate currently connected network
            WifiInfo wifiInfo = wifiManager.getConnectionInfo();
            if (wifiInfo != null && wifiInfo.getSSID() != null && wifiInfo.getSSID().equals("\"" + scanResult.SSID + "\"")) {
                ssidTextView.setTextColor(getResources().getColor(R.color.greenConnected));
                // Add a subtle animation to indicate active connection
                Animation pulse = new AlphaAnimation(1.0f, 0.7f);
                pulse.setDuration(1000);
                pulse.setRepeatMode(Animation.REVERSE);
                pulse.setRepeatCount(Animation.INFINITE);
                convertView.startAnimation(pulse);
            } else {
                ssidTextView.setTextColor(getResources().getColor(R.color.textColorPrimary));
                convertView.clearAnimation();
            }

            return convertView;
        }
    }
}
