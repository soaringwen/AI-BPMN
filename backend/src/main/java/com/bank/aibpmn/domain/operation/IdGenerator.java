package com.bank.aibpmn.domain.operation;

import com.bank.aibpmn.domain.model.NodeType;
import com.bank.aibpmn.domain.model.ProcessModel;

import java.util.Set;

/**
 * 正式元素ID生成器。临时ID只允许在单次模型响应中使用，
 * 所有新增元素的正式ID由后端生成（DESIGN.md 3.1 / 7.5）。
 */
public final class IdGenerator {

    private IdGenerator() {
    }

    public static String prefixFor(NodeType type) {
        return switch (type) {
            case START_EVENT -> "event_start";
            case END_EVENT -> "event_end";
            case INTERMEDIATE_CATCH_EVENT, INTERMEDIATE_THROW_EVENT -> "event_mid";
            case USER_TASK, SERVICE_TASK, MANUAL_TASK, BUSINESS_RULE_TASK, SUB_PROCESS -> "task";
            case EXCLUSIVE_GATEWAY, PARALLEL_GATEWAY, INCLUSIVE_GATEWAY -> "gateway";
        };
    }

    public static String newNodeId(ProcessModel model, NodeType type) {
        String prefix = prefixFor(type);
        return nextId(model, prefix, Set.of());
    }

    public static String newFlowId(ProcessModel model) {
        return nextId(model, "flow", Set.of());
    }

    public static String newLaneId(ProcessModel model) {
        return nextId(model, "lane", Set.of());
    }

    public static String newPoolId(ProcessModel model) {
        return nextId(model, "pool", Set.of());
    }

    public static String newModelId() {
        return "model_" + Long.toHexString(System.currentTimeMillis());
    }

    private static String nextId(ProcessModel model, String prefix, Set<String> extra) {
        int index = 1;
        String candidate = prefix + "_" + index;
        while (isTaken(model, candidate, extra)) {
            index++;
            candidate = prefix + "_" + index;
        }
        return candidate;
    }

    private static boolean isTaken(ProcessModel model, String candidate, Set<String> extra) {
        if (extra.contains(candidate)) {
            return true;
        }
        return model.findNode(candidate).isPresent()
            || model.findSequenceFlow(candidate).isPresent()
            || model.findLane(candidate).isPresent()
            || model.findPool(candidate).isPresent()
            || candidate.equals(model.getProcess().getId())
            || model.getMessageFlows().stream().anyMatch(mf -> candidate.equals(mf.getId()));
    }
}
