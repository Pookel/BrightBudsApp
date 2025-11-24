package com.example.brightbuds_app.models;

/**
 * Represents the parent profile that is stored locally on the device.
 * This data is used for the parent dashboard, avatar and local security.
 */
public class ParentProfile {

    // Local primary key for this parent row in SQLite.
    private long parentId;

    // Email used for login. This mirrors the Firebase Authentication email.
    private String email;

    // Parent display name that is shown on screens.
    private String name;

    // Hash of the parent password or PIN. Plain text passwords are not stored.
    private String passwordHash;

    // File system path of the parent avatar image stored on the device.
    private String avatarPath;

    public ParentProfile() {
    }

    public ParentProfile(long parentId, String email, String name,
                         String passwordHash, String avatarPath) {
        this.parentId = parentId;
        this.email = email;
        this.name = name;
        this.passwordHash = passwordHash;
        this.avatarPath = avatarPath;
    }

    public long getParentId() {
        return parentId;
    }

    public void setParentId(long parentId) {
        this.parentId = parentId;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public void setPasswordHash(String passwordHash) {
        this.passwordHash = passwordHash;
    }

    public String getAvatarPath() {
        return avatarPath;
    }

    public void setAvatarPath(String avatarPath) {
        this.avatarPath = avatarPath;
    }
}
