package com.bank.aibpmn.infrastructure.bpmn;

import com.bank.aibpmn.domain.model.Bounds;
import com.bank.aibpmn.domain.model.FlowNode;
import com.bank.aibpmn.domain.model.Lane;
import com.bank.aibpmn.domain.model.MessageFlow;
import com.bank.aibpmn.domain.model.NodeType;
import com.bank.aibpmn.domain.model.Point;
import com.bank.aibpmn.domain.model.Pool;
import com.bank.aibpmn.domain.model.ProcessInfo;
import com.bank.aibpmn.domain.model.ProcessModel;
import com.bank.aibpmn.domain.model.SequenceFlow;
import org.springframework.stereotype.Component;

import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * 标准BPMN 2.0 XML安全解析器。
 * 解析必须关闭外部实体、外部DTD、XInclude（DESIGN.md 10.4 / 19）。
 */
@Component
public class StandardBpmn20XmlParser implements BpmnXmlParser {

    private static final String BPMN_NS = "http://www.omg.org/spec/BPMN/20100524/MODEL";
    private static final String BPMNDI_NS = "http://www.omg.org/spec/BPMN/20100524/DI";
    private static final String DC_NS = "http://www.omg.org/spec/DD/20100524/DC";
    private static final String DI_NS = "http://www.omg.org/spec/DD/20100524/DI";

    private static final List<String> VENDOR_NAMESPACES = List.of(
        "http://flowable.org/bpmn", "http://flowable.org/bpmn20",
        "http://camunda.org/schema/1.0/bpmn", "http://zeebe.io/bpmn");

    private static final Map<String, NodeType> ELEMENT_TO_TYPE = new HashMap<>();

    static {
        for (NodeType type : NodeType.values()) {
            ELEMENT_TO_TYPE.put(type.bpmnElementName(), type);
        }
    }

