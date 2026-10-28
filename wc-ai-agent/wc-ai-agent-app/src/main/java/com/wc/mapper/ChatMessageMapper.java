package com.wc.mapper;

import java.time.LocalDateTime;
import java.util.List;

import org.apache.ibatis.annotations.Arg;
import org.apache.ibatis.annotations.ConstructorArgs;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import com.wc.chatmemory.ChatMessageRecord;

/**
 * 对话消息的 Mapper（MyBatis 注解 SQL）
 *
 * 查的是 Spring AI 框架自己建的表 SPRING_AI_CHAT_MEMORY（PostgreSQL 里未加引号的标识符
 * 会折叠成小写，所以实际表名是 spring_ai_chat_memory），列结构：
 *   conversation_id VARCHAR(36)  会话 ID
 *   content         TEXT          消息正文
 *   type            VARCHAR(10)   消息角色：USER / ASSISTANT / SYSTEM / TOOL
 *   "timestamp"     TIMESTAMP     入库时间（timestamp 是 SQL 关键字，必须加双引号）
 *
 * 为什么这个 Mapper 不继承 BaseMapper？
 * 这是框架管理的表：同一个会话有多条消息，没有单列主键（conversation_id 会重复），
 * 而且 timestamp 是 SQL 关键字。BaseMapper 的 CRUD 依赖单一主键与可写实体，
 * 套上去只会误导；这里只读、只有三条固定查询，用注解 SQL 最直接。
 * 应用自己的表（app_user / user_profile）则使用 MyBatis-Plus 的 BaseMapper。
 *
 * 为什么要自己查这张表？
 * 框架的 ChatMemoryRepository 只提供「按会话取消息」，取回来的 Message 上不带入库时间，
 * 而历史记录接口要返回时间（前端用来排版）。所以这里直接查表。
 *
 * 写入仍然交给框架（JdbcChatMemoryRepository），这里只读。
 */
@Mapper
public interface ChatMessageMapper
{
    /**
     * 按会话 ID 查历史消息（按时间正序）
     *
     * @param conversationId 会话 ID
     * @return 消息列表；没有消息时返回空列表
     */
    @Select("""
            SELECT type, content, "timestamp"
            FROM spring_ai_chat_memory
            WHERE conversation_id = #{conversationId}
            ORDER BY "timestamp"
            """)
    @ConstructorArgs({
            @Arg(column = "type", javaType = String.class),
            @Arg(column = "content", javaType = String.class),
            @Arg(column = "timestamp", javaType = LocalDateTime.class)
    })
    List<ChatMessageRecord> findByConversationId(@Param("conversationId") String conversationId);

    /**
     * 统计某个会话的消息条数
     *
     * @param conversationId 会话 ID
     * @return 条数
     */
    @Select("SELECT COUNT(*) FROM spring_ai_chat_memory WHERE conversation_id = #{conversationId}")
    int countByConversationId(@Param("conversationId") String conversationId);

    /**
     * 取某个会话的第一条消息角色
     *
     * @param conversationId 会话 ID
     * @return 角色名（USER / ASSISTANT / SYSTEM / TOOL）；没有消息时返回 null
     */
    @Select("""
            SELECT type
            FROM spring_ai_chat_memory
            WHERE conversation_id = #{conversationId}
            LIMIT 1
            """)
    String findFirstType(@Param("conversationId") String conversationId);
}
