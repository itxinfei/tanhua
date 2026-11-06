/**
 * 全局网络请求封装
 * 基于 uni.request，统一处理 Token 注入、错误码映射、Loading
 */

import { log, error } from "@/utils/logger";
import { fail, info, networkError, networkOffline, networkTimeout } from "@/utils/ux";
import { shouldBlockRequest } from "@/utils/network-monitor";

/** 普通请求超时（毫秒）：小程序默认 60s 太长，弱网用户体验差 */
const REQUEST_TIMEOUT_MS = 15000;
/** 文件上传超时（毫秒）：大文件/弱网需要更长窗口 */
const UPLOAD_TIMEOUT_MS = 60000;

// 后端服务地址：优先读环境变量 VITE_API_BASE_URL（见 .env.development / .env.production）。
// 未配置时按构建模式回退：
//   - H5 生产：同源相对路径 /api，由 nginx 反代到网关 8090（见 deploy/docker/nginx.conf）
//   - App / 小程序生产：无同源概念，必须使用绝对地址，回退内网网关
//   - 开发：本机网关
// 开发联调可在 xfjy-app/.env.development 配置 VITE_API_BASE_URL=http://192.168.x.x:8090
let PRODUCTION_FALLBACK = "/api";
// #ifndef H5
// App / 小程序无同源概念：使用绝对地址；公网发布请把 .env.production 的
// VITE_API_BASE_URL 改成 https 公网域名（此回退地址仅在未配置时生效）
PRODUCTION_FALLBACK = "http://192.168.20.133:8090";
// #endif

export const BASE_URL: string =
  import.meta.env.VITE_API_BASE_URL ||
  (import.meta.env.MODE === "production"
    ? PRODUCTION_FALLBACK
    : "http://localhost:8090"); // 统一走网关(8090，避开8080端口占用)，由网关路由到 sso/server

// 请求配置类型
interface RequestOptions {
  url: string;
  method?: "GET" | "POST" | "PUT" | "DELETE";
  data?: Record<string, any> | string;
  header?: Record<string, string>;
  loading?: boolean; // 是否显示 loading
  auth?: boolean; // 是否需要 Token（默认 true）
}

// 后端统一响应体
interface ApiResponse<T = any> {
  code: number;
  message: string;
  data: T;
}

// Token 存取
export const getToken = (): string => {
  return uni.getStorageSync("xfjy_token") || "";
};

export const setToken = (token: string): void => {
  uni.setStorageSync("xfjy_token", token);
};

export const removeToken = (): void => {
  uni.removeStorageSync("xfjy_token");
};

/** 401 并发去重：多个在途请求同时过期时只清一次状态、只跳一次登录 */
let redirectingToLogin = false;

/** tab 页路径（无前导斜杠，与 getCurrentPages().route 格式一致）：回跳须 switchTab 而非 reLaunch */
const TAB_PAGES = new Set([
  "pages/match/index",
  "pages/circle/index",
  "pages/circle/publish",
  "pages/video/index",
  "pages/mine/index",
]);

