package com.bank.aibpmn.domain.model;

public class SequenceFlow {

    private String id;
    private String sourceId;
    private String targetId;
    private String name;
    private String condition;
    private boolean defaultFlow = false;

    public SequenceFlow() {
    }

    public SequenceFlow(String id, String sourceId, String targetId, String name, String condition,
                        boolean defaultFlow) {
        this.id = id;
        this.sourceId = sourceId;
        this.targetId = targetId;
        this.name = name;
        this.condition = condition;
        this.defaultFlow = defaultFlow;
    }

    public SequenceFlow copy() {
        return new SequenceFlow(id, sourceId, targetId, name, condition, defaultFlow);
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

    public String getCondition() {
        return condition;
    }

    public void setCondition(String condition) {
        this.condition = condition;
    }

    public boolean isDefaultFlow() {
        return defaultFlow;
    }

    public void setDefaultFlow(boolean defaultFlow) {
        this.defaultFlow = defaultFlow;
    }
}
