import axios from 'axios'

// 后端接口地址前缀（开发环境走 vite 代理，生产环境可替换为真实地址）
const baseURL = import.meta.env.VITE_API_BASE_URL || '/api'

// 创建 Axios 实例，用于普通接口请求
const request = axios.create({
  baseURL,
  timeout: 30000,
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
    console.error('请求出错：', error)
    return Promise.reject(error)
  }
)

export default request
