package com.bank.aibpmn.application.dto;

import java.util.List;

public record ConsultResponse(String answer, List<String> suggestions) {
}
