package com.wc.app;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import com.wc.user.AppUser;
import com.wc.user.UserIdentity;
import com.wc.user.UserService;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 恋爱报告的端到端测试
 *
 * 验证三件事：
 *   1. 本人请求能拿到报告（标题 + 建议）
 *   2. 未登录 401、别人的会话 403
 *   3. 生成报告不会把内容写进对话记忆（报告是一次性产物，不是新一轮对话）
 *
 * @Transactional 让整个测试在事务里执行，结束后回滚，不往开发库留测试数据。
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
public class LoveReportApiTest
{
    /** 假模型返回的报告文本（首行标题，其余每行一条建议） */
    private static final String REPORT_TEXT = "小明的恋爱报告\n多沟通，别猜\n保留自己的节奏";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserService userService;

    /** 假的模型：不 mock 的话会连真实的大模型 API */
    @MockitoBean
    private ChatModel chatModel;

    private MockHttpSession sessionA;
    private MockHttpSession sessionB;
    private String chatId;

    @BeforeEach
    void setUp()
    {
        // 按系统提示词区分「聊天」和「生成报告」两条链路，返回不同的内容
        when(chatModel.call(any(Prompt.class))).thenAnswer(invocation -> {
            Prompt prompt = invocation.getArgument(0);
            String system = prompt.getInstructions().stream()
                    .filter(m -> m instanceof SystemMessage)
                    .map(Message::getText)
                    .collect(Collectors.joining("\n"));
            if (system.contains("恋爱分析报告")) {
                return textResponse(REPORT_TEXT);
            }
            return textResponse("好的");
        });

        sessionA = sessionOf(newUser());
        sessionB = sessionOf(newUser());
        chatId = UUID.randomUUID().toString();
    }

    private AppUser newUser()
    {
        String username = "rep_" + UUID.randomUUID().toString().replace("-", "").substring(0, 10);
        return userService.register(username, "secret123", "报告测试");
    }

    /** 造一个「已登录」的会话 */
    private MockHttpSession sessionOf(AppUser user)
    {
        MockHttpSession session = new MockHttpSession();
        session.setAttribute(UserIdentity.SESSION_USER_ID, user.getId());
        return session;
    }

    @Test
    void ownerCanGenerateReport() throws Exception
    {
        mockMvc.perform(get("/ai/love_app/report").param("chatId", chatId).session(sessionA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("小明的恋爱报告"))
                .andExpect(jsonPath("$.suggestions.length()").value(2))
                .andExpect(jsonPath("$.suggestions[0]").value("多沟通，别猜"));
    }

    @Test
    void reportRequiresLogin() throws Exception
    {
        mockMvc.perform(get("/ai/love_app/report").param("chatId", chatId))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void otherUserCannotGenerateReport() throws Exception
    {
        // 先让 A 聊一句，把 chatId 绑定到 A 名下
        mockMvc.perform(get("/ai/love_app/chat/sync")
                        .param("message", "你好")
                        .param("chatId", chatId)
                        .session(sessionA))
                .andExpect(status().isOk());

        mockMvc.perform(get("/ai/love_app/report").param("chatId", chatId).session(sessionB))
                .andExpect(status().isForbidden());
    }

    @Test
    void reportDoesNotPolluteConversationHistory() throws Exception
    {
        mockMvc.perform(get("/ai/love_app/chat/sync")
                        .param("message", "你好")
                        .param("chatId", chatId)
                        .session(sessionA))
                .andExpect(status().isOk());

        // 聊完一轮：历史里是 2 条（用户 1 条 + AI 1 条）
        mockMvc.perform(get("/ai/love_app/history").param("chatId", chatId).session(sessionA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2));

        mockMvc.perform(get("/ai/love_app/report").param("chatId", chatId).session(sessionA))
                .andExpect(status().isOk());

        // 生成报告不写记忆：还是 2 条，内容里也不该出现报告正文
        mockMvc.perform(get("/ai/love_app/history").param("chatId", chatId).session(sessionA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].content").value("你好"))
                .andExpect(jsonPath("$[1].content").value("好的"));
    }

    /** 构造一个普通文本回复 */
    private ChatResponse textResponse(String text)
    {
        return ChatResponse.builder()
                .generations(List.of(new Generation(new AssistantMessage(text))))
                .build();
    }
}
