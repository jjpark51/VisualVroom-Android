package edu.skku.cs.visualvroom;

import android.util.Log;

import org.json.JSONObject;

import java.io.IOException;

import okhttp3.*;
import org.json.JSONObject;
import java.io.IOException;
import android.util.Log;

public class AudioSender {
    private static final String TAG = "AudioSender";
    private static final String BACKEND_URL = "http://211.211.177.45:8017/predict";
    private final OkHttpClient client;

    public AudioSender() {
        this.client = new OkHttpClient.Builder()
                .build();
    }

    public interface AudioSenderCallback {
        void onSuccess(String vehicleType, String direction, double confidence);
        void onError(String error);
    }

    public void sendAudioFiles(byte[] leftData, byte[] rightData, byte[] rearData, AudioSenderCallback callback) {
        // Create request body parts
        RequestBody requestBody = new MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart("sample_rate", "16000")
                .addFormDataPart("left_channel", "left.raw",
                        RequestBody.create(MediaType.parse("application/octet-stream"), leftData))
                .addFormDataPart("right_channel", "right.raw",
                        RequestBody.create(MediaType.parse("application/octet-stream"), rightData))
                .addFormDataPart("rear_channel", "rear.raw",
                        RequestBody.create(MediaType.parse("application/octet-stream"), rearData))
                .build();

        // Build the request
        Request request = new Request.Builder()
                .url(BACKEND_URL)
                .post(requestBody)
                .build();

        // Execute the request asynchronously
        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                Log.e(TAG, "Failed to send audio data", e);
                callback.onError("Network error: " + e.getMessage());
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                try (ResponseBody responseBody = response.body()) {
                    if (!response.isSuccessful()) {
                        callback.onError("Server error: " + response.code());
                        return;
                    }

                    if (responseBody == null) {
                        callback.onError("Empty response from server");
                        return;
                    }

                    String jsonStr = responseBody.string();
                    JSONObject json = new JSONObject(jsonStr);

                    if (json.has("message")) {
                        // No confident prediction
                        callback.onError(json.getString("message"));
                        return;
                    }

                    // Parse successful response
                    String vehicleType = json.getString("vehicle_type");
                    String direction = json.getString("direction");
                    double confidence = json.getDouble("confidence");

                    callback.onSuccess(vehicleType, direction, confidence);
                } catch (Exception e) {
                    Log.e(TAG, "Error processing response", e);
                    callback.onError("Error processing response: " + e.getMessage());
                }
            }
        });
    }
}