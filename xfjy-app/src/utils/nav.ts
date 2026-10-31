/**
 * 安全返回：无历史栈时（小程序直达/分享进入/H5 刷新）兜底跳主页，避免 navigateBack 静默失效。
 */
export function safeGoBack(fallbackUrl: string): void {
  if (getCurrentPages().length > 1) {
    uni.navigateBack();
    return;
  }
  uni.switchTab({
    url: fallbackUrl,
    fail: () => uni.reLaunch({ url: fallbackUrl }),
  });
}
