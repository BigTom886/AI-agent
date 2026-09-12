# wc-ai-agent

基于 **Spring AI 1.1 + Spring Boot 3.4** 的 AI Agent 实战项目：包含一个带多轮对话记忆与 RAG 知识库的「AI 恋爱大师」应用、一个可自主调用多种工具的「YuManus 超级智能体」（ReAct 循环）、一个以 **MCP（Model Context Protocol）** 方式接入的 Pexels 搜图/搜视频服务，以及配套的 Vue 3 聊天前端。支持 SSE 流式输出，可容器化部署到微信云托管。

## 功能特性

- **AI 恋爱大师**：ChatClient + 滑动窗口对话记忆（含本地文件记忆实现），基于 pgvector 的 RAG 恋爱 FAQ 知识库，SSE 逐 token 流式回复
- **YuManus 超级智能体**：自研 ReAct / Tool-Calling 智能体抽象（`BaseAgent → ReActAgent → ToolCallAgent → YuManus`），最多 20 步思考-行动循环，可自主组合调用多种工具
- **内置工具箱**：联网搜索（SearchAPI）、网页抓取（jsoup）、文件读写、终端命令执行、资源下载、Markdown/HTML → PDF 生成（iText，支持中文与 WebP 图片）、任务终止
- **MCP 搜图服务**：独立的 MCP Server 模块，封装 Pexels 图片/视频搜索，主应用通过 **stdio** 以子进程方式拉起，无需占用 HTTP 端口
- **Vue 3 前端**：聊天室式 UI，`EventSource` 接收 SSE 流式回复，两个应用一键切换
- **接口文档**：Knife4j / springdoc-openapi（Swagger UI）
- **生产裁剪部署**：prod profile 下排除数据库、向量库、embedding、MCP 自动装配，密钥全部走环境变量

## 架构概览

```
┌──────────────────────────────┐
│   wc-ai-agent-frontend       │  Vue 3 + Vite（:5173 / Nginx :80）
│   /love-app   /manus         │  EventSource (SSE)
└───────────────┬──────────────┘
                │ /api/**
┌───────────────▼──────────────┐
│   wc-ai-agent-app            │  Spring Boot 3.4 / Java 21（:8222, context-path=/api）
│                              │
│  LoveApp（ChatClient + 记忆） │──► 阿里云 DashScope（OpenAI 兼容模式）
│  YuManus（ReAct 智能体）      │
│  本地工具 × 7                │
│  RAG Advisor                 │──► PostgreSQL + pgvector（:5432）
│  MCP Client（stdio）         │
└───────────────┬──────────────┘
                │ stdio JSON-RPC（自动拉起子进程）
┌───────────────▼──────────────┐
│  wc-ai-agent-mcp-search      │  MCP Server（无 Web 容器）
│  Pexels 搜图 / 搜视频工具      │──► https://api.pexels.com
└──────────────────────────────┘
```

## 技术栈

| 层 | 技术 |
| --- | --- |
| 后端框架 | Java 21、Spring Boot 3.4.4、Spring AI 1.1.8 |
| 大模型 | 阿里云 DashScope（OpenAI 兼容接口） |
| RAG / 向量库 | Spring AI RAG、PostgreSQL + pgvector（HNSW，1024 维，余弦距离） |
| MCP | spring-ai MCP Server / Client（stdio 传输） |
| 工具相关 | jsoup、iText 9 + html2pdf + flexmark、Hutool |
| 接口文档 | Knife4j 4.4、springdoc-openapi 2.8 |
| 前端 | Vue 3、Vite 5、Vue Router 4、Axios |
| 部署 | Docker、Nginx、微信云托管 |

## 项目结构

```
wc-ai-agent/
├── wc-ai-agent-app/            # 主应用（Spring Boot Web 服务）
│   └── src/main/java/com/wc/
│       ├── agent/              # 智能体抽象：BaseAgent / ReActAgent / ToolCallAgent / YuManus
│       ├── app/LoveApp.java    # AI 恋爱大师：ChatClient + 记忆 + RAG
│       ├── tools/              # @Tool 工具集与统一注册（本地工具 + MCP 工具汇总）
│       ├── rag/                # RAG：文档加载、切分器、Advisor 工厂、向量库配置
│       ├── chatmemory/         # 基于文件的对话记忆实现
│       ├── controller/         # SSE 对话 / MCP 搜图 / 健康检查接口
│       └── demo/               # 学习过程中的 Spring AI 调用、Advisor、RAG 示例
├── wc-ai-agent-mcp-search/     # MCP Server：Pexels 搜图/搜视频（stdio）
├── wc-ai-agent-frontend/       # Vue 3 前端（/love-app、/manus 两个聊天页）
├── docs/                       # 设计/学习笔记
└── Dockerfile                  # 后端镜像（微信云托管，prod profile，端口 80）
```

