package com.bank.aibpmn.controller;

import com.bank.aibpmn.application.dto.BpmnXmlDtos.GenerateXmlRequest;
import com.bank.aibpmn.application.dto.BpmnXmlDtos.LayoutRequest;
import com.bank.aibpmn.application.dto.BpmnXmlDtos.ParseXmlRequest;
import com.bank.aibpmn.application.dto.BpmnXmlDtos.ParseXmlResponse;
import com.bank.aibpmn.application.dto.BpmnXmlDtos.ValidateModelRequest;
import com.bank.aibpmn.application.dto.BpmnXmlDtos.ValidateModelResponse;
import com.bank.aibpmn.application.dto.BpmnXmlDtos.XmlResponse;
import com.bank.aibpmn.config.AppProperties;
import com.bank.aibpmn.domain.layout.LayoutService;
import com.bank.aibpmn.domain.model.ProcessModel;
import com.bank.aibpmn.domain.validation.ProcessModelValidator;
import com.bank.aibpmn.domain.validation.ValidationResult;
import com.bank.aibpmn.infrastructure.bpmn.BpmnXmlGenerator;
import com.bank.aibpmn.infrastructure.bpmn.BpmnXmlParser;
import com.bank.aibpmn.infrastructure.dialect.BpmnDialect;
import com.bank.aibpmn.infrastructure.dialect.BpmnDialectAdapter;
import com.bank.aibpmn.infrastructure.dialect.BpmnDialectAdapterRegistry;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * BPMN工具API（DESIGN.md 15.2）。不提供任何服务端保存能力。
 */
@RestController
@RequestMapping("/api/v1/bpmn")
@Tag(name = "BPMN", description = "BPMN XML生成/解析/校验/布局API")
public class BpmnController {

    private static final long MAX_FILE_SIZE = 10L * 1024 * 1024;

    private final BpmnXmlGenerator xmlGenerator;
    private final BpmnXmlParser xmlParser;
    private final ProcessModelValidator validator;
    private final LayoutService layoutService;
    private final BpmnDialectAdapterRegistry dialectRegistry;
    private final AppProperties properties;

    public BpmnController(BpmnXmlGenerator xmlGenerator,
                          BpmnXmlParser xmlParser,
                          ProcessModelValidator validator,
                          LayoutService layoutService,
                          BpmnDialectAdapterRegistry dialectRegistry,
                          AppProperties properties) {
        this.xmlGenerator = xmlGenerator;
        this.xmlParser = xmlParser;
        this.validator = validator;
        this.layoutService = layoutService;
        this.dialectRegistry = dialectRegistry;
        this.properties = properties;
    }

    @PostMapping("/xml/generate")
    @Operation(summary = "ProcessModel生成标准XML")
    public XmlResponse generateXml(@RequestBody GenerateXmlRequest request) {
        String xml = xmlGenerator.generate(request.processModel());
        return new XmlResponse(xml, validator.validate(request.processModel()));
    }

    @PostMapping("/xml/parse")
    @Operation(summary = "标准XML解析为ProcessModel")
    public ParseXmlResponse parseXml(@RequestBody ParseXmlRequest request) {
        ProcessModel model = xmlParser.parse(request.xml());
        ValidationResult validation = validator.validate(model);
        return new ParseXmlResponse(model, validation);
    }

    @PostMapping(value = "/xml/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "上传BPMN/XML文件并解析（仅.bpmn/.xml，最大10MB）")
    public ParseXmlResponse uploadXml(@RequestPart("file") MultipartFile file) throws IOException {
        String name = file.getOriginalFilename() == null ? "" : file.getOriginalFilename().toLowerCase();
        if (!name.endsWith(".bpmn") && !name.endsWith(".xml")) {
            throw new IllegalArgumentException("仅允许上传.bpmn或.xml文件");
        }
        if (file.getSize() > Math.min(MAX_FILE_SIZE, properties.getBpmn().getMaxFileSizeBytes())) {
            throw new IllegalArgumentException("文件大小超过限制（10MB）");
        }
        String content = new String(file.getBytes(), StandardCharsets.UTF_8);
        ProcessModel model = xmlParser.parse(content);
        return new ParseXmlResponse(model, validator.validate(model));
    }

    @PostMapping("/validate/model")
    @Operation(summary = "校验ProcessModel")
    public ValidateModelResponse validateModel(@RequestBody ValidateModelRequest request) {
        return new ValidateModelResponse(validator.validate(request.processModel()));
    }

    @PostMapping("/validate/xml")
    @Operation(summary = "校验BPMN XML（安全检查+解析校验）")
    public ValidateModelResponse validateXml(@RequestBody ParseXmlRequest request) {
        ProcessModel model = xmlParser.parse(request.xml());
        return new ValidateModelResponse(validator.validate(model));
    }

    @PostMapping("/layout")
    @Operation(summary = "执行全图自动布局")
    public XmlResponse layout(@RequestBody LayoutRequest request) {
        ProcessModel model = request.processModel();
        if (model == null) {
            throw new IllegalArgumentException("流程模型为空");
        }
        layoutService.fullLayout(model);
        String xml = xmlGenerator.generate(model);
        return new XmlResponse(xml, validator.validate(model));
    }

    @PostMapping("/convert")
    @Operation(summary = "按方言转换（未实现的方言返回DIALECT_NOT_SUPPORTED）")
    public XmlResponse convert(@RequestBody GenerateXmlRequest request) {
        BpmnDialect dialect = BpmnDialect.valueOf(properties.getBpmn().getDefaultDialect());
        BpmnDialectAdapter adapter = dialectRegistry.find(dialect)
            .orElseThrow(() -> new UnsupportedOperationException("DIALECT_NOT_SUPPORTED"));
        String standard = xmlGenerator.generate(request.processModel());
        String converted = adapter.convert(request.processModel(), standard,
            com.bank.aibpmn.infrastructure.dialect.DialectConversionOptions.empty());
        return new XmlResponse(converted, adapter.validate(converted));
    }

    @GetMapping("/capabilities")
    @Operation(summary = "查询支持的元素和方言")
    public Map<String, Object> capabilities() {
        Map<String, Object> capabilities = new HashMap<>();
        capabilities.put("nodeTypes", com.bank.aibpmn.domain.model.NodeType.values());
        capabilities.put("dialects", List.of(BpmnDialect.STANDARD_BPMN_20));
        capabilities.put("maxNodes", 100);
        capabilities.put("targetNamespace", properties.getBpmn().getTargetNamespace());
        return capabilities;
    }
}
