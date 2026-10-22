import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import { loginApi } from '@/api/auth'

export interface AdminInfo {
  id: number
  username: string
  nickname: string
  avatar: string
  role: string
}

export const useAuthStore = defineStore('auth', () => {
  const token = ref<string>(localStorage.getItem('xfjy_admin_token') || '')
  const adminInfo = ref<AdminInfo | null>(null)

  const isLoggedIn = computed(() => !!token.value)

  async function login(username: string, password: string) {
    const res = await loginApi(username, password)
    token.value = res.token
    adminInfo.value = res.admin
    localStorage.setItem('xfjy_admin_token', res.token)
    localStorage.setItem('xfjy_admin_info', JSON.stringify(res.admin))
  }

  function logout() {
    token.value = ''
    adminInfo.value = null
    localStorage.removeItem('xfjy_admin_token')
    localStorage.removeItem('xfjy_admin_info')
  }

  // 从 localStorage 恢复管理员信息
  function restoreAdminInfo() {
    const info = localStorage.getItem('xfjy_admin_info')
    if (info) {
      try {
        adminInfo.value = JSON.parse(info)
      } catch {
        adminInfo.value = null
      }
    }
  }

  return { token, adminInfo, isLoggedIn, login, logout, restoreAdminInfo }
})