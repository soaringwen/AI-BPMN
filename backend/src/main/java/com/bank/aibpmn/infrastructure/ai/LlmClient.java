package com.bank.aibpmn.infrastructure.ai;

/**
 * 大模型调用抽象。后端不保存ChatMemory，上下文由调用方拼装（DESIGN.md 11.4）。
 */
public interface LlmClient {

    /**
     * @param system 系统提示词
     * @param user   用户提示词（含任务标记、当前模型JSON、对话上下文）
     * @return 模型原始输出（必须是纯JSON）
     */
    String complete(String system, String user);
}
