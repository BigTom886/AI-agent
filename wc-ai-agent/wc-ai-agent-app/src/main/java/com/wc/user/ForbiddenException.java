package com.wc.user;

/**
 * 没有权限（登录了，但不是自己的东西）
 *
 * GlobalExceptionHandler 会把它转成 HTTP 403。
 * 与 {@link UnauthorizedException}（401，未登录）的区别：
 * 401 是「你是谁？」不清楚，403 是「知道你是谁，但这个不归你管」。
 */
public class ForbiddenException extends RuntimeException
{
    public ForbiddenException(String message)
    {
        super(message);
    }
}
