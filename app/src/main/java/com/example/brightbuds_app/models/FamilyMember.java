package com.example.brightbuds_app.models;

/**
 * Represents one person in the family tree.
 * These records are shared by all children of the same parent.
 */
public class FamilyMember {

    // Local primary key in SQLite.
    private long familyId;

    // Local foreign key that links this family member to a parent.
    private long parentId;

    // First name that will be shown on the family card and in the game.
    private String firstName;

    // Relationship text such as "Mom", "Grandpa" or "Sister".
    private String relationship;

    // File system path of the family member photo.
    private String imagePath;

    public FamilyMember() {
    }

    public FamilyMember(long familyId,
                        long parentId,
                        String firstName,
                        String relationship,
                        String imagePath) {
        this.familyId = familyId;
        this.parentId = parentId;
        this.firstName = firstName;
        this.relationship = relationship;
        this.imagePath = imagePath;
    }

    public long getFamilyId() {
        return familyId;
    }

    public void setFamilyId(long familyId) {
        this.familyId = familyId;
    }

    public long getParentId() {
        return parentId;
    }

    public void setParentId(long parentId) {
        this.parentId = parentId;
    }

    public String getFirstName() {
        return firstName;
    }

    public void setFirstName(String firstName) {
        this.firstName = firstName;
    }

    public String getRelationship() {
        return relationship;
    }

    public void setRelationship(String relationship) {
        this.relationship = relationship;
    }

    public String getImagePath() {
        return imagePath;
    }

    public void setImagePath(String imagePath) {
        this.imagePath = imagePath;
    }
}
