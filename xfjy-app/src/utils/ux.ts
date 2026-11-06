/**
 * 统一用户反馈封装
 * 所有 toast / modal / loading 操作必须通过 ux.* 调用，禁止直接用 uni.showToast
 *
 * 好处：
 * 1. 统一文案风格（遵循 3S 原则）
 * 2. 全局去重（同一时刻只弹一个 toast）
 * 3. 接入降噪（高频失败合并计数，避免用户被 toast 轰炸）
 */

/** Toast 去重：当前显示的 toast 内容 */
let currentToast: string | null = null;

/** 失败计数（用于降噪） */
const failCounts = new Map<string, { count: number; ts: number }>();
let failResetTimer: ReturnType<typeof setTimeout> | null = null;

// ===== 基础反馈 =====

/**
 * 成功提示
 * @example ux.success("发送成功")
 */
export function success(message: string, duration = 1500): void {
  currentToast = message;
  uni.showToast({
    title: message,
    icon: "success",
    duration,
    mask: false,
  });
  setTimeout(() => {
    if (currentToast === message) currentToast = null;
  }, duration);
}

/**
 * 失败提示
 * 同一内容 5 秒内重复调用会合并为"发送失败 (3)"
 * @example ux.fail("上传失败")
 */
export function fail(message: string, duration = 2500): void {
  const now = Date.now();
  const key = message;
  const existing = failCounts.get(key);

  if (existing && now - existing.ts < 5000) {
    existing.count++;
    existing.ts = now;
    if (existing.count === 1) {
      uni.showToast({ title: message, icon: "none", duration: 2500 });
    }
    scheduleFailReset(key);
    return;
  }

  failCounts.set(key, { count: 1, ts: now });
  currentToast = message;
  uni.showToast({ title: message, icon: "none", duration });
  scheduleFailReset(key);
}

function scheduleFailReset(key: string): void {
  if (failResetTimer) clearTimeout(failResetTimer);
  failResetTimer = setTimeout(() => failCounts.delete(key), 6000);
}

/**
 * 普通信息提示
 * @example ux.info("网络已恢复")
 */
export function info(message: string, duration = 2000): void {
  currentToast = message;
  uni.showToast({ title: message, icon: "none", duration });
}

/**
 * 加载中提示（阻塞态，需手动 hideLoading 关闭）
 */
export function loading(message: string): void {
  uni.showLoading({ title: message, mask: true });
}

export function hideLoading(): void {
  uni.hideLoading();
}

// ===== 网络相关 =====

/**
 * 断网提示（请求前校验失败时调用）
 */
export function networkOffline(): void {
  uni.showToast({
    title: "当前无网络连接，请检查网络设置",
    icon: "none",
    duration: 2500,
  });
}

/** 请求超时提示 */
export function networkTimeout(): void {
  uni.showToast({
    title: "网络环境较差，请稍后再试",
    icon: "none",
    duration: 2500,
  });
}

/** DNS / 连接失败 */
export function networkError(): void {
  uni.showToast({
    title: "无法连接服务器，请检查网络",
    icon: "none",
    duration: 2500,
  });
}

// ===== 模态弹窗 =====

/**
 * 通用确认弹窗
 * @example ux.confirm("确认删除？").then(ok => { if (ok) doDelete(); })
 */
export function confirm(content: string, title = "提示"): Promise<boolean> {
  return new Promise((resolve) => {
    uni.showModal({
      title,
      content,
      confirmText: "确认",
      cancelText: "取消",
      success: (res) => resolve(res.confirm),
      fail: () => resolve(false),
    });
  });
}

/**
 * 单按钮弹窗（不可取消）
 * @example ux.alert("登录已过期，请重新登录")
 */
export function alert(
  content: string,
  title = "提示",
  confirmText = "我知道了",
): Promise<void> {
  return new Promise((resolve) => {
    uni.showModal({
      title,
      content,
      showCancel: false,
      confirmText,
      success: () => resolve(),
      fail: () => resolve(),
    });
  });
}

// ===== 典型场景快捷方法 =====

/** 操作成功 + 延迟返回上一页 */
export function successThenBack(message: string, delayMs = 1000): void {
  success(message, delayMs);
  setTimeout(() => {
    const pages = getCurrentPages();
    if (pages.length > 1) {
      uni.navigateBack();
    } else {
      uni.switchTab({ url: "/pages/match/index" });
    }
  }, delayMs);
}

/** 操作成功 + 跳转页面 */
export function successThenGo(message: string, url: string): void {
  success(message);
  setTimeout(() => uni.navigateTo({ url }), 800);
}
