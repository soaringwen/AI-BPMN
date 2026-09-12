package com.bank.aibpmn.domain.model;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * ProcessModel是流程业务语义的唯一事实源（参见DESIGN.md 7.1）。
 */
public class ProcessModel {

    public static final String SCHEMA_VERSION = "1.0";

    private String schemaVersion = SCHEMA_VERSION;
    private String modelId;
    private ProcessInfo process = new ProcessInfo();
    private List<Pool> pools = new ArrayList<>();
    private List<Lane> lanes = new ArrayList<>();
    private List<FlowNode> nodes = new ArrayList<>();
    private List<SequenceFlow> sequenceFlows = new ArrayList<>();
    private List<MessageFlow> messageFlows = new ArrayList<>();
    private Layout layout = new Layout();
    private ModelMetadata metadata = new ModelMetadata();

    public ProcessModel() {
    }

    public ProcessModel copy() {
        ProcessModel copy = new ProcessModel();
        copy.schemaVersion = this.schemaVersion;
        copy.modelId = this.modelId;
        ProcessInfo p = new ProcessInfo(process.getId(), process.getName(), process.getDescription(),
            process.isExecutable());
        copy.process = p;
        pools.forEach(pool -> copy.pools.add(new Pool(pool.getId(), pool.getName(), pool.getProcessRef())));
        lanes.forEach(lane -> copy.lanes.add(new Lane(lane.getId(), lane.getName(), lane.getPoolId(),
            new ArrayList<>(lane.getNodeRefs()))));
        nodes.forEach(node -> copy.nodes.add(node.copy()));
        sequenceFlows.forEach(flow -> copy.sequenceFlows.add(flow.copy()));
        messageFlows.forEach(flow -> copy.messageFlows.add(
            new MessageFlow(flow.getId(), flow.getSourceId(), flow.getTargetId(), flow.getName())));
        copy.layout = layout.copy();
        copy.metadata = new ModelMetadata(metadata.getCreatedBy(), metadata.getGenerationPrompt());
        return copy;
    }

    public Optional<FlowNode> findNode(String nodeId) {
        return nodes.stream().filter(n -> n.getId().equals(nodeId)).findFirst();
    }

    public Optional<SequenceFlow> findSequenceFlow(String flowId) {
        return sequenceFlows.stream().filter(f -> f.getId().equals(flowId)).findFirst();
    }

    public Optional<Lane> findLane(String laneId) {
        return lanes.stream().filter(l -> l.getId().equals(laneId)).findFirst();
    }

    public Optional<Pool> findPool(String poolId) {
        return pools.stream().filter(p -> p.getId().equals(poolId)).findFirst();
    }

    public Map<String, List<SequenceFlow>> outgoingFlows() {
        Map<String, List<SequenceFlow>> map = new LinkedHashMap<>();
        for (SequenceFlow flow : sequenceFlows) {
            map.computeIfAbsent(flow.getSourceId(), k -> new ArrayList<>()).add(flow);
        }
        return map;
    }

    public Map<String, List<SequenceFlow>> incomingFlows() {
        Map<String, List<SequenceFlow>> map = new LinkedHashMap<>();
        for (SequenceFlow flow : sequenceFlows) {
            map.computeIfAbsent(flow.getTargetId(), k -> new ArrayList<>()).add(flow);
        }
        return map;
    }

    public List<SequenceFlow> flowsTouching(String nodeId) {
        return sequenceFlows.stream()
            .filter(f -> nodeId.equals(f.getSourceId()) || nodeId.equals(f.getTargetId()))
            .toList();
    }

    public String getSchemaVersion() {
        return schemaVersion;
    }

    public void setSchemaVersion(String schemaVersion) {
        this.schemaVersion = schemaVersion;
    }

    public String getModelId() {
        return modelId;
    }

    public void setModelId(String modelId) {
        this.modelId = modelId;
    }

    public ProcessInfo getProcess() {
        return process;
    }

    public void setProcess(ProcessInfo process) {
        this.process = process;
    }

    public List<Pool> getPools() {
        return pools;
    }

    public void setPools(List<Pool> pools) {
        this.pools = pools;
    }

    public List<Lane> getLanes() {
        return lanes;
    }

    public void setLanes(List<Lane> lanes) {
        this.lanes = lanes;
    }

    public List<FlowNode> getNodes() {
        return nodes;
    }

    public void setNodes(List<FlowNode> nodes) {
        this.nodes = nodes;
    }

    public List<SequenceFlow> getSequenceFlows() {
        return sequenceFlows;
    }

    public void setSequenceFlows(List<SequenceFlow> sequenceFlows) {
        this.sequenceFlows = sequenceFlows;
    }

    public List<MessageFlow> getMessageFlows() {
        return messageFlows;
    }

    public void setMessageFlows(List<MessageFlow> messageFlows) {
        this.messageFlows = messageFlows;
    }

    public Layout getLayout() {
        return layout;
    }

    public void setLayout(Layout layout) {
        this.layout = layout;
    }

    public ModelMetadata getMetadata() {
        return metadata;
    }

    public void setMetadata(ModelMetadata metadata) {
        this.metadata = metadata;
    }
}
