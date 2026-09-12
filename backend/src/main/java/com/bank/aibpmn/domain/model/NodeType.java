package com.bank.aibpmn.domain.model;

/**
 * 支持的BPMN节点类型（第一阶段）。
 */
public enum NodeType {
    START_EVENT("startEvent"),
    END_EVENT("endEvent"),
    USER_TASK("userTask"),
    SERVICE_TASK("serviceTask"),
    MANUAL_TASK("manualTask"),
    BUSINESS_RULE_TASK("businessRuleTask"),
    EXCLUSIVE_GATEWAY("exclusiveGateway"),
    PARALLEL_GATEWAY("parallelGateway"),
    INCLUSIVE_GATEWAY("inclusiveGateway"),
    SUB_PROCESS("subProcess"),
    INTERMEDIATE_CATCH_EVENT("intermediateCatchEvent"),
    INTERMEDIATE_THROW_EVENT("intermediateThrowEvent");

    private final String bpmnElementName;

    NodeType(String bpmnElementName) {
        this.bpmnElementName = bpmnElementName;
    }

    public String bpmnElementName() {
        return bpmnElementName;
    }

    public boolean isEvent() {
        return this == START_EVENT || this == END_EVENT
            || this == INTERMEDIATE_CATCH_EVENT || this == INTERMEDIATE_THROW_EVENT;
    }

    public boolean isGateway() {
        return this == EXCLUSIVE_GATEWAY || this == PARALLEL_GATEWAY || this == INCLUSIVE_GATEWAY;
    }

    public boolean isTask() {
        return this == USER_TASK || this == SERVICE_TASK || this == MANUAL_TASK
            || this == BUSINESS_RULE_TASK || this == SUB_PROCESS;
    }
}
