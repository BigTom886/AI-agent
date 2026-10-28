package com.wc.chatmemory;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.AssistantMessage;
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

import reactor.core.publisher.Flux;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 会话归属的端到端测试
 *
 * 验证三件事：
 *   1. 自己的会话，自己能读能删
 *   2. 别人的会话，读和删都不行（403）
 *   3. 别人的会话，也不能拿来接着聊（403）
 *
 * @Transactional 让整个测试在事务里执行，结束后回滚，不往开发库留测试数据。
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
public class ChatOwnershipApiTest
{
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
        ChatResponse reply = ChatResponse.builder()
                .generations(List.of(new Generation(new AssistantMessage("好的"))))
                .build();
        when(chatModel.call(any(Prompt.class))).thenReturn(reply);
        when(chatModel.stream(any(Prompt.class))).thenReturn(Flux.just(reply));

        sessionA = sessionOf(newUser());
        sessionB = sessionOf(newUser());
        chatId = UUID.randomUUID().toString();
    }

    private AppUser newUser()
    {
        String username = "own_" + UUID.randomUUID().toString().replace("-", "").substring(0, 10);
        return userService.register(username, "secret123", "归属测试");
    }

    /** 造一个「已登录」的会话 */
    private MockHttpSession sessionOf(AppUser user)
    {
        MockHttpSession session = new MockHttpSession();
        session.setAttribute(UserIdentity.SESSION_USER_ID, user.getId());
        return session;
    }

    @Test
    void ownerCanReadAndDeleteOwnConversation() throws Exception
    {
        mockMvc.perform(get("/ai/love_app/chat/sync")
                        .param("message", "你好")
                        .param("chatId", chatId)
                        .session(sessionA))
                .andExpect(status().isOk());

        mockMvc.perform(get("/ai/love_app/history")
                        .param("chatId", chatId)
                        .session(sessionA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].content").value("你好"));

        mockMvc.perform(delete("/ai/love_app/conversation")
                        .param("chatId", chatId)
                        .session(sessionA))
                .andExpect(status().isOk());

        // 删完就空了
        mockMvc.perform(get("/ai/love_app/history")
                        .param("chatId", chatId)
                        .session(sessionA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isEmpty());
    }

    @Test
    void otherUserCannotDeleteConversation() throws Exception
    {
        mockMvc.perform(get("/ai/love_app/chat/sync")
                        .param("message", "你好")
                        .param("chatId", chatId)
                        .session(sessionA))
                .andExpect(status().isOk());

        mockMvc.perform(delete("/ai/love_app/conversation")
                        .param("chatId", chatId)
                        .session(sessionB))
                .andExpect(status().isForbidden());

        // 会话还在主人手里
        mockMvc.perform(get("/ai/love_app/history")
                        .param("chatId", chatId)
                        .session(sessionA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].content").value("你好"));
    }

    @Test
    void otherUserCannotReadHistory() throws Exception
    {
        mockMvc.perform(get("/ai/love_app/chat/sync")
                        .param("message", "你好")
                        .param("chatId", chatId)
                        .session(sessionA))
                .andExpect(status().isOk());

        mockMvc.perform(get("/ai/love_app/history")
                        .param("chatId", chatId)
                        .session(sessionB))
                .andExpect(status().isForbidden());
    }

    @Test
    void otherUserCannotJoinExistingConversation() throws Exception
    {
        mockMvc.perform(get("/ai/love_app/chat/sync")
                        .param("message", "你好")
                        .param("chatId", chatId)
                        .session(sessionA))
                .andExpect(status().isOk());

        mockMvc.perform(get("/ai/love_app/chat/sync")
                        .param("message", "我来插一脚")
                        .param("chatId", chatId)
                        .session(sessionB))
                .andExpect(status().isForbidden());
    }

    @Test
    void anonymousCannotDeleteConversation() throws Exception
    {
        mockMvc.perform(delete("/ai/love_app/conversation").param("chatId", chatId))
                .andExpect(status().isUnauthorized());
    }
}
