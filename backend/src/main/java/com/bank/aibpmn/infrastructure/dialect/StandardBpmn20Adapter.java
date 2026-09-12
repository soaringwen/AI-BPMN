package com.bank.aibpmn.infrastructure.dialect;

import com.bank.aibpmn.domain.model.ProcessModel;
import com.bank.aibpmn.domain.validation.ValidationResult;
import org.springframework.stereotype.Component;

/**
 * 标准BPMN 2.0方言：恒等转换。
 */
@Component
public class StandardBpmn20Adapter implements BpmnDialectAdapter {

    @Override
    public BpmnDialect targetDialect() {
        return BpmnDialect.STANDARD_BPMN_20;
    }

    @Override
    public boolean supports(BpmnDialect dialect) {
        return dialect == BpmnDialect.STANDARD_BPMN_20;
    }

    @Override
    public String convert(ProcessModel processModel, String standardBpmnXml, DialectConversionOptions options) {
        return standardBpmnXml;
    }

    @Override
    public ValidationResult validate(String convertedXml) {
        return ValidationResult.empty();
    }
}
