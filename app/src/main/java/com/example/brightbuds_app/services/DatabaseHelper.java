package com.example.brightbuds_app.services;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.util.Log;

import com.example.brightbuds_app.models.FamilyMember;
import com.example.brightbuds_app.models.Progress;
import com.example.brightbuds_app.models.SyncItem;
import com.example.brightbuds_app.utils.ModuleIds;
import com.example.brightbuds_app.models.ChildProfile;


import android.os.SystemClock;
import com.example.brightbuds_app.interfaces.DataCallbacks;
import com.example.brightbuds_app.services.ProgressService;
import com.example.brightbuds_app.utils.AnalyticsSessionManager;

import java.util.ArrayList;
import java.util.List;

/**
 * Manages local data persistence for BrightBuds.
 * Handles child profiles, progress cache, sync queue, analytics and family members.
 */
public class DatabaseHelper extends SQLiteOpenHelper {

    private static final String TAG = "DatabaseHelper";
    private static final String DATABASE_NAME = "brightbuds.db";
    // Version incremented to include family_members table.
    private static final int DATABASE_VERSION = 6;

    // Table names
    public static final String TABLE_CHILD_PROFILE = "ChildProfile";
    public static final String TABLE_CHILD_PROGRESS = "child_progress";
    public static final String TABLE_PROGRESS = TABLE_CHILD_PROGRESS;    // alias for compatibility
    public static final String TABLE_SYNC_QUEUE = "SyncQueue";

    // Analytics tables
    public static final String TABLE_GAME_SESSIONS = "game_sessions";
    public static final String TABLE_CHILD_ANALYTICS = "child_analytics";

    // Family members table
    public static final String TABLE_FAMILY = "family_members";
    public static final String COL_FAMILY_ID = "family_id";
    public static final String COL_FAMILY_PARENT_ID = "parent_id";
    public static final String COL_FAMILY_FIRST_NAME = "first_name";
    public static final String COL_FAMILY_RELATIONSHIP = "relationship";
    public static final String COL_FAMILY_IMAGE_PATH = "image_path";

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
    public static final String COLUMN_SYNC_STATUS = "sync_status"; // 0 = pending, 1 = synced

    // Sync queue
    public static final String COLUMN_SYNC_ID = "sync_id";
    public static final String COLUMN_TABLE_NAME = "table_name";
    public static final String COLUMN_RECORD_ID = "record_id";
    public static final String COLUMN_OPERATION = "operation";

    // Child profile columns
    public static final String COLUMN_CHILD_NAME = "child_name";
    public static final String COLUMN_CHILD_AGE = "child_age";
    public static final String COLUMN_CHILD_GENDER = "child_gender";
    public static final String COLUMN_CHILD_LEVEL = "child_level";
    public static final String COLUMN_CHILD_AVATAR_KEY = "child_avatar_key";
    public static final String COLUMN_CHILD_WORD1 = "child_word1";
    public static final String COLUMN_CHILD_WORD2 = "child_word2";
    public static final String COLUMN_CHILD_WORD3 = "child_word3";
    public static final String COLUMN_CHILD_WORD4 = "child_word4";
    public static final String COLUMN_CHILD_ACTIVE = "child_active";

    // game_sessions columns
    public static final String COLUMN_SESSION_ID = "session_id";
    public static final String COLUMN_SESSION_CHILD_ID = "session_child_id";
    public static final String COLUMN_SESSION_MODULE_ID = "session_module_id";
    public static final String COLUMN_SESSION_START_MS = "session_start_ms";
    public static final String COLUMN_SESSION_END_MS = "session_end_ms";
    public static final String COLUMN_SESSION_TIME_MS = "session_time_ms";
    public static final String COLUMN_SESSION_SCORE = "session_score";
    public static final String COLUMN_SESSION_TOTAL_CORRECT = "session_total_correct";
    public static final String COLUMN_SESSION_TOTAL_ATTEMPTS = "session_total_attempts";
    public static final String COLUMN_SESSION_STARS_EARNED = "session_stars_earned";
    public static final String COLUMN_SESSION_COMPLETED = "session_completed";
    public static final String COLUMN_SESSION_EXTRA_JSON = "session_extra_json";

