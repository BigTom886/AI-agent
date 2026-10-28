package com.wc.user;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 修改密码请求体
 *
 * @param oldPassword 原密码，用来确认是本人操作
 * @param newPassword 新密码：6~64 位
 */
public record ChangePasswordRequest(
        @NotBlank(message = "原密码不能为空") String oldPassword,

        @NotBlank(message = "新密码不能为空")
        @Size(min = 6, max = 64, message = "新密码长度需为6-64位")
        String newPassword)
{
}
