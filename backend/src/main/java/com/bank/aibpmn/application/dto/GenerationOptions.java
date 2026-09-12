package com.bank.aibpmn.application.dto;

public record GenerationOptions(
    boolean includePool,
    boolean includeLanes,
    boolean askClarification,
    Integer maxClarificationQuestions,
    String targetDialect) {

    public static GenerationOptions defaults() {
        return new GenerationOptions(true, true, true, 5, "STANDARD_BPMN_20");
    }
}
