# wc-ai-agent

AI 恋爱顾问「小爱」+ AI 超级智能体的全栈项目。

后端基于 **Spring Boot 3.4.4 + Spring AI 1.1.8**，前端是 **Vue 3 + Vite**，知识库落在 **PostgreSQL + pgvector**，
数据与记忆全部持久化到数据库，生产环境部署在 **微信云托管**。

---

## 一、项目是什么

一个能真正「记住你」的 AI 恋爱顾问：

- **小爱**（恋爱顾问）：28 岁的恋爱顾问人设，帮用户看清关系里的信号、想清楚自己要什么、说出那句合适的话。
  内置恋爱知识库（热恋 / 恋爱中 / 分手三种状态下的应对方法与话术），跨会话用户画像，
  可生成一份结构化的「恋爱报告」。
- **Manus**（超级智能体）：ReAct / ToolCall 两种智能体范式，配套一整套工具
  （文件读写、PDF 生成、终端、网页抓取、联网搜索、搜图搜视频），会自己拆解任务并执行。

> 注意产品边界：小爱不提供操控、打压、冷暴力、情感勒索一类的方法，不做心理疾病诊断，
> 遇到自伤/家暴等风险场景会优先引导求助（详见 `prompts/love-master-system.md` 的「安全底线」）。

---

## 二、核心特性

| 特性 | 说明 |
| --- | --- |
| 跨对话用户画像 | 模型自己决定何时「记住」用户的事，写入 `user_profile` 表；下次对话由 Advisor 注入系统提示词，长期生效 |
| 滚动摘要式对话记忆 | 消息超过阈值时把早期内容压成摘要 + 保留最近若干条原文，摘要与新摘要会合并，信息不丢失 |
| 记忆全部落库 | 对话记忆、用户画像、会话归属全部存 PostgreSQL，不依赖内存，重启不丢 |
| 模型自主决定的 RAG | 恋爱知识库以**工具**形式挂给模型，由模型判断这次要不要检索、怎么改写检索词 |
| Markdown 渲染 | 前端统一按 Markdown 渲染回复，支持代码高亮与引用块 |
| 用户模块 + 历史会话 | 注册/登录（HttpSession）、资料修改、历史会话列表与删除、会话归属校验 |
| MCP 工具接入 | 独立的 MCP 服务端子模块，stdio 方式被主应用拉起，提供 Pexels 搜图/搜视频工具 |
| 一键部署 | Dockerfile 多阶段构建，产物符合微信云托管镜像规范（端口 80） |

---

## 三、技术栈

| 层次 | 选型 |
| --- | --- |
| 语言 / 运行时 | Java 21 |
| 框架 | Spring Boot 3.4.4 |
| AI | Spring AI 1.1.8（`spring-ai-bom` 统一管理版本）、OpenAI 兼容协议（任何兼容服务商均可接入） |
| 记忆 / 存储 | Spring AI `ChatMemory` + JDBC `ChatMemoryRepository`（PostgreSQL） |
| 向量库 | PostgreSQL 17 + pgvector 0.8.2（表 `public.vector_store`） |
| 数据访问 | MyBatis-Plus 3.5.18 + JdbcTemplate |
| 接口文档 | springdoc-openapi 2.8.13 + Knife4j 4.4.0 |
| MCP | `spring-ai-starter-mcp-client`（stdio） |
| 前端 | Vue 3.4 + Vite 5 + Vue Router 4 + Axios + markdown-it + highlight.js |
| 部署 | Docker（maven:3.9-amazoncorretto-21）、微信云托管 |

---

## 四、项目结构

