import request from './request'
import type { PageResult } from './user'

export interface ReviewItem {
  id: number
  type: 'POST' | 'PHOTO' | 'VIDEO'
  userId: number
  userNickname: string
  userAvatar: string
  content: string
  medias: string[]
  status: 'PENDING' | 'APPROVED' | 'REJECTED'
  submittedAt: string
  reviewer?: string
  reviewedAt?: string
  rejectReason?: string
}

export interface ReviewQuery {
  page: number
  pageSize: number
  type?: string
  status?: string
}

/** 分页查询待审核/已审核内容 */
export function getReviewPageApi(params: ReviewQuery) {
  return request.get<any, PageResult<ReviewItem>>('/admin/reviews', { params })
}

/** 通过审核（单个） */
export function approveReviewApi(id: number) {
  return request.put(`/admin/reviews/${id}/approve`)
}

/** 驳回审核（单个） */
export function rejectReviewApi(id: number, reason: string) {
  return request.put(`/admin/reviews/${id}/reject`, { reason })
}

/** 批量通过 */
export function batchApproveApi(ids: number[]) {
  return request.put('/admin/reviews/batch-approve', { ids })
}

/** 批量驳回 */
export function batchRejectApi(ids: number[], reason: string) {
  return request.put('/admin/reviews/batch-reject', { ids, reason })
}