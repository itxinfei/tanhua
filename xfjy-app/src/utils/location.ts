/**
 * 定位工具：统一「取坐标 → 取地址名」的两段逻辑
 *
 * 背景（实测缺陷）：
 * 1. H5 在非 HTTPS 环境（如 http://192.168.x.x）下浏览器直接禁用 Geolocation，
 *    uni.getLocation 必然失败，原先各页面只提示「需要定位权限」，用户无从解决；
 * 2. uni.chooseLocation 的地图 Key 是 manifest 编译期写死的，运营无法在后台调整，
 *    未配置时页面对用户报「检查网络」，实际问题与网络无关。
 *
 * 因此这里统一做两件事：设备定位拿不到坐标时走服务端 IP 兜底（城市级），
 * 地址名解析交给服务端（Key 由后台维护，30 秒内生效）。
 */

import {
  ipLocationApi,
  reverseGeocodeApi,
  type LocationResult,
} from "@/api/config";

/** 当前坐标（source 用于区分精度：gps 精确、ip 城市级） */
export interface CurrentLocation {
  longitude: number;
  latitude: number;
  source: "gps" | "ip";
}

/** 设备定位（gcj02）；不可用/拒绝授权/非安全上下文统一返回 null，不抛错 */
function getDeviceLocation(): Promise<{ longitude: number; latitude: number } | null> {
  return new Promise((resolve) => {
    if (typeof (uni as any).getLocation !== "function") {
      resolve(null);
      return;
    }
    try {
      uni.getLocation({
        type: "gcj02",
        success: (res: any) => {
          const lat = Number(res.latitude);
          const lng = Number(res.longitude);
          // 0,0 是浏览器拒绝授权时常见的占位值，按失败处理
          if (Number.isFinite(lat) && Number.isFinite(lng) && lng !== 0 && lat !== 0) {
            resolve({ longitude: lng, latitude: lat });
            return;
          }
          resolve(null);
        },
        fail: () => resolve(null),
      });
    } catch {
      resolve(null);
    }
  });
}

/** IP 兜底定位（城市级）；接口不可用/未配置 Key 时返回 null */
export async function resolveIpLocation(): Promise<LocationResult | null> {
  try {
    const result = await ipLocationApi();
    if (result && Number.isFinite(result.longitude) && Number.isFinite(result.latitude)) {
      return result;
    }
    return null;
  } catch {
    return null;
  }
}

/**
 * 取当前坐标：设备定位优先，失败则用 IP 定位（城市中心点）
 *
 * @returns 坐标与精度来源；两者都不可用时返回 null，由调用方决定提示文案
 */
export async function resolveCurrentLocation(): Promise<CurrentLocation | null> {
  const device = await getDeviceLocation();
  if (device) {
    return { longitude: device.longitude, latitude: device.latitude, source: "gps" };
  }
  const ip = await resolveIpLocation();
  if (ip && ip.longitude !== undefined && ip.latitude !== undefined) {
    return { longitude: ip.longitude, latitude: ip.latitude, source: "ip" };
  }
  return null;
}

/** 坐标 → 地址名；服务端未配置 Key 或解析失败返回 null（调用方用「当前位置」兜底） */
export async function resolveAddressName(
  latitude: number,
  longitude: number,
): Promise<LocationResult | null> {
  try {
    return await reverseGeocodeApi({ latitude, longitude });
  } catch {
    return null;
  }
}

/** 地址展示名：短名（市+区）优先，其次完整地址 */
export function locationDisplayName(result: LocationResult | null, fallback = "当前位置"): string {
  if (!result) return fallback;
  if (result.name) return result.name;
  if (result.address) return result.address;
  return fallback;
}
