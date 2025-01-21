package edu.skku.cs.visualvroom;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.media.AudioFormat;
import android.media.AudioRecord;

import androidx.core.content.ContextCompat;

public class MultiAudioRecorder {
    private static final int SAMPLE_RATE = 16000;  // 16 kHz
    private static final int DURATION_SECONDS = 10;
    private final Context context;

    public MultiAudioRecorder(Context context) {
        this.context = context;
    }

    private boolean checkPermission() {
        return ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO)
                == PackageManager.PERMISSION_GRANTED;
    }

    public byte[] recordLeftChannel() throws SecurityException {
        if (!checkPermission()) {
            throw new SecurityException("Recording permission not granted");
        }

        int minBufferSize = AudioRecord.getMinBufferSize(SAMPLE_RATE,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT);

        if (minBufferSize == AudioRecord.ERROR || minBufferSize == AudioRecord.ERROR_BAD_VALUE) {
            throw new IllegalStateException("Failed to get minimum buffer size");
        }

        int bufferSize = Math.max(SAMPLE_RATE * DURATION_SECONDS, minBufferSize);

        AudioFormat leftFormat = new AudioFormat.Builder()
                .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                .setSampleRate(SAMPLE_RATE)
                .setChannelMask(AudioFormat.CHANNEL_IN_LEFT)
                .build();

        AudioRecord leftRecorder;
        try {
            leftRecorder = new AudioRecord.Builder()
                    .setAudioFormat(leftFormat)
                    .setBufferSizeInBytes(bufferSize)
                    .build();
        } catch (SecurityException e) {
            throw new SecurityException("Failed to create AudioRecord: " + e.getMessage());
        }

        if (leftRecorder.getState() != AudioRecord.STATE_INITIALIZED) {
            leftRecorder.release();
            throw new IllegalStateException("Failed to initialize AudioRecord");
        }

        byte[] leftData = new byte[bufferSize];

        try {
            leftRecorder.startRecording();
            int bytesRead = leftRecorder.read(leftData, 0, bufferSize);
            if (bytesRead < 0) {
                throw new IllegalStateException("Error reading from AudioRecord: " + bytesRead);
            }
            return leftData;
        } finally {
            leftRecorder.stop();
            leftRecorder.release();
        }
    }

    public byte[] recordRightChannel() throws SecurityException {
        if (!checkPermission()) {
            throw new SecurityException("Recording permission not granted");
        }

        int minBufferSize = AudioRecord.getMinBufferSize(SAMPLE_RATE,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT);

        if (minBufferSize == AudioRecord.ERROR || minBufferSize == AudioRecord.ERROR_BAD_VALUE) {
            throw new IllegalStateException("Failed to get minimum buffer size");
        }

        int bufferSize = Math.max(SAMPLE_RATE * DURATION_SECONDS, minBufferSize);

        AudioFormat rightFormat = new AudioFormat.Builder()
                .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                .setSampleRate(SAMPLE_RATE)
                .setChannelMask(AudioFormat.CHANNEL_IN_RIGHT)
                .build();

        AudioRecord rightRecorder;
        try {
            rightRecorder = new AudioRecord.Builder()
                    .setAudioFormat(rightFormat)
                    .setBufferSizeInBytes(bufferSize)
                    .build();
        } catch (SecurityException e) {
            throw new SecurityException("Failed to create AudioRecord: " + e.getMessage());
        }

        if (rightRecorder.getState() != AudioRecord.STATE_INITIALIZED) {
            rightRecorder.release();
            throw new IllegalStateException("Failed to initialize AudioRecord");
        }

        byte[] rightData = new byte[bufferSize];

        try {
            rightRecorder.startRecording();
            int bytesRead = rightRecorder.read(rightData, 0, bufferSize);
            if (bytesRead < 0) {
                throw new IllegalStateException("Error reading from AudioRecord: " + bytesRead);
            }
            return rightData;
        } finally {
            rightRecorder.stop();
            rightRecorder.release();
        }
    }

    public byte[] recordBothChannels() throws SecurityException {
        if (!checkPermission()) {
            throw new SecurityException("Recording permission not granted");
        }

        int minBufferSize = AudioRecord.getMinBufferSize(SAMPLE_RATE,
                AudioFormat.CHANNEL_IN_STEREO,
                AudioFormat.ENCODING_PCM_16BIT);

        if (minBufferSize == AudioRecord.ERROR || minBufferSize == AudioRecord.ERROR_BAD_VALUE) {
            throw new IllegalStateException("Failed to get minimum buffer size");
        }

        int bufferSize = Math.max(SAMPLE_RATE * DURATION_SECONDS * 2, minBufferSize);

        AudioFormat stereoFormat = new AudioFormat.Builder()
                .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                .setSampleRate(SAMPLE_RATE)
                .setChannelMask(AudioFormat.CHANNEL_IN_STEREO)
                .build();

        AudioRecord stereoRecorder;
        try {
            stereoRecorder = new AudioRecord.Builder()
                    .setAudioFormat(stereoFormat)
                    .setBufferSizeInBytes(bufferSize)
                    .build();
        } catch (SecurityException e) {
            throw new SecurityException("Failed to create AudioRecord: " + e.getMessage());
        }

        if (stereoRecorder.getState() != AudioRecord.STATE_INITIALIZED) {
            stereoRecorder.release();
            throw new IllegalStateException("Failed to initialize AudioRecord");
        }

        byte[] stereoData = new byte[bufferSize];

        try {
            stereoRecorder.startRecording();
            int bytesRead = stereoRecorder.read(stereoData, 0, bufferSize);
            if (bytesRead < 0) {
                throw new IllegalStateException("Error reading from AudioRecord: " + bytesRead);
            }
            return stereoData;
        } finally {
            stereoRecorder.stop();
            stereoRecorder.release();
        }
    }
}