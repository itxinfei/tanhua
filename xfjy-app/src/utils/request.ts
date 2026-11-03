/**
 * 全局网络请求封装
 * 基于 uni.request，统一处理 Token 注入、错误码映射、Loading
 */

import { log, error } from "@/utils/logger";

// 后端服务地址：优先读环境变量 VITE_API_BASE_URL，未配置则按构建模式回退
// 开发联调可在 xfjy-app/.env.development 配置 VITE_API_BASE_URL=http://192.168.x.x:8090
export const BASE_URL: string =
  import.meta.env.VITE_API_BASE_URL ||
  (import.meta.env.MODE === "production"
    ? "https://api.xfjy.com"
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

/**
 * 登录过期统一处理：清本地 token + 重置 userStore 登录态 + reLaunch 登录页
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
        url: "/pages/auth/login",
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
      header: {
        "Content-Type": "application/json",
        ...header,
      },
      success: (res: any) => {
        log(`${method} ${url} → ${res.statusCode} (${Date.now() - startAt}ms)`);
        if (loading) uni.hideLoading();

        // HTTP 状态码处理
        if (res.statusCode === 401) {
          // 登录接口 401=验证码错误（后端携带 message）：只提示不登出；
          // token 过期（无 body）才走统一登出流程
          const msg = (res.data as any)?.message;
          if (msg) {
            uni.showToast({ title: msg, icon: "none", duration: 3000 });
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
          uni.showToast({ title: msg, icon: "none", duration: 3000 });
          reject(new Error(msg));
          return;
        }

        // 402 付费墙拦截（滑动次数耗尽等 → 跳 VIP 引导页）
        if (res.statusCode === 402) {
          const msg = (res.data as any)?.message || "升级 VIP 解锁更多权益";
          uni.showToast({ title: msg, icon: "none", duration: 2000 });
          setTimeout(() => {
            uni.navigateTo({ url: "/pages/mine/vip?source=swipe_out" });
          }, 1500);
          reject(new Error(msg));
          return;
        }

        // 429 风控限流：操作过于频繁
        if (res.statusCode === 429) {
          uni.showToast({ title: "操作过于频繁，请稍后再试", icon: "none" });
          reject(new Error("操作过于频繁"));
          return;
        }

        // 400 参数/格式错误：优先展示后端携带的 message（如换绑手机号格式错）
        if (res.statusCode === 400) {
          const msg = (res.data as any)?.message || "操作失败，请检查输入内容";
          uni.showToast({ title: msg, icon: "none" });
          reject(new Error(msg));
          return;
        }

        // 403 权限/账号状态错误：优先展示后端携带的 reason（如封禁原因）
        if (res.statusCode === 403) {
          const reason = (res.data as any)?.reason || (res.data as any)?.message;
          uni.showToast({ title: reason || "没有操作权限", icon: "none", duration: 3000 });
          reject(new Error(reason || "无权限"));
          return;
        }

        // 409 资源冲突（如换绑手机号已被占用）
        if (res.statusCode === 409) {
          const msg = (res.data as any)?.message || "数据冲突，请重试";
          uni.showToast({ title: msg, icon: "none", duration: 3000 });
          reject(new Error(msg));
          return;
        }

        if (res.statusCode >= 400) {
          uni.showToast({ title: `请求失败(${res.statusCode})`, icon: "none" });
          reject(new Error(`HTTP ${res.statusCode}`));
          return;
        }

        const body = res.data as ApiResponse<T>;

        // 业务状态码处理（后端约定 code=200 为成功）
        if (body.code !== undefined && body.code !== 200) {
          uni.showToast({ title: body.message || "操作失败", icon: "none" });
          reject(new Error(body.message));
          return;
        }

        resolve(body.data !== undefined ? body.data : (body as unknown as T));
      },
      fail: (err: any) => {
        error(`${method} ${url} ✗ (${Date.now() - startAt}ms)`, err);
        if (loading) uni.hideLoading();
        uni.showToast({ title: "网络异常，请检查网络", icon: "none" });
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
          uni.showToast({ title: msg, icon: "none", duration: 2000 });
          setTimeout(() => {
            uni.navigateTo({ url: "/pages/mine/vip?source=swipe_out" });
          }, 1500);
          reject(new Error(msg));
          return;
        }
        // 429 风控限流
        if (res.statusCode === 429) {
          uni.showToast({ title: "操作过于频繁，请稍后再试", icon: "none" });
          reject(new Error("操作过于频繁"));
          return;
        }
        // 400 参数/格式错误：优先展示后端携带的 message（与 request 函数对齐）
        if (res.statusCode === 400) {
          const msg = (res.data as any)?.message || "操作失败，请检查输入内容";
          uni.showToast({ title: msg, icon: "none" });
          reject(new Error(msg));
          return;
        }
        // 403 权限/账号状态错误：优先展示后端携带的 reason/message（与 request 函数对齐）
        if (res.statusCode === 403) {
          const msg = (res.data as any)?.reason || (res.data as any)?.message || "没有操作权限";
          uni.showToast({ title: msg, icon: "none", duration: 3000 });
          reject(new Error(msg));
          return;
        }
        if (res.statusCode >= 400) {
          uni.showToast({ title: `上传失败(${res.statusCode})`, icon: "none" });
          reject(new Error(`HTTP ${res.statusCode}`));
          return;
        }
        let body: any;
        try {
          body = typeof res.data === "string" ? JSON.parse(res.data) : res.data;
        } catch {
          // 网关/代理返回 HTML 错误页等非 JSON 内容
          uni.showToast({ title: "服务异常，请稍后再试", icon: "none" });
          reject(new Error("响应格式错误"));
          return;
        }
        // 业务状态码校验（与 request 函数对齐：HTTP 200 但 code≠200 视为失败）
        if (body.code !== undefined && body.code !== 200) {
          uni.showToast({ title: body.message || "上传失败", icon: "none" });
          reject(new Error(body.message));
          return;
        }
        resolve(body.data !== undefined ? body.data : (body as unknown as T));
      },
      fail: (err: any) => {
        error(`UPLOAD ${url} ✗ (${Date.now() - startAt}ms)`, err);
        uni.showToast({ title: "网络异常，请检查网络", icon: "none" });
        reject(err);
      },
    });
  });
}

export default request;