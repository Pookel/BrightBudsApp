package com.example.brightbuds_app.services;

import android.util.Log;

import com.example.brightbuds_app.interfaces.DataCallbacks;
import com.example.brightbuds_app.models.ChildProfile;
import com.example.brightbuds_app.utils.Constants;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.SetOptions;

import java.util.HashMap;
import java.util.Map;

/**
 * Handles Firestore operations for child profiles.
 * Sends the fields needed for parent views and reports.
 * Local caching is done through ChildProfileDAO.
 */
public class ChildProfileService {

    private static final String TAG = "ChildProfileService";

    private final FirebaseFirestore db;
    private final FirebaseAuth auth;

    public ChildProfileService() {
        db = FirebaseFirestore.getInstance();
        auth = FirebaseAuth.getInstance();
    }

    /**
     * Updates or creates a child profile document for the current parent.
     * Document path: child_profiles/{childId}
     */
    public void updateChildProfile(ChildProfile child,
                                   DataCallbacks.GenericCallback callback) {

        if (child == null || child.getChildId() == null) {
            if (callback != null) {
                callback.onFailure(new IllegalArgumentException("Child or childId missing"));
            }
            return;
        }

        if (auth.getCurrentUser() == null) {
            if (callback != null) {
                callback.onFailure(new IllegalStateException("User not logged in"));
            }
            return;
        }

        String parentId = auth.getCurrentUser().getUid();
        String childId = child.getChildId();

        Map<String, Object> data = new HashMap<>();
        data.put("childId", childId);
        data.put("parentId", parentId);

        // Anonymous code instead of name
        data.put("childCode", child.getChildCode());

        // Other safe fields for dashboards and analytics
        data.put("age", child.getAge());
        data.put("gender", child.getGender());
        data.put("learningLevel", child.getLearningLevel());
        data.put("word1", child.getWord1());
        data.put("word2", child.getWord2());
        data.put("word3", child.getWord3());
        data.put("word4", child.getWord4());
        data.put("avatarKey", child.getAvatarKey());

        // Status flags
        data.put("active", child.isActive());

        db.collection(Constants.COLLECTION_CHILD_PROFILES)
                .document(childId)
                .set(data, SetOptions.merge())
                .addOnSuccessListener(unused -> {
                    Log.d(TAG, "Child profile synced in child_profiles for " + childId);
                    if (callback != null) {
                        callback.onSuccess(childId);
                    }
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Failed to sync child profile", e);
                    if (callback != null) {
                        callback.onFailure(e);
                    }
                });
    }
}
