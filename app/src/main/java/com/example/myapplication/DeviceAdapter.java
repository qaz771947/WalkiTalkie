package com.example.myapplication;

import android.graphics.Color;
import android.net.wifi.p2p.WifiP2pDevice;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public class DeviceAdapter extends RecyclerView.Adapter<DeviceAdapter.DeviceViewHolder> {

    public interface OnDeviceClickListener {
        void onDeviceClicked(WifiP2pDevice device);
    }

    private final List<WifiP2pDevice> devices;
    private final OnDeviceClickListener listener;

    public DeviceAdapter(OnDeviceClickListener listener) {
        this.devices = new ArrayList<>();
        this.listener = listener;
    }

    public void updateDevices(Collection<WifiP2pDevice> newDevices) {
        devices.clear();
        devices.addAll(newDevices);
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public DeviceViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        // 載入自定義佈局檔 device_item.xml
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.device_item, parent, false);
        return new DeviceViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull DeviceViewHolder holder, int position) {
        final WifiP2pDevice device = devices.get(position);
        holder.deviceName.setText(device.deviceName);
        String statusText = getStatusString(device.status);
        holder.connectionStatus.setText(statusText);
        holder.connectionStatus.setTextColor(getStatusColor(device.status));

        holder.itemView.setOnClickListener(v -> {
            if (listener != null) {
                listener.onDeviceClicked(device);
            }
        });
    }

    @Override
    public int getItemCount() {
        return devices.size();
    }

    private String getStatusString(int status) {
        switch (status) {
            case WifiP2pDevice.AVAILABLE:
                return "Available";
            case WifiP2pDevice.INVITED:
                return "Invited";
            case WifiP2pDevice.CONNECTED:
                return "Connected";
            case WifiP2pDevice.FAILED:
                return "Failed";
            case WifiP2pDevice.UNAVAILABLE:
                return "Unavailable";
            default:
                return "Unknown";
        }
    }

    private int getStatusColor(int status) {
        switch (status) {
            case WifiP2pDevice.AVAILABLE:
                return Color.GREEN;       // Available -> 綠色
            case WifiP2pDevice.INVITED:
                return Color.GRAY;        // Invited -> 灰色
            case WifiP2pDevice.CONNECTED:
                return Color.BLUE;        // Connected -> 藍色
            case WifiP2pDevice.FAILED:
                return Color.RED;         // Failed -> 紅色
            case WifiP2pDevice.UNAVAILABLE:
                return Color.YELLOW;      // Unavailable -> 黃色
            default:
                // Unknown -> 紫色 (使用自訂的紫色值)
                return Color.parseColor("#800080");
        }
    }

    static class DeviceViewHolder extends RecyclerView.ViewHolder {
        TextView deviceName;
        TextView connectionStatus;  // 顯示連接狀態

        public DeviceViewHolder(@NonNull View itemView) {
            super(itemView);
            deviceName = itemView.findViewById(R.id.deviceName);
            connectionStatus = itemView.findViewById(R.id.connectionStatus);
        }
    }
}
