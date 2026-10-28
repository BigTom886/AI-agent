package com.wc.mapper;

import org.apache.ibatis.annotations.Mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.wc.userprofile.UserProfile;

/**
 * 用户画像 Mapper（MyBatis-Plus）
 *
 * 增删改查由 BaseMapper 提供：
 *   selectById(userId) / insert / updateById / insertOrUpdate / deleteById(userId)
 * 表名、主键、列名映射见 {@link UserProfile} 上的注解。
 *
 * 建表 DDL 不写在 Mapper 里，统一放在 resources/schema.sql。
 *
 * 为什么不用框架的 ChatMemoryRepository？
 * 那个接口是给「对话消息」设计的（conversation_id + 一条条消息），
 * 而画像是「一个用户一整份档案」，语义完全不同，硬套会让以后想加字段时很别扭。
 */
@Mapper
public interface UserProfileMapper extends BaseMapper<UserProfile>
{
}
