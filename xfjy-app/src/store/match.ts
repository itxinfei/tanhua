import { defineStore } from "pinia";
import { ref } from "vue";
import { getCards, swipeCard } from "@/api/match";

// 推荐卡片类型
export interface MatchCard {
  id: number | string;
  nickname: string;
  avatar: string;
  gender: string;
  age: number;
  city: string;
  distance: number; // 距离 km
  signature: string;
  photos: string[]; // 照片轮播
  verified: boolean;
  tags: string[]; // 个人标签
}

// 匹配成功信息
export interface MatchResult {
  matched: boolean;
  userId: number | string;
  nickname: string;
  avatar: string;
}

export const useMatchStore = defineStore("match", () => {
  // ===== state =====
  const cards = ref<MatchCard[]>([]);
  const todayBest = ref<MatchCard | null>(null);
  const loading = ref(false);
  const swipeCount = ref(0); // 今日已滑动次数
  const maxSwipe = ref(50); // 免费用户每日上限

  // ===== actions =====
  /** 加载推荐卡片 */
  async function loadCards(): Promise<void> {
    if (loading.value) return;
    loading.value = true;
    try {
      const res = await getCards();
      cards.value = res;
    } finally {
      loading.value = false;
    }
  }

  /**
   * 上报滑动动作
   * @param userId 对方用户ID
   * @param action like / dislike / superlike
   * @returns 匹配结果（null 表示未匹配）
   */
  async function doSwipe(
    userId: number | string,
    action: "like" | "dislike" | "superlike"
  ): Promise<MatchResult | null> {
    swipeCount.value++;
    const res = await swipeCard(userId, action);
    return res.matched ? res : null;
  }

  /** 加载今日佳人 */
  async function loadTodayBest(): Promise<void> {
    // TODO: 对接 GET /match/todayBest
  }

  /** 判断是否还有滑动次数 */
  function canSwipe(): boolean {
    return swipeCount.value < maxSwipe.value;
  }

  return {
    cards,
    todayBest,
    loading,
    swipeCount,
    maxSwipe,
    loadCards,
    doSwipe,
    loadTodayBest,
    canSwipe,
  };
});