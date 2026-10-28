import { createApp } from 'vue'
import App from './App.vue'
import router from './router'
import './assets/main.css'
// Markdown 排版样式：全局引入（v-html 渲染出来的元素带不了 scoped）
import './assets/markdown.css'

createApp(App).use(router).mount('#app')
