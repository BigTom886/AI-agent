package com.wc.user;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * 注册请求体
 *
 * @param username 登录名：只允许字母、数字、下划线、中划线，3~32 位
 * @param password 密码：6~64 位，不做 trim（空格也是密码的一部分）
 * @param nickname 昵称：可空，默认与用户名相同
 */
public record RegisterRequest(
        @NotBlank(message = "用户名不能为空")
        @Pattern(regexp = "^[A-Za-z0-9_-]{3,32}$",
                message = "用户名需为3-32位字母、数字、下划线或中划线")
        String username,

        @NotBlank(message = "密码不能为空")
        @Size(min = 6, max = 64, message = "密码长度需为6-64位")
        String password,

        @Size(max = 32, message = "昵称不能超过32个字符")
        String nickname)
{
}
