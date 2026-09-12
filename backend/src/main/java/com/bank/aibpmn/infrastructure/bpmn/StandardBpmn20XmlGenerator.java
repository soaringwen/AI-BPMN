package com.bank.aibpmn.infrastructure.bpmn;

import com.bank.aibpmn.config.AppProperties;
import com.bank.aibpmn.domain.model.Bounds;
import com.bank.aibpmn.domain.model.FlowNode;
import com.bank.aibpmn.domain.model.Lane;
import com.bank.aibpmn.domain.model.NodeType;
import com.bank.aibpmn.domain.model.Point;
import com.bank.aibpmn.domain.model.Pool;
import com.bank.aibpmn.domain.model.ProcessModel;
import com.bank.aibpmn.domain.model.SequenceFlow;
import com.bank.aibpmn.domain.validation.ProcessModelValidator;
import com.bank.aibpmn.domain.validation.ValidationResult;
import org.springframework.stereotype.Component;

import javax.xml.XMLConstants;
import javax.xml.stream.XMLOutputFactory;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamWriter;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.stream.StreamResult;
import javax.xml.transform.stream.StreamSource;
import java.io.StringReader;
import java.io.StringWriter;
import java.util.List;
import java.util.Map;

/**
 * 标准BPMN 2.0 XML生成器。
 * 使用StAX XMLStreamWriter，禁止字符串拼接（DESIGN.md 10.3）。
 * 不包含任何Flowable/Camunda判断逻辑（DESIGN.md 3.6）。
 */
@Component
public class StandardBpmn20XmlGenerator implements BpmnXmlGenerator {

    private static final String BPMN_NS = "http://www.omg.org/spec/BPMN/20100524/MODEL";
    private static final String BPMNDI_NS = "http://www.omg.org/spec/BPMN/20100524/DI";
    private static final String DC_NS = "http://www.omg.org/spec/DD/20100524/DC";
    private static final String DI_NS = "http://www.omg.org/spec/DD/20100524/DI";
    private static final String XSI_NS = "http://www.w3.org/2001/XMLSchema-instance";

    private final AppProperties properties;
    private final ProcessModelValidator validator;

    public StandardBpmn20XmlGenerator(AppProperties properties, ProcessModelValidator validator) {
        this.properties = properties;
        this.validator = validator;
    }

