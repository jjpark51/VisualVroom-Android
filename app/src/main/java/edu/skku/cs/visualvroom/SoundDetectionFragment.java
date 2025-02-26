package edu.skku.cs.visualvroom;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.fragment.app.Fragment;
import androidx.core.content.ContextCompat;

import com.airbnb.lottie.LottieAnimationView;
import com.google.android.material.floatingactionbutton.FloatingActionButton;

public class SoundDetectionFragment extends Fragment {
    private View leftIndicator;
    private View rightIndicator;
    private LottieAnimationView soundAnimation;
    private TextView soundText;
    private FloatingActionButton micButton;
    private boolean isRecording = false;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_sound_detection, container, false);

        leftIndicator = view.findViewById(R.id.leftIndicator);
        rightIndicator = view.findViewById(R.id.rightIndicator);
        soundAnimation = view.findViewById(R.id.soundAnimation);
        soundText = view.findViewById(R.id.soundText);
        micButton = view.findViewById(R.id.micButton);

        // Set up mic button click listener
        micButton.setOnClickListener(v -> {
            if (getActivity() instanceof MainActivity) {
                MainActivity activity = (MainActivity) getActivity();
                if (!isRecording) {
                    // Start recording
                    activity.startRecording();
                    micButton.setImageResource(R.drawable.ic_mic_active);
                    isRecording = true;
                } else {
                    // Stop recording
                    activity.stopRecording();
                    micButton.setImageResource(R.drawable.ic_mic);
                    isRecording = false;
                    // Reset UI elements
                    resetUI();
                }
            }
        });

        return view;
    }

    public void updateDetection(String vehicleType, String direction) {
        if (getActivity() == null) return;

        getActivity().runOnUiThread(() -> {
            // Reset background colors
            leftIndicator.setBackgroundColor(ContextCompat.getColor(getActivity(), android.R.color.transparent));
            rightIndicator.setBackgroundColor(ContextCompat.getColor(getActivity(), android.R.color.transparent));

            // Set the appropriate indicator
            int highlightColor = ContextCompat.getColor(getActivity(), R.color.highlight_color);
            if ("Left".equalsIgnoreCase(direction)) {
                leftIndicator.setBackgroundColor(highlightColor);
            } else if ("Right".equalsIgnoreCase(direction)) {
                rightIndicator.setBackgroundColor(highlightColor);
            }

            // Update animation based on vehicle type
            int animationResource = getAnimationResource(vehicleType);
            if (animationResource != 0) {
                soundAnimation.setAnimation(animationResource);
                soundAnimation.setVisibility(View.VISIBLE);
                soundAnimation.playAnimation();
            }

            // Update text
            soundText.setText(vehicleType);
            soundText.setVisibility(View.VISIBLE);
        });
    }

    private void resetUI() {
        if (getActivity() == null) return;

        getActivity().runOnUiThread(() -> {
            // Reset indicators
            leftIndicator.setBackgroundColor(ContextCompat.getColor(getActivity(), android.R.color.transparent));
            rightIndicator.setBackgroundColor(ContextCompat.getColor(getActivity(), android.R.color.transparent));

            // Reset animation
            if (soundAnimation != null) {
                soundAnimation.cancelAnimation();
                soundAnimation.setVisibility(View.GONE);
            }

            // Reset text
            soundText.setText("");
            soundText.setVisibility(View.GONE);
        });
    }

    private int getAnimationResource(String vehicleType) {
        switch (vehicleType.toLowerCase()) {
            case "Siren":
                return R.raw.car_horn;
            case "Bike":
                return R.raw.car_horn;
            case "Horn":
                return R.raw.bike;
            default:
                return 0;
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        if (soundAnimation != null) {
            soundAnimation.cancelAnimation();
        }
        // Stop recording if it's still running
        if (isRecording && getActivity() instanceof MainActivity) {
            ((MainActivity) getActivity()).stopRecording();
        }
    }
}