import { defineStore } from "pinia";
import { ref } from "vue";
import {
  getFriendMovements,
  getRecommendMovements,
  likeMovement,
  dislikeMovement,
  loveMovement,
  unloveMovement,
  type Movement,
  type PageResult,
} from "@/api/circle";

/** 默认分页大小 */
const DEFAULT_PAGE_SIZE = 10;

/**
 * 圈子动态状态管理
 * 管理好友动态/推荐动态双 Tab 列表的分页加载与互动状态
 */
export const useCircleStore = defineStore("circle", () => {
  /** 当前 Tab：friend=好友动态，recommend=推荐动态 */
  const activeTab = ref<"friend" | "recommend">("friend");

  /** 好友动态列表 */
  const friendMovements = ref<Movement[]>([]);
  /** 好友动态分页状态 */
  const friendPage = ref(1);
  const friendHasMore = ref(true);

  /** 推荐动态列表 */
  const recommendMovements = ref<Movement[]>([]);
  /** 推荐动态分页状态 */
  const recommendPage = ref(1);
  const recommendHasMore = ref(true);

  /** 加载状态 */
  const loading = ref(false);

  /**
   * 加载好友动态（首页/下拉刷新）
   */
  async function loadFriendMovements(): Promise<void> {
    if (loading.value) return;
    loading.value = true;
    try {
      const result: PageResult<Movement> = await getFriendMovements({
        page: 1,
        pagesize: DEFAULT_PAGE_SIZE,
      });
      friendMovements.value = result.items || [];
      friendPage.value = 1;
      friendHasMore.value = (result.items?.length || 0) >= DEFAULT_PAGE_SIZE;
    } finally {
      loading.value = false;
    }
  }

  /**
   * 加载更多好友动态（上拉加载）
   */
  async function loadMoreFriendMovements(): Promise<void> {
    if (loading.value || !friendHasMore.value) return;
    loading.value = true;
    try {
      const nextPage = friendPage.value + 1;
      const result: PageResult<Movement> = await getFriendMovements({
        page: nextPage,
        pagesize: DEFAULT_PAGE_SIZE,
      });
      friendMovements.value.push(...(result.items || []));
      friendPage.value = nextPage;
      friendHasMore.value = (result.items?.length || 0) >= DEFAULT_PAGE_SIZE;
    } finally {
      loading.value = false;
    }
  }

  /**
   * 加载推荐动态（首页/下拉刷新）
   */
  async function loadRecommendMovements(): Promise<void> {
    if (loading.value) return;
    loading.value = true;
    try {
      const result: PageResult<Movement> = await getRecommendMovements({
        page: 1,
        pagesize: DEFAULT_PAGE_SIZE,
      });
      recommendMovements.value = result.items || [];
      recommendPage.value = 1;
      recommendHasMore.value = (result.items?.length || 0) >= DEFAULT_PAGE_SIZE;
    } finally {
      loading.value = false;
    }
  }

  /**
   * 加载更多推荐动态（上拉加载）
   */
  async function loadMoreRecommendMovements(): Promise<void> {
    if (loading.value || !recommendHasMore.value) return;
    loading.value = true;
    try {
      const nextPage = recommendPage.value + 1;
      const result: PageResult<Movement> = await getRecommendMovements({
        page: nextPage,
        pagesize: DEFAULT_PAGE_SIZE,
      });
      recommendMovements.value.push(...(result.items || []));
      recommendPage.value = nextPage;
      recommendHasMore.value = (result.items?.length || 0) >= DEFAULT_PAGE_SIZE;
    } finally {
      loading.value = false;
    }
  }

  /**
   * 切换 Tab
   */
  function switchTab(tab: "friend" | "recommend"): void {
    activeTab.value = tab;
  }

  /**
   * 切换动态点赞状态（本地乐观更新 + 远端同步）
   */
  async function toggleLike(movementId: string): Promise<void> {
    const movement = findMovement(movementId);
    if (!movement) return;

    if (movement.hasLiked) {
      movement.hasLiked = 0;
      movement.loveLikeCount = Math.max(0, movement.loveLikeCount - 1);
      await dislikeMovement(movementId);
    } else {
      movement.hasLiked = 1;
      movement.loveLikeCount += 1;
      await likeMovement(movementId);
    }
  }

  /**
   * 切换动态喜欢状态（本地乐观更新 + 远端同步）
   */
  async function toggleLove(movementId: string): Promise<void> {
    const movement = findMovement(movementId);
    if (!movement) return;

    if (movement.hasLoved) {
      movement.hasLoved = 0;
      await unloveMovement(movementId);
    } else {
      movement.hasLoved = 1;
      await loveMovement(movementId);
    }
  }

  /** 在当前可见列表中查找动态 */
  function findMovement(movementId: string): Movement | undefined {
    const allMovements = [...friendMovements.value, ...recommendMovements.value];
    return allMovements.find((item) => item.id === movementId);
  }

  return {
    activeTab,
    friendMovements,
    friendHasMore,
    recommendMovements,
    recommendHasMore,
    loading,
    loadFriendMovements,
    loadMoreFriendMovements,
    loadRecommendMovements,
    loadMoreRecommendMovements,
    switchTab,
    toggleLike,
    toggleLove,
  };
});