package com.bank.aibpmn.infrastructure.dialect;

import com.bank.aibpmn.domain.model.ProcessModel;
import com.bank.aibpmn.domain.validation.ValidationResult;

/**
 * 方言适配器扩展点（DESIGN.md 17.2）。
 * 第一阶段不得创建没有实际转换能力的Flowable或Camunda伪实现。
 */
public interface BpmnDialectAdapter {

    BpmnDialect targetDialect();

    boolean supports(BpmnDialect dialect);

    String convert(ProcessModel processModel, String standardBpmnXml, DialectConversionOptions options);

    ValidationResult validate(String convertedXml);
}
