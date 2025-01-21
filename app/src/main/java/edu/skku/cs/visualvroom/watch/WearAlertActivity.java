package edu.skku.cs.visualvroom.watch;

import android.app.Activity;
import android.os.Bundle;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.widget.TextView;
import android.util.Log;

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
    private MessageClient messageClient;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_wear_alert);

        vibrator = (Vibrator) getSystemService(VIBRATOR_SERVICE);
        alertText = findViewById(R.id.alertText);

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
            String alertMessage = String.format("%s\nApproaching from %s",
                    vehicleType.toUpperCase(),
                    direction.toUpperCase());
            alertText.setText(alertMessage);

            // Trigger vibration
            if (vibrator != null && pattern != null) {
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                    vibrator.vibrate(VibrationEffect.createWaveform(pattern, -1));
                } else {
                    // Fallback for older devices
                    vibrator.vibrate(pattern, -1);
                }
            }
        });
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
        messageClient.removeListener(this);
        super.onDestroy();
    }
}