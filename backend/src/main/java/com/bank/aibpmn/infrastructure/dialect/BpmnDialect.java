package com.bank.aibpmn.infrastructure.dialect;

/**
 * BPMN方言（DESIGN.md 17.1）。第一阶段只实现STANDARD_BPMN_20。
 */
public enum BpmnDialect {
    STANDARD_BPMN_20,
    FLOWABLE,
    CAMUNDA_7,
    CAMUNDA_8
}
