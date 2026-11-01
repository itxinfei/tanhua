/**
 * 网络状态实时监控
 * 断网时自动显示顶部黄色提示条、阻止核心请求；网络恢复时变绿提示 + 重连 IM
 *
 * 使用：在 App.vue onLaunch 中调用 startNetworkMonitor()
 */

import { ref } from "vue";

/** 是否在线（全局响应式，供组件 v-if 绑定） */
export const isOnline = ref<boolean>(true);

/** 当前网络类型：wifi / 2g / 3g / 4g / 5g / none / unknown */
export const networkType = ref<string>("unknown");

/** 网络恢复事件订阅者 */
type NetworkRestoredCallback = () => void;
const restoredCallbacks: Set<NetworkRestoredCallback> = new Set();

/** 订阅网络恢复事件 */
export function onNetworkRestored(cb: NetworkRestoredCallback): () => void {
  restoredCallbacks.add(cb);
  return () => restoredCallbacks.delete(cb);
}

/** 通知所有订阅者网络已恢复 */
function emitNetworkRestored(): void {
  restoredCallbacks.forEach((cb) => {
    try {
      cb();
    } catch (e) {
      console.error("[network-monitor] 回调异常", e);
    }
  });
}

let started = false;

/**
 * 启动网络监听（幂等：多次调用仅生效一次）
 */
export function startNetworkMonitor(): void {
  if (started) return;
  started = true;

  // 1. 初始检测
  uni.getNetworkType({
    success: (res) => {
      networkType.value = res.networkType;
      isOnline.value = res.networkType !== "none";
    },
  });

  // 2. 持续监听
  uni.onNetworkStatusChange((res) => {
    const wasOffline = !isOnline.value;
    networkType.value = res.networkType;
    isOnline.value = res.isConnected && res.networkType !== "none";

    if (wasOffline && isOnline.value) {
      emitNetworkRestored();
    }
  });
}

/**
 * 显示/隐藏顶部网络提示条（调用方通过 NetworkStatusBar 组件响应 isOnline 变化即可）
 * 提供此函数供不想用组件的场景直接调用 toast
 */
let barVisible = false;

export function showNetworkBar(): void {
  if (barVisible) return;
  barVisible = true;
  uni.showToast({
    title: "网络连接已断开",
    icon: "none",
    duration: 99999, // 不自动消失，由 hideNetworkBar 关闭
    position: "top",
  });
}

export function hideNetworkBar(): void {
  if (!barVisible) return;
  barVisible = false;
  uni.hideToast();
}

/**
 * 判断是否需要阻止请求（断网时直接返回，不发请求）
 * 在 request.ts 中调用此函数实现"请求前校验"
 */
export function shouldBlockRequest(): boolean {
  return !isOnline.value;
}
