/**
 * 全局网络请求封装
 * 基于 uni.request，统一处理 Token 注入、错误码映射、Loading
 */

// 后端服务地址（按环境切换）
const BASE_URL: string =
  import.meta.env.MODE === "production"
    ? "https://api.xfjy.com"
    : "http://localhost:18081";

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
        if (loading) uni.hideLoading();

        // HTTP 状态码处理
        if (res.statusCode === 401) {
          removeToken();
          uni.reLaunch({ url: "/pages/auth/login" });
          reject(new Error("登录已过期，请重新登录"));
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

export default request;