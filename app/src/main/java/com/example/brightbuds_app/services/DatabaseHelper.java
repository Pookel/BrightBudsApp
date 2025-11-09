package com.example.brightbuds_app.services;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.util.Log;

import com.example.brightbuds_app.models.Progress;
import com.example.brightbuds_app.models.SyncItem;

import java.util.ArrayList;
import java.util.List;

/**
 * DatabaseHelper — Manages local data persistence for BrightBuds.
 * Offline-first cache for child_progress.
 */
public class DatabaseHelper extends SQLiteOpenHelper {

    private static final String TAG = "DatabaseHelper";
    private static final String DATABASE_NAME = "brightbuds.db";
    private static final int DATABASE_VERSION = 3;

    // Table names
    public static final String TABLE_CHILD_PROFILE = "ChildProfile";
    public static final String TABLE_CHILD_PROGRESS = "child_progress";
    public static final String TABLE_PROGRESS = TABLE_CHILD_PROGRESS;    // alias for compatibility
    public static final String TABLE_SYNC_QUEUE = "SyncQueue";

    // Common
    public static final String COLUMN_ID = "id";
    public static final String COLUMN_CREATED_AT = "created_at";

    // Progress
    public static final String COLUMN_PROGRESS_ID = "progress_id";
    public static final String COLUMN_PARENT_ID = "parent_id";
    public static final String COLUMN_CHILD_ID = "child_id";
    public static final String COLUMN_MODULE_ID = "module_id";
    public static final String COLUMN_SCORE = "score";
    public static final String COLUMN_STATUS = "status";
    public static final String COLUMN_TIMESTAMP = "timestamp";
    public static final String COLUMN_TIME_SPENT = "time_spent";
    public static final String COLUMN_SYNC_STATUS = "sync_status"; // 0=pending, 1=synced

    // Sync queue
    public static final String COLUMN_SYNC_ID = "sync_id";
    public static final String COLUMN_TABLE_NAME = "table_name";
    public static final String COLUMN_RECORD_ID = "record_id";
    public static final String COLUMN_OPERATION = "operation";

    // child_progress table
    private static final String CREATE_TABLE_PROGRESS =
            "CREATE TABLE IF NOT EXISTS " + TABLE_CHILD_PROGRESS + " (" +
                    COLUMN_PROGRESS_ID + " TEXT PRIMARY KEY, " +
                    COLUMN_PARENT_ID + " TEXT, " +
                    COLUMN_CHILD_ID + " TEXT, " +
                    COLUMN_MODULE_ID + " TEXT, " +
                    COLUMN_SCORE + " INTEGER, " +
                    COLUMN_STATUS + " TEXT, " +
                    COLUMN_TIMESTAMP + " INTEGER, " +
                    COLUMN_TIME_SPENT + " INTEGER DEFAULT 0, " +
                    COLUMN_SYNC_STATUS + " INTEGER DEFAULT 0" +
                    ")";

    // SyncQueue table
    private static final String CREATE_TABLE_SYNC_QUEUE =
            "CREATE TABLE IF NOT EXISTS " + TABLE_SYNC_QUEUE + " (" +
                    COLUMN_SYNC_ID + " INTEGER PRIMARY KEY AUTOINCREMENT, " +
                    COLUMN_TABLE_NAME + " TEXT NOT NULL, " +
                    COLUMN_RECORD_ID + " TEXT NOT NULL, " +
                    COLUMN_OPERATION + " TEXT NOT NULL, " +
                    COLUMN_SYNC_STATUS + " INTEGER DEFAULT 0, " +
                    COLUMN_CREATED_AT + " DATETIME DEFAULT CURRENT_TIMESTAMP" +
                    ")";

    public DatabaseHelper(Context context) {
        super(context, DATABASE_NAME, null, DATABASE_VERSION);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        Log.i(TAG, "Creating local database...");
        db.execSQL(CREATE_TABLE_PROGRESS);
        db.execSQL(CREATE_TABLE_SYNC_QUEUE);
        Log.i(TAG, "✅ Local database created successfully.");
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        Log.w(TAG, "Upgrading DB from " + oldVersion + " to " + newVersion);
        if (oldVersion < 3) {
            try {
                db.execSQL("ALTER TABLE " + TABLE_CHILD_PROGRESS +
                        " ADD COLUMN " + COLUMN_TIME_SPENT + " INTEGER DEFAULT 0");
                db.execSQL("ALTER TABLE " + TABLE_CHILD_PROGRESS +
                        " ADD COLUMN " + COLUMN_SYNC_STATUS + " INTEGER DEFAULT 0");
                Log.i(TAG, "✅ Database upgraded to version 3");
            } catch (Exception e) {
                Log.e(TAG, "⚠️ Upgrade failed, recreating tables", e);
                db.execSQL("DROP TABLE IF EXISTS " + TABLE_CHILD_PROGRESS);
                db.execSQL("DROP TABLE IF EXISTS " + TABLE_SYNC_QUEUE);
                onCreate(db);
            }
        }
    }

    @Override
    public void onConfigure(SQLiteDatabase db) {
        super.onConfigure(db);
        db.setForeignKeyConstraintsEnabled(true);
    }

