package edu.skku.cs.visualvroom;

import android.Manifest;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Binder;
import android.os.Build;
import android.os.IBinder;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.util.Log;
import androidx.core.app.NotificationCompat;
import androidx.core.content.ContextCompat;

import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.common.api.PendingResult;
import com.google.android.gms.wearable.MessageApi;
import com.google.android.gms.wearable.MessageClient;
import com.google.android.gms.wearable.Node;
import com.google.android.gms.wearable.NodeApi;
import com.google.android.gms.wearable.Wearable;

import org.json.JSONArray;
import org.json.JSONObject;

public class WearNotificationService extends Service {
    private static final String TAG = "WearNotificationService";
    private final IBinder binder = new LocalBinder();
    private static final String PATH_VEHICLE_ALERT = "/vehicle_alert";
    private GoogleApiClient googleApiClient;

    public class LocalBinder extends Binder {
        WearNotificationService getService() {
            return WearNotificationService.this;
        }
    }

    @Override
    public void onCreate() {
        super.onCreate();
        googleApiClient = new GoogleApiClient.Builder(this)
                .addApi(Wearable.API)
                .build();
        googleApiClient.connect();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return binder;
    }

    @Override
    public void onDestroy() {
        if (googleApiClient != null && googleApiClient.isConnected()) {
            googleApiClient.disconnect();
        }
        super.onDestroy();
    }

    public void sendAlert(String vehicleType, String direction) {
        try {
            // Create the alert JSON with all necessary information
            JSONObject alert = new JSONObject();
            alert.put("vehicle_type", vehicleType);
            alert.put("direction", direction);

            // Add vibration pattern based on the type of vehicle
            JSONArray vibrationPattern = createVibrationPattern(vehicleType);
            alert.put("vibration_pattern", vibrationPattern);

            // Actually send the alert to the wear device
            if (googleApiClient != null && googleApiClient.isConnected()) {
                final String alertJson = alert.toString();

                // Get all connected nodes (watch devices)
                PendingResult<NodeApi.GetConnectedNodesResult> nodes =
                        Wearable.NodeApi.getConnectedNodes(googleApiClient);

                nodes.setResultCallback(result -> {
                    for (Node node : result.getNodes()) {
                        // Send the message to each connected watch
                        Wearable.MessageApi.sendMessage(
                                        googleApiClient,
                                        node.getId(),
                                        PATH_VEHICLE_ALERT,
                                        alertJson.getBytes())
                                .setResultCallback(sendMessageResult -> {
                                    if (sendMessageResult.getStatus().isSuccess()) {
                                        Log.d(TAG, "Alert successfully sent to watch: " + node.getDisplayName());
                                    } else {
                                        Log.e(TAG, "Failed to send alert to watch: " +
                                                sendMessageResult.getStatus().getStatusCode());
                                    }
                                });
                    }
                });

                Log.d(TAG, "Alert prepared for watch: " + alertJson);
            } else {
                Log.e(TAG, "GoogleApiClient not connected, can't send alert to watch");
            }
        } catch (Exception e) {
            Log.e(TAG, "Error sending alert: " + e.getMessage());
        }
    }

    // Create different vibration patterns based on vehicle type
    private JSONArray createVibrationPattern(String vehicleType) throws Exception {
        JSONArray pattern = new JSONArray();

        switch (vehicleType.toLowerCase()) {
            case "siren":
                // Urgent pattern - short pulses
                pattern.put(0L);    // Start immediately
                pattern.put(100L);  // Vibrate
                pattern.put(100L);  // Pause
                pattern.put(100L);  // Vibrate
                pattern.put(100L);  // Pause
                pattern.put(300L);  // Longer vibrate
                pattern.put(200L);  // Pause
                pattern.put(300L);  // Longer vibrate
                break;

            case "bike":
                // Moderate pattern - medium pulses
                pattern.put(0L);    // Start immediately
                pattern.put(200L);  // Vibrate
                pattern.put(200L);  // Pause
                pattern.put(200L);  // Vibrate
                pattern.put(500L);  // Longer pause
                pattern.put(200L);  // Vibrate
                break;

            case "horn":
                // Alert pattern - longer pulses
                pattern.put(0L);    // Start immediately
                pattern.put(400L);  // Vibrate
                pattern.put(200L);  // Pause
                pattern.put(400L);  // Vibrate
                break;

            default:
                // Default pattern - single pulse
                pattern.put(0L);    // Start immediately
                pattern.put(300L);  // Vibrate
        }

        return pattern;
    }

    // Add this if you're using this as a Foreground Service
    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        // Create notification channel for Android O and above
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    "wear_service_channel",
                    "Wear Service Channel",
                    NotificationManager.IMPORTANCE_LOW);

            NotificationManager notificationManager = getSystemService(NotificationManager.class);
            notificationManager.createNotificationChannel(channel);
        }

        // Create the notification
        Notification notification = new NotificationCompat.Builder(this, "wear_service_channel")
                .setContentTitle("VisualVroom Service")
                .setContentText("Running...")
                .setSmallIcon(R.drawable.ic_notification)
                .build();

        try {
            startForeground(1, notification);
        } catch (SecurityException e) {
            Log.e(TAG, "Failed to start foreground service: " + e.getMessage());
            stopSelf();
            return START_NOT_STICKY;
        }
        return START_STICKY;
    }
}