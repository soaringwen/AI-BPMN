package com.bank.aibpmn.exception;

import java.util.List;

/**
 * 统一错误格式（DESIGN.md 20）。
 */
public record ApiError(String code, String message, String requestId, List<ErrorDetail> details) {

    public record ErrorDetail(String path, String message) {
    }

    public static ApiError of(String code, String message, String requestId) {
        return new ApiError(code, message, requestId, List.of());
    }
}
