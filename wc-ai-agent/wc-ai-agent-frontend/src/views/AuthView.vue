<template>
  <div class="auth-page">
    <div class="auth-bg"></div>

    <div class="auth-card">
      <div class="auth-logo">🤖</div>
      <h1 class="auth-title">{{ isRegister ? '创建账号' : '欢迎回来' }}</h1>
      <p class="auth-subtitle">
        {{ isRegister ? '注册后即可使用全部 AI 应用' : '登录后继续你的 AI 之旅' }}
      </p>

      <form class="auth-form" @submit.prevent="submit">
        <label class="field">
          <span class="field-label">用户名</span>
          <input
            v-model.trim="form.username"
            type="text"
            autocomplete="username"
            placeholder="3-32 位字母、数字、下划线或中划线"
            :disabled="loading"
          />
        </label>

        <label v-if="isRegister" class="field">
          <span class="field-label">昵称 <em>（可选）</em></span>
          <input
            v-model.trim="form.nickname"
            type="text"
            autocomplete="nickname"
            placeholder="不填则与用户名相同"
            :disabled="loading"
          />
        </label>

        <label class="field">
          <span class="field-label">密码</span>
          <input
            v-model="form.password"
            type="password"
            :autocomplete="isRegister ? 'new-password' : 'current-password'"
            placeholder="6-64 位"
            :disabled="loading"
          />
        </label>

        <label v-if="isRegister" class="field">
          <span class="field-label">确认密码</span>
          <input
            v-model="form.confirmPassword"
            type="password"
            autocomplete="new-password"
            placeholder="再次输入密码"
            :disabled="loading"
          />
        </label>

        <p v-if="error" class="form-error">{{ error }}</p>

        <button class="submit-btn" type="submit" :disabled="loading">
          {{ loading ? '请稍候…' : isRegister ? '注册并登录' : '登录' }}
        </button>
      </form>

      <p class="switch-tip">
        <template v-if="isRegister">
          已有账号？<router-link :to="switchLink">去登录</router-link>
        </template>
        <template v-else>
          还没有账号？<router-link :to="switchLink">去注册</router-link>
        </template>
      </p>

      <router-link class="back-home" to="/">← 返回首页</router-link>
    </div>
  </div>
</template>

