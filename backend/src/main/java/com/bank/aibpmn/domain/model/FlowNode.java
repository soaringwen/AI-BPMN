package com.bank.aibpmn.domain.model;

import java.util.Map;

public class FlowNode {

    private String id;
    private NodeType type;
    private String name;
    private String laneId;
    private String documentation;
    private Map<String, Object> properties = Map.of();

    public FlowNode() {
    }

    public FlowNode(String id, NodeType type, String name, String laneId, String documentation,
                    Map<String, Object> properties) {
        this.id = id;
        this.type = type;
        this.name = name;
        this.laneId = laneId;
        this.documentation = documentation;
        this.properties = properties == null ? Map.of() : properties;
    }

    public FlowNode copy() {
        return new FlowNode(id, type, name, laneId, documentation,
            properties == null ? Map.of() : Map.copyOf(properties));
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public NodeType getType() {
        return type;
    }

    public void setType(NodeType type) {
        this.type = type;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getLaneId() {
        return laneId;
    }

    public void setLaneId(String laneId) {
        this.laneId = laneId;
    }

    public String getDocumentation() {
        return documentation;
    }

    public void setDocumentation(String documentation) {
        this.documentation = documentation;
    }

    public Map<String, Object> getProperties() {
        return properties;
    }

    public void setProperties(Map<String, Object> properties) {
        this.properties = properties;
    }
}
