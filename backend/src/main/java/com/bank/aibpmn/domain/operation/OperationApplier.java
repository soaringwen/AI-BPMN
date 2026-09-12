package com.bank.aibpmn.domain.operation;

import com.bank.aibpmn.domain.layout.LayoutService;
import com.bank.aibpmn.domain.model.Bounds;
import com.bank.aibpmn.domain.model.FlowNode;
import com.bank.aibpmn.domain.model.Lane;
import com.bank.aibpmn.domain.model.NodeType;
import com.bank.aibpmn.domain.model.Point;
import com.bank.aibpmn.domain.model.Pool;
import com.bank.aibpmn.domain.model.ProcessModel;
import com.bank.aibpmn.domain.model.SequenceFlow;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 增量操作引擎：模拟应用并应用AI返回的白名单操作。
 * AI不得直接替换完整ProcessModel（DESIGN.md 3.3）。
 */
public class OperationApplier {

    private final LayoutService layoutService = new LayoutService();

    private final ProcessModel model;
    private final Map<String, String> tempToFormal = new LinkedHashMap<>();
    private final Set<String> touchedNodeIds = new LinkedHashSet<>();

    public OperationApplier(ProcessModel base) {
        this.model = base.copy();
    }

    public ProcessModel apply(List<ModelOperation> operations) {
        for (ModelOperation op : operations) {
            if (op.getType() == null) {
                throw new OperationException(op.getOperationId(), "OPERATION_TYPE_REQUIRED", "操作类型不能为空");
            }
            switch (op.getType()) {
                case ADD_NODE -> applyAddNode(op);
                case UPDATE_NODE -> applyUpdateNode(op);
                case DELETE_NODE -> applyDeleteNode(op);
                case MOVE_NODE -> applyMoveNode(op);
                case ADD_FLOW -> applyAddFlow(op);
                case UPDATE_FLOW -> applyUpdateFlow(op);
                case DELETE_FLOW -> applyDeleteFlow(op);
                case ADD_LANE -> applyAddLane(op);
                case UPDATE_LANE -> applyUpdateLane(op);
                case DELETE_LANE -> applyDeleteLane(op);
                case ADD_POOL -> applyAddPool(op);
                case UPDATE_POOL -> applyUpdatePool(op);
                case DELETE_POOL -> applyDeletePool(op);
                case ADD_BRANCH -> applyAddBranch(op, false);
                case ADD_PARALLEL_BRANCH -> applyAddBranch(op, true);
                default -> throw new OperationException(op.getOperationId(), "OPERATION_NOT_SUPPORTED",
                    "不支持的操作类型：" + op.getType());
            }
        }
        layoutService.recomputeWaypointsFor(model, touchedNodeIds);
        return model;
    }

    // ---------- helpers ----------

    private static String str(Map<String, Object> payload, String key) {
        Object value = payload.get(key);
        return value == null ? null : String.valueOf(value);
    }

