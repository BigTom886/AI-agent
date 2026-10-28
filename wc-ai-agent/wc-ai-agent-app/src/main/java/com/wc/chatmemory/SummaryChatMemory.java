package com.wc.chatmemory;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.memory.ChatMemoryRepository;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.Prompt;

/**
 * 带摘要压缩的对话记忆（滚动摘要）
 *
 * Spring AI 官方只提供滑动窗口一种短期记忆实现（MessageWindowChatMemory）：
 * 超出窗口的消息会被直接丢弃，而存储又是覆盖写，等到发现时旧消息已经没了，
 * 没有任何机会做摘要。
 *
 * 这个实现把「即将被丢弃的那部分」先交给大模型压成一段摘要，
 * 再把「摘要 + 最近几条原文」一起存回去，等价于滚动摘要：
 *   [老消息…老消息, 摘要] → 压缩 → [新摘要, 最近N条原文]
 * 下一轮压缩时，新摘要会和再旧的消息一起被合并，所以信息不会断片。
 *
 * 存储仍然复用框架的 ChatMemoryRepository（JDBC 版本），本类只负责「怎么算」——
 * 这正是 Spring AI 把 ChatMemory 做成接口、而不是具体类的原因。
 */
public class SummaryChatMemory implements ChatMemory
{
    private static final Logger log = LoggerFactory.getLogger(SummaryChatMemory.class);

    /** 摘要前缀：用 AssistantMessage 而不是 SystemMessage，否则会占掉「第一个系统消息」的位置，把人设提示词挤到后面 */
    private static final String SUMMARY_PREFIX = "【历史对话摘要】\n";

    /** 摘要提示词：要求模型只保留事实，不要编造 */
    private static final String SUMMARY_PROMPT = """
            请把下面的对话历史压缩成一段简明摘要，必须保留：
            1. 用户提到的关键信息（姓名、偏好、需求、约束条件）
            2. 已经达成的结论或约定
            3. 尚未解决的问题
            不要编造信息，用中文，不超过 200 字。
            """;

    /** 存储：框架的 JDBC 仓库（对话记忆的唯一持久化位置） */
    private final ChatMemoryRepository repository;

    /** 调大模型生成摘要 */
    private final ChatModel chatModel;

    /** 超过多少条消息触发一次压缩 */
    private final int threshold;

    /** 压缩后保留最近多少条原文 */
    private final int keep;

    public SummaryChatMemory(ChatMemoryRepository repository, ChatModel chatModel, int threshold, int keep)
    {
        this.repository = repository;
        this.chatModel = chatModel;
        this.threshold = threshold;
        this.keep = keep;
    }

    @Override
    public void add(String conversationId, List<Message> messages)
    {
        // 取回当前存着的全部消息（就是上一轮的窗口）
        List<Message> all = new ArrayList<>(repository.findByConversationId(conversationId));
        all.addAll(messages);

        // 没超阈值：原样保存
        if (all.size() <= threshold)
        {
            repository.saveAll(conversationId, all);
            return;
        }

        // 超阈值：头部是要被丢掉的部分（交给模型压缩），尾部是保留的原文
        int split = all.size() - keep;
        List<Message> toSummarize = all.subList(0, split);
        List<Message> recent = all.subList(split, all.size());

        String summary = summarize(toSummarize);

        List<Message> compacted = new ArrayList<>();
        if (summary != null)
        {
            // 上一轮的摘要也在 toSummarize 里 → 模型会把它和新旧消息合并成新摘要
            compacted.add(new AssistantMessage(SUMMARY_PREFIX + summary));
        }
        // summary 为 null 说明压缩失败：退化成「只保留最近几条原文」，不摘要但也不丢整段对话
        compacted.addAll(recent);
        repository.saveAll(conversationId, compacted);
    }

    @Override
    public List<Message> get(String conversationId)
    {
        return repository.findByConversationId(conversationId);
    }

    @Override
    public void clear(String conversationId)
    {
        repository.deleteByConversationId(conversationId);
    }

    /**
     * 调一次大模型把老消息压成摘要
     *
     * @param messages 需要压缩的消息（含上一轮的摘要）
     * @return 摘要文本；失败时返回 null，由调用方退化成「只截断不摘要」
     */
    private String summarize(List<Message> messages)
    {
        try
        {
            String transcript = messages.stream()
                    .map(m -> m.getMessageType() + ": " + (m.getText() == null ? "" : m.getText()))
                    .collect(Collectors.joining("\n"));
            return chatModel.call(new Prompt(
                    new SystemMessage(SUMMARY_PROMPT),
                    new UserMessage(transcript)))
                    .getResult().getOutput().getText();
        }
        catch (Exception e)
        {
            // 摘要失败不能拖垮整个对话：记日志，退化成纯截断
            log.warn("对话摘要生成失败，本次仅截断不压缩: {}", e.getMessage());
            return null;
        }
    }
}
