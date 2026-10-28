package com.wc.user;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;

/**
 * 登录身份的唯一出入口
 *
 * 对话、画像等业务接口都必须先登录：身份只认 session，
 * 前端传的 userId 一律忽略，也不再支持匿名回退。
 *
 * 注意这里所有方法都接收 HttpServletRequest 而不是 HttpSession：
 * 如果把 HttpSession 写成 Controller 参数，Spring 的解析器会调用
 * request.getSession()，导致每个未登录请求都白白创建一个会话。
 * 只有真正需要建立登录态的地方（login/register）才 getSession(true)。
 */
public final class UserIdentity
{
    /** session 里存的是 app_user.id（Long），不是用户名：改昵称/改名都不会让身份漂移 */
    public static final String SESSION_USER_ID = "wc.user.id";

    private UserIdentity()
    {
    }

    /**
     * 当前登录用户的主键；未登录返回 null。
     * 用 getSession(false)，不会创建 session。
     */
    public static Long currentUserId(HttpServletRequest request)
    {
        HttpSession session = request.getSession(false);
        if (session == null)
        {
            return null;
        }
        Object value = session.getAttribute(SESSION_USER_ID);
        return value instanceof Number number ? number.longValue() : null;
    }

    /**
     * 登录用户对应的画像键（user_profile.user_id）
     */
    public static String profileUserId(long userId)
    {
        return "u_" + userId;
    }

    /**
     * 要求已登录，返回本次请求对应的画像键（user_profile.user_id）
     *
     * @throws UnauthorizedException 未登录 / session 已过期
     */
    public static String requireProfileUserId(HttpServletRequest request)
    {
        return profileUserId(requireUserId(request));
    }

    /**
     * 要求已登录，返回用户主键
     *
     * @throws UnauthorizedException 未登录 / session 已过期
     */
    public static long requireUserId(HttpServletRequest request)
    {
        Long userId = currentUserId(request);
        if (userId == null)
        {
            throw new UnauthorizedException("未登录或登录已过期");
        }
        return userId;
    }

    /**
     * 建立登录态
     *
     * 先 getSession(true) 再 changeSessionId()：后者要求 session 已存在。
     * 换 sessionId 是为了防 session fixation（攻击者预先塞一个 sessionId 给受害者）。
     */
    public static void login(HttpServletRequest request, long userId)
    {
        HttpSession session = request.getSession(true);  //找不到session时，新建 Session
        request.changeSessionId();
        session.setAttribute(SESSION_USER_ID, userId);
    }

    /**
     * 退出登录（幂等：没有 session 时什么也不做）
     */
    public static void logout(HttpServletRequest request)
    {
        HttpSession session = request.getSession(false);//找不到session时，返回null
        if (session != null)
        {
            session.invalidate(); //销毁当前这个 HttpSession，让会话直接失效
        }
    }
}
