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
      :on-reset="startNewConversation"
      :load-history="loadHistory"
      :on-user-message="handleUserMessage"
    />

    <!-- 悬浮操作：我的画像 / 历史会话（带文字，避免用户找不到） -->
    <div class="fab-stack">
      <button
        class="fab-pill"
        :class="{ active: profileOpen }"
        title="AI 记住的关于你的信息"
        @click="toggleProfile"
      >
        <span class="fab-icon">🧠</span>
        <span class="fab-text">我的画像</span>
      </button>
      <button
        class="fab-pill"
        :class="{ active: historyOpen }"
        title="查看以前聊过的对话"
        @click="toggleHistory"
      >
        <span class="fab-icon">🗂</span>
        <span class="fab-text">历史会话</span>
      </button>
      <button
        class="fab-pill"
        :class="{ active: reportOpen }"
        title="基于这段对话生成一份恋爱分析报告"
        @click="toggleReport"
      >
        <span class="fab-icon">📋</span>
        <span class="fab-text">恋爱报告</span>
      </button>
    </div>

    <!-- 历史会话：切换以前聊过的对话 -->
    <div v-if="historyOpen" class="history-panel">
      <div class="history-header">
        <span class="history-title">🗂 历史会话</span>
        <button class="history-close" title="关闭" @click="historyOpen = false">✕</button>
      </div>
      <div class="history-body">
        <p v-if="historyError" class="history-error">{{ historyError }}</p>
        <p v-else-if="conversations.length === 0" class="history-tip">还没有历史会话</p>
        <ul v-else class="history-list">
          <li
            v-for="item in conversations"
            :key="item.id"
            :class="{ active: item.id === chatId }"
            @click="switchConversation(item.id)"
          >
            <span class="conv-title">{{ item.title || '新的对话' }}</span>
            <span class="conv-time">{{ formatTime(item.updatedAt) }}</span>
            <button class="conv-del" title="删除该会话" @click.stop="deleteConversation(item.id)">
              ✕
            </button>
          </li>
        </ul>
      </div>
    </div>

    <!-- 跨会话画像：查看 / 清空 AI 记住的关于你的信息 -->
    <ProfilePanel
      v-if="profileOpen"
      :open="profileOpen"
      :reload-key="profileReloadKey"
      @close="profileOpen = false"
    />

    <!-- 恋爱报告：基于当前会话的对话 + 画像生成 -->
    <ReportPanel
      v-if="reportOpen"
      :open="reportOpen"
      :chat-id="chatId"
      @close="reportOpen = false"
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
import ProfilePanel from '../components/ProfilePanel.vue'
import ReportPanel from '../components/ReportPanel.vue'
import request from '../utils/request'
import { authState } from '../utils/auth'
import { errorMessage } from '../utils/error'
import {
  createConversation,
  getConversations,
  getCurrentChatId,
  removeConversation,
  touchConversation,
} from '../utils/conversations'

// 会话按用户名隔离：同一个用户沿用上次的会话，换个用户就是另一份列表
const username = authState.user?.username || 'guest'

// 沿用这个用户最近使用的会话；没有就新建一个
// （chatId 变了就等于换了一个会话，所以必须持久化，否则刷新后历史找不回来）
const chatId = ref(getCurrentChatId(username) || createConversation(username))
const conversations = ref(getConversations(username))

// 历史会话面板 / 画像面板 / 报告面板的开关
const historyOpen = ref(false)
const profileOpen = ref(false)
const reportOpen = ref(false)

// 聊天中 AI 可能新记住了一条画像，用它让已打开的画像面板刷新
const profileReloadKey = ref(0)

// 删除会话时的错误提示
const historyError = ref('')

const refreshConversations = () => {
  conversations.value = getConversations(username)
}

// 三个悬浮面板位置接近，同时打开会互相盖住，这里让它们互斥
const toggleHistory = () => {
  historyOpen.value = !historyOpen.value
  if (historyOpen.value) {
    profileOpen.value = false
    reportOpen.value = false
  }
}

const toggleProfile = () => {
  profileOpen.value = !profileOpen.value
  if (profileOpen.value) {
    historyOpen.value = false
    reportOpen.value = false
  }
}

const toggleReport = () => {
  reportOpen.value = !reportOpen.value
  if (reportOpen.value) {
    historyOpen.value = false
    profileOpen.value = false
  }
}

/**
 * 载入历史消息
 *
 * 后端返回的是这个会话当前保存的对话记忆
 * （老消息被压成摘要 + 最近几条原文），转成组件要的消息格式。
 */
const loadHistory = async () => {
  try {
    const records = await request.get('/ai/love_app/history', {
      params: { chatId: chatId.value },
    })
    return (records || [])
      // 只显示对话本身：系统提示词和工具调用记录对用户没意义
      .filter((record) => record.role === 'USER' || record.role === 'ASSISTANT')
      .map((record) => ({
        role: record.role === 'USER' ? 'user' : 'ai',
        content: record.content,
      }))
  } catch (e) {
    // 没有历史 / 会话失效时按空会话处理，不打断聊天
    return []
  }
}

// 开启新会话
const startNewConversation = () => {
  chatId.value = createConversation(username)
  refreshConversations()
}

// 用户发了消息：把第一条消息记成会话标题，同时让画像面板（如果开着）刷新
const handleUserMessage = (text) => {
  touchConversation(username, chatId.value, text)
  refreshConversations()
  profileReloadKey.value += 1
}

// 切换到某个历史会话（chatId 变化会让 ChatRoom 重新挂载并载入历史）
const switchConversation = (id) => {
  historyOpen.value = false
  if (id === chatId.value) return
  chatId.value = id
}

