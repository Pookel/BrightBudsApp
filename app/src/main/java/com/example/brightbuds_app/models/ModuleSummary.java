package com.example.brightbuds_app.models;

/**
 * Holds summary analytics for one module and one child.
 * Used to show rows in the parent reports table.
 */
public class ModuleSummary {

    // Stable id used internally for lookups and mapping to ModuleIds
    private String moduleId;

    // User friendly name such as "Feed the Monster"
    private String moduleName;

    // Aggregated stats from child_analytics table
    private int sessionCount;
    private int totalScore;
    private int totalCorrect;
    private int totalAttempts;
    private long totalTimeMs;

    // Last session information for extra context
    private long lastPlayedMs;
    private int lastScore;
    private double lastAccuracy;
    private long lastSessionTimeMs;
    private int bestScore;

    public ModuleSummary(String moduleId, String moduleName) {
        this.moduleId = moduleId;
        this.moduleName = moduleName;
    }

    public String getModuleId() {
        return moduleId;
    }

    public String getModuleName() {
        return moduleName;
    }

    public int getSessionCount() {
        return sessionCount;
    }

    public void setSessionCount(int sessionCount) {
        this.sessionCount = sessionCount;
    }

    public int getTotalScore() {
        return totalScore;
    }

    public void setTotalScore(int totalScore) {
        this.totalScore = totalScore;
    }

    public int getTotalCorrect() {
        return totalCorrect;
    }

    public void setTotalCorrect(int totalCorrect) {
        this.totalCorrect = totalCorrect;
    }

    public int getTotalAttempts() {
        return totalAttempts;
    }

    public void setTotalAttempts(int totalAttempts) {
        this.totalAttempts = totalAttempts;
    }

    public long getTotalTimeMs() {
        return totalTimeMs;
    }

    public void setTotalTimeMs(long totalTimeMs) {
        this.totalTimeMs = totalTimeMs;
    }

    public long getLastPlayedMs() {
        return lastPlayedMs;
    }

    public void setLastPlayedMs(long lastPlayedMs) {
        this.lastPlayedMs = lastPlayedMs;
    }

    public int getLastScore() {
        return lastScore;
    }

    public void setLastScore(int lastScore) {
        this.lastScore = lastScore;
    }

    public double getLastAccuracy() {
        return lastAccuracy;
    }

    public void setLastAccuracy(double lastAccuracy) {
        this.lastAccuracy = lastAccuracy;
    }

    public long getLastSessionTimeMs() {
        return lastSessionTimeMs;
    }

    public void setLastSessionTimeMs(long lastSessionTimeMs) {
        this.lastSessionTimeMs = lastSessionTimeMs;
    }

    public int getBestScore() {
        return bestScore;
    }

    public void setBestScore(int bestScore) {
        this.bestScore = bestScore;
    }

    /**
     * Average score across all sessions for this module.
     */
    public double getAverageScore() {
        if (sessionCount <= 0) {
            return 0;
        }
        return totalScore * 1.0 / sessionCount;
    }

    /**
     * Overall accuracy ratio across all sessions.
     * 0 to 1 so that UI can convert to percentage.
     */
    public double getAccuracyRatio() {
        if (totalAttempts <= 0) {
            return 0;
        }
        return totalCorrect * 1.0 / totalAttempts;
    }
}
