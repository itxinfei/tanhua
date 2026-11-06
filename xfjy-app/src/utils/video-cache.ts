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
 *  4. 请求失败时保留旧内容，由页面后台刷新纠正。
 *
 * 注意：只缓存第一页「列表快照」。点赞/关注属于用户易变状态，不能随缓存落盘
 * （否则下次进入会看到「已点赞」的旧态、点一下反而触发重复点赞），读取时统一重置。
 */
import { getVideoList, type Video } from "@/api/video";
import { getToken } from "@/utils/request";

const CACHE_PREFIX = "xfjy_video_first_page_";
/** 缓存结构版本；结构变更时递增，自动丢弃旧格式 */
const CACHE_VERSION = 1;

/** 与 video/index.vue 的 PAGE_SIZE 保持一致，确保预取数据可直接首屏使用 */
export const VIDEO_FIRST_PAGE_SIZE = 30;

interface CachedPage {
  v: number;
  at: number;
  items: Video[];
}

/**
 * 缓存命名空间：按登录凭证派生。
 * 同一台机器不登出直接换号时，旧 key 会把上个账号的视频流先渲染出来，因此按 token 分空间。
 * token 轮换（重新登录）会让旧缓存自然失效，可接受——缓存只是首屏加速。
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

function key(channel: string): string {
  return CACHE_PREFIX + cacheScope() + "_" + channel;
}

/** 读取某频道首屏缓存；无缓存 / 过期结构 / 脏数据时返回空数组 */
export function readVideoCache(channel: string): Video[] {
  try {
    const raw = uni.getStorageSync(key(channel));
    if (!raw) return [];
    const cached: CachedPage = typeof raw === "string" ? JSON.parse(raw) : raw;
    if (!cached || cached.v !== CACHE_VERSION || !Array.isArray(cached.items)) return [];
    // 只做基本字段校验，避免后端返回异常对象时污染 swiper；
    // 点赞/关注态不信任缓存，统一归零，等后台刷新回来再纠正。
    const items = cached.items
      .filter((v) => v && v.id != null && v.videoUrl)
      .map((v) => ({ ...v, hasLiked: 0, hasFocus: 0 }));
    return items;
  } catch {
    return [];
  }
}

export function writeVideoCache(channel: string, items: Video[]): void {
  if (!items || !items.length) return;
  try {
    // 剥离用户易变状态，只落盘展示所需的稳定字段
    const stable = items.map((v) => ({ ...v, hasLiked: 0, hasFocus: 0 }));
    const payload: CachedPage = { v: CACHE_VERSION, at: Date.now(), items: stable };
    uni.setStorageSync(key(channel), JSON.stringify(payload));
  } catch {
    // 存储配额不足 / 隐私模式：缓存只是加速手段，失败不影响主流程
  }
}

/** 登出或切换账号时清空：按前缀遍历，覆盖所有账号命名空间，避免把上个账号的视频流展示给新账号 */
export function clearVideoCache(): void {
  try {
    const info = uni.getStorageInfoSync();
    (info.keys || []).forEach((k) => {
      if (k.indexOf(CACHE_PREFIX) !== 0) return;
      try {
        uni.removeStorageSync(k);
      } catch {
        // 单个 key 删除失败不影响其它频道
      }
    });
  } catch {
    // 取不到 storage 信息时按当前作用域兜底清理
    ["recommend", "follow", "hot", "location"].forEach((ch) => {
      try {
        uni.removeStorageSync(key(ch));
      } catch {
        // ignore
      }
    });
  }
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

  // 迁移期双传（pageSize 为新契约、pagesize 兼容存量网关），xfjy-server 上线后删除 pagesize
  prefetchInFlight = getVideoList({ page: 1, pageSize: VIDEO_FIRST_PAGE_SIZE, pagesize: VIDEO_FIRST_PAGE_SIZE })
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
