package com.bank.aibpmn.exception;

import com.bank.aibpmn.agent.ModelOutputInvalidException;
import com.bank.aibpmn.domain.operation.OperationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.List;
import java.util.UUID;

/**
 * 全局异常处理：错误响应不暴露模型网关地址与内部堆栈（DESIGN.md 19 / 20）。
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiError> handleBadRequest(IllegalArgumentException e) {
        log.warn("请求参数错误：{}", e.getMessage());
        return ResponseEntity.badRequest().body(
            ApiError.of("INVALID_REQUEST", e.getMessage(), requestId()));
    }

    @ExceptionHandler(ModelOutputInvalidException.class)
    public ResponseEntity<ApiError> handleModelOutput(ModelOutputInvalidException e) {
        log.warn("模型输出无效：{}", e.getMessage());
        return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(
            ApiError.of("MODEL_OUTPUT_INVALID", e.getMessage(), requestId()));
    }

    @ExceptionHandler(OperationException.class)
    public ResponseEntity<ApiError> handleOperation(OperationException e) {
        log.warn("增量操作失败：{}", e.getMessage());
        return ResponseEntity.badRequest().body(new ApiError(e.getCode(), e.getMessage(), requestId(),
            List.of(new ApiError.ErrorDetail("operationId", e.getOperationId()))));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiError> handleUnexpected(Exception e) {
        log.error("未处理异常", e);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(
            ApiError.of("INTERNAL_ERROR", "服务内部错误，请稍后重试", requestId()));
    }

    private String requestId() {
        return "req-" + UUID.randomUUID().toString().substring(0, 8);
    }
}
