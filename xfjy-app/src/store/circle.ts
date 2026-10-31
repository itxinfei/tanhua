import { defineStore } from "pinia";
import { ref, computed } from "vue";
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
  /** 好友动态加载中（与推荐流互不阻塞，避免切 Tab 时请求被吞） */
  const friendLoading = ref(false);

  /** 推荐动态列表 */
  const recommendMovements = ref<Movement[]>([]);
  /** 推荐动态分页状态 */
  const recommendPage = ref(1);
  const recommendHasMore = ref(true);
  /** 推荐动态加载中 */
  const recommendLoading = ref(false);

  /** 当前 Tab 是否加载中（供页面骨架屏/加载提示使用） */
  const loading = computed(() =>
    activeTab.value === "friend" ? friendLoading.value : recommendLoading.value
  );

  /**
   * 按动态 id 去重后追加到列表（防止分页期间服务端插入导致重复卡片）
   */
  function appendUnique(list: Movement[], items: Movement[] | undefined): void {
    if (!items || items.length === 0) return;
    const knownIds = new Set(list.map((item) => item.id));
    list.push(...items.filter((item) => !knownIds.has(item.id)));
  }

  /**
   * 加载好友动态（首页/下拉刷新）
   */
  async function loadFriendMovements(): Promise<void> {
    if (friendLoading.value) return;
    friendLoading.value = true;
    try {
      const result: PageResult<Movement> = await getFriendMovements({
        page: 1,
        pagesize: DEFAULT_PAGE_SIZE,
      });
      friendMovements.value = result.items || [];
      friendPage.value = 1;
      friendHasMore.value = (result.items?.length || 0) >= DEFAULT_PAGE_SIZE;
    } finally {
      friendLoading.value = false;
    }
  }

  /**
   * 加载更多好友动态（上拉加载）
   */
  async function loadMoreFriendMovements(): Promise<void> {
    if (friendLoading.value || !friendHasMore.value) return;
    friendLoading.value = true;
    try {
      const nextPage = friendPage.value + 1;
      const result: PageResult<Movement> = await getFriendMovements({
        page: nextPage,
        pagesize: DEFAULT_PAGE_SIZE,
      });
      appendUnique(friendMovements.value, result.items);
      friendPage.value = nextPage;
      friendHasMore.value = (result.items?.length || 0) >= DEFAULT_PAGE_SIZE;
    } finally {
      friendLoading.value = false;
    }
  }

  /**
   * 加载推荐动态（首页/下拉刷新）
   */
  async function loadRecommendMovements(): Promise<void> {
    if (recommendLoading.value) return;
    recommendLoading.value = true;
    try {
      const result: PageResult<Movement> = await getRecommendMovements({
        page: 1,
        pagesize: DEFAULT_PAGE_SIZE,
      });
      recommendMovements.value = result.items || [];
      recommendPage.value = 1;
      recommendHasMore.value = (result.items?.length || 0) >= DEFAULT_PAGE_SIZE;
    } finally {
      recommendLoading.value = false;
    }
  }

  /**
   * 加载更多推荐动态（上拉加载）
   */
  async function loadMoreRecommendMovements(): Promise<void> {
    if (recommendLoading.value || !recommendHasMore.value) return;
    recommendLoading.value = true;
    try {
      const nextPage = recommendPage.value + 1;
      const result: PageResult<Movement> = await getRecommendMovements({
        page: nextPage,
        pagesize: DEFAULT_PAGE_SIZE,
      });
      appendUnique(recommendMovements.value, result.items);
      recommendPage.value = nextPage;
      recommendHasMore.value = (result.items?.length || 0) >= DEFAULT_PAGE_SIZE;
    } finally {
      recommendLoading.value = false;
    }
  }

  /**
   * 切换 Tab
   */
  function switchTab(tab: "friend" | "recommend"): void {
    activeTab.value = tab;
  }

  /**
   * 切换动态点赞状态（本地乐观更新，失败回滚；成功时用接口返回的最新计数覆盖）
   */
  async function toggleLike(movementId: string): Promise<void> {
    const movement = findMovement(movementId);
    if (!movement) return;

    const wasLiked = movement.hasLiked;
    const prevCount = movement.likeCount;
    try {
      if (wasLiked) {
        movement.hasLiked = 0;
        movement.likeCount = Math.max(0, movement.likeCount - 1);
        const latest = await dislikeMovement(movementId);
        if (typeof latest === "number") movement.likeCount = latest;
      } else {
        movement.hasLiked = 1;
        movement.likeCount += 1;
        const latest = await likeMovement(movementId);
        if (typeof latest === "number") movement.likeCount = latest;
      }
    } catch (err) {
      // 请求失败：回滚本地状态，与服务端保持一致
      movement.hasLiked = wasLiked;
      movement.likeCount = prevCount;
      throw err;
    }
  }

  /**
   * 切换动态喜欢状态（本地乐观更新，失败回滚）
   */
  async function toggleLove(movementId: string): Promise<void> {
    const movement = findMovement(movementId);
    if (!movement) return;

    const wasLoved = movement.hasLoved;
    try {
      if (wasLoved) {
        movement.hasLoved = 0;
        await unloveMovement(movementId);
      } else {
        movement.hasLoved = 1;
        await loveMovement(movementId);
      }
    } catch (err) {
      movement.hasLoved = wasLoved;
      throw err;
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
