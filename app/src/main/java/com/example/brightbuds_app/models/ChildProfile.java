package com.example.brightbuds_app.models;

import android.content.Context;

/**
 * Represents a child profile that belongs to a parent.
 * Used both for local SQLite storage and Firestore sync.
 */
public class ChildProfile {

    // Firestore document id. We also use this as the primary key in SQLite.
    private String childId;

    // Parent Firebase UID.
    private String parentId;

    // Display name for the child.
    private String name;

    // Optional: gender for future use.
    private String gender;

    // Learning level string such as "Beginner", "Intermediate", "Advanced".
    private String learningLevel;

    private int age;

    // Simple key that maps to a drawable, for example "avatar_1"
    // or "ic_child_avatar_placeholder".
    private String avatarKey;

    // Optional: custom words for Word Builder.
    private String word1;
    private String word2;
    private String word3;
    private String word4;

    // Progress related fields, useful for dashboards and reports.
    private int stars;
    private int completedModules;
    private int progress;
    private boolean active = true;

    public ChildProfile() {
    }

    /** Full constructor */
    public ChildProfile(String childId,
                        String parentId,
                        String name,
                        int age,
                        String gender,
                        String learningLevel,
                        String avatarKey,
                        String word1,
                        String word2,
                        String word3,
                        String word4) {
        this.childId = childId;
        this.parentId = parentId;
        this.name = name;
        this.age = age;
        this.gender = gender;
        this.learningLevel = learningLevel;
        this.avatarKey = avatarKey;
        this.word1 = word1;
        this.word2 = word2;
        this.word3 = word3;
        this.word4 = word4;
    }

    /** Convenience constructor used by some older screens */
    public ChildProfile(String parentId,
                        String name,
                        int age,
                        String gender,
                        String learningLevel) {
        this.childId = null; // will be assigned later
        this.parentId = parentId;
        this.name = name;
        this.age = age;
        this.gender = gender;
        this.learningLevel = learningLevel;
        this.avatarKey = "ic_child_avatar_placeholder";
    }

    public String getChildId() {
        return childId;
    }

    public void setChildId(String childId) {
        this.childId = childId;
    }

    public String getParentId() {
        return parentId;
    }

    public void setParentId(String parentId) {
        this.parentId = parentId;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getGender() {
        return gender;
    }

    public void setGender(String gender) {
        this.gender = gender;
    }

    public String getLearningLevel() {
        return learningLevel;
    }

    public void setLearningLevel(String learningLevel) {
        this.learningLevel = learningLevel;
    }

    // For older code that used getLevel / setLevel
    public String getLevel() {
        return learningLevel;
    }

    public void setLevel(String level) {
        this.learningLevel = level;
    }

    public int getAge() {
        return age;
    }

    public void setAge(int age) {
        this.age = age;
    }

    public String getAvatarKey() {
        return avatarKey;
    }

    public void setAvatarKey(String avatarKey) {
        this.avatarKey = avatarKey;
    }

    public String getWord1() {
        return word1;
    }

    public void setWord1(String word1) {
        this.word1 = word1;
    }

    public String getWord2() {
        return word2;
    }

    public void setWord2(String word2) {
        this.word2 = word2;
    }

    public String getWord3() {
        return word3;
    }

    public void setWord3(String word3) {
        this.word3 = word3;
    }

    public String getWord4() {
        return word4;
    }

    public void setWord4(String word4) {
        this.word4 = word4;
    }

    public int getStars() {
        return stars;
    }

    public void setStars(int stars) {
        this.stars = stars;
    }

    public int getCompletedModules() {
        return completedModules;
    }

    public void setCompletedModules(int completedModules) {
        this.completedModules = completedModules;
    }

    public int getProgress() {
        return progress;
    }

    public void setProgress(int progress) {
        this.progress = progress;
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    /**
     * Anonymous child code for use in Firestore and reports.
     * Example: parentUid_child_1 becomes BB1.
     * If pattern is different, falls back to childId.
     */
    public String getChildCode() {
        String id = this.childId;
        if (id == null || id.trim().isEmpty()) {
            return "";
        }

        // Default pattern created in ChildProfileDAO: parentId_child_1
        int idx = id.indexOf("_child_");
        if (idx >= 0) {
            int start = idx + "_child_".length();
            if (start < id.length()) {
                String slot = id.substring(start); // "1", "2", etc
                return "BB" + slot;
            }
        }

        // Fallback to raw id if no pattern match
        return id;
    }

    /**
     * Helper that resolves the correct avatar drawable id.
     * Falls back to ic_child_avatar_placeholder if avatarKey is missing
     * or invalid.
     */
    public int resolveAvatarResId(Context context) {
        String key = avatarKey;
        if (key == null || key.trim().isEmpty()) {
            key = "ic_child_avatar_placeholder";
        }

        int resId = context.getResources().getIdentifier(
                key,
                "drawable",
                context.getPackageName()
        );

        if (resId == 0) {
            resId = context.getResources().getIdentifier(
                    "ic_child_avatar_placeholder",
                    "drawable",
                    context.getPackageName()
            );
        }
        return resId;
    }
}
