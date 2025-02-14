package edu.skku.cs.visualvroom;

import android.Manifest;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.os.Build;
import android.os.IBinder;
import android.util.Log;
import androidx.core.app.NotificationCompat;
import androidx.core.content.ContextCompat;

import okhttp3.*;
import java.io.IOException;
import java.util.ArrayList;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.ArrayDeque;

public class AudioRecordingService extends Service {
    private static final String TAG = "AudioRecordingService";
    private static final int SAMPLE_RATE = 16000;
    private static final int CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_STEREO;
    private static final int AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT;
    private static final int BYTES_PER_SAMPLE = 2;
    private static final int BUFFER_SIZE = AudioRecord.getMinBufferSize(
            SAMPLE_RATE, CHANNEL_CONFIG, AUDIO_FORMAT) * 2;
    private static final String NOTIFICATION_CHANNEL_ID = "audio_service_channel";
    private static final int NOTIFICATION_ID = 1;

    // Buffer for 3 seconds of audio
    private static final int SECONDS_TO_BUFFER = 3;
    private static final int SAMPLES_PER_BUFFER = SAMPLE_RATE * SECONDS_TO_BUFFER;

    private AudioRecord audioRecord;
    private AtomicBoolean isRecording;
    private Thread recordingThread;
    private final OkHttpClient client;
    private static final String SERVER_URL = "http://211.211.177.45:8017/predict";

    // Circular buffers for each channel
    private final ArrayDeque<Short> leftBuffer = new ArrayDeque<>(SAMPLES_PER_BUFFER);
    private final ArrayDeque<Short> rightBuffer = new ArrayDeque<>(SAMPLES_PER_BUFFER);
    private final ArrayDeque<Short> stereoBuffer = new ArrayDeque<>(SAMPLES_PER_BUFFER * 2);

    public AudioRecordingService() {
        client = new OkHttpClient.Builder()
                .build();
        isRecording = new AtomicBoolean(false);
    }

