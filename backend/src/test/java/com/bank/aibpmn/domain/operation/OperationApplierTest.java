package com.bank.aibpmn.domain.operation;

import com.bank.aibpmn.TestModels;
import com.bank.aibpmn.domain.model.NodeType;
import com.bank.aibpmn.domain.model.ProcessModel;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 增量操作引擎测试（DESIGN.md 24.1）。
 */
class OperationApplierTest {

    @Test
    void addNodeWithInsertAfterRewiresFlow() {
        ProcessModel base = TestModels.simpleApproval();
        OperationApplier applier = new OperationApplier(base);

        ModelOperation op = new ModelOperation("op-1", OperationType.ADD_NODE, null, Map.of(
            "temporaryId", "new_node_1",
            "nodeType", "USER_TASK",
            "name", "复核",
            "insertAfter", "task_submit_1"));

        ProcessModel result = applier.apply(List.of(op));

        assertThat(result.getNodes()).hasSize(base.getNodes().size() + 1);
        // 原连线 task_submit_1 → task_approve_1 被拆分
        assertThat(result.getSequenceFlows().stream()
            .noneMatch(f -> "task_submit_1".equals(f.getSourceId()) && "task_approve_1".equals(f.getTargetId())))
            .isTrue();
        // 新节点有正式ID（非临时ID）
        var newNode = result.getNodes().stream().filter(n -> "复核".equals(n.getName())).findFirst().orElseThrow();
        assertThat(newNode.getId()).startsWith("task_").isNotEqualTo("new_node_1");
        // 新连线：submit→new, new→approve
        assertThat(result.getSequenceFlows().stream()
            .anyMatch(f -> "task_submit_1".equals(f.getSourceId()) && newNode.getId().equals(f.getTargetId())))
            .isTrue();
        assertThat(result.getSequenceFlows().stream()
            .anyMatch(f -> newNode.getId().equals(f.getSourceId()) && "task_approve_1".equals(f.getTargetId())))
            .isTrue();
        // 新节点有布局
        assertThat(result.getLayout().getNodes()).containsKey(newNode.getId());
    }

    @Test
    void addBranchCreatesGatewayAndReturnPath() {
        ProcessModel base = TestModels.simpleApproval();
        OperationApplier applier = new OperationApplier(base);

        ModelOperation op = new ModelOperation("op-2", OperationType.ADD_BRANCH, null, Map.of(
            "sourceId", "gateway_1",
            "branchName", "不通过",
            "condition", "${approved == false}",
            "targetId", "task_submit_1"));

        ProcessModel result = applier.apply(List.of(op));

        // 排他网关增加退回分支：gateway_1 → task_submit_1
        assertThat(result.getSequenceFlows().stream()
            .anyMatch(f -> "gateway_1".equals(f.getSourceId())
                && "task_submit_1".equals(f.getTargetId())
                && "不通过".equals(f.getName())))
            .isTrue();
    }

    @Test
    void addBranchFromTaskInsertsExclusiveGateway() {
        ProcessModel base = TestModels.simpleApproval();
        OperationApplier applier = new OperationApplier(base);

        ModelOperation op = new ModelOperation("op-3", OperationType.ADD_BRANCH, null, Map.of(
            "sourceId", "task_approve_1",
            "branchName", "升级审批",
            "tasks", List.of(Map.of(
                "temporaryId", "t1",
                "nodeType", "USER_TASK",
                "name", "上级审批"))));

        ProcessModel result = applier.apply(List.of(op));

        assertThat(result.getNodes().stream().anyMatch(n -> n.getType() == NodeType.EXCLUSIVE_GATEWAY)).isTrue();
        assertThat(result.getNodes().stream().anyMatch(n -> "上级审批".equals(n.getName()))).isTrue();
    }

    @Test
    void deleteNodeRemovesTouchingFlowsWithoutRewiring() {
        ProcessModel base = TestModels.simpleApproval();
        OperationApplier applier = new OperationApplier(base);

        ModelOperation op = new ModelOperation("op-4", OperationType.DELETE_NODE, "task_submit_1", Map.of());
        ProcessModel result = applier.apply(List.of(op));

        assertThat(result.findNode("task_submit_1")).isEmpty();
        assertThat(result.flowsTouching("task_submit_1")).isEmpty();
        // 不擅自连接前后节点
        assertThat(result.getSequenceFlows().stream()
            .noneMatch(f -> "event_start_1".equals(f.getSourceId()) && "task_approve_1".equals(f.getTargetId())))
            .isTrue();
    }

