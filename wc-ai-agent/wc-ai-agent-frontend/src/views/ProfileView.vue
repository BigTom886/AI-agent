<template>
  <div class="profile-page">
    <div class="profile-bg"></div>

    <header class="page-topbar">
      <router-link class="back-link" to="/">← 返回首页</router-link>
      <button class="logout-btn" :disabled="loggingOut" @click="handleLogout">
        {{ loggingOut ? '退出中…' : '退出登录' }}
      </button>
    </header>

    <main class="profile-main">
      <section class="card user-card">
        <div class="avatar">👤</div>
        <div class="user-info">
          <h1>{{ user?.nickname || '加载中…' }}</h1>
          <p class="username">@{{ user?.username }}</p>
          <p v-if="user?.createdAt" class="meta">注册于 {{ formatDate(user.createdAt) }}</p>
        </div>
      </section>

      <section class="card">
        <h2 class="card-title">修改昵称</h2>
        <form @submit.prevent="saveNickname">
          <label class="field">
            <span class="field-label">昵称</span>
            <input
              v-model.trim="nickname"
              type="text"
              maxlength="32"
              placeholder="1-32 个字符"
              :disabled="savingNickname"
            />
          </label>
          <p v-if="nicknameMessage" class="form-tip" :class="{ error: nicknameIsError }">
            {{ nicknameMessage }}
          </p>
          <button class="btn primary" type="submit" :disabled="savingNickname || !nickname">
            {{ savingNickname ? '保存中…' : '保存昵称' }}
          </button>
        </form>
      </section>

      <section class="card">
        <h2 class="card-title">修改密码</h2>
        <form @submit.prevent="savePassword">
          <label class="field">
            <span class="field-label">原密码</span>
            <input
              v-model="oldPassword"
              type="password"
              autocomplete="current-password"
              placeholder="请输入当前密码"
              :disabled="savingPassword"
            />
          </label>
          <label class="field">
            <span class="field-label">新密码</span>
            <input
              v-model="newPassword"
              type="password"
              autocomplete="new-password"
              placeholder="6-64 位"
              :disabled="savingPassword"
            />
          </label>
          <label class="field">
            <span class="field-label">确认新密码</span>
            <input
              v-model="confirmPassword"
              type="password"
              autocomplete="new-password"
              placeholder="再次输入新密码"
              :disabled="savingPassword"
            />
          </label>
          <p v-if="passwordMessage" class="form-tip" :class="{ error: passwordIsError }">
            {{ passwordMessage }}
          </p>
          <button class="btn" type="submit" :disabled="savingPassword">
            {{ savingPassword ? '提交中…' : '修改密码' }}
          </button>
        </form>
      </section>
    </main>
  </div>
</template>

