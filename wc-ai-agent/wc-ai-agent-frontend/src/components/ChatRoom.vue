<template>
  <div class="chat-page">
    <!-- 顶部栏 -->
    <header class="chat-header">
      <button class="back-btn" @click="goHome">← 返回主页</button>
      <div class="chat-title">
        <h2>{{ title }}</h2>
        <span v-if="chatId" class="chat-id">聊天室 ID：{{ chatId }}</span>
      </div>
      <button class="new-btn" @click="resetSession">新会话</button>
    </header>

    <!-- 聊天记录区域 -->
    <main class="chat-body" ref="chatBodyRef">
      <div v-if="messages.length === 0" class="chat-empty">
        <div class="empty-icon">💬</div>
        <p>{{ emptyTip }}</p>
      </div>

      <div
        v-for="(msg, index) in messages"
        :key="index"
        class="chat-message"
        :class="msg.role === 'user' ? 'message-right' : 'message-left'"
      >
        <div class="avatar">{{ msg.role === 'user' ? '🧑' : '🤖' }}</div>
        <div class="bubble" :class="msg.role === 'user' ? 'bubble-user' : 'bubble-ai'">
          <span class="bubble-text">{{ msg.content }}</span>
          <span v-if="msg.loading" class="cursor">▍</span>
        </div>
      </div>
    </main>

    <!-- 输入框区域 -->
    <footer class="chat-footer">
      <input
        v-model="inputMessage"
        class="chat-input"
        type="text"
        :placeholder="loading ? 'AI 正在回复中，请稍候…' : '请输入你的问题，按回车发送'"
        :disabled="loading"
        @keydown.enter="sendMessage"
      />
      <button class="send-btn" :disabled="loading || !inputMessage.trim()" @click="sendMessage">
        {{ loading ? '回复中…' : '发送' }}
      </button>
    </footer>
  </div>
</template>

<script setup>
import { ref, nextTick } from 'vue'
import { useRouter } from 'vue-router'

const props = defineProps({
  // 页面标题
  title: {
    type: String,
    required: true,
  },
  // 空页面提示
  emptyTip: {
    type: String,
    default: '开始你的第一次对话吧！',
  },
  // 聊天室 ID（可选，用于区分会话）
  chatId: {
    type: String,
    default: '',
  },
  /**
   * 根据 message 构造 SSE 请求地址
   * @param {string} message 用户消息
   * @returns {string} SSE 接口 URL
   */
  buildSseUrl: {
    type: Function,
    required: true,
  },
  // 会话重置回调（如重新生成 chatId）
  onReset: {
    type: Function,
    default: null,
  },
})

const router = useRouter()

// 消息列表：{ role: 'user' | 'ai', content: string, loading?: boolean }
const messages = ref([])
const inputMessage = ref('')
const loading = ref(false)
const chatBodyRef = ref(null)

// 当前 SSE 连接，用于中断
let eventSource = null

// 滚动到底部
const scrollToBottom = () => {
  nextTick(() => {
    if (chatBodyRef.value) {
      chatBodyRef.value.scrollTop = chatBodyRef.value.scrollHeight
    }
  })
}

// 关闭已有连接
const closeEventSource = () => {
  if (eventSource) {
    eventSource.close()
    eventSource = null
  }
}

// 发送消息（通过 SSE 实时接收 AI 回复）
const sendMessage = () => {
  const message = inputMessage.value.trim()
  if (!message || loading.value) return

  // 关闭上一次未完成的连接
  closeEventSource()

  // 添加用户消息
  messages.value.push({ role: 'user', content: message })
  inputMessage.value = ''

  // 添加 AI 占位消息，后续流式填充
  const aiMessage = { role: 'ai', content: '', loading: true }
  messages.value.push(aiMessage)
  loading.value = true
  scrollToBottom()

  // 通过 EventSource 调用 SSE 接口，实时接收对话内容
  eventSource = new EventSource(props.buildSseUrl(message))

  eventSource.onmessage = (event) => {
    if (event.data === '[DONE]') {
      finishLoading()
      return
    }
    aiMessage.content += event.data
    scrollToBottom()
  }

  eventSource.onerror = (event) => {
    // 服务端正常结束连接时也会触发 error，若已有内容则视为正常结束
    finishLoading()
    if (!aiMessage.content) {
      aiMessage.content = 'AI 回复失败，请稍后重试。'
    }
    console.warn('SSE 连接关闭：', event)
  }
}

