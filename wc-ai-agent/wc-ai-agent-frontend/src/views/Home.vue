<template>
  <div class="home">
    <header class="home-header">
      <h1>AI 应用中心</h1>
      <p class="subtitle">选择一个应用，开始你的 AI 之旅</p>
    </header>

    <main class="app-grid">
      <div
        v-for="app in appList"
        :key="app.path"
        class="app-card"
        :style="{ '--accent': app.color }"
        @click="router.push(app.path)"
      >
        <div class="app-icon">{{ app.icon }}</div>
        <h2 class="app-name">{{ app.name }}</h2>
        <p class="app-desc">{{ app.description }}</p>
        <span class="app-enter">进入应用 →</span>
      </div>
    </main>

    <footer class="home-footer">Powered by wc-ai-agent</footer>
  </div>
</template>

<script setup>
import { useRouter } from 'vue-router'
import { generateChatId } from '../utils/chatId'

const router = useRouter()

const appList = [
  {
    path: '/love-app',
    name: 'AI 恋爱大师',
    icon: '💘',
    color: '#ff6b81',
    description: '你的专属恋爱顾问，情感问题一键解答',
  },
  {
    path: '/manus',
    name: 'AI 超级智能体',
    icon: '🤖',
    color: '#4a7cff',
    description: '自主规划任务的超级智能体，帮你搞定一切',
  },
]

// 预生成一个聊天室 id，供聊天页使用（刷新主页会重新生成）
generateChatId()
</script>

<style scoped>
.home {
  min-height: 100%;
  display: flex;
  flex-direction: column;
}

.home-header {
  text-align: center;
  padding: 64px 16px 32px;
}

.home-header h1 {
  font-size: 36px;
  margin-bottom: 12px;
}

.subtitle {
  color: #7f8c9b;
  font-size: 16px;
}

.app-grid {
  flex: 1;
  display: flex;
  justify-content: center;
  align-items: flex-start;
  gap: 32px;
  padding: 24px 16px;
  flex-wrap: wrap;
}

.app-card {
  width: 300px;
  padding: 32px 24px;
  background: #fff;
  border-radius: 16px;
  box-shadow: 0 4px 16px rgba(0, 0, 0, 0.06);
  cursor: pointer;
  text-align: center;
  transition: transform 0.25s ease, box-shadow 0.25s ease;
  border-top: 4px solid var(--accent);
}

.app-card:hover {
  transform: translateY(-6px);
  box-shadow: 0 10px 28px rgba(0, 0, 0, 0.12);
}

.app-icon {
  font-size: 56px;
  margin-bottom: 16px;
}

.app-name {
  font-size: 22px;
  margin-bottom: 10px;
  color: var(--accent);
}

.app-desc {
  font-size: 14px;
  color: #7f8c9b;
  line-height: 1.6;
  margin-bottom: 20px;
  min-height: 44px;
}

.app-enter {
  display: inline-block;
  font-size: 14px;
  color: var(--accent);
  font-weight: 600;
}

.home-footer {
  text-align: center;
  padding: 24px;
  color: #b0b8c4;
  font-size: 13px;
}
</style>
