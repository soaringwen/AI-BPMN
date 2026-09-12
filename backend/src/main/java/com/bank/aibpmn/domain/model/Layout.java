package com.bank.aibpmn.domain.model;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * BPMN DI布局：节点Bounds与连线waypoints。
 */
public class Layout {

    private Map<String, Bounds> nodes = new LinkedHashMap<>();
    private Map<String, List<Point>> flows = new LinkedHashMap<>();

    public Layout() {
    }

    public Layout(Map<String, Bounds> nodes, Map<String, List<Point>> flows) {
        this.nodes = nodes;
        this.flows = flows;
    }

    public Layout copy() {
        Layout copy = new Layout();
        nodes.forEach((k, v) -> copy.getNodes().put(k, new Bounds(v.getX(), v.getY(), v.getWidth(), v.getHeight())));
        flows.forEach((k, v) -> copy.getFlows().put(k,
            v.stream().map(p -> new Point(p.getX(), p.getY())).toList()));
        return copy;
    }

    public Map<String, Bounds> getNodes() {
        return nodes;
    }

    public void setNodes(Map<String, Bounds> nodes) {
        this.nodes = nodes;
    }

    public Map<String, List<Point>> getFlows() {
        return flows;
    }

    public void setFlows(Map<String, List<Point>> flows) {
        this.flows = flows;
    }
}
