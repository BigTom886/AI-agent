package com.wc.chatmemory;

import java.time.OffsetDateTime;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 会话归属（对应 chat_conversation 表）
 *
 * chatId 是前端生成的 UUID，本来谁拿到都能用；
 * 这张表把「会话」和「用户」绑在一起：
 *   聊天时登记归属，之后读取/删除会话都要先校验是不是自己的。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@TableName("chat_conversation")
public class ChatConversation
{
    /** 会话 ID（前端生成的 UUID，由业务方提供，不是自增） */
    @TableId(value = "chat_id", type = IdType.INPUT)
    private String chatId;

    /** 拥有这个会话的用户（app_user.id） */
    @TableField("user_id")
    private Long userId;

    @TableField("created_at")
    private OffsetDateTime createdAt;

    @TableField("updated_at")
    private OffsetDateTime updatedAt;
}
