package com.bank.aibpmn.agent;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

/**
 * 模型输出处理：提取JSON、剥离Markdown、Jackson解析。
 * 失败时由调用方携带错误信息重试一次（DESIGN.md 11.5）。
 */
@Component
public class StructuredOutputService {

    private final ObjectMapper objectMapper = new ObjectMapper();

    public JsonNode parse(String rawOutput) {
        String json = extractJson(rawOutput);
        try {
            return objectMapper.readTree(json);
        } catch (Exception e) {
            throw new ModelOutputInvalidException("模型输出不是合法JSON：" + e.getMessage());
        }
    }

    /**
     * 提取JSON：剥离Markdown代码块与外围文本。
     */
    public String extractJson(String raw) {
        if (raw == null) {
            throw new ModelOutputInvalidException("模型输出为空");
        }
        String text = raw.trim();
        if (text.startsWith("```")) {
            int firstNewline = text.indexOf('\n');
            if (firstNewline > 0) {
                text = text.substring(firstNewline + 1);
            }
            int closingFence = text.lastIndexOf("```");
            if (closingFence >= 0) {
                text = text.substring(0, closingFence);
            }
            text = text.trim();
        }
        int start = text.indexOf('{');
        int end = text.lastIndexOf('}');
        if (start < 0 || end <= start) {
            throw new ModelOutputInvalidException("模型输出中未找到JSON对象");
        }
        return text.substring(start, end + 1);
    }

    public ObjectMapper objectMapper() {
        return objectMapper;
    }
}
