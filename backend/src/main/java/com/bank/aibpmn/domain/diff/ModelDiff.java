package com.bank.aibpmn.domain.diff;

import java.util.ArrayList;
import java.util.List;

public class ModelDiff {

    private List<ElementChange> added = new ArrayList<>();
    private List<ElementChange> updated = new ArrayList<>();
    private List<ElementChange> deleted = new ArrayList<>();
    private List<ElementChange> flowsAdded = new ArrayList<>();
    private List<ElementChange> flowsUpdated = new ArrayList<>();
    private List<ElementChange> flowsDeleted = new ArrayList<>();

    public boolean isEmpty() {
        return added.isEmpty() && updated.isEmpty() && deleted.isEmpty()
            && flowsAdded.isEmpty() && flowsUpdated.isEmpty() && flowsDeleted.isEmpty();
    }

    public List<ElementChange> getAdded() {
        return added;
    }

    public void setAdded(List<ElementChange> added) {
        this.added = added;
    }

    public List<ElementChange> getUpdated() {
        return updated;
    }

    public void setUpdated(List<ElementChange> updated) {
        this.updated = updated;
    }

    public List<ElementChange> getDeleted() {
        return deleted;
    }

    public void setDeleted(List<ElementChange> deleted) {
        this.deleted = deleted;
    }

    public List<ElementChange> getFlowsAdded() {
        return flowsAdded;
    }

    public void setFlowsAdded(List<ElementChange> flowsAdded) {
        this.flowsAdded = flowsAdded;
    }

    public List<ElementChange> getFlowsUpdated() {
        return flowsUpdated;
    }

    public void setFlowsUpdated(List<ElementChange> flowsUpdated) {
        this.flowsUpdated = flowsUpdated;
    }

    public List<ElementChange> getFlowsDeleted() {
        return flowsDeleted;
    }

    public void setFlowsDeleted(List<ElementChange> flowsDeleted) {
        this.flowsDeleted = flowsDeleted;
    }
}
