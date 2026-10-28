package com.wc.userprofile;

import org.springframework.ai.chat.client.ChatClientRequest;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.client.advisor.api.Advisor;
import org.springframework.ai.chat.client.advisor.api.AdvisorChain;
import org.springframework.ai.chat.client.advisor.api.BaseAdvisor;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.stereotype.Component;

/**
 * 用户画像 Advisor —— 把跨会话画像注入到系统提示词里
 *
 * 链路：
 *   请求 ──► UserProfileAdvisor.before()  ← 读 user_profile 表，把画像拼到系统消息末尾
 *        ──► MessageChatMemoryAdvisor.before() ← 拼上本次会话的历史消息
 *        ──► 大模型
 *
 * 为什么用 Advisor 而不是每次调用前手动拼字符串？
 * Advisor 是「每次请求都会经过」的位置，只写一次就对该 ChatClient 的所有调用生效
 * （同步、流式、报告生成），业务代码里完全不用管画像的存在。
 *
 * 为什么排在 MessageChatMemoryAdvisor 前面？
 * MessageChatMemoryAdvisor 会把「第一个 SystemMessage」提到最前面。
 * 如果我们排在它后面，augmentSystemMessage 有可能改到记忆里的系统消息而不是人设提示词。
 * 排在前面就一定改的是本次请求的提示词，行为确定。
 *
 * 画像不会被写进对话记忆：它每次都从库里现读，所以用户中途更新画像，
 * 下一轮就能用上最新的，不需要重建会话。
 */
@Component
public class UserProfileAdvisor implements BaseAdvisor
{
    /** Advisor 上下文里放 userId 的 key */
    public static final String USER_ID = "userId";

    /** 拼到系统消息末尾的画像小标题 */
    private static final String PROFILE_HEADER = "\n\n# 用户画像（跨会话记忆，自动注入）\n";

    private final UserProfileService userProfileService;

    public UserProfileAdvisor(UserProfileService userProfileService)
    {
        this.userProfileService = userProfileService;
    }

    @Override
    public int getOrder()
    {
        // 比 MessageChatMemoryAdvisor（DEFAULT_CHAT_MEMORY_PRECEDENCE_ORDER）先跑
        return Advisor.DEFAULT_CHAT_MEMORY_PRECEDENCE_ORDER - 1;
    }

    @Override
    public ChatClientRequest before(ChatClientRequest chatClientRequest, AdvisorChain advisorChain)
    {
        String userId = resolveUserId(chatClientRequest);
        if (userId == null)
        {
            // 没有用户身份：这次请求不注入画像
            return chatClientRequest;
        }

        String profile = userProfileService.profileText(userId);
        if (profile.isBlank())
        {
            return chatClientRequest;
        }

        // augmentSystemMessage 会找到「第一个 SystemMessage」并替换它；
        // 如果请求里压根没有系统消息，它会新建一个（不会 NPE）。
        Prompt prompt = chatClientRequest.prompt().augmentSystemMessage(
                system -> system.mutate().text(system.getText() + PROFILE_HEADER + profile + "\n").build());

        return chatClientRequest.mutate().prompt(prompt).build();
    }

    @Override
    public ChatClientResponse after(ChatClientResponse chatClientResponse, AdvisorChain advisorChain)
    {
        // 画像只读不写，写入交给记忆工具（模型自己决定什么时候记）
        return chatClientResponse;
    }

    /** 从 Advisor 上下文里取出 userId；没有或格式非法都当成「没有身份」 */
    private String resolveUserId(ChatClientRequest chatClientRequest)
    {
        Object raw = chatClientRequest.context().get(USER_ID);
        if (raw == null)
        {
            return null;
        }
        return UserIds.optional(String.valueOf(raw));
    }
}
