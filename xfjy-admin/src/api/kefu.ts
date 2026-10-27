import request from './request'
import type { PageResult } from './user'

/** 意见反馈项（后端 Feedback 实体，created/updated 为 Date 序列化值） */
export interface FeedbackItem {
  id: number
  userId: number
  content: string
  contact: string | null
  reply: string | null
  status: number // 0待处理 1已回复 2已关闭
  created: string | number
  updated: string | number
}

/** FAQ 项 */
export interface FaqItem {
  id: number
  category: string
  question: string
  answer: string
  sort: number
  status: number // 1启用 0停用
  created: string | number
  updated: string | number
}

export interface FeedbackQuery {
  page: number
  pageSize: number
  status?: number
}

export interface FaqQuery {
  page: number
  pageSize: number
  category?: string
  status?: number
}

export interface FaqForm {
  id?: number
  category: string
  question: string
  answer: string
  sort: number
}

/** 分页查询反馈列表 */
export function getFeedbackPageApi(params: FeedbackQuery) {
  return request.get<any, PageResult<FeedbackItem>>('/admin/kefu/feedback', { params })
}

/** 回复反馈 */
export function replyFeedbackApi(id: number, reply: string) {
  return request.put(`/admin/kefu/feedback/${id}/reply`, { reply })
}

/** 关闭反馈 */
export function closeFeedbackApi(id: number) {
  return request.put(`/admin/kefu/feedback/${id}/close`)
}

/** 分页查询 FAQ */
export function getFaqPageApi(params: FaqQuery) {
  return request.get<any, PageResult<FaqItem>>('/admin/kefu/faq', { params })
}

/** 新增 FAQ */
export function createFaqApi(data: FaqForm) {
  return request.post('/admin/kefu/faq', data)
}

/** 编辑 FAQ */
export function updateFaqApi(id: number, data: FaqForm) {
  return request.put(`/admin/kefu/faq/${id}`, data)
}

/** FAQ 启用/停用 */
export function toggleFaqApi(id: number, status: number) {
  return request.put(`/admin/kefu/faq/${id}/status`, { status })
}

/** 删除 FAQ */
export function deleteFaqApi(id: number) {
  return request.delete(`/admin/kefu/faq/${id}`)
}

/** 客服环信登录凭证（工作台 SDK 登录用） */
export interface KefuCredential {
  username: string
  token: string | null
}

/** 获取客服环信登录凭证 */
export function getAgentCredentialApi() {
  return request.get<any, KefuCredential>('/admin/kefu/agent/credential')
}