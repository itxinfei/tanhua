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

  // 从 localStorage 读取管理员信息
  const loadInfo = (): AdminInfo | null => {
    const info = localStorage.getItem('xfjy_admin_info')
    if (!info) return null
    try {
      return JSON.parse(info) as AdminInfo
    } catch {
      return null
    }
  }

  // 初始化时即恢复，保证首屏路由守卫/权限指令可用
  const adminInfo = ref<AdminInfo | null>(loadInfo())

  const isLoggedIn = computed(() => !!token.value)

  /**
   * 判断当前登录管理员是否具备任一角色。
   * roles 为空/未传表示不限制（登录即可）。
   */
  function hasRole(roles?: string | string[]): boolean {
    if (!roles || (Array.isArray(roles) && roles.length === 0)) return true
    const current = adminInfo.value?.role
    if (!current) return false
    const list = Array.isArray(roles) ? roles : [roles]
    return list.includes(current)
  }

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

  // 从 localStorage 恢复管理员信息（初始化已自动恢复一次，保留供手动刷新）
  function restoreAdminInfo() {
    adminInfo.value = loadInfo()
  }

  return { token, adminInfo, isLoggedIn, hasRole, login, logout, restoreAdminInfo }
})
