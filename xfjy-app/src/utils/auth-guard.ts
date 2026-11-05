/**
 * 全局登录路由守卫
 * 包装 uni.navigateTo / redirectTo / reLaunch / switchTab，
 * 未登录时一律拦截并跳转登录页（登录页自身放行）。
 */

import { getToken } from "@/utils/request";

const LOGIN_PAGE = "/pages/auth/login";

// 免登录页面白名单（前缀匹配）
// 协议页：匿名接口 + 本地兜底文本，登录前必须可读（合规要求：登录前先看协议）
const WHITE_LIST = [LOGIN_PAGE, "/pages/mine/agreement"];

function isWhiteListed(url: string): boolean {
  const path = url.split("?")[0];
  return WHITE_LIST.some((p) => path === p || path.startsWith(p));
}

function checkLogin(url: string): boolean {
  if (isWhiteListed(url)) return true;
  if (getToken()) return true;

  // 开发环境 mock：localhost / 127.0.0.1 视为开发环境，允许无 token 导航
  if (
    typeof location !== "undefined" &&
    (location.hostname === "localhost" || location.hostname === "127.0.0.1")
  ) {
    return true;
  }

  uni.showToast({ title: "请先登录", icon: "none" });
  // 统一 reLaunch，清空页面栈避免未登录页面残留在返回栈里
  uni.reLaunch({ url: LOGIN_PAGE });
  return false;
}

type NavOptions = { url: string } & Record<string, any>;

function wrapNav<K extends "navigateTo" | "redirectTo" | "reLaunch" | "switchTab">(
  name: K
): void {
  const original = uni[name].bind(uni);
  Object.defineProperty(uni, name, {
    writable: true,
    configurable: true,
    value(options: NavOptions & Parameters<typeof original>[0]) {
      if (!checkLogin(options.url)) {
        // 被拦截时不能静默丢弃回调：调用方（尤其 await 导航的）必须感知失败
        const opts = options as {
          fail?: (err: unknown) => void;
          complete?: (err: unknown) => void;
        };
        const err = { errMsg: `${name}:fail 未登录，已拦截跳转` };
        opts.fail?.(err);
        opts.complete?.(err);
        return Promise.reject(err) as never;
      }
      original(options as never);
      return undefined as never;
    },
  });
}

/** 在 App.vue onLaunch 中调用（需在任意页面导航发生前安装） */
export function installAuthGuard(): void {
  wrapNav("navigateTo");
  wrapNav("redirectTo");
  wrapNav("reLaunch");
  wrapNav("switchTab");
}
