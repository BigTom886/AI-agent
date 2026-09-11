<template>
  <ChatRoom
    :key="chatId"
    title="💘 AI 恋爱大师"
    empty-tip="我是你的专属恋爱大师，有什么情感问题想问我吗？"
    :chat-id="chatId"
    :build-sse-url="buildSseUrl"
    :on-reset="regenerateChatId"
  />
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
