package com.example.brightbuds_app.services;

import android.util.Log;

import androidx.annotation.NonNull;

import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.auth.FirebaseAuth;


import java.util.HashMap;
import java.util.Map;

/**
 * Handles all writes from the local app into Firebase Firestore.
 * Used by ProgressService so that each child and module
 * always has an up to date progress and analytics document.
 */
public class FirebaseSyncService {

    private static final String TAG = "FirebaseSyncService";
    private final FirebaseFirestore db;

    public FirebaseSyncService() {
        db = FirebaseFirestore.getInstance();
    }

    /**
     * Store a summary of the latest progress for one child and one module.
     * This will be used by the parent dashboard and reports.
     */
    public void upsertChildProgress(
            @NonNull String childId,
            @NonNull String moduleId,
            int lastScore,
            int stars,
            int totalPlays,
            long totalTimeMs,
            boolean completedFlag
    ) {
        Map<String, Object> data = new HashMap<>();
        data.put("child_id", childId);
        data.put("module_id", moduleId);
        data.put("lastScore", lastScore);
        data.put("stars", stars);
        data.put("totalPlays", totalPlays);
        data.put("totalTimeMs", totalTimeMs);
        data.put("completedFlag", completedFlag);
        data.put("updatedAt", System.currentTimeMillis());

        String docId = childId + "_" + moduleId;

        db.collection("child_progress")
                .document(docId)
                .set(data)
                .addOnSuccessListener(unused ->
                        Log.d(TAG, "Progress written for " + docId))
                .addOnFailureListener(e ->
                        Log.e(TAG, "Error writing progress for " + docId, e));
    }

    /**
     * Store aggregated analytics for one child and one module.
     * These values come from AnalyticsSessionManager totals.
     */
    public void upsertChildAnalytics(
            @NonNull String childId,
            @NonNull String moduleId,
            int sessionCount,
            int totalScore,
            int totalCorrect,
            int totalAttempts,
            long totalTimeMs
    ) {
        double avgScore = sessionCount > 0 ? (double) totalScore / sessionCount : 0.0;
        double accuracy = totalAttempts > 0 ? (double) totalCorrect / totalAttempts : 0.0;
        double avgTimeMs = sessionCount > 0 ? (double) totalTimeMs / sessionCount : 0.0;

        String parentId = FirebaseAuth.getInstance().getUid();  // NEW

        Map<String, Object> data = new HashMap<>();
        data.put("child_id", childId);
        data.put("module_id", moduleId);
        data.put("parentId", parentId);
        data.put("sessionCount", sessionCount);
        data.put("totalScore", totalScore);
        data.put("totalCorrect", totalCorrect);
        data.put("totalAttempts", totalAttempts);
        data.put("totalTimeMs", totalTimeMs);
        data.put("avgScore", avgScore);
        data.put("accuracy", accuracy);
        data.put("avgTimeMs", avgTimeMs);
        data.put("updatedAt", System.currentTimeMillis());

        String docId = childId + "_" + moduleId;

        db.collection("child_analytics")
                .document(docId)
                .set(data)
                .addOnSuccessListener(unused ->
                        Log.d(TAG, "Analytics written for " + docId))
                .addOnFailureListener(e ->
                        Log.e(TAG, "Error writing analytics for " + docId, e));
    }

}