    @Override
    public void onCreate() {
        super.onCreate();
        createNotificationChannel();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && "START_RECORDING".equals(intent.getAction())) {
            if (!checkPermission()) {
                Log.e(TAG, "Recording permission not granted");
                stopSelf();
                return START_NOT_STICKY;
            }

            try {
                startForeground(NOTIFICATION_ID, createNotification());
                initializeAudioRecorder();
                startRecording();
            } catch (SecurityException e) {
                Log.e(TAG, "Security Exception in onStartCommand: " + e.getMessage());
                stopSelf();
            } catch (Exception e) {
                Log.e(TAG, "Error in onStartCommand: " + e.getMessage());
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

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    NOTIFICATION_CHANNEL_ID,
                    "Audio Recording Service",
                    NotificationManager.IMPORTANCE_LOW
            );
            channel.setDescription("Channel for Audio Recording Service");
            NotificationManager notificationManager = getSystemService(NotificationManager.class);
            notificationManager.createNotificationChannel(channel);
        }
    }

    private Notification createNotification() {
        return new NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
                .setContentTitle("Audio Recording Service")
                .setContentText("Recording in progress...")
                .setSmallIcon(R.drawable.ic_notification)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .build();
    }

    private void initializeAudioRecorder() {
        if (!checkPermission()) {
            Log.e(TAG, "Recording permission not granted");
            throw new SecurityException("Recording permission not granted");
        }

        try {
            audioRecord = new AudioRecord.Builder()
                    .setAudioSource(MediaRecorder.AudioSource.DEFAULT)
                    .setAudioFormat(new AudioFormat.Builder()
                            .setEncoding(AUDIO_FORMAT)
                            .setSampleRate(SAMPLE_RATE)
                            .setChannelMask(CHANNEL_CONFIG)
                            .build())
                    .setBufferSizeInBytes(BUFFER_SIZE)
                    .build();

            if (audioRecord.getState() != AudioRecord.STATE_INITIALIZED) {
                throw new IllegalStateException("Failed to initialize AudioRecord");
            }
        } catch (SecurityException e) {
            Log.e(TAG, "Security Exception initializing AudioRecord: " + e.getMessage());
            throw e;
        } catch (Exception e) {
            Log.e(TAG, "Error initializing AudioRecord: " + e.getMessage());
            throw e;
        }
    }

    private void startRecording() {
        if (!checkPermission()) {
            Log.e(TAG, "Recording permission not granted");
            stopSelf();
            return;
        }

        if (isRecording.get()) return;

        isRecording.set(true);
        recordingThread = new Thread(() -> {
            short[] readBuffer = new short[BUFFER_SIZE / 2]; // Convert bytes to shorts

            try {
                audioRecord.startRecording();

                while (isRecording.get()) {
                    int shortsRead = audioRecord.read(readBuffer, 0, readBuffer.length);

                    if (shortsRead > 0) {
                        // Process and buffer the audio data
                        processAudioData(readBuffer, shortsRead);
                    }
                }
            } catch (Exception e) {
                Log.e(TAG, "Error during recording: " + e.getMessage());
            } finally {
                stopRecording();
            }
        });
        recordingThread.start();
    }

    private synchronized void processAudioData(short[] buffer, int shortsRead) {
        // Add new samples to buffers
        for (int i = 0; i < shortsRead; i += 2) {
            // Maintain buffer size by removing old samples if necessary
            if (leftBuffer.size() >= SAMPLES_PER_BUFFER) {
                leftBuffer.removeFirst();
                rightBuffer.removeFirst();
            }
            if (stereoBuffer.size() >= SAMPLES_PER_BUFFER * 2) {
                stereoBuffer.removeFirst();
                stereoBuffer.removeFirst();
            }

            // Add new samples
            short leftSample = buffer[i];
            short rightSample = buffer[i + 1];

            leftBuffer.addLast(leftSample);
            rightBuffer.addLast(rightSample);
            stereoBuffer.addLast(leftSample);
            stereoBuffer.addLast(rightSample);
        }

        // Check if we have enough data to send
        if (leftBuffer.size() >= SAMPLES_PER_BUFFER) {
            sendBufferedData();
        }
    }

    private void sendBufferedData() {
        if (leftBuffer.size() < SAMPLES_PER_BUFFER) return;

        try {
            // Create the MultipartBody with proper Content-Type headers
            MultipartBody.Builder builder = new MultipartBody.Builder()
                    .setType(MultipartBody.FORM);

            // Add audio_request as application/json
            MediaType jsonType = MediaType.parse("application/json; charset=utf-8");
//            RequestBody audioRequestBody = RequestBody.create(
//                    jsonType,
//                    "{\"sample_rate\":" + SAMPLE_RATE + "}"
//            );
            builder.addFormDataPart("sample_rate", String.valueOf(SAMPLE_RATE));

            // Add channel data
            MediaType audioType = MediaType.parse("application/octet-stream");
            builder.addFormDataPart("left_channel", "left.raw",
                    RequestBody.create(audioType, shortArrayToByteArray(new ArrayList<>(leftBuffer))));
            builder.addFormDataPart("right_channel", "right.raw",
                    RequestBody.create(audioType, shortArrayToByteArray(new ArrayList<>(rightBuffer))));
            builder.addFormDataPart("rear_channel", "rear.raw",
                    RequestBody.create(audioType, shortArrayToByteArray(new ArrayList<>(stereoBuffer))));

            Request request = new Request.Builder()
                    .url(SERVER_URL)
                    .post(builder.build())
                    .build();

            // Log request details
            Log.d(TAG, "Sending request with sample rate: " + SAMPLE_RATE +
                    ", Buffer sizes: L:" + leftBuffer.size() +
                    ", R:" + rightBuffer.size() +
                    ", Stereo:" + stereoBuffer.size());

            // Send request asynchronously
            client.newCall(request).enqueue(new Callback() {
                @Override
                public void onFailure(Call call, IOException e) {
                    Log.e(TAG, "Failed to send audio data: " + e.getMessage());
                }

                @Override
                public void onResponse(Call call, Response response) throws IOException {
                    try (ResponseBody responseBody = response.body()) {
                        if (!response.isSuccessful()) {
                            String errorBody = responseBody != null ? responseBody.string() : "No error body";
                            Log.e(TAG, String.format("Server error %d: %s", response.code(), errorBody));
                            return;
                        }

                        if (responseBody != null) {
                            String result = responseBody.string();
                            Log.d(TAG, "Server response: " + result);
                            // Broadcast result to activity
                            Intent intent = new Intent("AUDIO_INFERENCE_RESULT");
                            intent.putExtra("result", result);
                            sendBroadcast(intent);
                        }
                    }
                }
            });
        } catch (Exception e) {
            Log.e(TAG, "Error sending audio data: " + e.getMessage());
            e.printStackTrace();
        }
    }
    private byte[] shortArrayToByteArray(ArrayList<Short> shortArray) {
        byte[] byteArray = new byte[shortArray.size() * 2];
        for (int i = 0; i < shortArray.size(); i++) {
            short sample = shortArray.get(i);
            byteArray[i * 2] = (byte) (sample & 0xff);
            byteArray[i * 2 + 1] = (byte) ((sample >> 8) & 0xff);
        }
        return byteArray;
    }
    private synchronized void stopRecording() {
        if (!isRecording.get()) {
            return;  // Already stopped
        }

        // First set the flag to stop the recording thread
        isRecording.set(false);

        // Safely stop the recording thread
        if (recordingThread != null) {
            try {
                // Give the thread a chance to finish naturally
                recordingThread.join(1000);  // Wait up to 1 second

                if (recordingThread.isAlive()) {
                    recordingThread.interrupt();
                }
            } catch (InterruptedException e) {
                Log.e(TAG, "Error stopping recording thread: " + e.getMessage());
            } finally {
                recordingThread = null;
            }
        }

        // Safely stop and release AudioRecord
        if (audioRecord != null) {
            try {
                // Check if we're still recording before stopping
                if (audioRecord.getRecordingState() == AudioRecord.RECORDSTATE_RECORDING) {
                    audioRecord.stop();
                }
            } catch (IllegalStateException e) {
                Log.e(TAG, "Error stopping AudioRecord: " + e.getMessage());
            } finally {
                try {
                    audioRecord.release();
                } catch (Exception e) {
                    Log.e(TAG, "Error releasing AudioRecord: " + e.getMessage());
                }
                audioRecord = null;
            }
        }

        // Clear the buffers in a thread-safe way
        synchronized (leftBuffer) {
            leftBuffer.clear();
        }
        synchronized (rightBuffer) {
            rightBuffer.clear();
        }
        synchronized (stereoBuffer) {
            stereoBuffer.clear();
        }
    }
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onDestroy() {
        try {
            // Stop the recording first
            stopRecording();

            // Cancel any pending network requests
            if (client != null) {
                client.dispatcher().cancelAll();
            }

            // Stop the foreground service
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                stopForeground(STOP_FOREGROUND_REMOVE);
            } else {
                stopForeground(true);
            }
        } catch (Exception e) {
            Log.e(TAG, "Error in onDestroy: " + e.getMessage());
        } finally {
            super.onDestroy();
        }
    }
}