/**
 * 删除会话
 *
 * 后端会校验「必须已登录 + 必须是自己的会话」，
 * 删掉的是服务器上的对话记录，这里同步把本地列表里的记录去掉。
 */
const deleteConversation = async (id) => {
  const item = conversations.value.find((conversation) => conversation.id === id)
  const name = item?.title || '该会话'
  if (!window.confirm(`确定要删除「${name}」吗？对话记录将无法恢复。`)) {
    return
  }

  historyError.value = ''
  try {
    await request.delete('/ai/love_app/conversation', {
      params: { chatId: id },
    })
    removeConversation(username, id)
    if (id === chatId.value) {
      // 删掉的正是当前会话，换一个新会话继续
      chatId.value = createConversation(username)
    }
    refreshConversations()
  } catch (e) {
    historyError.value = errorMessage(e, '删除失败，请稍后重试')
  }
}

const formatTime = (timestamp) => {
  const date = new Date(timestamp)
  if (Number.isNaN(date.getTime())) return ''
  const pad = (n) => String(n).padStart(2, '0')
  return `${pad(date.getMonth() + 1)}-${pad(date.getDate())} ${pad(date.getHours())}:${pad(date.getMinutes())}`
}

// 构造 AI 恋爱大师 SSE 接口地址：
// GET /api/ai/love_app/chat/sse?message=xxx&chatId=xxx
// 用户身份由后端从登录 session 里取，前端不再传 userId
const buildSseUrl = (message) =>
  `/api/ai/love_app/chat/sse?message=${encodeURIComponent(message)}` +
  `&chatId=${encodeURIComponent(chatId.value)}`
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

/* 悬浮操作按钮组：挂在聊天区右下角 */
.fab-stack {
  position: absolute;
  right: 16px;
  bottom: 96px;
  display: flex;
  flex-direction: column;
  align-items: flex-end;
  gap: 8px;
  z-index: 15;
}

/* 带文字的胶囊按钮：光看 emoji 用户很难发现它能点 */
.fab-pill {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 8px 14px;
  border: 1px solid var(--border-color);
  border-radius: 20px;
  background: rgba(255, 255, 255, 0.95);
  box-shadow: var(--shadow-md);
  color: var(--text-main);
  font-size: 13px;
  cursor: pointer;
  transition: transform 0.2s, box-shadow 0.2s, color 0.2s, background 0.2s;
}

.fab-pill:hover {
  transform: translateY(-2px);
  box-shadow: var(--shadow-lg);
  color: var(--accent, #ff6b81);
}

.fab-pill.active {
  background: var(--accent, #ff6b81);
  border-color: transparent;
  color: #fff;
}

.fab-icon {
  font-size: 16px;
  line-height: 1;
}

.fab-text {
  line-height: 1;
}

/* 历史会话面板 */
.history-panel {
  position: absolute;
  right: 16px;
  /* 三个悬浮按钮叠起来约 118px 高，面板要排在按钮上方 */
  bottom: 228px;
  width: 280px;
  max-width: calc(100% - 32px);
  background: var(--bg-card);
  border: 1px solid var(--border-color);
  border-radius: var(--radius-sm);
  box-shadow: var(--shadow-md);
  overflow: hidden;
  z-index: 20;
}

.history-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 10px 14px;
  border-bottom: 1px solid var(--border-color);
  background: rgba(255, 235, 240, 0.6);
}

.history-title {
  font-size: 14px;
  font-weight: 600;
  color: var(--text-main);
}

.history-close {
  background: transparent;
  border: none;
  cursor: pointer;
  font-size: 14px;
  color: var(--text-light);
  padding: 2px 4px;
}

.history-close:hover {
  color: var(--text-main);
}

.history-body {
  max-height: 260px;
  overflow-y: auto;
  padding: 6px;
}

.history-tip {
  margin: 0;
  padding: 14px 8px;
  font-size: 13px;
  color: var(--text-light);
  text-align: center;
}

.history-list {
  list-style: none;
  margin: 0;
  padding: 0;
}

.history-list li {
  position: relative;
  display: flex;
  flex-direction: column;
  gap: 2px;
  padding: 8px 34px 8px 10px;
  border-radius: 8px;
  cursor: pointer;
  transition: background 0.2s;
}

.history-list li:hover {
  background: var(--bg-chat);
}

.history-list li.active {
  background: rgba(255, 107, 129, 0.12);
}

.conv-title {
  font-size: 13px;
  color: var(--text-main);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.conv-time {
  font-size: 11px;
  color: var(--text-light);
}

/* 删除某个会话 */
.conv-del {
  position: absolute;
  top: 50%;
  right: 6px;
  transform: translateY(-50%);
  width: 22px;
  height: 22px;
  border: none;
  border-radius: 50%;
  background: transparent;
  color: var(--text-light);
  font-size: 12px;
  line-height: 1;
  cursor: pointer;
  opacity: 0.55;
  transition: opacity 0.2s, background 0.2s, color 0.2s;
}

.conv-del:hover {
  opacity: 1;
  background: #ffe9ec;
  color: #d9536f;
}

.history-error {
  margin: 0 0 6px;
  padding: 8px 10px;
  border-radius: 8px;
  background: #fff1f2;
  color: #d9536f;
  font-size: 12px;
}

/* ===== 适配深色手机状态栏等 ===== */
@media (max-width: 480px) {
  .love-bg-decoration {
    display: none;
  }

  .history-panel {
    bottom: 218px;
    left: 12px;
    right: 12px;
    width: auto;
  }
}
</style>
