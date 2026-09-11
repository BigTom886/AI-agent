# wc-ai-agent-frontend

AI 应用中心前端项目，包含两个 AI 应用：

- **AI 恋爱大师**（`/love-app`）：聊天室风格，通过 SSE 调用 `GET /api/ai/love_app/chat/sse?message=xxx&chatId=xxx`，进入页面自动生成聊天室 id 区分会话。
- **AI 超级智能体**（`/manus`）：聊天室风格，通过 SSE 调用 `GET /api/ai/manus/chat?message=xxx`，实时显示智能体执行内容。

## 技术选型

- Vue 3 + Vite
- Vue Router
- Axios（普通请求封装，见 `src/utils/request.js`；SSE 流式对话使用原生 `EventSource`，浏览器端 Axios 无法处理流式响应）

## 快速开始

```bash
# 安装依赖
npm install

# 启动开发服务器（默认 http://localhost:5173）
npm run dev

# 构建生产版本
npm run build
```

## 后端接口

接口地址前缀：`http://localhost:8123/api`

开发环境已通过 `vite.config.js` 中的 proxy 将 `/api` 请求代理到后端，无需关心跨域问题。

| 接口 | 方法 | 说明 |
| --- | --- | --- |
| `/api/ai/love_app/chat/sse` | GET | AI 恋爱大师对话（SSE 流式），参数：`message`、`chatId` |
| `/api/ai/manus/chat` | GET | AI 超级智能体对话（SseEmitter 流式），参数：`message` |

## 项目结构

```
src/
├── assets/          # 全局样式
├── components/
│   └── ChatRoom.vue # 公共聊天室组件（两个聊天页共用）
├── router/          # 路由配置
├── utils/
│   ├── chatId.js    # 聊天室 id 生成工具
│   └── request.js   # Axios 请求封装
├── views/
│   ├── Home.vue         # 主页：应用切换
│   ├── LoveAppChat.vue  # 页面 1：AI 恋爱大师
│   └── ManusChat.vue    # 页面 2：AI 超级智能体
├── App.vue
└── main.js
```
