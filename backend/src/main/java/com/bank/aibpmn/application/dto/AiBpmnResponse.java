package com.bank.aibpmn.application.dto;

import com.bank.aibpmn.domain.model.ProcessModel;
import com.bank.aibpmn.domain.validation.ValidationResult;

import java.util.List;
import java.util.Map;

/**
 * AI生成响应：COMPLETED（含模型与XML）或CLARIFICATION_REQUIRED（含澄清问题）。
 */
public record AiBpmnResponse(
    String status,
    ProcessModel processModel,
    String bpmnXml,
    ValidationResult validation,
    List<QuestionDto> questions,
    Map<String, Object> clarificationContext,
    String summary) {

    public static final String COMPLETED = "COMPLETED";
    public static final String CLARIFICATION_REQUIRED = "CLARIFICATION_REQUIRED";
}