    @Override
    public String generate(ProcessModel model) {
        ValidationResult validation = validator.validate(model);
        if (!validation.isValid()) {
            throw new IllegalArgumentException("ProcessModel校验失败，无法生成BPMN XML："
                + validation.getErrors().get(0).getMessage());
        }

        try {
            XMLOutputFactory factory = XMLOutputFactory.newFactory();
            factory.setProperty(XMLOutputFactory.IS_REPAIRING_NAMESPACES, true);
            StringWriter raw = new StringWriter();
            XMLStreamWriter w = factory.createXMLStreamWriter(raw);

            w.writeStartDocument("UTF-8", "1.0");

            String modelKey = sanitizeKey(model.getModelId());
            w.writeStartElement("bpmn", "definitions", BPMN_NS);
            w.writeNamespace("bpmn", BPMN_NS);
            w.writeNamespace("bpmndi", BPMNDI_NS);
            w.writeNamespace("dc", DC_NS);
            w.writeNamespace("di", DI_NS);
            w.writeNamespace("xsi", XSI_NS);
            w.writeAttribute("id", "Definitions_" + modelKey);
            w.writeAttribute("targetNamespace", properties.getBpmn().getTargetNamespace());

            boolean hasPools = !model.getPools().isEmpty();
            String planeElementRef;
            if (hasPools) {
                String collaborationId = "Collaboration_" + modelKey;
                w.writeStartElement("bpmn", "collaboration", BPMN_NS);
                w.writeAttribute("id", collaborationId);
                for (Pool pool : model.getPools()) {
                    w.writeEmptyElement("bpmn", "participant", BPMN_NS);
                    w.writeAttribute("id", pool.getId());
                    if (pool.getName() != null) {
                        w.writeAttribute("name", pool.getName());
                    }
                    w.writeAttribute("processRef", pool.getProcessRef());
                }
                writeMessageFlows(w, model);
                w.writeEndElement();
                planeElementRef = collaborationId;
            } else {
                planeElementRef = model.getProcess().getId();
            }

            w.writeStartElement("bpmn", "process", BPMN_NS);
            w.writeAttribute("id", model.getProcess().getId());
            if (model.getProcess().getName() != null) {
                w.writeAttribute("name", model.getProcess().getName());
            }
            w.writeAttribute("isExecutable", String.valueOf(model.getProcess().isExecutable()));

            if (hasPools && !model.getLanes().isEmpty()) {
                w.writeStartElement("bpmn", "laneSet", BPMN_NS);
                w.writeAttribute("id", "LaneSet_" + modelKey);
                for (Lane lane : model.getLanes()) {
                    w.writeStartElement("bpmn", "lane", BPMN_NS);
                    w.writeAttribute("id", lane.getId());
                    if (lane.getName() != null) {
                        w.writeAttribute("name", lane.getName());
                    }
                    for (String nodeRef : lane.getNodeRefs()) {
                        w.writeStartElement("bpmn", "flowNodeRef", BPMN_NS);
                        w.writeCharacters(nodeRef);
                        w.writeEndElement();
                    }
                    w.writeEndElement();
                }
                w.writeEndElement();
            }

            for (FlowNode node : model.getNodes()) {
                writeNode(w, node, model);
            }
            for (SequenceFlow flow : model.getSequenceFlows()) {
                writeSequenceFlow(w, flow);
            }
            w.writeEndElement(); // process

            writeDiagram(w, model, planeElementRef, modelKey);

            w.writeEndElement(); // definitions
            w.writeEndDocument();
            w.flush();
            w.close();

            return prettyPrint(raw.toString());
        } catch (XMLStreamException e) {
            throw new IllegalStateException("BPMN XML生成失败：" + e.getMessage(), e);
        } catch (Exception e) {
            if (e instanceof IllegalArgumentException) {
                throw (IllegalArgumentException) e;
            }
            throw new IllegalStateException("BPMN XML生成失败：" + e.getMessage(), e);
        }
    }

    private void writeMessageFlows(XMLStreamWriter w, ProcessModel model) throws XMLStreamException {
        for (var mf : model.getMessageFlows()) {
            w.writeEmptyElement("bpmn", "messageFlow", BPMN_NS);
            w.writeAttribute("id", mf.getId());
            if (mf.getName() != null) {
                w.writeAttribute("name", mf.getName());
            }
            w.writeAttribute("sourceRef", mf.getSourceId());
            w.writeAttribute("targetRef", mf.getTargetId());
        }
    }

    private void writeNode(XMLStreamWriter w, FlowNode node, ProcessModel model) throws XMLStreamException {
        String elementName = node.getType() == null ? NodeType.USER_TASK.bpmnElementName()
            : node.getType().bpmnElementName();
        w.writeStartElement("bpmn", elementName, BPMN_NS);
        w.writeAttribute("id", node.getId());
        if (node.getName() != null) {
            w.writeAttribute("name", node.getName());
        }
        // BPMN标准：默认流通过网关/活动的default属性引用
        if (node.getType() != null && node.getType().isGateway()) {
            model.getSequenceFlows().stream()
                .filter(f -> node.getId().equals(f.getSourceId()) && f.isDefaultFlow())
                .findFirst()
                .ifPresent(f -> {
                    try {
                        w.writeAttribute("default", f.getId());
                    } catch (XMLStreamException e) {
                        throw new IllegalStateException(e);
                    }
                });
        }
        if (node.getDocumentation() != null && !node.getDocumentation().isBlank()) {
            w.writeStartElement("bpmn", "documentation", BPMN_NS);
            w.writeCharacters(node.getDocumentation());
            w.writeEndElement();
        }
        for (SequenceFlow flow : model.getSequenceFlows()) {
            if (node.getId().equals(flow.getSourceId())) {
                w.writeStartElement("bpmn", "outgoing", BPMN_NS);
                w.writeCharacters(flow.getId());
                w.writeEndElement();
            }
            if (node.getId().equals(flow.getTargetId())) {
                w.writeStartElement("bpmn", "incoming", BPMN_NS);
                w.writeCharacters(flow.getId());
                w.writeEndElement();
            }
        }
        w.writeEndElement();
    }