    @Test
    void referencingUnknownNodeFails() {
        ProcessModel base = TestModels.simpleApproval();
        OperationApplier applier = new OperationApplier(base);

        ModelOperation op = new ModelOperation("op-5", OperationType.UPDATE_NODE, "not_exist", Map.of("name", "x"));

        assertThatThrownBy(() -> applier.apply(List.of(op)))
            .isInstanceOf(OperationException.class)
            .satisfies(e -> assertThat(((OperationException) e).getCode()).isEqualTo("NODE_NOT_FOUND"));
    }

    @Test
    void addFlowWithTemporaryIdsResolvesFormalIds() {
        ProcessModel base = TestModels.simpleApproval();
        OperationApplier applier = new OperationApplier(base);

        ModelOperation addNode = new ModelOperation("op-6", OperationType.ADD_NODE, null, Map.of(
            "temporaryId", "n1",
            "nodeType", "SERVICE_TASK",
            "name", "自动通知",
            "insertAfter", "task_approve_1"));
        ModelOperation addFlow = new ModelOperation("op-7", OperationType.ADD_FLOW, null, Map.of(
            "sourceId", "n1",
            "targetId", "task_submit_1",
            "name", "重新提交"));

        ProcessModel result = applier.apply(List.of(addNode, addFlow));

        var notify = result.getNodes().stream().filter(n -> "自动通知".equals(n.getName())).findFirst().orElseThrow();
        assertThat(result.getSequenceFlows().stream()
            .anyMatch(f -> notify.getId().equals(f.getSourceId()) && "task_submit_1".equals(f.getTargetId())))
            .isTrue();
    }

    @Test
    void addBranchFromNodeWithoutOutgoingSucceeds() {
        // 场景：用户在画布上只拖了一个孤立的开始事件，AI从该事件展开分支
        ProcessModel base = new ProcessModel();
        base.setModelId("model_blank");
        base.setProcess(new com.bank.aibpmn.domain.model.ProcessInfo("process_x", "x", null, false));
        base.getNodes().add(TestModels.node("event_start_1", NodeType.START_EVENT, "开始"));

        OperationApplier applier = new OperationApplier(base);
        ModelOperation op = new ModelOperation("op-9", OperationType.ADD_BRANCH, null, Map.of(
            "sourceId", "event_start_1",
            "branchName", "审批",
            "condition", "${approved}",
            "tasks", List.of(Map.of(
                "temporaryId", "t1",
                "nodeType", "USER_TASK",
                "name", "入职签约"))));

        ProcessModel result = applier.apply(List.of(op));

        // 源→网关→任务链→结束事件
        assertThat(result.getSequenceFlows().stream()
            .anyMatch(f -> "event_start_1".equals(f.getSourceId())
                && result.findNode(f.getTargetId()).orElseThrow().getType() == NodeType.EXCLUSIVE_GATEWAY))
            .isTrue();
        assertThat(result.getNodes().stream().anyMatch(n -> "入职签约".equals(n.getName()))).isTrue();
        assertThat(result.getNodes().stream().anyMatch(n -> n.getType() == NodeType.END_EVENT)).isTrue();
    }

    @Test
    void addParallelBranchInsertsParallelGateway() {
        ProcessModel base = TestModels.simpleApproval();
        OperationApplier applier = new OperationApplier(base);

        ModelOperation op = new ModelOperation("op-8", OperationType.ADD_PARALLEL_BRANCH, null, Map.of(
            "sourceId", "task_submit_1",
            "tasks", List.of(Map.of(
                "temporaryId", "p1",
                "nodeType", "USER_TASK",
                "name", "安全评审"))));

        ProcessModel result = applier.apply(List.of(op));

        assertThat(result.getNodes().stream().anyMatch(n -> n.getType() == NodeType.PARALLEL_GATEWAY)).isTrue();
        assertThat(result.getNodes().stream().anyMatch(n -> "安全评审".equals(n.getName()))).isTrue();
        // task_submit_1现在连接到并行网关
        assertThat(result.outgoingFlows().get("task_submit_1").stream()
            .allMatch(f -> result.findNode(f.getTargetId()).orElseThrow().getType() == NodeType.PARALLEL_GATEWAY))
            .isTrue();
    }
}
