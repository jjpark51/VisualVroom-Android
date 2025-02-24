package edu.skku.cs.visualvroom;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.ImageButton;
import androidx.fragment.app.Fragment;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.widget.Toast;

import okhttp3.*;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.concurrent.TimeUnit;
import java.io.ByteArrayOutputStream;


public class SpeechToTextFragment extends Fragment {
    private static final String TAG = "SpeechToTextFragment";
    private static final String BACKEND_URL = "http://211.211.177.45:8017/transcribe";
    private static final int SAMPLE_RATE = 16000;
    private static final int CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_MONO;
    private static final int AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT;
    private static final int BUFFER_SIZE = AudioRecord.getMinBufferSize(
            SAMPLE_RATE, CHANNEL_CONFIG, AUDIO_FORMAT) * 2;

    private EditText transcribedText;
    private FloatingActionButton micButton;
    private ImageButton clearButton;
    private ImageButton copyButton;
    private AudioRecord audioRecord;
    private boolean isRecording = false;
    private Thread recordingThread;
    private ByteArrayOutputStream audioBuffer;
    private final OkHttpClient client;

    public SpeechToTextFragment() {
        client = new OkHttpClient.Builder()
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .writeTimeout(30, TimeUnit.SECONDS)
                .build();
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_speech_text, container, false);

        transcribedText = view.findViewById(R.id.transcribedText);
        micButton = view.findViewById(R.id.micButton);
        clearButton = view.findViewById(R.id.clearButton);
        copyButton = view.findViewById(R.id.copyButton);

        micButton.setOnClickListener(v -> {
            if (isRecording) {
                stopRecording();
            } else {
                startRecording();
            }
        });

        clearButton.setOnClickListener(v -> transcribedText.setText(""));

        copyButton.setOnClickListener(v -> {
            String text = transcribedText.getText().toString();
            if (!TextUtils.isEmpty(text)) {
                ClipboardManager clipboard = (ClipboardManager)
                        requireActivity().getSystemService(Context.CLIPBOARD_SERVICE);
                ClipData clip = ClipData.newPlainText("Transcribed Text", text);
                clipboard.setPrimaryClip(clip);
                showToast("Text copied to clipboard");
            }
        });

        return view;
    }

    private void startRecording() {
        if (isRecording) return;

        try {
            // Create new buffer for recording
            audioBuffer = new ByteArrayOutputStream();

            audioRecord = new AudioRecord.Builder()
                    .setAudioSource(MediaRecorder.AudioSource.VOICE_RECOGNITION)
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

            isRecording = true;
            micButton.setImageResource(R.drawable.ic_mic_active);
            showToast("Recording started");

            recordingThread = new Thread(() -> {
                byte[] buffer = new byte[BUFFER_SIZE];
                audioRecord.startRecording();

                while (isRecording) {
                    int bytesRead = audioRecord.read(buffer, 0, buffer.length);
                    if (bytesRead > 0) {
                        synchronized (audioBuffer) {
                            audioBuffer.write(buffer, 0, bytesRead);
                        }
                    }
                }
            });
            recordingThread.start();

        } catch (SecurityException e) {
            Log.e(TAG, "Permission denied: " + e.getMessage());
            showToast("Permission denied for audio recording");
            resetRecordingState();
        } catch (Exception e) {
            Log.e(TAG, "Error starting recording: " + e.getMessage());
            showToast("Error starting audio recording");
            resetRecordingState();
        }
    }

    private void stopRecording() {
        if (!isRecording) return;

        try {
            isRecording = false;
            micButton.setImageResource(R.drawable.ic_mic);
            showToast("Processing audio...");

            // Stop the AudioRecord
            if (audioRecord != null) {
                audioRecord.stop();
                audioRecord.release();
            }

            // Wait for recording thread to finish
            if (recordingThread != null) {
                recordingThread.join();
            }

            // Get the final audio data
            byte[] audioData;
            synchronized (audioBuffer) {
                audioData = audioBuffer.toByteArray();
            }

            // Send data to backend only if we have audio
            if (audioData.length > 0) {
                Log.d(TAG, "Sending audio data, size: " + audioData.length + " bytes");
                sendAudioToBackend(audioData);
            } else {
                showToast("No audio recorded");
            }

        } catch (Exception e) {
            Log.e(TAG, "Error stopping recording: " + e.getMessage());
            showToast("Error processing audio");
        } finally {
            resetRecordingState();
        }
    }

    private void resetRecordingState() {
        isRecording = false;
        audioRecord = null;
        recordingThread = null;
        audioBuffer = null;
        micButton.setImageResource(R.drawable.ic_mic);
    }

    private void sendAudioToBackend(byte[] audioData) {
        RequestBody requestBody = new MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart("sample_rate", String.valueOf(SAMPLE_RATE))
                .addFormDataPart("audio_data", "audio.raw",
                        RequestBody.create(MediaType.parse("audio/raw"), audioData))
                .build();

        Request request = new Request.Builder()
                .url(BACKEND_URL)
                .post(requestBody)
                .build();

        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                Log.e(TAG, "Failed to send audio data: " + e.getMessage());
                showToast("Network error: " + e.getMessage());
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                try (ResponseBody responseBody = response.body()) {
                    if (!response.isSuccessful() || responseBody == null) {
                        Log.e(TAG, "Server error: " + response.code());
                        showToast("Server error: " + response.code());
                        return;
                    }

                    JSONObject result = new JSONObject(responseBody.string());
                    if ("success".equals(result.getString("status"))) {
                        String transcribedString = result.getString("text");
                        updateTranscribedText(transcribedString);
                        showToast("Transcription complete");
                    } else {
                        showToast("Transcription error: " + result.getString("error"));
                    }
                } catch (Exception e) {
                    Log.e(TAG, "Error processing response: " + e.getMessage());
                    showToast("Error processing response");
                }
            }
        });
    }

    private void updateTranscribedText(String newText) {
        if (getActivity() == null) return;

        getActivity().runOnUiThread(() -> {
            if (!TextUtils.isEmpty(newText)) {
                String currentText = transcribedText.getText().toString();
                String updatedText = TextUtils.isEmpty(currentText) ?
                        newText : currentText + "\n" + newText;
                transcribedText.setText(updatedText);
                transcribedText.setSelection(updatedText.length());
            }
        });
    }

    private void showToast(String message) {
        if (getActivity() == null) return;
        getActivity().runOnUiThread(() ->
                Toast.makeText(getActivity(), message, Toast.LENGTH_SHORT).show()
        );
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        if (isRecording) {
            stopRecording();
        }
    }
}