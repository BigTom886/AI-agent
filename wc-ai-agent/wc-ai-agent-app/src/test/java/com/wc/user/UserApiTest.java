package com.wc.user;

import java.util.UUID;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wc.userprofile.UserProfileService;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 用户模块的 HTTP 端到端测试
 *
 * 覆盖：注册写库 + 自动登录 → /me → 退出；登录失败；重名；
 * 以及「登录后画像身份以 session 为准，忽略前端 userId」。
 *
 * @Transactional 让整个测试在事务里执行，结束后回滚，不会往开发库里留测试账号。
 * MockMvc 不带 context-path，所以路径是 /user/**、/ai/**。
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
public class UserApiTest
{
    private static final String PASSWORD = "secret123";
    private static final String NICK = "E2E_USER_NICK";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserProfileService userProfileService;

    /** 避免上下文启动后真的去调大模型；本测试用不到模型输出 */
    @MockitoBean
    private ChatModel chatModel;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void registerLogsInAndMeReturnsProfile() throws Exception
    {
        String username = randomUsername();

        MvcResult registered = mockMvc.perform(post("/user/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registerBody(username)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value(username))
                .andExpect(jsonPath("$.nickname").value("测试用户"))
                // 密码哈希绝不能出现在响应里
                .andExpect(jsonPath("$.passwordHash").doesNotExist())
                .andReturn();

        MockHttpSession session = (MockHttpSession) registered.getRequest().getSession(false);
        Assertions.assertNotNull(session, "注册应自动建立登录态");

        mockMvc.perform(get("/user/me").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value(username));
    }

    @Test
    void meWithoutSessionIsUnauthorized() throws Exception
    {
        mockMvc.perform(get("/user/me"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void duplicateUsernameIsRejected() throws Exception
    {
        String username = randomUsername();

        mockMvc.perform(post("/user/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registerBody(username)))
                .andExpect(status().isOk());

        mockMvc.perform(post("/user/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registerBody(username)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void invalidRegisterBodyIsRejected() throws Exception
    {
        // 用户名太短、密码太短：@Valid 应拦下并返回 400
        mockMvc.perform(post("/user/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"ab","password":"123"}
                                """))
                .andExpect(status().isBadRequest());
    }

    @Test
    void wrongPasswordIsUnauthorized() throws Exception
    {
        String username = randomUsername();

        mockMvc.perform(post("/user/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registerBody(username)))
                .andExpect(status().isOk());

        mockMvc.perform(post("/user/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"%s","password":"wrong-password"}
                                """.formatted(username)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void updateNicknameAndChangePassword() throws Exception
    {
        String username = randomUsername();

        MvcResult registered = mockMvc.perform(post("/user/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registerBody(username)))
                .andExpect(status().isOk())
                .andReturn();

        MockHttpSession session = (MockHttpSession) registered.getRequest().getSession(false);

        // 改昵称：走 MyBatis-Plus updateById，只更新 nickname/updated_at
        mockMvc.perform(put("/user/me")
                        .session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"nickname":"新昵称"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nickname").value("新昵称"));

        // 改密码
        mockMvc.perform(put("/user/password")
                        .session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"oldPassword":"secret123","newPassword":"newsecret123"}
                                """))
                .andExpect(status().isOk());

        // 旧密码失效
        mockMvc.perform(post("/user/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"%s","password":"secret123"}
                                """.formatted(username)))
                .andExpect(status().isUnauthorized());

        // 新密码可用，且昵称仍是更新后的值
        mockMvc.perform(post("/user/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"%s","password":"newsecret123"}
                                """.formatted(username)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nickname").value("新昵称"));
    }

    @Test
    void logoutInvalidatesSession() throws Exception
    {
        String username = randomUsername();

        MvcResult registered = mockMvc.perform(post("/user/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registerBody(username)))
                .andExpect(status().isOk())
                .andReturn();

        MockHttpSession session = (MockHttpSession) registered.getRequest().getSession(false);

        mockMvc.perform(post("/user/logout").session(session))
                .andExpect(status().isOk());

        Assertions.assertTrue(session.isInvalid(), "退出后 session 必须失效");
    }

    @Test
    void loggedInProfileUsesSessionIdentity() throws Exception
    {
        String username = randomUsername();

        MvcResult registered = mockMvc.perform(post("/user/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registerBody(username)))
                .andExpect(status().isOk())
                .andReturn();

        long id = objectMapper.readTree(registered.getResponse().getContentAsString())
                .get("id").asLong();
        MockHttpSession session = (MockHttpSession) registered.getRequest().getSession(false);

        // 登录用户的画像键是 u_<id>
        userProfileService.remember(UserIdentity.profileUserId(id), "昵称", NICK);

        // 即使前端传一个无关的 userId，后端也只认 session
        mockMvc.perform(get("/ai/love_app/profile")
                        .param("userId", "someone_else")
                        .session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0]").value("- [昵称] " + NICK));
    }

    @Test
    void anonymousCannotReadProfile() throws Exception
    {
        mockMvc.perform(get("/ai/love_app/profile"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void allChatEndpointsRequireLogin() throws Exception
    {
        String chatId = UUID.randomUUID().toString();

        mockMvc.perform(get("/ai/love_app/chat/sync")
                        .param("message", "hi").param("chatId", chatId))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(get("/ai/love_app/chat/sse")
                        .param("message", "hi").param("chatId", chatId))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(get("/ai/love_app/chat/sse/emitter")
                        .param("message", "hi").param("chatId", chatId))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(get("/ai/love_app/history").param("chatId", chatId))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(delete("/ai/love_app/conversation").param("chatId", chatId))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(get("/mcp/chat").param("message", "hi"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(get("/ai/manus/chat").param("message", "hi"))
                .andExpect(status().isUnauthorized());
    }

    private static String randomUsername()
    {
        return "t" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
    }

    private static String registerBody(String username)
    {
        return """
                {"username":"%s","password":"%s","nickname":"测试用户"}
                """.formatted(username, PASSWORD);
    }
}
