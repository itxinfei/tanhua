import request from './request'
import type { AdminInfo } from '@/stores/auth'

export interface LoginResult {
  token: string
  admin: AdminInfo
}

/** 管理员登录 */
export function loginApi(username: string, password: string) {
  return request.post<any, LoginResult>('/admin/login', { username, password })
}

/** 获取当前管理员信息 */
export function getAdminInfoApi() {
  return request.get<any, AdminInfo>('/admin/info')
}

/** 修改密码 */
export function changePasswordApi(oldPassword: string, newPassword: string) {
  return request.put('/admin/password', { oldPassword, newPassword })
}