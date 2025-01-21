package edu.skku.cs.visualvroom;

import android.Manifest;
import android.app.Service;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.os.IBinder;
import android.util.Log;
import androidx.core.content.ContextCompat;

import org.java_websocket.client.WebSocketClient;
import org.java_websocket.handshake.ServerHandshake;

import java.net.URI;
import java.nio.ByteBuffer;
import java.util.concurrent.atomic.AtomicBoolean;

public class AudioRecordingService extends Service {
    private static final String TAG = "AudioRecordingService";
    private static final int SAMPLE_RATE = 16000;
    private static final int CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_STEREO;
    private static final int AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT;
    private static final int BUFFER_SIZE = AudioRecord.getMinBufferSize(
            SAMPLE_RATE, CHANNEL_CONFIG, AUDIO_FORMAT) * 2;

    private AudioRecord[] audioRecorders;
    private AtomicBoolean isRecording;
    private WebSocketClient webSocket;
    private Thread recordingThread;

    @Override
    public void onCreate() {
        super.onCreate();
        isRecording = new AtomicBoolean(false);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && "START_RECORDING".equals(intent.getAction())) {
            if (checkPermission()) {
                initializeWebSocket();
                initializeAudioRecorders();
                startRecording();
            } else {
                Log.e(TAG, "Recording permission not granted");
                stopSelf();
            }
        } else if (intent != null && "STOP_RECORDING".equals(intent.getAction())) {
            stopRecording();
            stopSelf();
        }
        return START_NOT_STICKY;
    }

    private boolean checkPermission() {
        return ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
                == PackageManager.PERMISSION_GRANTED;
    }

    private void initializeWebSocket() {
        try {
            URI serverURI = new URI("ws://10.5.31.87:8080/audio");
            webSocket = new WebSocketClient(serverURI) {
                @Override
                public void onOpen(ServerHandshake handshake) {
                    Log.d(TAG, "WebSocket Connected");
                }

                @Override
                public void onMessage(String message) {
                    // Handle inference results from server
                    Intent intent = new Intent("AUDIO_INFERENCE_RESULT");
                    intent.putExtra("result", message);
                    sendBroadcast(intent);
                }

                @Override
                public void onClose(int code, String reason, boolean remote) {
                    Log.d(TAG, "WebSocket Closed: " + reason);
                }

                @Override
                public void onError(Exception ex) {
                    Log.e(TAG, "WebSocket Error: " + ex.getMessage());
                }
            };
            webSocket.connect();
        } catch (Exception e) {
            Log.e(TAG, "WebSocket Init Error: " + e.getMessage());
        }
    }

    private void initializeAudioRecorders() {
        try {
            audioRecorders = new AudioRecord[3]; // Left, Right, Both channels

            // Initialize recorders with different channel configurations
            audioRecorders[0] = createAudioRecorder(AudioFormat.CHANNEL_IN_LEFT);
            audioRecorders[1] = createAudioRecorder(AudioFormat.CHANNEL_IN_RIGHT);
            audioRecorders[2] = createAudioRecorder(AudioFormat.CHANNEL_IN_STEREO);

            // Verify initialization
            for (int i = 0; i < audioRecorders.length; i++) {
                if (audioRecorders[i].getState() != AudioRecord.STATE_INITIALIZED) {
                    throw new IllegalStateException("Failed to initialize AudioRecord " + i);
                }
            }
        } catch (SecurityException e) {
            Log.e(TAG, "Security Exception initializing AudioRecorders: " + e.getMessage());
            throw e;
        } catch (Exception e) {
            Log.e(TAG, "Error initializing AudioRecorders: " + e.getMessage());
            throw e;
        }
    }

    private AudioRecord createAudioRecorder(int channelConfig) {
        try {
            return new AudioRecord.Builder()
                    .setAudioSource(android.media.MediaRecorder.AudioSource.DEFAULT)
                    .setAudioFormat(new AudioFormat.Builder()
                            .setEncoding(AUDIO_FORMAT)
                            .setSampleRate(SAMPLE_RATE)
                            .setChannelMask(channelConfig)
                            .build())
                    .setBufferSizeInBytes(BUFFER_SIZE)
                    .build();
        } catch (SecurityException e) {
            Log.e(TAG, "Security Exception creating AudioRecord: " + e.getMessage());
            throw e;
        }
    }

    private void startRecording() {
        if (isRecording.get()) return;

        isRecording.set(true);
        recordingThread = new Thread(() -> {
            byte[][] buffers = new byte[3][BUFFER_SIZE];

            try {
                for (AudioRecord recorder : audioRecorders) {
                    recorder.startRecording();
                }

                while (isRecording.get()) {
                    for (int i = 0; i < audioRecorders.length; i++) {
                        int read = audioRecorders[i].read(buffers[i], 0, BUFFER_SIZE);
                        if (read > 0 && webSocket != null && webSocket.isOpen()) {
                            ByteBuffer packet = ByteBuffer.allocate(read + 1);
                            packet.put((byte)i); // Channel identifier
                            packet.put(buffers[i], 0, read);
                            webSocket.send(packet.array());
                        }
                    }
                }
            } catch (SecurityException e) {
                Log.e(TAG, "Security Exception during recording: " + e.getMessage());
            } catch (Exception e) {
                Log.e(TAG, "Error during recording: " + e.getMessage());
            } finally {
                stopRecording();
            }
        });
        recordingThread.start();
    }

    private void stopRecording() {
        isRecording.set(false);
        if (recordingThread != null) {
            try {
                recordingThread.join();
                recordingThread = null;
            } catch (InterruptedException e) {
                Log.e(TAG, "Error stopping recording thread: " + e.getMessage());
            }
        }

        if (audioRecorders != null) {
            for (AudioRecord recorder : audioRecorders) {
                try {
                    if (recorder != null) {
                        recorder.stop();
                        recorder.release();
                    }
                } catch (Exception e) {
                    Log.e(TAG, "Error releasing AudioRecord: " + e.getMessage());
                }
            }
            audioRecorders = null;
        }

        if (webSocket != null) {
            try {
                webSocket.close();
                webSocket = null;
            } catch (Exception e) {
                Log.e(TAG, "Error closing WebSocket: " + e.getMessage());
            }
        }
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onDestroy() {
        stopRecording();
        super.onDestroy();
    }
}