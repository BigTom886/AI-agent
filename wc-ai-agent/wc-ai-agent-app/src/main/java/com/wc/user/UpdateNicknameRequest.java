package com.wc.user;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 修改个人资料请求体（目前只有昵称）
 *
 * @param nickname 新昵称：1~32 个字符
 */
public record UpdateNicknameRequest(
        @NotBlank(message = "昵称不能为空")
        @Size(max = 32, message = "昵称不能超过32个字符")
        String nickname)
{
}
