package com.bank.aibpmn.infrastructure.dialect;

import java.util.Map;

public record DialectConversionOptions(Map<String, Object> options) {

    public static DialectConversionOptions empty() {
        return new DialectConversionOptions(Map.of());
    }
}
