package com.bank.aibpmn.domain.validation;

import com.bank.aibpmn.domain.model.FlowNode;
import com.bank.aibpmn.domain.model.NodeType;
import com.bank.aibpmn.domain.model.ProcessModel;
import com.bank.aibpmn.domain.model.SequenceFlow;
import org.springframework.stereotype.Component;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * ProcessModel基础规则校验（DESIGN.md 16.2 BPMN-001 ~ BPMN-016）。
 */
@Component
public class ProcessModelValidator {

    public ValidationResult validate(ProcessModel model) {
        ValidationResult result = ValidationResult.empty();
        if (model == null) {
            result.add(new ValidationIssue("BPMN-000", Severity.ERROR, "流程模型为空", null));
            return result;
        }

        // BPMN-014 元素ID必须唯一
        Set<String> seen = new HashSet<>();
        for (FlowNode node : model.getNodes()) {
            if (!seen.add(node.getId())) {
                result.add(new ValidationIssue("BPMN-014", Severity.ERROR, "元素ID重复：" + node.getId(), node.getId()));
            }
        }
        for (SequenceFlow flow : model.getSequenceFlows()) {
            if (!seen.add(flow.getId())) {
                result.add(new ValidationIssue("BPMN-014", Severity.ERROR, "元素ID重复：" + flow.getId(), flow.getId()));
            }
        }

        // BPMN-013 连线端点必须存在
        for (SequenceFlow flow : model.getSequenceFlows()) {
            if (model.findNode(flow.getSourceId()).isEmpty()) {
                result.add(new ValidationIssue("BPMN-013", Severity.ERROR,
                    "连线的源节点不存在：" + flow.getSourceId(), flow.getId()));
            }
            if (model.findNode(flow.getTargetId()).isEmpty()) {
                result.add(new ValidationIssue("BPMN-013", Severity.ERROR,
                    "连线的目标节点不存在：" + flow.getTargetId(), flow.getId()));
            }
        }

        // BPMN-001 / BPMN-002 开始与结束事件
        boolean hasStart = model.getNodes().stream().anyMatch(n -> n.getType() == NodeType.START_EVENT);
        boolean hasEnd = model.getNodes().stream().anyMatch(n -> n.getType() == NodeType.END_EVENT);
        if (!hasStart) {
            result.add(new ValidationIssue("BPMN-001", Severity.ERROR, "流程至少包含一个开始事件", null));
        }
        if (!hasEnd) {
            result.add(new ValidationIssue("BPMN-002", Severity.ERROR, "流程至少包含一个结束事件", null));
        }

        Map<String, List<SequenceFlow>> outgoing = model.outgoingFlows();
        Map<String, List<SequenceFlow>> incoming = model.incomingFlows();

        for (FlowNode node : model.getNodes()) {
            List<SequenceFlow> out = outgoing.getOrDefault(node.getId(), List.of());
            List<SequenceFlow> in = incoming.getOrDefault(node.getId(), List.of());

            // BPMN-003 普通任务应有进入和离开连线
            if (node.getType().isTask() && (in.isEmpty() || out.isEmpty())) {
                result.add(new ValidationIssue("BPMN-003", Severity.ERROR,
                    "任务应具有进入和离开连线：" + node.getName(), node.getId()));
            }

            // BPMN-009 任务名称不得为空
            if (node.getType().isTask() && (node.getName() == null || node.getName().isBlank())) {
                result.add(new ValidationIssue("BPMN-009", Severity.ERROR,
                    "任务名称不得为空：" + node.getId(), node.getId()));
            }

            // BPMN-004 / BPMN-005 排他网关分支条件
            if (node.getType() == NodeType.EXCLUSIVE_GATEWAY && out.size() > 1) {
                SequenceFlow defaultFlow = out.stream().filter(SequenceFlow::isDefaultFlow).findFirst().orElse(null);
                if (defaultFlow == null) {
                    result.add(new ValidationIssue("BPMN-005", Severity.WARNING,
                        "排他网关建议配置默认路径：" + node.getName(), node.getId()));
                }
                for (SequenceFlow flow : out) {
                    if (flow != defaultFlow && (flow.getCondition() == null || flow.getCondition().isBlank())) {
                        result.add(new ValidationIssue("BPMN-004", Severity.ERROR,
                            "排他网关的非默认分支应有明确条件：" + node.getName(), node.getId()));
                    }
                }
            }

            // BPMN-010 用户任务应位于Lane
            if (node.getType() == NodeType.USER_TASK && (node.getLaneId() == null || node.getLaneId().isBlank())) {
                result.add(new ValidationIssue("BPMN-010", Severity.WARNING,
                    "用户任务应位于明确的Lane中：" + node.getName(), node.getId()));
            }
        }

        // BPMN-007 / BPMN-008 可达性
        Set<String> reachableFromStart = reachable(outgoing,
            model.getNodes().stream().filter(n -> n.getType() == NodeType.START_EVENT).map(FlowNode::getId).toList());
        Set<String> canReachEnd = reachableReverse(reverse(model),
            model.getNodes().stream().filter(n -> n.getType() == NodeType.END_EVENT).map(FlowNode::getId).toList());

        for (FlowNode node : model.getNodes()) {
            if (!reachableFromStart.contains(node.getId())) {
                result.add(new ValidationIssue("BPMN-007", Severity.ERROR,
                    "存在无法到达的孤立节点：" + displayName(node), node.getId()));
            } else if (!canReachEnd.contains(node.getId())) {
                result.add(new ValidationIssue("BPMN-008", Severity.ERROR,
                    "存在无法到达结束节点的路径：" + displayName(node), node.getId()));
            }
        }

        // Lane引用完整性
        Set<String> nodeIds = new HashSet<>();
        model.getNodes().forEach(n -> nodeIds.add(n.getId()));
        model.getLanes().forEach(lane -> lane.getNodeRefs().removeIf(ref -> !nodeIds.contains(ref)));

        return result;
    }

    private String displayName(FlowNode node) {
        return node.getName() == null || node.getName().isBlank() ? node.getId() : node.getName();
    }

    private Set<String> reachable(Map<String, List<SequenceFlow>> edges, List<String> sources) {
        Set<String> visited = new LinkedHashSet<>();
        Deque<String> queue = new ArrayDeque<>(sources);
        while (!queue.isEmpty()) {
            String current = queue.poll();
            if (!visited.add(current)) {
                continue;
            }
            for (SequenceFlow flow : edges.getOrDefault(current, List.of())) {
                queue.add(flow.getTargetId());
            }
        }
        return visited;
    }

    /**
     * 反向可达遍历：在以targetId为key的反向边表中沿sourceId前进。
     */
    private Set<String> reachableReverse(Map<String, List<SequenceFlow>> reversedEdges, List<String> sources) {
        Set<String> visited = new LinkedHashSet<>();
        Deque<String> queue = new ArrayDeque<>(sources);
        while (!queue.isEmpty()) {
            String current = queue.poll();
            if (!visited.add(current)) {
                continue;
            }
            for (SequenceFlow flow : reversedEdges.getOrDefault(current, List.of())) {
                queue.add(flow.getSourceId());
            }
        }
        return visited;
    }

    /**
     * 反向边表：key为节点ID，value为指向该节点的连线。
     */
    private Map<String, List<SequenceFlow>> reverse(ProcessModel model) {
        Map<String, List<SequenceFlow>> reversed = new java.util.LinkedHashMap<>();
        for (SequenceFlow flow : model.getSequenceFlows()) {
            reversed.computeIfAbsent(flow.getTargetId(), k -> new java.util.ArrayList<>()).add(flow);
        }
        return reversed;
    }
}
