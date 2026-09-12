package com.bank.aibpmn.domain.validation;

public class ValidationIssue {

    private String code;
    private Severity severity;
    private String message;
    private String elementId;

    public ValidationIssue() {
    }

    public ValidationIssue(String code, Severity severity, String message, String elementId) {
        this.code = code;
        this.severity = severity;
        this.message = message;
        this.elementId = elementId;
    }

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }

    public Severity getSeverity() {
        return severity;
    }

    public void setSeverity(Severity severity) {
        this.severity = severity;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public String getElementId() {
        return elementId;
    }

    public void setElementId(String elementId) {
        this.elementId = elementId;
    }
}
