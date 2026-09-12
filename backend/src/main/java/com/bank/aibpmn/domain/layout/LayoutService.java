package com.bank.aibpmn.domain.layout;

import com.bank.aibpmn.domain.model.Bounds;
import com.bank.aibpmn.domain.model.FlowNode;
import com.bank.aibpmn.domain.model.NodeType;
import com.bank.aibpmn.domain.model.Point;
import com.bank.aibpmn.domain.model.ProcessModel;
import com.bank.aibpmn.domain.model.SequenceFlow;
import org.springframework.stereotype.Component;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * 布局服务：
 * - 未变化节点沿用原坐标；
 * - 未变化连线沿用原waypoints；
 * - 新节点只执行局部布局（DESIGN.md 14.1）。
 */
@Component
public class LayoutService {

    public static final int TASK_WIDTH = 100;
    public static final int TASK_HEIGHT = 80;
    public static final int EVENT_SIZE = 36;
    public static final int GATEWAY_SIZE = 50;
    public static final int GAP = 60;

    /**
     * 在prev与next之间插入一条新节点链，返回链尾插入点应连接的原后继。
     * 若空间不足，仅向右平移直接下游受影响节点。
     */
    public void insertChain(ProcessModel model, List<String> chainNodeIds, String prevId, String nextId) {
        FlowNode prev = model.findNode(prevId).orElseThrow();
        double cursorX = prevId.equals(nextId)
            ? bounds(model, prevId).map(Bounds::right).orElse(0.0) + GAP
            : bounds(model, prevId).map(Bounds::right).orElse(0.0) + GAP;

        double y = bounds(model, prevId).map(Bounds::centerY).orElse(200.0);

        List<Bounds> newBounds = new ArrayList<>();
        for (String nodeId : chainNodeIds) {
            Optional<Bounds> existing = bounds(model, nodeId);
            final double cursor = cursorX;
            Bounds b = existing.orElseGet(() -> defaultBounds(model, nodeId, cursor, y));
            if (existing.isEmpty()) {
                b.setX(cursorX);
                b.setY(y - b.getHeight() / 2);
                model.getLayout().getNodes().put(nodeId, b);
                newBounds.add(b);
            }
            cursorX = b.right() + GAP;
        }

        if (nextId != null && !nextId.equals(prevId)) {
            Bounds nextBounds = model.getLayout().getNodes().get(nextId);
            if (nextBounds != null && cursorX > nextBounds.getX()) {
                // 空间不足：局部右移直接下游节点
                double delta = cursorX - nextBounds.getX();
                shiftDownstream(model, nextId, delta);
            }
        }
    }

    public void recomputeWaypointsFor(ProcessModel model, Set<String> touchedNodeIds) {
        for (SequenceFlow flow : model.getSequenceFlows()) {
            if (touchedNodeIds.contains(flow.getSourceId()) || touchedNodeIds.contains(flow.getTargetId())) {
                model.getLayout().getFlows().put(flow.getId(), straightWaypoints(model, flow));
            }
        }
        Set<String> known = new LinkedHashSet<>();
        model.getSequenceFlows().forEach(f -> known.add(f.getId()));
        model.getLayout().getFlows().keySet().retainAll(known);
        Set<String> knownNodes = new LinkedHashSet<>();
        model.getNodes().forEach(n -> knownNodes.add(n.getId()));
        model.getLayout().getNodes().keySet().retainAll(knownNodes);
    }

    public List<Point> straightWaypoints(ProcessModel model, SequenceFlow flow) {
        Bounds source = model.getLayout().getNodes().get(flow.getSourceId());
        Bounds target = model.getLayout().getNodes().get(flow.getTargetId());
        if (source == null || target == null) {
            return List.of(new Point(0, 0), new Point(0, 0));
        }
        return List.of(
            new Point(source.right(), source.centerY()),
            new Point(target.getX(), target.centerY()));
    }

