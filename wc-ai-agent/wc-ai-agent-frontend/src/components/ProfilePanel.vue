<template>
  <div class="profile-panel">
    <div class="panel-header">
      <span class="panel-title">🧠 我的画像</span>
      <button class="close-btn" title="关闭" @click="$emit('close')">✕</button>
    </div>

    <div class="panel-body">
      <p v-if="loading" class="tip">加载中…</p>
      <p v-else-if="error" class="tip error">{{ error }}</p>
      <p v-else-if="items.length === 0" class="tip">
        还没有画像。聊天时 AI 觉得某件事值得长期记住，会自动记在这里。
      </p>
      <ul v-else class="items">
        <li v-for="(item, index) in items" :key="index">{{ item }}</li>
      </ul>
    </div>

    <div v-if="items.length > 0" class="panel-footer">
      <button class="clear-btn" :disabled="clearing" @click="clearProfile">
        {{ clearing ? '清空中…' : '清空画像' }}
      </button>
    </div>
  </div>
</template>

<script setup>
import { ref, watch } from 'vue'
import request from '../utils/request'

const props = defineProps({
  // 面板是否展开
  open: {
    type: Boolean,
    default: false,
  },
  /**
   * 外部触发重新加载
   *
   * 聊天时 AI 可能新记住了一条画像，父组件把这个值 +1 就能让面板刷新。
   */
  reloadKey: {
    type: Number,
    default: 0,
  },
})

defineEmits(['close'])

// 画像条目列表，每条形如 "- [昵称] 韩跑跑"
const items = ref([])
const loading = ref(false)
const clearing = ref(false)
const error = ref('')

const loadProfile = async () => {
  loading.value = true
  error.value = ''
  try {
    // 拦截器已经剥掉了一层 response，这里拿到的就是数组本身；
    // 用户身份由后端从登录 session 里取，前端不用再传 userId
    items.value = await request.get('/ai/love_app/profile')
  } catch (e) {
    error.value = '画像加载失败，请稍后重试'
    items.value = []
  } finally {
    loading.value = false
  }
}

const clearProfile = async () => {
  if (!window.confirm('确定要清空画像吗？AI 将不再记得关于你的这些信息。')) {
    return
  }
  clearing.value = true
  error.value = ''
  try {
    await request.delete('/ai/love_app/profile')
    items.value = []
  } catch (e) {
    error.value = '清空失败，请稍后重试'
  } finally {
    clearing.value = false
  }
}

// 展开面板时拉一次最新画像（后端是现读的，可能刚被 AI 改过）
//
// 注意 immediate: true —— 面板是用 v-if 创建的，组件一挂载 open 已经是 true，
// 如果只在「值发生变化」时才加载，watch 永远不会触发，画像就一直是空的。
watch(
  () => props.open,
  (opened) => {
    if (opened) {
      loadProfile()
    }
  },
  { immediate: true }
)

// 外部要求刷新时（聊天中新记住了一条画像）也重新拉一次
watch(
  () => props.reloadKey,
  () => {
    if (props.open) {
      loadProfile()
    }
  }
)
</script>

<style scoped>
.profile-panel {
  position: absolute;
  right: 16px;
  /* 三个悬浮按钮叠起来约 118px 高，面板要排在按钮上方 */
  bottom: 228px;
  width: 300px;
  max-width: calc(100% - 32px);
  background: var(--bg-card);
  border: 1px solid var(--border-color);
  border-radius: var(--radius-sm);
  box-shadow: var(--shadow-md);
  overflow: hidden;
  z-index: 20;
}

.panel-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 10px 14px;
  border-bottom: 1px solid var(--border-color);
  background: rgba(255, 235, 240, 0.6);
}

.panel-title {
  font-size: 14px;
  font-weight: 600;
  color: var(--text-main);
}

.close-btn {
  background: transparent;
  border: none;
  cursor: pointer;
  font-size: 14px;
  color: var(--text-light);
  padding: 2px 4px;
}

.close-btn:hover {
  color: var(--text-main);
}

.panel-body {
  padding: 12px 14px;
  max-height: 260px;
  overflow-y: auto;
}

.tip {
  margin: 0;
  font-size: 13px;
  line-height: 1.6;
  color: var(--text-light);
}

.tip.error {
  color: #e05a5a;
}

.items {
  margin: 0;
  padding-left: 18px;
  font-size: 13px;
  line-height: 1.8;
  color: var(--text-sub);
}

.items li {
  word-break: break-word;
}

.panel-footer {
  padding: 10px 14px;
  border-top: 1px solid var(--border-color);
}

.clear-btn {
  width: 100%;
  padding: 7px 12px;
  border: 1px solid #f0c4cd;
  border-radius: 8px;
  background: #fff;
  color: #d9536f;
  font-size: 13px;
  cursor: pointer;
  transition: background 0.2s;
}

.clear-btn:hover:not(:disabled) {
  background: #fff1f4;
}

.clear-btn:disabled {
  opacity: 0.6;
  cursor: not-allowed;
}

@media (max-width: 480px) {
  .profile-panel {
    bottom: 218px;
    left: 12px;
    right: 12px;
    width: auto;
  }
}
</style>
