import request from './request'

export interface OperationLogItem {
  id: number
  adminId: number
  adminUsername: string
  module: string
  action: string
  targetId: string
  detail: string
  ip: string
  created: string
}

export interface LogPageResult {
  total: number
  list: OperationLogItem[]
}

/** 分页查询操作日志 */
export function getLogPageApi(params: {
  page: number
  pageSize: number
  module?: string
  adminUsername?: string
  startDate?: string
  endDate?: string
}) {
  return request.get<any, LogPageResult>('/admin/operation-logs', { params })
}