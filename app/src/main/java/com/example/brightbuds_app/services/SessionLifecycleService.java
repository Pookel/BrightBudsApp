package com.example.brightbuds_app.services;

import android.app.Service;
import android.content.Intent;
import android.os.IBinder;
import android.util.Log;

import androidx.annotation.Nullable;

/**
 * SessionLifecycleService
 * Tracks app session duration for analytics.
 * Can be extended to send data to AnalyticsSessionManager or Firebase.
 */
public class SessionLifecycleService extends Service {

    private static final String TAG = "SessionLifecycleService";
    private long sessionStartTime = 0L;

    @Override
    public void onCreate() {
        super.onCreate();
        sessionStartTime = System.currentTimeMillis();
        Log.d(TAG, "Session service created, startTime=" + sessionStartTime);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.d(TAG, "Session service started");

        // You can add extra session tracking logic here if needed

        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        long sessionDuration = 0L;
        if (sessionStartTime > 0L) {
            sessionDuration = System.currentTimeMillis() - sessionStartTime;
        }
        Log.d(TAG, "Session ended. Duration: " + sessionDuration + " ms");

        // Save analytics somewhere (local or Firebase)
        saveSessionAnalytics(sessionDuration);
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        // Not a bound service
        return null;
    }

    // Optional: Method to save session analytics
    private void saveSessionAnalytics(long durationMs) {
        // TODO: Implement your analytics saving logic here,
        // for example via AnalyticsSessionManager or ProgressService.
        Log.d(TAG, "Saving session analytics: " + durationMs + " ms");
    }
}
