<template>
  <div class="chat-page" :style="{ '--accent': accent }">
    <!-- 顶部栏 -->
    <header class="chat-header">
      <button class="header-btn back-btn" @click="goHome">
        <span class="btn-icon">←</span>
        <span class="btn-text">主页</span>
      </button>
      <div class="chat-title">
        <span class="title-icon">{{ icon }}</span>
        <div class="title-text">
          <h2>{{ title }}</h2>
          <span v-if="chatId" class="chat-id" :title="chatId">会话 ID：{{ chatId }}</span>
        </div>
      </div>
      <button class="header-btn new-btn" @click="resetSession">
        <span class="btn-icon">＋</span>
        <span class="btn-text">新会话</span>
      </button>
    </header>

    <!-- 聊天记录区域 -->
    <main class="chat-body" ref="chatBodyRef">
      <div v-if="messages.length === 0" class="chat-empty">
        <div class="empty-avatar">{{ icon }}</div>
        <p class="empty-title">{{ title }}</p>
        <p class="empty-tip">{{ emptyTip }}</p>
      </div>

      <div
        v-for="(msg, index) in messages"
        :key="index"
        class="chat-message"
        :class="msg.role === 'user' ? 'message-right' : 'message-left'"
      >
        <div class="avatar" :class="msg.role === 'ai' ? 'avatar-ai' : 'avatar-user'">
          {{ msg.role === 'user' ? '🧑' : icon }}
        </div>
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
import { ref, reactive, nextTick } from 'vue'
import { useRouter } from 'vue-router'

const props = defineProps({
  // 页面标题
  title: {
    type: String,
    required: true,
  },
  // AI 头像（emoji），每个应用可配置不同的默认头像
  icon: {
    type: String,
    default: '🤖',
  },
  // 应用主题色
  accent: {
    type: String,
    default: '#4a7cff',
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
  // 每条 SSE 消息末尾追加一个换行（适用于分步响应的智能体场景）
  appendNewline: {
    type: Boolean,
    default: false,
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
  // 注意：必须用 reactive 包裹，否则直接修改原始对象不会触发视图更新
  const aiMessage = reactive({ role: 'ai', content: '', loading: true })
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
    // 分步响应场景（如智能体）：每完成一步响应一次，每条消息后追加换行区分步骤
    aiMessage.content += props.appendNewline ? event.data + '\n' : event.data
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
  height: 100dvh;
  display: flex;
  flex-direction: column;
  max-width: 960px;
  margin: 0 auto;
  background: var(--bg-card);
  box-shadow: 0 0 24px rgba(0, 0, 0, 0.05);
}

/* 顶部栏 */
.chat-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 12px 20px;
  border-bottom: 1px solid var(--border-color);
  background: var(--bg-card);
  flex-shrink: 0;
}

.chat-title {
  display: flex;
  align-items: center;
  gap: 10px;
  min-width: 0;
}

.title-icon {
  font-size: 26px;
}

.title-text {
  display: flex;
  flex-direction: column;
  min-width: 0;
}

.chat-title h2 {
  font-size: 17px;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.chat-id {
  font-size: 12px;
  color: var(--text-light);
  max-width: 40vw;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.header-btn {
  display: flex;
  align-items: center;
  gap: 4px;
  background: #f2f4f8;
  color: #4a5568;
  padding: 8px 14px;
  border-radius: var(--radius-sm);
  font-size: 13px;
  transition: background 0.2s;
  flex-shrink: 0;
}

.header-btn:hover {
  background: #e4e8ef;
}

/* 聊天记录区域 */
.chat-body {
  flex: 1;
  overflow-y: auto;
  padding: 24px 20px;
  background: var(--bg-chat);
}

.chat-empty {
  height: 100%;
  display: flex;
  flex-direction: column;
  justify-content: center;
  align-items: center;
  color: var(--text-light);
  text-align: center;
  padding: 0 24px;
}

.empty-avatar {
  width: 80px;
  height: 80px;
  font-size: 44px;
  border-radius: 50%;
  background: var(--bg-card);
  box-shadow: var(--shadow-md);
  display: flex;
  align-items: center;
  justify-content: center;
  margin-bottom: 16px;
}

.empty-title {
  font-size: 18px;
  font-weight: 600;
  color: var(--text-sub);
  margin-bottom: 8px;
}

.empty-tip {
  font-size: 14px;
  line-height: 1.6;
  max-width: 320px;
}

.chat-message {
  display: flex;
  align-items: flex-start;
  margin-bottom: 18px;
  gap: 10px;
}

.message-right {
  flex-direction: row-reverse;
}

.avatar {
  width: 38px;
  height: 38px;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 20px;
  flex-shrink: 0;
}

.avatar-user {
  background: var(--bg-card);
  box-shadow: var(--shadow-sm);
}

.avatar-ai {
  background: var(--bg-card);
  border: 2px solid var(--accent);
}

.bubble {
  max-width: min(70%, 620px);
  padding: 12px 16px;
  border-radius: 20px;
  font-size: 14px;
  line-height: 1.7;
  white-space: pre-wrap;
  word-break: break-word;
  text-align: left;
}

.bubble-user {
  background: var(--accent);
  color: #fff;
  border-top-right-radius: 4px;
}

.bubble-ai {
  background: var(--bg-card);
  color: var(--text-main);
  border-top-left-radius: 4px;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.05);
}

.cursor {
  animation: blink 1s step-start infinite;
  color: var(--accent);
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
  border-top: 1px solid var(--border-color);
  background: var(--bg-card);
  flex-shrink: 0;
}

.chat-input {
  flex: 1;
  min-width: 0;
  padding: 12px 16px;
  border: 1px solid #dfe3ea;
  border-radius: 10px;
  font-size: 14px;
  outline: none;
  transition: border-color 0.2s;
  background: var(--bg-chat);
}

.chat-input:focus {
  border-color: var(--accent);
  background: var(--bg-card);
}

.send-btn {
  padding: 0 24px;
  border-radius: 10px;
  background: var(--accent);
  color: #fff;
  font-size: 14px;
  transition: opacity 0.2s;
  flex-shrink: 0;
}

.send-btn:hover:not(:disabled) {
  opacity: 0.9;
}

.send-btn:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}

/* ===== 平板适配 ===== */
@media (max-width: 768px) {
  .chat-body {
    padding: 16px 14px;
  }

  .bubble {
    max-width: 78%;
  }

  .btn-text {
    display: none;
  }

  .header-btn {
    padding: 8px 12px;
  }
}

/* ===== 手机适配 ===== */
@media (max-width: 480px) {
  .chat-header {
    padding: 10px 12px;
  }

  .chat-footer {
    padding: 10px 12px;
    gap: 8px;
  }

  .chat-body {
    padding: 12px 10px;
  }

  .chat-message {
    gap: 8px;
    margin-bottom: 14px;
  }

  .avatar {
    width: 32px;
    height: 32px;
    font-size: 17px;
  }

  .bubble {
    max-width: 82%;
    font-size: 13px;
    padding: 9px 12px;
  }

  .title-icon {
    font-size: 22px;
  }

  .chat-title h2 {
    font-size: 15px;
  }

  .chat-id {
    max-width: 36vw;
  }

  .send-btn {
    padding: 0 16px;
  }
}
</style>