    // child_analytics columns
    public static final String COLUMN_ANALYTICS_CHILD_ID = "analytics_child_id";
    public static final String COLUMN_ANALYTICS_MODULE_ID = "analytics_module_id";
    public static final String COLUMN_ANALYTICS_SESSION_COUNT = "session_count";
    public static final String COLUMN_ANALYTICS_TOTAL_SCORE = "total_score";
    public static final String COLUMN_ANALYTICS_TOTAL_CORRECT = "total_correct";
    public static final String COLUMN_ANALYTICS_TOTAL_ATTEMPTS = "total_attempts";
    public static final String COLUMN_ANALYTICS_TOTAL_TIME_MS = "total_time_ms";
    public static final String COLUMN_ANALYTICS_LAST_PLAYED_MS = "last_played_ms";
    public static final String COLUMN_ANALYTICS_LAST_SCORE = "last_score";
    public static final String COLUMN_ANALYTICS_LAST_ACCURACY = "last_accuracy";
    public static final String COLUMN_ANALYTICS_LAST_SESSION_TIME_MS = "last_session_time_ms";
    public static final String COLUMN_ANALYTICS_BEST_SCORE = "best_score";

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

    // ChildProfile table
    private static final String CREATE_TABLE_CHILD_PROFILE =
            "CREATE TABLE IF NOT EXISTS " + TABLE_CHILD_PROFILE + " (" +
                    COLUMN_CHILD_ID + " TEXT PRIMARY KEY, " +
                    COLUMN_PARENT_ID + " TEXT, " +
                    COLUMN_CHILD_NAME + " TEXT, " +
                    COLUMN_CHILD_AGE + " INTEGER, " +
                    COLUMN_CHILD_GENDER + " TEXT, " +
                    COLUMN_CHILD_LEVEL + " TEXT, " +
                    COLUMN_CHILD_AVATAR_KEY + " TEXT, " +
                    COLUMN_CHILD_WORD1 + " TEXT, " +
                    COLUMN_CHILD_WORD2 + " TEXT, " +
                    COLUMN_CHILD_WORD3 + " TEXT, " +
                    COLUMN_CHILD_WORD4 + " TEXT, " +
                    COLUMN_CHILD_ACTIVE + " INTEGER DEFAULT 1" +
                    ")";

    // game_sessions table
    private static final String CREATE_TABLE_GAME_SESSIONS =
            "CREATE TABLE IF NOT EXISTS " + TABLE_GAME_SESSIONS + " (" +
                    COLUMN_SESSION_ID + " INTEGER PRIMARY KEY AUTOINCREMENT, " +
                    COLUMN_SESSION_CHILD_ID + " TEXT NOT NULL, " +
                    COLUMN_SESSION_MODULE_ID + " TEXT NOT NULL, " +
                    COLUMN_SESSION_START_MS + " INTEGER NOT NULL, " +
                    COLUMN_SESSION_END_MS + " INTEGER NOT NULL, " +
                    COLUMN_SESSION_TIME_MS + " INTEGER NOT NULL, " +
                    COLUMN_SESSION_SCORE + " INTEGER DEFAULT 0, " +
                    COLUMN_SESSION_TOTAL_CORRECT + " INTEGER DEFAULT 0, " +
                    COLUMN_SESSION_TOTAL_ATTEMPTS + " INTEGER DEFAULT 0, " +
                    COLUMN_SESSION_STARS_EARNED + " INTEGER DEFAULT 0, " +
                    COLUMN_SESSION_COMPLETED + " INTEGER DEFAULT 1, " +
                    COLUMN_SESSION_EXTRA_JSON + " TEXT" +
                    ")";

