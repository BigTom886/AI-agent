package com.wc.user;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

/**
 * 登录身份解析测试
 *
 * 现在所有业务接口都要求登录，身份只认 session：
 * 没有 session 一律抛 UnauthorizedException，不再有匿名回退。
 */
public class UserIdentityTest
{
    @Test
    void currentUserIdIsNullWhenNotLoggedIn()
    {
        MockHttpServletRequest request = new MockHttpServletRequest();

        Assertions.assertNull(UserIdentity.currentUserId(request));
        // 读取身份不应该顺手创建 session
        Assertions.assertNull(request.getSession(false));
    }

    @Test
    void requireUserIdThrowsWhenNotLoggedIn()
    {
        Assertions.assertThrows(UnauthorizedException.class,
                () -> UserIdentity.requireUserId(new MockHttpServletRequest()));
    }

    @Test
    void requireProfileUserIdThrowsWhenNotLoggedIn()
    {
        Assertions.assertThrows(UnauthorizedException.class,
                () -> UserIdentity.requireProfileUserId(new MockHttpServletRequest()));
    }

    @Test
    void requireProfileUserIdReturnsDerivedIdWhenLoggedIn()
    {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.getSession(true).setAttribute(UserIdentity.SESSION_USER_ID, 42L);

        Assertions.assertEquals("u_42", UserIdentity.requireProfileUserId(request));
    }

    @Test
    void loginRotatesSessionIdAndStoresUserId()
    {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.getSession(true);
        String originalSessionId = request.getSession(false).getId();

        UserIdentity.login(request, 42L);

        Assertions.assertNotEquals(originalSessionId, request.getSession(false).getId(),
                "登录后必须换 sessionId，防 session fixation");
        Assertions.assertEquals(42L,
                request.getSession(false).getAttribute(UserIdentity.SESSION_USER_ID));
    }

    @Test
    void logoutInvalidatesSession()
    {
        MockHttpServletRequest request = new MockHttpServletRequest();
        UserIdentity.login(request, 42L);

        UserIdentity.logout(request);

        Assertions.assertNull(request.getSession(false));
    }
}
