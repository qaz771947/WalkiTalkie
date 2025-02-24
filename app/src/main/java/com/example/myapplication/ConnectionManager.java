package com.example.myapplication;

import android.content.Context;
import android.net.wifi.p2p.WifiP2pManager;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.widget.Toast;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class ConnectionManager {

    private static final int SOCKET_PORT = 8888;

    private WifiP2pManager wifiP2pManager;
    private WifiP2pManager.Channel channel;
    private static ConnectionManager instance;
    private final Map<Socket, DataOutputStream> socketOutputMap = new ConcurrentHashMap<>();
    private final ExecutorService executorService = Executors.newFixedThreadPool(10);

    // 用於將接收到的訊息通知給聊天畫面
    public interface MessageCallback {
        void onTextMessageReceived(String message);
        void onAudioMessageReceived(File audioFile);
    }
    private MessageCallback messageCallback;

    // 新增 Application Context 變數
    private Context appContext;

    private ConnectionManager() { }

    public static synchronized ConnectionManager getInstance() {
        if (instance == null) {
            instance = new ConnectionManager();
        }
        return instance;
    }

    // 請在使用前先呼叫此方法設定 context (例如在 MainActivity.onCreate 中)
    public void init(Context context) {
        this.appContext = context.getApplicationContext();
    }

    // 設定 Wi-Fi P2P 相關變數（例如在 MainActivity 中初始化時呼叫）
    public void setWifiP2pManager(WifiP2pManager manager, WifiP2pManager.Channel channel) {
        this.wifiP2pManager = manager;
        this.channel = channel;
    }

    public void setMessageCallback(MessageCallback callback) {
        this.messageCallback = callback;
    }

    // 加入新連線的 Socket，並啟動接收處理
    public void addSocket(Socket socket) {
        try {
            DataOutputStream dos = new DataOutputStream(socket.getOutputStream());
            socketOutputMap.put(socket, dos);
            Log.d("ConnectionManager", "Socket added: " + socket.getInetAddress());
            executorService.submit(() -> handleClientSocket(socket));
        } catch (IOException e) {
            Log.e("ConnectionManager", "addSocket failed", e);
        }
    }

    // 發送文字訊息給所有連線
    public void sendMessageToAll(String message) {
        executorService.submit(() -> {
            for (Map.Entry<Socket, DataOutputStream> entry : socketOutputMap.entrySet()) {
                DataOutputStream dos = entry.getValue();
                try {
                    synchronized (dos) {
                        dos.writeUTF("TEXT");
                        dos.writeUTF(message);
                        dos.flush();
                    }
                } catch (IOException e) {
                    Log.e("ConnectionManager", "Failed to send message", e);
                }
            }
        });
    }

    // 發送音檔給所有連線
    public void sendAudioFile(File audioFile) {
        executorService.submit(() -> {
            for (Map.Entry<Socket, DataOutputStream> entry : socketOutputMap.entrySet()) {
                DataOutputStream dos = entry.getValue();
                try {
                    synchronized (dos) {
                        dos.writeUTF("AUDIO_FILE");
                        long fileLength = audioFile.length();
                        dos.writeLong(fileLength);
                        try (FileInputStream fis = new FileInputStream(audioFile)) {
                            byte[] buffer = new byte[4096];
                            int bytesRead;
                            while ((bytesRead = fis.read(buffer)) != -1) {
                                dos.write(buffer, 0, bytesRead);
                            }
                        }
                        dos.flush();
                    }
                } catch (IOException e) {
                    Log.e("ConnectionManager", "Failed to send audio file", e);
                }
            }
        });
    }

    // 處理接收端的資料
    private void handleClientSocket(Socket socket) {
        try (DataInputStream dis = new DataInputStream(socket.getInputStream())) {
            socket.setSoTimeout(600000);
            while (!socket.isClosed() && socket.isConnected()) {
                try {
                    String header = dis.readUTF();
                    if ("AUDIO_FILE".equals(header)) {
                        // 呼叫修改後的方法接收並處理音檔
                        receiveAndSaveAudio(dis);
                    } else if ("TEXT".equals(header)) {
                        String message = dis.readUTF();
                        if (messageCallback != null) {
                            messageCallback.onTextMessageReceived(message);
                        }
                    } else {
                        Log.w("ConnectionManager", "Unknown header: " + header);
                    }
                } catch (IOException e) {
                    Log.e("ConnectionManager", "Exception while reading from socket", e);
                    break;
                }
            }
        } catch (IOException e) {
            Log.e("ConnectionManager", "Socket input stream error", e);
        } finally {
            // 使用新方法關閉 socket 並斷開群組連線
            closeSocketAndRemove(socket);
        }
    }

    // 修改後：使用 appContext.getFilesDir() 儲存接收到的音檔，
    // 並透過 Handler 切回主執行緒呼叫 messageCallback.onAudioMessageReceived
    private void receiveAndSaveAudio(DataInputStream dis) throws IOException {
        long fileLength = dis.readLong();
        if (appContext == null) {
            throw new IllegalStateException("ConnectionManager 未初始化 context，請先呼叫 init(context) 方法");
        }
        File audioFile = new File(appContext.getFilesDir(), "received_audio.3gp");
        try (FileOutputStream fos = new FileOutputStream(audioFile)) {
            byte[] buffer = new byte[4096];
            int bytesRead;
            long totalBytesRead = 0;
            while (totalBytesRead < fileLength &&
                    (bytesRead = dis.read(buffer, 0, (int) Math.min(buffer.length, fileLength - totalBytesRead))) != -1) {
                fos.write(buffer, 0, bytesRead);
                totalBytesRead += bytesRead;
            }
        }
        Log.d("ConnectionManager", "Received audio file: " + audioFile.getAbsolutePath());
        // 使用 Handler 切回主執行緒
        new Handler(Looper.getMainLooper()).post(() -> {
            if (messageCallback != null) {
                messageCallback.onAudioMessageReceived(audioFile);
            }
        });
    }

    // 關閉單一 socket 並移除，並嘗試斷開 Wi-Fi Direct 群組連線
    private void closeSocketAndRemove(Socket socket) {
        try {
            Log.d("WiFiP2P_DEBUG", "Closing socket from " + socket.getInetAddress());
            socket.close();
        } catch (IOException e) {
            Log.e("WiFiP2P_DEBUG", "closeSocketAndRemove: Failed to close socket", e);
        }
        socketOutputMap.remove(socket);

        // 斷開 Wi-Fi Direct 群組連線
        if (wifiP2pManager != null && channel != null) {
            wifiP2pManager.removeGroup(channel, new WifiP2pManager.ActionListener() {
                @Override
                public void onSuccess() {
                    Log.d("WiFiP2P_DEBUG", "removeGroup: Successfully removed Wi-Fi Direct group.");
                    new Handler(Looper.getMainLooper()).post(() ->
                            Toast.makeText(appContext, "Disconnected from Wi-Fi Direct group", Toast.LENGTH_SHORT).show()
                    );
                }
                @Override
                public void onFailure(int reason) {
                    Log.e("WiFiP2P_DEBUG", "removeGroup: Failed, reason: " + reason);
                }
            });
        }
    }

    // 啟動伺服器端：群組擁有者呼叫
    public void startServer() {
        executorService.submit(() -> {
            try (ServerSocket serverSocket = new ServerSocket(SOCKET_PORT)) {
                Log.d("ConnectionManager", "Server started, waiting for connections...");
                while (true) {
                    Socket clientSocket = serverSocket.accept();
                    Log.d("ConnectionManager", "Connection received from " + clientSocket.getInetAddress());
                    addSocket(clientSocket);
                }
            } catch (IOException e) {
                Log.e("ConnectionManager", "Server error", e);
            }
        });
    }

    // 啟動客戶端：接收端呼叫
    public void startClient(String hostAddress) {
        executorService.submit(() -> {
            try {
                Log.d("ConnectionManager", "Client connecting to " + hostAddress);
                Socket socket = new Socket(hostAddress, SOCKET_PORT);
                addSocket(socket);
            } catch (IOException e) {
                Log.e("ConnectionManager", "Client connection failed", e);
            }
        });
    }
}
