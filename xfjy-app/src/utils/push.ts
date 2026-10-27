/**
 * 极光推送封装
 * APP 端（APP-PLUS）：通过极光 uni-app 原生插件获取 registrationId 并上报后端绑定
 * H5 端：空实现（H5 不支持极光原生推送）
 * 真实推送需打包 APP 并集成极光官方 uni-app 原生插件（JG-JPush）
 */
import { post } from "@/utils/request";

/**
 * 上报设备推送标识到后端（登录后调用）
 * 对接后端: POST /users/device/report
 *
 * @param registrationId 极光推送标识
 * @param platform 平台 ANDROID/IOS
 */
export function reportDeviceApi(registrationId: string, platform?: string): Promise<void> {
  return post<void>("/users/device/report", { registrationId, platform });
}

/**
 * 初始化推送：APP 端获取 registrationId 并上报，H5 端跳过
 * 建议在登录成功后调用
 */
export function initPush(): void {
  // #ifdef APP-PLUS
  try {
    // 极光 uni-app 原生插件（JG-JPush），插件未集成时静默跳过
    const jpush = (uni as any).requireNativePlugin?.("JG-PUSH");
    if (jpush?.getRegistrationID) {
      jpush.getRegistrationID((res: any) => {
        const rid = typeof res === "string" ? res : res?.registrationID;
        if (rid) {
          const platform =
            uni.getSystemInfoSync().platform === "ios" ? "IOS" : "ANDROID";
          reportDeviceApi(rid, platform).catch(() => {
            // 上报失败不阻断主流程，下次登录会重试
          });
        }
      });
    } else {
      console.debug("[Push] 极光插件未集成，跳过设备上报");
    }
  } catch (e) {
    console.warn("[Push] 初始化失败", e);
  }
  // #endif
  // #ifndef APP-PLUS
  console.debug("[Push] H5 端不支持极光推送，跳过");
  // #endif
}