/** 回跳目标是否 tab 页（tab 页须 switchTab，非 tab 页 reLaunch 带参数） */
export function isTabPage(path: string): boolean {
  const p = path.split("?")[0].replace(/^\//, "");
  return TAB_PAGES.has(p);
}

/**
 * 取当前页面路由+参数，编码为 redirect 查询参数附加到登录页 URL。
 * 登录成功后优先回跳原页面，避免 token 过期打断深层页/支付流程后上下文全丢。
 */
export function buildLoginUrlWithRedirect(): string {
  const LOGIN_URL = "/pages/auth/login";
  const pages = getCurrentPages();
  const cur = pages[pages.length - 1] as
    | { route?: string; options?: Record<string, string>; $page?: { options?: Record<string, string> } }
    | undefined;
  // 无页面栈（极早期启动）或已在登录页：不带 redirect，避免死循环回跳
  if (!cur || !cur.route || cur.route === "pages/auth/login") return LOGIN_URL;
  let target = "/" + cur.route;
  const opts = cur.options || cur.$page?.options || {};
  const query = Object.keys(opts)
    .filter((k) => opts[k] !== undefined && opts[k] !== null)
    .map((k) => `${k}=${encodeURIComponent(String(opts[k]))}`)
    .join("&");
  if (query) target += "?" + query;
  return `${LOGIN_URL}?redirect=${encodeURIComponent(target)}`;
}

/**
 * 登录过期统一处理：清本地 token + 重置 userStore 登录态 + reLaunch 登录页（带 redirect 回跳）
 * userStore 用动态 import，避免 request.ts ← store 的循环依赖
 */
export function handleUnauthorized(): void {
  // 开发环境 mock：localhost / 127.0.0.1 不清除 token、不重定向，便于本地调试
  if (
    typeof location !== "undefined" &&
    (location.hostname === "localhost" || location.hostname === "127.0.0.1")
  ) {
    return;
  }
  removeToken();
  if (redirectingToLogin) return;
  redirectingToLogin = true;
  import("@/store/user")
    .then((m) => {
      m.useUserStore().token = "";
    })
    .catch(() => {})
    .finally(() => {
      uni.reLaunch({
        url: buildLoginUrlWithRedirect(),
        complete: () => {
          redirectingToLogin = false;
        },
      });
    });
}

/**
 * 核心请求函数
 */
function request<T = any>(options: RequestOptions): Promise<T> {
  const {
    url,
    method = "GET",
    data,
    header = {},
    loading = false,
    auth = true,
  } = options;

  // 断网快速失败：不发请求直接提示，避免弱网等满 15s 超时（S1）
  if (shouldBlockRequest()) {
    networkOffline();
    return Promise.reject(new Error("网络已断开"));
  }

  if (loading) {
    uni.showLoading({ title: "加载中", mask: true });
  }

  // Token 注入
  if (auth) {
    const token = getToken();
    if (token) {
      header["Authorization"] = `Bearer ${token}`;
    }
  }

  const startAt = Date.now();
  log(`${method} ${url}`, data || "");

  return new Promise<T>((resolve, reject) => {
    uni.request({
      url: BASE_URL + url,
      method,
      data,
      timeout: REQUEST_TIMEOUT_MS,
      header: {
        "Content-Type": "application/json",
        ...header,
      },
      success: (res: any) => {
        log(`${method} ${url} → ${res.statusCode} (${Date.now() - startAt}ms)`);
        if (loading) uni.hideLoading();

        // HTTP 状态码处理（toast 统一走 ux 封装：同文案 5s 合并降噪，弱网并发失败不互相顶替）
        if (res.statusCode === 401) {
          // 登录接口 401=验证码错误（后端携带 message）：只提示不登出；
          // token 过期（无 body）才走统一登出流程
          const msg = (res.data as any)?.message;
          if (msg) {
            fail(msg, 3000);
            reject(new Error(msg));
            return;
          }
          handleUnauthorized();
          reject(new Error("登录已过期，请重新登录"));
          return;
        }

        // 423 账号锁定（登录连续失败 5 次）
        if (res.statusCode === 423) {
          const msg = (res.data as any)?.message || "账号已暂时锁定，请稍后再试";
          fail(msg, 3000);
          reject(new Error(msg));
          return;
        }

        // 402 付费墙拦截（滑动次数耗尽等 → 跳 VIP 引导页）
        if (res.statusCode === 402) {
          const msg = (res.data as any)?.message || "升级 VIP 解锁更多权益";
          info(msg, 2000);
          setTimeout(() => {
            uni.navigateTo({ url: "/pages/mine/vip?source=swipe_out" });
          }, 1500);
          reject(new Error(msg));
          return;
        }

        // 429 风控限流：操作过于频繁
        if (res.statusCode === 429) {
          fail("操作过于频繁，请稍后再试");
          reject(new Error("操作过于频繁"));
          return;
        }

        // 400 参数/格式错误：优先展示后端携带的 message（如换绑手机号格式错）
        if (res.statusCode === 400) {
          const msg = (res.data as any)?.message || "操作失败，请检查输入内容";
          fail(msg);
          reject(new Error(msg));
          return;
        }

        // 403 权限/账号状态错误：优先展示后端携带的 reason（如封禁原因）
        if (res.statusCode === 403) {
          const reason = (res.data as any)?.reason || (res.data as any)?.message;
          fail(reason || "没有操作权限", 3000);
          reject(new Error(reason || "无权限"));
          return;
        }

        // 409 资源冲突（如换绑手机号已被占用）
        if (res.statusCode === 409) {
          const msg = (res.data as any)?.message || "数据冲突，请重试";
          fail(msg, 3000);
          reject(new Error(msg));
          return;
        }

        if (res.statusCode >= 400) {
          fail(`请求失败(${res.statusCode})`);
          reject(new Error(`HTTP ${res.statusCode}`));
          return;
        }

        const rawBody = res.data;
        const body = rawBody as ApiResponse<T>;

        // 空响应体处理：后端 ResponseEntity.ok(null) 返回 200 + 空 body，
        // 直接访问 body.code 会抛 TypeError（null.code），导致成功请求被误判为失败
        if (rawBody === null || rawBody === undefined || rawBody === "") {
          resolve(undefined as unknown as T);
          return;
        }

        // 业务状态码处理（后端约定 code=200 为成功）
        if (body.code !== undefined && body.code !== 200) {
          fail(body.message || "操作失败");
          reject(new Error(body.message));
          return;
        }

        resolve(body.data !== undefined ? body.data : (body as unknown as T));
      },
      fail: (err: any) => {
        error(`${method} ${url} ✗ (${Date.now() - startAt}ms)`, err);
        if (loading) uni.hideLoading();
        // 超时单独提示（errMsg 含 "timeout"），其余走通用网络异常
        if (String(err?.errMsg || "").includes("timeout")) {
          networkTimeout();
        } else {
          networkError();
        }
        reject(err);
      },
    });
  });
}

// 快捷方法
export const get = <T = any>(url: string, data?: Record<string, any>) =>
  request<T>({ url, method: "GET", data });

export const post = <T = any>(
  url: string,
  data?: Record<string, any>,
  loading = false
) => request<T>({ url, method: "POST", data, loading });

export const put = <T = any>(url: string, data?: Record<string, any>) =>
  request<T>({ url, method: "PUT", data });

export const del = <T = any>(url: string) =>
  request<T>({ url, method: "DELETE" });

/**
 * 文件上传（multipart/form-data）
 * 用于发布动态、发布视频等含文件的接口
 *
 * @param url       接口路径
 * @param filePaths  本地文件路径列表
 * @param fileField  文件字段名（与后端 @RequestParam 对应）
 * @param formData   附加表单字段
 * @returns 请求结果
 */
export function upload<T = any>(
  url: string,
  filePaths: string[],
  fileField: string,
  formData?: Record<string, any>
): Promise<T> {
  const startAt = Date.now();
  log(`UPLOAD ${url}`, filePaths);

  // 断网快速失败：上传前判网络，避免大文件白等 60s 超时（S1）
  if (shouldBlockRequest()) {
    networkOffline();
    return Promise.reject(new Error("网络已断开"));
  }

  return new Promise<T>((resolve, reject) => {
    const token = getToken();
    const header: Record<string, string> = {};
    if (token) {
      header["Authorization"] = `Bearer ${token}`;
    }

    uni.uploadFile({
      url: BASE_URL + url,
      files: filePaths.map((filePath) => ({
        name: fileField,
        uri: filePath,
      })),
      formData,
      header,
      timeout: UPLOAD_TIMEOUT_MS,
      success: (res: any) => {
        log(`UPLOAD ${url} → ${res.statusCode} (${Date.now() - startAt}ms)`);
        // 401 登录过期：统一走 handleUnauthorized（与 request 函数对齐）
        if (res.statusCode === 401) {
          handleUnauthorized();
          reject(new Error("登录已过期，请重新登录"));
          return;
        }
        // 402 付费墙拦截
        if (res.statusCode === 402) {
          const msg = (res.data as any)?.message || "升级 VIP 解锁更多权益";
          info(msg, 2000);
          setTimeout(() => {
            uni.navigateTo({ url: "/pages/mine/vip?source=swipe_out" });
          }, 1500);
          reject(new Error(msg));
          return;
        }
        // 429 风控限流
        if (res.statusCode === 429) {
          fail("操作过于频繁，请稍后再试");
          reject(new Error("操作过于频繁"));
          return;
        }
        // 400 参数/格式错误：优先展示后端携带的 message（与 request 函数对齐）
        if (res.statusCode === 400) {
          const msg = (res.data as any)?.message || "操作失败，请检查输入内容";
          fail(msg);
          reject(new Error(msg));
          return;
        }
        // 403 权限/账号状态错误：优先展示后端携带的 reason/message（与 request 函数对齐）
        if (res.statusCode === 403) {
          const msg = (res.data as any)?.reason || (res.data as any)?.message || "没有操作权限";
          fail(msg, 3000);
          reject(new Error(msg));
          return;
        }
        if (res.statusCode >= 400) {
          fail(`上传失败(${res.statusCode})`);
          reject(new Error(`HTTP ${res.statusCode}`));
          return;
        }
        let body: any;
        // 空响应体：后端 200 + 空 body（如只返回 ResponseEntity.ok()）不应视为异常；
        // 原实现 JSON.parse("") 抛错会被 catch 误判为「响应格式错误」
        if (res.data === null || res.data === undefined || res.data === "") {
          resolve(undefined as unknown as T);
          return;
        }
        try {
          body = typeof res.data === "string" ? JSON.parse(res.data) : res.data;
        } catch {
          // 网关/代理返回 HTML 错误页等非 JSON 内容
          fail("服务异常，请稍后再试");
          reject(new Error("响应格式错误"));
          return;
        }
        // 业务状态码校验（与 request 函数对齐：HTTP 200 但 code≠200 视为失败）
        if (body.code !== undefined && body.code !== 200) {
          fail(body.message || "上传失败");
          reject(new Error(body.message));
          return;
        }
        resolve(body.data !== undefined ? body.data : (body as unknown as T));
      },
      fail: (err: any) => {
        error(`UPLOAD ${url} ✗ (${Date.now() - startAt}ms)`, err);
        // 超时单独提示（errMsg 含 "timeout"），其余走通用网络异常
        if (String(err?.errMsg || "").includes("timeout")) {
          networkTimeout();
        } else {
          networkError();
        }
        reject(err);
      },
    });
  });
}

export default request;