/**
 * 视频首屏缓存 + 启动预取（stale-while-revalidate）
 *
 * 背景：xfjy-server 组装 VideoVo 需要跨服务批量查询用户信息，线上实测
 * GET /small-videos 稳定耗时 4~5s（与 pagesize 无关，pagesize=1 同样 5.8s）。
 * 原实现进入视频页会先显示 4~7 秒纯黑（暗色骨架叠在黑底上几乎不可见），
 * 用户会误以为页面卡死或没有内容。
 *
 * 对策（不依赖后端部署，前端即可生效）：
 *  1. App 冷启动后静默预取「推荐」首页写入本地缓存（不阻塞启动、失败静默）；
 *  2. 视频页进入 / 切频道时先渲染缓存（0 秒出画面），再后台拉新数据；
 *  3. 每个频道独立缓存，来回切换不再黑屏；
 *  4. 缓存有 TTL，避免长期展示过期内容；请求失败时保留旧内容。
 *
 * 注意：这里只缓存第一页「列表快照」，不缓存点赞/关注等易变状态，
 * 这些状态仍由页面按需从后端刷新。
 */
import { getVideoList, type Video } from "@/api/video";
import { getToken } from "@/utils/request";

const CACHE_PREFIX = "xfjy_video_first_page_";
/** 缓存结构版本；结构变更时递增，自动丢弃旧格式 */
const CACHE_VERSION = 1;
/** 缓存有效期：30 分钟内可立即展示，超时后仍展示但强制后台刷新 */
const CACHE_TTL_MS = 30 * 60 * 1000;

/** 与 video/index.vue 的 PAGE_SIZE 保持一致，确保预取数据可直接首屏使用 */
export const VIDEO_FIRST_PAGE_SIZE = 30;

interface CachedPage {
  v: number;
  at: number;
  items: Video[];
}

function key(channel: string): string {
  return CACHE_PREFIX + channel;
}

/** 读取某频道首屏缓存；无缓存 / 过期结构 / 脏数据时返回空数组 */
export function readVideoCache(channel: string): Video[] {
  try {
    const raw = uni.getStorageSync(key(channel));
    if (!raw) return [];
    const cached: CachedPage = typeof raw === "string" ? JSON.parse(raw) : raw;
    if (!cached || cached.v !== CACHE_VERSION || !Array.isArray(cached.items)) return [];
    // 只做基本字段校验，避免后端返回异常对象时污染 swiper
    const items = cached.items.filter(
      (v) => v && v.id != null && v.videoUrl,
    );
    return items;
  } catch {
    return [];
  }
}

/** 缓存是否仍在有效期内（用于决定是否后台刷新） */
export function isVideoCacheFresh(channel: string): boolean {
  try {
    const raw = uni.getStorageSync(key(channel));
    if (!raw) return false;
    const cached: CachedPage = typeof raw === "string" ? JSON.parse(raw) : raw;
    return !!cached && cached.v === CACHE_VERSION && Date.now() - cached.at < CACHE_TTL_MS;
  } catch {
    return false;
  }
}

export function writeVideoCache(channel: string, items: Video[]): void {
  if (!items || !items.length) return;
  try {
    const payload: CachedPage = { v: CACHE_VERSION, at: Date.now(), items };
    uni.setStorageSync(key(channel), JSON.stringify(payload));
  } catch {
    // 存储配额不足 / 隐私模式：缓存只是加速手段，失败不影响主流程
  }
}

/** 登出或切换账号时清空，避免把上个账号的视频流展示给新账号 */
export function clearVideoCache(): void {
  // 频道 key 与 pages/video/index.vue 的 channels 保持一致
  ["recommend", "follow", "hot", "location"].forEach((ch) => {
    try {
      uni.removeStorageSync(key(ch));
    } catch {
      // ignore
    }
  });
}

/** 同一时刻只允许一个预取在途，避免 App 启动与页面请求重复打后端 */
let prefetchInFlight: Promise<Video[]> | null = null;

/**
 * 预取「推荐」频道首页。返回本次拿到的视频列表（失败返回空数组）。
 * 页面若正好也在等数据，可直接复用这个 Promise，避免 4~5s 的双倍等待。
 */
export function prefetchRecommendVideos(): Promise<Video[]> {
  if (prefetchInFlight) return prefetchInFlight;
  if (!getToken()) return Promise.resolve([]);

  prefetchInFlight = getVideoList({ page: 1, pagesize: VIDEO_FIRST_PAGE_SIZE })
    .then((res) => {
      const items = (res?.items || []) as Video[];
      if (items.length) writeVideoCache("recommend", items);
      return items;
    })
    .catch(() => [] as Video[])
    .finally(() => {
      prefetchInFlight = null;
    });

  return prefetchInFlight;
}

/** 取出正在进行的预取（若有），供视频页复用，避免重复请求 */
export function getPrefetchPromise(): Promise<Video[]> | null {
  return prefetchInFlight;
}
