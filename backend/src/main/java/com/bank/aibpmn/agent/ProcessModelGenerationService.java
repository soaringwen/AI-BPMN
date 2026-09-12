package com.bank.aibpmn.agent;

import com.bank.aibpmn.application.dto.AiBpmnResponse;
import com.bank.aibpmn.application.dto.ClarifyBpmnRequest;
import com.bank.aibpmn.application.dto.GenerateBpmnRequest;
import com.bank.aibpmn.application.dto.GenerationOptions;
import com.bank.aibpmn.application.dto.QuestionDto;
import com.bank.aibpmn.config.AppProperties;
import com.bank.aibpmn.domain.model.ProcessModel;
import com.bank.aibpmn.domain.validation.ProcessModelValidator;
import com.bank.aibpmn.domain.validation.ValidationResult;
import com.bank.aibpmn.infrastructure.ai.LlmClient;
import com.bank.aibpmn.infrastructure.bpmn.BpmnXmlGenerator;
import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 新建流程处理：描述→澄清或ProcessModel草案→规范化→校验→标准XML（DESIGN.md 12）。
 */
@Service
public class ProcessModelGenerationService {

    private static final Logger log = LoggerFactory.getLogger(ProcessModelGenerationService.class);

    private final LlmClient llmClient;
    private final StructuredOutputService structuredOutputService;
    private final PromptTemplateRegistry prompts;
    private final DraftNormalizer draftNormalizer;
    private final ProcessModelValidator validator;
    private final BpmnXmlGenerator xmlGenerator;
    private final AppProperties properties;

    public ProcessModelGenerationService(LlmClient llmClient,
                                         StructuredOutputService structuredOutputService,
                                         PromptTemplateRegistry prompts,
                                         DraftNormalizer draftNormalizer,
                                         ProcessModelValidator validator,
                                         BpmnXmlGenerator xmlGenerator,
                                         AppProperties properties) {
        this.llmClient = llmClient;
        this.structuredOutputService = structuredOutputService;
        this.prompts = prompts;
        this.draftNormalizer = draftNormalizer;
        this.validator = validator;
        this.xmlGenerator = xmlGenerator;
        this.properties = properties;
    }

    public AiBpmnResponse generate(GenerateBpmnRequest request) {
        String description = requireDescription(request.description());
        GenerationOptions options = request.generationOptions() == null
            ? GenerationOptions.defaults() : request.generationOptions();
        int maxQuestions = options.maxClarificationQuestions() == null
            ? properties.getAi().getMaxClarificationQuestions() : options.maxClarificationQuestions();

        String clarifications = "（无）";
        String prompt = prompts.render("generate", Map.of(
            "DESCRIPTION", description,
            "CLARIFICATIONS", clarifications,
            "MAX_QUESTIONS", String.valueOf(maxQuestions)));

        JsonNode result = callModel(prompt);

        if ("CLARIFICATION_REQUIRED".equalsIgnoreCase(result.path("status").asText())) {
            List<QuestionDto> questions = readQuestions(result.path("questions"), maxQuestions);
            Map<String, Object> context = new LinkedHashMap<>();
            context.put("originalDescription", description);
            context.put("normalizedFacts", List.of());
            return new AiBpmnResponse(AiBpmnResponse.CLARIFICATION_REQUIRED, null, null,
                ValidationResult.empty(), questions, context, null);
        }

        return buildCompleted(result.path("processDraft"), description, options);
    }

    public AiBpmnResponse clarify(ClarifyBpmnRequest request) {
        String description = requireDescription(request.originalDescription());
        GenerationOptions options = request.generationOptions() == null
            ? GenerationOptions.defaults() : request.generationOptions();
        int maxQuestions = options.maxClarificationQuestions() == null
            ? properties.getAi().getMaxClarificationQuestions() : options.maxClarificationQuestions();

        String answers = "（无）";
        if (request.answers() != null && !request.answers().isEmpty()) {
            answers = request.answers().stream()
                .map(a -> "问：" + safe(a.question()) + "\n答：" + safe(a.answer()))
                .collect(Collectors.joining("\n\n"));
        }

        String prompt = prompts.render("generate", Map.of(
            "DESCRIPTION", description,
            "CLARIFICATIONS", answers,
            "MAX_QUESTIONS", String.valueOf(maxQuestions)));

        JsonNode result = callModel(prompt);

        if ("CLARIFICATION_REQUIRED".equalsIgnoreCase(result.path("status").asText())) {
            List<QuestionDto> questions = readQuestions(result.path("questions"), maxQuestions);
            Map<String, Object> context = new LinkedHashMap<>();
            context.put("originalDescription", description);
            context.put("normalizedFacts", List.of());
            return new AiBpmnResponse(AiBpmnResponse.CLARIFICATION_REQUIRED, null, null,
                ValidationResult.empty(), questions, context, null);
        }

        return buildCompleted(result.path("processDraft"), description, options);
    }

    private AiBpmnResponse buildCompleted(JsonNode draft, String description, GenerationOptions options) {
        if (draft.isMissingNode() || draft.isNull() || draft.isEmpty()) {
            throw new ModelOutputInvalidException("模型输出缺少processDraft");
        }
        ProcessModel model = draftNormalizer.normalize(draft, description, options.includePool());
        ValidationResult validation = validator.validate(model);
        String xml = xmlGenerator.generate(model);
        return new AiBpmnResponse(AiBpmnResponse.COMPLETED, model, xml, validation,
            List.of(), null, "已生成流程：" + model.getProcess().getName());
    }

    private List<QuestionDto> readQuestions(JsonNode questionsNode, int maxQuestions) {
        List<QuestionDto> questions = new ArrayList<>();
        if (questionsNode.isArray()) {
            for (JsonNode q : questionsNode) {
                List<String> options = new ArrayList<>();
                q.path("options").forEach(o -> options.add(o.asText()));
                questions.add(new QuestionDto(
                    q.path("questionId").asText("q" + (questions.size() + 1)),
                    q.path("question").asText(),
                    q.path("type").asText("SINGLE_SELECT"),
                    options,
                    q.path("required").asBoolean(true)));
                if (questions.size() >= maxQuestions) {
                    break;
                }
            }
        }
        return questions;
    }

    /**
     * 调用模型；失败时携带错误信息重试一次；第二次失败返回明确错误（DESIGN.md 11.5）。
     */
    private JsonNode callModel(String prompt) {
        String system = "你是业务流程建模助手。只输出JSON，不得输出Markdown，不得生成BPMN XML。";
        String raw;
        try {
            raw = llmClient.complete(system, prompt);
            return structuredOutputService.parse(raw);
        } catch (Exception first) {
            log.warn("模型输出首次处理失败：{}", first.getMessage());
            String retryPrompt = prompt + "\n\n上一次输出错误：" + first.getMessage()
                + "\n请严格只输出符合要求的JSON对象。";
            try {
                raw = llmClient.complete(system, retryPrompt);
                return structuredOutputService.parse(raw);
            } catch (Exception second) {
                throw new ModelOutputInvalidException("模型输出两次处理后仍无效：" + second.getMessage());
            }
        }
    }

    private String requireDescription(String description) {
        if (description == null || description.isBlank()) {
            throw new IllegalArgumentException("流程描述不能为空");
        }
        if (description.length() > properties.getAi().getMaxInputCharacters()) {
            throw new IllegalArgumentException("流程描述超过最大长度限制");
        }
        return description;
    }

    private String safe(String value) {
        return value == null ? "（无）" : value;
    }
}
