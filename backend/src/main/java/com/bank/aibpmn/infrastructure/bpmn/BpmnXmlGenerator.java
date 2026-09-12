package com.bank.aibpmn.infrastructure.bpmn;

import com.bank.aibpmn.domain.model.ProcessModel;

/**
 * ProcessModel → 标准BPMN 2.0 XML（DESIGN.md 10.3）。
 */
public interface BpmnXmlGenerator {

    String generate(ProcessModel model);
}
