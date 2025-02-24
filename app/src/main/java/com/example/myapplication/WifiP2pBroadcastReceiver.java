package com.example.myapplication;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.net.NetworkInfo;
import android.net.wifi.WifiManager;
import android.net.wifi.p2p.WifiP2pManager;
import android.os.Build;
import android.util.Log;
import android.widget.Toast;

import androidx.core.app.ActivityCompat;

public class WifiP2pBroadcastReceiver extends BroadcastReceiver {

    private static final String TAG = "WifiP2pBroadcastReceiver";

    private final WifiP2pManager manager;
    private final WifiP2pManager.Channel channel;
    private final MainActivity activity;

    /**
     * 建構子，傳入管理器、Channel 與 Activity 以供後續操作使用
     */
    public WifiP2pBroadcastReceiver(WifiP2pManager manager, WifiP2pManager.Channel channel, MainActivity activity) {
        this.manager = manager;
        this.channel = channel;
        this.activity = activity;
    }

    /**
     * 檢查 Wi-Fi 是否開啟，如果未啟用則導向 Wi-Fi 設定頁面
     *
     * @param context 用於啟動設定頁面的 Context
     */
    public void checkWifiIsOnable(Context context) {
        WifiManager wifiManager = (WifiManager) context.getSystemService(Context.WIFI_SERVICE);
        if (wifiManager != null && !wifiManager.isWifiEnabled()) {
            Log.d(TAG, "Wi-Fi is disabled. Redirecting to Wi-Fi settings.");
            Intent intent = new Intent(android.provider.Settings.ACTION_WIFI_SETTINGS);
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(intent);
        } else {
            Log.d(TAG, "Wi-Fi is enabled.");
        }
    }

    /**
     * 根據不同的 action 分發處理邏輯
     */
    @Override
    public void onReceive(Context context, Intent intent) {
        String action = intent.getAction();

        if (WifiP2pManager.WIFI_P2P_STATE_CHANGED_ACTION.equals(action)) {
            handleP2pStateChanged(intent);
        } else if (WifiP2pManager.WIFI_P2P_PEERS_CHANGED_ACTION.equals(action)) {
            handlePeersChanged(context);
        } else if (WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION.equals(action)) {
            handleConnectionChanged(intent);
        }
    }

    /**
     * 處理 Wi-Fi P2P 狀態變化事件
     *
     * @param intent 傳入的 Intent 包含狀態資訊
     */
    private void handleP2pStateChanged(Intent intent) {
        int state = intent.getIntExtra(WifiP2pManager.EXTRA_WIFI_STATE, -1);
        if (state == WifiP2pManager.WIFI_P2P_STATE_ENABLED) {
            Log.d(TAG, "Wi-Fi P2P is enabled.");
        } else {
            Log.d(TAG, "Wi-Fi P2P is disabled.");
        }
    }

    /**
     * 處理可用設備列表變化事件
     *
     * @param context 用於權限檢查
     */
    private void handlePeersChanged(Context context) {
        if (manager == null) {
            return;
        }

        // 檢查必須權限是否已授予
        if (ActivityCompat.checkSelfPermission(context, android.Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED ||
                (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                        ActivityCompat.checkSelfPermission(context, android.Manifest.permission.NEARBY_WIFI_DEVICES)
                                != PackageManager.PERMISSION_GRANTED)) {
            // 權限不足，無法請求設備列表
            return;
        }

        // 請求設備列表
        manager.requestPeers(channel, peerList -> {
            Log.d(TAG, "Peers found: " + peerList.getDeviceList());
            // 回到主線程更新設備列表
            activity.runOnUiThread(() -> activity.updateDeviceList(peerList.getDeviceList()));
        });
    }

    /**
     * 處理連線狀態改變事件
     *
     * 當連線成功後，僅在裝置為 Client 時建立 ClientSocket 連線，
     * 群主的 ServerSocket 由 MainActivity.handleConnectionInfo 負責啟動，避免重複建立。
     *
     * @param intent 傳入的 Intent 包含連線資訊
     */
    private void handleConnectionChanged(Intent intent) {
        if (manager == null) {
            return;
        }

        // 從 Intent 中取得 NetworkInfo
        NetworkInfo networkInfo = intent.getParcelableExtra(WifiP2pManager.EXTRA_NETWORK_INFO);
        if (networkInfo != null && networkInfo.isConnected()) {
            Log.d(TAG, "Connected to a device.");
            Toast.makeText(activity, "Connected to a device.", Toast.LENGTH_SHORT).show();
            // 無論裝置角色如何，都請求連線資訊，由 MainActivity 處理
            manager.requestConnectionInfo(channel, info -> activity.handleConnectionInfo(info));
        } else {
            Log.d(TAG, "Disconnected from device.");
            Toast.makeText(activity, "Disconnected from device.", Toast.LENGTH_SHORT).show();
        }
    }


}
