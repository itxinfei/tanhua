import { get, post } from "@/utils/request";

/** 地图服务配置（后台「系统配置 → 地图服务」维护，不含服务端 Web 服务 Key） */
export interface MapConfig {
  enabled: string;
  provider: string;
  jsKey: string;
  securityJsCode: string;
}

/** 定位/逆地理结果 */
export interface LocationResult {
  longitude?: number;
  latitude?: number;
  name?: string;
  address?: string;
  province?: string;
  city?: string;
  district?: string;
  adcode?: string;
  /** gps=设备定位，ip=IP 兜底（城市级） */
  source?: string;
}

/** 地图配置：判断前端是否具备地图选点能力 */
export function getMapConfigApi() {
  return get<MapConfig>("/xfjy/config/map");
}

/** 经纬度逆地理：坐标 → 地址名（服务端用后台维护的 Key 调用高德） */
export function reverseGeocodeApi(data: { longitude: number; latitude: number }) {
  return post<LocationResult>("/xfjy/location/reverse-geocode", data);
}

/** IP 兜底定位：设备定位不可用时落到城市级 */
export function ipLocationApi() {
  return get<LocationResult>("/xfjy/location/ip-location");
}
