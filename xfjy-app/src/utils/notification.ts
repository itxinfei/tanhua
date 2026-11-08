/**
 * App 消息推送封装（H5 Web Notification + MP 订阅消息）
 *
 * 设计：
 *  - H5：使用浏览器 Notification API，需用户授权（首次进入时请求）
 *  - MP：使用 uni.requestSubscribeMessage（一次性订阅，用户授权后可用）
 *  - 收到新消息时，若 App 在后台，弹出系统通知
 *
 * 使用：
 *  1. App 启动时调用 requestPermission() 请求权限
 *  2. 收到新消息时调用 showNotification(title, body)
 */

/** 通知权限状态 */
let h5Permission: NotificationPermission = "default";
/** MP 订阅状态缓存（避免重复请求） */
let mpSubscribed = false;

/**
 * 请求通知权限（H5 浏览器 Notification）
 * @returns 是否已授权
 */
export async function requestPermission(): Promise<boolean> {
  // #ifdef H5
  if (!("Notification" in window)) {
    return false;
  }
  if (Notification.permission === "granted") {
    h5Permission = "granted";
    return true;
  }
  if (Notification.permission === "denied") {
    return false;
  }
  try {
    h5Permission = await Notification.requestPermission();
    return h5Permission === "granted";
  } catch {
    return false;
  }
  // #endif
  // #ifndef H5
  // MP 端：请求订阅消息权限（一次性订阅模板）
  return requestMPSubscribe();
  // #endif
}

/**
 * MP 端请求订阅消息（一次性订阅）
 * 需要后端提供订阅模板 ID，这里用占位符，实际接入时替换
 */
async function requestMPSubscribe(): Promise<boolean> {
  // #ifdef MP-WEIXIN
  if (mpSubscribed) return true;
  try {
    // TODO: 替换为实际订阅模板 ID（需后端在微信公众平台配置）
    const tmplIds = ["PLACEHOLDER_TEMPLATE_ID"];
    await uni.requestSubscribeMessage({ tmplIds });
    mpSubscribed = true;
    return true;
  } catch {
    return false;
  }
  // #endif
  return false;
}

/**
 * 显示系统通知（H5）
 * @param title 通知标题
 * @param body 通知内容
 * @param icon 通知图标（可选）
 */
export function showNotification(title: string, body: string, icon?: string): void {
  // #ifdef H5
  if (h5Permission !== "granted") return;
  try {
    const notification = new Notification(title, {
      body,
      icon: icon || "/static/logo.png",
      tag: "xfjy-message",
      silent: false,
    });
    // 点击通知打开 App（H5 场景：聚焦窗口）
    notification.onclick = () => {
      window.focus();
      notification.close();
    };
    // 5s 后自动关闭
    setTimeout(() => notification.close(), 5000);
  } catch {
    // 部分浏览器（如 iOS Safari）不支持 Notification，静默降级
  }
  // #endif
  // #ifndef H5
  // MP 端：使用 uni.showModal 模拟系统通知（或依赖后端订阅消息推送）
  // 这里仅记录，实际推送由后端通过微信服务通知完成
  console.log(`[push] ${title}: ${body}`);
  // #endif
}

/**
 * 判断 App 是否在后台（用于决定是否弹通知）
 */
export function isAppInBackground(): boolean {
  // #ifdef H5
  return document.hidden;
  // #endif
  // #ifndef H5
  // MP 端无法直接判断，依赖 onShow/onHide 状态
  return false;
  // #endif
}
