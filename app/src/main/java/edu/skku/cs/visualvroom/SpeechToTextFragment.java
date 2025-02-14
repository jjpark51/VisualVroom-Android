package edu.skku.cs.visualvroom;

import android.content.Intent;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.fragment.app.Fragment;
import com.google.android.material.floatingactionbutton.FloatingActionButton;

import okhttp3.*;
import org.json.JSONObject;
import java.io.IOException;

public class SpeechToTextFragment extends Fragment {
    private static final String TAG = "SpeechToTextFragment";
    private static final String BACKEND_URL = "http://211.211.177.45:8017/transcribe";
    private static final int SAMPLE_RATE = 16000;
    private static final int CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_MONO;
    private static final int AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT;
    private static final int BUFFER_SIZE = AudioRecord.getMinBufferSize(
            SAMPLE_RATE, CHANNEL_CONFIG, AUDIO_FORMAT) * 2;

    private TextView transcribedText;
    private FloatingActionButton micButton;
    private AudioRecord audioRecord;
    private boolean isRecording = false;
    private Thread recordingThread;
    private final OkHttpClient client = new OkHttpClient();

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_speech_text, container, false);

        transcribedText = view.findViewById(R.id.transcribedText);
        micButton = view.findViewById(R.id.micButton);

        micButton.setOnClickListener(v -> {
            if (isRecording) {
                stopRecording();
            } else {
                startRecording();
            }
        });

        return view;
    }

    private void startRecording() {
        if (isRecording) return;

        try {
            audioRecord = new AudioRecord.Builder()
                    .setAudioSource(MediaRecorder.AudioSource.MIC)
                    .setAudioFormat(new AudioFormat.Builder()
                            .setEncoding(AUDIO_FORMAT)
                            .setSampleRate(SAMPLE_RATE)
                            .setChannelMask(CHANNEL_CONFIG)
                            .build())
                    .setBufferSizeInBytes(BUFFER_SIZE)
                    .build();

            isRecording = true;
            micButton.setImageResource(R.drawable.ic_mic_active);

            recordingThread = new Thread(() -> {
                byte[] buffer = new byte[BUFFER_SIZE];
                audioRecord.startRecording();

                while (isRecording) {
                    int bytesRead = audioRecord.read(buffer, 0, buffer.length);
                    if (bytesRead > 0) {
                        sendAudioToBackend(buffer, bytesRead);
                    }
                }
            });
            recordingThread.start();

        } catch (SecurityException e) {
            Log.e(TAG, "Permission denied: " + e.getMessage());
        } catch (Exception e) {
            Log.e(TAG, "Error starting recording: " + e.getMessage());
        }
    }

    private void stopRecording() {
        isRecording = false;
        micButton.setImageResource(R.drawable.ic_mic);

        if (audioRecord != null) {
            try {
                audioRecord.stop();
                audioRecord.release();
                audioRecord = null;
            } catch (Exception e) {
                Log.e(TAG, "Error stopping recording: " + e.getMessage());
            }
        }

        if (recordingThread != null) {
            try {
                recordingThread.join();
                recordingThread = null;
            } catch (InterruptedException e) {
                Log.e(TAG, "Error stopping recording thread: " + e.getMessage());
            }
        }
    }

    private void sendAudioToBackend(byte[] audioData, int bytesRead) {
        RequestBody requestBody = new MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart("sample_rate", String.valueOf(SAMPLE_RATE))
                .addFormDataPart("audio_data", "audio.raw",
                        RequestBody.create(MediaType.parse("application/octet-stream"),
                                audioData, 0, bytesRead))
                .build();

        Request request = new Request.Builder()
                .url(BACKEND_URL)
                .post(requestBody)
                .build();

        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                Log.e(TAG, "Failed to send audio data: " + e.getMessage());
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                try (ResponseBody responseBody = response.body()) {
                    if (!response.isSuccessful() || responseBody == null) {
                        Log.e(TAG, "Server error: " + response.code());
                        return;
                    }

                    JSONObject result = new JSONObject(responseBody.string());
                    String transcribedString = result.getString("text");
                    updateTranscribedText(transcribedString);
                } catch (Exception e) {
                    Log.e(TAG, "Error processing response: " + e.getMessage());
                }
            }
        });
    }

    private void updateTranscribedText(String newText) {
        if (getActivity() == null) return;

        getActivity().runOnUiThread(() -> {
            String currentText = transcribedText.getText().toString();
            String updatedText = currentText.isEmpty() ? newText :
                    currentText + "\n" + newText;
            transcribedText.setText(updatedText);
        });
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        if (isRecording) {
            stopRecording();
        }
    }
}