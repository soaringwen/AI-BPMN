package com.bank.aibpmn.application.dto;

public record ConversationMessageDto(String role, String content) {

    public String normalizedRole() {
        return role == null ? "USER" : role.toUpperCase();
    }
}
