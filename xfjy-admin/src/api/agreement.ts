import request from './request'
import type { PageResult } from './user'

export interface AgreementItem {
  id: number
  title: string
  content: string
  type: 'USER' | 'PRIVACY'
  status: 'DRAFT' | 'PUBLISHED' | 'OFFLINE'
  version: number
  publisher: string
  publishedAt?: string
  created?: string
  updated?: string
}

export interface AgreementQuery {
  page: number
  pageSize: number
  type?: string
  status?: string
}

export interface AgreementForm {
  id?: number
  title: string
  content: string
  type: 'USER' | 'PRIVACY'
}

/** 分页查询协议 */
export function getAgreementPageApi(params: AgreementQuery) {
  return request.get<any, PageResult<AgreementItem>>('/admin/agreements', { params })
}

/** 协议详情 */
export function getAgreementDetailApi(id: number) {
  return request.get<any, AgreementItem>(`/admin/agreements/${id}`)
}

/** 新增协议 */
export function createAgreementApi(data: AgreementForm) {
  return request.post('/admin/agreements', data)
}

/** 更新协议 */
export function updateAgreementApi(id: number, data: AgreementForm) {
  return request.put(`/admin/agreements/${id}`, data)
}

/** 发布协议 */
export function publishAgreementApi(id: number) {
  return request.put(`/admin/agreements/${id}/publish`)
}

/** 下线协议 */
export function offlineAgreementApi(id: number) {
  return request.put(`/admin/agreements/${id}/offline`)
}

/** 删除协议 */
export function deleteAgreementApi(id: number) {
  return request.delete(`/admin/agreements/${id}`)
}