## 环境要求

- JDK 21、Maven 3.9+
- Node.js 18+、npm
- PostgreSQL 14+，并安装 [pgvector](https://github.com/pgvector/pgvector) 扩展
- 三个 API Key（均有免费额度）：
  - **DASHSCOPE_API_KEY**：[阿里云百炼 DashScope](https://bailian.console.aliyun.com/) 密钥
  - **SEARCH_API_KEY**：[SearchAPI.io](https://www.searchapi.io/) 密钥（联网搜索工具）
  - **PEXELS_API_KEY**：[Pexels API](https://www.pexels.com/api/) 密钥（MCP 搜图服务）

## 快速开始

### 1. 准备 PostgreSQL

```sql
CREATE DATABASE wc_ai_agent;
\c wc_ai_agent
CREATE EXTENSION IF NOT EXISTS vector;
```

默认连接 `localhost:5432/wc_ai_agent`，用户名/密码见 `wc-ai-agent-app/src/main/resources/application.yml`，按需修改。

### 2. 配置本地密钥（不会提交 Git）

两个模块都提供了模板，复制为 `application-local.yml` 后填入真实 key：

```powershell
Copy-Item wc-ai-agent-app/src/main/resources/application-local.yml.example wc-ai-agent-app/src/main/resources/application-local.yml
Copy-Item wc-ai-agent-mcp-search/src/main/resources/application-local.yml.example wc-ai-agent-mcp-search/src/main/resources/application-local.yml
```

也可以不建文件，直接设置同名环境变量：`DASHSCOPE_API_KEY`、`SEARCH_API_KEY`（主应用）、`PEXELS_API_KEY`（MCP 服务）。

### 3. 构建 MCP 搜图服务

主应用以 stdio 方式拉起该模块的可执行 jar，需先打包：

```powershell
mvn -pl wc-ai-agent-mcp-search -am clean package
```

> 注意：jar 路径目前硬编码在主应用 `application.yml` 的
> `spring.ai.mcp.client.stdio.connections.pexels-search.args` 中（默认 `D:/Study/AI-agent/...`），
> 克隆到本地后请改成你自己的绝对路径。

### 4. 启动后端

```powershell
mvn -pl wc-ai-agent-app spring-boot:run
```

后端默认监听 `http://localhost:8222/api`（local profile）。
接口文档：Knife4j `http://localhost:8222/api/doc.html`，Swagger UI `http://localhost:8222/api/swagger-ui.html`。

### 5. 启动前端

```powershell
cd wc-ai-agent-frontend
npm install
npm run dev
```

打开 `http://localhost:5173`：

- `/` 应用选择页
- `/love-app` AI 恋爱大师（按聊天室 id 隔离多轮会话）
- `/manus` YuManus 超级智能体（实时展示思考与工具执行过程）

> 前端开发代理在 `wc-ai-agent-frontend/vite.config.js` 中配置，当前转发目标为
> `http://localhost:80`，本地联调后端（8222）时请改成对应端口。

## HTTP 接口

统一前缀 `/api`：

| 接口 | 方法 | 说明 |
| --- | --- | --- |
| `/health` | GET | 健康检查，返回 `ok` |
| `/ai/love_app/chat/sync` | GET | 恋爱大师同步对话，参数 `message`、`chatId` |
| `/ai/love_app/chat/sse` | GET | 恋爱大师流式对话（Flux + SSE） |
| `/ai/love_app/chat/sse/emitter` | GET | 恋爱大师流式对话（SseEmitter 手动推送示例） |
| `/ai/manus/chat` | GET | YuManus 智能体流式执行，参数 `message` |
| `/mcp/chat` | GET | 自然语言驱动 MCP 工具（模型自行决定搜图/搜视频） |

## 部署（微信云托管）

- 后端：根目录 `Dockerfile` 多阶段在 Maven 镜像内构建，以 `--spring.profiles.active=prod` 启动，监听 **80** 端口
- prod profile 排除数据源、pgvector、embedding、MCP 客户端自动装配（云端只跑基础对话/智能体），密钥由环境变量 `DASHSCOPE_API_KEY`、`SEARCH_API_KEY` 注入
- 前端：`wc-ai-agent-frontend/Dockerfile` 基于 Nginx 1.27（不能用 alpine，平台证书钩子依赖 glibc），构建产物 `dist/` 打入镜像，通过 `BACKEND_UPSTREAM` 环境变量指向同环境后端服务名

## 说明

- `application-local.yml`、`chat_memory/`、`target/`、`node_modules/` 等均已在 `.gitignore` 中，密钥不会进入版本库
- `com.wc.demo` 包为学习 Spring AI 时的示例代码（ChatClient 调用、Advisor、RAG、Re2 等），不影响主功能