```
wc-ai-agent/
├── pom.xml                        # 父 POM：模块聚合 + spring-ai-bom
├── Dockerfile                     # 多阶段构建，微信云托管镜像（EXPOSE 80）
├── 优化记录.txt                    # 本项目的四条改造记录
├── wc-ai-agent-app/               # 主应用（Spring Boot）
│   └── src/main/
│       ├── java/com/wc/
│       │   ├── app/               # LoveApp：ChatClient 组装、对话、报告
│       │   ├── agent/             # BaseAgent / ReActAgent / ToolCallAgent / YuManus
│       │   ├── chatmemory/        # 滚动摘要记忆、会话归属、消息记录
│       │   ├── rag/               # 向量库配置 + 知识库检索工具
│       │   ├── user/              # 注册登录、身份、用户实体与请求体
│       │   ├── userprofile/       # 跨会话用户画像（Service/Advisor/Tools）
│       │   ├── tools/             # 工具集（文件、PDF、终端、网页、搜索等）
│       │   ├── controller/        # REST 接口 + 全局异常处理
│       │   ├── mapper/            # MyBatis-Plus Mapper
│       │   └── constant/ demo/    # 常量与示例代码
│       └── resources/
│           ├── application.yml            # 公共配置
│           ├── application-local.yml      # 本地配置（gitignore，存真实密钥）
│           ├── application-local.yml.example
│           ├── application-prod.yml       # 生产配置（微信云托管）
│           ├── schema.sql                 # 自建表：app_user / user_profile / chat_conversation
│           └── prompts/                   # 小爱与报告的人设提示词
├── wc-ai-agent-mcp-search/        # MCP 服务端子模块（Pexels 搜图 / 搜视频）
└── wc-ai-agent-frontend/          # Vue 3 前端（非 Maven 模块）
    └── src/
        ├── views/     # AuthView / Home / LoveAppChat / ManusChat / ProfileView
        ├── components/# ChatRoom / MarkdownText / ProfilePanel / ReportPanel
        ├── utils/     # request / chatId / auth / conversations / markdown / error
        ├── router/ assets/
```

---

## 五、快速开始

### 1. 环境要求

- JDK 21
- Maven 3.9+（仓库自带 `mvnw.cmd`）
- Node.js 18+（前端）
- PostgreSQL 14+，**并安装 `vector` 扩展**（RAG 必需）

```sql
CREATE EXTENSION IF NOT EXISTS vector;
```

### 2. 准备数据库

```sql
CREATE DATABASE wc_ai_agent;
```

应用启动时会自动执行 `schema.sql` 建表（`app_user` / `user_profile` / `chat_conversation`），
Spring AI 也会自动建 `SPRING_AI_CHAT_MEMORY` 表。

### 3. 配置密钥

复制本地配置模板：

```bash
cp wc-ai-agent-app/src/main/resources/application-local.yml.example \
   wc-ai-agent-app/src/main/resources/application-local.yml
```

真实密钥只写在 `application-local.yml`（已被 `.gitignore` 忽略），生产环境用环境变量注入。
需要配置：

| 配置项 | 说明 |
| --- | --- |
| `spring.ai.openai.api-key` | 聊天大模型密钥 |
| `spring.ai.openai.base-url` | OpenAI 兼容服务地址（**只写到域名，不要带 `/v1`**） |
| `spring.ai.openai.chat.options.model` | 聊天模型名 |
| `spring.ai.openai.embedding.api-key` | 向量模型密钥（可与聊天不同服务商） |
| `spring.ai.openai.embedding.base-url` | 向量服务地址，默认阿里百炼 OpenAI 兼容模式 |
| `spring.ai.openai.embedding.options.model` | 向量模型名，**必须与灌库时用的是同一个模型**（否则向量空间对不上，检索全是噪声） |
| `spring.datasource.*` | PostgreSQL 连接信息 |

> 维度对齐：知识库里存的是 **1024 维**向量，`PgVectorVectorStoreConfig` 里也是
> `.dimensions(1024)`。换向量模型时必须同步改维度并重新灌库。

### 4. 知识库数据（可选，但 RAG 需要）

知识库本体在 pgvector 的 `public.vector_store` 表里（列：`id` / `content` / `metadata` / `embedding`）。
`metadata` 里存 `title`、`status`（热恋 / 恋爱 / 分手）、`filename` 等信息。
生产环境需要自行把知识库灌进云数据库的同名表。

### 5. 启动后端

```bash
mvnw.cmd clean package -DskipTests
java -jar wc-ai-agent-app/target/wc-ai-agent-app-1.0-SNAPSHOT.jar
```

- 后端端口：`8222`，统一前缀 `/api`（即 `http://localhost:8222/api`）
- 接口文档：`http://localhost:8222/api/swagger-ui.html`（Knife4j 中文版 `doc.html`）
- 健康检查：`GET /api/health`

