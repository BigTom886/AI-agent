import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'

// https://vitejs.dev/config/
export default defineConfig({
  plugins: [vue()],
  server: {
    host: true, // 监听 0.0.0.0，允许局域网内的手机等设备访问
    port: 5173,
    // 代理后端接口，解决跨域问题
    // 前端请求 /api/** 会被转发到 http://localhost:8222/api/**
    //
    // 端口 8222 = 后端 Spring Boot 的 server.port（application.yml）；
    // 后端还有 context-path: /api，所以 /api 前缀原样带过去正好对上，
    // 不需要 rewrite。
    //
    // 为什么不是 80：80 是生产环境 nginx 的端口（nginx 发静态文件 + 反代后端）。
    // 本地开发不跑 nginx，直接指后端 8222 即可。
    proxy: {
      '/api': {
        target: 'http://localhost:8222',
        changeOrigin: true,
      },
    },
  },
})
