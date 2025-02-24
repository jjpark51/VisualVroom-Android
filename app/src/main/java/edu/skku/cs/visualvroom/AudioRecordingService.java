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
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
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
            SAMPLE_RATE, CHANNEL_CONFIG, AUDIO_FORMAT) * 4; // Increased buffer size
    private static final String NOTIFICATION_CHANNEL_ID = "audio_service_channel";
    private static final int NOTIFICATION_ID = 1;

    // Buffer for 5 seconds of audio (increased from 3)
    private static final int SECONDS_TO_BUFFER = 5;
    private static final int SAMPLES_PER_BUFFER = SAMPLE_RATE * SECONDS_TO_BUFFER;

    private AudioRecord audioRecord;
    private AtomicBoolean isRecording;
    private Thread recordingThread;
    private final OkHttpClient client;
    private static final String SERVER_URL = "http://211.211.177.45:8017/predict";

    // Buffers for left and right channels
    private final ArrayDeque<Short> leftBuffer = new ArrayDeque<>(SAMPLES_PER_BUFFER);
    private final ArrayDeque<Short> rightBuffer = new ArrayDeque<>(SAMPLES_PER_BUFFER);

    // Audio level monitoring
    private float leftMicLevel = 0;
    private float rightMicLevel = 0;
    private static final int LEVEL_MONITOR_INTERVAL = 100;

    // RMS level tracking for auto-gain
    private double leftRmsSum = 0;
    private double rightRmsSum = 0;
    private int rmsSampleCount = 0;
    private static final int RMS_WINDOW_SIZE = SAMPLE_RATE; // 1 second window
    private static final double BASE_GAIN = 50.0;  // Increased from 25.0f
    private static final double MAX_GAIN = 100.0;    // Increased from 15.0
    private static final double TARGET_RMS = 0.95;  // Increased from 0.9
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
            // Configure for stereo recording with higher quality settings
            AudioFormat audioFormat = new AudioFormat.Builder()
                    .setEncoding(AUDIO_FORMAT)
                    .setSampleRate(SAMPLE_RATE)
                    .setChannelMask(CHANNEL_CONFIG)
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .build();

            // Try to use UNPROCESSED source first for raw audio
            try {
                audioRecord = new AudioRecord.Builder()
                        .setAudioSource(MediaRecorder.AudioSource.MIC)
                        .setAudioFormat(audioFormat)
                        .setBufferSizeInBytes(BUFFER_SIZE)
                        .build();
            } catch (Exception e) {
                // Fall back to DEFAULT if UNPROCESSED is not supported
                Log.w(TAG, "UNPROCESSED source not supported, falling back to DEFAULT");
                audioRecord = new AudioRecord.Builder()
                        .setAudioSource(MediaRecorder.AudioSource.DEFAULT)
                        .setAudioFormat(audioFormat)
                        .setBufferSizeInBytes(BUFFER_SIZE)
                        .build();
            }

            if (audioRecord.getState() != AudioRecord.STATE_INITIALIZED) {
                throw new IllegalStateException("Failed to initialize AudioRecord");
            }

            Log.i(TAG, "AudioRecord initialized successfully");
            Log.i(TAG, "Channel count: " + audioFormat.getChannelCount());
            Log.i(TAG, "Channel mask: " + audioFormat.getChannelMask());
            Log.i(TAG, "Sample rate: " + audioFormat.getSampleRate());
            Log.i(TAG, "Encoding: " + audioFormat.getEncoding());
            Log.i(TAG, "Buffer size: " + BUFFER_SIZE);
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

    private double calculateOptimalGain(short[] buffer, int shortsRead) {
        double maxAmplitude = 0;
        for (int i = 0; i < shortsRead; i++) {
            maxAmplitude = Math.max(maxAmplitude, Math.abs(buffer[i]));
        }

        // Target amplitude around 50-70% of max (32767)
        double targetAmplitude = 20000;  // About 60% of max
        return Math.min(targetAmplitude / (maxAmplitude + 1e-6), MAX_GAIN);  // Use MAX_GAIN constant
    }


    private synchronized void processAudioData(short[] buffer, int shortsRead) {
        // Log original signal values
        for (int i = 0; i < Math.min(10, shortsRead); i += 2) {
            Log.d(TAG, String.format("Original Signal - Left: %d, Right: %d",
                    buffer[i], buffer[i + 1]));
        }

        int sampleCounter = 0;
        float leftSum = 0;
        float rightSum = 0;

        // Process interleaved stereo data
        for (int i = 0; i < shortsRead; i += 2) {
            // Apply BASE_GAIN
            double leftAmplified = buffer[i] * BASE_GAIN;
            double rightAmplified = buffer[i + 1] * BASE_GAIN;

            short leftSample = (short)Math.max(Math.min(leftAmplified, 32767), -32768);
            short rightSample = (short)Math.max(Math.min(rightAmplified, 32767), -32768);

            // Log amplified values for first few samples
            if (i < 10) {
                Log.d(TAG, String.format("After BASE_GAIN - Left: %d (raw: %.2f), Right: %d (raw: %.2f)",
                        leftSample, leftAmplified, rightSample, rightAmplified));
            }

            // Update RMS calculation
            leftRmsSum += (leftSample * leftSample) / 32768.0 / 32768.0;
            rightRmsSum += (rightSample * rightSample) / 32768.0 / 32768.0;
            rmsSampleCount++;

            // Apply auto-gain if we have enough samples
            if (rmsSampleCount >= RMS_WINDOW_SIZE) {
                double leftRms = Math.sqrt(leftRmsSum / rmsSampleCount);
                double rightRms = Math.sqrt(rightRmsSum / rmsSampleCount);

                Log.d(TAG, String.format("RMS Values - Left: %.4f, Right: %.4f", leftRms, rightRms));

                // Calculate gain adjustments
                double leftGain = TARGET_RMS / Math.max(leftRms, 1e-9);
                double rightGain = TARGET_RMS / Math.max(rightRms, 1e-9);

                // Limit to MAX_GAIN
                leftGain = Math.min(leftGain, MAX_GAIN);
                rightGain = Math.min(rightGain, MAX_GAIN);

                Log.d(TAG, String.format("Auto Gains - Left: %.2f, Right: %.2f", leftGain, rightGain));

                // Apply gain (with limiting)
                double leftFinal = leftSample * leftGain;
                double rightFinal = rightSample * rightGain;

                leftSample = (short) Math.max(Math.min(leftFinal, 32767), -32768);
                rightSample = (short) Math.max(Math.min(rightFinal, 32767), -32768);

                if (i < 10) {
                    Log.d(TAG, String.format("Final Values - Left: %d (raw: %.2f), Right: %d (raw: %.2f)",
                            leftSample, leftFinal, rightSample, rightFinal));
                }

                // Reset RMS tracking
                leftRmsSum = 0;
                rightRmsSum = 0;
                rmsSampleCount = 0;
            }

            // Maintain buffer size
            if (leftBuffer.size() >= SAMPLES_PER_BUFFER) {
                leftBuffer.removeFirst();
                rightBuffer.removeFirst();
            }

            // Add new samples
            leftBuffer.addLast(leftSample);
            rightBuffer.addLast(rightSample);

            // Calculate audio levels
            sampleCounter++;
            leftSum += Math.abs(leftSample);
            rightSum += Math.abs(rightSample);

            // Monitor levels periodically
            if (sampleCounter >= LEVEL_MONITOR_INTERVAL) {
                leftMicLevel = leftSum / LEVEL_MONITOR_INTERVAL;
                rightMicLevel = rightSum / LEVEL_MONITOR_INTERVAL;

                // Log mic levels for verification
                Log.d(TAG, String.format("Mic Levels - Left: %.2f, Right: %.2f",
                        leftMicLevel, rightMicLevel));

                // Reset counters
                sampleCounter = 0;
                leftSum = 0;
                rightSum = 0;
            }
        }

        // Check if we have enough data to send
        if (leftBuffer.size() >= SAMPLES_PER_BUFFER) {
            sendBufferedData();
        }
    }

    private byte[] normalizeAudioData(byte[] rawData) {
        // Convert bytes to shorts
        short[] shorts = new short[rawData.length / 2];
        ByteBuffer.wrap(rawData).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer().get(shorts);

        // Pre-amplification (add this)
        float preAmpGain = 5.0f;
        for (int i = 0; i < shorts.length; i++) {
            shorts[i] = (short)Math.max(Math.min(shorts[i] * preAmpGain, 32767), -32768);
        }

        // Convert back to bytes without normalization
        byte[] amplified = new byte[rawData.length];
        for (int i = 0; i < shorts.length; i++) {
            amplified[i*2] = (byte)(shorts[i] & 0xff);
            amplified[i*2 + 1] = (byte)((shorts[i] >> 8) & 0xff);
        }

        return amplified;
    }
    private void sendBufferedData() {
        try {
            // Convert buffers to byte arrays
            byte[] leftData = normalizeAudioData(shortArrayToByteArray(new ArrayList<>(leftBuffer)));
            byte[] rightData = normalizeAudioData(shortArrayToByteArray(new ArrayList<>(rightBuffer)));

            // Create request parts
            MultipartBody.Builder builder = new MultipartBody.Builder()
                    .setType(MultipartBody.FORM)
                    .addFormDataPart("sample_rate", String.valueOf(SAMPLE_RATE));

            // Add parts to the request
            MediaType audioType = MediaType.parse("application/octet-stream");

            // Add left channel
            RequestBody leftBody = RequestBody.create(audioType, leftData);
            builder.addFormDataPart("left_channel", "left.raw", leftBody);

            // Add right channel
            RequestBody rightBody = RequestBody.create(audioType, rightData);
            builder.addFormDataPart("right_channel", "right.raw", rightBody);

            // Build and send the request
            Request request = new Request.Builder()
                    .url(SERVER_URL)
                    .post(builder.build())
                    .build();

            // Log the request details
            Log.d(TAG, String.format("Sending request to %s", SERVER_URL));
            Log.d(TAG, String.format("Left channel size: %d bytes, Right channel size: %d bytes",
                    leftData.length, rightData.length));

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
    private byte[] shortArrayToByteArray(ArrayList<Short> shorts) {
        byte[] bytes = new byte[shorts.size() * 2];
        for (int i = 0; i < shorts.size(); i++) {
            short value = shorts.get(i);
            // Convert to little-endian
            bytes[i * 2] = (byte) (value & 0xff);
            bytes[i * 2 + 1] = (byte) ((value >> 8) & 0xff);
        }
        return bytes;
    }


    private synchronized void stopRecording() {
        if (!isRecording.get()) {
            return;
        }

        isRecording.set(false);

        if (recordingThread != null) {
            try {
                recordingThread.join(1000);
                if (recordingThread.isAlive()) {
                    recordingThread.interrupt();
                }
            } catch (InterruptedException e) {
                Log.e(TAG, "Error stopping recording thread: " + e.getMessage());
            }
            recordingThread = null;
        }

        if (audioRecord != null) {
            try {
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

        // Clear buffers
        synchronized (leftBuffer) {
            leftBuffer.clear();
        }
        synchronized (rightBuffer) {
            rightBuffer.clear();
        }

        // Reset mic levels
        leftMicLevel = 0;
        rightMicLevel = 0;
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onDestroy() {
        try {
            stopRecording();

            if (client != null) {
                client.dispatcher().cancelAll();
            }

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