    // child_analytics table
    private static final String CREATE_TABLE_CHILD_ANALYTICS =
            "CREATE TABLE IF NOT EXISTS " + TABLE_CHILD_ANALYTICS + " (" +
                    COLUMN_ANALYTICS_CHILD_ID + " TEXT NOT NULL, " +
                    COLUMN_ANALYTICS_MODULE_ID + " TEXT NOT NULL, " +
                    COLUMN_ANALYTICS_SESSION_COUNT + " INTEGER NOT NULL DEFAULT 0, " +
                    COLUMN_ANALYTICS_TOTAL_SCORE + " INTEGER NOT NULL DEFAULT 0, " +
                    COLUMN_ANALYTICS_TOTAL_CORRECT + " INTEGER NOT NULL DEFAULT 0, " +
                    COLUMN_ANALYTICS_TOTAL_ATTEMPTS + " INTEGER NOT NULL DEFAULT 0, " +
                    COLUMN_ANALYTICS_TOTAL_TIME_MS + " INTEGER NOT NULL DEFAULT 0, " +
                    COLUMN_ANALYTICS_LAST_PLAYED_MS + " INTEGER NOT NULL DEFAULT 0, " +
                    COLUMN_ANALYTICS_LAST_SCORE + " INTEGER NOT NULL DEFAULT 0, " +
                    COLUMN_ANALYTICS_LAST_ACCURACY + " REAL NOT NULL DEFAULT 0.0, " +
                    COLUMN_ANALYTICS_LAST_SESSION_TIME_MS + " INTEGER NOT NULL DEFAULT 0, " +
                    COLUMN_ANALYTICS_BEST_SCORE + " INTEGER NOT NULL DEFAULT 0, " +
                    "PRIMARY KEY (" + COLUMN_ANALYTICS_CHILD_ID + ", " + COLUMN_ANALYTICS_MODULE_ID + ")" +
                    ")";

    // family_members table
    private static final String CREATE_TABLE_FAMILY =
            "CREATE TABLE IF NOT EXISTS " + TABLE_FAMILY + " (" +
                    COL_FAMILY_ID + " INTEGER PRIMARY KEY AUTOINCREMENT, " +
                    COL_FAMILY_PARENT_ID + " INTEGER NOT NULL, " +
                    COL_FAMILY_FIRST_NAME + " TEXT NOT NULL, " +
                    COL_FAMILY_RELATIONSHIP + " TEXT, " +
                    COL_FAMILY_IMAGE_PATH + " TEXT" +
                    ")";

    public DatabaseHelper(Context context) {
        super(context, DATABASE_NAME, null, DATABASE_VERSION);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        Log.i(TAG, "Creating local database...");
        db.execSQL(CREATE_TABLE_CHILD_PROFILE);
        db.execSQL(CREATE_TABLE_PROGRESS);
        db.execSQL(CREATE_TABLE_SYNC_QUEUE);
        db.execSQL(CREATE_TABLE_GAME_SESSIONS);
        db.execSQL(CREATE_TABLE_CHILD_ANALYTICS);
        db.execSQL(CREATE_TABLE_FAMILY);
        Log.i(TAG, "Local database created successfully.");
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        Log.w(TAG, "Upgrading DB from " + oldVersion + " to " + newVersion);

        // Upgrade to version 3 for progress extra columns.
        if (oldVersion < 3) {
            try {
                db.execSQL("ALTER TABLE " + TABLE_CHILD_PROGRESS +
                        " ADD COLUMN " + COLUMN_TIME_SPENT + " INTEGER DEFAULT 0");
                db.execSQL("ALTER TABLE " + TABLE_CHILD_PROGRESS +
                        " ADD COLUMN " + COLUMN_SYNC_STATUS + " INTEGER DEFAULT 0");
                Log.i(TAG, "Database upgraded to version 3");
            } catch (Exception e) {
                Log.e(TAG, "Upgrade to v3 failed, recreating progress and sync tables", e);
                db.execSQL("DROP TABLE IF EXISTS " + TABLE_CHILD_PROGRESS);
                db.execSQL("DROP TABLE IF EXISTS " + TABLE_SYNC_QUEUE);
                db.execSQL(CREATE_TABLE_PROGRESS);
                db.execSQL(CREATE_TABLE_SYNC_QUEUE);
            }
        }

        // Upgrade to version 4 for ChildProfile table.
        if (oldVersion < 4) {
            try {
                db.execSQL(CREATE_TABLE_CHILD_PROFILE);
                Log.i(TAG, "ChildProfile table created for version 4");
            } catch (Exception e) {
                Log.e(TAG, "Failed to create ChildProfile table", e);
            }
        }

        // Upgrade to version 5 for analytics tables.
        if (oldVersion < 5) {
            try {
                db.execSQL(CREATE_TABLE_GAME_SESSIONS);
                db.execSQL(CREATE_TABLE_CHILD_ANALYTICS);
                Log.i(TAG, "Analytics tables created for version 5");
            } catch (Exception e) {
                Log.e(TAG, "Failed to create analytics tables", e);
            }
        }

        // Upgrade to version 6 for family_members table.
        if (oldVersion < 6) {
            try {
                db.execSQL(CREATE_TABLE_FAMILY);
                Log.i(TAG, "Family members table created for version 6");
            } catch (Exception e) {
                Log.e(TAG, "Failed to create family_members table", e);
            }
        }
    }

