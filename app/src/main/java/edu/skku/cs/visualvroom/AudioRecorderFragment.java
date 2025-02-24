package edu.skku.cs.visualvroom;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import com.airbnb.lottie.LottieAnimationView;
import com.google.android.material.floatingactionbutton.FloatingActionButton;

public class AudioRecorderFragment extends Fragment {
    private static final String TAG = "AudioRecorderFragment";
    private static final int RECORDING_DURATION_MS = 10000; // 10 seconds

    // UI Components
    private FloatingActionButton recordButton;
    private TextView statusText;
    private LottieAnimationView recordingAnimation;

    // State Management
    private enum RecordingState {
        IDLE,
        RECORDING,
        PROCESSING
    }
    private RecordingState currentState = RecordingState.IDLE;

    // Core Components
    private AudioRecorder audioRecorder;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private Runnable recordingTimeoutRunnable;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_audio_recorder, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        initializeViews(view);
        initializeComponents();
        setupClickListeners();
    }

    private void initializeViews(View view) {
        recordButton = view.findViewById(R.id.recordButton);
        statusText = view.findViewById(R.id.statusText);
        recordingAnimation = view.findViewById(R.id.recordingAnimation);

        // Set initial UI state
        updateUIState(RecordingState.IDLE);
    }

    private void initializeComponents() {
        audioRecorder = new AudioRecorder(requireContext());
        recordingTimeoutRunnable = () -> {
            if (currentState == RecordingState.RECORDING) {
                stopRecording();
            }
        };
    }

    private void setupClickListeners() {
        recordButton.setOnClickListener(v -> {
            switch (currentState) {
                case IDLE:
                    startRecording();
                    break;
                case RECORDING:
                    stopRecording();
                    break;
                case PROCESSING:
                    // Disable button during processing
                    break;
            }
        });
    }

    private void startRecording() {
        try {
            audioRecorder.startRecording();
            updateUIState(RecordingState.RECORDING);

            // Schedule auto-stop
            mainHandler.postDelayed(recordingTimeoutRunnable, RECORDING_DURATION_MS);

            Log.d(TAG, "Recording started successfully");
        } catch (SecurityException e) {
            Log.e(TAG, "Permission denied: " + e.getMessage());
            showError("Recording permission denied");
        } catch (Exception e) {
            Log.e(TAG, "Failed to start recording: " + e.getMessage());
            showError("Failed to start recording");
        }
    }

    private void stopRecording() {
        try {
            // Remove any pending auto-stop
            mainHandler.removeCallbacks(recordingTimeoutRunnable);

            // Stop the recording
            audioRecorder.stopRecording();
            updateUIState(RecordingState.PROCESSING);

            // Process the recording
            processRecording();

            Log.d(TAG, "Recording stopped successfully");
        } catch (Exception e) {
            Log.e(TAG, "Error stopping recording: " + e.getMessage());
            showError("Error stopping recording");
            updateUIState(RecordingState.IDLE);
        }
    }

    private void processRecording() {
        audioRecorder.sendToBackend(new AudioRecorder.AudioRecorderCallback() {
            @Override
            public void onSuccess(AudioRecorder.InferenceResult result) {
                mainHandler.post(() -> {
                    // Log inference details
                    Log.d(TAG, String.format("Inference result - %s from %s (confidence: %.2f)",
                            result.getVehicleType(),
                            result.getDirection(),
                            result.getConfidence()));

                    // Show result to user
                    String message = String.format("%s detected from %s",
                            result.getVehicleType(),
                            result.getDirection());
                    showToast(message);

                    // Handle high-confidence detection
                    if (result.getShouldNotify()) {
                        Log.d(TAG, "High confidence detection: " + message);
                        // Additional notification handling could be added here
                    }

                    updateUIState(RecordingState.IDLE);
                });
            }

            @Override
            public void onError(String error) {
                mainHandler.post(() -> {
                    handleProcessingError(error);
                });
            }
        });
    }

    private void handleProcessingError(String error) {
        Log.e(TAG, "Processing error: " + error);
        showError("Processing failed: " + error);
        updateUIState(RecordingState.IDLE);
    }

    private void updateUIState(RecordingState newState) {
        if (!isAdded()) return;

        currentState = newState;

        switch (newState) {
            case IDLE:
                recordButton.setImageResource(R.drawable.ic_mic);
                recordButton.setEnabled(true);
                statusText.setText("Ready to record");
                recordingAnimation.cancelAnimation();
                recordingAnimation.setVisibility(View.GONE);
                break;

            case RECORDING:
                recordButton.setImageResource(R.drawable.ic_stop);
                recordButton.setEnabled(true);
                statusText.setText("Recording...");
                recordingAnimation.setVisibility(View.VISIBLE);
                recordingAnimation.setAnimation(R.raw.recording_animation);
                recordingAnimation.playAnimation();
                break;

            case PROCESSING:
                recordButton.setEnabled(false);
                statusText.setText("Processing...");
                recordingAnimation.cancelAnimation();
                recordingAnimation.setVisibility(View.GONE);
                break;
        }
    }

    private void showError(String message) {
        if (isAdded()) {
            Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show();
        }
    }

    private void showToast(String message) {
        if (isAdded()) {
            Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    public void onDestroyView() {
        mainHandler.removeCallbacks(recordingTimeoutRunnable);
        if (currentState == RecordingState.RECORDING) {
            audioRecorder.stopRecording();
        }
        super.onDestroyView();
    }
}