package com.bank.aibpmn.infrastructure.ai;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * 基于Spring AI的行内模型网关客户端（OpenAI兼容协议）。
 * 模型地址、API Key、模型名称均通过环境变量注入，不得硬编码（DESIGN.md 11.3 / 19）。
 */
@Component
@ConditionalOnProperty(prefix = "app.ai", name = "mock-enabled", havingValue = "false", matchIfMissing = true)
public class SpringAiLlmClient implements LlmClient {

    private final ChatClient chatClient;

    public SpringAiLlmClient(ChatModel chatModel) {
        this.chatClient = ChatClient.builder(chatModel)
            .defaultSystem("""
                你是业务流程建模助手。
                你的输出必须符合调用方提供的JSON Schema。
                不得输出Markdown。
                不得生成BPMN XML。
                不得生成平台特定扩展属性。
                不得执行任何修改。
                信息不足时必须返回澄清问题。
                """)
            .build();
    }

    @Override
    public String complete(String system, String user) {
        return chatClient.prompt()
            .system(system)
            .user(user)
            .call()
            .content();
    }
}
