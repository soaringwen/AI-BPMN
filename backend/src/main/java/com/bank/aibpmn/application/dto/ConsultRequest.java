package com.bank.aibpmn.application.dto;

import com.bank.aibpmn.domain.model.ProcessModel;

import java.util.List;

/**
 * mode: EXPLAIN | OPTIMIZE | SEMANTIC_CHECK
 */
public record ConsultRequest(
    String mode,
    String instruction,
    ProcessModel currentModel,
    List<String> selectedElementIds,
    List<ConversationMessageDto> conversationContext) {
}
