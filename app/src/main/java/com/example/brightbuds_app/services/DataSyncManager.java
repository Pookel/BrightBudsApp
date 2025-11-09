package com.example.brightbuds_app.services;

import android.content.Context;
import android.util.Log;

import com.example.brightbuds_app.interfaces.DataCallbacks;
import com.example.brightbuds_app.models.Progress;
import com.example.brightbuds_app.models.SyncItem;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.List;

/**
 * DataSyncManager
 * Syncs local SQLite to Firestore for:
 *  - child_progress (offline progress)
 *  - optional queued operations (SyncQueue)
 */
public class DataSyncManager {

    private static final String TAG = "DataSyncManager";

    private final DatabaseHelper localDb;
    private final FirebaseFirestore firestore;

    public DataSyncManager(Context context) {
        this.localDb = new DatabaseHelper(context);
        this.firestore = FirebaseFirestore.getInstance();
    }

    // Sync unsynced child_progress rows
    public void syncAllPendingChanges(DataCallbacks.GenericCallback callback) {
        List<Progress> unsynced = localDb.getUnsyncedProgressDetails();

        if (unsynced.isEmpty()) {
            String msg = "✅ All progress records are already synced";
            Log.i(TAG, msg);
            callback.onSuccess(msg);
            return;
        }

        Log.i(TAG, "🔄 Syncing " + unsynced.size() + " offline progress records...");
        syncNextProgress(unsynced, 0, callback);
    }

    private void syncNextProgress(List<Progress> list,
                                  int index,
                                  DataCallbacks.GenericCallback callback) {

        if (index >= list.size()) {
            String msg = "✅ Sync complete for all progress records";
            Log.i(TAG, msg);
            callback.onSuccess(msg);
            return;
        }

        Progress p = list.get(index);
        String progressId = p.getProgressId();
        if (progressId == null || progressId.isEmpty()) {
            Log.w(TAG, "Skipping progress with no ID");
            syncNextProgress(list, index + 1, callback);
            return;
        }

        Log.d(TAG, "⬆️ Syncing progress " + progressId +
                " child=" + p.getChildId() + " module=" + p.getModuleId());

        firestore.collection("child_progress")
                .document(progressId)
                .set(p)
                .addOnSuccessListener(unused -> {
                    localDb.markProgressAsSynced(progressId);
                    Log.d(TAG, "✅ Synced " + progressId);
                    syncNextProgress(list, index + 1, callback);
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "❌ Failed to sync " + progressId, e);
                    // Stop here; keep remaining as unsynced (retry later)
                    callback.onFailure(e);
                });
    }

    // sync generic queued operations
    public void syncQueuedOperations(DataCallbacks.GenericCallback callback) {
        List<SyncItem> queue = localDb.getSyncQueue();
        if (queue.isEmpty()) {
            callback.onSuccess("✅ No queued operations");
            return;
        }

        Log.i(TAG, "🔄 Syncing " + queue.size() + " queued operations...");
        syncNextQueueItem(queue, 0, callback);
    }

    private void syncNextQueueItem(List<SyncItem> items,
                                   int index,
                                   DataCallbacks.GenericCallback callback) {

        if (index >= items.size()) {
            callback.onSuccess("✅ All queued operations synced");
            return;
        }

        SyncItem item = items.get(index);
        String collection = resolveCollectionName(item.getTableName());

        Log.d(TAG, "Processing queued item " + item.getOperation() +
                " on " + collection + "/" + item.getRecordId());

        switch (item.getOperation().toLowerCase()) {
            case "insert":
                firestore.collection(collection)
                        .document(item.getRecordId())
                        .set(item)
                        .addOnSuccessListener(unused -> {
                            localDb.markAsSynced(item.getTableName(), item.getRecordId());
                            syncNextQueueItem(items, index + 1, callback);
                        })
                        .addOnFailureListener(callback::onFailure);
                break;

            case "update":
                firestore.collection(collection)
                        .document(item.getRecordId())
                        .update("lastSynced", System.currentTimeMillis())
                        .addOnSuccessListener(unused -> {
                            localDb.markAsSynced(item.getTableName(), item.getRecordId());
                            syncNextQueueItem(items, index + 1, callback);
                        })
                        .addOnFailureListener(callback::onFailure);
                break;

            case "delete":
                firestore.collection(collection)
                        .document(item.getRecordId())
                        .delete()
                        .addOnSuccessListener(unused -> {
                            localDb.markAsSynced(item.getTableName(), item.getRecordId());
                            syncNextQueueItem(items, index + 1, callback);
                        })
                        .addOnFailureListener(callback::onFailure);
                break;

            default:
                Log.w(TAG, "⚠️ Unknown operation: " + item.getOperation());
                syncNextQueueItem(items, index + 1, callback);
        }
    }

    private String resolveCollectionName(String tableName) {
        if (DatabaseHelper.TABLE_CHILD_PROFILE.equals(tableName)) {
            return "child_profiles";
        }
        if (DatabaseHelper.TABLE_CHILD_PROGRESS.equals(tableName)
                || "child_progress".equalsIgnoreCase(tableName)) {
            return "child_progress";
        }
        return tableName.toLowerCase();
    }

    // Status
    public void getSyncStatus(DataCallbacks.GenericCallback callback) {
        int pending = localDb.getUnsyncedProgressDetails().size();
        if (pending == 0) {
            callback.onSuccess("✅ All local data synced");
        } else {
            callback.onSuccess("⚠️ " + pending + " unsynced progress records");
        }
    }
}
