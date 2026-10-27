import request from './request'

/** VIP 套餐（金额单位：分） */
export interface VipPackageItem {
  id: number
  name: string
  /** VIP 等级：1-月卡 2-季卡 3-年卡 */
  level: number
  price: number
  originalPrice: number
  durationDays: number
  description: string
  /** 状态：1-上架 0-下架 */
  status: number
  sort: number
  created: string
  updated: string
}

/** 增值权益 */
export interface BenefitItem {
  id: number
  serviceCode: string
  name: string
  vipLevelRequired: number
  dailyLimit: number | null
  description: string
  status: number
  sort: number
  created: string
  updated: string
}

export interface VipQuery {
  page: number
  pageSize: number
  name?: string
  status?: number
}

export interface VipPackageForm {
  name: string
  level: number
  price: number
  originalPrice?: number
  durationDays: number
  description?: string
  sort?: number
}

export interface BenefitForm {
  vipLevelRequired: number
  dailyLimit?: number | null
  description?: string
  sort?: number
}

export interface PageResult<T> {
  total: number
  list: T[]
}

/** 分页查询 VIP 套餐 */
export function getVipPackagePageApi(params: VipQuery) {
  return request.get<any, PageResult<VipPackageItem>>('/admin/vip/packages', { params })
}

/** 新增 VIP 套餐 */
export function createVipPackageApi(data: VipPackageForm) {
  return request.post('/admin/vip/packages', data)
}

/** 编辑 VIP 套餐 */
export function updateVipPackageApi(id: number, data: Partial<VipPackageForm>) {
  return request.put(`/admin/vip/packages/${id}`, data)
}

/** 套餐上架/下架 */
export function toggleVipPackageApi(id: number, status: number) {
  return request.put(`/admin/vip/packages/${id}/status`, { status })
}

/** 分页查询增值权益 */
export function getBenefitPageApi(params: VipQuery) {
  return request.get<any, PageResult<BenefitItem>>('/admin/vip/benefits', { params })
}

/** 编辑权益（serviceCode 不可改） */
export function updateBenefitApi(id: number, data: BenefitForm) {
  return request.put(`/admin/vip/benefits/${id}`, data)
}

/** 权益启用/停用 */
export function toggleBenefitApi(id: number, status: number) {
  return request.put(`/admin/vip/benefits/${id}/status`, { status })
}