<script setup>
import { computed, reactive, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { login, register } from '../utils/auth'
import { errorMessage } from '../utils/error'

const route = useRoute()
const router = useRouter()

const isRegister = computed(() => route.name === 'register')

const form = reactive({
  username: '',
  nickname: '',
  password: '',
  confirmPassword: '',
})

const loading = ref(false)
const error = ref('')

// 登录/注册之间切换时，保留已填的用户名，只清掉错误提示
watch(isRegister, () => {
  error.value = ''
})

const switchLink = computed(() => ({
  name: isRegister.value ? 'login' : 'register',
  query: route.query.redirect ? { redirect: route.query.redirect } : {},
}))

const validate = () => {
  if (!/^[A-Za-z0-9_-]{3,32}$/.test(form.username)) {
    return '用户名需为 3-32 位字母、数字、下划线或中划线'
  }
  if (form.password.length < 6 || form.password.length > 64) {
    return '密码长度需为 6-64 位'
  }
  if (isRegister.value && form.password !== form.confirmPassword) {
    return '两次输入的密码不一致'
  }
  if (isRegister.value && form.nickname.length > 32) {
    return '昵称不能超过 32 个字符'
  }
  return ''
}

/** 只允许站内相对路径，避免 ?redirect=//evil.com 这类跳转 */
const safeRedirect = (value) => {
  if (typeof value !== 'string') return '/'
  if (!value.startsWith('/') || value.startsWith('//')) return '/'
  return value
}

const submit = async () => {
  error.value = validate()
  if (error.value) return

  loading.value = true
  try {
    if (isRegister.value) {
      await register({
        username: form.username,
        password: form.password,
        nickname: form.nickname || null,
      })
    } else {
      await login({
        username: form.username,
        password: form.password,
      })
    }
    router.replace(safeRedirect(route.query.redirect))
  } catch (e) {
    error.value = errorMessage(
      e,
      isRegister.value ? '注册失败，请稍后重试' : '登录失败，请稍后重试'
    )
  } finally {
    loading.value = false
  }
}
</script>

<style scoped>
.auth-page {
  min-height: 100%;
  min-height: 100dvh;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 32px 16px;
  position: relative;
  overflow: hidden;
}

.auth-bg {
  position: fixed;
  inset: 0;
  background: radial-gradient(
      circle at 10% 20%,
      rgba(255, 229, 236, 0.85) 0%,
      transparent 50%
    ),
    radial-gradient(circle at 90% 80%, rgba(214, 233, 255, 0.7) 0%, transparent 50%);
  z-index: 0;
  pointer-events: none;
}

.auth-card {
  position: relative;
  z-index: 1;
  width: 100%;
  max-width: 380px;
  padding: 36px 32px 28px;
  background: rgba(255, 255, 255, 0.94);
  backdrop-filter: blur(12px);
  border-radius: var(--radius-lg);
  box-shadow: var(--shadow-lg);
  text-align: center;
}

.auth-logo {
  width: 64px;
  height: 64px;
  margin: 0 auto 16px;
  font-size: 32px;
  border-radius: 20px;
  background: rgba(255, 255, 255, 0.95);
  box-shadow: 0 6px 20px rgba(255, 107, 129, 0.18);
  display: flex;
  align-items: center;
  justify-content: center;
}

.auth-title {
  font-size: 24px;
  font-weight: 700;
  color: var(--text-main);
  margin-bottom: 6px;
}

.auth-subtitle {
  font-size: 14px;
  color: var(--text-sub);
  margin-bottom: 26px;
}

.auth-form {
  text-align: left;
}

.field {
  display: block;
  margin-bottom: 16px;
}

.field-label {
  display: block;
  margin-bottom: 6px;
  font-size: 13px;
  color: var(--text-sub);
}

.field-label em {
  font-style: normal;
  color: var(--text-light);
}

.field input {
  width: 100%;
  padding: 11px 13px;
  border: 1px solid var(--border-color);
  border-radius: var(--radius-sm);
  background: #fff;
  font-size: 14px;
  color: var(--text-main);
  transition: border-color 0.2s, box-shadow 0.2s;
}

.field input:focus {
  outline: none;
  border-color: var(--primary);
  box-shadow: 0 0 0 3px rgba(74, 124, 255, 0.12);
}

.field input:disabled {
  background: #f7f8fa;
  cursor: not-allowed;
}

.form-error {
  margin: 0 0 14px;
  padding: 9px 12px;
  border-radius: var(--radius-sm);
  background: #fff1f2;
  color: #d9536f;
  font-size: 13px;
  line-height: 1.5;
  text-align: left;
}

.submit-btn {
  width: 100%;
  padding: 12px;
  margin-top: 4px;
  border: none;
  border-radius: var(--radius-sm);
  background: linear-gradient(135deg, #ff6b81 0%, #4a7cff 100%);
  color: #fff;
  font-size: 15px;
  font-weight: 600;
  cursor: pointer;
  transition: opacity 0.2s, transform 0.2s;
}

.submit-btn:hover:not(:disabled) {
  transform: translateY(-1px);
  opacity: 0.94;
}

.submit-btn:disabled {
  opacity: 0.65;
  cursor: not-allowed;
}

.switch-tip {
  margin-top: 20px;
  font-size: 13px;
  color: var(--text-sub);
}

.switch-tip a {
  color: var(--primary);
  font-weight: 600;
}

.back-home {
  display: inline-block;
  margin-top: 14px;
  font-size: 13px;
  color: var(--text-light);
}

.back-home:hover {
  color: var(--text-sub);
}

@media (max-width: 480px) {
  .auth-card {
    padding: 28px 22px 24px;
  }
}
</style>
