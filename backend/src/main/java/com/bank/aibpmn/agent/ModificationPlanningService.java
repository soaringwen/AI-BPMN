package com.bank.aibpmn.agent;

import com.bank.aibpmn.application.dto.ModifyPreviewRequest;
import com.bank.aibpmn.application.dto.ModifyPreviewResponse;
import com.bank.aibpmn.application.dto.QuestionDto;
import com.bank.aibpmn.domain.diff.ModelDiff;
import com.bank.aibpmn.domain.diff.ModelDiffCalculator;
import com.bank.aibpmn.domain.model.NodeType;
import com.bank.aibpmn.domain.model.ProcessModel;
import com.bank.aibpmn.domain.operation.ModelOperation;
import com.bank.aibpmn.domain.operation.OperationApplier;
import com.bank.aibpmn.domain.validation.ProcessModelValidator;
import com.bank.aibpmn.domain.validation.ValidationResult;
import com.bank.aibpmn.infrastructure.ai.LlmClient;
import com.bank.aibpmn.infrastructure.bpmn.BpmnXmlGenerator;
import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 对话修改：AI生成增量操作→模拟应用→校验→差异→预览XML（DESIGN.md 13）。
 */
@Service
public class ModificationPlanningService {

    private static final Logger log = LoggerFactory.getLogger(ModificationPlanningService.class);

    private final LlmClient llmClient;
    private final StructuredOutputService structuredOutputService;
    private final PromptTemplateRegistry prompts;
    private final ProcessModelValidator validator;
    private final ModelDiffCalculator diffCalculator;
    private final BpmnXmlGenerator xmlGenerator;

    public ModificationPlanningService(LlmClient llmClient,
                                       StructuredOutputService structuredOutputService,
                                       PromptTemplateRegistry prompts,
                                       ProcessModelValidator validator,
                                       ModelDiffCalculator diffCalculator,
                                       BpmnXmlGenerator xmlGenerator) {
        this.llmClient = llmClient;
        this.structuredOutputService = structuredOutputService;
        this.prompts = prompts;
        this.validator = validator;
        this.diffCalculator = diffCalculator;
        this.xmlGenerator = xmlGenerator;
    }

    public ModifyPreviewResponse preview(ModifyPreviewRequest request) {
        ProcessModel current = request.currentModel();
        if (current == null) {
            throw new IllegalArgumentException("当前模型为空，无法执行修改");
        }
        if (request.instruction() == null || request.instruction().isBlank()) {
            throw new IllegalArgumentException("修改指令不能为空");
        }

        String modelJson = toModelSummaryJson(current);
        String selected = request.selectedElementIds() == null || request.selectedElementIds().isEmpty()
            ? "（无）" : String.join(", ", request.selectedElementIds());

        String prompt = prompts.render("modify", Map.of(
            "CURRENT_MODEL", modelJson,
            "SELECTED", selected,
            "INSTRUCTION", request.instruction()));

        JsonNode result = callModel(prompt, current);

        boolean clarificationRequired = result.path("clarificationRequired").asBoolean(false);
        if (clarificationRequired) {
            List<QuestionDto> questions = new ArrayList<>();
            result.path("questions").forEach(q -> {
                List<String> options = new ArrayList<>();
                q.path("options").forEach(o -> options.add(o.asText()));
                questions.add(new QuestionDto(
                    q.path("questionId").asText("q1"),
                    q.path("question").asText(),
                    q.path("type").asText("SINGLE_SELECT"),
                    options,
                    q.path("required").asBoolean(true)));
            });
            return new ModifyPreviewResponse(request.baseRevision(), result.path("summary").asText("需要澄清"),
                true, questions, null, null, List.of(), new ModelDiff(), ValidationResult.empty());
        }

        List<ModelOperation> operations = readOperations(result.path("operations"));

        // 模拟应用操作并校验（DESIGN.md 3.4）
        ProcessModel proposed = new OperationApplier(current).apply(operations);
        ValidationResult validation = validator.validate(proposed);
        String xml = xmlGenerator.generate(proposed);
        ModelDiff diff = diffCalculator.diff(current, proposed);

        return new ModifyPreviewResponse(request.baseRevision(),
            result.path("summary").asText("AI增量修改"),
            false, List.of(), proposed, xml, operations, diff, validation);
    }

