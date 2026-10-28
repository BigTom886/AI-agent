package com.wc.user;

import java.time.OffsetDateTime;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.wc.mapper.AppUserMapper;

import cn.hutool.crypto.digest.BCrypt;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 用户业务单元测试（不启动 Spring、不连数据库）
 *
 * Mapper 用 Mockito 替身。现在 Mapper 是 MyBatis-Plus 的 BaseMapper，
 * 所以桩点从原来的 findByUsername/insert(...) 变成了
 * selectOne(Wrapper) / selectById / insert(entity) / updateById(entity)。
 *
 * 重点验证：密码只以 BCrypt 哈希落库、登录/改密码的校验分支、
 * 以及 updateById 时只带允许修改的字段。
 */
@ExtendWith(MockitoExtension.class)
public class UserServiceTest
{
    @Mock
    private AppUserMapper appUserMapper;

    @InjectMocks
    private UserService userService;

    private static final OffsetDateTime NOW = OffsetDateTime.now();

    private static AppUser user(long id, String username, String nickname)
    {
        return new AppUser(id, username, BCrypt.hashpw("secret123"), nickname, NOW, NOW);
    }

    /** 让 mock 的 insert 像 MyBatis-Plus 一样回填自增主键 */
    private void insertAssignsId(long id)
    {
        when(appUserMapper.insert(any(AppUser.class))).thenAnswer(invocation -> {
            AppUser inserted = invocation.getArgument(0);
            inserted.setId(id);
            return 1;
        });
    }

    @Test
    void registerHashesPasswordAndReturnsCreatedUser()
    {
        AppUser created = user(1L, "alice", "Alice");
        when(appUserMapper.selectOne(any())).thenReturn(null);
        insertAssignsId(1L);
        when(appUserMapper.selectById(1L)).thenReturn(created);

        AppUser result = userService.register("alice", "secret123", "Alice");

        Assertions.assertEquals(1L, result.getId());
        Assertions.assertEquals("alice", result.getUsername());

        // 落库的必须是 BCrypt 哈希，而不是明文
        ArgumentCaptor<AppUser> captor = ArgumentCaptor.forClass(AppUser.class);
        verify(appUserMapper).insert(captor.capture());

        AppUser inserted = captor.getValue();
        Assertions.assertNotEquals("secret123", inserted.getPasswordHash());
        Assertions.assertTrue(BCrypt.checkpw("secret123", inserted.getPasswordHash()),
                "哈希应能校验原密码");
        Assertions.assertFalse(BCrypt.checkpw("wrong", inserted.getPasswordHash()),
                "错误密码不应通过");
    }

    @Test
    void registerDefaultsNicknameToUsername()
    {
        when(appUserMapper.selectOne(any())).thenReturn(null);
        insertAssignsId(1L);
        when(appUserMapper.selectById(1L)).thenReturn(user(1L, "alice", "alice"));

        userService.register("alice", "secret123", null);

        ArgumentCaptor<AppUser> captor = ArgumentCaptor.forClass(AppUser.class);
        verify(appUserMapper).insert(captor.capture());
        Assertions.assertEquals("alice", captor.getValue().getNickname());
    }

    @Test
    void usernameIsTrimmedAndLowercased()
    {
        when(appUserMapper.selectOne(any())).thenReturn(null);
        insertAssignsId(1L);
        when(appUserMapper.selectById(1L)).thenReturn(user(1L, "alice", "Alice"));

        userService.register("  Alice  ", "secret123", "Alice");

        ArgumentCaptor<AppUser> captor = ArgumentCaptor.forClass(AppUser.class);
        verify(appUserMapper).insert(captor.capture());
        Assertions.assertEquals("alice", captor.getValue().getUsername());
    }

    @Test
    void registerRejectsDuplicateUsername()
    {
        when(appUserMapper.selectOne(any())).thenReturn(user(1L, "alice", "Alice"));

        Assertions.assertThrows(IllegalArgumentException.class,
                () -> userService.register("alice", "secret123", "Alice"));

        verify(appUserMapper, never()).insert(any(AppUser.class));
    }

    @Test
    void registerRejectsIllegalUsername()
    {
        Assertions.assertThrows(IllegalArgumentException.class,
                () -> userService.register("ab", "secret123", "Alice"));

        verify(appUserMapper, never()).insert(any(AppUser.class));
    }

    @Test
    void loginReturnsUserForCorrectPassword()
    {
        when(appUserMapper.selectOne(any())).thenReturn(user(7L, "alice", "Alice"));

        AppUser result = userService.login("alice", "secret123");

        Assertions.assertEquals(7L, result.getId());
    }

    @Test
    void loginRejectsWrongPassword()
    {
        when(appUserMapper.selectOne(any())).thenReturn(user(7L, "alice", "Alice"));

        Assertions.assertThrows(UnauthorizedException.class,
                () -> userService.login("alice", "wrong-password"));
    }

    @Test
    void loginRejectsUnknownUser()
    {
        when(appUserMapper.selectOne(any())).thenReturn(null);

        Assertions.assertThrows(UnauthorizedException.class,
                () -> userService.login("nobody", "secret123"));
    }

    @Test
    void changePasswordRejectsWrongOldPassword()
    {
        when(appUserMapper.selectById(7L)).thenReturn(user(7L, "alice", "Alice"));

        Assertions.assertThrows(IllegalArgumentException.class,
                () -> userService.changePassword(7L, "wrong", "newsecret123"));

        verify(appUserMapper, never()).updateById(any(AppUser.class));
    }

    @Test
    void changePasswordStoresNewHash()
    {
        when(appUserMapper.selectById(7L)).thenReturn(user(7L, "alice", "Alice"));

        userService.changePassword(7L, "secret123", "newsecret123");

        ArgumentCaptor<AppUser> captor = ArgumentCaptor.forClass(AppUser.class);
        verify(appUserMapper).updateById(captor.capture());

        AppUser update = captor.getValue();
        Assertions.assertEquals(7L, update.getId());
        Assertions.assertTrue(BCrypt.checkpw("newsecret123", update.getPasswordHash()));
        Assertions.assertFalse(BCrypt.checkpw("secret123", update.getPasswordHash()));
        Assertions.assertNotNull(update.getUpdatedAt());
        // 只更新密码和时间，不要把用户名/昵称也带进 UPDATE
        Assertions.assertNull(update.getUsername());
        Assertions.assertNull(update.getNickname());
    }

    @Test
    void updateNicknameTrimsValueAndReturnsFreshUser()
    {
        AppUser updated = new AppUser(7L, "alice", "hash", "新名字", NOW, NOW);
        when(appUserMapper.updateById(any(AppUser.class))).thenReturn(1);
        when(appUserMapper.selectById(7L)).thenReturn(updated);

        AppUser result = userService.updateNickname(7L, "  新名字  ");

        Assertions.assertEquals("新名字", result.getNickname());

        ArgumentCaptor<AppUser> captor = ArgumentCaptor.forClass(AppUser.class);
        verify(appUserMapper).updateById(captor.capture());
        AppUser update = captor.getValue();
        Assertions.assertEquals(7L, update.getId());
        Assertions.assertEquals("新名字", update.getNickname());
        Assertions.assertNull(update.getUsername());
        Assertions.assertNull(update.getPasswordHash());
    }

    @Test
    void requireByIdRejectsMissingUser()
    {
        when(appUserMapper.selectById(anyLong())).thenReturn(null);

        Assertions.assertThrows(UnauthorizedException.class, () -> userService.requireById(99L));
    }
}
