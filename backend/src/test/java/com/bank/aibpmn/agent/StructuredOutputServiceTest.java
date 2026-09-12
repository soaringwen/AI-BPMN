package com.bank.aibpmn.agent;

import com.bank.aibpmn.domain.model.NodeType;
import com.bank.aibpmn.domain.operation.OperationType;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 模型输出处理契约测试（DESIGN.md 24.2）。
 */
class StructuredOutputServiceTest {

    private final StructuredOutputService service = new StructuredOutputService();

    @Test
    void parsesPlainJson() {
        var node = service.parse("{\"status\":\"COMPLETED\"}");
        assertThat(node.path("status").asText()).isEqualTo("COMPLETED");
    }

    @Test
    void stripsMarkdownFences() {
        var node = service.parse("```json\n{\"ok\":true}\n```");
        assertThat(node.path("ok").asBoolean()).isTrue();
    }

    @Test
    void stripsSurroundingText() {
        var node = service.parse("以下是结果：\n{\"ok\":1}\n完毕。");
        assertThat(node.path("ok").asInt()).isEqualTo(1);
    }

    @Test
    void rejectsNonJson() {
        assertThatThrownBy(() -> service.parse("抱歉，我无法完成"))
            .isInstanceOf(ModelOutputInvalidException.class);
    }
}
