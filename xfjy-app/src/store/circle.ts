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
import {
  readFeedCache,
  writeFeedCache,
  clearFeedCache,
  getFeedPrefetchPromise,
} from "@/utils/feed-cache";

/** 默认分页大小 */
const DEFAULT_PAGE_SIZE = 10;

/** 发布成功后写 storage 的刷新标记键（圈子页 onShow 消费后清除） */
export const CIRCLE_NEED_REFRESH_KEY = "xfjy_circle_need_refresh";

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
  /** 好友动态首屏加载失败（区分「真的没动态」与「服务故障」） */
  const friendLoadFailed = ref(false);

  /** 推荐动态列表 */
  const recommendMovements = ref<Movement[]>([]);
  /** 推荐动态分页状态 */
  const recommendPage = ref(1);
  const recommendHasMore = ref(true);
  /** 推荐动态加载中 */
  const recommendLoading = ref(false);
  /** 推荐动态首屏加载失败 */
  const recommendLoadFailed = ref(false);

  /** 当前 Tab 是否加载中（供页面骨架屏/加载提示使用） */
  const loading = computed(() =>
    activeTab.value === "friend" ? friendLoading.value : recommendLoading.value
  );

  /** 当前 Tab 首屏是否加载失败（供页面区分空态与错误态） */
  const loadFailed = computed(() =>
    activeTab.value === "friend" ? friendLoadFailed.value : recommendLoadFailed.value
  );

  /**
   * 内容指纹：同一作者 + 同一正文 + 同一时间视为同一条动态。
   * 后端 mock/重复发布可能产生不同 id 但内容完全一致的卡片，
   * 只按 id 去重无法拦截，会造成信息流里两条一模一样的帖子（C2）。
   */
  function contentKey(item: Movement): string {
    return `${item.userId}|${(item.textContent || "").trim()}|${item.createDate || ""}`;
  }

  /**
   * 按 id + 内容指纹去重后追加到列表（防止分页期间服务端插入导致重复卡片）
   */
  function appendUnique(list: Movement[], items: Movement[] | undefined): void {
    if (!items || items.length === 0) return;
    const knownIds = new Set(list.map((item) => item.id));
    const knownContent = new Set(list.map(contentKey));
    for (const item of items) {
      if (knownIds.has(item.id) || knownContent.has(contentKey(item))) continue;
      knownIds.add(item.id);
      knownContent.add(contentKey(item));
      list.push(item);
    }
  }

  /** 首页/刷新时同样做内容级去重，避免首屏就出现重复卡片 */
  function dedupItems(items: Movement[] | undefined): Movement[] {
    if (!items || items.length === 0) return [];
    const seenContent = new Set<string>();
    const seenIds = new Set<string>();
    return items.filter((item) => {
      const key = contentKey(item);
      if (seenIds.has(item.id) || seenContent.has(key)) return false;
      seenIds.add(item.id);
      seenContent.add(key);
      return true;
    });
  }

  /**
   * 加载好友动态（首页/下拉刷新）
   */
  async function loadFriendMovements(): Promise<void> {
    if (friendLoading.value) return;
    // 先渲染首屏缓存：后端 /movements 可达 11s，缓存命中时 0 秒出内容
    if (friendMovements.value.length === 0) {
      const cached = readFeedCache("friend");
      if (cached.length) friendMovements.value = dedupItems(cached);
    }
    friendLoading.value = true;
    friendLoadFailed.value = false;
    try {
      // 复用 App 启动时的在途预取，避免同一份数据重复等 3~11s
      const inflight = getFeedPrefetchPromise();
      const items = inflight
        ? await inflight
        : (await getFriendMovements({ page: 1, pagesize: DEFAULT_PAGE_SIZE })).items;
      friendMovements.value = dedupItems(items);
      writeFeedCache("friend", friendMovements.value);
      friendPage.value = 1;
      friendHasMore.value = (items?.length || 0) >= DEFAULT_PAGE_SIZE;
    } catch (err) {
      // 首屏失败标记：页面据此显示错误态+重试，而非误报「暂无动态」
      friendLoadFailed.value = true;
      throw err;
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
    if (recommendMovements.value.length === 0) {
      const cached = readFeedCache("recommend");
      if (cached.length) recommendMovements.value = dedupItems(cached);
    }
    recommendLoading.value = true;
    recommendLoadFailed.value = false;
    try {
      const result: PageResult<Movement> = await getRecommendMovements({
        page: 1,
        pagesize: DEFAULT_PAGE_SIZE,
      });
      recommendMovements.value = dedupItems(result.items);
      writeFeedCache("recommend", recommendMovements.value);
      recommendPage.value = 1;
      recommendHasMore.value = (result.items?.length || 0) >= DEFAULT_PAGE_SIZE;
    } catch (err) {
      recommendLoadFailed.value = true;
      throw err;
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

  /** 清除状态（登出时调用，避免换账号残留上一账号的动态列表） */
  function clear(): void {
    activeTab.value = "friend";
    friendMovements.value = [];
    friendPage.value = 1;
    friendHasMore.value = true;
    friendLoading.value = false;
    friendLoadFailed.value = false;
    recommendMovements.value = [];
    recommendPage.value = 1;
    recommendHasMore.value = true;
    recommendLoading.value = false;
    recommendLoadFailed.value = false;
    // 首屏缓存按账号隔离：登出 / 换号后不能残留上一账号的动态
    clearFeedCache();
  }

  return {
    activeTab,
    friendMovements,
    friendHasMore,
    recommendMovements,
    recommendHasMore,
    loading,
    loadFailed,
    loadFriendMovements,
    loadMoreFriendMovements,
    loadRecommendMovements,
    loadMoreRecommendMovements,
    switchTab,
    toggleLike,
    toggleLove,
    clear,
  };
});
