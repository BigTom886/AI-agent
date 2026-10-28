import { reactive } from 'vue'
import request from './request'

/**
 * 全局登录态
 *
 * 后端用 HttpSession（JSESSIONID Cookie）保存登录状态，
 * 前端不存 token，只缓存「当前用户」这份展示数据。
 * 刷新页面后由 ensureAuth() 调 /user/me 重新确认。
 */
export const authState = reactive({
  /** 当前用户（UserView）；未登录为 null */
  user: null,
  /** 是否已经向后端确认过登录态，避免重复请求 */
  initialized: false,
})

/** 正在进行中的 /user/me 请求，防止并发导航时重复发起 */
let pendingMe = null

/**
 * 确认登录态
 *
 * @param {boolean} force true 表示忽略缓存，强制向后端确认（受保护路由用）
 * @returns {Promise<object|null>} 当前用户；未登录返回 null
 */
export async function ensureAuth(force = false) {
  if (force) {
    authState.initialized = false
    authState.user = null
  }
  if (authState.initialized && !force) {
    return authState.user
  }
  if (pendingMe) {
    return pendingMe
  }

  pendingMe = (async () => {
    try {
      // 未登录时后端返回 401，这里属于正常分支，不当作异常抛出
      authState.user = await request.get('/user/me')
    } catch (e) {
      authState.user = null
    } finally {
      authState.initialized = true
      pendingMe = null
    }
    return authState.user
  })()

  return pendingMe
}

/**
 * 登录
 *
 * @param {{username: string, password: string}} payload
 * @returns {Promise<object>} 当前用户
 */
export async function login(payload) {
  const user = await request.post('/user/login', payload)
  authState.user = user
  authState.initialized = true
  return user
}

/**
 * 注册（后端注册成功后会自动登录）
 *
 * @param {{username: string, password: string, nickname?: string}} payload
 * @returns {Promise<object>} 当前用户
 */
export async function register(payload) {
  const user = await request.post('/user/register', payload)
  authState.user = user
  authState.initialized = true
  return user
}

/**
 * 退出登录
 *
 * 即使请求失败（比如会话已经过期），前端也按退出成功处理。
 */
export async function logout() {
  try {
    await request.post('/user/logout')
  } catch (e) {
    // 会话本来就失效时不必打扰用户
  }
  authState.user = null
  authState.initialized = true
}

/**
 * 修改昵称
 *
 * @param {string} nickname 新昵称
 * @returns {Promise<object>} 更新后的用户
 */
export async function updateNickname(nickname) {
  const user = await request.put('/user/me', { nickname })
  authState.user = user
  return user
}

/**
 * 修改密码
 *
 * @param {string} oldPassword 原密码
 * @param {string} newPassword 新密码
 */
export async function changePassword(oldPassword, newPassword) {
  return request.put('/user/password', { oldPassword, newPassword })
}
