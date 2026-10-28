package com.wc.chatmemory;

import java.time.OffsetDateTime;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.wc.mapper.ChatConversationMapper;
import com.wc.user.ForbiddenException;

/**
 * 会话归属服务
 *
 * 回答一个问题：这个 chatId 是不是当前用户的？
 *
 * chatId 由前端生成（UUID），本来「谁拿到都能用」，
 * 加上这张归属表之后：
 *   - 第一次用某个 chatId 聊天时，把它登记到当前用户名下
 *   - 之后读历史、删会话，都要先过归属校验
 */
@Service
public class ChatConversationService
{
    private final ChatConversationMapper mapper;

    public ChatConversationService(ChatConversationMapper mapper)
    {
        this.mapper = mapper;
    }

    /**
     * 登记会话归属（聊天时调用）
     *
     * @throws ForbiddenException 这个 chatId 已经属于别人
     */
    @Transactional
    public void bind(String chatId, long userId)
    {
        ChatConversation existing = mapper.selectById(chatId);
        if (existing != null)
        {
            requireSameUser(existing, userId);
            return;
        }

        ChatConversation conversation = new ChatConversation();
        conversation.setChatId(chatId);
        conversation.setUserId(userId);
        conversation.setCreatedAt(OffsetDateTime.now());
        conversation.setUpdatedAt(OffsetDateTime.now());
        try
        {
            mapper.insert(conversation);
        }
        catch (DuplicateKeyException e)
        {
            // 并发时可能被别人先登记了，再查一次确认归属
            ChatConversation now = mapper.selectById(chatId);
            if (now != null)
            {
                requireSameUser(now, userId);
            }
        }
    }

    /**
     * 校验会话归属（读历史、删会话时调用）
     *
     * 还没有归属记录的，是接登录体系之前留下的老会话：
     * 谁先访问就算谁的（chatId 是随机 UUID，撞上的概率极低）。
     *
     * @throws ForbiddenException 这个 chatId 已经属于别人
     */
    @Transactional
    public void requireOwnership(String chatId, long userId)
    {
        ChatConversation existing = mapper.selectById(chatId);
        if (existing == null)
        {
            bind(chatId, userId);
            return;
        }
        requireSameUser(existing, userId);
    }

    /**
     * 删除归属记录（会话本身删掉后调用）
     */
    @Transactional
    public void remove(String chatId)
    {
        mapper.deleteById(chatId);
    }

    private static void requireSameUser(ChatConversation conversation, long userId)
    {
        Long owner = conversation.getUserId();
        if (owner == null || owner.longValue() != userId)
        {
            throw new ForbiddenException("该会话不属于当前用户");
        }
    }
}
