package com.example.brightbuds_app.models;

/**
 * Represents a single play session of one mini game for one child.
 * Each row stores the metrics used for reports and Firebase sync.
 */
public class GameSession {

    // Local primary key of this session in SQLite.
    private long sessionId;

    // Local foreign key that links this session to a child profile.
    private long childId;

    // Identifier of the mini game, for example "FEED_MONSTER" or "MEMORY_MATCH".
    private String gameCode;

    // Start time in epoch milliseconds.
    private long startedAt;

    // End time in epoch milliseconds.
    private long endedAt;

    // Convenience duration in milliseconds. This is derived from end minus start.
    private long durationMs;

    // Total number of correct answers in this session.
    private int correctAnswers;

    // Total number of incorrect attempts in this session.
    private int wrongAttempts;

    // Total attempts. This is correct plus wrong for the session.
    private int attemptsTotal;

    // Stars earned in this session according to the reward rules of the game.
    private int starsEarned;

    // Optional level or difficulty tag used by the game.
    private String level;

    // Indicator that shows whether this row was already pushed to Firebase.
    private boolean synced;

    public GameSession() {
    }

    public GameSession(long sessionId,
                       long childId,
                       String gameCode,
                       long startedAt,
                       long endedAt,
                       long durationMs,
                       int correctAnswers,
                       int wrongAttempts,
                       int attemptsTotal,
                       int starsEarned,
                       String level,
                       boolean synced) {
        this.sessionId = sessionId;
        this.childId = childId;
        this.gameCode = gameCode;
        this.startedAt = startedAt;
        this.endedAt = endedAt;
        this.durationMs = durationMs;
        this.correctAnswers = correctAnswers;
        this.wrongAttempts = wrongAttempts;
        this.attemptsTotal = attemptsTotal;
        this.starsEarned = starsEarned;
        this.level = level;
        this.synced = synced;
    }

    public long getSessionId() {
        return sessionId;
    }

    public void setSessionId(long sessionId) {
        this.sessionId = sessionId;
    }

    public long getChildId() {
        return childId;
    }

    public void setChildId(long childId) {
        this.childId = childId;
    }

    public String getGameCode() {
        return gameCode;
    }

    public void setGameCode(String gameCode) {
        this.gameCode = gameCode;
    }

    public long getStartedAt() {
        return startedAt;
    }

    public void setStartedAt(long startedAt) {
        this.startedAt = startedAt;
    }

    public long getEndedAt() {
        return endedAt;
    }

    public void setEndedAt(long endedAt) {
        this.endedAt = endedAt;
    }

    public long getDurationMs() {
        return durationMs;
    }

    public void setDurationMs(long durationMs) {
        this.durationMs = durationMs;
    }

    public int getCorrectAnswers() {
        return correctAnswers;
    }

    public void setCorrectAnswers(int correctAnswers) {
        this.correctAnswers = correctAnswers;
    }

    public int getWrongAttempts() {
        return wrongAttempts;
    }

    public void setWrongAttempts(int wrongAttempts) {
        this.wrongAttempts = wrongAttempts;
    }

    public int getAttemptsTotal() {
        return attemptsTotal;
    }

    public void setAttemptsTotal(int attemptsTotal) {
        this.attemptsTotal = attemptsTotal;
    }

    public int getStarsEarned() {
        return starsEarned;
    }

    public void setStarsEarned(int starsEarned) {
        this.starsEarned = starsEarned;
    }

    public String getLevel() {
        return level;
    }

    public void setLevel(String level) {
        this.level = level;
    }

    public boolean isSynced() {
        return synced;
    }

    public void setSynced(boolean synced) {
        this.synced = synced;
    }
}
