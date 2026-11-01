/**
 * 前端日志工具（admin）
 *
 * 开关：import.meta.env.VITE_ENABLE_LOG
 *   - 未设置 / 任意非 'false' 值 → 开启（默认）
 *   - 设为 'false' → 关闭（生产构建可关）
 *
 * 包含：
 *   - console 封装（统一前缀 [xfjy-admin]）
 *   - 异常捕获（Vue errorHandler + window.onerror + unhandledrejection）
 */

const ENABLE_LOG: boolean = import.meta.env.VITE_ENABLE_LOG !== "false";

const TAG = "[xfjy-admin]";

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
 * Vue errorHandler + window.onerror + unhandledrejection
 */
export function installErrorHandler(app: { config: { errorHandler?: unknown } }): void {
  type VueErrorHandler = (err: unknown, instance: unknown, info: string) => void;
  (app.config as { errorHandler?: VueErrorHandler }).errorHandler = (err: unknown, _instance: unknown, _info: string) => {
    reportError("Vue error:", err);
  };
  if (typeof window !== "undefined") {
    const handler: OnErrorEventHandlerNonNull = (message, _source, _lineno, _colno, err) => {
      reportError("window.onerror:", typeof message === 'string' ? message : String(message), err || "");
    };
    window.onerror = handler;
    window.addEventListener("unhandledrejection", (event: PromiseRejectionEvent) => {
      reportError("unhandledrejection:", event.reason);
    });
  }
}
