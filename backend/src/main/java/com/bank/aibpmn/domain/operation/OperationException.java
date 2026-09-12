package com.bank.aibpmn.domain.operation;

public class OperationException extends RuntimeException {

    private final String operationId;
    private final String code;

    public OperationException(String operationId, String code, String message) {
        super(message);
        this.operationId = operationId;
        this.code = code;
    }

    public String getOperationId() {
        return operationId;
    }

    public String getCode() {
        return code;
    }
}
