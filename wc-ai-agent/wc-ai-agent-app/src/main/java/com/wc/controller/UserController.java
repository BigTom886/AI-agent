package com.wc.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.wc.user.AppUser;
import com.wc.user.ChangePasswordRequest;
import com.wc.user.LoginRequest;
import com.wc.user.RegisterRequest;
import com.wc.user.UpdateNicknameRequest;
import com.wc.user.UserIdentity;
import com.wc.user.UserService;
import com.wc.user.UserView;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;

/**
 * 用户管理接口
 *
 * 登录态用 HttpSession（JSESSIONID Cookie），不引入 Spring Security / JWT。
 * 所有路径实际都带 /api 前缀（server.servlet.context-path）。
 */
@RestController
@RequestMapping("/user")
@Tag(name = "用户管理", description = "注册、登录、退出、个人资料")
public class UserController
{
    private final UserService userService;

    public UserController(UserService userService)
    {
        this.userService = userService;
    }

    /**
     * 注册并自动登录
     *
     * @return 用户信息（不含密码哈希）
     */
    @PostMapping("/register")
    @Operation(summary = "注册", description = "用户名 3-32 位字母/数字/下划线/中划线，密码 6-64 位；注册成功后自动登录")
    public UserView register(@Valid @RequestBody RegisterRequest body, HttpServletRequest request)
    {
        AppUser user = userService.register(body.username(), body.password(), body.nickname());
        UserIdentity.login(request, user.getId());
        return UserView.from(user);
    }

    /**
     * 登录
     *
     * @return 用户信息（不含密码哈希）
     */
    @PostMapping("/login")
    @Operation(summary = "登录", description = "成功后服务端写入 session，并把 sessionId 换成新的")
    public UserView login(@Valid @RequestBody LoginRequest body, HttpServletRequest request)
    {
        AppUser user = userService.login(body.username(), body.password());
        UserIdentity.login(request, user.getId());
        return UserView.from(user);
    }

    /**
     * 退出登录（幂等）
     */
    @PostMapping("/logout")
    @Operation(summary = "退出登录")
    public String logout(HttpServletRequest request)
    {
        UserIdentity.logout(request);
        return "ok";
    }

    /**
     * 当前登录用户的信息
     */
    @GetMapping("/me")
    @Operation(summary = "查看当前用户")
    public UserView me(HttpServletRequest request)
    {
        long userId = UserIdentity.requireUserId(request);
        return UserView.from(userService.requireById(userId));
    }

    /**
     * 修改当前用户的昵称
     */
    @PutMapping("/me")
    @Operation(summary = "修改个人资料", description = "目前只支持修改昵称")
    public UserView updateMe(@Valid @RequestBody UpdateNicknameRequest body,
            HttpServletRequest request)
    {
        long userId = UserIdentity.requireUserId(request);
        return UserView.from(userService.updateNickname(userId, body.nickname()));
    }

    /**
     * 修改密码（需要提供原密码）
     */
    @PutMapping("/password")
    @Operation(summary = "修改密码", description = "需要提供原密码；成功后轮换 sessionId")
    public String changePassword(@Valid @RequestBody ChangePasswordRequest body,
            HttpServletRequest request)
    {
        long userId = UserIdentity.requireUserId(request);
        userService.changePassword(userId, body.oldPassword(), body.newPassword());
        // 改完密码换一个 sessionId，旧会话标识立即作废
        request.changeSessionId();
        return "ok";
    }
}
