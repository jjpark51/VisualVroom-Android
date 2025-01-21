package edu.skku.cs.visualvroom;

import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.IBinder;
import android.util.Log;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import org.json.JSONException;
import org.json.JSONObject;

public class MainActivity extends AppCompatActivity {
    private static final String TAG = "MainActivity";
    private WearNotificationService wearService;
    private boolean isServiceBound = false;
    private BroadcastReceiver messageReceiver;

    // Service connection
    private final ServiceConnection serviceConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName className, IBinder service) {
            WearNotificationService.LocalBinder binder = (WearNotificationService.LocalBinder) service;
            wearService = binder.getService();
            isServiceBound = true;
            Log.d(TAG, "WearNotificationService bound successfully");
        }

        @Override
        public void onServiceDisconnected(ComponentName arg0) {
            isServiceBound = false;
            wearService = null;
            Log.d(TAG, "WearNotificationService disconnected");
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Start and bind to the WearNotificationService
        Intent serviceIntent = new Intent(this, WearNotificationService.class);
        startService(serviceIntent);
        bindService(serviceIntent, serviceConnection, Context.BIND_AUTO_CREATE);

        // Register broadcast receiver for WebSocket messages
        messageReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                if ("AUDIO_INFERENCE_RESULT".equals(intent.getAction())) {
                    String result = intent.getStringExtra("result");
                    handleInferenceResult(result);
                }
            }
        };

        registerReceiver(messageReceiver, new IntentFilter("AUDIO_INFERENCE_RESULT"));
    }

    private void handleInferenceResult(String resultJson) {
        try {
            JSONObject result = new JSONObject(resultJson);
            if (result.has("should_notify") && result.getBoolean("should_notify")) {
                String vehicleType = result.getString("vehicle_type");
                String direction = result.getString("direction");

                // Send alert to watch if service is bound
                if (isServiceBound && wearService != null) {
                    wearService.sendAlert(vehicleType, direction);
                } else {
                    Log.e(TAG, "WearNotificationService not bound, cannot send alert");
                }

                // Update UI
                updateAlertUI(vehicleType, direction);
            }
        } catch (JSONException e) {
            Log.e(TAG, "Error parsing inference result: " + e.getMessage());
        }
    }

    private void updateAlertUI(String vehicleType, String direction) {
        runOnUiThread(() -> {
            String alertMessage = vehicleType + " approaching from " + direction;
            Toast.makeText(this, alertMessage, Toast.LENGTH_SHORT).show();
        });
    }

    @Override
    protected void onDestroy() {
        // Unbind from service
        if (isServiceBound) {
            unbindService(serviceConnection);
            isServiceBound = false;
        }

        // Unregister broadcast receiver
        if (messageReceiver != null) {
            unregisterReceiver(messageReceiver);
        }

        super.onDestroy();
    }
}