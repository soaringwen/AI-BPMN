package com.bank.aibpmn.agent;

import com.bank.aibpmn.application.dto.ConsultRequest;
import com.bank.aibpmn.application.dto.ConsultResponse;
import com.bank.aibpmn.domain.validation.ProcessModelValidator;
import com.bank.aibpmn.domain.validation.ValidationIssue;
import com.bank.aibpmn.infrastructure.ai.LlmClient;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 流程解释、优化建议与语义检查（DESIGN.md 5.2）。
 */
@Service
public class ProcessConsultService {

    private final LlmClient llmClient;
    private final StructuredOutputService structuredOutputService;
    private final PromptTemplateRegistry prompts;
    private final ModificationPlanningService modelJsonHelper;
    private final ProcessModelValidator validator;

    public ProcessConsultService(LlmClient llmClient,
                                 StructuredOutputService structuredOutputService,
                                 PromptTemplateRegistry prompts,
                                 ModificationPlanningService modelJsonHelper,
                                 ProcessModelValidator validator) {
        this.llmClient = llmClient;
        this.structuredOutputService = structuredOutputService;
        this.prompts = prompts;
        this.modelJsonHelper = modelJsonHelper;
        this.validator = validator;
    }

    public ConsultResponse consult(ConsultRequest request) {
        String modeLabel = switch (request.mode() == null ? "EXPLAIN" : request.mode()) {
            case "OPTIMIZE" -> "请提出流程简化与优化建议";
            case "SEMANTIC_CHECK" -> "请进行业务语义检查";
            default -> "请解释当前流程";
        };

        String localCheck = describeLocalValidation(request);
        String instruction = (request.instruction() == null || request.instruction().isBlank()
            ? modeLabel : modeLabel + "。用户补充：" + request.instruction())
            + "\n\n系统本地规则校验结果：\n" + localCheck;

        String modelJson = request.currentModel() == null
            ? "（当前无模型）"
            : modelJsonHelper.toModelSummaryJson(request.currentModel());

        String prompt = prompts.render("consult", Map.of(
            "CURRENT_MODEL", modelJson,
            "INSTRUCTION", instruction));

        String system = "你是业务流程建模助手。只输出JSON，不得输出Markdown，不得生成BPMN XML。";
        String raw = llmClient.complete(system, prompt);
        JsonNode result = structuredOutputService.parse(raw);

        List<String> suggestions = new ArrayList<>();
        result.path("suggestions").forEach(s -> suggestions.add(s.asText()));
        return new ConsultResponse(result.path("answer").asText(""), suggestions);
    }

    private String describeLocalValidation(ConsultRequest request) {
        if (request.currentModel() == null) {
            return "（当前无模型）";
        }
        var result = validator.validate(request.currentModel());
        if (result.isValid() && result.getWarnings().isEmpty()) {
            return "无错误、无警告";
        }
        String errors = result.getErrors().stream()
            .map(ValidationIssue::getMessage).collect(Collectors.joining("；"));
        String warnings = result.getWarnings().stream()
            .map(ValidationIssue::getMessage).collect(Collectors.joining("；"));
        return (errors.isEmpty() ? "" : "错误：" + errors)
            + (warnings.isEmpty() ? "" : (errors.isEmpty() ? "" : "\n") + "警告：" + warnings);
    }
}
