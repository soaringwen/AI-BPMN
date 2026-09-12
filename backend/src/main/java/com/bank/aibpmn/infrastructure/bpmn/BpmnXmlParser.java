package com.bank.aibpmn.infrastructure.bpmn;

import com.bank.aibpmn.domain.model.ProcessModel;

/**
 * 标准BPMN 2.0 XML → ProcessModel（DESIGN.md 10.4）。
 */
public interface BpmnXmlParser {

    ProcessModel parse(String xml);
}
