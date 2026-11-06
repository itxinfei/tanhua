/**
 * 圈子信息流首屏缓存 + 启动预取（与 utils/video-cache.ts 同思路）
 *
 * 背景：GET /movements 线上实测 3.3~11.6s（跨服务批量查用户信息，波动大），
 * 冷启动进入圈子页会长时间只剩空白 / 骨架，用户容易误判为「没有内容」或卡死。
 *
 * 对策：
 *  1. App 启动后静默预取「好友动态」首页写本地缓存；
 *  2. 圈子 store 加载前先 hydration 缓存，0 秒出内容，再后台刷新；
 *  3. 请求失败保留旧内容；登出清空，避免跨账号串数据。
 */
import { getFriendMovements, type Movement } from "@/api/circle";
import { getToken } from "@/utils/request";

const CACHE_PREFIX = "xfjy_circle_first_page_";
const CACHE_VERSION = 1;
export const FEED_FIRST_PAGE_SIZE = 10;

interface CachedPage {
  v: number;
  at: number;
  items: Movement[];
}

/**
 * 缓存命名空间：按登录凭证派生（与 video-cache.ts 对齐）。
 * 不登出直接换号时，旧 key 会把上个账号的动态先渲染出来。
 */
function cacheScope(): string {
  const token = getToken();
  if (!token) return "anon";
  let hash = 0;
  for (let i = 0; i < token.length; i++) {
    hash = (hash * 31 + token.charCodeAt(i)) | 0;
  }
  return "u" + Math.abs(hash).toString(36);
}

function key(tab: string): string {
  return CACHE_PREFIX + cacheScope() + "_" + tab;
}

export function readFeedCache(tab: "friend" | "recommend"): Movement[] {
  try {
    const raw = uni.getStorageSync(key(tab));
    if (!raw) return [];
    const cached: CachedPage = typeof raw === "string" ? JSON.parse(raw) : raw;
    if (!cached || cached.v !== CACHE_VERSION || !Array.isArray(cached.items)) return [];
    // 点赞/喜欢态不信任缓存，统一归零（与 video-cache.ts 同口径）：
    // 否则下次进入会看到旧的「已点赞」态，点一下反而触发取消
    return cached.items
      .filter((v) => v && v.id != null)
      .map((v) => ({ ...v, hasLiked: 0, hasLoved: 0 }));
  } catch {
    return [];
  }
}

export function writeFeedCache(tab: "friend" | "recommend", items: Movement[]): void {
  if (!items || !items.length) return;
  try {
    // 剥离用户易变状态，只落盘展示所需的稳定字段（与 video-cache.ts 对齐）
    const stable = items.map((v) => ({ ...v, hasLiked: 0, hasLoved: 0 }));
    uni.setStorageSync(
      key(tab),
      JSON.stringify({ v: CACHE_VERSION, at: Date.now(), items: stable } as CachedPage),
    );
  } catch {
    // 缓存只是加速手段，写入失败不影响主流程
  }
}

export function clearFeedCache(): void {
  try {
    // 按前缀遍历，覆盖所有账号命名空间（与 video-cache.ts 同口径）：
    // 原实现只清 friend/recommend 两个 tab，新增 tab 时会漏
    const info = uni.getStorageInfoSync();
    (info.keys || []).forEach((k) => {
      if (k.indexOf(CACHE_PREFIX) !== 0) return;
      try {
        uni.removeStorageSync(k);
      } catch {
        // 单个 key 删除失败不影响其它 tab
      }
    });
  } catch {
    // 取不到 storage 信息时按当前作用域兜底清理
    ["friend", "recommend"].forEach((tab) => {
      try {
        uni.removeStorageSync(key(tab));
      } catch {
        // ignore
      }
    });
  }
}

/** 同一时刻只允许一个预取在途，避免启动预取与页面请求重复打后端 */
let prefetchInFlight: Promise<Movement[]> | null = null;

export function prefetchCircleFeed(): Promise<Movement[]> {
  if (prefetchInFlight) return prefetchInFlight;
  if (!getToken()) return Promise.resolve([]);

  prefetchInFlight = getFriendMovements({ page: 1, pagesize: FEED_FIRST_PAGE_SIZE })
    .then((res) => {
      const items = (res?.items || []) as Movement[];
      if (items.length) writeFeedCache("friend", items);
      return items;
    })
    .catch(() => [] as Movement[])
    .finally(() => {
      prefetchInFlight = null;
    });

  return prefetchInFlight;
}

export function getFeedPrefetchPromise(): Promise<Movement[]> | null {
  return prefetchInFlight;
}
