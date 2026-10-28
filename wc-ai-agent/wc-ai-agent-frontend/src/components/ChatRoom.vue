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
        <p class="empty-tip">{{ loadingHistory ? '正在加载历史记录…' : emptyTip }}</p>
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
          <!-- 用户消息：原样显示，保留输入的换行 -->
          <span v-if="msg.role === 'user'" class="bubble-text">{{ msg.content }}</span>
          <!-- AI 消息：按 Markdown 渲染（标题、列表、代码块、表格……） -->
          <MarkdownText v-else :content="msg.content" :streaming="msg.loading" />
        </div>
      </div>
    </main>

    <!-- 输入框区域 -->
    <footer class="chat-footer">
      <textarea
        ref="inputRef"
        v-model="inputMessage"
        class="chat-input"
        rows="1"
        :placeholder="loading ? 'AI 正在回复中，请稍候…' : '请输入你的问题，Enter 发送，Shift+Enter 换行'"
        :disabled="loading"
        @input="autoResize"
        @keydown="onKeydown"
        @compositionstart="isComposing = true"
        @compositionend="isComposing = false"
      />
      <button class="send-btn" :disabled="loading || !inputMessage.trim()" @click="sendMessage">
        {{ loading ? '回复中…' : '发送' }}
      </button>
    </footer>
  </div>
</template>

<script setup>
import { ref, reactive, nextTick, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import MarkdownText from './MarkdownText.vue'

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
  /**
   * 载入历史消息
   *
   * 组件挂载时调用一次，返回 [{ role: 'user' | 'ai', content: string }]。
   * 返回空数组表示这个会话还没有历史。
   */
  loadHistory: {
    type: Function,
    default: null,
  },
  /**
   * 用户发出消息时的回调（用于记录会话标题等）
   * @param {string} message 用户消息原文
   */
  onUserMessage: {
    type: Function,
    default: null,
  },
})

const router = useRouter()

// 消息列表：{ role: 'user' | 'ai', content: string, loading?: boolean }
const messages = ref([])
const inputMessage = ref('')
const loading = ref(false)
const loadingHistory = ref(false)
const chatBodyRef = ref(null)
const inputRef = ref(null)

// 载入历史消息：这个 chatId 之前聊过什么，刷新页面 / 重新登录后也要能看到
onMounted(async () => {
  if (!props.loadHistory) return
  loadingHistory.value = true
  try {
    const list = await props.loadHistory()
    if (Array.isArray(list) && list.length > 0) {
      messages.value = list
      scrollToBottom()
    }
  } catch (e) {
    console.warn('加载历史记录失败：', e)
  } finally {
    loadingHistory.value = false
  }
})

/**
 * 是否正在用中文输入法打字
 *
 * 中文输入法在选词时，用户按的键其实是「确认候选词」，
 * 如果这时候 Enter 直接发消息，用户会发现话只打了一半就发出去了。
 * 所以组合期间要拦住 Enter，等 compositionend 之后才允许发送。
 */
const isComposing = ref(false)

// 输入框随内容自适应高度（最多长到 5 行，再长就内部滚动）
const MAX_INPUT_HEIGHT = 120

const autoResize = () => {
  const el = inputRef.value
  if (!el) return
  // 先归零再量，不然高度只会单调递增、缩不回去
  el.style.height = 'auto'
  el.style.height = Math.min(el.scrollHeight, MAX_INPUT_HEIGHT) + 'px'
  el.style.overflowY = el.scrollHeight > MAX_INPUT_HEIGHT ? 'auto' : 'hidden'
}

/**
 * 回车发送，Shift+回车换行
 *
 * 用 exact 判断「没有按任何修饰键」，这样 Ctrl+Enter / Alt+Enter 也不会误触发。
 */
const onKeydown = (event) => {
  if (event.key !== 'Enter' || event.shiftKey || event.ctrlKey || event.altKey || event.metaKey) {
    return
  }
  // 中文输入法选词中：让输入法处理，不发送
  if (isComposing.value) {
    return
  }
  event.preventDefault()
  sendMessage()
}

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
  // 通知外部（比如记一下会话标题）
  if (props.onUserMessage) props.onUserMessage(message)
  inputMessage.value = ''
  // 等 v-model 把新值同步到 DOM 后，再把输入框缩回单行
  nextTick(autoResize)

  // 添加 AI 占位消息，后续流式填充
  // 注意：必须用 reactive 包裹，否则直接修改原始对象不会触发视图更新
  const aiMessage = reactive({ role: 'ai', content: '', loading: true })
  messages.value.push(aiMessage)
  loading.value = true
  scrollToBottom()

  // 通过 EventSource 调用 SSE 接口，实时接收对话内容
  // 对话接口要求登录：同源请求会自动带 JSESSIONID，
  // withCredentials 是为了兼容前后端跨域部署的情况
  eventSource = new EventSource(props.buildSseUrl(message), { withCredentials: true })

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
  nextTick(autoResize)
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
  /* AI 气泡走 Markdown 渲染：不能继承 pre-wrap，
     否则 v-html 里的 HTML 源码缩进和换行会变成可见空白 */
  white-space: normal;
}

/* 「正在输入」光标的样式在 assets/markdown.css 里（全局）——
   因为它是被 v-html 注入的 span，scoped 样式匹配不到 */

/* 输入框区域 */
.chat-footer {
  display: flex;
  /* 输入框会随内容变高，发送按钮跟着长高会很难看 —— 改成底部对齐，
     按钮保持固定高度 */
  align-items: flex-end;
  gap: 12px;
  padding: 14px 20px;
  border-top: 1px solid var(--border-color);
  background: var(--bg-card);
  flex-shrink: 0;
}

.chat-input {
  flex: 1;
  min-width: 0;
  padding: 10px 16px;
  border: 1px solid #dfe3ea;
  border-radius: 10px;
  font-size: 14px;
  line-height: 1.5;
  outline: none;
  transition: border-color 0.2s;
  background: var(--bg-chat);
  /* 高度由脚本按内容调整，用户不能手动拖拽 */
  resize: none;
  /* 初始隐藏溢出；内容超过上限时脚本会切成 auto 出现滚动条 */
  overflow-y: hidden;
}

.chat-input:focus {
  border-color: var(--accent);
  background: var(--bg-card);
}

.send-btn {
  padding: 0 24px;
  /* 与单行输入框同高（10px*2 padding + 14px*1.5 行高 + 2px 边框 ≈ 43px） */
  height: 43px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
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
