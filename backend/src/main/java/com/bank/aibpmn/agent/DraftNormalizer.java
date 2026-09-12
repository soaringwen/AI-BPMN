package com.bank.aibpmn.agent;

import com.bank.aibpmn.domain.layout.LayoutService;
import com.bank.aibpmn.domain.model.FlowNode;
import com.bank.aibpmn.domain.model.Lane;
import com.bank.aibpmn.domain.model.NodeType;
import com.bank.aibpmn.domain.model.Pool;
import com.bank.aibpmn.domain.model.ProcessInfo;
import com.bank.aibpmn.domain.model.ProcessModel;
import com.bank.aibpmn.domain.model.SequenceFlow;
import com.bank.aibpmn.domain.operation.IdGenerator;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * ProcessModel草案规范化：临时ID→正式ID、Lane归属、初始布局。
 * 正式ID只由后端生成（DESIGN.md 12.1 步骤11）。
 */
@Component
public class DraftNormalizer {

    private final LayoutService layoutService = new LayoutService();

    public ProcessModel normalize(JsonNode draft, String description, boolean includePool) {
        ProcessModel model = new ProcessModel();
        model.setModelId(IdGenerator.newModelId());

        String processName = draft.path("processName").asText("未命名流程");
        String processId = "process_" + Math.abs(processName.hashCode()) + "_" + Long.toHexString(System.currentTimeMillis() % 100000);
        model.setProcess(new ProcessInfo(processId, processName, description, false));

        Map<String, String> tempToFormal = new HashMap<>();

        JsonNode pools = draft.path("pools");
        if (includePool && pools.isArray() && !pools.isEmpty()) {
            for (JsonNode poolNode : pools) {
                String poolId = IdGenerator.newPoolId(model);
                model.getPools().add(new Pool(poolId, poolNode.path("name").asText("协作方"), processId));
                if (poolNode.hasNonNull("temporaryId")) {
                    tempToFormal.put(poolNode.get("temporaryId").asText(), poolId);
                }
            }
        }

        for (JsonNode laneNode : draft.path("lanes")) {
            String laneId = IdGenerator.newLaneId(model);
            String poolRef = laneNode.hasNonNull("poolId")
                ? tempToFormal.getOrDefault(laneNode.get("poolId").asText(),
                    model.getPools().isEmpty() ? null : model.getPools().get(0).getId())
                : (model.getPools().isEmpty() ? null : model.getPools().get(0).getId());
            model.getLanes().add(new Lane(laneId, laneNode.path("name").asText("角色"), poolRef, new ArrayList<>()));
            if (laneNode.hasNonNull("temporaryId")) {
                tempToFormal.put(laneNode.get("temporaryId").asText(), laneId);
            }
        }

        for (JsonNode nodeJson : draft.path("nodes")) {
            String typeStr = nodeJson.path("nodeType").asText("");
            NodeType type;
            try {
                type = NodeType.valueOf(typeStr);
            } catch (IllegalArgumentException e) {
                throw new ModelOutputInvalidException("不支持的节点类型：" + typeStr);
            }
            String formalId = IdGenerator.newNodeId(model, type);
            FlowNode node = new FlowNode(formalId, type,
                nodeJson.path("name").asText(null),
                null,
                nodeJson.hasNonNull("documentation") ? nodeJson.get("documentation").asText() : null,
                new LinkedHashMap<>());
            model.getNodes().add(node);
            String tempId = nodeJson.hasNonNull("temporaryId")
                ? nodeJson.get("temporaryId").asText()
                : (nodeJson.hasNonNull("id") ? nodeJson.get("id").asText() : null);
            if (tempId != null) {
                tempToFormal.put(tempId, formalId);
            }
        }

        for (JsonNode flowJson : draft.path("sequenceFlows")) {
            String sourceId = tempToFormal.get(flowJson.path("sourceId").asText(""));
            String targetId = tempToFormal.get(flowJson.path("targetId").asText(""));
            if (sourceId == null || targetId == null) {
                throw new ModelOutputInvalidException("连线引用了未定义的节点："
                    + flowJson.path("sourceId").asText() + " → " + flowJson.path("targetId").asText());
            }
            model.getSequenceFlows().add(new SequenceFlow(IdGenerator.newFlowId(model), sourceId, targetId,
                flowJson.hasNonNull("name") ? flowJson.get("name").asText() : null,
                flowJson.hasNonNull("condition") ? flowJson.get("condition").asText() : null,
                flowJson.path("defaultFlow").asBoolean(false)));
        }

        // Lane归属
        for (FlowNode node : model.getNodes()) {
            String laneId = null;
            for (JsonNode nodeJson : draft.path("nodes")) {
                String tempId = nodeJson.hasNonNull("temporaryId")
                    ? nodeJson.get("temporaryId").asText()
                    : (nodeJson.hasNonNull("id") ? nodeJson.get("id").asText() : "");
                if (tempToFormal.get(tempId) != null && tempToFormal.get(tempId).equals(node.getId())) {
                    String laneTemp = nodeJson.path("laneId").asText(null);
                    if (laneTemp != null) {
                        laneId = tempToFormal.get(laneTemp);
                    }
                    break;
                }
            }
            node.setLaneId(laneId);
            if (laneId != null) {
                model.findLane(laneId).ifPresent(lane -> {
                    if (!lane.getNodeRefs().contains(node.getId())) {
                        lane.getNodeRefs().add(node.getId());
                    }
                });
            }
        }

        // 初始布局：Lane分行、拓扑列布局
        layoutService.fullLayout(model);

        model.getMetadata().setCreatedBy("AI");
        model.getMetadata().setGenerationPrompt(description);
        return model;
    }
}
