import { post, get } from "@/utils/request";
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