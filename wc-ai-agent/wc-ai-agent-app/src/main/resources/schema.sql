-- ============================================================
-- 应用自己的表结构（PostgreSQL）
--
-- 由 Spring Boot 的 SQL 初始化在启动时执行：
--   spring.sql.init.mode = always
--   spring.sql.init.schema-locations = classpath:schema.sql
--
-- 所有语句都是幂等的（IF NOT EXISTS），重复启动不会报错、不会丢数据。
-- 对话记忆表 SPRING_AI_CHAT_MEMORY 由 Spring AI 自己创建，不在这里维护。
-- ============================================================

-- ---------- 用户账号 ----------
CREATE TABLE IF NOT EXISTS app_user
(
    id            BIGSERIAL PRIMARY KEY,
    username      VARCHAR(32)  NOT NULL,
    password_hash VARCHAR(100) NOT NULL,
    nickname      VARCHAR(32)  NOT NULL,
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at    TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- 用户名唯一：并发注册时由数据库兜底，Service 把冲突转成「用户名已存在」。
-- 单独建命名索引（而不是写在 CREATE TABLE 的 UNIQUE 里），
-- 这样历史遗留的 app_user 表也能补上同一个约束。
CREATE UNIQUE INDEX IF NOT EXISTS app_user_username_unique
    ON app_user (username);

-- ---------- 跨会话用户画像 ----------
CREATE TABLE IF NOT EXISTS user_profile
(
    user_id    VARCHAR(64) PRIMARY KEY,
    content    TEXT        NOT NULL,
    updated_at TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- ---------- 会话归属 ----------
-- chatId 是前端生成的 UUID，靠这张表绑定到具体用户：
-- 读历史、删会话前都要先校验是不是自己的。
CREATE TABLE IF NOT EXISTS chat_conversation
(
    chat_id    VARCHAR(64) PRIMARY KEY,
    user_id    BIGINT      NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- 方便按用户查自己的会话
CREATE INDEX IF NOT EXISTS chat_conversation_user_idx
    ON chat_conversation (user_id);
