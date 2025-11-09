package com.example.brightbuds_app.models;

import com.example.brightbuds_app.utils.EncryptionUtil;
import java.util.Date;
import java.util.Map;

/**
 * ChildProfile:
 * Represents each child account linked to a parent profile.
 * Supports encryption/decryption for sensitive data and includes
 * progress and star calculation for 7-module curriculum.
 */
public class ChildProfile {

    private static final int TOTAL_MODULES = 7; // Used for progress and stars calculation

    private String childId;
    private String parentId;
    private String name;
    private String gender;
    private String displayName;
    private int age;
    private String learningLevel;
    private boolean active;
    private int progress;           // Computed progress percentage (0–100)
    private int stars;              // Computed star rating (0–5)
    private int completedModules;   // Number of completed modules (0–7)
    private Date createdAt;

    // Default constructor
    public ChildProfile() {
        this.active = true;
        this.progress = 0;
        this.stars = 0;
        this.completedModules = 0;
        this.createdAt = new Date();
    }

    // Parameterized constructor
    public ChildProfile(String parentId, String name, int age, String gender, String learningLevel) {
        this(); // call default constructor
        this.parentId = parentId;
        this.name = name;
        this.age = age;
        this.gender = gender;
        this.learningLevel = learningLevel;
        this.displayName = name;
    }

    // Encryption helpers
    public String getEncryptedName() {
        return EncryptionUtil.encrypt(name != null ? name : "");
    }

    public void setDecryptedName(String encrypted) {
        name = encrypted != null ? EncryptionUtil.decrypt(encrypted) : "";
    }

    public String getEncryptedGender() {
        return EncryptionUtil.encrypt(gender != null ? gender : "");
    }

    public void setDecryptedGender(String encrypted) {
        gender = encrypted != null ? EncryptionUtil.decrypt(encrypted) : "";
    }

    public String getEncryptedDisplayName() {
        return EncryptionUtil.encrypt(displayName != null ? displayName : "");
    }

    public void setDecryptedDisplayName(String encrypted) {
        displayName = encrypted != null ? EncryptionUtil.decrypt(encrypted) : "";
    }

    // Standard Getters/Setters
    public String getChildId() { return childId; }
    public void setChildId(String childId) { this.childId = childId; }

    public String getParentId() { return parentId; }
    public void setParentId(String parentId) { this.parentId = parentId; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getGender() { return gender; }
    public void setGender(String gender) { this.gender = gender; }

    public String getDisplayName() { return displayName; }
    public void setDisplayName(String displayName) { this.displayName = displayName; }

    public int getAge() { return age; }
    public void setAge(int age) { this.age = age; }

    public String getLearningLevel() { return learningLevel; }
    public void setLearningLevel(String learningLevel) { this.learningLevel = learningLevel; }

    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }

    public int getProgress() { return progress; }
    public void setProgress(int progress) { this.progress = progress; }

    public int getStars() { return stars; }
    public void setStars(int stars) { this.stars = stars; }

    public int getCompletedModules() { return completedModules; }
    public void setCompletedModules(int completedModules) {
        this.completedModules = Math.max(0, Math.min(completedModules, TOTAL_MODULES));
        recalculateProgressAndStars();
    }

    public Date getCreatedAt() { return createdAt; }
    public void setCreatedAt(Date createdAt) { this.createdAt = createdAt; }

    public Map<Object, Object> getAvatarUrl() {
        return java.util.Collections.emptyMap();
    }

    // Derived values
    private void recalculateProgressAndStars() {
        this.progress = (int) Math.round((completedModules / (double) TOTAL_MODULES) * 100);
        this.stars = (int) Math.round((completedModules / (double) TOTAL_MODULES) * 5);
        if (progress > 100) progress = 100;
        if (stars > 5) stars = 5;
    }

    @Override
    public String toString() {
        return "ChildProfile{" +
                "name='" + displayName + '\'' +
                ", modules=" + completedModules +
                ", progress=" + progress +
                "%, stars=" + stars +
                '}';
    }
}
