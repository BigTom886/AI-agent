package com.wc.chatmemory;

import java.time.LocalDateTime;

/**
 * 对话消息记录（带入库时间），用于历史记录接口返回
 *
 * @param role 消息角色：USER / ASSISTANT / SYSTEM
 * @param content 消息正文
 * @param createdAt 入库时间
 */
public record ChatMessageRecord(String role, String content, LocalDateTime createdAt)
{
}
