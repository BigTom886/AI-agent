/**
 * 从 Axios 错误里取出可展示的提示
 *
 * 后端 GlobalExceptionHandler 返回的是纯文本（如「用户名已存在」），
 * 所以优先直接用 response.data；没有响应时再按网络/超时分类兜底。
 *
 * @param {unknown} error Axios 错误
 * @param {string} fallback 兜底文案
 * @returns {string}
 */
export function errorMessage(error, fallback = '操作失败，请稍后重试') {
  const data = error?.response?.data
  if (typeof data === 'string' && data.trim()) {
    return data
  }
  if (error?.code === 'ECONNABORTED') {
    return '请求超时，请稍后重试'
  }
  if (!error?.response) {
    return '网络异常，请检查后端服务是否已启动'
  }
  return fallback
}
