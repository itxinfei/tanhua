import request from './request'
import type { AdminInfo } from '@/stores/auth'

export interface AdminAccount extends AdminInfo {
  status: string
  created: string
  updated: string
}

export interface AdminPageResult {
  total: number
  list: AdminAccount[]
}

/** 分页查询管理员列表 */
export function getAdminPageApi(params: {
  page: number
  pageSize: number
  keyword?: string
}) {
  return request.get<any, AdminPageResult>('/admin/accounts', { params })
}

/** 新增管理员 */
export function createAdminApi(data: {
  username: string
  password: string
  nickname: string
  role: string
}) {
  return request.post('/admin/accounts', data)
}

/** 编辑管理员 */
export function updateAdminApi(id: number, data: {
  nickname: string
  role: string
  avatar: string
}) {
  return request.put(`/admin/accounts/${id}`, data)
}

/** 禁用/启用管理员 */
export function toggleAdminStatusApi(id: number, status: string) {
  return request.put(`/admin/accounts/${id}/status`, { status })
}

/** 重置管理员密码 */
export function resetAdminPasswordApi(id: number, newPassword: string) {
  return request.put(`/admin/accounts/${id}/reset-password`, { newPassword })
}