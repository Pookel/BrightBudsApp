package com.example.brightbuds_app.models;

/**
 * SyncItem — Represents a queued record waiting to sync with Firestore.
 * Used for generic sync operations (insert/update/delete).
 */
public class SyncItem {
    private String id;
    private String tableName;
    private String recordId;
    private String operation;

    public SyncItem() {}

    public SyncItem(String id, String tableName, String recordId, String operation) {
        this.id = id;
        this.tableName = tableName;
        this.recordId = recordId;
        this.operation = operation;
    }

    // Getters and Setters
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getTableName() { return tableName; }
    public void setTableName(String tableName) { this.tableName = tableName; }

    public String getRecordId() { return recordId; }
    public void setRecordId(String recordId) { this.recordId = recordId; }

    public String getOperation() { return operation; }
    public void setOperation(String operation) { this.operation = operation; }
}
