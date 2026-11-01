/**
 * 前端日志工具（app）
 *
 * 开关：import.meta.env.VITE_ENABLE_LOG
 *   - 未设置 / 任意非 'false' 值 → 开启（默认）
 *   - 设为 'false' → 关闭（生产构建可关）
 *
 * 包含：
 *   - console 封装（统一前缀 [xfjy]）
 *   - 异常捕获（Vue errorHandler + window.onerror + unhandledrejection，后两者仅 H5 端生效）
 */

const ENABLE_LOG: boolean = import.meta.env.VITE_ENABLE_LOG !== "false";

const TAG = "[xfjy]";

export const log = (...args: unknown[]): void => {
  if (!ENABLE_LOG) return;
  console.log(TAG, ...args);
};

export const info = (...args: unknown[]): void => {
  if (!ENABLE_LOG) return;
  console.info(TAG, ...args);
};

export const warn = (...args: unknown[]): void => {
  if (!ENABLE_LOG) return;
  console.warn(TAG, ...args);
};

export const reportError = (...args: unknown[]): void => {
  if (!ENABLE_LOG) return;
  console.error(TAG, ...args);
};

/** @deprecated Use reportError instead — kept for backward compatibility */
export const error = reportError;

/**
 * 安装全局异常捕获
 * Vue errorHandler（组件渲染/生命周期/方法错误）+ window.onerror（未捕获脚本错误）
 * + unhandledrejection（未处理 Promise rejection），后两者仅 H5 端生效
 */
export function installErrorHandler(app: { config: { errorHandler?: unknown } }): void {
  type VueErrorHandler = (err: unknown, instance: unknown, info: string) => void;
  (app.config as { errorHandler?: VueErrorHandler }).errorHandler = (err, _instance, _info) => {
    reportError("Vue error:", err);
  };

  // #ifdef H5
  if (typeof window !== "undefined") {
    const handler: OnErrorEventHandlerNonNull = (message, _source, _lineno, _colno, err) => {
      reportError("window.onerror:", message, err || "");
    };
    window.onerror = handler;
    window.addEventListener("unhandledrejection", (event: PromiseRejectionEvent) => {
      reportError("unhandledrejection:", event.reason);
    });
  }
  // #endif
}
