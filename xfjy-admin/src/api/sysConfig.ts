import request from './request'

/** 系统配置项 */
export interface SysConfigItem {
  id: number
  configKey: string
  configValue: string
  description?: string
  created?: string | number
  updated?: string | number
}

/** 列出全部系统配置 */
export function getSysConfigListApi() {
  return request.get<unknown, SysConfigItem[]>('/admin/sys-config')
}

/** 更新系统配置（存在则更新，不存在则插入；约 30 秒内全节点生效） */
export function updateSysConfigApi(data: {
  configKey: string
  configValue: string
  description?: string
}) {
  return request.post<unknown, void>('/admin/sys-config/update', data)
}

/** 网关限流配置 */
export interface RateLimitConfig {
  limit: number
  windowSeconds: number
  defaultLimit: number
  defaultWindowSeconds: number
}

/** 查询当前网关限流配置 */
export function getRateLimitConfigApi() {
  return request.get<unknown, RateLimitConfig>('/admin/rate-limit')
}

/** 更新网关限流配置（约 30 秒内全节点生效） */
export function updateRateLimitConfigApi(data: { limit: number; windowSeconds: number }) {
  return request.put<unknown, void>('/admin/rate-limit', data)
}
