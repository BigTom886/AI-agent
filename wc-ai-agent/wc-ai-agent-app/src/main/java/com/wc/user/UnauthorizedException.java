package com.wc.user;

/**
 * 未登录 / 登录失效 / 用户名或密码错误
 *
 * GlobalExceptionHandler 会把它转成 HTTP 401。
 * 单独建一个异常，是为了和「参数不合法」（400）区分开：
 * 前者需要前端跳登录，后者只是这次请求写错了。
 */
public class UnauthorizedException extends RuntimeException
{
    public UnauthorizedException(String message)
    {
        super(message);
    }
}
