import type { Directive, DirectiveBinding } from 'vue'
import { useAuthStore } from '@/stores/auth'

/**
 * 按钮级角色权限指令。
 * 用法：v-perm="'ADMIN'" 或 v-perm="['ADMIN','SUPER_ADMIN']"
 * 当前登录管理员角色不在允许列表内则移除该元素。
 * 角色取值：SUPER_ADMIN / ADMIN / OPERATOR
 */
export const perm: Directive = {
  mounted(el: HTMLElement, binding: DirectiveBinding) {
    const allowed = binding.value
    const roles: string[] = Array.isArray(allowed) ? allowed : [String(allowed)]
    const authStore = useAuthStore()
    const currentRole = authStore.adminInfo?.role || ''
    if (!roles.includes(currentRole)) {
      el.parentNode?.removeChild(el)
    }
  },
}
