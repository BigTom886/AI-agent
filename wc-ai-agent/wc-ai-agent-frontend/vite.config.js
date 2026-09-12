import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'

// https://vitejs.dev/config/
export default defineConfig({
  plugins: [vue()],
  server: {
    host: true, // 监听 0.0.0.0，允许局域网内的手机等设备访问
    port: 5173,
    // 代理后端接口，解决跨域问题
    // 前端请求 /api/** 会被转发到 http://localhost:80/api/**
    proxy: {
      '/api': {
        target: 'http://localhost:80',
        changeOrigin: true,
      },
    },
  },
})
