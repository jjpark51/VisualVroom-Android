package edu.skku.cs.visualvroom.watch;

import android.app.Activity;
import android.os.Bundle;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.view.View;
import android.widget.TextView;
import android.util.Log;

import com.airbnb.lottie.LottieAnimationView;
import com.google.android.gms.wearable.MessageClient;
import com.google.android.gms.wearable.MessageEvent;
import com.google.android.gms.wearable.Wearable;

import org.json.JSONObject;
import org.json.JSONArray;

import edu.skku.cs.visualvroom.R;

public class WearAlertActivity extends Activity implements MessageClient.OnMessageReceivedListener {
    private static final String TAG = "WearAlertActivity";
    private static final String PATH_VEHICLE_ALERT = "/vehicle_alert";

    private Vibrator vibrator;
    private TextView alertText;
    private LottieAnimationView alertAnimation;
    private View leftIndicator;
    private View rightIndicator;
    private MessageClient messageClient;
    private boolean animationsAvailable = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_wear_alert);

        vibrator = (Vibrator) getSystemService(VIBRATOR_SERVICE);
        alertText = findViewById(R.id.alertText);
        alertAnimation = findViewById(R.id.alertAnimation);
        leftIndicator = findViewById(R.id.leftIndicator);
        rightIndicator = findViewById(R.id.rightIndicator);

        // Initialize with invisible animation and direction indicators
        alertAnimation.setVisibility(View.GONE);
        leftIndicator.setVisibility(View.GONE);
        rightIndicator.setVisibility(View.GONE);

        // Check if animations are available by trying to load one
        try {
            // Try to verify if animations exist
            if (getResources().getIdentifier("siren", "raw", getPackageName()) != 0) {
                animationsAvailable = true;
                Log.d(TAG, "Animations are available");
            } else {
                Log.d(TAG, "Animations are not available in raw resources");
            }
        } catch (Exception e) {
            Log.e(TAG, "Error checking animations: " + e.getMessage());
            animationsAvailable = false;
        }

        // Initialize the MessageClient
        messageClient = Wearable.getMessageClient(this);

        // Register message listener
        messageClient.addListener(this);
    }

    @Override
    public void onMessageReceived(MessageEvent messageEvent) {
        if (messageEvent.getPath().equals(PATH_VEHICLE_ALERT)) {
            try {
                String message = new String(messageEvent.getData());
                Log.d(TAG, "Received message: " + message);

                JSONObject alert = new JSONObject(message);

                String vehicleType = alert.getString("vehicle_type");
                String direction = alert.getString("direction");
                long[] pattern = parseVibrationPattern(alert.getJSONArray("vibration_pattern"));

                handleAlert(vehicleType, direction, pattern);
            } catch (Exception e) {
                Log.e(TAG, "Error processing message: " + e.getMessage());
            }
        }
    }

    private void handleAlert(String vehicleType, String direction, long[] pattern) {
        runOnUiThread(() -> {
            // Update UI with large, easily readable text
            String alertMessage = String.format("%s\nFrom %s",
                    vehicleType.toUpperCase(),
                    direction.equalsIgnoreCase("L") ? "LEFT" :
                            direction.equalsIgnoreCase("R") ? "RIGHT" : direction);
            alertText.setText(alertMessage);

            // Show the appropriate direction indicator
            leftIndicator.setVisibility(View.GONE);
            rightIndicator.setVisibility(View.GONE);
            if (direction.equalsIgnoreCase("L")) {
                leftIndicator.setVisibility(View.VISIBLE);
            } else if (direction.equalsIgnoreCase("R")) {
                rightIndicator.setVisibility(View.VISIBLE);
            }

            // Set and play the appropriate animation only if animations are available
            if (animationsAvailable) {
                try {
                    int animationResource = getAnimationResource(vehicleType);
                    if (animationResource != 0) {
                        alertAnimation.setAnimation(animationResource);
                        alertAnimation.setVisibility(View.VISIBLE);
                        alertAnimation.setRepeatCount(3); // Play the animation 3 times
                        alertAnimation.playAnimation();
                    } else {
                        alertAnimation.setVisibility(View.GONE);
                    }
                } catch (Exception e) {
                    Log.e(TAG, "Error loading animation: " + e.getMessage());
                    alertAnimation.setVisibility(View.GONE);
                }
            } else {
                // No animations available, just hide the animation view
                alertAnimation.setVisibility(View.GONE);
            }

            // Trigger vibration
            if (vibrator != null && pattern != null) {
                try {
                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                        vibrator.vibrate(VibrationEffect.createWaveform(pattern, -1));
                    } else {
                        // Fallback for older devices
                        vibrator.vibrate(pattern, -1);
                    }
                } catch (Exception e) {
                    Log.e(TAG, "Error during vibration: " + e.getMessage());
                }
            }
        });
    }

    private int getAnimationResource(String vehicleType) {
        try {
            switch (vehicleType.toLowerCase()) {
                case "siren":
                    return getResources().getIdentifier("siren", "raw", getPackageName());
                case "bike":
                    return getResources().getIdentifier("bike", "raw", getPackageName());
                case "horn":
                    return getResources().getIdentifier("car_horn", "raw", getPackageName());
                default:
                    return 0;
            }
        } catch (Exception e) {
            Log.e(TAG, "Error getting animation resource: " + e.getMessage());
            return 0;
        }
    }

    private long[] parseVibrationPattern(JSONArray patternArray) {
        try {
            long[] pattern = new long[patternArray.length()];
            for (int i = 0; i < patternArray.length(); i++) {
                pattern[i] = patternArray.getLong(i);
            }
            return pattern;
        } catch (Exception e) {
            Log.e(TAG, "Error parsing vibration pattern: " + e.getMessage());
            return null;
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        messageClient.addListener(this);
    }

    @Override
    protected void onPause() {
        messageClient.removeListener(this);
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        if (alertAnimation != null) {
            alertAnimation.cancelAnimation();
        }
        messageClient.removeListener(this);
        super.onDestroy();
    }
}