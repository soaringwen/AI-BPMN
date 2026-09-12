# 系统扩展点说明 — 以扩展 Flowable BPMN 输出为例

> 面向后续开发者的架构说明。回答两个问题：**扩展点在哪里**、**扩展 Flowable 方言的具体做法**。

---

## 1. 现有扩展点盘点

系统在架构上已为平台适配预留了完整的扩展点，分布如下：

### 1.1 方言适配层（核心扩展点，DESIGN.md 第17节）

```text
backend/src/main/java/com/bank/aibpmn/infrastructure/dialect/
├── BpmnDialect.java                  # 方言枚举：STANDARD_BPMN_20 / FLOWABLE / CAMUNDA_7 / CAMUNDA_8
├── BpmnDialectAdapter.java           # 适配器SPI接口
├── BpmnDialectAdapterRegistry.java   # 注册中心：自动收集Spring容器中所有Adapter
├── StandardBpmn20Adapter.java        # 标准方言（恒等转换，已实现）
└── DialectConversionOptions.java     # 转换选项
```

**接入方式：只需实现 `BpmnDialectAdapter` 接口并标注 `@Component`，注册中心自动发现，无需改任何现有代码。**

```java
public interface BpmnDialectAdapter {
    BpmnDialect targetDialect();          // 声明目标方言
    boolean supports(BpmnDialect dialect);
    String convert(ProcessModel processModel, String standardBpmnXml,
                   DialectConversionOptions options);   // 标准XML → 方言XML
    ValidationResult validate(String convertedXml);      // 方言规则校验
}
```

### 1.2 其他已预留的扩展点

| 扩展点 | 位置 | 说明 |
|---|---|---|
| 节点类型扩展 | `domain/model/NodeType.java` | 新增枚举值 + `bpmnElementName()` 映射，生成/解析/校验自动生效 |
| 校验规则扩展 | `domain/validation/ProcessModelValidator.java` | 按 `BPMN-xxx` 编号追加规则；行内规则建议独立成类 |
| 生成器扩展 | `infrastructure/bpmn/BpmnXmlGenerator.java` 接口 | 可替换/装饰标准生成器 |
| 解析器扩展 | `infrastructure/bpmn/BpmnXmlParser.java` 接口 | 厂商扩展命名空间已在解析时识别并告警 |
| AI 提示词扩展 | `resources/prompts/*.md` | 模板占位符化，可按方言加载不同提示词 |
| 模型客户端扩展 | `infrastructure/ai/LlmClient.java` 接口 | 已有 SpringAI / Mock 两个实现，可替换其他协议 |
| API 能力查询 | `GET /api/v1/bpmn/capabilities` | 前端动态感知支持的方言与元素 |

### 1.3 设计红线（扩展时不得违反）

1. **标准优先、方言隔离**：标准 BPMN 生成器中不得出现任何 Flowable/Camunda 判断逻辑（DESIGN.md 3.6）；
2. **不得创建伪实现**：没有实际转换能力就不注册该方言，未实现方言必须返回 `DIALECT_NOT_SUPPORTED`；
3. **AI 不生成平台属性**：大模型永远只产出标准语义，平台专有配置由**确定性映射规则**产生；
4. **ProcessModel 仍是事实源**：方言转换以 ProcessModel + 标准XML 为输入，不另起数据模型。

---

## 2. 扩展 Flowable 输出的实施方案

### 2.1 目标效果

用户一键导出 Flowable 可直接部署的 `.bpmn20.xml` / `.bpmn`，包含：

```xml
<definitions xmlns:flowable="http://flowable.org/bpmn" ...>
  <bpmn:process id="process_x" isExecutable="true">
    <bpmn:userTask id="task_approve" name="审批"
                   flowable:assignee="${manager}"              <!-- 责任人 -->
                   flowable:candidateGroups="deptManager"      <!-- 候选组(角色) -->
                   flowable:dueDate="${dueDate}"
                   flowable:priority="50"/>
    <bpmn:serviceTask id="task_notify" flowable:class="com.bank.NotifyDelegate"/>
    <bpmn:sequenceFlow id="flow_reject" name="拒绝">
      <bpmn:conditionExpression xsi:type="bpmn:tFormalExpression">${approved == false}</bpmn:conditionExpression>
    </bpmn:sequenceFlow>
  </bpmn:process>
</definitions>
```