    private List<ModelOperation> readOperations(JsonNode operationsNode) {
        List<ModelOperation> operations = new ArrayList<>();
        if (!operationsNode.isArray()) {
            throw new ModelOutputInvalidException("模型输出缺少operations数组");
        }
        for (JsonNode opNode : operationsNode) {
            String typeStr = opNode.path("type").asText("");
            ModelOperation operation;
            try {
                operation = new ModelOperation(
                    opNode.path("operationId").asText("op-" + (operations.size() + 1)),
                    com.bank.aibpmn.domain.operation.OperationType.valueOf(typeStr),
                    opNode.hasNonNull("targetId") ? opNode.get("targetId").asText() : null,
                    structuredOutputService.objectMapper().convertValue(opNode.path("payload"), Map.class));
            } catch (IllegalArgumentException e) {
                throw new ModelOutputInvalidException("不允许的操作类型（白名单校验失败）：" + typeStr);
            }
            operations.add(operation);
        }
        return operations;
    }

    /**
     * 发送精简ProcessModel而非完整XML（DESIGN.md 11.4）。
     */
    public String toModelSummaryJson(ProcessModel model) {
        StringBuilder sb = new StringBuilder();
        sb.append("{\"process\": {\"id\": \"").append(model.getProcess().getId())
            .append("\", \"name\": \"").append(model.getProcess().getName()).append("\"}, ");
        sb.append("\"lanes\": [");
        for (int i = 0; i < model.getLanes().size(); i++) {
            var lane = model.getLanes().get(i);
            if (i > 0) {
                sb.append(", ");
            }
            sb.append("{\"id\": \"").append(lane.getId()).append("\", \"name\": \"").append(lane.getName()).append("\"}");
        }
        sb.append("], ");
        sb.append("\"nodes\": [");
        for (int i = 0; i < model.getNodes().size(); i++) {
            var node = model.getNodes().get(i);
            if (i > 0) {
                sb.append(", ");
            }
            sb.append("{\"id\": \"").append(node.getId()).append("\", \"type\": \"")
                .append(node.getType()).append("\", \"name\": \"")
                .append(node.getName() == null ? "" : node.getName()).append("\", \"laneId\": \"")
                .append(node.getLaneId() == null ? "" : node.getLaneId()).append("\"}");
        }
        sb.append("], ");
        sb.append("\"sequenceFlows\": [");
        for (int i = 0; i < model.getSequenceFlows().size(); i++) {
            var flow = model.getSequenceFlows().get(i);
            if (i > 0) {
                sb.append(", ");
            }
            sb.append("{\"id\": \"").append(flow.getId()).append("\", \"sourceId\": \"")
                .append(flow.getSourceId()).append("\", \"targetId\": \"").append(flow.getTargetId())
                .append("\", \"name\": \"").append(flow.getName() == null ? "" : flow.getName()).append("\"}");
        }
        sb.append("]}");
        return sb.toString();
    }

    private JsonNode callModel(String prompt, ProcessModel current) {
        String system = "你是业务流程建模助手。只输出JSON，不得输出Markdown，不得生成BPMN XML。";
        try {
            String raw = llmClient.complete(system, prompt);
            return structuredOutputService.parse(substituteMockPlaceholders(raw, current));
        } catch (ModelOutputInvalidException e) {
            throw e;
        } catch (Exception first) {
            log.warn("模型输出首次处理失败：{}", first.getMessage());
            String retryPrompt = prompt + "\n\n上一次输出错误：" + first.getMessage()
                + "\n请严格只输出符合要求的JSON对象。";
            try {
                String raw = llmClient.complete(system, retryPrompt);
                return structuredOutputService.parse(substituteMockPlaceholders(raw, current));
            } catch (Exception second) {
                throw new ModelOutputInvalidException("模型输出两次处理后仍无效：" + second.getMessage());
            }
        }
    }

    /**
     * 仅演示模式：Mock输出中的占位符替换为当前模型的真实ID。
     */
    private String substituteMockPlaceholders(String raw, ProcessModel current) {
        if (raw == null || !raw.contains("__FIRST_")) {
            return raw;
        }
        String firstUserTask = current.getNodes().stream()
            .filter(n -> n.getType() == NodeType.USER_TASK)
            .map(n -> n.getId())
            .findFirst().orElse("");
        String firstGateway = current.getNodes().stream()
            .filter(n -> n.getType() == NodeType.EXCLUSIVE_GATEWAY)
            .map(n -> n.getId())
            .findFirst().orElse("");
        String firstGatewayFlow = current.getSequenceFlows().stream()
            .filter(f -> f.getSourceId().equals(firstGateway))
            .map(f -> f.getId())
            .findFirst().orElse("");
        return raw.replace("__FIRST_USER_TASK__", firstUserTask)
            .replace("__FIRST_GATEWAY_FLOW__", firstGatewayFlow)
            .replace("__FIRST_GATEWAY__", firstGateway);
    }
}
