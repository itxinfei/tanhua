import { defineStore } from "pinia";
import { ref } from "vue";
import { getCards, loveUser, unloveUser, getTodayBest, type TodayBest } from "@/api/match";

/** 每日免费滑动上限 */
const MAX_FREE_SWIPE = 50;
/** 每日免费撤销上限 */
const MAX_FREE_REWIND = 3;

/** 滑动计数 storage key */
const SWIPE_COUNT_STORAGE_KEY = "xfjy_swipe_count";
/** 撤销计数 storage key */
const REWIND_COUNT_STORAGE_KEY = "xfjy_rewind_count";
/** 计数日期 storage key（按日重置依据） */
const COUNT_DATE_STORAGE_KEY = "xfjy_count_date";

/** 今日日期（yyyy-MM-dd），用于按日重置计数 */
function todayStr(): string {
  const d = new Date();
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, "0")}-${String(d.getDate()).padStart(2, "0")}`;
}

/**
 * 从 storage 恢复今日计数（跨日自动清零）
 * @param key storage key
 * @returns 今日已用次数
 */
function restoreDailyCount(key: string): number {
  const savedDate = uni.getStorageSync(COUNT_DATE_STORAGE_KEY) || "";
  if (savedDate !== todayStr()) return 0;
  return Number(uni.getStorageSync(key)) || 0;
}

/**
 * 持久化今日计数（同步更新日期标记）
 * @param key storage key
 * @param value 今日已用次数
 */
function persistDailyCount(key: string, value: number): void {
  uni.setStorageSync(key, String(value));
  uni.setStorageSync(COUNT_DATE_STORAGE_KEY, todayStr());
}

/** 清除计数 storage（登出时调用） */
function clearDailyCountStorage(): void {
  uni.removeStorageSync(SWIPE_COUNT_STORAGE_KEY);
  uni.removeStorageSync(REWIND_COUNT_STORAGE_KEY);
  uni.removeStorageSync(COUNT_DATE_STORAGE_KEY);
}

/**
 * 匹配模块状态管理
 * 负责探花卡片加载、滑动上报、今日佳人缓存
 * 滑动/撤销次数持久化到 storage 并按日重置
 */
export const useMatchStore = defineStore("match", () => {
  // ===== state =====
  /** 探花卡片列表 */
  const cards = ref<TodayBest[]>([]);
  /** 今日佳人 */
  const todayBest = ref<TodayBest | null>(null);
  /** 加载状态 */
  const loading = ref(false);
  /** 今日已滑动次数（storage 持久化，跨日自动清零） */
  const swipeCount = ref(restoreDailyCount(SWIPE_COUNT_STORAGE_KEY));
  /** 今日已撤销次数（storage 持久化，跨日自动清零） */
  const rewindCount = ref(restoreDailyCount(REWIND_COUNT_STORAGE_KEY));

  // ===== actions =====
  /**
   * 加载探花卡片列表（追加模式）
   * @returns 新加载的卡片列表
   */
  async function loadCards(): Promise<TodayBest[]> {
    if (loading.value) return [];
    loading.value = true;
    try {
      const list = await getCards();
      cards.value.push(...list);
      return list;
    } finally {
      loading.value = false;
    }
  }

  /**
   * 上报滑动动作
   * like/superlike → love 接口；dislike → unlove 接口
   * @param userId 对方用户ID
   * @param action 滑动动作：like | dislike | superlike
   * @returns true=互相喜欢（匹配成功）
   */
  async function doSwipe(
    userId: number | string,
    action: "like" | "dislike" | "superlike"
  ): Promise<boolean> {
    swipeCount.value++;
    persistDailyCount(SWIPE_COUNT_STORAGE_KEY, swipeCount.value);
    if (action === "dislike") {
      await unloveUser(userId);
      return false;
    }
    return await loveUser(userId);
  }

  /**
   * 撤销上一次滑动：回滚后端操作并返还滑动次数
   * like/superlike → 调 unlove 删除刚保存的喜欢关系；dislike → 后端无状态变更无需回滚
   * @param userId 对方用户ID
   * @param action 被撤销的滑动动作
   */
  async function undoSwipe(
    userId: number | string,
    action: "like" | "dislike" | "superlike"
  ): Promise<void> {
    if (action !== "dislike") {
      await unloveUser(userId);
    }
    // 撤销返还本次滑动计数
    swipeCount.value = Math.max(0, swipeCount.value - 1);
    persistDailyCount(SWIPE_COUNT_STORAGE_KEY, swipeCount.value);
  }

  /**
   * 加载今日佳人
   */
  async function loadTodayBest(): Promise<void> {
    todayBest.value = await getTodayBest();
  }

  /**
   * 判断是否还有免费滑动次数
   * @returns true 表示可以继续滑动
   */
  function canSwipe(): boolean {
    return swipeCount.value < MAX_FREE_SWIPE;
  }

  /**
   * 判断是否还有免费撤销次数
   * @returns true 表示可以撤销
   */
  function canRewind(): boolean {
    return rewindCount.value < MAX_FREE_REWIND;
  }

  /**
   * 记录一次撤销使用
   */
  function useRewind(): void {
    rewindCount.value++;
    persistDailyCount(REWIND_COUNT_STORAGE_KEY, rewindCount.value);
  }

  /**
   * 重置状态（登出时调用）
   */
  function clear(): void {
    cards.value = [];
    todayBest.value = null;
    swipeCount.value = 0;
    rewindCount.value = 0;
    loading.value = false;
    clearDailyCountStorage();
  }

  return {
    cards,
    todayBest,
    loading,
    swipeCount,
    rewindCount,
    loadCards,
    doSwipe,
    undoSwipe,
    loadTodayBest,
    canSwipe,
    canRewind,
    useRewind,
    clear,
  };
});