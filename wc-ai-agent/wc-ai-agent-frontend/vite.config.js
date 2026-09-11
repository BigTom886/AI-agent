import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'

// https://vitejs.dev/config/
export default defineConfig({
  plugins: [vue()],
  server: {
    port: 5173,
    // 代理后端接口，解决跨域问题
    // 前端请求 /api/** 会被转发到 http://localhost:8123/api/**
    proxy: {
      '/api': {
        target: 'http://localhost:8222',
        changeOrigin: true,
      },
    },
  },
})
