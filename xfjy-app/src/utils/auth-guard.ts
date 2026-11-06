/**
 * 全局登录路由守卫
 * 包装 uni.navigateTo / redirectTo / reLaunch / switchTab，
 * 未登录时一律拦截并跳转登录页（登录页自身放行）。
 */

import { getToken } from "@/utils/request";
import { isDevExemptionEnabled } from "@/config/env";

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

  // 开发环境豁免：本地开发时允许无 token 导航（生产构建设 VITE_DISABLE_DEV_EXEMPTION=true 关闭）
  if (isDevExemptionEnabled()) {
    return true;
  }

  uni.showToast({ title: "请先登录", icon: "none" });
  // 统一 reLaunch，清空页面栈避免未登录页面残留在返回栈里；
  // 带 redirect 编码用户想去的目标页（而非当前页），登录成功后回跳到原本要去的页面
  const target = url.startsWith("/") ? url : "/" + url;
  uni.reLaunch({ url: `${LOGIN_PAGE}?redirect=${encodeURIComponent(target)}` });
  return false;
}

type NavOptions = { url: string } & Record<string, any>;

/**
 * 包装单个导航 API。
 * 防御性设计：uni-h5 插件的 install 在 app.use(plugin) 时执行，而 App.vue 的
 * onLaunch 早于 app.use（uni-app H5 的已知时序问题）。若此时 uni[name] 尚未
 * 注册，直接 .bind 会抛 TypeError 导致白屏。改为延迟重试直到 API 可用。
 */
function wrapNav<K extends "navigateTo" | "redirectTo" | "reLaunch" | "switchTab">(
  name: K
): void {
  // 已注册：立即包装
  if (typeof uni[name] === "function") {
    doWrap(name);
    return;
  }

  // 未注册：轮询等待 uni-h5 插件 install 完成（最多 50 次 × 20ms = 1s）
  let retries = 0;
  const timer = setInterval(() => {
    if (typeof uni[name] === "function") {
      clearInterval(timer);
      doWrap(name);
    } else if (++retries >= 50) {
      clearInterval(timer);
      // 超时后放弃：不阻塞启动，导航守卫缺失时由后端鉴权兜底
    }
  }, 20);
}

function doWrap<K extends "navigateTo" | "redirectTo" | "reLaunch" | "switchTab">(
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
