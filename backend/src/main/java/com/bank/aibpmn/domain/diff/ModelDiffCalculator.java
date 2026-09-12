package com.bank.aibpmn.domain.diff;

import com.bank.aibpmn.domain.model.FlowNode;
import com.bank.aibpmn.domain.model.ProcessModel;
import com.bank.aibpmn.domain.model.SequenceFlow;
import org.springframework.stereotype.Component;

import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 差异计算：比较基础模型与建议模型，供前端展示AI修改预览（DESIGN.md 13.3）。
 */
@Component
public class ModelDiffCalculator {

    public ModelDiff diff(ProcessModel base, ProcessModel proposed) {
        ModelDiff diff = new ModelDiff();
        if (base == null || proposed == null) {
            return diff;
        }

        Set<String> baseNodeIds = base.getNodes().stream().map(FlowNode::getId).collect(Collectors.toSet());
        Set<String> proposedNodeIds = proposed.getNodes().stream().map(FlowNode::getId).collect(Collectors.toSet());

        for (FlowNode node : proposed.getNodes()) {
            if (!baseNodeIds.contains(node.getId())) {
                diff.getAdded().add(new ElementChange(node.getId(),
                    node.getType() == null ? null : node.getType().name(), node.getName()));
            } else {
                FlowNode before = base.findNode(node.getId()).orElseThrow();
                if (changed(before, node)) {
                    diff.getUpdated().add(new ElementChange(node.getId(),
                        node.getType() == null ? null : node.getType().name(), node.getName()));
                }
            }
        }
        for (FlowNode node : base.getNodes()) {
            if (!proposedNodeIds.contains(node.getId())) {
                diff.getDeleted().add(new ElementChange(node.getId(),
                    node.getType() == null ? null : node.getType().name(), node.getName()));
            }
        }

        Set<String> baseFlowIds = base.getSequenceFlows().stream().map(SequenceFlow::getId).collect(Collectors.toSet());
        Set<String> proposedFlowIds = proposed.getSequenceFlows().stream().map(SequenceFlow::getId)
            .collect(Collectors.toSet());

        for (SequenceFlow flow : proposed.getSequenceFlows()) {
            if (!baseFlowIds.contains(flow.getId())) {
                diff.getFlowsAdded().add(new ElementChange(flow.getId(), "SEQUENCE_FLOW",
                    describeFlow(proposed, flow)));
            } else {
                SequenceFlow before = base.findSequenceFlow(flow.getId()).orElseThrow();
                if (flowChanged(before, flow)) {
                    diff.getFlowsUpdated().add(new ElementChange(flow.getId(), "SEQUENCE_FLOW",
                        describeFlow(proposed, flow)));
                }
            }
        }
        for (SequenceFlow flow : base.getSequenceFlows()) {
            if (!proposedFlowIds.contains(flow.getId())) {
                diff.getFlowsDeleted().add(new ElementChange(flow.getId(), "SEQUENCE_FLOW",
                    describeFlow(base, flow)));
            }
        }

        return diff;
    }

    private String describeFlow(ProcessModel model, SequenceFlow flow) {
        String source = model.findNode(flow.getSourceId()).map(FlowNode::getName).orElse(flow.getSourceId());
        String target = model.findNode(flow.getTargetId()).map(FlowNode::getName).orElse(flow.getTargetId());
        String label = flow.getName() == null ? "" : "[" + flow.getName() + "] ";
        return label + source + " → " + target;
    }

    private boolean changed(FlowNode before, FlowNode after) {
        return !Objects.equals(before.getName(), after.getName())
            || before.getType() != after.getType()
            || !Objects.equals(before.getLaneId(), after.getLaneId())
            || !Objects.equals(before.getDocumentation(), after.getDocumentation());
    }

    private boolean flowChanged(SequenceFlow before, SequenceFlow after) {
        return !Objects.equals(before.getSourceId(), after.getSourceId())
            || !Objects.equals(before.getTargetId(), after.getTargetId())
            || !Objects.equals(before.getName(), after.getName())
            || !Objects.equals(before.getCondition(), after.getCondition())
            || before.isDefaultFlow() != after.isDefaultFlow();
    }
}
