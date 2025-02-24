package com.example.myapplication;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import android.media.MediaPlayer;
import android.media.MediaRecorder;
import android.os.Bundle;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageButton;

import java.io.File;
import java.util.ArrayList;

public class ReceiveViewActivity extends AppCompatActivity implements ConnectionManager.MessageCallback {

    private RecyclerView messageRecyclerView;
    private MessageAdapter messageAdapter;
    private EditText msgInput;
    private ImageButton sendBtn, recordBtn;

    private MediaRecorder mediaRecorder;
    private File audioFile;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.recive_view);

        // 初始化 ConnectionManager，確保後續使用 getFilesDir() 正確運作
        ConnectionManager.getInstance().init(this);

        messageRecyclerView = findViewById(R.id.receive_message_list);
        messageRecyclerView.setLayoutManager(new LinearLayoutManager(this));
        messageAdapter = new MessageAdapter(new ArrayList<>());
        messageRecyclerView.setAdapter(messageAdapter);

        msgInput = findViewById(R.id.msg_input);
        sendBtn = findViewById(R.id.send_btn);
        recordBtn = findViewById(R.id.record_btn);

        sendBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                String message = msgInput.getText().toString().trim();
                if (!message.isEmpty()) {
                    // 呼叫 ConnectionManager 發送文字訊息
                    ConnectionManager.getInstance().sendMessageToAll(message);
                    messageAdapter.addMessage("Me: " + message);
                    msgInput.setText("");
                }
            }
        });

        // 錄音按鈕：按下開始錄音，放開後停止錄音並發送音檔
        recordBtn.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                switch (event.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        startRecording();
                        return true;
                    case MotionEvent.ACTION_UP:
                        stopRecordingAndSend();
                        return true;
                }
                return false;
            }
        });

        // 註冊訊息回呼，接收來自 ConnectionManager 的訊息
        ConnectionManager.getInstance().setMessageCallback(this);
    }

    @Override
    public void onTextMessageReceived(String message) {
        runOnUiThread(() -> messageAdapter.addMessage("Peer: " + message));
    }

    @Override
    public void onAudioMessageReceived(File audioFile) {
        runOnUiThread(() -> {
            messageAdapter.addMessage("Received an audio message");
            playReceivedAudio(audioFile);
        });
    }

    private void startRecording() {
        try {
            // 使用內部儲存區，建立錄音檔案
            audioFile = new File(getFilesDir(), "audio_message.3gp");
            mediaRecorder = new MediaRecorder();
            mediaRecorder.setAudioSource(MediaRecorder.AudioSource.MIC);
            mediaRecorder.setOutputFormat(MediaRecorder.OutputFormat.THREE_GPP);
            mediaRecorder.setOutputFile(audioFile.getAbsolutePath());
            mediaRecorder.setAudioEncoder(MediaRecorder.AudioEncoder.AMR_NB);
            mediaRecorder.prepare();
            mediaRecorder.start();
        } catch (Exception e) {
            Log.e("ReceiveViewActivity", "startRecording failed", e);
            e.printStackTrace();
        }
    }

    private void stopRecordingAndSend() {
        try {
            if (mediaRecorder != null) {
                mediaRecorder.stop();
                mediaRecorder.release();
                mediaRecorder = null;
                // 透過 ConnectionManager 傳送音檔
                ConnectionManager.getInstance().sendAudioFile(audioFile);
                messageAdapter.addMessage("Me: Sent an audio message");
            }
        } catch (Exception e) {
            Log.e("ReceiveViewActivity", "stopRecordingAndSend failed", e);
            e.printStackTrace();
        }
    }

    private void playReceivedAudio(File audioFile) {
        MediaPlayer mediaPlayer = new MediaPlayer();
        try {
            mediaPlayer.setDataSource(audioFile.getAbsolutePath());
            mediaPlayer.prepare();
            mediaPlayer.start();
        } catch (Exception e) {
            Log.e("ReceiveViewActivity", "playReceivedAudio failed", e);
            e.printStackTrace();
        }
    }
}
