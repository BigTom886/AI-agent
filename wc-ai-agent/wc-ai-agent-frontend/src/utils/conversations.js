import { generateChatId } from './chatId'

/**
 * 会话（对话）列表 —— 按用户保存在 localStorage
 *
 * 后端按 chatId（UUID）保存对话记忆，chatId 变了就等于换了一个会话。
 * 之前 chatId 每次进页面都重新生成，所以「刷新页面 / 重新登录」后
 * 看到的一定是一个全新的空会话，历史消息还在库里却找不回来。
 *
 * 这里把 chatId 持久化下来，并且按用户名隔离：
 *   同一个用户 → 沿用上次的会话，能看到以前的对话
 *   不同用户   → 各自的会话，互不干扰
 */

/** 最多保留多少个会话（localStorage 空间有限） */
const MAX_CONVERSATIONS = 20

const storageKey = (username) => `wc_conversations_${username}`

/**
 * 读取某个用户的会话列表（最近使用的在前）
 *
 * @param {string} username 用户名
 * @returns {Array<{id: string, title: string, createdAt: number, updatedAt: number}>}
 */
export function getConversations(username) {
  try {
    const raw = localStorage.getItem(storageKey(username))
    const list = raw ? JSON.parse(raw) : []
    return Array.isArray(list) ? list : []
  } catch (e) {
    // 存储被禁用或数据损坏时按「没有会话」处理
    return []
  }
}

function saveConversations(username, list) {
  try {
    localStorage.setItem(storageKey(username), JSON.stringify(list.slice(0, MAX_CONVERSATIONS)))
  } catch (e) {
    // 隐私模式 / 存储写满时忽略，不影响聊天
  }
}

/**
 * 当前会话 id：最近使用过的那个
 *
 * @param {string} username 用户名
 * @returns {string} 没有会话时返回空串
 */
export function getCurrentChatId(username) {
  const list = getConversations(username)
  return list[0]?.id || ''
}

/**
 * 新建一个会话并放到最前面
 *
 * @param {string} username 用户名
 * @param {string} title 会话标题（一般是第一条用户消息）
 * @returns {string} 新会话的 id
 */
export function createConversation(username, title = '') {
  const list = getConversations(username)
  const now = Date.now()
  const conversation = { id: generateChatId(), title, createdAt: now, updatedAt: now }
  list.unshift(conversation)
  saveConversations(username, list)
  return conversation.id
}

/**
 * 记录一次使用：更新标题（只记第一条）和最后使用时间
 *
 * @param {string} username 用户名
 * @param {string} id 会话 id
 * @param {string} title 本次发送的用户消息
 */
export function touchConversation(username, id, title = '') {
  const list = getConversations(username)
  const item = list.find((conversation) => conversation.id === id)
  if (!item) return
  if (title && !item.title) {
    item.title = title.slice(0, 20)
  }
  item.updatedAt = Date.now()
  // 最近用过的排最前
  list.sort((a, b) => b.updatedAt - a.updatedAt)
  saveConversations(username, list)
}

/**
 * 从列表里移除一个会话（只是本地列表，数据库里的消息仍然保留）
 *
 * @param {string} username 用户名
 * @param {string} id 会话 id
 */
export function removeConversation(username, id) {
  const list = getConversations(username).filter((conversation) => conversation.id !== id)
  saveConversations(username, list)
}
