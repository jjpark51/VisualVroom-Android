package edu.skku.cs.visualvroom;

import android.Manifest;
import android.app.Service;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Binder;
import android.os.IBinder;
import android.util.Log;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.core.content.ContextCompat;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.os.Build;
import android.os.VibrationEffect;
import android.os.Vibrator;

import com.google.android.gms.wearable.MessageClient;
import com.google.android.gms.wearable.Node;
import com.google.android.gms.wearable.Wearable;

import org.json.JSONObject;



public class WearNotificationService extends Service {
    private static final String TAG = "WearNotificationService";
    private static final String CHANNEL_ID = "VehicleAlerts";
    private static final String PATH_VEHICLE_ALERT = "/vehicle_alert";
    private static final int NOTIFICATION_ID = 1;

    private MessageClient messageClient;
    private Vibrator vibrator;

    @Override
    public void onCreate() {
        super.onCreate();
        createNotificationChannel();
        messageClient = Wearable.getMessageClient(this);
        vibrator = (Vibrator) getSystemService(VIBRATOR_SERVICE);
    }

    private boolean checkNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            return ContextCompat.checkSelfPermission(this,
                    Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED;
        }
        return true; // Permission not required for older Android versions
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    "Vehicle Alerts",
                    NotificationManager.IMPORTANCE_HIGH
            );
            channel.setDescription("Alerts for approaching emergency vehicles");
            NotificationManager notificationManager = getSystemService(NotificationManager.class);
            notificationManager.createNotificationChannel(channel);
        }
    }

    public void sendAlert(String vehicleType, String direction) {
        try {
            JSONObject alert = new JSONObject();
            alert.put("vehicle_type", vehicleType);
            alert.put("direction", direction);
            alert.put("vibration_pattern", getVibrationPattern(vehicleType));

            // Vibrate the phone
            vibrate(vehicleType);

            // Send to all connected wear devices
            Wearable.getNodeClient(this).getConnectedNodes().addOnSuccessListener(nodes -> {
                for (Node node : nodes) {
                    messageClient.sendMessage(
                            node.getId(),
                            PATH_VEHICLE_ALERT,
                            alert.toString().getBytes()
                    ).addOnFailureListener(e ->
                            Log.e(TAG, "Failed to send message to node " + node.getId(), e)
                    );
                }
            });

            // Show notification if permission is granted
            if (checkNotificationPermission()) {
                showNotification(vehicleType, direction);
            }

        } catch (Exception e) {
            Log.e(TAG, "Error sending alert to wear device: " + e.getMessage());
        }
    }

    private void vibrate(String vehicleType) {
        if (vibrator != null) {
            long[] pattern = getVibrationPattern(vehicleType);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator.vibrate(VibrationEffect.createWaveform(pattern, -1));
            } else {
                vibrator.vibrate(pattern, -1);
            }
        }
    }

    private void showNotification(String vehicleType, String direction) {
        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(R.drawable.warning_icon) // Using a generic warning icon instead
                .setContentTitle(vehicleType + " Alert")
                .setContentText("Approaching from " + direction)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setAutoCancel(true)
                .setVibrate(getVibrationPattern(vehicleType));

        try {
            NotificationManagerCompat.from(this).notify(NOTIFICATION_ID, builder.build());
        } catch (SecurityException e) {
            Log.e(TAG, "Permission denied for showing notification", e);
        }
    }

    private long[] getVibrationPattern(String vehicleType) {
        switch (vehicleType.toUpperCase()) {
            case "AMBULANCE":
                return new long[]{0, 200, 100, 200};  // Short-short pattern
            case "POLICE":
                return new long[]{0, 400, 200, 400};  // Medium-medium pattern
            case "FIRETRUCK":
                return new long[]{0, 800, 400, 800};  // Long-long pattern
            default:
                return new long[]{0, 500};  // Single vibration
        }
    }

    private final IBinder binder = new LocalBinder();

    @Override
    public IBinder onBind(Intent intent) {
        return binder;
    }

    public class LocalBinder extends Binder {
        WearNotificationService getService() {
            return WearNotificationService.this;
        }
    }
}