package com.bank.aibpmn.domain.model;

import java.util.ArrayList;
import java.util.List;

public class Lane {

    private String id;
    private String name;
    private String poolId;
    private List<String> nodeRefs = new ArrayList<>();

    public Lane() {
    }

    public Lane(String id, String name, String poolId, List<String> nodeRefs) {
        this.id = id;
        this.name = name;
        this.poolId = poolId;
        this.nodeRefs = nodeRefs == null ? new ArrayList<>() : new ArrayList<>(nodeRefs);
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getPoolId() {
        return poolId;
    }

    public void setPoolId(String poolId) {
        this.poolId = poolId;
    }

    public List<String> getNodeRefs() {
        return nodeRefs;
    }

    public void setNodeRefs(List<String> nodeRefs) {
        this.nodeRefs = nodeRefs;
    }
}
