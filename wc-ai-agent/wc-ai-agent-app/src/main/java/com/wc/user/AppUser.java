package com.wc.user;

import java.time.OffsetDateTime;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.ToString;

/**
 * 用户账号（对应 app_user 表）
 *
 * MyBatis-Plus 需要可变的实体（无参构造 + getter/setter），
 * 所以这里用 Lombok 的 @Data，而不是 record。
 *
 * 注意：passwordHash 只用于服务端校验，绝不能直接返回给前端，
 * 对外统一用 {@link UserView}。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@TableName("app_user")
public class AppUser
{
    /** 自增主键；insert 后由 MyBatis-Plus 回填 */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    private String username;

    /** 任何时候都不该出现在日志里，toString 也排除掉 */
    @ToString.Exclude
    @TableField("password_hash")
    private String passwordHash;

    private String nickname;

    /** 建表默认值 CURRENT_TIMESTAMP；insert 时为 null 会被自动忽略 */
    @TableField("created_at")
    private OffsetDateTime createdAt;

    /** 更新时由 Service 显式设为当前时间 */
    @TableField("updated_at")
    private OffsetDateTime updatedAt;
}
