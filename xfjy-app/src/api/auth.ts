import { post, get, put } from "@/utils/request";
import type { UserInfo } from "@/store/user";

// 登录响应
export interface LoginResult {
  token: string;
  user: UserInfo;
  isNew: boolean; // 是否新用户（需引导完善资料）
}

/**
 * 发送短信验证码
 * 对接后端: POST /auth/sms（xfjy-sso 服务）
 */
export function sendSmsCode(phone: string): Promise<void> {
  return post<void>("/user/sendCode", { phone });
}

/**
 * 手机号 + 验证码登录/注册
 * 对接后端: POST /user/loginVerification（xfjy-sso 服务）
 */
export function login(phone: string, code: string): Promise<LoginResult> {
  return post<LoginResult>("/user/loginVerification", { phone, code });
}

/**
 * Token 续签
 */
export function refreshToken(): Promise<{ token: string }> {
  return post<{ token: string }>("/auth/refresh");
}

/**
 * 换绑手机号（需验证新手机号的短信验证码）
 * 对接后端: PUT /user/phone（xfjy-sso 服务）
 * 失败状态码：403 验证码错误 / 409 手机号已被占用
 */
export function changePhone(phone: string, code: string): Promise<void> {
  return put<void>("/user/phone", { phone, code });
}