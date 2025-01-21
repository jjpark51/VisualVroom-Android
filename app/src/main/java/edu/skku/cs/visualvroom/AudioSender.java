package edu.skku.cs.visualvroom;

import java.io.IOException;

import okhttp3.MediaType;
import okhttp3.MultipartBody;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class AudioSender {
    private static final String BACKEND_URL = "http://10.5.31.87:5000/upload";
    private final OkHttpClient client = new OkHttpClient();

    public void sendAudioFiles(byte[] leftData, byte[] rightData, byte[] bothData) {
        MultipartBody.Builder builder = new MultipartBody.Builder()
                .setType(MultipartBody.FORM);

        // Add each audio file
        builder.addFormDataPart("left_mic", "left.raw",
                RequestBody.create(MediaType.parse("application/octet-stream"), leftData));

        builder.addFormDataPart("right_mic", "right.raw",
                RequestBody.create(MediaType.parse("application/octet-stream"), rightData));

        builder.addFormDataPart("both_mics", "both.raw",
                RequestBody.create(MediaType.parse("application/octet-stream"), bothData));

        Request request = new Request.Builder()
                .url(BACKEND_URL)
                .post(builder.build())
                .build();

        try {
            Response response = client.newCall(request).execute();
            if (!response.isSuccessful()) {
                throw new IOException("Unexpected response " + response);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}