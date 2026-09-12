<template>
  <div class="manus-page-wrapper">
    <ChatRoom
      title="AI 超级智能体"
      icon="🤖"
      accent="#4a7cff"
      empty-tip="我是 AI 超级智能体 Manus，可以自主规划并完成复杂任务，快给我下达指令吧！"
      :build-sse-url="buildSseUrl"
      :append-newline="true"
    />
  </div>
</template>

<script setup>
import ChatRoom from '../components/ChatRoom.vue'

// 构造 AI 超级智能体 SSE 接口地址：GET /api/ai/manus/chat?message=xxx
const buildSseUrl = (message) => `/api/ai/manus/chat?message=${encodeURIComponent(message)}`
</script>

<style scoped>
.manus-page-wrapper {
  position: relative;
  height: 100%;
  background: radial-gradient(
    circle at top,
    rgba(214, 233, 255, 0.6) 0%,
    transparent 60%
  );
  overflow: hidden;
}

/* 确保聊天容器在装饰之上 */
:deep(.chat-page) {
  position: relative;
  z-index: 1;
  background: rgba(255, 255, 255, 0.92);
  backdrop-filter: blur(8px);
}
</style>
