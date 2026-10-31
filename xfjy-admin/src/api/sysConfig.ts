import request from './request'

/** IM 通道类型 */
export type ImProvider = 'wukongim' | 'huanxin'

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

/** 获取当前 IM 通道 */
export function getImProviderApi() {
  return request.get<any, { provider: ImProvider }>('/admin/sys-config/im-provider')
}

/** 切换 IM 通道 */
export function switchImProviderApi(provider: ImProvider) {
  return request.put<any, { success: boolean; provider: string; message: string }>(
    '/admin/sys-config/im-provider',
    { provider }
  )
}