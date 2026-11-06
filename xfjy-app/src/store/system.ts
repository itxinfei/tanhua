import { defineStore } from "pinia";
import { ref } from "vue";
import { reportLocation, getUserSettings, type UserSettings, type LocationParam } from "@/api/mine";
import type { VideoSearchItem } from "@/api/video-search";

/** 本地存储键 */
const CITY_STORAGE_KEY = "xfjy_city";

/**
 * 视频深链来源。
 * - share：分享链接冷启动，走视频页 onLoad/hash，不经过本 store；
 * - search：搜索结果卡片跳转。视频页是 tabBar 页，switchTab 不支持 URL 参数、
 *   且 tab 页 onLoad 全生命周期只触发一次，搜索深链只能经 store 中转。
 */
export type VideoDeepLinkSource = "share" | "search";

/** 待定视频深链：搜索页写入，视频 tab onShow 消费一次后立即清除 */
export interface PendingVideoDeepLink {
  videoId: string;
  source: VideoDeepLinkSource;
  /** 搜索结果原始条目；与视频页 Video 字段不同源，缺字段在视频页侧显式映射，禁止 as any */
  sourceItem: VideoSearchItem;
}

/**
 * 系统状态管理
 * 管理全局定位、城市选择、用户设置
 */
export const useSystemStore = defineStore("system", () => {
  /** 当前定位城市 */
  const currentCity = ref<string>(
    uni.getStorageSync(CITY_STORAGE_KEY) || "北京"
  );

  /** 当前坐标 */
  const longitude = ref(0);
  const latitude = ref(0);

  /** 用户设置 */
  const settings = ref<UserSettings | null>(null);

  /** 待定视频深链（搜索结果 → 视频 tab 的中转载体），消费后置空，避免下次 onShow 重复强制定位 */
  const pendingVideoDeepLink = ref<PendingVideoDeepLink | null>(null);

  /**
   * 写入待定视频深链（搜索结果卡片点击时调用）。
   * 只保留最新一条：用户连续点不同卡片时，以最后一次选择为准。
   */
  function setPendingVideoDeepLink(videoId: string, sourceItem: VideoSearchItem): void {
    pendingVideoDeepLink.value = { videoId, source: "search", sourceItem };
  }

  /** 消费后清除深链（视频页 onShow 取走即调，保证只生效一次） */
  function clearPendingVideoDeepLink(): void {
    pendingVideoDeepLink.value = null;
  }

  /**
   * 更新当前城市（持久化到本地存储）
   */
  function setCity(city: string): void {
    currentCity.value = city;
    uni.setStorageSync(CITY_STORAGE_KEY, city);
  }

  /**
   * 上报地理位置到后端（写入 ES 索引，供附近的人搜索）
   *
   * @param param 经纬度 + 地址描述
   */
  async function updateLocation(param: LocationParam): Promise<void> {
    longitude.value = param.longitude;
    latitude.value = param.latitude;
    await reportLocation(param);
  }

  /**
   * 加载用户设置（首次调用后缓存）
   */
  async function loadSettings(): Promise<UserSettings> {
    if (settings.value) {
      return settings.value;
    }
    const result = await getUserSettings();
    settings.value = result;
    return result;
  }

  /**
   * 刷新用户设置（设置页修改后调用）
   */
  async function refreshSettings(): Promise<UserSettings> {
    const result = await getUserSettings();
    settings.value = result;
    return result;
  }

  /** 清除状态（登出时调用） */
  function clear(): void {
    settings.value = null;
    longitude.value = 0;
    latitude.value = 0;
    // 深链一并重置：防止换账号后 onShow 把上个账号待定的搜索视频强制定位出来（账号隔离）
    pendingVideoDeepLink.value = null;
    // 同步清本地城市：否则换账号后残留上个账号的城市选择
    // （与 chat.ts 已读水位同类的账号隔离缺陷）
    currentCity.value = "北京";
    uni.removeStorageSync(CITY_STORAGE_KEY);
  }

  return {
    currentCity,
    longitude,
    latitude,
    settings,
    pendingVideoDeepLink,
    setPendingVideoDeepLink,
    clearPendingVideoDeepLink,
    setCity,
    updateLocation,
    loadSettings,
    refreshSettings,
    clear,
  };
});