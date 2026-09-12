package com.bank.aibpmn.application.dto;

import com.bank.aibpmn.domain.model.ProcessModel;
import com.bank.aibpmn.domain.validation.ValidationResult;

import java.util.List;

/**
 * BPMN工具类API的请求/响应DTO集合。
 */
public final class BpmnXmlDtos {

    private BpmnXmlDtos() {
    }

    public record GenerateXmlRequest(ProcessModel processModel) {
    }

    public record XmlResponse(String bpmnXml, ValidationResult validation) {
    }

    public record ParseXmlRequest(String xml, Boolean strict) {
    }

    public record ParseXmlResponse(ProcessModel processModel, ValidationResult validation) {
    }

    public record ValidateModelRequest(ProcessModel processModel) {
    }

    public record ValidateModelResponse(ValidationResult validation) {
    }

    public record LayoutRequest(ProcessModel processModel, String mode, List<String> nodeIds) {
    }
}
