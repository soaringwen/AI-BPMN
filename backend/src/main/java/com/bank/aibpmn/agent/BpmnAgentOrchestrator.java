package com.bank.aibpmn.agent;

import com.bank.aibpmn.application.dto.AiBpmnResponse;
import com.bank.aibpmn.application.dto.ClarifyBpmnRequest;
import com.bank.aibpmn.application.dto.ConsultRequest;
import com.bank.aibpmn.application.dto.ConsultResponse;
import com.bank.aibpmn.application.dto.GenerateBpmnRequest;
import com.bank.aibpmn.application.dto.ModifyPreviewRequest;
import com.bank.aibpmn.application.dto.ModifyPreviewResponse;
import org.springframework.stereotype.Service;

/**
 * Spring AI编排入口：受控、确定性的服务编排，不允许模型任意调用工具（DESIGN.md 11.1）。
 */
@Service
public class BpmnAgentOrchestrator {

    private final ProcessModelGenerationService generationService;
    private final ModificationPlanningService modificationPlanningService;
    private final ProcessConsultService consultService;

    public BpmnAgentOrchestrator(ProcessModelGenerationService generationService,
                                 ModificationPlanningService modificationPlanningService,
                                 ProcessConsultService consultService) {
        this.generationService = generationService;
        this.modificationPlanningService = modificationPlanningService;
        this.consultService = consultService;
    }

    public AiBpmnResponse generate(GenerateBpmnRequest request) {
        return generationService.generate(request);
    }

    public AiBpmnResponse clarify(ClarifyBpmnRequest request) {
        return generationService.clarify(request);
    }

    public ModifyPreviewResponse modifyPreview(ModifyPreviewRequest request) {
        return modificationPlanningService.preview(request);
    }

    public ConsultResponse consult(ConsultRequest request) {
        return consultService.consult(request);
    }
}
