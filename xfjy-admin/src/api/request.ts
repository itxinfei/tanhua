import axios, { type InternalAxiosRequestConfig } from 'axios'
import { limitedMessage } from '@/utils/limitedMessage'
import router from '@/router'
import { log, error } from '@/utils/logger'

/**
 * 挂在请求 config 上的耗时计时字段。
 * axios 原生 InternalAxiosRequestConfig 不含该属性，用局部交叉类型扩展，
 * 替代用 any 断言绕过类型检查的写法。
 */
type TimedAxiosConfig = InternalAxiosRequestConfig & { __startAt?: number }

// 生产默认同源 /api（由 nginx 反代到网关），开发走 vite proxy
const request = axios.create({
  baseURL: import.meta.env.VITE_API_BASE_URL || '/api',
  timeout: 15000,
})

// 请求拦截器
request.interceptors.request.use(
  (config) => {
    const token = localStorage.getItem('xfjy_admin_token')
    if (token) {
      config.headers.Authorization = `Bearer ${token}`
    }
    // 记录请求起始时间（挂在 config 上，响应拦截器读取算耗时）
    ;(config as TimedAxiosConfig).__startAt = Date.now()
    log(`${config.method?.toUpperCase() || 'GET'} ${config.url}`, config.params || config.data || '')
    return config
  },
  (err) => Promise.reject(err)
)

// 响应拦截器
request.interceptors.response.use(
  (response) => {
    const startAt = (response.config as TimedAxiosConfig).__startAt
    log(`${response.config?.method?.toUpperCase() || 'GET'} ${response.config?.url} → ${response.status} (${startAt ? Date.now() - startAt : 0}ms)`)
    const res = response.data
    if (res.code !== undefined && res.code !== 200) {
      limitedMessage.error(res.message || '请求失败')
      // 401: Token 过期或未授权
      if (res.code === 401) {
        localStorage.removeItem('xfjy_admin_token')
        localStorage.removeItem('xfjy_admin_info')
        router.push('/login')
      }
      return Promise.reject(new Error(res.message || '请求失败'))
    }
    return res
  },
  (err) => {
    const startAt = (err.config as TimedAxiosConfig | undefined)?.__startAt
    error(`${err.config?.method?.toUpperCase() || 'GET'} ${err.config?.url} ✗ (${startAt ? Date.now() - startAt : 0}ms)`, err.message)
    if (err.response) {
      const { status } = err.response
      if (status === 401) {
        // 优先展示后端返回的具体原因（如登录接口的"账号或密码错误"）
        const backendMsg = err.response.data?.message
        limitedMessage.error(backendMsg || '登录已过期，请重新登录')
        // 登录接口本身返回 401 时不跳转（用户已在登录页），其余 401 清 token 跳登录
        const isLoginRequest = err.config?.url?.includes('/login')
        if (!isLoginRequest) {
          localStorage.removeItem('xfjy_admin_token')
          localStorage.removeItem('xfjy_admin_info')
          router.push('/login')
        }
      } else if (status === 403) {
        limitedMessage.error('没有权限执行此操作')
      } else if (status === 500) {
        limitedMessage.error('服务器内部错误')
      } else {
        limitedMessage.error(err.response.data?.message || '请求失败')
      }
    } else if (err.message.includes('timeout')) {
      limitedMessage.error('请求超时，请稍后重试')
    } else {
      limitedMessage.error('网络异常，请检查网络连接')
    }
    return Promise.reject(err)
  }
)

export default request