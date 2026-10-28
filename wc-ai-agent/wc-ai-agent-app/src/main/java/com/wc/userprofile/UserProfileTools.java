package com.wc.userprofile;

import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.stereotype.Component;

/**
 * 画像记忆工具 —— 让模型自己决定「什么时候该记住用户的事」
 *
 * 为什么用「显式工具」而不是「每次对话后让模型自动抽取画像」？
 * - 自动抽取要多调一次大模型，每次对话都多一次延迟和一次费用
 * - 自动抽取会误判：用户随口一提的事被当成长期信息存下来
 * - 显式工具可控：只有模型真的判断「这条值得长期记住」时才落库，
 *   而且用户能通过「忘掉我的偏好」这种话术撤回
 *
 * userId 从哪来？
 * 工具方法里加一个 ToolContext 参数，Spring AI 在调用工具时会把
 * ChatClientRequestSpec 上设置的 toolContext 注入进来（这个参数不会出现在
 * 给模型看的 JSON Schema 里，模型不需要、也不该知道 userId）。
 * 于是同一个工具实例就能服务所有用户，不用每个用户建一个。
 */
@Component
public class UserProfileTools
{
    /** ToolContext 里放 userId 的 key */
    public static final String USER_ID = "userId";

    private static final String NO_IDENTITY = "当前请求没有用户身份，无法保存画像。";

    private final UserProfileService userProfileService;

    public UserProfileTools(UserProfileService userProfileService)
    {
        this.userProfileService = userProfileService;
    }

    /**
     * 记录一条用户画像
     *
     * @param category 类别，例如「昵称」「职业」「偏好」「雷点」
     * @param value    具体内容
     * @param toolContext Spring AI 注入的调用上下文，用来拿到当前 userId
     * @return 给模型看的执行结果
     */
    @Tool(description = """
            把用户的个人信息或偏好存进长期画像（跨会话保留，下次对话自动带上）。
            当用户明确说出自己的身份信息、偏好、忌讳或目标时调用，例如：
            "记住我叫韩跑跑"、"我不喜欢太热情的语气"、"我目标是今年年底结婚"。
            category 用简短稳定的中文类别名，例如：昵称、年龄、职业、感情状态、偏好、雷点、目标。
            同一个类别再次记录会覆盖旧值。不要保存密码、身份证号、银行卡号等敏感信息。
            """)
    public String rememberUserPreference(String category, String value, ToolContext toolContext)
    {
        String userId = userId(toolContext);
        if (userId == null)
        {
            return NO_IDENTITY + "请告诉用户需要先登录。";
        }
        userProfileService.remember(userId, category, value);
        return "已记住：" + category + " → " + value;
    }

    /**
     * 删除某一类画像
     *
     * @param category 要删除的类别
     * @param toolContext Spring AI 注入的调用上下文
     * @return 给模型看的执行结果
     */
    @Tool(description = """
            删除用户长期画像里的某一类信息。
            当用户说「忘掉我的偏好」「别记我的职业了」「把我之前说的都删掉」时调用。
            category 填要删除的类别名，例如「偏好」「雷点」。
            """)
    public String forgetUserPreference(String category, ToolContext toolContext)
    {
        String userId = userId(toolContext);
        if (userId == null)
        {
            return NO_IDENTITY + "请告诉用户需要先登录。";
        }
        boolean removed = userProfileService.forget(userId, category);
        return removed ? "已忘掉：" + category : "画像里本来就没有「" + category + "」这一类。";
    }

    /**
     * 查看当前画像
     *
     * @param toolContext Spring AI 注入的调用上下文
     * @return 画像内容
     */
    @Tool(description = """
            查看当前已经保存的用户长期画像（跨会话记忆）。
            当用户问「你还记得我什么吗」「我之前告诉过你哪些信息」时调用。
            """)
    public String listUserPreferences(ToolContext toolContext)
    {
        String userId = userId(toolContext);
        if (userId == null)
        {
            return NO_IDENTITY + "请告诉用户需要先登录。";
        }
        var items = userProfileService.items(userId);
        if (items.isEmpty())
        {
            return "目前还没有保存任何用户画像。";
        }
        return "当前用户画像：\n" + String.join("\n", items);
    }

    /** 从 ToolContext 里取 userId；没有就说明这次请求没有用户身份 */
    private String userId(ToolContext toolContext)
    {
        if (toolContext == null)
        {
            return null;
        }
        Object raw = toolContext.getContext().get(USER_ID);
        if (raw == null)
        {
            return null;
        }
        String userId = String.valueOf(raw).trim();
        return userId.isEmpty() ? null : userId;
    }
}
