package com.example.brightbuds_app.services;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.util.Log;

import com.example.brightbuds_app.models.ChildProfile;

import java.util.ArrayList;
import java.util.List;

/**
 * Handles local SQLite operations for child profiles.
 * This DAO always works with 5 fixed child slots per parent.
 */
public class ChildProfileDAO {

    private static final String TAG = "ChildProfileDao";

    private final DatabaseHelper dbHelper;

    public ChildProfileDAO(Context context) {
        this.dbHelper = new DatabaseHelper(context);
    }

    /**
     * Ensures that exactly 5 child profiles exist for the given parent.
     * Returns the current list (after creation of missing slots).
     */
    public List<ChildProfile> ensureFiveDefaultChildren(String parentId) {
        List<ChildProfile> existing = getChildrenForParent(parentId);

        if (existing.size() >= 5) {
            return existing;
        }

        // Create missing slots
        for (int i = existing.size(); i < 5; i++) {
            int slotNumber = i + 1;
            String childId = parentId + "_child_" + slotNumber;
            String defaultName = "Child " + slotNumber;

            ChildProfile child = new ChildProfile();
            child.setChildId(childId);
            child.setParentId(parentId);
            child.setName(defaultName);
            child.setAge(0);
            child.setGender(null);
            child.setLearningLevel(null);
            child.setAvatarKey("ic_child_avatar_placeholder");
            child.setActive(true);

            upsertChild(child);
        }

        return getChildrenForParent(parentId);
    }

    public void upsertChild(ChildProfile child) {
        if (child.getChildId() == null) {
            Log.e(TAG, "Child id is null in upsertChild");
            return;
        }

        SQLiteDatabase db = dbHelper.getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put(DatabaseHelper.COLUMN_CHILD_ID, child.getChildId());
        values.put(DatabaseHelper.COLUMN_PARENT_ID, child.getParentId());
        values.put(DatabaseHelper.COLUMN_CHILD_NAME, child.getName());
        values.put(DatabaseHelper.COLUMN_CHILD_AGE, child.getAge());
        values.put(DatabaseHelper.COLUMN_CHILD_GENDER, child.getGender());
        values.put(DatabaseHelper.COLUMN_CHILD_LEVEL, child.getLearningLevel());
        values.put(DatabaseHelper.COLUMN_CHILD_AVATAR_KEY, child.getAvatarKey());
        values.put(DatabaseHelper.COLUMN_CHILD_WORD1, child.getWord1());
        values.put(DatabaseHelper.COLUMN_CHILD_WORD2, child.getWord2());
        values.put(DatabaseHelper.COLUMN_CHILD_WORD3, child.getWord3());
        values.put(DatabaseHelper.COLUMN_CHILD_WORD4, child.getWord4());
        values.put(DatabaseHelper.COLUMN_CHILD_ACTIVE, child.isActive() ? 1 : 0);

        db.insertWithOnConflict(
                DatabaseHelper.TABLE_CHILD_PROFILE,
                null,
                values,
                SQLiteDatabase.CONFLICT_REPLACE
        );
        db.close();
    }

    public List<ChildProfile> getChildrenForParent(String parentId) {
        List<ChildProfile> list = new ArrayList<>();
        SQLiteDatabase db = dbHelper.getReadableDatabase();

        Cursor c = db.query(
                DatabaseHelper.TABLE_CHILD_PROFILE,
                null,
                DatabaseHelper.COLUMN_PARENT_ID + "=?",
                new String[]{parentId},
                null,
                null,
                DatabaseHelper.COLUMN_CHILD_ID + " ASC"
        );

        while (c.moveToNext()) {
            ChildProfile child = new ChildProfile();
            child.setChildId(c.getString(c.getColumnIndexOrThrow(DatabaseHelper.COLUMN_CHILD_ID)));
            child.setParentId(c.getString(c.getColumnIndexOrThrow(DatabaseHelper.COLUMN_PARENT_ID)));
            child.setName(c.getString(c.getColumnIndexOrThrow(DatabaseHelper.COLUMN_CHILD_NAME)));
            child.setAge(c.getInt(c.getColumnIndexOrThrow(DatabaseHelper.COLUMN_CHILD_AGE)));
            child.setGender(c.getString(c.getColumnIndexOrThrow(DatabaseHelper.COLUMN_CHILD_GENDER)));
            child.setLearningLevel(c.getString(c.getColumnIndexOrThrow(DatabaseHelper.COLUMN_CHILD_LEVEL)));
            child.setAvatarKey(c.getString(c.getColumnIndexOrThrow(DatabaseHelper.COLUMN_CHILD_AVATAR_KEY)));
            child.setWord1(c.getString(c.getColumnIndexOrThrow(DatabaseHelper.COLUMN_CHILD_WORD1)));
            child.setWord2(c.getString(c.getColumnIndexOrThrow(DatabaseHelper.COLUMN_CHILD_WORD2)));
            child.setWord3(c.getString(c.getColumnIndexOrThrow(DatabaseHelper.COLUMN_CHILD_WORD3)));
            child.setWord4(c.getString(c.getColumnIndexOrThrow(DatabaseHelper.COLUMN_CHILD_WORD4)));
            child.setActive(c.getInt(c.getColumnIndexOrThrow(DatabaseHelper.COLUMN_CHILD_ACTIVE)) == 1);

            list.add(child);
        }

        c.close();
        db.close();
        return list;
    }

    public ChildProfile getChildById(String childId) {
        SQLiteDatabase db = dbHelper.getReadableDatabase();

        Cursor c = db.query(
                DatabaseHelper.TABLE_CHILD_PROFILE,
                null,
                DatabaseHelper.COLUMN_CHILD_ID + "=?",
                new String[]{childId},
                null,
                null,
                null
        );

        ChildProfile child = null;
        if (c.moveToFirst()) {
            child = new ChildProfile();
            child.setChildId(c.getString(c.getColumnIndexOrThrow(DatabaseHelper.COLUMN_CHILD_ID)));
            child.setParentId(c.getString(c.getColumnIndexOrThrow(DatabaseHelper.COLUMN_PARENT_ID)));
            child.setName(c.getString(c.getColumnIndexOrThrow(DatabaseHelper.COLUMN_CHILD_NAME)));
            child.setAge(c.getInt(c.getColumnIndexOrThrow(DatabaseHelper.COLUMN_CHILD_AGE)));
            child.setGender(c.getString(c.getColumnIndexOrThrow(DatabaseHelper.COLUMN_CHILD_GENDER)));
            child.setLearningLevel(c.getString(c.getColumnIndexOrThrow(DatabaseHelper.COLUMN_CHILD_LEVEL)));
            child.setAvatarKey(c.getString(c.getColumnIndexOrThrow(DatabaseHelper.COLUMN_CHILD_AVATAR_KEY)));
            child.setWord1(c.getString(c.getColumnIndexOrThrow(DatabaseHelper.COLUMN_CHILD_WORD1)));
            child.setWord2(c.getString(c.getColumnIndexOrThrow(DatabaseHelper.COLUMN_CHILD_WORD2)));
            child.setWord3(c.getString(c.getColumnIndexOrThrow(DatabaseHelper.COLUMN_CHILD_WORD3)));
            child.setWord4(c.getString(c.getColumnIndexOrThrow(DatabaseHelper.COLUMN_CHILD_WORD4)));
            child.setActive(c.getInt(c.getColumnIndexOrThrow(DatabaseHelper.COLUMN_CHILD_ACTIVE)) == 1);
        }

        c.close();
        db.close();
        return child;
    }
}
