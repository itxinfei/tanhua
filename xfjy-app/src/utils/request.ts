/**
 * 全局网络请求封装
 * 基于 uni.request，统一处理 Token 注入、错误码映射、Loading
 */

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

        // 429 风控限流：操作过于频繁
        if (res.statusCode === 429) {
          uni.showToast({ title: "操作过于频繁，请稍后再试", icon: "none" });
          reject(new Error("操作过于频繁"));
          return;
        }

        // 400 内容审核拒发
        if (res.statusCode === 400) {
          uni.showToast({ title: "内容包含违规信息，请修改后发布", icon: "none" });
          reject(new Error("内容审核未通过"));
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
        // 401 登录过期：清 token 跳登录页（与 request 函数对齐）
        if (res.statusCode === 401) {
          removeToken();
          uni.reLaunch({ url: "/pages/auth/login" });
          reject(new Error("登录已过期，请重新登录"));
          return;
        }
        // 429 风控限流
        if (res.statusCode === 429) {
          uni.showToast({ title: "操作过于频繁，请稍后再试", icon: "none" });
          reject(new Error("操作过于频繁"));
          return;
        }
        // 400 内容审核拒发
        if (res.statusCode === 400) {
          uni.showToast({ title: "内容包含违规信息，请修改后发布", icon: "none" });
          reject(new Error("内容审核未通过"));
          return;
        }
        if (res.statusCode >= 400) {
          uni.showToast({ title: `上传失败(${res.statusCode})`, icon: "none" });
          reject(new Error(`HTTP ${res.statusCode}`));
          return;
        }
        const body = typeof res.data === "string" ? JSON.parse(res.data) : res.data;
        resolve(body.data !== undefined ? body.data : (body as unknown as T));
      },
      fail: (err: any) => {
        uni.showToast({ title: "网络异常，请检查网络", icon: "none" });
        reject(err);
      },
    });
  });
}

export default request;