<script setup>
import { computed, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import { authState, changePassword, logout, updateNickname } from '../utils/auth'
import { errorMessage } from '../utils/error'

const router = useRouter()

const user = computed(() => authState.user)
const loggingOut = ref(false)

const nickname = ref('')
watch(
  user,
  (value) => {
    if (value) {
      nickname.value = value.nickname
    }
  },
  { immediate: true }
)

const savingNickname = ref(false)
const nicknameMessage = ref('')
const nicknameIsError = ref(false)

const oldPassword = ref('')
const newPassword = ref('')
const confirmPassword = ref('')
const savingPassword = ref(false)
const passwordMessage = ref('')
const passwordIsError = ref(false)

const redirectToLogin = () => {
  router.replace({ name: 'login', query: { redirect: '/profile' } })
}

/** 会话过期统一跳登录；返回 true 表示已经处理 */
const handleUnauthorized = (error) => {
  if (error?.response?.status === 401) {
    redirectToLogin()
    return true
  }
  return false
}

const saveNickname = async () => {
  nicknameMessage.value = ''
  if (!nickname.value) {
    nicknameIsError.value = true
    nicknameMessage.value = '昵称不能为空'
    return
  }
  if (nickname.value.length > 32) {
    nicknameIsError.value = true
    nicknameMessage.value = '昵称不能超过 32 个字符'
    return
  }
  if (nickname.value === user.value?.nickname) {
    nicknameIsError.value = false
    nicknameMessage.value = '昵称没有变化'
    return
  }

  savingNickname.value = true
  try {
    await updateNickname(nickname.value)
    nicknameIsError.value = false
    nicknameMessage.value = '昵称已更新'
  } catch (e) {
    if (handleUnauthorized(e)) return
    nicknameIsError.value = true
    nicknameMessage.value = errorMessage(e, '保存失败，请稍后重试')
  } finally {
    savingNickname.value = false
  }
}

const savePassword = async () => {
  passwordMessage.value = ''
  if (!oldPassword.value) {
    passwordIsError.value = true
    passwordMessage.value = '请输入原密码'
    return
  }
  if (newPassword.value.length < 6 || newPassword.value.length > 64) {
    passwordIsError.value = true
    passwordMessage.value = '新密码长度需为 6-64 位'
    return
  }
  if (newPassword.value !== confirmPassword.value) {
    passwordIsError.value = true
    passwordMessage.value = '两次输入的新密码不一致'
    return
  }
  if (newPassword.value === oldPassword.value) {
    passwordIsError.value = true
    passwordMessage.value = '新密码不能与原密码相同'
    return
  }

  savingPassword.value = true
  try {
    await changePassword(oldPassword.value, newPassword.value)
    passwordIsError.value = false
    passwordMessage.value = '密码已修改，下次请使用新密码登录'
    oldPassword.value = ''
    newPassword.value = ''
    confirmPassword.value = ''
  } catch (e) {
    if (handleUnauthorized(e)) return
    passwordIsError.value = true
    passwordMessage.value = errorMessage(e, '修改失败，请稍后重试')
  } finally {
    savingPassword.value = false
  }
}

const handleLogout = async () => {
  loggingOut.value = true
  await logout()
  loggingOut.value = false
  router.replace({ name: 'login' })
}

const formatDate = (value) => {
  const date = new Date(value)
  return Number.isNaN(date.getTime()) ? value : date.toLocaleString('zh-CN')
}
</script>

<style scoped>
.profile-page {
  min-height: 100%;
  min-height: 100dvh;
  position: relative;
  padding-bottom: 48px;
  overflow-x: hidden;
}

.profile-bg {
  position: fixed;
  inset: 0;
  background: radial-gradient(
      circle at 10% 10%,
      rgba(255, 229, 236, 0.7) 0%,
      transparent 50%
    ),
    radial-gradient(circle at 90% 80%, rgba(214, 233, 255, 0.6) 0%, transparent 50%);
  z-index: 0;
  pointer-events: none;
}

.page-topbar {
  position: relative;
  z-index: 2;
  max-width: 640px;
  margin: 0 auto;
  padding: 24px 20px 0;
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.back-link {
  font-size: 14px;
  color: var(--text-sub);
}

.back-link:hover {
  color: var(--text-main);
}

.logout-btn {
  padding: 8px 16px;
  border: 1px solid #f0c4cd;
  border-radius: 20px;
  background: rgba(255, 255, 255, 0.8);
  color: #d9536f;
  font-size: 13px;
  cursor: pointer;
  transition: background 0.2s;
}

.logout-btn:hover:not(:disabled) {
  background: #fff1f4;
}

.logout-btn:disabled {
  opacity: 0.6;
  cursor: not-allowed;
}

.profile-main {
  position: relative;
  z-index: 1;
  max-width: 640px;
  margin: 20px auto 0;
  padding: 0 20px;
  display: flex;
  flex-direction: column;
  gap: 18px;
}

.card {
  padding: 24px;
  background: rgba(255, 255, 255, 0.94);
  backdrop-filter: blur(10px);
  border: 1px solid var(--border-color);
  border-radius: var(--radius-lg);
  box-shadow: var(--shadow-sm);
}

.user-card {
  display: flex;
  align-items: center;
  gap: 18px;
}

.avatar {
  width: 64px;
  height: 64px;
  flex-shrink: 0;
  border-radius: 50%;
  background: rgba(74, 124, 255, 0.12);
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 30px;
}

.user-info h1 {
  font-size: 22px;
  color: var(--text-main);
  margin-bottom: 4px;
}

.username {
  font-size: 14px;
  color: var(--text-sub);
  margin-bottom: 4px;
}

.meta {
  font-size: 12px;
  color: var(--text-light);
}

.card-title {
  font-size: 16px;
  font-weight: 600;
  color: var(--text-main);
  margin-bottom: 16px;
}

.field {
  display: block;
  margin-bottom: 14px;
}

.field-label {
  display: block;
  margin-bottom: 6px;
  font-size: 13px;
  color: var(--text-sub);
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

.btn {
  padding: 10px 20px;
  border: 1px solid var(--primary);
  border-radius: var(--radius-sm);
  background: #fff;
  color: var(--primary);
  font-size: 14px;
  font-weight: 600;
  cursor: pointer;
  transition: background 0.2s, opacity 0.2s;
}

.btn.primary {
  background: var(--primary);
  color: #fff;
}

.btn:hover:not(:disabled) {
  opacity: 0.88;
}

.btn:disabled {
  opacity: 0.55;
  cursor: not-allowed;
}

.form-tip {
  margin: 0 0 14px;
  font-size: 13px;
  color: #2e9e6b;
}

.form-tip.error {
  color: #d9536f;
}

@media (max-width: 480px) {
  .page-topbar {
    padding: 16px 16px 0;
  }

  .profile-main {
    padding: 0 16px;
  }

  .card {
    padding: 20px 18px;
  }

  .user-card {
    gap: 14px;
  }

  .avatar {
    width: 54px;
    height: 54px;
    font-size: 26px;
  }
}
</style>
