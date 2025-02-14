package edu.skku.cs.visualvroom;

import android.Manifest;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.os.IBinder;
import android.util.Log;
import android.view.View;
import android.widget.ImageView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.viewpager2.adapter.FragmentStateAdapter;
import androidx.viewpager2.widget.ViewPager2;

import com.google.android.material.tabs.TabLayout;
import com.google.android.material.tabs.TabLayoutMediator;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

public class MainActivity extends AppCompatActivity {
    private static final String TAG = "MainActivity";
    private static final int PERMISSION_REQUEST_CODE = 1;

    private ViewPager2 viewPager;
    private TabLayout tabLayout;
    private WearNotificationService wearService;
    private boolean isServiceBound = false;
    private BroadcastReceiver messageReceiver;
    private boolean isRecording = false;

    // Reference to fragments
    private SoundDetectionFragment soundDetectionFragment;
    private SpeechToTextFragment speechToTextFragment;

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

        // Initialize ViewPager and TabLayout
        viewPager = findViewById(R.id.viewPager);
        tabLayout = findViewById(R.id.tabLayout);

        // Set up the adapter
        ViewPagerAdapter adapter = new ViewPagerAdapter(this);
        viewPager.setAdapter(adapter);

        // Connect TabLayout with ViewPager2
        new TabLayoutMediator(tabLayout, viewPager,
                (tab, position) -> {
                    tab.setText(position == 0 ? "Sound Detection" : "Speech to Text");
                }
        ).attach();

        // Register broadcast receiver
        messageReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                if ("AUDIO_INFERENCE_RESULT".equals(intent.getAction())) {
                    String result = intent.getStringExtra("result");
                    handleInferenceResult(result);
                }
            }
        };

        registerReceiver(messageReceiver, new IntentFilter("AUDIO_INFERENCE_RESULT"),
                Context.RECEIVER_NOT_EXPORTED);

        // Check permissions before starting any services
        checkAndRequestPermissions();
    }

    private class ViewPagerAdapter extends FragmentStateAdapter {
        public ViewPagerAdapter(FragmentActivity activity) {
            super(activity);
        }

        @Override
        public Fragment createFragment(int position) {
            if (position == 0) {
                soundDetectionFragment = new SoundDetectionFragment();
                return soundDetectionFragment;
            } else {
                speechToTextFragment = new SpeechToTextFragment();
                return speechToTextFragment;
            }
        }

        @Override
        public int getItemCount() {
            return 2;
        }
    }

    private void checkAndRequestPermissions() {
        List<String> permissions = new ArrayList<>();

        // Basic permissions
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
                != PackageManager.PERMISSION_GRANTED) {
            permissions.add(Manifest.permission.RECORD_AUDIO);
        }

        // Location permissions
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {
            permissions.add(Manifest.permission.ACCESS_FINE_LOCATION);
        }
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {
            permissions.add(Manifest.permission.ACCESS_COARSE_LOCATION);
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                    != PackageManager.PERMISSION_GRANTED) {
                permissions.add(Manifest.permission.POST_NOTIFICATIONS);
            }
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.FOREGROUND_SERVICE_MICROPHONE)
                    != PackageManager.PERMISSION_GRANTED) {
                permissions.add(Manifest.permission.FOREGROUND_SERVICE_MICROPHONE);
            }
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.FOREGROUND_SERVICE_LOCATION)
                    != PackageManager.PERMISSION_GRANTED) {
                permissions.add(Manifest.permission.FOREGROUND_SERVICE_LOCATION);
            }
        }

        if (!permissions.isEmpty()) {
            ActivityCompat.requestPermissions(this,
                    permissions.toArray(new String[0]),
                    PERMISSION_REQUEST_CODE);
        } else {
            startWearService();
        }
    }

    private boolean checkAllPermissionsGranted() {
        boolean allGranted = true;

        allGranted &= ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
                == PackageManager.PERMISSION_GRANTED;
        allGranted &= ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                == PackageManager.PERMISSION_GRANTED;
        allGranted &= ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION)
                == PackageManager.PERMISSION_GRANTED;

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            allGranted &= ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                    == PackageManager.PERMISSION_GRANTED;
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            allGranted &= ContextCompat.checkSelfPermission(this, Manifest.permission.FOREGROUND_SERVICE_MICROPHONE)
                    == PackageManager.PERMISSION_GRANTED;
            allGranted &= ContextCompat.checkSelfPermission(this, Manifest.permission.FOREGROUND_SERVICE_LOCATION)
                    == PackageManager.PERMISSION_GRANTED;
        }

        return allGranted;
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == PERMISSION_REQUEST_CODE) {
            boolean allPermissionsGranted = true;
            for (int result : grantResults) {
                if (result != PackageManager.PERMISSION_GRANTED) {
                    allPermissionsGranted = false;
                    break;
                }
            }

            if (allPermissionsGranted) {
                startWearService();
                Toast.makeText(this, "All permissions granted", Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(this,
                        "Required permissions not granted. The app may not function correctly.",
                        Toast.LENGTH_LONG).show();
            }
        }
    }

    private void startWearService() {
        Intent serviceIntent = new Intent(this, WearNotificationService.class);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(serviceIntent);
        } else {
            startService(serviceIntent);
        }
        bindService(serviceIntent, serviceConnection, Context.BIND_AUTO_CREATE);
    }

    public void startRecording() {
        if (checkAllPermissionsGranted()) {
            Intent recordIntent = new Intent(this, AudioRecordingService.class);
            recordIntent.setAction("START_RECORDING");
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(recordIntent);
            } else {
                startService(recordIntent);
            }
            isRecording = true;
        } else {
            checkAndRequestPermissions();
        }
    }

    public void stopRecording() {
        Intent recordIntent = new Intent(this, AudioRecordingService.class);
        recordIntent.setAction("STOP_RECORDING");
        startService(recordIntent);
        isRecording = false;
    }

    private void handleInferenceResult(String result) {
        try {
            JSONObject resultJson = new JSONObject(result);
            if (resultJson.has("should_notify") && resultJson.getBoolean("should_notify")) {
                String vehicleType = resultJson.getString("vehicle_type");
                String direction = resultJson.getString("direction");

                // Update the sound detection fragment UI
                if (soundDetectionFragment != null) {
                    soundDetectionFragment.updateDetection(vehicleType, direction);
                }

                // Send alert to watch if service is bound
                if (isServiceBound && wearService != null) {
                    wearService.sendAlert(vehicleType, direction);
                }
            }
        } catch (JSONException e) {
            Log.e(TAG, "Error parsing inference result: " + e.getMessage());
        }
    }

    @Override
    protected void onDestroy() {
        if (isRecording) {
            stopRecording();
        }

        if (isServiceBound) {
            unbindService(serviceConnection);
            isServiceBound = false;
        }

        if (messageReceiver != null) {
            unregisterReceiver(messageReceiver);
        }

        super.onDestroy();
    }
}