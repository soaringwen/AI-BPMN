package com.bank.aibpmn.infrastructure.bpmn;

import com.bank.aibpmn.TestModels;
import com.bank.aibpmn.config.AppProperties;
import com.bank.aibpmn.domain.model.NodeType;
import com.bank.aibpmn.domain.model.ProcessModel;
import com.bank.aibpmn.domain.validation.ProcessModelValidator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * ProcessModel → XML → ProcessModel 往返测试（DESIGN.md 24.1）。
 */
class BpmnXmlRoundTripTest {

    private StandardBpmn20XmlGenerator generator;
    private StandardBpmn20XmlParser parser;

    @BeforeEach
    void setUp() {
        AppProperties properties = new AppProperties();
        generator = new StandardBpmn20XmlGenerator(properties, new ProcessModelValidator());
        parser = new StandardBpmn20XmlParser();
    }

    @Test
    void generateProducesStandardNamespacesWithoutVendorExtensions() {
        String xml = generator.generate(TestModels.simpleApproval());

        assertThat(xml).contains("http://www.omg.org/spec/BPMN/20100524/MODEL");
        assertThat(xml).contains("http://www.omg.org/spec/BPMN/20100524/DI");
        assertThat(xml).contains("isExecutable=\"false\"");
        assertThat(xml).doesNotContain("flowable");
        assertThat(xml).doesNotContain("camunda");
        assertThat(xml).doesNotContain("zeebe");
    }

    @Test
    void roundTripPreservesCoreSemantics() {
        ProcessModel original = TestModels.simpleApproval();
        String xml = generator.generate(original);
        ProcessModel parsed = parser.parse(xml);

        assertThat(parsed.getProcess().getId()).isEqualTo("process_test");
        assertThat(parsed.getProcess().isExecutable()).isFalse();
        assertThat(parsed.getNodes()).hasSameSizeAs(original.getNodes());
        assertThat(parsed.getSequenceFlows()).hasSameSizeAs(original.getSequenceFlows());
        assertThat(parsed.getLanes()).hasSameSizeAs(original.getLanes());
        assertThat(parsed.getLanes().get(1).getNodeRefs()).containsExactlyInAnyOrder("task_approve_1", "gateway_1");
        assertThat(parsed.findNode("task_approve_1")).isPresent();
        assertThat(parsed.findNode("task_approve_1").orElseThrow().getType()).isEqualTo(NodeType.USER_TASK);
        assertThat(parsed.getLayout().getNodes()).containsKey("task_submit_1");
        assertThat(parsed.getLayout().getFlows()).containsKey("flow_1");
    }

    @Test
    void generatedXmlContainsDi() {
        String xml = generator.generate(TestModels.simpleApproval());
        assertThat(xml).contains("BPMNDiagram");
        assertThat(xml).contains("BPMNShape");
        assertThat(xml).contains("BPMNEdge");
        assertThat(xml).contains("dc:Bounds");
        assertThat(xml).contains("di:waypoint");
    }

    @Test
    void generatedXmlContainsPoolAndLaneShapes() {
        // bpmn-js要求：协作图中必须为participant/lane提供DI形状，否则拖拽编辑报错
        String xml = generator.generate(TestModels.simpleApproval());
        assertThat(xml).contains("bpmnElement=\"pool_1\"");
        assertThat(xml).contains("bpmnElement=\"lane_a\"");
        assertThat(xml).contains("isHorizontal=\"true\"");
    }
}
