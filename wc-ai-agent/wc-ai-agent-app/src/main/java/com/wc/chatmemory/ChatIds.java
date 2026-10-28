package com.wc.chatmemory;

import java.util.UUID;
import java.util.regex.Pattern;

/**
 * 会话 ID 校验
 *
 * chatId 由前端生成，本质上是"持有即拥有"的凭证。
 * 一旦允许任意字符串，就可能出现 "1"、"a" 这类可枚举的 ID，
 * 导致他人通过猜测 ID 读取/删除别人的对话，因此统一要求 UUID 格式。
 */
public final class ChatIds
{
    /** 标准 UUID 格式（含连字符） */
    private static final Pattern UUID_PATTERN = Pattern
            .compile("^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$");

    private ChatIds()
    {
    }

    /**
     * 校验并归一化会话 ID
     *
     * @param chatId 原始会话 ID
     * @return 归一化（小写）后的 UUID 字符串
     * @throws IllegalArgumentException 格式非法时抛出，Controller 会转成 400
     */
    public static String require(String chatId)
    {
        if (chatId == null || chatId.isBlank())
        {
            throw new IllegalArgumentException("chatId 不能为空");
        }
        String trimmed = chatId.trim();
        if (!UUID_PATTERN.matcher(trimmed).matches())
        {
            throw new IllegalArgumentException("chatId 格式非法，应为 UUID");
        }
        // 统一小写，避免同一个会话因为大小写不同被当成两个
        return UUID.fromString(trimmed).toString();
    }
}
