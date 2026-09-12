package com.bank.aibpmn.infrastructure.bpmn;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * XML外部实体攻击防护测试（DESIGN.md 24.1）。
 */
class XmlSecurityTest {

    private final StandardBpmn20XmlParser parser = new StandardBpmn20XmlParser();

    @Test
    void rejectsDoctype() {
        String xml = """
            <?xml version="1.0"?>
            <!DOCTYPE definitions [<!ENTITY xxe SYSTEM "file:///etc/passwd">]>
            <definitions xmlns="http://www.omg.org/spec/BPMN/20100524/MODEL"/>
            """;
        assertThatThrownBy(() -> parser.parse(xml))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("DTD");
    }

    @Test
    void rejectsEntityDeclaration() {
        String xml = """
            <?xml version="1.0"?>
            <!DOCTYPE definitions [<!ENTITY evil "boom">]>
            <definitions xmlns="http://www.omg.org/spec/BPMN/20100524/MODEL">&evil;</definitions>
            """;
        assertThatThrownBy(() -> parser.parse(xml))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsXInclude() {
        String xml = """
            <?xml version="1.0"?>
            <definitions xmlns="http://www.omg.org/spec/BPMN/20100524/MODEL"
                         xmlns:xi="http://www.w3.org/2001/XInclude">
              <xi:include href="other.xml"/>
            </definitions>
            """;
        assertThatThrownBy(() -> parser.parse(xml))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("XInclude");
    }

    @Test
    void rejectsBlankContent() {
        assertThatThrownBy(() -> parser.parse("   "))
            .isInstanceOf(IllegalArgumentException.class);
    }
}
