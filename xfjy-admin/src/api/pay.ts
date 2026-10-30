import request from './request'

/** 支付订单（金额单位：分） */
export interface PayOrderItem {
  id: number
  orderNo: string
  userId: number
  packageId: number
  packageName: string
  amount: number
  status: 'PENDING' | 'PAID' | 'EXPIRED' | 'CANCELLED'
  payType: 'SANDBOX' | 'ALIPAY' | 'WECHAT'
  paidTime: string | null
  expireTime: string | null
  created: string
  updated: string
}

export interface PayOrderQuery {
  page: number
  pageSize: number
  orderNo?: string
  status?: string
  userId?: number
  startDate?: string
  endDate?: string
}

export interface PayOrderSummary {
  total: number
  paidCount: number
  pendingCount: number
  totalAmount: number
}

export interface PageResult<T> {
  total: number
  list: T[]
}

/** 分页查询支付订单 */
export function getPayOrderPageApi(params: PayOrderQuery) {
  return request.get<any, PageResult<PayOrderItem>>('/admin/pay-orders', { params })
}

/** 查询订单汇总 */
export function getPayOrderSummaryApi(params: { status?: string; startDate?: string; endDate?: string }) {
  return request.get<any, PayOrderSummary>('/admin/pay-orders/summary', { params })
}

/** 导出订单（CSV） */
export function exportPayOrdersApi(params: { orderNo?: string; status?: string; userId?: number; startDate?: string; endDate?: string }) {
  return request.get('/admin/pay-orders/export', { params, responseType: 'blob' })
}