    @Override
    public ProcessModel parse(String xml) {
        if (xml == null || xml.isBlank()) {
            throw new IllegalArgumentException("BPMN XML内容为空");
        }
        rejectDangerousContent(xml);

        ProcessModel model = new ProcessModel();
        model.setModelId("model_" + Long.toHexString(System.currentTimeMillis()));
        List<String> extensionWarnings = new ArrayList<>();

        try {
            XMLInputFactory factory = XMLInputFactory.newFactory();
            factory.setProperty(XMLInputFactory.SUPPORT_DTD, false);
            factory.setProperty(XMLInputFactory.IS_SUPPORTING_EXTERNAL_ENTITIES, false);
            factory.setProperty(XMLInputFactory.IS_REPLACING_ENTITY_REFERENCES, false);
            XMLStreamReader reader = factory.createXMLStreamReader(new StringReader(xml));

            String currentProcessId = null;
            String currentLaneId = null;
            List<String> pendingLaneRefs = new ArrayList<>();
            Map<String, List<String>> laneRefs = new HashMap<>();
            String currentShapeElement = null;
            String currentEdgeElement = null;
            List<Point> currentWaypoints = null;
            Bounds currentBounds = null;
            Map<String, String> nodeLaneByRef = new HashMap<>();
            FlowNode lastNode = null;
            SequenceFlow lastFlow = null;
            Map<String, String> defaultFlowRefs = new HashMap<>();

            while (reader.hasNext()) {
                int event = reader.next();
                if (event == XMLStreamConstants.START_ELEMENT) {
                    String ns = reader.getNamespaceURI() == null ? "" : reader.getNamespaceURI();
                    String local = reader.getLocalName();

                    if (VENDOR_NAMESPACES.stream().anyMatch(ns::startsWith)) {
                        extensionWarnings.add("检测到厂商扩展命名空间：" + ns);
                    }

                    switch (local) {
                        case "definitions" -> model.setSchemaVersion(ProcessModel.SCHEMA_VERSION);
                        case "collaboration" -> { /* 容器元素 */ }
                        case "participant" -> {
                            String id = attr(reader, "id");
                            String name = attr(reader, "name");
                            String processRef = attr(reader, "processRef");
                            model.getPools().add(new Pool(id, name, processRef));
                        }
                        case "messageFlow" -> {
                            model.getMessageFlows().add(new MessageFlow(attr(reader, "id"),
                                attr(reader, "sourceRef"), attr(reader, "targetRef"), attr(reader, "name")));
                        }
                        case "process" -> {
                            currentProcessId = attr(reader, "id");
                            boolean executable = Boolean.parseBoolean(attr(reader, "isExecutable"));
                            model.setProcess(new ProcessInfo(currentProcessId, attr(reader, "name"), null, executable));
                        }
                        case "lane" -> {
                            currentLaneId = attr(reader, "id");
                            String poolId = model.getPools().isEmpty() ? null : model.getPools().get(0).getId();
                            model.getLanes().add(new Lane(currentLaneId, attr(reader, "name"), poolId,
                                new ArrayList<>()));
                            laneRefs.put(currentLaneId, pendingLaneRefs = new ArrayList<>());
                        }
                        case "flowNodeRef" -> { /* text节点在CHARACTERS中处理 */ }
                        case "sequenceFlow" -> {
                            String id = attr(reader, "id");
                            SequenceFlow flow = new SequenceFlow(id, attr(reader, "sourceRef"),
                                attr(reader, "targetRef"), attr(reader, "name"), null, false);
                            if ("true".equalsIgnoreCase(attr(reader, "isDefault"))) {
                                flow.setDefaultFlow(true);
                            }
                            model.getSequenceFlows().add(flow);
                            lastFlow = flow;
                        }
                        case "conditionExpression" -> { /* CHARACTERS中处理 */ }
                        case "documentation" -> { /* CHARACTERS中处理 */ }
                        case "BPMNPlane" -> { /* DI容器 */ }
                        case "BPMNShape" -> {
                            currentShapeElement = attr(reader, "bpmnElement");
                            currentBounds = null;
                        }
                        case "BPMNEdge" -> {
                            currentEdgeElement = attr(reader, "bpmnElement");
                            currentWaypoints = new ArrayList<>();
                        }
                        case "Bounds" -> {
                            if (DC_NS.equals(ns)) {
                                currentBounds = new Bounds(
                                    Double.parseDouble(attr(reader, "x")),
                                    Double.parseDouble(attr(reader, "y")),
                                    Double.parseDouble(attr(reader, "width")),
                                    Double.parseDouble(attr(reader, "height")));
                            }
                        }
                        case "waypoint" -> {
                            if (DI_NS.equals(ns) && currentWaypoints != null) {
                                currentWaypoints.add(new Point(
                                    Double.parseDouble(attr(reader, "x")),
                                    Double.parseDouble(attr(reader, "y"))));
                            }
                        }
                        default -> {
                            NodeType type = ELEMENT_TO_TYPE.get(local);
                            if (type != null && BPMN_NS.equals(ns)) {
                                String nodeId = attr(reader, "id");
                                FlowNode node = new FlowNode(nodeId, type, attr(reader, "name"), null, null,
                                    new HashMap<>());
                                model.getNodes().add(node);
                                lastNode = node;
                                // 网关default属性指向默认流
                                String defaultFlowId = attr(reader, "default");
                                if (defaultFlowId != null && !defaultFlowId.isBlank()) {
                                    defaultFlowRefs.put(nodeId, defaultFlowId);
                                }
                            }
                        }
                    }
                    if ("flowNodeRef".equals(local) || "conditionExpression".equals(local)
                        || "documentation".equals(local)) {
                        String text = readText(reader);
                        if ("flowNodeRef".equals(local) && currentLaneId != null && !text.isBlank()) {
                            pendingLaneRefs.add(text.trim());
                            nodeLaneByRef.put(text.trim(), currentLaneId);
                        } else if ("conditionExpression".equals(local) && lastFlow != null) {
                            lastFlow.setCondition(text.trim());
                        } else if ("documentation".equals(local) && lastNode != null) {
                            lastNode.setDocumentation(text.trim());
                        }
                    }
                } else if (event == XMLStreamConstants.END_ELEMENT) {
                    String local = reader.getLocalName();
                    if ("lane".equals(local)) {
                        currentLaneId = null;
                    } else if ("BPMNShape".equals(local)) {
                        if (currentShapeElement != null && currentBounds != null) {
                            model.getLayout().getNodes().put(currentShapeElement, currentBounds);
                        }
                        currentShapeElement = null;
                        currentBounds = null;
                    } else if ("BPMNEdge".equals(local)) {
                        if (currentEdgeElement != null && currentWaypoints != null && !currentWaypoints.isEmpty()) {
                            model.getLayout().getFlows().put(currentEdgeElement, currentWaypoints);
                        }
                        currentEdgeElement = null;
                        currentWaypoints = null;
                    }
                }
            }
            reader.close();

            laneRefs.forEach((laneId, refs) -> model.findLane(laneId)
                .ifPresent(lane -> refs.forEach(ref -> {
                    if (!lane.getNodeRefs().contains(ref)) {
                        lane.getNodeRefs().add(ref);
                    }
                })));
            defaultFlowRefs.forEach((gatewayId, flowId) -> model.findSequenceFlow(flowId)
                .ifPresent(flow -> flow.setDefaultFlow(true)));
            model.getNodes().forEach(node -> {
                if (node.getLaneId() == null) {
                    node.setLaneId(nodeLaneByRef.get(node.getId()));
                }
            });

            if (model.getProcess().getId() == null) {
                throw new IllegalArgumentException("BPMN XML中未找到process元素");
            }
            if (!extensionWarnings.isEmpty()) {
                model.getMetadata().setGenerationPrompt(String.join("; ", extensionWarnings));
            }
            return model;
        } catch (XMLStreamException e) {
            throw new IllegalArgumentException("BPMN XML解析失败：" + e.getMessage(), e);
        }
    }

    private String readText(XMLStreamReader reader) throws XMLStreamException {
        StringBuilder text = new StringBuilder();
        while (reader.hasNext()) {
            int event = reader.next();
            if (event == XMLStreamConstants.CHARACTERS) {
                text.append(reader.getText());
            } else if (event == XMLStreamConstants.END_ELEMENT) {
                break;
            }
        }
        return text.toString();
    }

    private String attr(XMLStreamReader reader, String name) {
        for (int i = 0; i < reader.getAttributeCount(); i++) {
            if (name.equals(reader.getAttributeLocalName(i))) {
                return reader.getAttributeValue(i);
            }
        }
        return null;
    }

    private void rejectDangerousContent(String xml) {
        String head = xml.substring(0, Math.min(xml.length(), 4096)).toLowerCase(Locale.ROOT);
        String whole = xml.toLowerCase(Locale.ROOT);
        if (head.contains("<!doctype") || head.contains("<!entity")) {
            throw new IllegalArgumentException("BPMN XML包含DTD声明，已被安全策略拒绝");
        }
        if (whole.contains("<!entity")) {
            throw new IllegalArgumentException("BPMN XML包含外部实体定义，已被安全策略拒绝");
        }
        if (whole.contains("xinclude")) {
            throw new IllegalArgumentException("BPMN XML包含XInclude，已被安全策略拒绝");
        }
    }
}
