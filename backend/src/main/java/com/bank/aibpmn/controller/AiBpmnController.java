package com.bank.aibpmn.controller;

import com.bank.aibpmn.agent.BpmnAgentOrchestrator;
import com.bank.aibpmn.application.dto.AiBpmnResponse;
import com.bank.aibpmn.application.dto.ClarifyBpmnRequest;
import com.bank.aibpmn.application.dto.ConsultRequest;
import com.bank.aibpmn.application.dto.ConsultResponse;
import com.bank.aibpmn.application.dto.GenerateBpmnRequest;
import com.bank.aibpmn.application.dto.ModifyPreviewRequest;
import com.bank.aibpmn.application.dto.ModifyPreviewResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * AI API（DESIGN.md 15.1）。后端无状态，不保存会话。
 */
@RestController
@RequestMapping("/api/v1/ai/bpmn")
@Tag(name = "AI BPMN", description = "AI流程生成与修改API")
public class AiBpmnController {

    private final BpmnAgentOrchestrator orchestrator;

    public AiBpmnController(BpmnAgentOrchestrator orchestrator) {
        this.orchestrator = orchestrator;
    }

    @PostMapping("/generate")
    @Operation(summary = "根据文字描述生成流程")
    public AiBpmnResponse generate(@RequestBody GenerateBpmnRequest request) {
        return orchestrator.generate(request);
    }

    @PostMapping("/clarify")
    @Operation(summary = "携带澄清答案重新生成")
    public AiBpmnResponse clarify(@RequestBody ClarifyBpmnRequest request) {
        return orchestrator.clarify(request);
    }

    @PostMapping("/modify-preview")
    @Operation(summary = "生成增量修改预览（差异+预览XML）")
    public ModifyPreviewResponse modifyPreview(@RequestBody ModifyPreviewRequest request) {
        return orchestrator.modifyPreview(request);
    }

    @PostMapping("/explain")
    @Operation(summary = "解释当前流程")
    public ConsultResponse explain(@RequestBody ConsultRequest request) {
        return orchestrator.consult(withMode(request, "EXPLAIN"));
    }

    @PostMapping("/optimize")
    @Operation(summary = "返回优化建议")
    public ConsultResponse optimize(@RequestBody ConsultRequest request) {
        return orchestrator.consult(withMode(request, "OPTIMIZE"));
    }

    @PostMapping("/semantic-check")
    @Operation(summary = "AI业务语义检查")
    public ConsultResponse semanticCheck(@RequestBody ConsultRequest request) {
        return orchestrator.consult(withMode(request, "SEMANTIC_CHECK"));
    }

    private ConsultRequest withMode(ConsultRequest request, String mode) {
        return new ConsultRequest(mode, request.instruction(), request.currentModel(),
            request.selectedElementIds(), request.conversationContext());
    }
}
