package com.bank.aibpmn.application.dto;

import com.bank.aibpmn.domain.model.ProcessModel;

import java.util.List;

public record ModifyPreviewRequest(
    Integer baseRevision,
    String instruction,
    ProcessModel currentModel,
    List<String> selectedElementIds,
    List<ConversationMessageDto> conversationContext) {
}