### 2.2 第一步：实现 FlowableDialectAdapter

新建 `infrastructure/dialect/flowable/FlowableDialectAdapter.java`：

```java
@Component
public class FlowableDialectAdapter implements BpmnDialectAdapter {

    private static final String FLOWABLE_NS = "http://flowable.org/bpmn";

    @Override
    public BpmnDialect targetDialect() {
        return BpmnDialect.FLOWABLE;
    }

    @Override
    public boolean supports(BpmnDialect dialect) {
        return dialect == BpmnDialect.FLOWABLE;
    }

    @Override
    public String convert(ProcessModel model, String standardXml, DialectConversionOptions options) {
        // 推荐实现方式见 2.3
    }

    @Override
    public ValidationResult validate(String convertedXml) {
        // Flowable侧规则：assignee/candidateGroups至少其一、class表达式合法、命名空间正确等
    }
}
```

注册中心无需改动——`BpmnDialectAdapterRegistry` 构造时自动收集所有 `BpmnDialectAdapter` Bean。

### 2.3 转换实现方式（推荐：标准XML + 确定性改写）

**推荐"两步走"，不要重写一个 Flowable 生成器：**

```text
ProcessModel ──(标准生成器)──> 标准BPMN XML ──(FlowableAdapter改写)──> Flowable BPMN
      │                                                          │
      └── 平台属性来源：node.properties / 映射规则 ────────────────┘
```

具体步骤：

1. **声明命名空间**：在 `definitions` 上追加 `xmlns:flowable="http://flowable.org/bpmn"`；
2. **任务属性映射**（遍历 ProcessModel 节点，用安全XML API改写，禁止字符串拼接）：

| ProcessModel 数据 | Flowable 属性 | 映射规则（确定性） |
|---|---|---|
| `node.properties["assignee"]` | `flowable:assignee` | 用户配置或AI对话收集 |
| `lane.name`（角色） | `flowable:candidateGroups` | 角色名→组ID映射表（可配置） |
| `node.type == SERVICE_TASK` + `properties["class"]` | `flowable:class` | 自动任务需用户指定实现类 |
| `properties["dueDate"]` | `flowable:dueDate` | 可选 |
| `condition`（已有） | 保留 `conditionExpression` | Flowable 语法兼容 UEL |
| `isExecutable` | `isExecutable="true"` | Flowable 部署通常要求可执行 |

3. **未映射属性处理**：输出**差异报告**（标准模型与Flowable模型差异），列出缺失的 `class`、`assignee` 等，不静默省略；
4. **边界情况**：Border Event、Timer 等第二阶段元素在标准层支持后，适配器追加 Flowable 对应属性。

XML 改写用 **DOM（DocumentBuilder，安全配置）** 或 StAX 过滤器：读入标准XML → 按 nodeId 定位元素 → `setAttributeNS(FLOWABLE_NS, "flowable:assignee", ...)` → 序列化。

### 2.4 平台属性从哪里来（关键设计决策）

三个来源，按优先级：

1. **AI 对话收集**（推荐先做）：修改提示词 `prompts/modify.md` 增加能力——AI 可在 `ADD_NODE/UPDATE_NODE` 的 payload 中输出 `properties: { "assignee": "${manager}" }`（白名单校验属性键），落库到 ProcessModel。AI 仍不生成XML，只填语义属性；
2. **属性面板配置**（第二优先）：前端集成 bpmn-js properties-panel（已有 `flowable-moddle` 社区描述文件），用户在画布上直接填写 `assignee/candidateGroups`，保存进 `node.properties`；
3. **全局映射规则**：如"Lane名称 → candidateGroups"的映射表，配置在 `application.yml`（如 `app.dialect.flowable.group-mapping: {审批人: deptManager, 财务: finance}`）。

### 2.5 API 与前端配合

**后端**（改动很小）：

