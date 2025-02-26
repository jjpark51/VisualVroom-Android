package edu.skku.cs.visualvroom;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.media.MediaRecorder;
import android.os.Build;
import android.util.Log;
import androidx.core.content.ContextCompat;
import okhttp3.*;
import org.json.JSONObject;

import java.io.File;
import java.io.IOException;
import java.util.concurrent.TimeUnit;

public class AudioRecorder {
    private static final String TAG = "AudioRecorder";
    private static final int SAMPLE_RATE = 48000;
    private static final int ENCODING_BIT_RATE = 256000;
    private static final String TEST_ENDPOINT = "http://211.211.177.45:8017/test";

    private final Context context;
    private MediaRecorder mediaRecorder;
    private File outputFile;
    private final OkHttpClient client;

    // Track recording start time for snapshots
    private long recordingStartTime;

    public AudioRecorder(Context context) {
        this.context = context;
        this.client = new OkHttpClient.Builder()
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .writeTimeout(30, TimeUnit.SECONDS)
                .build();
    }

    public interface AudioRecorderCallback {
        void onSuccess(InferenceResult result);
        void onError(String error);
        // New method to handle quiet audio
        void onQuietAudio();
    }

    public static class InferenceResult {
        private final String vehicleType;
        private final String direction;
        private final double confidence;
        private final boolean shouldNotify;
        private final boolean tooQuiet;  // New field

        public InferenceResult(String vehicleType, String direction,
                               double confidence, boolean shouldNotify, boolean tooQuiet) {
            this.vehicleType = vehicleType;
            this.direction = direction;
            this.confidence = confidence;
            this.shouldNotify = shouldNotify;
            this.tooQuiet = tooQuiet;
        }

        public String getVehicleType() { return vehicleType; }
        public String getDirection() { return direction; }
        public double getConfidence() { return confidence; }
        public boolean getShouldNotify() { return shouldNotify; }
        public boolean isTooQuiet() { return tooQuiet; }
    }

