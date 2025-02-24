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
    }

    public static class InferenceResult {
        private final String vehicleType;
        private final String direction;
        private final double confidence;
        private final boolean shouldNotify;

        public InferenceResult(String vehicleType, String direction,
                               double confidence, boolean shouldNotify) {
            this.vehicleType = vehicleType;
            this.direction = direction;
            this.confidence = confidence;
            this.shouldNotify = shouldNotify;
        }

        public String getVehicleType() { return vehicleType; }
        public String getDirection() { return direction; }
        public double getConfidence() { return confidence; }
        public boolean getShouldNotify() { return shouldNotify; }
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


            System.out.println(mediaRecorder.getMaxAmplitude());

            mediaRecorder.prepare();
            mediaRecorder.start();
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
                            InferenceResult result = new InferenceResult(
                                    inferenceJson.getString("vehicle_type"),
                                    inferenceJson.getString("direction"),
                                    inferenceJson.getDouble("confidence"),
                                    inferenceJson.getBoolean("should_notify")
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