    @Override
    public void onConfigure(SQLiteDatabase db) {
        super.onConfigure(db);
        db.setForeignKeyConstraintsEnabled(true);
    }

    // PROGRESS CACHE

    // Default: treat as unsynced
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

        Log.d(TAG, "Cached progress [" + progressId + "] synced=" + isSynced);
    }

    /** Returns unsynced progress rows for cloud sync. */
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

    /** Marks a progress row as synced after success. */
    public void markProgressAsSynced(String progressId) {
        SQLiteDatabase db = this.getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put(COLUMN_SYNC_STATUS, 1);
        db.update(TABLE_CHILD_PROGRESS, values,
                COLUMN_PROGRESS_ID + "=?",
                new String[]{progressId});
        db.close();
        Log.d(TAG, "Marked progress as synced: " + progressId);
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
        Log.d(TAG, "Added to sync queue: " + tableName + " / " + recordId);
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

    // ANALYTICS

    // Records one game or media session and updates aggregated analytics.
    public void recordGameSession(String childId,
                                  String moduleId,
                                  long sessionStartMs,
                                  long sessionEndMs,
                                  int score,
                                  int totalCorrect,
                                  int totalAttempts,
                                  int starsEarned,
                                  int completed) {

        long sessionTimeMs = sessionEndMs - sessionStartMs;
        if (sessionTimeMs < 0) {
            sessionTimeMs = 0;
        }

        SQLiteDatabase db = getWritableDatabase();
        db.beginTransaction();
        try {
            ContentValues sessionValues = new ContentValues();
            sessionValues.put(COLUMN_SESSION_CHILD_ID, childId);
            sessionValues.put(COLUMN_SESSION_MODULE_ID, moduleId);
            sessionValues.put(COLUMN_SESSION_START_MS, sessionStartMs);
            sessionValues.put(COLUMN_SESSION_END_MS, sessionEndMs);
            sessionValues.put(COLUMN_SESSION_TIME_MS, sessionTimeMs);
            sessionValues.put(COLUMN_SESSION_SCORE, score);
            sessionValues.put(COLUMN_SESSION_TOTAL_CORRECT, totalCorrect);
            sessionValues.put(COLUMN_SESSION_TOTAL_ATTEMPTS, totalAttempts);
            sessionValues.put(COLUMN_SESSION_STARS_EARNED, starsEarned);
            sessionValues.put(COLUMN_SESSION_COMPLETED, completed);

            db.insert(TABLE_GAME_SESSIONS, null, sessionValues);

            updateChildAnalyticsInternal(
                    db,
                    childId,
                    moduleId,
                    score,
                    totalCorrect,
                    totalAttempts,
                    sessionTimeMs,
                    sessionEndMs
            );

            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }
    }

    // Updates aggregated analytics per child and module.
    private void updateChildAnalyticsInternal(SQLiteDatabase db,
                                              String childId,
                                              String moduleId,
                                              int score,
                                              int totalCorrect,
                                              int totalAttempts,
                                              long sessionTimeMs,
                                              long lastPlayedMs) {

        String[] columns = new String[] {
                COLUMN_ANALYTICS_SESSION_COUNT,
                COLUMN_ANALYTICS_TOTAL_SCORE,
                COLUMN_ANALYTICS_TOTAL_CORRECT,
                COLUMN_ANALYTICS_TOTAL_ATTEMPTS,
                COLUMN_ANALYTICS_TOTAL_TIME_MS,
                COLUMN_ANALYTICS_BEST_SCORE
        };

        String selection = COLUMN_ANALYTICS_CHILD_ID + " = ? AND " + COLUMN_ANALYTICS_MODULE_ID + " = ?";
        String[] selectionArgs = new String[] { childId, moduleId };

        int sessionCount = 0;
        int totalScore = 0;
        int totalCorrectAll = 0;
        int totalAttemptsAll = 0;
        long totalTimeMs = 0L;
        int bestScore = 0;

        Cursor cursor = db.query(
                TABLE_CHILD_ANALYTICS,
                columns,
                selection,
                selectionArgs,
                null,
                null,
                null
        );

        if (cursor != null) {
            if (cursor.moveToFirst()) {
                sessionCount = cursor.getInt(0);
                totalScore = cursor.getInt(1);
                totalCorrectAll = cursor.getInt(2);
                totalAttemptsAll = cursor.getInt(3);
                totalTimeMs = cursor.getLong(4);
                bestScore = cursor.getInt(5);
            }
            cursor.close();
        }

        sessionCount = sessionCount + 1;
        totalScore = totalScore + score;
        totalCorrectAll = totalCorrectAll + totalCorrect;
        totalAttemptsAll = totalAttemptsAll + totalAttempts;
        totalTimeMs = totalTimeMs + sessionTimeMs;
        if (score > bestScore) {
            bestScore = score;
        }

        double lastAccuracy = 0.0;
        if (totalAttempts > 0) {
            lastAccuracy = (totalCorrect * 1.0) / totalAttempts;
        }

        ContentValues values = new ContentValues();
        values.put(COLUMN_ANALYTICS_CHILD_ID, childId);
        values.put(COLUMN_ANALYTICS_MODULE_ID, moduleId);
        values.put(COLUMN_ANALYTICS_SESSION_COUNT, sessionCount);
        values.put(COLUMN_ANALYTICS_TOTAL_SCORE, totalScore);
        values.put(COLUMN_ANALYTICS_TOTAL_CORRECT, totalCorrectAll);
        values.put(COLUMN_ANALYTICS_TOTAL_ATTEMPTS, totalAttemptsAll);
        values.put(COLUMN_ANALYTICS_TOTAL_TIME_MS, totalTimeMs);
        values.put(COLUMN_ANALYTICS_LAST_PLAYED_MS, lastPlayedMs);
        values.put(COLUMN_ANALYTICS_LAST_SCORE, score);
        values.put(COLUMN_ANALYTICS_LAST_ACCURACY, lastAccuracy);
        values.put(COLUMN_ANALYTICS_LAST_SESSION_TIME_MS, sessionTimeMs);
        values.put(COLUMN_ANALYTICS_BEST_SCORE, bestScore);

        db.insertWithOnConflict(
                TABLE_CHILD_ANALYTICS,
                null,
                values,
                SQLiteDatabase.CONFLICT_REPLACE
        );
    }

    /**
     * Returns the moduleId of the favourite song for one child.
     * Favourite is defined as:
     *  - highest session_count
     *  - if tied, highest total_time_ms
     * Limited to the three song modules.
     */
    public String getFavouriteSongModuleForChild(String childId) {
        SQLiteDatabase db = getReadableDatabase();

        String[] columns = new String[] {
                COLUMN_ANALYTICS_MODULE_ID,
                COLUMN_ANALYTICS_SESSION_COUNT,
                COLUMN_ANALYTICS_TOTAL_TIME_MS
        };

        String selection =
                COLUMN_ANALYTICS_CHILD_ID + " = ? AND " +
                        COLUMN_ANALYTICS_MODULE_ID + " IN (?,?,?)";

        String[] selectionArgs = new String[] {
                childId,
                ModuleIds.MODULE_ABC_SONG,
                ModuleIds.MODULE_123_SONG,
                ModuleIds.MODULE_SHAPES_SONG
        };

        Cursor c = db.query(
                TABLE_CHILD_ANALYTICS,
                columns,
                selection,
                selectionArgs,
                null,
                null,
                COLUMN_ANALYTICS_SESSION_COUNT + " DESC, " +
                        COLUMN_ANALYTICS_TOTAL_TIME_MS + " DESC",
                "1"
        );

        String favouriteModuleId = null;
        if (c != null) {
            if (c.moveToFirst()) {
                favouriteModuleId = c.getString(
                        c.getColumnIndexOrThrow(COLUMN_ANALYTICS_MODULE_ID)
                );
            }
            c.close();
        }

        return favouriteModuleId;
    }

    // Returns aggregated analytics cursor per module for one child.
    public Cursor getChildAnalyticsSummary(String childId) {
        SQLiteDatabase db = getReadableDatabase();

        String[] columns = new String[] {
                COLUMN_ANALYTICS_MODULE_ID,
                COLUMN_ANALYTICS_SESSION_COUNT,
                COLUMN_ANALYTICS_TOTAL_SCORE,
                COLUMN_ANALYTICS_TOTAL_CORRECT,
                COLUMN_ANALYTICS_TOTAL_ATTEMPTS,
                COLUMN_ANALYTICS_TOTAL_TIME_MS,
                COLUMN_ANALYTICS_LAST_PLAYED_MS,
                COLUMN_ANALYTICS_LAST_SCORE,
                COLUMN_ANALYTICS_LAST_ACCURACY,
                COLUMN_ANALYTICS_LAST_SESSION_TIME_MS,
                COLUMN_ANALYTICS_BEST_SCORE
        };

        String selection = COLUMN_ANALYTICS_CHILD_ID + " = ?";
        String[] selectionArgs = new String[] { childId };

        return db.query(
                TABLE_CHILD_ANALYTICS,
                columns,
                selection,
                selectionArgs,
                null,
                null,
                COLUMN_ANALYTICS_MODULE_ID + " ASC"
        );
    }

    // Returns all sessions for one child and module ordered by recency.
    public Cursor getSessionsForChildAndModule(String childId, String moduleId) {
        SQLiteDatabase db = getReadableDatabase();

        String[] columns = new String[] {
                COLUMN_SESSION_ID,
                COLUMN_SESSION_START_MS,
                COLUMN_SESSION_END_MS,
                COLUMN_SESSION_TIME_MS,
                COLUMN_SESSION_SCORE,
                COLUMN_SESSION_TOTAL_CORRECT,
                COLUMN_SESSION_TOTAL_ATTEMPTS,
                COLUMN_SESSION_STARS_EARNED,
                COLUMN_SESSION_COMPLETED
        };

        String selection = COLUMN_SESSION_CHILD_ID + " = ? AND " +
                COLUMN_SESSION_MODULE_ID + " = ?";
        String[] selectionArgs = new String[] { childId, moduleId };

        return db.query(
                TABLE_GAME_SESSIONS,
                columns,
                selection,
                selectionArgs,
                null,
                null,
                COLUMN_SESSION_END_MS + " DESC"
        );
    }

    // FAMILY HELPERS

    /**
     * Returns the current parent local id.
     * For now this is a simple placeholder.
     * When you link login to local parent rows, replace this with real logic.
     */
    public long getCurrentParentLocalId() {
        return 1L;
    }

    /**
     * Loads all family members for a given parent local id.
     */
    public List<FamilyMember> getFamilyMembersForParent(long parentId) {
        List<FamilyMember> result = new ArrayList<>();
        SQLiteDatabase db = getReadableDatabase();
        Cursor c = null;

        try {
            c = db.query(
                    TABLE_FAMILY,
                    new String[]{
                            COL_FAMILY_ID,
                            COL_FAMILY_PARENT_ID,
                            COL_FAMILY_FIRST_NAME,
                            COL_FAMILY_RELATIONSHIP,
                            COL_FAMILY_IMAGE_PATH
                    },
                    COL_FAMILY_PARENT_ID + " = ?",
                    new String[]{String.valueOf(parentId)},
                    null,
                    null,
                    COL_FAMILY_FIRST_NAME + " ASC"
            );

            while (c != null && c.moveToNext()) {
                FamilyMember fm = new FamilyMember();
                fm.setFamilyId(c.getLong(c.getColumnIndexOrThrow(COL_FAMILY_ID)));
                fm.setParentId(c.getLong(c.getColumnIndexOrThrow(COL_FAMILY_PARENT_ID)));
                fm.setFirstName(c.getString(c.getColumnIndexOrThrow(COL_FAMILY_FIRST_NAME)));
                fm.setRelationship(c.getString(c.getColumnIndexOrThrow(COL_FAMILY_RELATIONSHIP)));
                fm.setImagePath(c.getString(c.getColumnIndexOrThrow(COL_FAMILY_IMAGE_PATH)));
                result.add(fm);
            }
        } catch (Exception e) {
            Log.e(TAG, "Error loading family members for parent " + parentId, e);
        } finally {
            if (c != null) {
                c.close();
            }
        }

        return result;
    }

    // CHILD PROFILE HELPERS

    /**
     * Loads all active child profiles for a given Firebase parent uid.
     * Uses the local ChildProfile table.
     */
    public List<ChildProfile> getChildProfilesForParent(String parentUid) {
        List<ChildProfile> result = new ArrayList<>();
        SQLiteDatabase db = getReadableDatabase();
        Cursor c = null;

        try {
            c = db.query(
                    TABLE_CHILD_PROFILE,
                    new String[]{
                            COLUMN_CHILD_ID,
                            COLUMN_PARENT_ID,
                            COLUMN_CHILD_NAME,
                            COLUMN_CHILD_AGE,
                            COLUMN_CHILD_GENDER,
                            COLUMN_CHILD_LEVEL,
                            COLUMN_CHILD_AVATAR_KEY,
                            COLUMN_CHILD_WORD1,
                            COLUMN_CHILD_WORD2,
                            COLUMN_CHILD_WORD3,
                            COLUMN_CHILD_WORD4,
                            COLUMN_CHILD_ACTIVE
                    },
                    COLUMN_PARENT_ID + " = ? AND " + COLUMN_CHILD_ACTIVE + " = 1",
                    new String[]{parentUid},
                    null,
                    null,
                    COLUMN_CHILD_NAME + " ASC"
            );

            while (c != null && c.moveToNext()) {
                ChildProfile child = new ChildProfile();
                child.setChildId(c.getString(c.getColumnIndexOrThrow(COLUMN_CHILD_ID)));
                child.setParentId(c.getString(c.getColumnIndexOrThrow(COLUMN_PARENT_ID)));
                child.setName(c.getString(c.getColumnIndexOrThrow(COLUMN_CHILD_NAME)));
                child.setAge(c.getInt(c.getColumnIndexOrThrow(COLUMN_CHILD_AGE)));
                child.setGender(c.getString(c.getColumnIndexOrThrow(COLUMN_CHILD_GENDER)));
                child.setLearningLevel(c.getString(c.getColumnIndexOrThrow(COLUMN_CHILD_LEVEL)));
                child.setAvatarKey(c.getString(c.getColumnIndexOrThrow(COLUMN_CHILD_AVATAR_KEY)));
                child.setWord1(c.getString(c.getColumnIndexOrThrow(COLUMN_CHILD_WORD1)));
                child.setWord2(c.getString(c.getColumnIndexOrThrow(COLUMN_CHILD_WORD2)));
                child.setWord3(c.getString(c.getColumnIndexOrThrow(COLUMN_CHILD_WORD3)));
                child.setWord4(c.getString(c.getColumnIndexOrThrow(COLUMN_CHILD_WORD4)));
                int activeInt = c.getInt(c.getColumnIndexOrThrow(COLUMN_CHILD_ACTIVE));
                child.setActive(activeInt == 1);

                result.add(child);
            }
        } catch (Exception e) {
            Log.e(TAG, "Error loading child profiles for parent " + parentUid, e);
        } finally {
            if (c != null) {
                c.close();
            }
            db.close();
        }

        return result;
    }

}
