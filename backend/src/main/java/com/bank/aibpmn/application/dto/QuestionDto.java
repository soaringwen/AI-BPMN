package com.bank.aibpmn.application.dto;

import java.util.List;

public record QuestionDto(String questionId, String question, String type, List<String> options, boolean required) {
}
