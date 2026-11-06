/**
 * 环境相关工具：统一维护开发环境豁免逻辑
 *
 * 背景：auth-guard 与 request 都需要判断当前是否处于本地开发环境，
 * 以决定是否跳过登录拦截 / 401 登出。两处逻辑独立维护会导致口径不一致
 * （如新增环境变量时只改一处），故抽到此处统一。
 */

/**
 * 是否为本地开发环境（localhost / 127.0.0.1）。
 * 注意：H5 端通过 location.hostname 判断；小程序/App 端 location 不存在，
 * 返回 false（符合预期：非浏览器环境不做 localhost 豁免）。
 */
export function isLocalDev(): boolean {
  if (typeof location === "undefined") return false;
  return location.hostname === "localhost" || location.hostname === "127.0.0.1";
}

/**
 * 是否启用开发环境豁免（跳过登录拦截 / 401 登出）。
 *
 * 生产构建时若需彻底关闭豁免，可在 .env.production 中设置
 * VITE_DISABLE_DEV_EXEMPTION=true，此时即使运行在 localhost 域名也会
 * 走正常鉴权流程（避免内网穿透 / 本地回环部署时 401 被静默忽略）。
 */
export function isDevExemptionEnabled(): boolean {
  if (import.meta.env.VITE_DISABLE_DEV_EXEMPTION === "true") return false;
  return isLocalDev();
}
