import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import { login, logout, getUserInfo } from '@/api/auth'
import type { LoginForm, UserInfo } from '@/types/user'

export const useUserStore = defineStore('user', () => {
  const token = ref<string>('')
  const userInfo = ref<UserInfo | null>(null)

  // 默认头像
  const DEFAULT_AVATAR = 'https://cube.elemecdn.com/3/7c/3ea6beec64369c2642b92c6726f1epng.png'
  
  const isLogin = computed(() => !!token.value)
  const userId = computed(() => userInfo.value?.userId)
  const username = computed(() => userInfo.value?.username)
  const nickname = computed(() => userInfo.value?.nickname || userInfo.value?.username)
  const avatar = computed(() => userInfo.value?.avatar || DEFAULT_AVATAR)
  const isAdmin = computed(() => userInfo.value?.role === 'ROLE_ADMIN')

  // 登录
  async function doLogin(form: LoginForm) {
    const res = await login(form)
    token.value = res.data.token || ''
    // 确保 userId 是字符串，避免大数字精度丢失
    userInfo.value = {
      ...res.data,
      userId: String(res.data.userId)
    }
    return res
  }

  // 登出
  async function doLogout() {
    try {
      await logout()
    } finally {
      token.value = ''
      userInfo.value = null
    }
  }

  // 获取用户信息
  async function fetchUserInfo() {
    const res = await getUserInfo()
    // 确保 userId 是字符串，避免大数字精度丢失
    userInfo.value = {
      ...res.data,
      userId: String(res.data.userId)
    }
    return res
  }

  // 更新头像
  function updateAvatar(avatarUrl: string) {
    if (userInfo.value) {
      userInfo.value = { ...userInfo.value, avatar: avatarUrl }
    }
  }

  // 重置状态
  function resetState() {
    token.value = ''
    userInfo.value = null
  }

  return {
    token,
    userInfo,
    isLogin,
    userId,
    username,
    nickname,
    avatar,
    isAdmin,
    doLogin,
    doLogout,
    fetchUserInfo,
    updateAvatar,
    resetState
  }
}, {
  persist: {
    key: 'jelly-user',
    paths: ['token', 'userInfo']
  }
})
