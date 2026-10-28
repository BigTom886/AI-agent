package com.wc.chatmemory;

import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.memory.ChatMemoryRepository;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 对话记忆相关 Bean 配置
 *
 * Spring AI 自动装配本来会提供一个 ChatMemory（MessageWindowChatMemory，滑动窗口 20 条）。
 * 我们自己定义一个同名类型的 Bean，自动装配就会退位——因为
 * ChatMemoryAutoConfiguration#chatMemory 上带着 @ConditionalOnMissingBean。
 *
 * 换成 SummaryChatMemory 后：
 *   - 平时：滑动窗口，超过 threshold 条时把老消息压成摘要
 *   - 存储：仍然用框架的 ChatMemoryRepository（JDBC → SPRING_AI_CHAT_MEMORY 表）
 */
@Configuration
public class ChatMemoryConfig
{
    /**
     * 带摘要压缩的对话记忆
     *
     * @param repository 框架的 JDBC 仓库（自动装配提供）
     * @param chatModel 用来生成摘要的大模型
     * @param threshold 超过多少条消息触发一次压缩（20 条 ≈ 10 轮对话）
     * @param keep 压缩后保留最近多少条原文（6 条 ≈ 3 轮）
     */
    @Bean
    public ChatMemory chatMemory(ChatMemoryRepository repository,
            ChatModel chatModel,
            @Value("${love.memory.summary-threshold:20}") int threshold,
            @Value("${love.memory.summary-keep:6}") int keep)
    {
        return new SummaryChatMemory(repository, chatModel, threshold, keep);
    }
}
