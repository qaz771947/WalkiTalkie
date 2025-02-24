package com.example.myapplication;

import android.Manifest;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.net.wifi.WpsInfo;
import android.net.wifi.p2p.WifiP2pConfig;
import android.net.wifi.p2p.WifiP2pDevice;
import android.net.wifi.p2p.WifiP2pInfo;
import android.net.wifi.p2p.WifiP2pManager;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.widget.ImageButton;
import android.widget.Switch;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public class MainActivity extends AppCompatActivity implements DeviceAdapter.OnDeviceClickListener {

    private static final int PERMISSIONS_REQUEST_CODE = 100;

    // Wi-Fi P2P 相關變數
    private WifiP2pManager wifiP2pManager;
    private WifiP2pManager.Channel channel;
    private BroadcastReceiver broadcastReceiver;
    private IntentFilter intentFilter;

    // UI 元件
    private RecyclerView recyclerView;
    private DeviceAdapter adapter;
    private Switch roleSwitch;
    private ImageButton chat_room_btn;

    // 角色設定：發送邀請端預設為 false（代表 Client），若為 true 則為 Server（群主）
    private boolean isInvitationSender = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.main_layout);

        // 檢查必要權限
        checkAndRequestPermissions();

        // 初始化 Wi-Fi P2P 相關
        initWifiP2p();

        // 初始化 UI 元件
        initUI();



    }

    @Override
    protected void onResume() {
        super.onResume();
        registerReceiver(broadcastReceiver, intentFilter);
        Log.d("WiFiP2P_DEBUG", "BroadcastReceiver registered");
    }

    @Override
    protected void onPause() {
        super.onPause();
        //unregisterReceiver(broadcastReceiver);
        Log.d("WiFiP2P_DEBUG", "BroadcastReceiver unregistered");
    }

    private void initWifiP2p() {
        wifiP2pManager = (WifiP2pManager) getSystemService(Context.WIFI_P2P_SERVICE);
        if (wifiP2pManager != null) {
            channel = wifiP2pManager.initialize(MainActivity.this, getMainLooper(), null);
            ConnectionManager.getInstance().init(this);
            ConnectionManager.getInstance().setWifiP2pManager(wifiP2pManager, channel);
        }
        // 初始化廣播接收器並檢查 Wi-Fi 狀態
        broadcastReceiver = new WifiP2pBroadcastReceiver(wifiP2pManager, channel, MainActivity.this);
        ((WifiP2pBroadcastReceiver) broadcastReceiver).checkWifiIsOnable(MainActivity.this);
        intentFilter = new IntentFilter();
        intentFilter.addAction(WifiP2pManager.WIFI_P2P_STATE_CHANGED_ACTION);
        intentFilter.addAction(WifiP2pManager.WIFI_P2P_PEERS_CHANGED_ACTION);
        intentFilter.addAction(WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION);
        intentFilter.addAction(WifiP2pManager.WIFI_P2P_THIS_DEVICE_CHANGED_ACTION);
    }

    private void initUI() {
        recyclerView = findViewById(R.id.device_list);
        adapter = new DeviceAdapter(this);
        recyclerView.setAdapter(adapter);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));

        roleSwitch = findViewById(R.id.role_switch);
        isInvitationSender = roleSwitch.isChecked();
        roleSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
            isInvitationSender = isChecked;
            String role = isChecked ? "Server (發送邀請端)" : "Client (接收邀請端)";
            Toast.makeText(MainActivity.this, "已設定為 " + role, Toast.LENGTH_SHORT).show();
        });

        // 掃描按鈕點擊事件
        findViewById(R.id.scan_btn).setOnClickListener(v -> {
            Toast.makeText(MainActivity.this, "Starting scan...", Toast.LENGTH_SHORT).show();
            Log.d("WiFiP2P_DEBUG", "Scan button clicked.");
            scanDevice();
        });

        //聊天室按鈕
        findViewById(R.id.chat_room_btn).setOnClickListener(v -> {
            startActivity(new Intent(MainActivity.this, ReceiveViewActivity.class));
        });
    }

    private void checkAndRequestPermissions() {
        List<String> permissionsNeeded = new ArrayList<>();
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {
            permissionsNeeded.add(Manifest.permission.ACCESS_FINE_LOCATION);
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.NEARBY_WIFI_DEVICES)
                    != PackageManager.PERMISSION_GRANTED) {
                permissionsNeeded.add(Manifest.permission.NEARBY_WIFI_DEVICES);
            }
        }
        if (!permissionsNeeded.isEmpty()) {
            ActivityCompat.requestPermissions(this,
                    permissionsNeeded.toArray(new String[0]),
                    PERMISSIONS_REQUEST_CODE);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == PERMISSIONS_REQUEST_CODE) {
            boolean allGranted = true;
            for (int result : grantResults) {
                if (result != PackageManager.PERMISSION_GRANTED) {
                    allGranted = false;
                    break;
                }
            }
            String msg = allGranted ? "All required permissions granted." : "Some required permissions were denied.";
            Toast.makeText(MainActivity.this, msg, Toast.LENGTH_SHORT).show();
        }
    }

    private void scanDevice() {
        if (wifiP2pManager != null && channel != null) {
            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED ||
                    ActivityCompat.checkSelfPermission(this, Manifest.permission.NEARBY_WIFI_DEVICES) != PackageManager.PERMISSION_GRANTED) {
                return;
            }
            wifiP2pManager.discoverPeers(channel, new WifiP2pManager.ActionListener() {
                @Override
                public void onSuccess() {
                    runOnUiThread(() -> {
                        Toast.makeText(MainActivity.this, "Scanning for devices...", Toast.LENGTH_SHORT).show();
                        Log.d("WiFiP2P_DEBUG", "Device discovery started successfully.");
                    });
                }

                @Override
                public void onFailure(int reason) {
                    String errorMsg;
                    switch (reason) {
                        case WifiP2pManager.P2P_UNSUPPORTED:
                            errorMsg = "Wi-Fi P2P not supported.";
                            break;
                        case WifiP2pManager.BUSY:
                            errorMsg = "System is busy. Try again later.";
                            break;
                        case WifiP2pManager.ERROR:
                        default:
                            errorMsg = "An error occurred. Please try again.";
                            break;
                    }
                    runOnUiThread(() -> {
                        Toast.makeText(MainActivity.this, "Failed to start scanning: " + errorMsg, Toast.LENGTH_LONG).show();
                        Log.e("WiFiP2P_DEBUG", "Device discovery failed: " + errorMsg);
                    });
                }
            });
        } else {
            Toast.makeText(this, "Wi-Fi P2P is not initialized.", Toast.LENGTH_SHORT).show();
            Log.e("WiFiP2P_DEBUG", "Wi-Fi P2P is not initialized.");
        }
    }

    public void updateDeviceList(Collection<WifiP2pDevice> deviceList) {
        if (adapter != null) {
            adapter.updateDevices(deviceList);
        } else {
            Log.e("WiFiP2P_DEBUG", "Adapter is null. Cannot update device list.");
        }
    }

    private void connectDevice(WifiP2pDevice device) {
        if (wifiP2pManager == null || channel == null) {
            Log.e("WiFiP2P_DEBUG", "Wi-Fi P2P is not initialized.");
            Toast.makeText(MainActivity.this, "Wi-Fi P2P is not initialized.", Toast.LENGTH_SHORT).show();
            return;
        }
        WifiP2pConfig config = new WifiP2pConfig();
        config.deviceAddress = device.deviceAddress;
        config.wps.setup = WpsInfo.PBC;
        config.groupOwnerIntent = isInvitationSender ? 15 : 0;
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED ||
                (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                        ActivityCompat.checkSelfPermission(this, Manifest.permission.NEARBY_WIFI_DEVICES) != PackageManager.PERMISSION_GRANTED)) {
            Toast.makeText(MainActivity.this, "Required permissions are missing for connecting.", Toast.LENGTH_SHORT).show();
            Log.e("WiFiP2P_DEBUG", "Missing permissions for connecting.");
            return;
        }
        wifiP2pManager.connect(channel, config, new WifiP2pManager.ActionListener() {
            @Override
            public void onSuccess() {
                Log.d("WiFiP2P_DEBUG", "Connection initiated with device: " + device.deviceName);
                Toast.makeText(MainActivity.this, "Connecting to " + device.deviceName, Toast.LENGTH_SHORT).show();
                wifiP2pManager.requestConnectionInfo(channel, info -> handleConnectionInfo(info));
            }

            @Override
            public void onFailure(int reason) {
                String errorMsg;
                switch (reason) {
                    case WifiP2pManager.P2P_UNSUPPORTED:
                        errorMsg = "Wi-Fi P2P not supported.";
                        break;
                    case WifiP2pManager.BUSY:
                        errorMsg = "System is busy. Try again later.";
                        break;
                    case WifiP2pManager.ERROR:
                    default:
                        errorMsg = "Connection failed. Please try again.";
                        break;
                }
                Log.e("WiFiP2P_DEBUG", "Connection failed: " + errorMsg);
                Toast.makeText(MainActivity.this, "Connection failed: " + errorMsg, Toast.LENGTH_SHORT).show();
            }
        });
    }

    @Override
    public void onDeviceClicked(WifiP2pDevice device) {
        Log.d("WiFiP2P_DEBUG", "Device clicked: " + device.deviceName);
        connectDevice(device);
    }

    // 處理連線資訊，依角色啟動 server 或 client，並跳轉至聊天畫面
    public void handleConnectionInfo(WifiP2pInfo info) {
        if (info.groupFormed) {
            if (info.isGroupOwner) {
                Log.d("WiFiP2P_DEBUG", "This device is Group Owner. Starting server.");
                ConnectionManager.getInstance().startServer();
            } else {
                String hostAddress = info.groupOwnerAddress.getHostAddress();
                Log.d("WiFiP2P_DEBUG", "This device is Client. Connecting to " + hostAddress);
                ConnectionManager.getInstance().startClient(hostAddress);
            }

        }
    }
}
