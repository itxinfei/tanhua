/// <reference types="vite/client" />

interface ImportMetaEnv {
  /** 后端网关地址（开发联调用，如 http://192.168.1.100:8090），未配置则按构建模式回退 */
  readonly VITE_API_BASE_URL?: string;
}