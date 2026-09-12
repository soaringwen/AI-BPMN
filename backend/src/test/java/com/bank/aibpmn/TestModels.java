package com.bank.aibpmn;

import com.bank.aibpmn.domain.layout.LayoutService;
import com.bank.aibpmn.domain.model.FlowNode;
import com.bank.aibpmn.domain.model.Lane;
import com.bank.aibpmn.domain.model.NodeType;
import com.bank.aibpmn.domain.model.Pool;
import com.bank.aibpmn.domain.model.ProcessInfo;
import com.bank.aibpmn.domain.model.ProcessModel;
import com.bank.aibpmn.domain.model.SequenceFlow;

import java.util.ArrayList;
import java.util.LinkedHashMap;

/**
 * 测试共享模型工厂。
 */
public final class TestModels {

    private TestModels() {
    }

    public static ProcessModel simpleApproval() {
        ProcessModel model = new ProcessModel();
        model.setModelId("model_test");
        model.setProcess(new ProcessInfo("process_test", "测试审批流程", null, false));
        model.getPools().add(new Pool("pool_1", "测试机构", "process_test"));
        model.getLanes().add(new Lane("lane_a", "申请人", "pool_1", new ArrayList<>()));
        model.getLanes().add(new Lane("lane_b", "审批人", "pool_1", new ArrayList<>()));

        model.getNodes().add(new FlowNode("event_start_1", NodeType.START_EVENT, "开始", "lane_a", null,
            new LinkedHashMap<>()));
        model.getNodes().add(new FlowNode("task_submit_1", NodeType.USER_TASK, "提交申请", "lane_a", null,
            new LinkedHashMap<>()));
        model.getNodes().add(new FlowNode("task_approve_1", NodeType.USER_TASK, "审批", "lane_b", null,
            new LinkedHashMap<>()));
        model.getNodes().add(new FlowNode("gateway_1", NodeType.EXCLUSIVE_GATEWAY, "是否通过", "lane_b", null,
            new LinkedHashMap<>()));
        model.getNodes().add(new FlowNode("event_end_1", NodeType.END_EVENT, "结束", "lane_a", null,
            new LinkedHashMap<>()));

        model.getSequenceFlows().add(new SequenceFlow("flow_1", "event_start_1", "task_submit_1", null, null, false));
        model.getSequenceFlows().add(new SequenceFlow("flow_2", "task_submit_1", "task_approve_1", null, null, false));
        model.getSequenceFlows().add(new SequenceFlow("flow_3", "task_approve_1", "gateway_1", null, null, false));
        model.getSequenceFlows().add(new SequenceFlow("flow_4", "gateway_1", "event_end_1", "通过", null, false));

        model.getLanes().get(0).getNodeRefs().addAll(java.util.List.of("event_start_1", "task_submit_1", "event_end_1"));
        model.getLanes().get(1).getNodeRefs().addAll(java.util.List.of("task_approve_1", "gateway_1"));

        new LayoutService().fullLayout(model);
        return model;
    }

    public static FlowNode node(String id, NodeType type, String name) {
        return new FlowNode(id, type, name, null, null, new LinkedHashMap<>());
    }
}
