package com.wc.userprofile;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.model.tool.ToolCallingChatOptions;
import org.springframework.ai.model.tool.ToolCallingManager;
import org.springframework.ai.model.tool.ToolExecutionResult;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import com.wc.user.UserIdentity;

import reactor.core.publisher.Flux;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 用户画像的端到端测试（HTTP 层 → 业务 → 数据库）
 *
 * 现在所有对话/画像接口都要求登录，所以每个请求都带一个 MockHttpSession，
 * 身份是测试里选定的数字用户 ID（不要求 app_user 里真的有这条记录，
 * 因为对话链路只依赖 session 里的 ID）。
 *
 * chatId 用来隔离「对话记忆」，userId（u_<id>）用来隔离「跨会话画像」。
 * ChatModel 被替换成 mock，所以不打真实网络。
 * MockMvc 不带 context-path（那是 Servlet 容器的事），所以路径是 /ai/** 而不是 /api/ai/**。
 */
@SpringBootTest
@AutoConfigureMockMvc
public class UserProfileApiTest
{
    /** 画像里写入的特征值，用它来断言「画像真的到模型那儿了」 */
    private static final String NICK = "E2E_NICK_E2E";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserProfileService userProfileService;

    @Autowired
    private ChatMemory chatMemory;

    @Autowired
    private com.wc.chatmemory.ChatConversationService chatConversationService;

    /** 假的模型：不 mock 的话会连真实的大模型 API */
    @MockitoBean
    private ChatModel chatModel;

    /** 本次测试的登录态：session 里放数字用户 ID */
    private MockHttpSession session;

    /** 画像键：u_<数字 ID> */
    private String userId;

    private String chatId;

    @BeforeEach
    void setUp()
    {
        // 每个测试用不同的数字 ID，避免画像互相串
        long numericUserId = ThreadLocalRandom.current().nextLong(1_000_000_000L, 9_999_999_999L);
        userId = UserIdentity.profileUserId(numericUserId);
        chatId = UUID.randomUUID().toString();

        session = new MockHttpSession();
        session.setAttribute(UserIdentity.SESSION_USER_ID, numericUserId);

        when(chatModel.call(any(Prompt.class))).thenReturn(textResponse("好的"));
        when(chatModel.stream(any(Prompt.class))).thenReturn(Flux.just(textResponse("好的")));
    }

    @AfterEach
    void tearDown()
    {
        userProfileService.clear(userId);
        chatMemory.clear(chatId);
        // 历史接口会给 chatId 登记归属，测试结束一起清掉，别给开发库留孤儿数据
        chatConversationService.remove(chatId);
    }

    // ==================== 画像 REST 接口 ====================

    @Test
    void profileEndpointReturnsItems() throws Exception
    {
        userProfileService.remember(userId, "昵称", NICK);

        mockMvc.perform(get("/ai/love_app/profile").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0]").value("- [昵称] " + NICK));
    }

    @Test
    void deleteProfileEndpointClearsItems() throws Exception
    {
        userProfileService.remember(userId, "昵称", NICK);

        mockMvc.perform(delete("/ai/love_app/profile").session(session))
                .andExpect(status().isOk());

        mockMvc.perform(get("/ai/love_app/profile").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isEmpty());
    }

