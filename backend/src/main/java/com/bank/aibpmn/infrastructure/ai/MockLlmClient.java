package com.bank.aibpmn.infrastructure.ai;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * 演示模式模型客户端：不调用外部模型，返回确定性的结构化结果，
 * 用于无模型网关环境下的开发与端到端演示。
 */
@Component
@ConditionalOnProperty(prefix = "app.ai", name = "mock-enabled", havingValue = "true")
public class MockLlmClient implements LlmClient {

    private static final Logger log = LoggerFactory.getLogger(MockLlmClient.class);

    private static final String SYSTEM = "演示模式：返回确定性结构化JSON。";

    @Override
    public String complete(String system, String user) {
        log.info("MockLlmClient处理请求，任务标记：{}", extractTask(user));
        return switch (extractTask(user)) {
            case "GENERATE" -> mockGenerate(user);
            case "MODIFY" -> mockModify(user);
            default -> "{\"answer\": \"（演示模式）当前流程包含开始事件、审批任务、排他网关与结束事件。"
                + "拒绝分支退回申请人后可重新提交，最多一次。\", \"suggestions\": ["
                + "\"建议为排他网关补充默认路径\", \"审批任务建议明确责任角色\"]}";
        };
    }

    private String extractTask(String user) {
        if (user.contains("TASK=GENERATE")) {
            return "GENERATE";
        }
        if (user.contains("TASK=MODIFY")) {
            return "MODIFY";
        }
        return "CONSULT";
    }

    private String mockGenerate(String user) {
        // 若描述中包含“澄清”触发词则演示澄清路径
        if (user.contains("[CLARIFY_HINT]") && user.contains("未说明拒绝路径")) {
            return """
                {"status":"CLARIFICATION_REQUIRED",
                 "questions":[
                   {"questionId":"q1","question":"（演示模式）审批不通过时应如何处理？","type":"SINGLE_SELECT",
                    "options":["退回申请人修改","流程直接结束","提交上级决定"],"required":true}
                 ]}
                """;
        }
        return """
            {"status":"COMPLETED",
             "processDraft":{
               "processName":"演示审批流程",
               "pools":[{"temporaryId":"pool_1","name":"演示机构"}],
               "lanes":[
                 {"temporaryId":"lane_1","name":"申请人"},
                 {"temporaryId":"lane_2","name":"审批人"}
               ],
               "nodes":[
                 {"temporaryId":"n1","nodeType":"START_EVENT","name":"开始","laneId":"lane_1"},
                 {"temporaryId":"n2","nodeType":"USER_TASK","name":"提交申请","laneId":"lane_1"},
                 {"temporaryId":"n3","nodeType":"USER_TASK","name":"审批","laneId":"lane_2"},
                 {"temporaryId":"n4","nodeType":"EXCLUSIVE_GATEWAY","name":"是否通过","laneId":"lane_2"},
                 {"temporaryId":"n5","nodeType":"END_EVENT","name":"结束","laneId":"lane_1"}
               ],
               "sequenceFlows":[
                 {"temporaryId":"f1","sourceId":"n1","targetId":"n2"},
                 {"temporaryId":"f2","sourceId":"n2","targetId":"n3"},
                 {"temporaryId":"f3","sourceId":"n3","targetId":"n4"},
                 {"temporaryId":"f4","sourceId":"n4","targetId":"n5","name":"通过"}
               ]
             }}
            """;
    }

    private String mockModify(String user) {
        return """
            {"summary":"（演示模式）在首个用户任务后增加“复核”环节，并从排他网关增加退回路径",
             "clarificationRequired":false,
             "questions":[],
             "operations":[
               {"operationId":"op-1","type":"ADD_NODE","targetId":null,
                "payload":{"temporaryId":"new_node_1","nodeType":"USER_TASK","name":"复核",
                           "insertAfter":"__FIRST_USER_TASK__"}},
               {"operationId":"op-2","type":"UPDATE_FLOW","targetId":"__FIRST_GATEWAY_FLOW__",
                "payload":{"defaultFlow":true}},
               {"operationId":"op-3","type":"ADD_BRANCH","targetId":null,
                "payload":{"sourceId":"__FIRST_GATEWAY__","branchName":"不通过","condition":"${approved == false}","targetId":"__FIRST_USER_TASK__"}}
             ]}
            """;
    }

}
