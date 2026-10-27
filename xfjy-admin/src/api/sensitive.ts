import request from './request'

/** 敏感词 */
export interface SensitiveWordItem {
  id: number
  word: string
  category: 'POLITICS' | 'PORN' | 'VIOLENCE' | 'AD' | 'OTHER'
  /** 级别：1-高危 2-中危 3-低危 */
  level: number
  /** 状态：1-启用 0-停用 */
  status: number
  created: string
  updated: string
}

export interface SensitiveWordQuery {
  page: number
  pageSize: number
  word?: string
  category?: string
  level?: number
  status?: number
}

export interface SensitiveWordForm {
  word: string
  category: string
  level: number
}

export interface PageResult<T> {
  total: number
  list: T[]
}

/** 分页查询敏感词 */
export function getSensitiveWordPageApi(params: SensitiveWordQuery) {
  return request.get<any, PageResult<SensitiveWordItem>>('/admin/sensitive-words', { params })
}

/** 新增敏感词 */
export function createSensitiveWordApi(data: SensitiveWordForm) {
  return request.post('/admin/sensitive-words', data)
}

/** 编辑敏感词 */
export function updateSensitiveWordApi(id: number, data: Partial<SensitiveWordForm>) {
  return request.put(`/admin/sensitive-words/${id}`, data)
}

/** 启用/停用敏感词 */
export function toggleSensitiveWordApi(id: number, status: number) {
  return request.put(`/admin/sensitive-words/${id}/status`, { status })
}

/** 删除敏感词 */
export function deleteSensitiveWordApi(id: number) {
  return request.delete(`/admin/sensitive-words/${id}`)
}

/** 手动刷新 DFA 词库 */
export function refreshSensitiveWordApi() {
  return request.put('/admin/sensitive-words/refresh')
}