    /**
     * 全图自动布局：按Lane分行、拓扑序从左到右排列（用户可手工触发）。
     */
    public void fullLayout(ProcessModel model) {
        Map<String, List<SequenceFlow>> outgoing = model.outgoingFlows();
        Map<String, List<SequenceFlow>> incoming = model.incomingFlows();
        Map<String, Integer> depth = new LinkedHashMap<>();

        Deque<String> queue = new ArrayDeque<>();
        for (FlowNode node : model.getNodes()) {
            if (node.getType() == NodeType.START_EVENT || incoming.getOrDefault(node.getId(), List.of()).isEmpty()) {
                depth.put(node.getId(), 0);
                queue.add(node.getId());
            }
        }
        while (!queue.isEmpty()) {
            String current = queue.poll();
            int d = depth.getOrDefault(current, 0);
            for (SequenceFlow flow : outgoing.getOrDefault(current, List.of())) {
                int candidate = d + 1;
                if (candidate > depth.getOrDefault(flow.getTargetId(), -1) && candidate < 200) {
                    depth.put(flow.getTargetId(), candidate);
                    queue.add(flow.getTargetId());
                }
            }
        }

        Map<String, Integer> laneRow = new LinkedHashMap<>();
        int rowIndex = 0;
        for (com.bank.aibpmn.domain.model.Lane lane : model.getLanes()) {
            laneRow.put(lane.getId(), rowIndex++);
        }

        Map<Integer, Integer> columnCursor = new LinkedHashMap<>();
        Map<String, List<String>> laneColumns = new LinkedHashMap<>();
        for (FlowNode node : model.getNodes()) {
            int col = depth.getOrDefault(node.getId(), 0);
            if (col >= 200) {
                col = 0;
            }
            laneColumns.computeIfAbsent(String.valueOf(laneIndex(model, node.getLaneId())), k -> new ArrayList<>());
            laneColumns.get(String.valueOf(laneIndex(model, node.getLaneId()))).add(node.getId());
            columnCursor.put(col, Math.max(columnCursor.getOrDefault(col, 0), col));
        }

        for (FlowNode node : model.getNodes()) {
            int col = Math.min(depth.getOrDefault(node.getId(), 0), 100);
            int row = laneRow.getOrDefault(node.getLaneId(), laneRow.size());
            double x = 120 + col * (TASK_WIDTH + GAP * 2);
            double y = 100 + row * 160;
            model.getLayout().getNodes().put(node.getId(), defaultBounds(model, node.getId(), x, y - defaultHeight(node.getType()) / 2.0));
        }
        recomputeWaypointsFor(model, model.getNodes().stream().map(FlowNode::getId).collect(java.util.stream.Collectors.toSet()));
    }

    private int laneIndex(ProcessModel model, String laneId) {
        for (int i = 0; i < model.getLanes().size(); i++) {
            if (model.getLanes().get(i).getId().equals(laneId)) {
                return i;
            }
        }
        return model.getLanes().size();
    }

    private void shiftDownstream(ProcessModel model, String startNodeId, double delta) {
        Set<String> visited = new LinkedHashSet<>();
        Deque<String> queue = new ArrayDeque<>();
        queue.add(startNodeId);
        while (!queue.isEmpty()) {
            String nodeId = queue.poll();
            if (!visited.add(nodeId)) {
                continue;
            }
            Bounds b = model.getLayout().getNodes().get(nodeId);
            if (b != null) {
                b.setX(b.getX() + delta);
            }
            for (SequenceFlow flow : model.outgoingFlows().getOrDefault(nodeId, List.of())) {
                queue.add(flow.getTargetId());
            }
        }
    }

    private Optional<Bounds> bounds(ProcessModel model, String nodeId) {
        return Optional.ofNullable(model.getLayout().getNodes().get(nodeId));
    }

    public Bounds defaultBounds(ProcessModel model, String nodeId, double x, double y) {
        FlowNode node = model.findNode(nodeId).orElse(null);
        NodeType type = node != null ? node.getType() : NodeType.USER_TASK;
        double w = defaultWidth(type);
        double h = defaultHeight(type);
        return new Bounds(x, y, w, h);
    }

    public double defaultWidth(NodeType type) {
        if (type.isEvent()) {
            return EVENT_SIZE;
        }
        if (type.isGateway()) {
            return GATEWAY_SIZE;
        }
        return TASK_WIDTH;
    }

    public double defaultHeight(NodeType type) {
        if (type.isEvent()) {
            return EVENT_SIZE;
        }
        if (type.isGateway()) {
            return GATEWAY_SIZE;
        }
        return TASK_HEIGHT;
    }
}
