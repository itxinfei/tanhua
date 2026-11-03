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
  return request.get<any, SysConfigItem[]>('/admin/sys-config')
}