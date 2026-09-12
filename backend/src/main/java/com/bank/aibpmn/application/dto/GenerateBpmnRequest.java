package com.bank.aibpmn.application.dto;

import java.util.List;

public record GenerateBpmnRequest(
    String name,
    String description,
    List<ConversationMessageDto> conversationContext,
    GenerationOptions generationOptions) {
}
