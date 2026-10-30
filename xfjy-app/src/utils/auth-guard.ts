/**
 * 全局登录路由守卫
 * 包装 uni.navigateTo / redirectTo / reLaunch / switchTab，
 * 未登录时一律拦截并跳转登录页（登录页自身放行）。
 */

import { getToken } from "@/utils/request";

const LOGIN_PAGE = "/pages/auth/login";

// 免登录页面白名单（前缀匹配）
const WHITE_LIST = [LOGIN_PAGE];

function isWhiteListed(url: string): boolean {
  const path = url.split("?")[0];
  return WHITE_LIST.some((p) => path === p || path.startsWith(p));
}

function checkLogin(url: string): boolean {
  if (isWhiteListed(url)) return true;
  if (getToken()) return true;

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
      if (!checkLogin(options.url)) return;
      original(options as never);
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
