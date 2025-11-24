package com.example.brightbuds_app.utils;

import androidx.annotation.Nullable;
import android.content.Context;
import android.content.SharedPreferences;

/**
 * Helper for reading and updating the currently selected child.
 * Storage uses the same shared preferences file as the rest of the app.
 */
public final class CurrentChildManager {

    private static final String PREFS_NAME = "BrightBudsPrefs";
    private static final String KEY_SELECTED_CHILD_ID = "selectedChildId";
    private static final String KEY_SELECTED_CHILD_NAME = "selectedChildName";

    private CurrentChildManager() {
        // Prevent instantiation
    }

    /**
     * Stores child id and name as the active child.
     */
    public static void setCurrentChild(Context context, String childId, String childName) {
        SharedPreferences sp =
                context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        sp.edit()
                .putString(KEY_SELECTED_CHILD_ID, childId)
                .putString(KEY_SELECTED_CHILD_NAME, childName)
                .apply();
    }

    /**
     * Stores only child id as the active child.
     */
    public static void setCurrentChildId(Context context, String childId) {
        SharedPreferences sp =
                context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        sp.edit()
                .putString(KEY_SELECTED_CHILD_ID, childId)
                .apply();
    }

    /**
     * Returns active child id, or null if no child has been selected.
     */
    @Nullable
    public static String getCurrentChildId(Context context) {
        SharedPreferences sp =
                context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        return sp.getString(KEY_SELECTED_CHILD_ID, null);
    }

    /**
     * Returns active child name, or null if name is not stored.
     */
    @Nullable
    public static String getCurrentChildName(Context context) {
        SharedPreferences sp =
                context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        return sp.getString(KEY_SELECTED_CHILD_NAME, null);
    }

    /**
     * Clears any stored active child information.
     */
    public static void clearCurrentChild(Context context) {
        SharedPreferences sp =
                context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        sp.edit()
                .remove(KEY_SELECTED_CHILD_ID)
                .remove(KEY_SELECTED_CHILD_NAME)
                .apply();
    }
}
