import { defineStore } from "pinia";
import { ref } from "vue";
import { reportLocation, getUserSettings, type UserSettings, type LocationParam } from "@/api/mine";

/** 本地存储键 */
const CITY_STORAGE_KEY = "xfjy_city";

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
  }

  return {
    currentCity,
    longitude,
    latitude,
    settings,
    setCity,
    updateLocation,
    loadSettings,
    refreshSettings,
    clear,
  };
});