const finishLoading = () => {
  loading.value = false
  const last = messages.value[messages.value.length - 1]
  if (last) last.loading = false
  closeEventSource()
}

// 开启新会话：清空记录，并按需重新生成 chatId
const resetSession = () => {
  closeEventSource()
  messages.value = []
  inputMessage.value = ''
  loading.value = false
  if (props.onReset) props.onReset()
}

// 返回主页
const goHome = () => {
  closeEventSource()
  router.push('/')
}
</script>

<style scoped>
.chat-page {
  height: 100%;
  display: flex;
  flex-direction: column;
  max-width: 900px;
  margin: 0 auto;
  background: #fff;
  box-shadow: 0 0 24px rgba(0, 0, 0, 0.05);
}

/* 顶部栏 */
.chat-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 12px 20px;
  border-bottom: 1px solid #eef0f4;
  background: #fff;
}

.chat-title {
  text-align: center;
}

.chat-title h2 {
  font-size: 18px;
}

.chat-id {
  font-size: 12px;
  color: #9aa4b2;
}

.back-btn,
.new-btn {
  background: #f2f4f8;
  color: #4a5568;
  padding: 8px 14px;
  border-radius: 8px;
  font-size: 13px;
  transition: background 0.2s;
}

.back-btn:hover,
.new-btn:hover {
  background: #e4e8ef;
}

/* 聊天记录区域 */
.chat-body {
  flex: 1;
  overflow-y: auto;
  padding: 20px;
  background: #f7f8fc;
}

.chat-empty {
  height: 100%;
  display: flex;
  flex-direction: column;
  justify-content: center;
  align-items: center;
  color: #9aa4b2;
}

.empty-icon {
  font-size: 48px;
  margin-bottom: 12px;
}

.chat-message {
  display: flex;
  align-items: flex-start;
  margin-bottom: 16px;
  gap: 10px;
}

.message-right {
  flex-direction: row-reverse;
}

.avatar {
  width: 38px;
  height: 38px;
  border-radius: 50%;
  background: #fff;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 20px;
  box-shadow: 0 2px 6px rgba(0, 0, 0, 0.08);
  flex-shrink: 0;
}

.bubble {
  max-width: 70%;
  padding: 10px 14px;
  border-radius: 12px;
  font-size: 14px;
  line-height: 1.7;
  white-space: pre-wrap;
  word-break: break-word;
}

.bubble-user {
  background: #4a7cff;
  color: #fff;
  border-top-right-radius: 4px;
}

.bubble-ai {
  background: #fff;
  color: #2c3e50;
  border-top-left-radius: 4px;
  box-shadow: 0 2px 6px rgba(0, 0, 0, 0.05);
}

.cursor {
  animation: blink 1s step-start infinite;
}

@keyframes blink {
  50% {
    opacity: 0;
  }
}

/* 输入框区域 */
.chat-footer {
  display: flex;
  gap: 12px;
  padding: 14px 20px;
  border-top: 1px solid #eef0f4;
  background: #fff;
}

.chat-input {
  flex: 1;
  padding: 12px 16px;
  border: 1px solid #dfe3ea;
  border-radius: 10px;
  font-size: 14px;
  outline: none;
  transition: border-color 0.2s;
}

.chat-input:focus {
  border-color: #4a7cff;
}

.send-btn {
  padding: 0 24px;
  border-radius: 10px;
  background: #4a7cff;
  color: #fff;
  font-size: 14px;
  transition: opacity 0.2s;
}

.send-btn:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}
</style>