### 6. 启动前端

```bash
cd wc-ai-agent-frontend
npm install
npm run dev
```

访问 `http://localhost:5173`。开发服务器已配好 `/api` 代理到 `http://localhost:8222`，无需处理跨域。

### 7.（可选）本地 MCP 搜图工具

MCP 服务端以 **stdio 子进程**方式被主应用拉起，需要先把它打成可执行 jar：

```bash
mvnw.cmd -pl wc-ai-agent-mcp-search clean package -DskipTests
```

`application.yml` 里 `spring.ai.mcp.client.stdio.connections.pexels-search.args` 指向该 jar 的绝对路径。
生产环境不启用 MCP（`application-prod.yml` 里排除了 MCP 自动装配）。

---

## 六、核心链路说明

### 6.1 一次普通对话

```
Controller(/ai/love_app/chat/sse)
  → LoveApp.prepare(userText, chatId, userId)
      ├─ MessageChatMemoryAdvisor  按 chatId 取历史 + 本轮落库
      ├─ UserProfileAdvisor        把 user_profile 画像拼进系统提示词
      ├─ profileToolCallbacks      画像工具（模型决定要不要记）
      └─ knowledgeToolCallbacks    知识库工具（模型决定要不要查）
  → ChatClient.call() / .stream()
```

工具调用的执行顺序由 Spring AI 内部控制：模型第一次返回 `tool_calls` →
框架本地执行工具 → 把结果作为 `tool` 消息追加进历史 → 再次请求模型 →
直到模型不再返回 `tool_calls` 为止。**对调用方来说仍然只有一次 API 调用**。

### 6.2 对话记忆压缩（`chatmemory/SummaryChatMemory`）

Spring AI 官方的 `MessageWindowChatMemory` 超窗后会**直接丢弃**旧消息。
本项目换成滚动摘要实现：

```
[老消息…] → 压成一段摘要 → [摘要] + [最近 keep 条原文]
```

下一次压缩时，新摘要会和旧摘要一起合并，信息不会断片。
阈值由 `love.memory.summary-threshold`（默认 20 条）与 `love.memory.summary-keep`（默认 6 条）控制。

### 6.3 知识库检索（RAG）

知识库检索是**工具**，不是 Advisor —— 由模型自己判断这次要不要查。

- 好处：闲聊、打招呼、单纯宣泄情绪时不必付一次向量化 + 检索的成本；
  模型还可以先把口语化的问题改写成更适合检索的短句再查。
- 实现：`rag/LoveAppKnowledgeTools.searchKnowledgeBase(query)` → `PgVectorStore.similaritySearch`；
  召回 `love.rag.top-k`（默认 3）条，相似度阈值 `love.rag.similarity-threshold`（默认 0.5）。
- 兜底：检索异常（向量服务不通、数据库连不上）时工具返回一句人话让模型按自身经验回答，
  不会把异常原文当检索结果念给用户。
- 提示词约定：查到的资料要融进自己的话里回答，不要提「知识库」「检索」等字样。

### 6.4 跨会话用户画像

- `UserProfileTools`：模型调用工具写入/更新画像（如「记住，我和她异地三年了」）；
- `UserProfileAdvisor`：每次对话把画像拼到系统提示词里；
- 画像条数上限 `love.profile.max-items`（默认 30），超出丢最旧的，防止提示词无限膨胀。

---

## 七、接口一览

所有接口都带 `/api` 前缀；除注册/登录/健康检查外，其余接口都要求已登录（HttpSession）。

### 用户模块

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| POST | `/api/user/register` | 注册 |
| POST | `/api/user/login` | 登录 |
| POST | `/api/user/logout` | 登出 |
| GET | `/api/user/me` | 当前用户信息 |
| PUT | `/api/user/me` | 修改昵称 |
| PUT | `/api/user/password` | 修改密码 |

