package edu.skku.cs.visualvroom;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.fragment.app.Fragment;
import androidx.core.content.ContextCompat;

import com.airbnb.lottie.LottieAnimationView;

public class SoundDetectionFragment extends Fragment {
    private View leftIndicator;
    private View rightIndicator;
    private LottieAnimationView soundAnimation;
    private TextView soundText;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_sound_detection, container, false);

        leftIndicator = view.findViewById(R.id.leftIndicator);
        rightIndicator = view.findViewById(R.id.rightIndicator);
        soundAnimation = view.findViewById(R.id.soundAnimation);
        soundText = view.findViewById(R.id.soundText);

        // Start recording when the fragment is created
        if (getActivity() instanceof MainActivity) {
            ((MainActivity) getActivity()).startRecording();
        }

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

    private int getAnimationResource(String vehicleType) {
        switch (vehicleType.toLowerCase()) {
            case "ambulance":
                return R.raw.siren;
            case "police":
                return R.raw.siren;
            case "firetruck":
                return R.raw.siren;
            case "horn":
                return R.raw.car_horns;
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
        // Stop recording when the fragment is destroyed
        if (getActivity() instanceof MainActivity) {
            ((MainActivity) getActivity()).stopRecording();
        }
    }
}