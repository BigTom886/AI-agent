<template>
  <div class="report-panel">
    <div class="panel-header">
      <span class="panel-title">📋 恋爱报告</span>
      <button class="close-btn" title="关闭" @click="$emit('close')">✕</button>
    </div>

    <div class="panel-body">
      <!-- 想重点了解什么（可留空，留空则针对整段对话） -->
      <input
        v-model="topic"
        class="topic-input"
        placeholder="想重点了解什么？（可留空）"
        :disabled="loading"
        @keyup.enter="generate"
      />

      <p v-if="loading" class="tip">报告生成中…通常需要 10~30 秒，请稍候</p>
      <p v-else-if="error" class="tip error">{{ error }}</p>

      <div v-else-if="report" class="report">
        <h3 class="report-title">{{ report.title }}</h3>
        <ol v-if="report.suggestions && report.suggestions.length" class="report-list">
          <li v-for="(item, index) in report.suggestions" :key="index">{{ item }}</li>
        </ol>
        <p v-else class="tip">这次没拿到具体建议，换个说法再试试。</p>
      </div>

      <p v-else-if="notice" class="tip">{{ notice }}</p>
      <p v-else class="tip">
        报告基于当前这段对话和 AI 记住的画像生成。
      </p>
    </div>

    <div class="panel-footer">
      <button class="generate-btn" :disabled="loading" @click="generate">
        {{ loading ? '生成中…' : report ? '重新生成' : '生成报告' }}
      </button>
    </div>
  </div>
</template>

<script setup>
import { ref, watch } from 'vue'
import request from '../utils/request'
import { errorMessage } from '../utils/error'

const props = defineProps({
  // 面板是否展开
  open: {
    type: Boolean,
    default: false,
  },
  // 当前会话 ID：报告基于这段对话生成
  chatId: {
    type: String,
    default: '',
  },
})

defineEmits(['close'])

/** 用户想重点了解的方向（可留空） */
const topic = ref('')
/** 报告结果：{ title, suggestions } */
const report = ref(null)
const loading = ref(false)
const error = ref('')
/** 提示信息（不是错误，比如切换了会话） */
const notice = ref('')

/**
 * 生成报告
 *
 * 后端会校验「必须已登录 + 必须是自己的会话」，
 * 报告内容由大模型基于当前会话的对话 + 用户画像生成。
 */
const generate = async () => {
  if (!props.chatId) {
    error.value = '当前没有会话，无法生成报告'
    return
  }
  loading.value = true
  error.value = ''
  notice.value = ''
  try {
    report.value = await request.get('/ai/love_app/report', {
      params: {
        chatId: props.chatId,
        topic: topic.value.trim() || undefined,
      },
      // 报告要跑一次大模型，比普通对话慢，单独放宽超时
      timeout: 120000,
    })
  } catch (e) {
    error.value = errorMessage(e, '报告生成失败，请稍后重试')
    report.value = null
  } finally {
    loading.value = false
  }
}

// 展开面板时自动生成一次：用户是主动点了「恋爱报告」，意图明确
//
// 注意 immediate: true —— 面板是用 v-if 创建的，组件一挂载 open 已经是 true，
// 如果只在「值发生变化」时才加载，watch 永远不会触发。
watch(
  () => props.open,
  (opened) => {
    if (opened) {
      report.value = null
      error.value = ''
      notice.value = ''
      generate()
    }
  },
  { immediate: true }
)

// 切换会话后，之前那份报告和当前会话对不上了，清掉让用户重新生成
watch(
  () => props.chatId,
  () => {
    if (props.open) {
      report.value = null
      error.value = ''
      notice.value = '已切换会话，点下方「生成报告」重新生成'
    }
  }
)
</script>

<style scoped>
.report-panel {
  position: absolute;
  right: 16px;
  bottom: 228px;
  width: 320px;
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
  max-height: 300px;
  overflow-y: auto;
}

.topic-input {
  width: 100%;
  margin-bottom: 10px;
  padding: 8px 10px;
  border: 1px solid var(--border-color);
  border-radius: 8px;
  background: var(--bg-chat);
  color: var(--text-main);
  font-size: 13px;
  box-sizing: border-box;
}

.topic-input:focus {
  outline: none;
  border-color: var(--accent, #ff6b81);
}

.topic-input:disabled {
  opacity: 0.6;
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

.report-title {
  margin: 2px 0 10px;
  font-size: 15px;
  font-weight: 600;
  color: var(--text-main);
}

.report-list {
  margin: 0;
  padding-left: 20px;
  font-size: 13px;
  line-height: 1.9;
  color: var(--text-sub);
}

.report-list li {
  word-break: break-word;
}

.panel-footer {
  padding: 10px 14px;
  border-top: 1px solid var(--border-color);
}

.generate-btn {
  width: 100%;
  padding: 8px 12px;
  border: none;
  border-radius: 8px;
  background: var(--accent, #ff6b81);
  color: #fff;
  font-size: 13px;
  cursor: pointer;
  transition: opacity 0.2s;
}

.generate-btn:hover:not(:disabled) {
  opacity: 0.9;
}

.generate-btn:disabled {
  opacity: 0.6;
  cursor: not-allowed;
}

@media (max-width: 480px) {
  .report-panel {
    bottom: 218px;
    left: 12px;
    right: 12px;
    width: auto;
  }
}
</style>
