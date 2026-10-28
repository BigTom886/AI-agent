package com.wc.userprofile;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 * 用户 ID 校验测试（纯单元测试，不依赖数据库）
 */
public class UserIdsTest
{
    @Test
    void acceptValidId()
    {
        Assertions.assertEquals("hanpaopao", UserIds.require("hanpaopao"));
        Assertions.assertEquals("user_001", UserIds.require("user_001"));
        Assertions.assertEquals("a-b-c", UserIds.require("a-b-c"));
    }

    @Test
    void trimSurroundingSpaces()
    {
        Assertions.assertEquals("hanpaopao", UserIds.require("  hanpaopao  "));
    }

    @Test
    void blankMeansNoIdentity()
    {
        // 空 userId 不是错误，表示「这次请求没有用户身份」
        Assertions.assertNull(UserIds.optional(null));
        Assertions.assertNull(UserIds.optional(""));
        Assertions.assertNull(UserIds.optional("   "));
        // 但 require 版本必须报错
        Assertions.assertThrows(IllegalArgumentException.class, () -> UserIds.require(null));
        Assertions.assertThrows(IllegalArgumentException.class, () -> UserIds.require(""));
    }

    @Test
    void rejectIllegalId()
    {
        // 太短（可枚举）、超长、带特殊字符、带 SQL 注入片段，都要拒掉
        Assertions.assertThrows(IllegalArgumentException.class, () -> UserIds.require("1"));
        Assertions.assertThrows(IllegalArgumentException.class, () -> UserIds.require("ab"));
        Assertions.assertThrows(IllegalArgumentException.class, () -> UserIds.require("a".repeat(65)));
        Assertions.assertThrows(IllegalArgumentException.class, () -> UserIds.require("user name"));
        Assertions.assertThrows(IllegalArgumentException.class, () -> UserIds.require("'; drop table user_profile; --"));
        Assertions.assertThrows(IllegalArgumentException.class, () -> UserIds.require("用户1"));
    }
}
