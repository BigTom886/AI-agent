import { createRouter, createWebHistory } from 'vue-router'
import Home from '../views/Home.vue'
import { ensureAuth } from '../utils/auth'

const router = createRouter({
  history: createWebHistory(),
  routes: [
    {
      path: '/',
      name: 'home',
      component: Home,
    },
    {
      path: '/login',
      name: 'login',
      component: () => import('../views/AuthView.vue'),
    },
    {
      path: '/register',
      name: 'register',
      component: () => import('../views/AuthView.vue'),
    },
    {
      path: '/profile',
      name: 'profile',
      component: () => import('../views/ProfileView.vue'),
      meta: { requiresAuth: true },
    },
    {
      path: '/love-app',
      name: 'loveApp',
      component: () => import('../views/LoveAppChat.vue'),
      meta: { requiresAuth: true },
    },
    {
      path: '/manus',
      name: 'manus',
      component: () => import('../views/ManusChat.vue'),
      meta: { requiresAuth: true },
    },
  ],
})

router.beforeEach(async (to) => {
  // 受保护页面：每次都向后端确认一次，避免 session 过期后仍拿着旧状态进入
  if (to.meta.requiresAuth) {
    const user = await ensureAuth(true)
    if (!user) {
      return {
        name: 'login',
        query: { redirect: to.fullPath },
      }
    }
    return true
  }

  // 已登录用户不再停留在登录/注册页
  if (to.name === 'login' || to.name === 'register') {
    const user = await ensureAuth()
    if (user) {
      return { name: 'home' }
    }
    return true
  }

  // 首页：初始化一下登录态，用于右上角展示登录/用户信息
  await ensureAuth()
  return true
})

export default router
