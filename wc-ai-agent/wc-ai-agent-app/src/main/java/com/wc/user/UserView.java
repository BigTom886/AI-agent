package com.wc.user;

import java.time.OffsetDateTime;

/**
 * 对外的用户信息
 *
 * 刻意不含 passwordHash —— 任何接口都不应该把密码哈希带出去。
 */
public record UserView(Long id, String username, String nickname, OffsetDateTime createdAt)
{
    public static UserView from(AppUser user)
    {
        return new UserView(user.getId(), user.getUsername(), user.getNickname(), user.getCreatedAt());
    }
}