    private static boolean bool(Map<String, Object> payload, String key) {
        Object value = payload.get(key);
        return Boolean.TRUE.equals(value) || "true".equalsIgnoreCase(String.valueOf(value));
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> listOfMaps(Map<String, Object> payload, String key) {
        Object value = payload.get(key);
        if (value instanceof List<?> list) {
            return (List<Map<String, Object>>) list;
        }
        return List.of();
    }

    private String resolve(String id) {
        if (id == null) {
            return null;
        }
        return tempToFormal.getOrDefault(id, id);
    }

    private FlowNode requireNode(String operationId, String id) {
        return model.findNode(id)
            .orElseThrow(() -> new OperationException(operationId, "NODE_NOT_FOUND", "引用的节点不存在：" + id));
    }

    private void attachToLane(String nodeId, String laneId) {
        model.getLanes().forEach(lane -> lane.getNodeRefs().remove(nodeId));
        if (laneId != null) {
            Lane lane = model.findLane(laneId)
                .orElseThrow(() -> new OperationException(null, "LANE_NOT_FOUND", "Lane不存在：" + laneId));
            if (!lane.getNodeRefs().contains(nodeId)) {
                lane.getNodeRefs().add(nodeId);
            }
        }
        model.findNode(nodeId).ifPresent(node -> node.setLaneId(laneId));
    }

    private void removeFromLanes(String nodeId) {
        model.getLanes().forEach(lane -> lane.getNodeRefs().remove(nodeId));
    }

    private void rewireSingleOutgoingThrough(String operationId, String sourceId, String gatewayId) {
        List<SequenceFlow> outgoing = model.outgoingFlows().getOrDefault(sourceId, List.of());
        if (outgoing.size() == 1) {
            SequenceFlow original = outgoing.get(0);
            model.getSequenceFlows().remove(original);
            model.getLayout().getFlows().remove(original.getId());
            String branchTarget = resolve(original.getTargetId());
            addFlow(gatewayId, branchTarget, original.getName(), null, false);
        } else if (outgoing.isEmpty()) {
            throw new OperationException(operationId, "INVALID_BRANCH_SOURCE",
                "源节点没有任何后继，无法插入分支：" + sourceId);
        }
        addFlow(sourceId, gatewayId, null, null, false);
    }

    private String addFlow(String sourceId, String targetId, String name, String condition, boolean defaultFlow) {
        requireNode(null, sourceId);
        requireNode(null, targetId);
        String flowId = IdGenerator.newFlowId(model);
        model.getSequenceFlows().add(new SequenceFlow(flowId, sourceId, targetId, name, condition, defaultFlow));
        touchedNodeIds.add(sourceId);
        touchedNodeIds.add(targetId);
        return flowId;
    }

    // ---------- node operations ----------

    private void applyAddNode(ModelOperation op) {
        Map<String, Object> payload = op.getPayload();
        String temporaryId = str(payload, "temporaryId");
        String typeStr = str(payload, "nodeType");
        if (typeStr == null) {
            throw new OperationException(op.getOperationId(), "NODE_TYPE_REQUIRED", "ADD_NODE缺少nodeType");
        }
        NodeType type;
        try {
            type = NodeType.valueOf(typeStr);
        } catch (IllegalArgumentException e) {
            throw new OperationException(op.getOperationId(), "NODE_TYPE_INVALID", "不支持的节点类型：" + typeStr);
        }

        String formalId = IdGenerator.newNodeId(model, type);
        FlowNode node = new FlowNode(formalId, type, str(payload, "name"), null, str(payload, "documentation"),
            new LinkedHashMap<>());
        model.getNodes().add(node);
        if (temporaryId != null) {
            tempToFormal.put(temporaryId, formalId);
        }

        String laneId = resolve(str(payload, "laneId"));
        if (laneId == null && !model.getLanes().isEmpty()) {
            laneId = model.getLanes().get(0).getId();
        }
        attachToLane(formalId, laneId);
        touchedNodeIds.add(formalId);

        String insertAfter = resolve(str(payload, "insertAfter"));
        String insertBefore = resolve(str(payload, "insertBefore"));
        if (insertAfter != null) {
            requireNode(op.getOperationId(), insertAfter);
            rewireSingleOutgoingThrough(op.getOperationId(), insertAfter, formalId);
            List<SequenceFlow> outgoing = model.outgoingFlows().getOrDefault(formalId, List.of());
            String nextId = outgoing.isEmpty() ? null : outgoing.get(0).getTargetId();
            layoutService.insertChain(model, List.of(formalId), insertAfter, nextId);
        } else if (insertBefore != null) {
            requireNode(op.getOperationId(), insertBefore);
            List<SequenceFlow> incoming = model.incomingFlows().getOrDefault(insertBefore, List.of());
            if (incoming.size() == 1) {
                SequenceFlow original = incoming.get(0);
                model.getSequenceFlows().remove(original);
                model.getLayout().getFlows().remove(original.getId());
                String prevId = resolve(original.getSourceId());
                addFlow(prevId, formalId, original.getName(), null, false);
                addFlow(formalId, insertBefore, null, null, false);
                layoutService.insertChain(model, List.of(formalId), prevId, insertBefore);
            } else {
                addFlow(formalId, insertBefore, null, null, false);
            }
        }
    }

    private void applyUpdateNode(ModelOperation op) {
        String nodeId = resolve(op.getTargetId());
        FlowNode node = requireNode(op.getOperationId(), nodeId);
        Map<String, Object> payload = op.getPayload();
        if (payload.containsKey("name")) {
            node.setName(str(payload, "name"));
        }
        if (payload.containsKey("documentation")) {
            node.setDocumentation(str(payload, "documentation"));
        }
        if (payload.containsKey("laneId")) {
            attachToLane(nodeId, resolve(str(payload, "laneId")));
        }
        touchedNodeIds.add(nodeId);
    }

    private void applyDeleteNode(ModelOperation op) {
        String nodeId = resolve(op.getTargetId());
        requireNode(op.getOperationId(), nodeId);
        // 删除任务后不擅自连接前后节点（DESIGN.md 13.2）
        List<SequenceFlow> touching = model.flowsTouching(nodeId);
        model.getSequenceFlows().removeAll(touching);
        touching.forEach(f -> model.getLayout().getFlows().remove(f.getId()));
        removeFromLanes(nodeId);
        model.getNodes().removeIf(n -> n.getId().equals(nodeId));
        model.getLayout().getNodes().remove(nodeId);
    }

    private void applyMoveNode(ModelOperation op) {
        String nodeId = resolve(op.getTargetId());
        requireNode(op.getOperationId(), nodeId);
        Map<String, Object> payload = op.getPayload();
        Bounds bounds = model.getLayout().getNodes().get(nodeId);
        if (bounds == null) {
            bounds = layoutService.defaultBounds(model, nodeId, 0, 0);
            model.getLayout().getNodes().put(nodeId, bounds);
        }
        if (payload.containsKey("x")) {
            bounds.setX(((Number) payload.get("x")).doubleValue());
        }
        if (payload.containsKey("y")) {
            bounds.setY(((Number) payload.get("y")).doubleValue());
        }
        touchedNodeIds.add(nodeId);
    }

    // ---------- flow operations ----------

    private void applyAddFlow(ModelOperation op) {
        Map<String, Object> payload = op.getPayload();
        String sourceId = resolve(str(payload, "sourceId"));
        String targetId = resolve(str(payload, "targetId"));
        if (sourceId == null || targetId == null) {
            throw new OperationException(op.getOperationId(), "FLOW_ENDPOINT_REQUIRED", "ADD_FLOW必须提供sourceId与targetId");
        }
        requireNode(op.getOperationId(), sourceId);
        requireNode(op.getOperationId(), targetId);
        String flowId = addFlow(sourceId, targetId, str(payload, "name"), str(payload, "condition"),
            bool(payload, "defaultFlow"));
        String temporaryId = str(payload, "temporaryId");
        if (temporaryId != null) {
            tempToFormal.put(temporaryId, flowId);
        }
    }

    private void applyUpdateFlow(ModelOperation op) {
        String flowId = op.getTargetId();
        SequenceFlow flow = model.findSequenceFlow(flowId)
            .orElseThrow(() -> new OperationException(op.getOperationId(), "FLOW_NOT_FOUND", "连线不存在：" + flowId));
        Map<String, Object> payload = op.getPayload();
        if (payload.containsKey("name")) {
            flow.setName(str(payload, "name"));
        }
        if (payload.containsKey("condition")) {
            flow.setCondition(str(payload, "condition"));
        }
        if (payload.containsKey("defaultFlow")) {
            flow.setDefaultFlow(bool(payload, "defaultFlow"));
        }
        if (payload.containsKey("targetId")) {
            String newTarget = resolve(str(payload, "targetId"));
            requireNode(op.getOperationId(), newTarget);
            flow.setTargetId(newTarget);
            touchedNodeIds.add(flow.getSourceId());
            touchedNodeIds.add(newTarget);
        }
    }

    private void applyDeleteFlow(ModelOperation op) {
        String flowId = op.getTargetId();
        SequenceFlow flow = model.findSequenceFlow(flowId)
            .orElseThrow(() -> new OperationException(op.getOperationId(), "FLOW_NOT_FOUND", "连线不存在：" + flowId));
        model.getSequenceFlows().remove(flow);
        model.getLayout().getFlows().remove(flowId);
        touchedNodeIds.add(flow.getSourceId());
        touchedNodeIds.add(flow.getTargetId());
    }

    // ---------- lane / pool operations ----------

    private void applyAddLane(ModelOperation op) {
        Map<String, Object> payload = op.getPayload();
        String poolId = resolve(str(payload, "poolId"));
        if (poolId == null && !model.getPools().isEmpty()) {
            poolId = model.getPools().get(0).getId();
        }
        String laneId = IdGenerator.newLaneId(model);
        model.getLanes().add(new Lane(laneId, str(payload, "name"), poolId, new ArrayList<>()));
        String temporaryId = str(payload, "temporaryId");
        if (temporaryId != null) {
            tempToFormal.put(temporaryId, laneId);
        }
    }

    private void applyUpdateLane(ModelOperation op) {
        Lane lane = model.findLane(op.getTargetId())
            .orElseThrow(() -> new OperationException(op.getOperationId(), "LANE_NOT_FOUND", "Lane不存在：" + op.getTargetId()));
        Map<String, Object> payload = op.getPayload();
        if (payload.containsKey("name")) {
            lane.setName(str(payload, "name"));
        }
    }

    private void applyDeleteLane(ModelOperation op) {
        Lane lane = model.findLane(op.getTargetId())
            .orElseThrow(() -> new OperationException(op.getOperationId(), "LANE_NOT_FOUND", "Lane不存在：" + op.getTargetId()));
        new ArrayList<>(lane.getNodeRefs()).forEach(nodeRef -> attachToLane(nodeRef, null));
        model.getLanes().remove(lane);
    }

    private void applyAddPool(ModelOperation op) {
        if (!model.getPools().isEmpty()) {
            throw new OperationException(op.getOperationId(), "OPERATION_NOT_SUPPORTED",
                "第一阶段每个模型仅支持一个Pool");
        }
        Map<String, Object> payload = op.getPayload();
        String processRef = model.getProcess().getId();
        String poolId = IdGenerator.newPoolId(model);
        model.getPools().add(new Pool(poolId, str(payload, "name"), processRef));
        String temporaryId = str(payload, "temporaryId");
        if (temporaryId != null) {
            tempToFormal.put(temporaryId, poolId);
        }
    }

    private void applyUpdatePool(ModelOperation op) {
        Pool pool = model.findPool(op.getTargetId())
            .orElseThrow(() -> new OperationException(op.getOperationId(), "POOL_NOT_FOUND", "Pool不存在：" + op.getTargetId()));
        Map<String, Object> payload = op.getPayload();
        if (payload.containsKey("name")) {
            pool.setName(str(payload, "name"));
        }
    }

    private void applyDeletePool(ModelOperation op) {
        Pool pool = model.findPool(op.getTargetId())
            .orElseThrow(() -> new OperationException(op.getOperationId(), "POOL_NOT_FOUND", "Pool不存在：" + op.getTargetId()));
        model.getPools().remove(pool);
        model.getLanes().clear();
    }

    // ---------- semantic branch operations ----------

    private void applyAddBranch(ModelOperation op, boolean parallel) {
        Map<String, Object> payload = op.getPayload();
        String sourceId = resolve(str(payload, "sourceId"));
        if (sourceId == null) {
            sourceId = resolve(op.getTargetId());
        }
        FlowNode source = requireNode(op.getOperationId(), sourceId);

        List<Map<String, Object>> taskDefs = listOfMaps(payload, "tasks");
        List<String> chainIds = new ArrayList<>();

        String gatewayId;
        List<SequenceFlow> outgoing = model.outgoingFlows().getOrDefault(sourceId, List.of());
        String joinNodeId;
        if (outgoing.isEmpty()) {
            // 源节点无后继（如刚拖入的开始事件）：插入网关并直接从源展开分支链
            NodeType gwType = parallel ? NodeType.PARALLEL_GATEWAY : NodeType.EXCLUSIVE_GATEWAY;
            gatewayId = IdGenerator.newNodeId(model, gwType);
            model.getNodes().add(new FlowNode(gatewayId, gwType, parallel ? null : "分支判断", source.getLaneId(), null,
                new LinkedHashMap<>()));
            attachToLane(gatewayId, source.getLaneId());
            joinNodeId = null;
            addFlow(sourceId, gatewayId, null, null, false);
            touchedNodeIds.add(gatewayId);
        } else if (outgoing.size() == 1 && !source.getType().isGateway()) {
            NodeType gwType = parallel ? NodeType.PARALLEL_GATEWAY : NodeType.EXCLUSIVE_GATEWAY;
            gatewayId = IdGenerator.newNodeId(model, gwType);
            model.getNodes().add(new FlowNode(gatewayId, gwType, parallel ? null : "分支判断", source.getLaneId(), null,
                new LinkedHashMap<>()));
            attachToLane(gatewayId, source.getLaneId());
            SequenceFlow original = outgoing.get(0);
            model.getSequenceFlows().remove(original);
            model.getLayout().getFlows().remove(original.getId());
            joinNodeId = resolve(original.getTargetId());
            addFlow(sourceId, gatewayId, null, null, false);
            touchedNodeIds.add(gatewayId);
        } else if (source.getType().isGateway()) {
            gatewayId = sourceId;
            joinNodeId = null;
        } else {
            throw new OperationException(op.getOperationId(), "INVALID_BRANCH_SOURCE",
                "源节点存在多个后继且不是网关，无法增加分支：" + sourceId);
        }

        String laneId = source.getLaneId();
        for (Map<String, Object> taskDef : taskDefs) {
            String typeStr = String.valueOf(taskDef.getOrDefault("nodeType", "USER_TASK"));
            NodeType type;
            try {
                type = NodeType.valueOf(typeStr);
            } catch (IllegalArgumentException e) {
                throw new OperationException(op.getOperationId(), "NODE_TYPE_INVALID", "不支持的节点类型：" + typeStr);
            }
            String nodeId = IdGenerator.newNodeId(model, type);
            model.getNodes().add(new FlowNode(nodeId, type, (String) taskDef.get("name"), null,
                (String) taskDef.get("documentation"), new LinkedHashMap<>()));
            String defLane = (String) taskDef.get("laneId");
            attachToLane(nodeId, defLane != null ? resolve(defLane) : laneId);
            String temporaryId = (String) taskDef.get("temporaryId");
            if (temporaryId != null) {
                tempToFormal.put(temporaryId, nodeId);
            }
            chainIds.add(nodeId);
            touchedNodeIds.add(nodeId);
        }

        String endpointId = resolve(str(payload, "targetId"));
        if (endpointId != null) {
            requireNode(op.getOperationId(), endpointId);
        } else {
            String endId = IdGenerator.newNodeId(model, NodeType.END_EVENT);
            model.getNodes().add(new FlowNode(endId, NodeType.END_EVENT, "结束", laneId, null, new LinkedHashMap<>()));
            attachToLane(endId, laneId);
            endpointId = endId;
            touchedNodeIds.add(endId);
        }

        String previousId = gatewayId;
        String branchName = str(payload, "branchName");
        String condition = str(payload, "condition");
        for (String chainId : chainIds) {
            addFlow(previousId, chainId, previousId.equals(gatewayId) ? branchName : null,
                previousId.equals(gatewayId) ? condition : null, false);
            previousId = chainId;
        }
        addFlow(previousId, endpointId, previousId.equals(gatewayId) ? branchName : null,
            previousId.equals(gatewayId) ? condition : null, false);

        List<String> layoutChain = new ArrayList<>(chainIds);
        if (!gatewayId.equals(sourceId)) {
            layoutChain.add(0, gatewayId);
        }
        if (!layoutChain.isEmpty()) {
            layoutService.insertChain(model, layoutChain, sourceId, joinNodeId);
        }
    }
}
