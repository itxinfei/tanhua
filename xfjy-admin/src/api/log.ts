import request from './request'

export interface OperationLogItem {
  id: number
  adminId: number
  adminUsername: string
  module: string
  action: string
  targetId?: string
  detail?: string
  ip?: string
  created: string
}

export interface OperationLogQuery {
  page: number
  pageSize: number
  module?: string
  adminUsername?: string
  startDate?: string
  endDate?: string
}

export interface PageResult<T> {
  total: number
  list: T[]
}

/** 分页查询操作日志 */
export function getLogPageApi(params: OperationLogQuery) {
  return request.get<any, PageResult<OperationLogItem>>('/admin/operation-logs', { params })
}

/** 查询日志详情 */
export function getLogDetailApi(id: number) {
  return request.get<any, OperationLogItem>(`/admin/operation-logs/${id}`)
}

/** 导出日志（CSV） */
export function exportLogsApi(params: { module?: string; adminUsername?: string; startDate?: string; endDate?: string }) {
  return request.get('/admin/operation-logs/export', { params, responseType: 'blob' })
}
