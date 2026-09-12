package com.bank.aibpmn.agent;

import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 提示词模板注册中心：模板存放于classpath:prompts，支持占位符替换。
 */
@Component
public class PromptTemplateRegistry {

    private final Map<String, String> cache = new ConcurrentHashMap<>();

    public String load(String name) {
        return cache.computeIfAbsent(name, n -> {
            try {
                return new String(new ClassPathResource("prompts/" + n + ".md")
                    .getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            } catch (IOException e) {
                throw new IllegalStateException("提示词模板不存在：" + n, e);
            }
        });
    }

    public String render(String name, Map<String, String> variables) {
        String template = load(name);
        for (Map.Entry<String, String> entry : variables.entrySet()) {
            template = template.replace("{{" + entry.getKey() + "}}", entry.getValue());
        }
        return template;
    }
}
