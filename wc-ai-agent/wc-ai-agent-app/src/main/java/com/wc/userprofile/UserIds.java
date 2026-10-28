package com.wc.userprofile;

import java.util.regex.Pattern;

/**
 * 用户 ID 校验
 *
 * userId 是「跨会话画像」的唯一钥匙。
 *
 * 现在所有对话/画像接口都要求登录，这个值由服务端根据 session 里的
 * app_user.id 生成（u_<id>），前端传的 userId 不再参与。
 *
 * 这里保留格式校验作为 Advisor / Tool 的防线：
 * 只允许字母、数字、下划线、中划线，长度 3~64 —— 避免 SQL 注入面、
 * 避免超长字符串把主键撑爆。
 */
public final class UserIds
{
    /** 允许的字符集：字母、数字、下划线、中划线 */
    private static final Pattern USER_ID_PATTERN = Pattern.compile("^[A-Za-z0-9_-]{3,64}$");

    private UserIds()
    {
    }

    /**
     * 必填校验：格式非法直接抛异常，Controller 的全局异常处理会转成 400
     *
     * @param userId 原始用户 ID
     * @return 去掉首尾空白后的用户 ID
     */
    public static String require(String userId)
    {
        String normalized = optional(userId);
        if (normalized == null)
        {
            throw new IllegalArgumentException("userId 不能为空");
        }
        return normalized;
    }

    /**
     * 可选校验：为空表示「这次请求没有用户身份」，返回 null
     *
     * @param userId 原始用户 ID
     * @return 归一化后的用户 ID；为空时返回 null
     * @throws IllegalArgumentException 格式非法（非空但不合法）时抛出
     */
    public static String optional(String userId)
    {
        if (userId == null || userId.isBlank())
        {
            return null;
        }
        String trimmed = userId.trim();
        if (!USER_ID_PATTERN.matcher(trimmed).matches())
        {
            throw new IllegalArgumentException("userId 格式非法：只允许字母、数字、下划线和中划线，长度 3~64");
        }
        return trimmed;
    }
}
