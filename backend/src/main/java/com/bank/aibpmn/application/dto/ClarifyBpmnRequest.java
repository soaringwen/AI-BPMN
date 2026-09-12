package com.bank.aibpmn.application.dto;

import java.util.List;

public record ClarifyBpmnRequest(
    String originalDescription,
    List<ClarificationAnswerDto> answers,
    List<ConversationMessageDto> conversationContext,
    GenerationOptions generationOptions) {
}
