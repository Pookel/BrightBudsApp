package com.example.brightbuds_app.utils;

import android.content.Context;
import android.util.Log;

import com.example.brightbuds_app.services.DatabaseHelper;

/**
 * Helper class for game session analytics.
 *
 * Responsibilities:
 *  - Tracks session start time for one module and one child
 *  - Accepts final game metrics on endSession
 *  - Writes to local SQLite analytics tables:
 *      game_sessions and child_analytics
 *
 * Cloud progress still uses ProgressService and the child_progress
 * collection. This class focuses on local analytics only.
 */
public class AnalyticsSessionManager {

    private static final String TAG = "AnalyticsSessionManager";

    private final Context appContext;
    private final String childId;
    private final String moduleId;
    private final DatabaseHelper localDb;

    private long sessionStartMs = 0L;
    private boolean sessionActive = false;

    public AnalyticsSessionManager(Context context, String childId, String moduleId) {
        this.appContext = context.getApplicationContext();
        this.childId = childId;
        this.moduleId = moduleId;
        this.localDb = new DatabaseHelper(appContext);
    }

    /**
     * Starts timing for a new analytics session.
     * Safe to call even if no child id is available.
     */
    public void startSession() {
        sessionStartMs = System.currentTimeMillis();
        sessionActive = true;

        Log.d(
                TAG,
                "startSession child=" + childId
                        + " module=" + moduleId
                        + " timeMs=" + sessionStartMs
        );
    }

    /**
     * Ends current session and logs aggregated metrics.
     * Also inserts one row into game_sessions and updates child_analytics.
     *
     * @param finalScore    final score value for the session
     * @param totalCorrect  total number of correct answers or matches
     * @param totalAttempts total number of attempts
     * @param stars         number of stars earned in the session
     * @param completedFlag 1 if module run is considered completed, 0 otherwise
     */
    public void endSession(int finalScore,
                           int totalCorrect,
                           int totalAttempts,
                           int stars,
                           int completedFlag) {

        if (!sessionActive) {
            Log.d(TAG, "endSession called but session not active. Ignoring.");
            return;
        }

        long now = System.currentTimeMillis();
        long durationMs = Math.max(0L, now - sessionStartMs);
        sessionActive = false;

        Log.d(
                TAG,
                "endSession child=" + childId
                        + " module=" + moduleId
                        + " durationMs=" + durationMs
                        + " score=" + finalScore
                        + " totalCorrect=" + totalCorrect
                        + " totalAttempts=" + totalAttempts
                        + " stars=" + stars
                        + " completed=" + completedFlag
        );

        if (childId == null || moduleId == null) {
            Log.w(TAG, "Cannot persist analytics session. childId or moduleId is null.");
            return;
        }

        try {
            localDb.recordGameSession(
                    childId,
                    moduleId,
                    sessionStartMs,
                    now,
                    finalScore,
                    totalCorrect,
                    totalAttempts,
                    stars,
                    completedFlag > 0 ? 1 : 0
            );
        } catch (Exception e) {
            Log.e(TAG, "Error saving analytics session to local DB", e);
        }
    }

    /** Cancels a session without recording it. */
    public void cancelSession() {
        sessionActive = false;
        sessionStartMs = 0L;
        Log.d(TAG, "Analytics session cancelled");
    }

    public boolean isSessionActive() {
        return sessionActive;
    }
}
