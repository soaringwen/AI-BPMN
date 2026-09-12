package com.bank.aibpmn.domain.operation;

import java.util.Map;

/**
 * 一条增量修改操作。AI新增元素使用temporaryId，正式ID由后端生成。
 */
public class ModelOperation {

    private String operationId;
    private OperationType type;
    private String targetId;
    private Map<String, Object> payload = Map.of();

    public ModelOperation() {
    }

    public ModelOperation(String operationId, OperationType type, String targetId, Map<String, Object> payload) {
        this.operationId = operationId;
        this.type = type;
        this.targetId = targetId;
        this.payload = payload == null ? Map.of() : payload;
    }

    public String getOperationId() {
        return operationId;
    }

    public void setOperationId(String operationId) {
        this.operationId = operationId;
    }

    public OperationType getType() {
        return type;
    }

    public void setType(OperationType type) {
        this.type = type;
    }

    public String getTargetId() {
        return targetId;
    }

    public void setTargetId(String targetId) {
        this.targetId = targetId;
    }

    public Map<String, Object> getPayload() {
        return payload;
    }

    public void setPayload(Map<String, Object> payload) {
        this.payload = payload;
    }
}
