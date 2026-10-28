package com.wc.user;

import jakarta.validation.constraints.NotBlank;

/**
 * 登录请求体
 *
 * 这里只校验非空，用户名格式和密码对错统一由 UserService 判断，
 * 避免通过「格式错误」和「密码错误」的不同响应泄露账号是否存在。
 */
public record LoginRequest(
        @NotBlank(message = "用户名不能为空") String username,
        @NotBlank(message = "密码不能为空") String password)
{
}
