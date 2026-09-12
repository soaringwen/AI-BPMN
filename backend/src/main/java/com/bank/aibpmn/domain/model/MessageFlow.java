package com.bank.aibpmn.domain.model;

public class MessageFlow {

    private String id;
    private String sourceId;
    private String targetId;
    private String name;

    public MessageFlow() {
    }

    public MessageFlow(String id, String sourceId, String targetId, String name) {
        this.id = id;
        this.sourceId = sourceId;
        this.targetId = targetId;
        this.name = name;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getSourceId() {
        return sourceId;
    }

    public void setSourceId(String sourceId) {
        this.sourceId = sourceId;
    }

    public String getTargetId() {
        return targetId;
    }

    public void setTargetId(String targetId) {
        this.targetId = targetId;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }
}
