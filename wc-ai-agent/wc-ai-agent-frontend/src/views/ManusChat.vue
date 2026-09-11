<template>
  <ChatRoom
    title="🤖 AI 超级智能体"
    empty-tip="我是 AI 超级智能体 Manus，可以自主规划并完成复杂任务，快给我下达指令吧！"
    :build-sse-url="buildSseUrl"
  />
</template>

<script setup>
import ChatRoom from '../components/ChatRoom.vue'

// 构造 AI 超级智能体 SSE 接口地址：GET /api/ai/manus/chat?message=xxx
const buildSseUrl = (message) => `/api/ai/manus/chat?message=${encodeURIComponent(message)}`
</script>
