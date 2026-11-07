/**
 * 通用定时工具：防抖 / 节流
 *
 * 场景：筛选条连续切换、搜索框输入等高频触发场景，避免每次触发都发起请求。
 *
 * 说明：
 * - debounce：延迟 wait 毫秒后执行，期间再次触发会重置计时（取最后一次）
 * - throttle：wait 毫秒内最多执行一次（首次立即执行）
 */

/** 防抖：连续触发时只执行最后一次 */
export function debounce<A extends unknown[]>(
  fn: (...args: A) => void,
  wait = 300,
): (...args: A) => void {
  let timer: ReturnType<typeof setTimeout> | null = null;
  return (...args: A) => {
    if (timer) clearTimeout(timer);
    timer = setTimeout(() => {
      timer = null;
      fn(...args);
    }, wait);
  };
}

/** 节流：wait 毫秒内最多执行一次（首次立即执行） */
export function throttle<A extends unknown[]>(
  fn: (...args: A) => void,
  wait = 300,
): (...args: A) => void {
  let last = 0;
  return (...args: A) => {
    const now = Date.now();
    if (now - last >= wait) {
      last = now;
      fn(...args);
    }
  };
}
