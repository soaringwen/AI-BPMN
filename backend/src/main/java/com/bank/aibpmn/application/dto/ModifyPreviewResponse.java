package com.bank.aibpmn.application.dto;

import com.bank.aibpmn.domain.diff.ModelDiff;
import com.bank.aibpmn.domain.model.ProcessModel;
import com.bank.aibpmn.domain.operation.ModelOperation;
import com.bank.aibpmn.domain.validation.ValidationResult;

import java.util.List;

public record ModifyPreviewResponse(
    Integer baseRevision,
    String summary,
    boolean clarificationRequired,
    List<QuestionDto> questions,
    ProcessModel proposedModel,
    String proposedBpmnXml,
    List<ModelOperation> operations,
    ModelDiff changes,
    ValidationResult validation) {
}