```java
// BpmnController /convert 增加方言参数
@PostMapping("/convert")
public XmlResponse convert(@RequestBody ConvertRequest request) {   // {processModel, dialect}
    BpmnDialect dialect = parse(request.dialect());                 // 默认 STANDARD_BPMN_20
    BpmnDialectAdapter adapter = dialectRegistry.find(dialect)
        .orElseThrow(() -> new ApiError("DIALECT_NOT_SUPPORTED", "当前版本未实现该方言"));
    String standard = xmlGenerator.generate(request.processModel());
    String converted = adapter.convert(request.processModel(), standard, options);
    return new XmlResponse(converted, adapter.validate(converted));
}

// capabilities 已返回注册的方言列表，前端动态感知
```

> 注：`/convert` 端点骨架已存在（当前只注册标准方言），只需把硬编码的默认方言改为请求参数。

**前端**：

1. 导出菜单增加方言选择："导出BPMN（标准）/ 导出BPMN（Flowable）"；
2. 导出前调用 `/api/v1/bpmn/capabilities` 判断可用方言；
3. 转换响应中的**差异报告**（缺失 assignee 等）用现有 `ChangePreview` 风格的对话框展示，用户可补填后重新转换。

### 2.6 校验差异

`FlowableDialectAdapter.validate` 在标准规则之上追加：

- `isExecutable="true"`（Flowable 部署要求）；
- UserTask 必须有 `assignee` 或 `candidateGroups` 之一（可配置为警告）；
- ServiceTask 必须有 `flowable:class` / `expression` / `delegateExpression` 之一；
- 条件表达式必须兼容 UEL（`${...}`）；
- 输出不得包含其他厂商命名空间（camunda/zeebe）。

### 2.7 测试要点

| 测试 | 内容 |
|---|---|
| 单元：属性映射 | Lane→candidateGroups、properties→flowable:* 逐一断言 |
| 单元：命名空间 | 输出含 `xmlns:flowable` 且无 camunda/zeebe |
| 单元：差分保护 | 标准XML输入不被修改（转换不改入参） |
| 回归：标准导出 | 标准方言输出与改造前完全一致（防止适配污染标准层） |
| 集成：Flowable 部署 | （可选）Flowable Engine Test：转换产物可被 Flowable RepositoryService 部署 |

---

## 3. Camunda 7 / Camunda 8 的后续路径

与 Flowable 完全同构，只需再各实现一个 Adapter：

| 方言 | 命名空间 | 典型属性差异 |
|---|---|---|
| `CAMUNDA_7` | `http://camunda.org/schema/1.0/bpmn` | `camunda:assignee`、`camunda:class`、`camunda:formKey` |
| `CAMUNDA_8` | `http://camunda.org/schema/zeebe/1.0` | `zeebe:taskDefinition`（job type）、`zeebe:assignee`，且**无** ServiceTask class 概念，需映射为 job worker |

Camunda 8 因执行模型不同（无共享引擎、job worker 模式），适配器内需要更重的语义映射（如 UserTask → UserTask + zeebe:formId），这正是"每个方言独立 Adapter"设计的原因。

---

## 4. 工作量评估

| 阶段 | 内容 | 预估 |
|---|---|---|
| P1 | FlowableDialectAdapter（命名空间+assignee/candidateGroups/isExecutable+校验） | 2~3人日 |
| P1 | `/convert` 方言参数化 + 前端导出方言选择 + 差异报告对话框 | 1~2人日 |
| P2 | AI 对话收集平台属性（prompt + properties 白名单） | 1~2人日 |
| P2 | 属性面板（bpmn-js properties-panel + flowable moddle 描述） | 2~3人日 |
| P3 | Camunda 7 / Camunda 8 适配器 | 各2~3人日 |

**P1 完成后即可导出可在 Flowable 中直接部署的基础流程图**；执行级属性（class、监听器）建议走属性面板人工配置，避免 AI 幻觉产生错误的执行绑定。

---

## 5. 一句话总结

> 扩展点就是 `BpmnDialectAdapter` SPI：实现一个 `FlowableDialectAdapter`（标准XML → Flowable 改写 + 差异报告），注册中心自动发现，`/convert` API 参数化方言，前端导出加选项——标准生成器一行不改，AI 依旧只产出标准语义。
