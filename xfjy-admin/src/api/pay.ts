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
}

export interface PageResult<T> {
  total: number
  list: T[]
}

/** 分页查询支付订单 */
export function getPayOrderPageApi(params: PayOrderQuery) {
  return request.get<any, PageResult<PayOrderItem>>('/admin/pay-orders', { params })
}