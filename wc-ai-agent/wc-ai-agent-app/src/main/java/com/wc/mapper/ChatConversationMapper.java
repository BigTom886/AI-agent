package com.wc.mapper;

import org.apache.ibatis.annotations.Mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.wc.chatmemory.ChatConversation;

/**
 * 会话归属 Mapper（MyBatis-Plus）
 *
 * 建表 DDL 在 resources/schema.sql 里。
 */
@Mapper
public interface ChatConversationMapper extends BaseMapper<ChatConversation>
{
}
