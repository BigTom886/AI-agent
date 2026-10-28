import axios from 'axios'

// 后端接口地址前缀（开发环境走 vite 代理，生产环境可替换为真实地址）
const baseURL = import.meta.env.VITE_API_BASE_URL || '/api'

// 创建 Axios 实例，用于普通接口请求
const request = axios.create({
  baseURL,
  timeout: 30000,
  // 登录态靠 HttpSession Cookie；同源请求默认就会带，
  // 这里显式打开是为了兼容 VITE_API_BASE_URL 指向跨域后端的部署方式
  withCredentials: true,
  headers: {
    'Content-Type': 'application/json',
  },
})

// 请求拦截器
request.interceptors.request.use(
  (config) => {
    // 可在此处统一添加 token 等认证信息
    return config
  },
  (error) => Promise.reject(error)
)

// 响应拦截器
request.interceptors.response.use(
  (response) => response.data,
  (error) => {
    // 401 是「未登录 / 会话过期」的正常分支，由路由守卫和页面自己处理，不必刷错误日志
    if (error.response?.status !== 401) {
      console.error('请求出错：', error)
    }
    return Promise.reject(error)
  }
)

export default request