    private void writeSequenceFlow(XMLStreamWriter w, SequenceFlow flow) throws XMLStreamException {
        w.writeStartElement("bpmn", "sequenceFlow", BPMN_NS);
        w.writeAttribute("id", flow.getId());
        if (flow.getName() != null) {
            w.writeAttribute("name", flow.getName());
        }
        w.writeAttribute("sourceRef", flow.getSourceId());
        w.writeAttribute("targetRef", flow.getTargetId());
        if (flow.getCondition() != null && !flow.getCondition().isBlank()) {
            w.writeStartElement("bpmn", "conditionExpression", BPMN_NS);
            w.writeAttribute(XSI_NS, "type", "bpmn:tFormalExpression");
            w.writeCharacters(flow.getCondition());
            w.writeEndElement();
        }
        w.writeEndElement();
    }

    /**
     * 为Participant（Pool）与Lane生成DI形状。
     * bpmn-js规则：协作图中的flow elements必须是pool/participant的子元素，
     * 缺少Pool形状会导致拖拽编辑报"flow elements must be children of pools/participants"。
     */
    private void writePoolAndLaneShapes(XMLStreamWriter w, ProcessModel model) throws XMLStreamException {
        if (model.getPools().isEmpty() || model.getNodes().isEmpty()) {
            return;
        }

        // 1. 计算节点整体X范围
        double minX = Double.MAX_VALUE;
        double maxX = -Double.MAX_VALUE;
        for (FlowNode node : model.getNodes()) {
            Bounds b = model.getLayout().getNodes().get(node.getId());
            if (b == null) {
                continue;
            }
            minX = Math.min(minX, b.getX());
            maxX = Math.max(maxX, b.right());
        }
        if (minX > maxX) {
            return;
        }

        // 2. 计算Lane纵向band与Pool纵向band
        double labelWidth = 30;
        double padding = 40;
        double[] poolBand = null;
        Map<String, double[]> laneBands = new java.util.LinkedHashMap<>();
        for (Lane lane : model.getLanes()) {
            double[] band = null;
            for (String ref : lane.getNodeRefs()) {
                Bounds b = model.getLayout().getNodes().get(ref);
                if (b == null) {
                    continue;
                }
                band = unionBand(band, b.getY(), b.getY() + b.getHeight());
            }
            if (band != null) {
                band[0] -= padding;
                band[1] += padding;
                laneBands.put(lane.getId(), band);
                poolBand = unionBand(poolBand, band[0], band[1]);
            }
        }
        for (FlowNode node : model.getNodes()) {
            if (node.getLaneId() == null) {
                Bounds b = model.getLayout().getNodes().get(node.getId());
                if (b != null) {
                    poolBand = unionBand(poolBand, b.getY() - padding, b.getY() + b.getHeight() + padding);
                }
            }
        }
        if (poolBand == null) {
            return;
        }

        // 3. 写Lane形状（裁剪到Pool范围内）
        double laneX = minX - labelWidth;
        double laneWidth = (maxX - minX) + labelWidth + padding;
        for (Lane lane : model.getLanes()) {
            double[] band = laneBands.get(lane.getId());
            if (band == null) {
                continue;
            }
            double top = Math.max(band[0], poolBand[0]);
            double bottom = Math.min(band[1], poolBand[1]);
            if (bottom <= top) {
                continue;
            }
            writeHorizontalShape(w, "Shape_" + lane.getId(), lane.getId(),
                laneX, top, laneWidth, bottom - top);
        }

        // 4. 写Pool（Participant）形状
        Pool pool = model.getPools().get(0);
        writeHorizontalShape(w, "Shape_" + pool.getId(), pool.getId(),
            minX - labelWidth - padding, poolBand[0],
            (maxX - minX) + labelWidth + 2 * padding, poolBand[1] - poolBand[0]);
    }

    private double[] unionBand(double[] band, double top, double bottom) {
        if (band == null) {
            return new double[]{top, bottom};
        }
        band[0] = Math.min(band[0], top);
        band[1] = Math.max(band[1], bottom);
        return band;
    }

