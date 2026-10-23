import { defineStore } from "pinia";
import { ref } from "vue";
import { getCards, loveUser, unloveUser, getTodayBest, type TodayBest } from "@/api/match";

/** 每日免费滑动上限 */
const MAX_FREE_SWIPE = 50;

/**
 * 匹配模块状态管理
 * 负责探花卡片加载、滑动上报、今日佳人缓存
 */
export const useMatchStore = defineStore("match", () => {
  // ===== state =====
  /** 探花卡片列表 */
  const cards = ref<TodayBest[]>([]);
  /** 今日佳人 */
  const todayBest = ref<TodayBest | null>(null);
  /** 加载状态 */
  const loading = ref(false);
  /** 今日已滑动次数 */
  const swipeCount = ref(0);

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
    if (action === "dislike") {
      await unloveUser(userId);
      return false;
    }
    return await loveUser(userId);
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
   * 重置状态（登出时调用）
   */
  function clear(): void {
    cards.value = [];
    todayBest.value = null;
    swipeCount.value = 0;
    loading.value = false;
  }

  return {
    cards,
    todayBest,
    loading,
    swipeCount,
    loadCards,
    doSwipe,
    loadTodayBest,
    canSwipe,
    clear,
  };
});