    private boolean checkPermission() {
        return ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO)
                == PackageManager.PERMISSION_GRANTED;
    }

    private MediaRecorder createMediaRecorder() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            return new MediaRecorder(context);
        } else {
            return new MediaRecorder();
        }
    }

    public void startRecording() throws SecurityException, IOException {
        if (!checkPermission()) {
            throw new SecurityException("Recording permission not granted");
        }

        try {
            // Create output file
            outputFile = File.createTempFile("audio_recording", ".m4a", context.getCacheDir());
            outputFile.deleteOnExit();

            mediaRecorder = createMediaRecorder();
            mediaRecorder.setAudioSource(MediaRecorder.AudioSource.MIC); // Uses raw audio
            mediaRecorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4); // M4A format
            mediaRecorder.setAudioEncoder(MediaRecorder.AudioEncoder.HE_AAC);
            mediaRecorder.setAudioChannels(2); // Stereo
            mediaRecorder.setAudioSamplingRate(SAMPLE_RATE);
            mediaRecorder.setAudioEncodingBitRate(ENCODING_BIT_RATE);
            mediaRecorder.setOutputFile(outputFile.getAbsolutePath());

            mediaRecorder.prepare();
            mediaRecorder.start();
            recordingStartTime = System.currentTimeMillis();
            Log.d(TAG, "Started recording to: " + outputFile.getAbsolutePath());
        } catch (Exception e) {
            Log.e(TAG, "Error starting recording: " + e.getMessage());
            stopRecording();
            throw e;
        }
    }

    public void stopRecording() {
        if (mediaRecorder != null) {
            try {
                mediaRecorder.stop();
            } catch (Exception e) {
                Log.e(TAG, "Error stopping recording: " + e.getMessage());
            } finally {
                mediaRecorder.release();
                mediaRecorder = null;
            }
        }
    }

    /**
     * Creates a snapshot of the current recording for processing without stopping the ongoing recording
     */
    public void createSnapshot(final AudioRecorderCallback callback) {
        // We need to stop and restart the current recorder to get a snapshot
        // This might create a small gap in the recording
        if (mediaRecorder == null) {
            callback.onError("No active recording");
            return;
        }

        File snapshotFile = null;

        try {
            // Stop current recording to save the file
            mediaRecorder.stop();

            // Make a copy of the current output file
            snapshotFile = new File(outputFile.getAbsolutePath());

            // Start a new recording immediately
            MediaRecorder newRecorder = createMediaRecorder();

            // Create a new output file for the continuing recording
            File newOutputFile = File.createTempFile("audio_recording", ".m4a", context.getCacheDir());
            newOutputFile.deleteOnExit();

            newRecorder.setAudioSource(MediaRecorder.AudioSource.MIC);
            newRecorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4);
            newRecorder.setAudioEncoder(MediaRecorder.AudioEncoder.HE_AAC);
            newRecorder.setAudioChannels(2);
            newRecorder.setAudioSamplingRate(SAMPLE_RATE);
            newRecorder.setAudioEncodingBitRate(ENCODING_BIT_RATE);
            newRecorder.setOutputFile(newOutputFile.getAbsolutePath());

            newRecorder.prepare();
            newRecorder.start();

            // Replace the old recorder with the new one
            mediaRecorder.release();
            mediaRecorder = newRecorder;
            outputFile = newOutputFile;

            // Process the snapshot file
            processSnapshotFile(snapshotFile, callback);

        } catch (Exception e) {
            Log.e(TAG, "Error creating snapshot: " + e.getMessage());
            callback.onError("Error creating snapshot: " + e.getMessage());

            // If there was an error, try to restart recording
            try {
                if (mediaRecorder != null) {
                    mediaRecorder.release();
                }

                startRecording();
            } catch (Exception restartError) {
                Log.e(TAG, "Error restarting recording after snapshot failure: " + restartError.getMessage());
            }
        }
    }

    private void processSnapshotFile(File snapshotFile, final AudioRecorderCallback callback) {
        try {
            if (snapshotFile == null || !snapshotFile.exists()) {
                callback.onError("No snapshot file available");
                return;
            }

            Log.d(TAG, "Processing snapshot file: " + snapshotFile.getAbsolutePath() +
                    " (Size: " + snapshotFile.length() + " bytes)");

            RequestBody requestBody = new MultipartBody.Builder()
                    .setType(MultipartBody.FORM)
                    .addFormDataPart(
                            "audio_file",
                            snapshotFile.getName(),
                            RequestBody.create(snapshotFile, MediaType.parse("audio/mp4"))
                    )
                    .build();

            Request request = new Request.Builder()
                    .url(TEST_ENDPOINT)
                    .post(requestBody)
                    .build();

            client.newCall(request).enqueue(new Callback() {
                @Override
                public void onFailure(Call call, IOException e) {
                    String error = "Network error: " + e.getMessage();
                    Log.e(TAG, error);
                    callback.onError(error);

                    // Clean up the snapshot file
                    if (snapshotFile != null) {
                        snapshotFile.delete();
                    }
                }

                @Override
                public void onResponse(Call call, Response response) throws IOException {
                    try (ResponseBody responseBody = response.body()) {
                        if (!response.isSuccessful() || responseBody == null) {
                            String errorMsg = responseBody != null ? responseBody.string() : "Unknown error";
                            String error = "Server error " + response.code() + ": " + errorMsg;
                            Log.e(TAG, error);
                            callback.onError(error);
                            return;
                        }

                        String responseString = responseBody.string();
                        Log.d(TAG, "Server response for snapshot: " + responseString);

                        try {
                            JSONObject json = new JSONObject(responseString);

                            if ("error".equals(json.getString("status"))) {
                                String errorMsg = json.getString("error");
                                Log.e(TAG, "Server returned error for snapshot: " + errorMsg);
                                callback.onError(errorMsg);
                                return;
                            }

                            JSONObject inferenceJson = json.getJSONObject("inference_result");

                            // Check if audio was too quiet
                            boolean tooQuiet = inferenceJson.optBoolean("too_quiet", false);
                            if (tooQuiet) {
                                Log.d(TAG, "Audio too quiet for processing");
                                callback.onQuietAudio();
                                return;
                            }

                            InferenceResult result = new InferenceResult(
                                    inferenceJson.getString("vehicle_type"),
                                    inferenceJson.getString("direction"),
                                    inferenceJson.getDouble("confidence"),
                                    inferenceJson.getBoolean("should_notify"),
                                    false  // Not too quiet
                            );
                            callback.onSuccess(result);
                        } catch (Exception e) {
                            String error = "Error parsing snapshot response: " + e.getMessage();
                            Log.e(TAG, error);
                            callback.onError(error);
                        }
                    } finally {
                        // Clean up the snapshot file
                        if (snapshotFile != null) {
                            snapshotFile.delete();
                        }
                    }
                }
            });
        } catch (Exception e) {
            String error = "Error processing snapshot file: " + e.getMessage();
            Log.e(TAG, error);
            callback.onError(error);

            if (snapshotFile != null) {
                snapshotFile.delete();
            }
        }
    }

    public void sendToBackend(final AudioRecorderCallback callback) {
        if (outputFile == null || !outputFile.exists()) {
            callback.onError("No recording file available");
            return;
        }

        try {
            Log.d(TAG, "Sending file: " + outputFile.getAbsolutePath() +
                    " (Size: " + outputFile.length() + " bytes)");

            RequestBody requestBody = new MultipartBody.Builder()
                    .setType(MultipartBody.FORM)
                    .addFormDataPart(
                            "audio_file",
                            outputFile.getName(),
                            RequestBody.create(outputFile, MediaType.parse("audio/mp4"))
                    )
                    .build();

            Request request = new Request.Builder()
                    .url(TEST_ENDPOINT)
                    .post(requestBody)
                    .build();

            client.newCall(request).enqueue(new Callback() {
                @Override
                public void onFailure(Call call, IOException e) {
                    String error = "Network error: " + e.getMessage();
                    Log.e(TAG, error);
                    callback.onError(error);
                }

                @Override
                public void onResponse(Call call, Response response) throws IOException {
                    try (ResponseBody responseBody = response.body()) {
                        if (!response.isSuccessful() || responseBody == null) {
                            String errorMsg = responseBody != null ? responseBody.string() : "Unknown error";
                            String error = "Server error " + response.code() + ": " + errorMsg;
                            Log.e(TAG, error);
                            callback.onError(error);
                            return;
                        }

                        String responseString = responseBody.string();
                        Log.d(TAG, "Server response: " + responseString);

                        try {
                            JSONObject json = new JSONObject(responseString);

                            if ("error".equals(json.getString("status"))) {
                                String errorMsg = json.getString("error");
                                Log.e(TAG, "Server returned error: " + errorMsg);
                                callback.onError(errorMsg);
                                return;
                            }

                            JSONObject inferenceJson = json.getJSONObject("inference_result");

                            // Check if audio was too quiet
                            boolean tooQuiet = inferenceJson.optBoolean("too_quiet", false);
                            if (tooQuiet) {
                                Log.d(TAG, "Audio too quiet for processing");
                                callback.onQuietAudio();
                                return;
                            }

                            InferenceResult result = new InferenceResult(
                                    inferenceJson.getString("vehicle_type"),
                                    inferenceJson.getString("direction"),
                                    inferenceJson.getDouble("confidence"),
                                    inferenceJson.getBoolean("should_notify"),
                                    false  // Not too quiet
                            );
                            callback.onSuccess(result);
                        } catch (Exception e) {
                            String error = "Error parsing response: " + e.getMessage();
                            Log.e(TAG, error);
                            callback.onError(error);
                        }
                    } finally {
                        if (outputFile != null) {
                            outputFile.delete();
                            outputFile = null;
                        }
                    }
                }
            });
        } catch (Exception e) {
            String error = "Error sending file: " + e.getMessage();
            Log.e(TAG, error);
            callback.onError(error);

            if (outputFile != null) {
                outputFile.delete();
                outputFile = null;
            }
        }
    }
}