    private void writeHorizontalShape(XMLStreamWriter w, String shapeId, String elementId,
                                      double x, double y, double width, double height)
        throws XMLStreamException {
        w.writeStartElement("bpmndi", "BPMNShape", BPMNDI_NS);
        w.writeAttribute("id", shapeId);
        w.writeAttribute("bpmnElement", elementId);
        w.writeAttribute("isHorizontal", "true");
        w.writeEmptyElement("dc", "Bounds", DC_NS);
        w.writeAttribute("x", String.valueOf(x));
        w.writeAttribute("y", String.valueOf(y));
        w.writeAttribute("width", String.valueOf(width));
        w.writeAttribute("height", String.valueOf(height));
        w.writeEndElement();
    }

    private void writeDiagram(XMLStreamWriter w, ProcessModel model, String planeElementRef, String modelKey)
        throws XMLStreamException {        w.writeStartElement("bpmndi", "BPMNDiagram", BPMNDI_NS);
        w.writeAttribute("id", "BPMNDiagram_" + modelKey);
        w.writeStartElement("bpmndi", "BPMNPlane", BPMNDI_NS);
        w.writeAttribute("id", "BPMNPlane_" + modelKey);
        w.writeAttribute("bpmnElement", planeElementRef);

        // Pool/Lane必须生成DI形状：bpmn-js要求协作图中的流程元素挂在participant之下
        writePoolAndLaneShapes(w, model);

        for (FlowNode node : model.getNodes()) {
            Bounds b = model.getLayout().getNodes().get(node.getId());
            if (b == null) {
                b = new Bounds(0, 0, 100, 80);
            }
            w.writeStartElement("bpmndi", "BPMNShape", BPMNDI_NS);
            w.writeAttribute("id", "Shape_" + node.getId());
            w.writeAttribute("bpmnElement", node.getId());
            w.writeEmptyElement("dc", "Bounds", DC_NS);
            w.writeAttribute("x", String.valueOf(b.getX()));
            w.writeAttribute("y", String.valueOf(b.getY()));
            w.writeAttribute("width", String.valueOf(b.getWidth()));
            w.writeAttribute("height", String.valueOf(b.getHeight()));
            w.writeEndElement();
        }

        for (SequenceFlow flow : model.getSequenceFlows()) {
            List<Point> waypoints = model.getLayout().getFlows()
                .getOrDefault(flow.getId(), List.of(new Point(0, 0), new Point(0, 0)));
            w.writeStartElement("bpmndi", "BPMNEdge", BPMNDI_NS);
            w.writeAttribute("id", "Edge_" + flow.getId());
            w.writeAttribute("bpmnElement", flow.getId());
            for (Point p : waypoints) {
                w.writeEmptyElement("di", "waypoint", DI_NS);
                w.writeAttribute("x", String.valueOf(p.getX()));
                w.writeAttribute("y", String.valueOf(p.getY()));
            }
            w.writeEndElement();
        }

        for (var mf : model.getMessageFlows()) {
            List<Point> waypoints = model.getLayout().getFlows()
                .getOrDefault(mf.getId(), List.of(new Point(0, 0), new Point(0, 0)));
            w.writeStartElement("bpmndi", "BPMNEdge", BPMNDI_NS);
            w.writeAttribute("id", "Edge_" + mf.getId());
            w.writeAttribute("bpmnElement", mf.getId());
            for (Point p : waypoints) {
                w.writeEmptyElement("di", "waypoint", DI_NS);
                w.writeAttribute("x", String.valueOf(p.getX()));
                w.writeAttribute("y", String.valueOf(p.getY()));
            }
            w.writeEndElement();
        }

        w.writeEndElement(); // BPMNPlane
        w.writeEndElement(); // BPMNDiagram
    }

    private String prettyPrint(String xml) throws Exception {
        TransformerFactory factory = TransformerFactory.newInstance();
        factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
        factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_STYLESHEET, "");
        Transformer transformer = factory.newTransformer();
        transformer.setOutputProperty(OutputKeys.INDENT, "yes");
        transformer.setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "no");
        transformer.setOutputProperty("{http://xml.apache.org/xslt}indent-amount", "2");
        StringWriter out = new StringWriter();
        transformer.transform(new StreamSource(new StringReader(xml)), new StreamResult(out));
        return out.toString();
    }

    private String sanitizeKey(String key) {
        if (key == null || key.isBlank()) {
            return String.valueOf(Math.abs(System.nanoTime()));
        }
        return key.replaceAll("[^a-zA-Z0-9_-]", "_");
    }
}
