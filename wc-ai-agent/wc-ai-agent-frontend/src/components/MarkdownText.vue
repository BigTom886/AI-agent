<template>
  <div class="markdown-body" v-html="html"></div>
</template>

<script setup>
import { ref, watch, onUnmounted } from 'vue'
import { renderMarkdown, withCursor } from '../utils/markdown'

const props = defineProps({
  // Markdown 源码（AI 回复的原始文本）
  content: {
    type: String,
    default: '',
  },
  // 是否正在流式接收：显示「正在输入」的光标
  streaming: {
    type: Boolean,
    default: false,
  },
})

const html = ref('')

// 帧号：用来做节流
let frameId = null

const render = () => {
  const result = renderMarkdown(props.content)
  html.value = props.streaming ? withCursor(result) : result
}

/**
 * 节流：SSE 是「每个 token 推一次」，一个长回复可能触发几百次。
 * 每次都全量重新解析 + 替换 innerHTML 会明显卡顿，
 * 所以用 requestAnimationFrame 合并 —— 一帧内最多渲染一次（≈60 次/秒上限）。
 */
const scheduleRender = () => {
  if (frameId !== null) {
    return
  }
  frameId = requestAnimationFrame(() => {
    frameId = null
    render()
  })
}

watch(() => props.content, scheduleRender, { immediate: true })
watch(() => props.streaming, scheduleRender)

onUnmounted(() => {
  if (frameId !== null) {
    cancelAnimationFrame(frameId)
  }
})
</script>