### 恋爱顾问

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| GET | `/api/ai/love_app/chat/sync` | 同步对话 |
| GET | `/api/ai/love_app/chat/sse` | SSE 流式对话（`text/event-stream`） |
| GET | `/api/ai/love_app/chat/sse/emitter` | SSE 流式对话（`SseEmitter` 版） |
| GET | `/api/ai/love_app/history` | 拉取某会话的历史消息 |
| DELETE | `/api/ai/love_app/conversation` | 删除会话（含消息记录） |
| GET | `/api/ai/love_app/profile` | 查看当前用户画像 |
| DELETE | `/api/ai/love_app/profile` | 清空用户画像 |
| GET | `/api/ai/love_app/report` | 生成恋爱报告（标题 + 3~5 条建议） |

### 智能体 / 其他

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| GET | `/api/ai/manus/chat` | Manus 超级智能体对话（流式） |
| GET | `/api/mcp/chat` | MCP 搜图对话（本地/测试用） |
| GET | `/api/health` | 健康检查 |

---

## 八、生产部署（微信云托管）

根目录 `Dockerfile` 是多阶段构建：编译阶段产出 `wc-ai-agent-app` 的 fat jar，
运行阶段以 prod profile 启动，监听 **80** 端口（与云托管要求一致）。

```bash
mvnw.cmd -pl wc-ai-agent-app -am clean package -DskipTests
```

镜像构建与启动：

```dockerfile
FROM maven:3.9-amazoncorretto-21
...
RUN mvn -pl wc-ai-agent-app -am clean package -DskipTests
EXPOSE 80
CMD ["java", "-jar", "/app/wc-ai-agent-app/target/wc-ai-agent-app-1.0-SNAPSHOT.jar", "--spring.profiles.active=prod"]
```

生产环境需要配置的环境变量：

| 变量 | 说明 |
| --- | --- |
| `PG_HOST` `PG_PORT` `PG_DATABASE` `PG_USERNAME` `PG_PASSWORD` | 云数据库连接信息（无默认值，缺失则启动失败） |
| `AI_API_KEY` `AI_BASE_URL` `AI_MODEL` | 聊天大模型 |
| `EMBEDDING_API_KEY` `EMBEDDING_BASE_URL` `EMBEDDING_MODEL` | 向量模型（RAG 必需） |
| `SEARCH_API_KEY` | 联网搜索工具密钥 |

生产环境的几点差异：

- **不启用 MCP 客户端**（排除 MCP 自动装配，不拉起搜图/搜视频子进程）；
- **RAG 照常启用**：要求云数据库装了 `pgvector` 扩展，且 `vector_store` 表已灌好知识库向量；
- **密钥只走环境变量**，不进镜像；
- `context-path` 仍为 `/api`，前端静态资源由云托管的 nginx 托管并反向代理到容器 80 端口。

---

## 九、配置项速查（`love.*`）

| 配置 | 默认值 | 说明 |
| --- | --- | --- |
| `love.memory.summary-threshold` | 20 | 超过多少条消息触发一次压缩 |
| `love.memory.summary-keep` | 6 | 压缩后保留最近多少条原文 |
| `love.profile.max-items` | 30 | 用户画像最多保存多少条 |
| `love.rag.top-k` | 3 | 一次检索召回几个片段 |
| `love.rag.similarity-threshold` | 0.5 | 相似度阈值（越高召回越少） |

---

## 十、常见问题

**Q：启动报 `Could not resolve placeholder 'AI_API_KEY'`**
配置缺失。`application.yml` 里的模型配置没有兜底默认值，是刻意 fail fast。
本地填 `application-local.yml`，生产配环境变量。

**Q：知识库检索结果全是噪声**
向量模型和灌库时不是一个。换了 embedding 模型就必须重新灌库，并同步
`PgVectorVectorStoreConfig` 的 `dimensions`（当前 1024）。

**Q：检索时提示 `知识库暂时检索不到内容（检索服务异常）`**
说明 `EMBEDDING_API_KEY` 不通，或数据库没装 pgvector 扩展。工具内部已兜住异常，
不会把报错原文喂给模型，但对话会退化成「纯经验回答」。

**Q：前端 404 / 接口调不通**
前端开发服务器把 `/api` 代理到 `http://localhost:8222`，确认后端已启动且端口是 8222
（生产是 80）。

---

## 十一、许可

仅供学习与研究使用。
