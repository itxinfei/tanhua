import request from './request'
import type { PageResult } from './user'

export interface NoticeItem {
  id: number
  title: string
  content: string
  type: 'SYSTEM' | 'ACTIVITY' | 'UPDATE'
  status: 'DRAFT' | 'PUBLISHED' | 'OFFLINE'
  publisher: string
  publishedAt?: string
  createdAt: string
}

export interface NoticeQuery {
  page: number
  pageSize: number
  title?: string
  type?: string
  status?: string
}

export interface NoticeForm {
  id?: number
  title: string
  content: string
  type: 'SYSTEM' | 'ACTIVITY' | 'UPDATE'
}

/** 分页查询公告 */
export function getNoticePageApi(params: NoticeQuery) {
  return request.get<any, PageResult<NoticeItem>>('/admin/notices', { params })
}

/** 获取公告详情 */
export function getNoticeDetailApi(id: number) {
  return request.get<any, NoticeItem>(`/admin/notices/${id}`)
}

/** 新增公告 */
export function createNoticeApi(data: NoticeForm) {
  return request.post('/admin/notices', data)
}

/** 更新公告 */
export function updateNoticeApi(id: number, data: NoticeForm) {
  return request.put(`/admin/notices/${id}`, data)
}

/** 发布公告 */
export function publishNoticeApi(id: number) {
  return request.put(`/admin/notices/${id}/publish`)
}

/** 下线公告 */
export function offlineNoticeApi(id: number) {
  return request.put(`/admin/notices/${id}/offline`)
}

/** 删除公告 */
export function deleteNoticeApi(id: number) {
  return request.delete(`/admin/notices/${id}`)
}