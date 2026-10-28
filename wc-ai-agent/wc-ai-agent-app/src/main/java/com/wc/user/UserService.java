package com.wc.user;

import java.time.OffsetDateTime;
import java.util.Locale;
import java.util.regex.Pattern;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.wc.mapper.AppUserMapper;

import cn.hutool.crypto.digest.BCrypt;

/**
 * 用户账号业务
 *
 * 只做四件事：注册、登录、改昵称、改密码。
 * 密码统一用 BCrypt 哈希（Hutool 自带实现），库里永远不存明文。
 *
 * 数据访问全部走 MyBatis-Plus 的 BaseMapper：
 * 主键查询用 selectById，条件查询用 LambdaQueryWrapper，更新用 updateById。
 */
@Service
public class UserService
{
    /** 与 RegisterRequest 的校验规则保持一致，Service 层再兜一道，防止绕过 Controller 调用 */
    private static final Pattern USERNAME_PATTERN = Pattern.compile("^[A-Za-z0-9_-]{3,32}$");

    private final AppUserMapper appUserMapper;

    public UserService(AppUserMapper appUserMapper)
    {
        this.appUserMapper = appUserMapper;
    }

    /**
     * 注册
     *
     * @return 新建的账号（含主键），调用方用它建立登录态
     * @throws IllegalArgumentException 用户名格式非法 / 已存在、密码或昵称不合法
     */
    @Transactional
    public AppUser register(String rawUsername, String rawPassword, String rawNickname)
    {
        String username = requireUsername(rawUsername);
        String password = requirePassword(rawPassword);
        String nickname = requireNickname(rawNickname, username);

        if (findByUsername(username) != null)
        {
            throw new IllegalArgumentException("用户名已存在");
        }

        AppUser user = new AppUser();
        user.setUsername(username);
        user.setPasswordHash(BCrypt.hashpw(password));
        user.setNickname(nickname);

        try
        {
            // id 是 IdType.AUTO，insert 成功后由 MyBatis-Plus 回填到 user.id
            appUserMapper.insert(user);
        }
        catch (DuplicateKeyException e)
        {
            // 上面的查重和这里的 INSERT 之间可能有并发请求，唯一索引兜底
            throw new IllegalArgumentException("用户名已存在");
        }

        if (user.getId() == null)
        {
            throw new IllegalStateException("注册失败，请重试");
        }

        // 回查一次：created_at/updated_at 由数据库默认值生成，insert 后的实体里没有
        AppUser created = appUserMapper.selectById(user.getId());
        return created != null ? created : user;
    }

    /**
     * 登录
     *
     * @throws UnauthorizedException 用户不存在或密码错误（两种情况返回同一句话，
     *                               避免被用来探测账号是否存在）
     */
    public AppUser login(String rawUsername, String rawPassword)
    {
        String username = rawUsername == null ? "" : rawUsername.trim().toLowerCase(Locale.ROOT);
        AppUser user = findByUsername(username);

        // Hutool BCrypt 的参数顺序：明文在前，已存的哈希在后
        if (user == null || rawPassword == null
                || !BCrypt.checkpw(rawPassword, user.getPasswordHash()))
        {
            throw new UnauthorizedException("用户名或密码错误");
        }
        return user;
    }

    /**
     * 按主键取账号（/user/me 和 session 校验用）
     *
     * @throws UnauthorizedException 账号已被删除 / session 指向了不存在的用户
     */
    public AppUser requireById(long id)
    {
        AppUser user = appUserMapper.selectById(id);
        if (user == null)
        {
            throw new UnauthorizedException("登录已失效，请重新登录");
        }
        return user;
    }

    /**
     * 修改昵称
     *
     * @return 更新后的账号
     */
    @Transactional
    public AppUser updateNickname(long id, String rawNickname)
    {
        String nickname = requireNickname(rawNickname, null);

        AppUser update = new AppUser();
        update.setId(id);
        update.setNickname(nickname);
        update.setUpdatedAt(OffsetDateTime.now());

        // updateById 只更新非 null 字段：username/passwordHash/createdAt 都不会被动到
        if (appUserMapper.updateById(update) == 0)
        {
            throw new UnauthorizedException("登录已失效，请重新登录");
        }
        return requireById(id);
    }

    /**
     * 修改密码
     *
     * @throws IllegalArgumentException 原密码不正确 / 新密码不合法
     */
    @Transactional
    public void changePassword(long id, String oldPassword, String newPassword)
    {
        AppUser user = requireById(id);
        if (oldPassword == null || !BCrypt.checkpw(oldPassword, user.getPasswordHash()))
        {
            throw new IllegalArgumentException("原密码不正确");
        }

        AppUser update = new AppUser();
        update.setId(id);
        update.setPasswordHash(BCrypt.hashpw(requirePassword(newPassword)));
        update.setUpdatedAt(OffsetDateTime.now());
        appUserMapper.updateById(update);
    }

    /**
     * 按用户名查账号
     *
     * 表上有唯一索引，最多一条；用 LambdaQueryWrapper 可以避免手写列名。
     */
    private AppUser findByUsername(String username)
    {
        return appUserMapper.selectOne(new LambdaQueryWrapper<AppUser>()
                .eq(AppUser::getUsername, username));
    }

    // ==================== 参数校验 ====================

    private static String requireUsername(String raw)
    {
        // 统一小写：登录名不区分大小写，避免「注册 Alice、却用 alice 登不上」
        String value = raw == null ? "" : raw.trim().toLowerCase(Locale.ROOT);
        if (!USERNAME_PATTERN.matcher(value).matches())
        {
            throw new IllegalArgumentException("用户名需为3-32位字母、数字、下划线或中划线");
        }
        return value;
    }

    private static String requirePassword(String raw)
    {
        // 密码不 trim：前后空格也是密码的一部分
        if (raw == null || raw.isEmpty())
        {
            throw new IllegalArgumentException("密码不能为空");
        }
        if (raw.length() < 6 || raw.length() > 64)
        {
            throw new IllegalArgumentException("密码长度需为6-64位");
        }
        return raw;
    }

    /**
     * @param fallback 为空时的默认值；传 null 表示「昵称必填」
     */
    private static String requireNickname(String raw, String fallback)
    {
        String value = raw == null ? "" : raw.trim();
        if (value.isEmpty())
        {
            if (fallback != null)
            {
                return fallback;
            }
            throw new IllegalArgumentException("昵称不能为空");
        }
        if (value.length() > 32)
        {
            throw new IllegalArgumentException("昵称不能超过32个字符");
        }
        return value;
    }
}
