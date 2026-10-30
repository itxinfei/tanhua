import request from './request'
import type { PageResult } from './user'

export interface ReportItem {
  id: number
  reporterId: number
  reporterNickname: string
  reporterAvatar: string
  targetUserId: number
  targetUserNickname: string
  targetType: 'USER' | 'POST' | 'COMMENT' | 'MESSAGE'
  targetId: number
  reason: string
  description: string
  evidence: string[]
  status: 'PENDING' | 'PROCESSING' | 'RESOLVED' | 'DISMISSED'
  handler?: string
  handledAt?: string
  handleResult?: string
  createdAt: string
}

export interface ReportQuery {
  page: number
  pageSize: number
  status?: string
  targetType?: string
  targetUserId?: number
}

/** 分页查询举报 */
export function getReportPageApi(params: ReportQuery) {
  return request.get<any, PageResult<ReportItem>>('/admin/reports', { params })
}

/** 获取举报详情 */
export function getReportDetailApi(id: number) {
  return request.get<any, ReportItem>(`/admin/reports/${id}`)
}

/** 处理举报（封禁目标用户 / 驳回） */
export function handleReportApi(id: number, result: 'RESOLVED' | 'DISMISSED', note: string) {
  return request.put(`/admin/reports/${id}/handle`, { result, note })
}

/** 批量处理举报 */
export function batchHandleReportApi(ids: number[], result: 'RESOLVED' | 'DISMISSED', note: string) {
  return request.put('/admin/reports/batch-handle', { ids, result, note })
}