    // PROGRESS CACHE

    // Default: treat as UNSYNCED (used for offline and failures)
    public void insertOrUpdateProgress(String progressId,
                                       String parentId,
                                       String childId,
                                       String moduleId,
                                       int score,
                                       String status,
                                       long timestamp,
                                       long timeSpent) {
        insertOrUpdateProgress(progressId, parentId, childId, moduleId,
                score, status, timestamp, timeSpent, false);
    }

    // Overload: explicitly control sync flag
    public void insertOrUpdateProgress(String progressId,
                                       String parentId,
                                       String childId,
                                       String moduleId,
                                       int score,
                                       String status,
                                       long timestamp,
                                       long timeSpent,
                                       boolean isSynced) {

        SQLiteDatabase db = this.getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put(COLUMN_PROGRESS_ID, progressId);
        values.put(COLUMN_PARENT_ID, parentId);
        values.put(COLUMN_CHILD_ID, childId);
        values.put(COLUMN_MODULE_ID, moduleId);
        values.put(COLUMN_SCORE, score);
        values.put(COLUMN_STATUS, status);
        values.put(COLUMN_TIMESTAMP, timestamp);
        values.put(COLUMN_TIME_SPENT, timeSpent);
        values.put(COLUMN_SYNC_STATUS, isSynced ? 1 : 0);

        db.insertWithOnConflict(TABLE_CHILD_PROGRESS, null, values, SQLiteDatabase.CONFLICT_REPLACE);
        db.close();

        Log.d(TAG, "📦 Cached progress [" + progressId + "] synced=" + isSynced);
    }

    /** Full unsynced rows for Progress sync */
    public List<Progress> getUnsyncedProgressDetails() {
        List<Progress> list = new ArrayList<>();
        SQLiteDatabase db = this.getReadableDatabase();

        Cursor c = db.query(TABLE_CHILD_PROGRESS,
                null,
                COLUMN_SYNC_STATUS + "=?",
                new String[]{"0"},
                null, null, null);

        while (c.moveToNext()) {
            Progress p = new Progress();
            p.setProgressId(c.getString(c.getColumnIndexOrThrow(COLUMN_PROGRESS_ID)));
            p.setParentId(c.getString(c.getColumnIndexOrThrow(COLUMN_PARENT_ID)));
            p.setChildId(c.getString(c.getColumnIndexOrThrow(COLUMN_CHILD_ID)));
            p.setModuleId(c.getString(c.getColumnIndexOrThrow(COLUMN_MODULE_ID)));
            p.setScore(c.getInt(c.getColumnIndexOrThrow(COLUMN_SCORE)));
            p.setStatus(c.getString(c.getColumnIndexOrThrow(COLUMN_STATUS)));
            p.setTimestamp(c.getLong(c.getColumnIndexOrThrow(COLUMN_TIMESTAMP)));
            p.setTimeSpent(c.getLong(c.getColumnIndexOrThrow(COLUMN_TIME_SPENT)));
            list.add(p);
        }

        c.close();
        db.close();
        return list;
    }

    /** Mark a local progress row as synced */
    public void markProgressAsSynced(String progressId) {
        SQLiteDatabase db = this.getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put(COLUMN_SYNC_STATUS, 1);
        db.update(TABLE_CHILD_PROGRESS, values,
                COLUMN_PROGRESS_ID + "=?",
                new String[]{progressId});
        db.close();
        Log.d(TAG, "✅ Marked as synced: " + progressId);
    }

    // Generic for legacy SyncQueue
    public void markAsSynced(String tableName, String recordId) {
        SQLiteDatabase db = this.getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put(COLUMN_SYNC_STATUS, 1);
        db.update(tableName, values,
                COLUMN_RECORD_ID + "=?",
                new String[]{recordId});
        db.close();
    }

    // SYNC QUEUE

    public void addToSyncQueue(String tableName, String recordId, String operation) {
        SQLiteDatabase db = this.getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put(COLUMN_TABLE_NAME, tableName);
        values.put(COLUMN_RECORD_ID, recordId);
        values.put(COLUMN_OPERATION, operation);
        db.insert(TABLE_SYNC_QUEUE, null, values);
        db.close();
        Log.d(TAG, "📤 Added to sync queue → " + tableName + " / " + recordId);
    }

    public List<SyncItem> getSyncQueue() {
        List<SyncItem> queue = new ArrayList<>();
        SQLiteDatabase db = this.getReadableDatabase();

        Cursor cursor = db.query(TABLE_SYNC_QUEUE,
                null,
                COLUMN_SYNC_STATUS + "=0",
                null, null, null,
                COLUMN_CREATED_AT + " ASC");

        while (cursor.moveToNext()) {
            SyncItem item = new SyncItem();
            item.setId(cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_SYNC_ID)));
            item.setTableName(cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_TABLE_NAME)));
            item.setOperation(cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_OPERATION)));
            item.setRecordId(cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_RECORD_ID)));
            queue.add(item);
        }

        cursor.close();
        db.close();
        return queue;
    }
}
