package com.wc.userprofile;

import java.time.LocalDateTime;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 用户画像（对应 user_profile 表）
 *
 * MyBatis-Plus 需要可变的实体（无参构造 + getter/setter），所以用 @Data。
 *
 * userId 是画像键（u_<app_user.id>），content 是画像正文（一行一条），
 * updatedAt 是最后更新时间。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@TableName("user_profile")
public class UserProfile
{
    /** 主键由业务方提供（不是自增），所以是 IdType.INPUT */
    @TableId(value = "user_id", type = IdType.INPUT)
    private String userId;

    private String content;

    @TableField("updated_at")
    private LocalDateTime updatedAt;
}
