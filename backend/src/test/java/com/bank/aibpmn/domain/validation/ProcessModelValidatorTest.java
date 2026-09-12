package com.bank.aibpmn.domain.validation;

import com.bank.aibpmn.TestModels;
import com.bank.aibpmn.domain.model.FlowNode;
import com.bank.aibpmn.domain.model.NodeType;
import com.bank.aibpmn.domain.model.ProcessModel;
import com.bank.aibpmn.domain.model.SequenceFlow;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 校验规则测试（DESIGN.md 24.1）。
 */
class ProcessModelValidatorTest {

    private final ProcessModelValidator validator = new ProcessModelValidator();

    @Test
    void validModelPasses() {
        ValidationResult result = validator.validate(TestModels.simpleApproval());
        assertThat(result.isValid()).isTrue();
        assertThat(result.getWarnings()).isEmpty();
    }

    @Test
    void detectsIsolatedNode() {
        ProcessModel model = TestModels.simpleApproval();
        model.getNodes().add(TestModels.node("task_orphan", NodeType.USER_TASK, "孤立任务"));

        ValidationResult result = validator.validate(model);
        assertThat(result.getErrors())
            .anyMatch(e -> "BPMN-007".equals(e.getCode()) && e.getElementId().equals("task_orphan"));
    }

    @Test
    void detectsPathNotReachingEnd() {
        ProcessModel model = TestModels.simpleApproval();
        // 增加一条死路分支：提交申请 → 死路任务（无后继）
        model.getNodes().add(TestModels.node("task_dead", NodeType.USER_TASK, "死路任务"));
        model.getSequenceFlows().add(new SequenceFlow("flow_dead", "task_submit_1", "task_dead", null, null, false));

        ValidationResult result = validator.validate(model);
        assertThat(result.getErrors())
            .anyMatch(e -> "BPMN-008".equals(e.getCode()) && "task_dead".equals(e.getElementId()));
    }

    @Test
    void detectsExclusiveGatewayWithoutCondition() {
        ProcessModel model = TestModels.simpleApproval();
        model.getNodes().add(TestModels.node("gateway_2", NodeType.EXCLUSIVE_GATEWAY, "另一个判断"));
        model.getSequenceFlows().add(new SequenceFlow("flow_g2a", "gateway_2", "event_end_1", "分支A", null, false));
        model.getNodes().add(TestModels.node("task_extra", NodeType.USER_TASK, "额外任务"));
        model.getSequenceFlows().add(new SequenceFlow("flow_g2b", "gateway_2", "task_extra", "分支B", null, false));
        model.getSequenceFlows().add(new SequenceFlow("flow_g2c", "task_extra", "event_end_1", null, null, false));

        ValidationResult result = validator.validate(model);
        assertThat(result.getErrors()).anyMatch(e -> "BPMN-004".equals(e.getCode()));
    }

    @Test
    void detectsMissingStartAndEnd() {
        ProcessModel model = new ProcessModel();
        model.setProcess(new com.bank.aibpmn.domain.model.ProcessInfo("process_x", "x", null, false));
        model.getNodes().add(TestModels.node("task_only", NodeType.USER_TASK, "唯一任务"));

        ValidationResult result = validator.validate(model);
        assertThat(result.getErrors())
            .anyMatch(e -> "BPMN-001".equals(e.getCode()))
            .anyMatch(e -> "BPMN-002".equals(e.getCode()));
    }

    @Test
    void detectsDuplicateIds() {
        ProcessModel model = TestModels.simpleApproval();
        model.getNodes().add(new FlowNode("task_submit_1", NodeType.MANUAL_TASK, "重复ID", null, null,
            java.util.Map.of()));

        ValidationResult result = validator.validate(model);
        assertThat(result.getErrors()).anyMatch(e -> "BPMN-014".equals(e.getCode()));
    }
}
