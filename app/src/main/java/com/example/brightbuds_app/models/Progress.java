package com.example.brightbuds_app.models;

import com.google.firebase.Timestamp;
import com.google.firebase.firestore.PropertyName;

/**
 * Progress:
 * Represents each child's activity and learning progress record in Firestore.
 * Includes helper logic for determining completion and module name mapping.
 */
public class Progress {

    private String progressId;
    private String parentId;
    private String childId;
    private String moduleId;
    private String status;
    private double score;
    private long timeSpent;
    private long timestamp;

    // Firestore fields for analytics and charting
    private int plays;
    private String type;
    private boolean completionStatus;

    // Flexible lastUpdated field — handles both Timestamp & Long
    private Object lastUpdated;

    // Default constructor (Firestore)
    public Progress() {}

    // --- Firestore Mappings ---
    @PropertyName("progressId")
    public String getProgressId() { return progressId; }
    @PropertyName("progressId")
    public void setProgressId(String progressId) { this.progressId = progressId; }

    @PropertyName("parentId")
    public String getParentId() { return parentId; }
    @PropertyName("parentId")
    public void setParentId(String parentId) { this.parentId = parentId; }

    @PropertyName("childId")
    public String getChildId() { return childId; }
    @PropertyName("childId")
    public void setChildId(String childId) { this.childId = childId; }

    @PropertyName("moduleId")
    public String getModuleId() { return moduleId; }
    @PropertyName("moduleId")
    public void setModuleId(String moduleId) { this.moduleId = moduleId; }

    @PropertyName("status")
    public String getStatus() { return status; }
    @PropertyName("status")
    public void setStatus(String status) { this.status = status; }

    @PropertyName("score")
    public double getScore() { return score; }
    @PropertyName("score")
    public void setScore(double score) { this.score = score; }

    @PropertyName("timeSpent")
    public long getTimeSpent() { return timeSpent; }
    @PropertyName("timeSpent")
    public void setTimeSpent(long timeSpent) { this.timeSpent = timeSpent; }

    @PropertyName("timestamp")
    public long getTimestamp() { return timestamp; }
    @PropertyName("timestamp")
    public void setTimestamp(long timestamp) { this.timestamp = timestamp; }

    @PropertyName("plays")
    public int getPlays() { return plays; }
    @PropertyName("plays")
    public void setPlays(int plays) { this.plays = plays; }

    @PropertyName("type")
    public String getType() { return type; }
    @PropertyName("type")
    public void setType(String type) { this.type = type; }

    @PropertyName("completionStatus")
    public boolean isCompletionStatus() { return completionStatus; }
    @PropertyName("completionStatus")
    public void setCompletionStatus(boolean completionStatus) { this.completionStatus = completionStatus; }

    @PropertyName("lastUpdated")
    public Object getLastUpdated() { return lastUpdated; }
    @PropertyName("lastUpdated")
    public void setLastUpdated(Object lastUpdated) { this.lastUpdated = lastUpdated; }

    // Timestamp utility
    public Timestamp getLastUpdatedTimestamp() {
        if (lastUpdated instanceof Timestamp) {
            return (Timestamp) lastUpdated;
        } else if (lastUpdated instanceof Long) {
            return new Timestamp(((Long) lastUpdated) / 1000, 0);
        }
        return null;
    }

    // Completion helper
    /**
     * Determines whether this progress record represents a completed module.
     * A module is considered completed if:
     *  - completionStatus == true, or
     *  - score >= 70, or
     *  - status equals "completed"
     */
    public boolean isModuleCompleted() {
        if (completionStatus) return true;
        if (score >= 70) return true;
        return status != null && status.equalsIgnoreCase("completed");
    }

    // Module Name Mapping (for reports and charts)
    public String getModuleName() {
        if (moduleId == null) return "Unknown Module";
        switch (moduleId.toLowerCase()) {
            case "module_abc_song":
                return "ABC Song";
            case "module_123_song":
                return "123 Song";
            case "module_feed_the_monster":
                return "Feed the Monster";
            case "module_match_the_letter":
                return "Match the Letter";
            case "module_memory_match":
                return "Memory Match";
            case "module_word_builder":
                return "Word Builder";
            case "module_my_family":
                return "My Family Album";
            default:
                return moduleId; // fallback if unrecognized
        }
    }

    // toString() for debugging
    @Override
    public String toString() {
        return "Progress{" +
                "childId='" + childId + '\'' +
                ", moduleId='" + moduleId + '\'' +
                ", moduleName='" + getModuleName() + '\'' +
                ", plays=" + plays +
                ", score=" + score +
                ", completed=" + isModuleCompleted() +
                ", lastUpdated=" + lastUpdated +
                '}';
    }
}
