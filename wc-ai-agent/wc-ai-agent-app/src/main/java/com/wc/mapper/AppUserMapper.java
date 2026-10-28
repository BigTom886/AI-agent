package com.wc.mapper;

import org.apache.ibatis.annotations.Mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.wc.user.AppUser;

/**
 * 用户账号 Mapper（MyBatis-Plus）
 *
 * 继承 BaseMapper 后，增删改查全部由 MyBatis-Plus 自动生成：
 *   insert / selectById / selectOne(Wrapper) / updateById / deleteById ...
 * 表名、主键、列名映射见 {@link AppUser} 上的 @TableName / @TableId / @TableField。
 *
 * 建表、唯一索引等 DDL 不写在 Mapper 里，
 * 统一放在 resources/schema.sql，由 Spring Boot 启动时执行。
 */
@Mapper
public interface AppUserMapper extends BaseMapper<AppUser>
{
}
