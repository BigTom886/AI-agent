package com.wc.userprofile;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClientRequest;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * 用户画像测试（跨会话长期记忆）
 *
 * 需要本地 PostgreSQL 处于运行状态：启动时会自动建出 user_profile 表。
 * 整个测试不调用大模型，所以不依赖 API Key。
 */
@SpringBootTest
public class UserProfileServiceTest
{
    @Autowired
    private UserProfileService userProfileService;

    @Autowired
    private UserProfileAdvisor userProfileAdvisor;

    @Autowired
    private UserProfileTools userProfileTools;

    private String newUserId()
    {
        return "test-" + java.util.UUID.randomUUID();
    }

    @Test
    void rememberAndRead()
    {
        String userId = newUserId();
        try
        {
            userProfileService.remember(userId, "昵称", "韩跑跑");
            userProfileService.remember(userId, "偏好", "说话别太热情");

            List<String> items = userProfileService.items(userId);
            Assertions.assertEquals(2, items.size());
            Assertions.assertTrue(items.contains("- [昵称] 韩跑跑"));
            Assertions.assertTrue(items.contains("- [偏好] 说话别太热情"));
        }
        finally
        {
            userProfileService.clear(userId);
        }
    }

    @Test
    void sameCategoryOverwrites()
    {
        String userId = newUserId();
        try
        {
            userProfileService.remember(userId, "昵称", "韩跑跑");
            userProfileService.remember(userId, "昵称", "韩立");

            List<String> items = userProfileService.items(userId);
            Assertions.assertEquals(1, items.size());
            Assertions.assertEquals("- [昵称] 韩立", items.get(0));
        }
        finally
        {
            userProfileService.clear(userId);
        }
    }

    @Test
    void forgetRemovesOneCategory()
    {
        String userId = newUserId();
        try
        {
            userProfileService.remember(userId, "昵称", "韩跑跑");
            userProfileService.remember(userId, "偏好", "别太热情");

            Assertions.assertTrue(userProfileService.forget(userId, "偏好"));
            Assertions.assertFalse(userProfileService.forget(userId, "偏好"), "重复删除应返回 false");

            List<String> items = userProfileService.items(userId);
            Assertions.assertEquals(1, items.size());
            Assertions.assertEquals("- [昵称] 韩跑跑", items.get(0));
        }
        finally
        {
            userProfileService.clear(userId);
        }
    }

    @Test
    void profilesAreIsolatedByUser()
    {
        String userA = newUserId();
        String userB = newUserId();
        try
        {
            userProfileService.remember(userA, "昵称", "A");
            userProfileService.remember(userB, "昵称", "B");

            Assertions.assertEquals(List.of("- [昵称] A"), userProfileService.items(userA));
            Assertions.assertEquals(List.of("- [昵称] B"), userProfileService.items(userB));
        }
        finally
        {
            userProfileService.clear(userA);
            userProfileService.clear(userB);
        }
    }

    @Test
    void advisorInjectsProfileIntoSystemMessage()
    {
        String userId = newUserId();
        try
        {
            userProfileService.remember(userId, "昵称", "韩跑跑");

            ChatClientRequest request = ChatClientRequest.builder()
                    .prompt(Prompt.builder()
                            .messages(new SystemMessage("你是一个恋爱顾问"), new UserMessage("我是谁？"))
                            .build())
                    .context(Map.of(UserProfileAdvisor.USER_ID, userId))
                    .build();

            ChatClientRequest processed = userProfileAdvisor.before(request, null);

            List<Message> instructions = processed.prompt().getInstructions();
            String systemText = instructions.get(0).getText();
            Assertions.assertTrue(systemText.startsWith("你是一个恋爱顾问"), "原人设提示词必须保留");
            Assertions.assertTrue(systemText.contains("韩跑跑"), "画像应被注入系统消息");
            // 画像只注入系统消息，不污染用户消息
            Assertions.assertEquals("我是谁？", instructions.get(1).getText());
        }
        finally
        {
            userProfileService.clear(userId);
        }
    }

    @Test
    void advisorSkipsWhenNoUserId()
    {
        ChatClientRequest request = ChatClientRequest.builder()
                .prompt(Prompt.builder()
                        .messages(new SystemMessage("你是一个恋爱顾问"), new UserMessage("你好"))
                        .build())
                .context(Map.of(UserProfileAdvisor.USER_ID, ""))
                .build();

        ChatClientRequest processed = userProfileAdvisor.before(request, null);
        Assertions.assertEquals("你是一个恋爱顾问", processed.prompt().getInstructions().get(0).getText());
    }

    @Test
    void toolsNeedUserIdentity()
    {
        // 没有 userId 时工具不能写库，只能提示
        var toolContext = new org.springframework.ai.chat.model.ToolContext(Map.of(UserProfileTools.USER_ID, ""));
        Assertions.assertTrue(userProfileTools.rememberUserPreference("昵称", "韩跑跑", toolContext)
                .contains("没有用户身份"));
    }
}
