<template>
  <div class="love-page-wrapper">
    <ChatRoom
      :key="chatId"
      title="AI 恋爱大师"
      icon="💘"
      accent="#ff6b81"
      empty-tip="我是你的专属恋爱大师，有什么情感问题想问我吗？"
      :chat-id="chatId"
      :build-sse-url="buildSseUrl"
      :on-reset="regenerateChatId"
    />
    <div class="love-bg-decoration">
      <span class="heart heart-1">💗</span>
      <span class="heart heart-2">💕</span>
      <span class="heart heart-3">💖</span>
      <span class="heart heart-4">💝</span>
      <span class="heart heart-5">💘</span>
    </div>
  </div>
</template>

<script setup>
import { ref } from 'vue'
import ChatRoom from '../components/ChatRoom.vue'
import { generateChatId } from '../utils/chatId'

// 进入页面自动生成一个聊天室 id，用于区分不同的会话
const chatId = ref(generateChatId())

const regenerateChatId = () => {
  chatId.value = generateChatId()
}

// 构造 AI 恋爱大师 SSE 接口地址：GET /api/ai/love_app/chat/sse?message=xxx&chatId=xxx
const buildSseUrl = (message) =>
  `/api/ai/love_app/chat/sse?message=${encodeURIComponent(message)}&chatId=${encodeURIComponent(chatId.value)}`
</script>

<style scoped>
.love-page-wrapper {
  position: relative;
  height: 100%;
  background: radial-gradient(
    circle at top,
    rgba(255, 229, 236, 0.6) 0%,
    transparent 60%
  );
  overflow: hidden;
}

.love-bg-decoration {
  position: fixed;
  inset: 0;
  pointer-events: none;
  z-index: 0;
}

.heart {
  position: absolute;
  font-size: 40px;
  opacity: 0.08;
  animation: float 20s ease-in-out infinite;
}

.heart-1 {
  top: 10%;
  left: 5%;
  animation-delay: 0s;
}
.heart-2 {
  top: 70%;
  right: 8%;
  animation-delay: 3s;
}
.heart-3 {
  bottom: 15%;
  left: 10%;
  animation-delay: 6s;
}
.heart-4 {
  top: 40%;
  right: 15%;
  animation-delay: 9s;
}
.heart-5 {
  top: 20%;
  right: 40%;
  animation-delay: 2s;
}

@keyframes float {
  0%, 100% {
    transform: translate(0, 0) rotate(0deg) scale(1);
  }
  25% {
    transform: translate(10px, -20px) rotate(5deg) scale(1.1);
  }
  50% {
    transform: translate(-15px, 10px) rotate(-5deg) scale(0.95);
  }
  75% {
    transform: translate(8px, 15px) rotate(3deg) scale(1.05);
  }
}

/* 确保聊天容器在装饰之上 */
:deep(.chat-page) {
  position: relative;
  z-index: 1;
  background: rgba(255, 255, 255, 0.92);
  backdrop-filter: blur(8px);
}

/* ===== 适配深色手机状态栏等 ===== */
@media (max-width: 480px) {
  .love-bg-decoration {
    display: none;
  }
}
</style>