    @Test
    void profileEndpointsRequireLogin() throws Exception
    {
        // 没有 session：不允许匿名读写画像
        mockMvc.perform(get("/ai/love_app/profile"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(delete("/ai/love_app/profile"))
                .andExpect(status().isUnauthorized());
    }

    // ==================== 注入链路：HTTP → Advisor → 系统提示词 ====================

    @Test
    void chatInjectsProfileIntoSystemMessage() throws Exception
    {
        userProfileService.remember(userId, "昵称", NICK);

        mockMvc.perform(get("/ai/love_app/chat/sync")
                        .param("message", "我是谁？")
                        .param("chatId", chatId)
                        .session(session))
                .andExpect(status().isOk());

        String systemText = systemTextOf(firstPromptFromCall());
        Assertions.assertTrue(systemText.contains(NICK),
                "画像应被注入系统提示词，实际是：\n" + systemText);
        Assertions.assertTrue(systemText.contains("用户画像"),
                "应带上画像小标题，实际是：\n" + systemText);
    }

    @Test
    void chatHistoryEndpointReturnsPreviousMessages() throws Exception
    {
        // 先聊一句，消息会按 chatId 存进对话记忆
        mockMvc.perform(get("/ai/love_app/chat/sync")
                        .param("message", "你好")
                        .param("chatId", chatId)
                        .session(session))
                .andExpect(status().isOk());

        // 前端刷新页面后靠这个接口把之前的对话显示出来
        mockMvc.perform(get("/ai/love_app/history")
                        .param("chatId", chatId)
                        .session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].role").value("USER"))
                .andExpect(jsonPath("$[0].content").value("你好"))
                .andExpect(jsonPath("$[1].role").value("ASSISTANT"));
    }

    @Test
    void chatWithoutLoginIsUnauthorized() throws Exception
    {
        mockMvc.perform(get("/ai/love_app/chat/sync")
                        .param("message", "我是谁？")
                        .param("chatId", chatId))
                .andExpect(status().isUnauthorized());

        // 未登录请求不能走到模型
        verify(chatModel, never()).call(any(Prompt.class));
    }

    @Test
    void sseChatInjectsProfileIntoSystemMessage() throws Exception
    {
        userProfileService.remember(userId, "昵称", NICK);

        // SSE 是前端真正用的接口（EventSource），这里也覆盖一下
        MvcResult result = mockMvc.perform(get("/ai/love_app/chat/sse")
                        .param("message", "我是谁？")
                        .param("chatId", chatId)
                        .session(session))
                .andExpect(request().asyncStarted())
                .andReturn();

        mockMvc.perform(asyncDispatch(result)).andExpect(status().isOk());

        // 流式走的是 chatModel.stream()，不是 call()
        String systemText = systemTextOf(firstPromptFromStream());
        Assertions.assertTrue(systemText.contains(NICK),
                "SSE 链路也应注入画像，实际是：\n" + systemText);
    }

    // ==================== 工具链路：HTTP → ToolContext → 工具 → 数据库 ====================

    @Test
    void toolContextCarriesTheUserIdToTheModel() throws Exception
    {
        mockMvc.perform(get("/ai/love_app/chat/sync")
                        .param("message", "记住我")
                        .param("chatId", chatId)
                        .session(session))
                .andExpect(status().isOk());

        // toolContext 必须出现在 prompt 的 options 里，OpenAiChatModel 才会把它注入给工具
        ToolCallingChatOptions options = (ToolCallingChatOptions) firstPromptFromCall().getOptions();
        Assertions.assertEquals(userId, options.getToolContext().get(UserProfileTools.USER_ID));
    }

    @Test
    void toolCallStoresProfileUnderTheRequestUser() throws Exception
    {
        // 让假模型「决定」调用记忆工具
        ChatResponse toolCallResponse = toolCallResponse();

        mockMvc.perform(get("/ai/love_app/chat/sync")
                        .param("message", "记住我叫" + NICK)
                        .param("chatId", chatId)
                        .session(session))
                .andExpect(status().isOk());

        // 用框架自己的 ToolCallingManager 执行工具 ——
        // 这正是 OpenAiChatModel 内部做的事（internalCall 里那段）：
        // 它从 prompt 的 options 里取出 toolContext，注入给工具方法的 ToolContext 参数。
        ToolExecutionResult result = ToolCallingManager.builder().build()
                .executeToolCalls(firstPromptFromCall(), toolCallResponse);

        // 画像落在登录用户 u_<id> 名下
        Assertions.assertEquals(List.of("- [昵称] " + NICK), userProfileService.items(userId));
        // 工具确实执行了，并回了「已记住」
        Assertions.assertTrue(toolResponseData(result).contains("已记住"),
                "工具应返回已记住，实际是：" + toolResponseData(result));
    }

    // ==================== 辅助方法 ====================

    /** 构造一个「模型决定调用记忆工具」的回复 */
    private ChatResponse toolCallResponse()
    {
        AssistantMessage.ToolCall toolCall = new AssistantMessage.ToolCall(
                "call-1", "function", "rememberUserPreference",
                "{\"category\":\"昵称\",\"value\":\"" + NICK + "\"}");
        ChatResponse response = ChatResponse.builder()
                .generations(List.of(new Generation(
                        AssistantMessage.builder().content("").toolCalls(List.of(toolCall)).build())))
                .build();
        when(chatModel.call(any(Prompt.class))).thenReturn(response);
        return response;
    }

    /** 构造一个普通文本回复 */
    private ChatResponse textResponse(String text)
    {
        return ChatResponse.builder()
                .generations(List.of(new Generation(new AssistantMessage(text))))
                .build();
    }

    /** 同步链路发到模型那里的第一个 Prompt */
    private Prompt firstPromptFromCall()
    {
        ArgumentCaptor<Prompt> captor = ArgumentCaptor.forClass(Prompt.class);
        verify(chatModel, atLeastOnce()).call(captor.capture());

        List<Prompt> prompts = captor.getAllValues();
        Assertions.assertFalse(prompts.isEmpty(), "模型一次都没被调用");
        return prompts.get(0);
    }

    /** 流式链路发到模型那里的第一个 Prompt */
    private Prompt firstPromptFromStream()
    {
        ArgumentCaptor<Prompt> captor = ArgumentCaptor.forClass(Prompt.class);
        verify(chatModel, atLeastOnce()).stream(captor.capture());

        List<Prompt> prompts = captor.getAllValues();
        Assertions.assertFalse(prompts.isEmpty(), "模型一次都没被调用");
        return prompts.get(0);
    }

    /** 取出 Prompt 里的系统提示词 */
    private String systemTextOf(Prompt prompt)
    {
        return prompt.getInstructions().stream()
                .filter(m -> m instanceof SystemMessage)
                .map(Message::getText)
                .collect(Collectors.joining("\n"));
    }

    /** 取出工具执行结果里，工具返回给模型的那段文本 */
    private String toolResponseData(ToolExecutionResult result)
    {
        List<Message> history = result.conversationHistory();
        Message last = history.get(history.size() - 1);
        Assertions.assertInstanceOf(ToolResponseMessage.class, last,
                "工具执行后最后一条应该是 ToolResponseMessage");
        ToolResponseMessage.ToolResponse response = ((ToolResponseMessage) last).getResponses().get(0);
        return response.responseData();
    }
}
