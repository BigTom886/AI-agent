package com.wc.chatmemory;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.memory.ChatMemoryRepository;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.MessageType;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import com.wc.mapper.ChatMessageMapper;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * 对话记忆测试
 *
 * 存储用框架自带的 JdbcChatMemoryRepository（表 SPRING_AI_CHAT_MEMORY，
 * PostgreSQL 中实际存储为小写表名），算法用项目自己实现的 SummaryChatMemory。
 *
 * 需要本地 PostgreSQL 处于运行状态：启动时 Spring AI 会自动执行内置脚本，
 * 建出 SPRING_AI_CHAT_MEMORY 表。
 *
 * ChatModel 被替换成 mock：SummaryChatMemory 在消息数超阈值时会调大模型生成摘要，
 * 这里不打真实网络（API Key 也可能是失效的），直接返回一句固定的摘要。
 */
@SpringBootTest
public class ChatMemoryTest
{
    @Autowired
    private ChatMemory chatMemory;

    @Autowired
    private ChatMemoryRepository repository;

    /** 查框架建的那张表（MyBatis Mapper） */
    @Autowired
    private ChatMessageMapper chatMessageMapper;

    /** 摘要用的假模型：不 mock 的话压缩时会去连真实的大模型 API */
    @MockitoBean
    private ChatModel chatModel;

    @BeforeEach
    void stubSummaryModel()
    {
        when(chatModel.call(any(Prompt.class))).thenReturn(ChatResponse.builder()
                .generations(List.of(new Generation(new AssistantMessage("摘要"))))
                .build());
    }

    private int countRows(String conversationId)
    {
        return chatMessageMapper.countByConversationId(conversationId);
    }

    @Test
    void memoryKeepsConversationAcrossCalls()
    {
        String conversationId = UUID.randomUUID().toString();
        try
        {
            // 第一轮：用户提问
            chatMemory.add(conversationId, List.of(new UserMessage("你好")));
            // 第二轮：模型回答（框架会一起存进去）
            chatMemory.add(conversationId, List.of(new AssistantMessage("你好，有什么可以帮你的？")));

            // 再取一次，应该能拿到这两条（窗口内）
            List<Message> messages = chatMemory.get(conversationId);
            Assertions.assertEquals(2, messages.size());
            Assertions.assertEquals("你好", messages.get(0).getText());
            Assertions.assertEquals("你好，有什么可以帮你的？", messages.get(1).getText());
        }
        finally
        {
            chatMemory.clear(conversationId);
        }
    }

    @Test
    void oldMessagesAreCompressedIntoSummary()
    {
        String conversationId = UUID.randomUUID().toString();
        try
        {
            // 阈值是 20 条（love.memory.summary-threshold），一条一条加，
            // 第 21 条进来时触发压缩：老的 15 条被压成 1 条摘要，保留最近 6 条原文
            for (int i = 0; i < 21; i++)
            {
                chatMemory.add(conversationId, List.of(new UserMessage("消息" + i)));
            }

            List<Message> messages = chatMemory.get(conversationId);
            // 1 条摘要 + 6 条原文
            Assertions.assertEquals(7, messages.size());
            Assertions.assertEquals(MessageType.ASSISTANT, messages.get(0).getMessageType(),
                    "摘要必须是 AssistantMessage：MessageChatMemoryAdvisor 只会把第一个 SystemMessage 提到最前，"
                            + "用 SystemMessage 会把人设提示词挤到后面");
            Assertions.assertTrue(messages.get(0).getText().contains("摘要"));
            // 最近 6 条原文原样保留
            Assertions.assertEquals("消息15", messages.get(1).getText());
            Assertions.assertEquals("消息20", messages.get(messages.size() - 1).getText());

            // saveAll 是整份覆盖语义：库里也只有「摘要 + 最近 6 条」，不会累积 21 条
            Assertions.assertEquals(7, countRows(conversationId));
        }
        finally
        {
            chatMemory.clear(conversationId);
        }
    }

    @Test
    void memoryNeverExceedsThreshold()
    {
        String conversationId = UUID.randomUUID().toString();
        try
        {
            for (int i = 0; i < 30; i++)
            {
                chatMemory.add(conversationId, List.of(new UserMessage("消息" + i)));
                Assertions.assertTrue(chatMemory.get(conversationId).size() <= 20,
                        "记忆条数不应超过阈值 20");
            }
            // 最新的消息一定在最后
            Assertions.assertEquals("消息29",
                    chatMemory.get(conversationId).get(chatMemory.get(conversationId).size() - 1).getText());
        }
        finally
        {
            chatMemory.clear(conversationId);
        }
    }

    @Test
    void messagesLandInFrameworkTable()
    {
        String conversationId = UUID.randomUUID().toString();
        try
        {
            chatMemory.add(conversationId, List.of(new UserMessage("落库校验")));

            Assertions.assertEquals(1, countRows(conversationId));
            // 角色存的是 MessageType 枚举名，不是我们自定义的 role 列
            Assertions.assertEquals("USER", chatMessageMapper.findFirstType(conversationId));
        }
        finally
        {
            chatMemory.clear(conversationId);
        }
    }

    @Test
    void historyQueryMapsRowsToRecords()
    {
        String conversationId = UUID.randomUUID().toString();
        try
        {
            chatMemory.add(conversationId, List.of(new UserMessage("第一条")));
            chatMemory.add(conversationId, List.of(new AssistantMessage("第二条")));

            // 历史记录接口走的是 Mapper（框架的 Repository 不返回入库时间）
            List<ChatMessageRecord> records = chatMessageMapper.findByConversationId(conversationId);
            Assertions.assertEquals(2, records.size());
            Assertions.assertEquals("USER", records.get(0).role());
            Assertions.assertEquals("第一条", records.get(0).content());
            Assertions.assertNotNull(records.get(0).createdAt(), "入库时间必须能映射出来");
            Assertions.assertEquals("ASSISTANT", records.get(1).role());
            Assertions.assertEquals("第二条", records.get(1).content());
        }
        finally
        {
            chatMemory.clear(conversationId);
        }
    }

    @Test
    void findConversationIdsReturnsSessions()
    {
        String conversationId = UUID.randomUUID().toString();
        try
        {
            chatMemory.add(conversationId, List.of(new UserMessage("会话列表校验")));
            Assertions.assertTrue(repository.findConversationIds().contains(conversationId));
        }
        finally
        {
            chatMemory.clear(conversationId);
        }
    }

    @Test
    void clearRemovesAllMessages()
    {
        String conversationId = UUID.randomUUID().toString();
        chatMemory.add(conversationId, List.of(new UserMessage("待删除的消息")));
        Assertions.assertEquals(1, countRows(conversationId));

        chatMemory.clear(conversationId);
        Assertions.assertEquals(0, countRows(conversationId));
        Assertions.assertTrue(repository.findByConversationId(conversationId).isEmpty());
    }
}
