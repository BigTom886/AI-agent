<template>
  <div class="home">
    <div class="home-bg-gradient"></div>
    <header class="home-header">
      <div class="logo">🤖</div>
      <h1>AI 应用中心</h1>
      <p class="subtitle">选择一个应用，开启你的 AI 之旅</p>
    </header>

    <main class="app-grid">
      <div
        v-for="app in appList"
        :key="app.path"
        class="app-card"
        :style="{ '--accent': app.color, '--accent-soft': app.colorSoft }"
        @click="router.push(app.path)"
      >
        <div class="app-icon">{{ app.icon }}</div>
        <h2 class="app-name">{{ app.name }}</h2>
        <p class="app-desc">{{ app.description }}</p>
        <span class="app-enter">点击进入 →</span>
      </div>
    </main>

    <footer class="home-footer">
      <div class="footer-content">
        <p>&copy; {{ year }} wc-ai-agent 版权所有</p>
        <p class="footer-desc">用心打造每一款 AI 应用，让技术温暖你的生活 ❤️</p>
      </div>
    </footer>
  </div>
</template>

<script setup>
import { useRouter } from 'vue-router'
import { generateChatId } from '../utils/chatId'

const router = useRouter()
const year = new Date().getFullYear()

const appList = [
  {
    path: '/love-app',
    name: 'AI 恋爱大师',
    icon: '💘',
    color: '#ff6b81',
    colorSoft: 'rgba(255, 107, 129, 0.15)',
    description: '你的专属恋爱顾问，解你心事，给你建议。',
  },
  {
    path: '/manus',
    name: 'AI 超级智能体',
    icon: '🤖',
    color: '#4a7cff',
    colorSoft: 'rgba(74, 124, 255, 0.15)',
    description: '自主规划任务，高效解决问题的超级 AI。',
  },
]

// 预生成一个聊天室 id，供聊天页使用（刷新主页会重新生成）
generateChatId()
</script>

<style scoped>
.home {
  min-height: 100%;
  min-height: 100dvh;
  display: flex;
  flex-direction: column;
  position: relative;
  overflow-x: hidden;
}

/* 浪漫渐变背景 */
.home-bg-gradient {
  position: fixed;
  inset: 0;
  background: radial-gradient(
      circle at 10% 20%,
      rgba(255, 229, 236, 0.8) 0%,
      transparent 50%
    ),
    radial-gradient(circle at 90% 80%, rgba(214, 233, 255, 0.6) 0%, transparent 50%);
  z-index: 0;
  pointer-events: none;
  animation: bgShift 20s ease-in-out infinite alternate;
}

@keyframes bgShift {
  0% {
    transform: translate(-2%, -2%) scale(1.05);
  }
  100% {
    transform: translate(2%, 2%) scale(1.1);
  }
}

.home-header {
  text-align: center;
  padding: 64px 16px 32px;
  position: relative;
  z-index: 1;
}

.logo {
  width: 88px;
  height: 88px;
  margin: 0 auto 20px;
  font-size: 48px;
  border-radius: 28px;
  background: rgba(255, 255, 255, 0.9);
  backdrop-filter: blur(10px);
  box-shadow: 0 8px 32px rgba(255, 107, 129, 0.2);
  display: flex;
  align-items: center;
  justify-content: center;
  transition: transform 0.3s ease;
}

.home-header h1 {
  font-size: 36px;
  margin-bottom: 12px;
  font-weight: 700;
  background: linear-gradient(135deg, #ff6b81 0%, #4a7cff 100%);
  -webkit-background-clip: text;
  -webkit-text-fill-color: transparent;
  background-clip: text;
  letter-spacing: 0.5px;
}

.subtitle {
  color: var(--text-sub);
  font-size: 16px;
}

.app-grid {
  flex: 1;
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(280px, 340px));
  justify-content: center;
  align-content: start;
  gap: 28px;
  padding: 24px 16px 48px;
  position: relative;
  z-index: 1;
}

.app-card {
  padding: 40px 24px 32px;
  background: rgba(255, 255, 255, 0.92);
  backdrop-filter: blur(10px);
  border-radius: 24px;
  box-shadow: 0 8px 24px rgba(0, 0, 0, 0.06);
  cursor: pointer;
  text-align: center;
  transition: all 0.3s cubic-bezier(0.4, 0, 0.2, 1);
  border: 1px solid var(--accent-soft);
  overflow: hidden;
  position: relative;
}

.app-card::before {
  content: '';
  position: absolute;
  inset: 0;
  background: radial-gradient(circle at 0 0, var(--accent-soft) 0%, transparent 50%);
  opacity: 0;
  transition: opacity 0.3s ease;
}

.app-card:hover {
  transform: translateY(-8px) scale(1.02);
  box-shadow: 0 16px 40px rgba(0, 0, 0, 0.12);
  border-color: var(--accent);
}

.app-card:hover::before {
  opacity: 1;
}

.app-icon {
  width: 80px;
  height: 80px;
  margin: 0 auto 20px;
  font-size: 44px;
  border-radius: 50%;
  background: var(--accent-soft);
  display: flex;
  align-items: center;
  justify-content: center;
  transition: transform 0.3s ease;
  position: relative;
  z-index: 1;
}

.app-name {
  font-size: 22px;
  margin-bottom: 10px;
  color: var(--accent);
  font-weight: 600;
  position: relative;
  z-index: 1;
}

.app-desc {
  font-size: 14px;
  color: var(--text-sub);
  line-height: 1.7;
  margin-bottom: 22px;
  min-height: 44px;
  position: relative;
  z-index: 1;
}

.app-enter {
  display: inline-block;
  font-size: 14px;
  color: var(--accent);
  font-weight: 600;
  padding: 8px 20px;
  border-radius: 20px;
  background: var(--accent-soft);
  transition: all 0.2s ease;
  position: relative;
  z-index: 1;
}

.app-card:hover .app-enter {
  background: var(--accent);
  color: white;
}

.app-card:hover .app-icon {
  transform: scale(1.05);
}

.home-footer {
  padding: 24px 16px;
  text-align: center;
  background: rgba(255, 255, 255, 0.8);
  backdrop-filter: blur(10px);
  color: var(--text-light);
  position: relative;
  z-index: 1;
  border-top: 1px solid rgba(255, 107, 129, 0.1);
}

.footer-content p {
  margin-bottom: 6px;
  font-size: 13px;
}

.footer-desc {
  font-size: 12px !important;
  opacity: 0.8;
}

/* ===== 平板适配 ===== */
@media (max-width: 768px) {
  .home-header {
    padding: 44px 16px 24px;
  }

  .home-header h1 {
    font-size: 28px;
  }

  .logo {
    width: 72px;
    height: 72px;
    font-size: 40px;
  }
}

/* ===== 手机适配 ===== */
@media (max-width: 480px) {
  .home-header {
    padding: 32px 16px 20px;
  }

  .home-header h1 {
    font-size: 24px;
  }

  .subtitle {
    font-size: 14px;
  }

  .app-grid {
    grid-template-columns: 1fr;
    gap: 18px;
    padding: 16px 16px 32px;
  }

  .app-card {
    padding: 28px 20px 24px;
  }

  .app-desc {
    min-height: 0;
  }
}
</style>
