import { createRouter, createWebHistory } from 'vue-router'
import Home from '../views/Home.vue'

const router = createRouter({
  history: createWebHistory(),
  routes: [
    {
      path: '/',
      name: 'home',
      component: Home,
    },
    {
      path: '/love-app',
      name: 'loveApp',
      component: () => import('../views/LoveAppChat.vue'),
    },
    {
      path: '/manus',
      name: 'manus',
      component: () => import('../views/ManusChat.vue'),
    },
  ],
})

export default router
