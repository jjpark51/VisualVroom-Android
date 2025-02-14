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

import org.json.JSONObject;

public class WearNotificationService extends Service {
    private static final String TAG = "WearNotificationService";
    private final IBinder binder = new LocalBinder();
    private static final String WEAR_PATH = "/message";
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

    public void sendMessageToWear(String message) {
        if (googleApiClient != null && googleApiClient.isConnected()) {
            PendingResult<NodeApi.GetConnectedNodesResult> nodes = Wearable.NodeApi.getConnectedNodes(googleApiClient);
            nodes.setResultCallback(result -> {
                for (Node node : result.getNodes()) {
                    PendingResult<MessageApi.SendMessageResult> messageResult =
                            Wearable.MessageApi.sendMessage(googleApiClient, node.getId(), WEAR_PATH, message.getBytes());

                    messageResult.setResultCallback(sendMessageResult -> {
                        if (!sendMessageResult.getStatus().isSuccess()) {
                            Log.e(TAG, "Failed to send message to wear: " + sendMessageResult.getStatus().getStatusCode());
                        } else {
                            Log.d(TAG, "Successfully sent message to wear");
                        }
                    });
                }
            });
        } else {
            Log.e(TAG, "GoogleApiClient not connected");
        }
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
            JSONObject alert = new JSONObject();
            alert.put("vehicle_type", vehicleType);
            alert.put("direction", direction);

            // Add your wear device communication logic here
            // For example, using Wearable.MessageClient to send the alert

            Log.d(TAG, "Alert sent to wear device: " + alert.toString());
        } catch (Exception e) {
            Log.e(TAG, "Error sending alert: " + e.getMessage());
        }
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