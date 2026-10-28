package com.wc.chatmemory;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 * 会话 ID 校验测试（纯单元测试，不依赖数据库）
 */
public class ChatIdsTest
{
    @Test
    void acceptValidUuid()
    {
        String uuid = "3f2a1b8c-9d4e-4f1a-8b2c-7d6e5f4a3b2c";
        Assertions.assertEquals(uuid, ChatIds.require(uuid));
    }

    @Test
    void normalizeToLowerCase()
    {
        String uuid = "3F2A1B8C-9D4E-4F1A-8B2C-7D6E5F4A3B2C";
        Assertions.assertEquals(uuid.toLowerCase(), ChatIds.require(uuid));
    }

    @Test
    void rejectGuessableId()
    {
        // "1" 这类可枚举的 ID 必须被拒掉，否则他人可以猜中别人的会话
        Assertions.assertThrows(IllegalArgumentException.class, () -> ChatIds.require("1"));
        Assertions.assertThrows(IllegalArgumentException.class, () -> ChatIds.require("../../etc/passwd"));
        Assertions.assertThrows(IllegalArgumentException.class, () -> ChatIds.require(""));
        Assertions.assertThrows(IllegalArgumentException.class, () -> ChatIds.require(null));
    }
}
