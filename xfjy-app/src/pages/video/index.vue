<template>
  <view class="video-page">
    <!-- 顶部 Tab（role=tablist 语义 + 可键盘操作） -->
    <view class="top-tabs" role="tablist" aria-label="视频频道">
      <view class="tab-group">
        <a11y-button
          v-for="ch in CHANNELS"
          :key="ch.key"
          class="tab"
          :class="{ active: activeChannel === ch.key }"
          role="tab"
          :checked="activeChannel === ch.key"
          :label="ch.label"
          @tap="switchChannel(ch.key)"
        >{{ ch.label }}</a11y-button>
      </view>
      <a11y-button class="search-btn" label="搜索" @tap="onSearch">
        <view class="search-ico i-ph-magnifying-glass" aria-hidden="true" />
      </a11y-button>
    </view>

    <swiper
      class="video-swiper"
      vertical
      :current="swiperIndex"
      @change="onSwiperChange"
    >
      <swiper-item v-for="(video, idx) in displayList" :key="video.id">
        <view class="video-item" :class="{ 'is-active': idx === swiperIndex }">
          <template v-if="Math.abs(idx - swiperIndex) <= 1">
            <video
              :id="'vid-' + video.id"
              :key="video.id + '-' + (videoRetryTick[video.id] || 0)"
              class="video-player"
              :src="video.videoUrl"
              :autoplay="idx === swiperIndex"
              loop
              :muted="isMuted"
              :controls="false"
              object-fit="cover"
              @timeupdate="idx === swiperIndex ? onTimeUpdate($event) : null"
              @play="onVideoPlay(idx)"
              @pause="onVideoPause(idx)"
              @error="onVideoError(video)"
            />
            <image
              v-if="idx !== swiperIndex"
              class="cover-residue"
              :src="video.cover"
              mode="aspectFill"
              aria-hidden="true"
            />
          </template>
          <image v-else class="cover" :src="video.cover" mode="aspectFill" lazy-load :aria-label="`${video.nickname} 的视频封面`" />

          <!-- 透明点击层：单击暂停/播放，双击点赞 -->
          <view class="tap-layer" aria-hidden="true" @click="onVideoTap(video, $event)" />

          <!-- 中央播放/暂停指示 -->
          <view v-if="currentId === video.id && !isPlaying && !erroredVideoIds.has(video.id)" class="center-state" aria-hidden="true">
            <view class="play-icon i-ph-play-fill" />
          </view>

          <!-- 播放失败浮层 -->
          <view
            v-if="video.id === currentId && erroredVideoIds.has(video.id)"
            class="video-error"
            role="alert"
            aria-label="视频加载失败，点击重试"
            @tap="retryVideo(video)"
          >
            <text class="video-error-title">视频加载失败</text>
            <view class="video-error-retry" aria-hidden="true">点此重试</view>
          </view>

          <!-- 双击飞出的爱心（抖音式大爱心跳弹） -->
          <view
            v-for="h in floatHearts"
            :key="h.id"
            class="float-heart"
            :style="{ left: h.x + 'px', top: h.y + 'px' }"
            aria-hidden="true"
          ><view class="float-heart__ico i-ph-heart-fill" /></view>

          <!-- 右侧操作栏（抖音原版布局） -->
          <view class="side-bar">
            <view class="side-stack">
              <!-- 作者头像 + 关注 -->
              <a11y-button
                class="side-btn follow-btn"
                :label="video.hasFocus ? `取消关注 ${video.nickname}` : `关注 ${video.nickname}`"
                @tap="handleFocus(video)"
              >
                <image class="side-avatar" :src="video.avatar" mode="aspectFill" lazy-load aria-hidden="true" />
                <view v-if="!video.hasFocus" class="follow-plus" aria-hidden="true">
                  <view class="follow-plus__ico i-ph-plus-bold" />
                </view>
              </a11y-button>

              <a11y-button
                class="side-btn"
                :label="video.hasLiked === 1 ? `取消点赞，当前 ${video.likeCount} 赞` : `点赞，当前 ${video.likeCount} 赞`"
                @tap="handleLike(video)"
              >
                <view class="side-ico" :class="{ 'like-pop': video.hasLiked === 1 }">
                  <view
                    class="side-ico__ph"
                    :class="video.hasLiked === 1 ? 'i-ph-heart-fill side-ico__ph--liked' : 'i-ph-heart'"
                    aria-hidden="true"
                  />
                </view>
                <!-- 0 赞时给明确引导，避免"恒为 0、不知能否互动" -->
                <text class="side-count">{{ video.likeCount > 0 ? formatCount(video.likeCount) : "抢首赞" }}</text>
              </a11y-button>

              <a11y-button class="side-btn" :label="`查看评论，共 ${video.commentCount} 条`" @tap="showComments(video)">
                <view class="side-ico">
                  <view class="side-ico__ph i-ph-chat-circle-dots-bold" aria-hidden="true" />
                </view>
                <text class="side-count">{{ video.commentCount > 0 ? formatCount(video.commentCount) : "抢首评" }}</text>
              </a11y-button>

              <a11y-button class="side-btn" :label="`分享 ${video.nickname} 的视频`" @tap="openShare(video)">
                <view class="side-ico">
                  <view class="side-ico__ph i-ph-arrow-bend-up-right-bold" aria-hidden="true" />
                </view>
              </a11y-button>
            </view>
          </view>

          <!-- 底部信息 + 滚动音乐条 -->
          <view class="video-info">
            <text class="author">@{{ video.nickname }}</text>
            <!-- 位置：城市名可空（H5 不支持逆地理），空时用距离标签，绝不展示坐标串 -->
            <text
              v-if="activeChannel === 'location' && (video.locationName || video.distance)"
              class="location-label"
            >{{ video.locationName || formatDistance(video.distance) }}</text>
            <text class="desc ellipsis-2">{{ video.signature }}</text>
            <view class="music-row">
              <view
                class="music-disc"
                :class="{ 'disc-paused': !isPlaying && currentId === video.id }"
                aria-hidden="true"
              >
                <image lazy-load class="disc-img" :src="video.cover" mode="aspectFill" />
              </view>
              <view class="music-track">
                <text class="music-text">{{ video.nickname }} 创作的原声 · {{ video.signature || '小视频' }}</text>
              </view>
            </view>
          </view>

          <!-- 进度条 -->
          <view v-if="currentId === video.id" class="progress-bar" role="progressbar" aria-label="播放进度" :aria-valuenow="Math.round(progress)" aria-valuemin="0" aria-valuemax="100">
            <view class="progress-inner" :style="{ width: progress + '%' }" />
          </view>
        </view>
      </swiper-item>
    </swiper>

    <!-- 顶部/底部暗角渐变 -->
    <view class="top-scrim" aria-hidden="true"></view>
    <view class="bottom-scrim" aria-hidden="true"></view>

    <!-- 附近回退提示：未授权/半径内无数据时后端回退全站热门，必须明示而不是伪装成附近内容 -->
    <view
      v-if="activeChannel === 'location' && nearbyFallback && displayList.length > 0"
      class="nearby-tip"
      role="status"
    >
      <text class="nearby-tip__text">{{ locationDenied ? "未开启定位权限，正在展示全站热门" : "附近暂无内容，正在展示全站热门" }}</text>
      <a11y-button class="nearby-tip__btn" label="开启定位" @tap="openLocationSetting">开启定位</a11y-button>
    </view>

    <!-- 上滑提示（抖音式 SVG 三角 + 呼吸动画） -->
    <view v-if="showSwipeHint" class="swipe-hint" aria-hidden="true">
      <svg class="swipe-triangle" viewBox="0 0 24 12" aria-hidden="true">
        <path d="M12 0L24 12H0z" fill="var(--text-inverse)" opacity="0.9"/>
      </svg>
      <text class="swipe-text">上滑看更多</text>
    </view>

    <!-- 首屏加载态：后端跨服务组装 VideoVo 约需 4~5s，旧版暗色骨架叠在黑底上几乎不可见，
         用户会看到 4~7 秒纯黑并误以为页面卡死。改为有文案、有进度的可见加载层 + 失败重试。 -->
    <view
      v-if="displayList.length === 0 && (loading || loadFailed)"
      class="video-loading"
      role="status"
      aria-live="polite"
    >
      <view class="video-loading__glow" aria-hidden="true"></view>
      <view class="video-loading__content">
        <view v-if="!loadFailed" class="video-loading__spinner" aria-hidden="true"></view>
        <text class="video-loading__title">
          {{ loadFailed ? "视频没能加载出来" : "正在为你挑选精彩视频" }}
        </text>
        <text class="video-loading__desc">{{ loadFailed ? "网络似乎不太给力，点下方按钮重试" : loadingHint }}</text>
        <a11y-button
          v-if="loadFailed"
          class="video-loading__retry"
          label="重新加载视频"
          @tap="retryLoad"
        >重新加载</a11y-button>
      </view>
    </view>

    <!-- 空状态：附近未授权时给可点击的授权引导，不显示成「没有内容」的坏页 -->
    <view
      v-if="activeChannel === 'location' && locationDenied && displayList.length === 0 && !loading && !loadFailed"
      class="nearby-auth"
      role="status"
    >
      <text class="nearby-auth__title">需要开启定位权限</text>
      <text class="nearby-auth__desc">开启后可查看附近的视频；也可以先在推荐页逛逛</text>
      <a11y-button class="nearby-auth__btn" label="开启定位权限" @tap="openLocationSetting">开启定位权限</a11y-button>
    </view>

    <empty-state
      v-else-if="displayList.length === 0 && !loading && !loadFailed"
      overlay
      dark
      icon="eye"
      :title="emptyStateTitle"
      :desc="emptyStateDesc"
    />

    <!-- 评论抽屉 -->
    <video-comment-drawer
      :visible="commentVisible"
      :video-id="activeCommentVideoId"
      @close="commentVisible = false"
      @commented="onVideoCommented"
    />

    <!-- 底部分享面板 -->
    <a11y-dialog class="share-mask" :visible="showShare" title="分享给好友" @close="showShare = false">
      <view class="share-panel-inner">
        <text class="share-title">分享给好友</text>
        <view class="share-grid">
          <a11y-button class="share-item" label="分享到微信" @tap="doShare('wechat')">
            <view class="share-ico ico-wechat" aria-hidden="true">微</view>
            <text class="share-label">微信</text>
          </a11y-button>
          <a11y-button class="share-item" label="分享到朋友圈" @tap="doShare('moments')">
            <view class="share-ico ico-moments" aria-hidden="true">友</view>
            <text class="share-label">朋友圈</text>
          </a11y-button>
          <a11y-button class="share-item" label="复制链接" @tap="doShare('copy')">
            <view class="share-ico ico-copy" aria-hidden="true">链</view>
            <text class="share-label">复制链接</text>
          </a11y-button>
        </view>
        <a11y-button class="share-cancel" label="取消分享" @tap="showShare = false">取消</a11y-button>
      </view>
    </a11y-dialog>
    <!-- 匹配成功弹窗（全局，由 match store 驱动） -->
    <MatchPopup />
  </view>
</template>

<script setup lang="ts">
import { ref, computed, watch, nextTick, getCurrentInstance } from "vue";
import { onShow, onHide, onShareAppMessage } from "@dcloudio/uni-app";
import MatchPopup from "@/components/match-popup.vue";
import {
  getVideoList,
  getFollowVideoList,
  getHotVideoList,
  getLocationVideoList,
  reportVideoPlay,
  likeVideo,
  dislikeVideo,
  focusVideoUser,
  unfocusVideoUser,
  type Video,
} from "@/api/video";
import VideoCommentDrawer from "@/components/video-comment-drawer.vue";
import A11yButton from "@/components/a11y-button.vue";
import EmptyState from "@/components/empty-state.vue";
import {
  readVideoCache,
  writeVideoCache,
  getPrefetchPromise,
} from "@/utils/video-cache";
import A11yDialog from "@/components/a11y-dialog.vue";
import { formatCount } from "@/utils/format";
import { info } from "@/utils/ux";

/**
 * 分页大小。
 * 取 30 而非 10：视频源按发布时间聚集，最新十几条常常全部来自同一作者，
 * 若每页只取 10 条会出现"连续刷到的视频都是同一个人"（用户误以为是自己的账号）。
 * 一次多取一批后由 interleaveByAuthor 按作者轮转打散，保证首屏即作者多样。
 * swiper 仅渲染当前 ±1 项，30 条不会带来渲染压力。
 */
const PAGE_SIZE = 30;

/** 4 个频道各自独立的列表与分页（「朋友」与「关注」同源重复，已从前后端移除） */
const channels = {
  recommend: { list: ref<Video[]>([]), page: ref(1), hasMore: ref(true) },
  follow: { list: ref<Video[]>([]), page: ref(1), hasMore: ref(true) },
  hot: { list: ref<Video[]>([]), page: ref(1), hasMore: ref(true) },
  location: { list: ref<Video[]>([]), page: ref(1), hasMore: ref(true) },
} as const;

/** 按频道 key 获取对应的 API 请求函数 */
function getChannelApi(key: ChannelKey) {
  switch (key) {
    case "recommend": return getVideoList;
    case "follow": return getFollowVideoList;
    case "hot": return getHotVideoList;
    case "location": return getLocationVideoList;
  }
}

const swiperIndex = ref(0);
type ChannelKey = "hot" | "follow" | "location" | "recommend";
/**
 * 频道精简为 4 个（原 5 个中「朋友」与「关注」后端同源、内容完全重复，
 * 「定位」已补全为真实地理数据）。
 * 各 tab 语义：热点=高赞优先 / 关注=已关注作者 / 附近=按距离排序 / 推荐=热度分排序。
 */
const CHANNELS = [
  { key: "hot", label: "热点" },
  { key: "follow", label: "关注" },
  { key: "location", label: "附近" },
  { key: "recommend", label: "推荐" },
] as const;
const activeChannel = ref<ChannelKey>("recommend");

const displayList = computed<Video[]>(() => channels[activeChannel.value].list.value);

const commentVisible = ref(false);
const activeCommentVideoId = ref("");
const loadingMore = ref(false);
let loadToken = 0;

const currentVideo = computed<Video | undefined>(() => displayList.value[swiperIndex.value]);
const currentId = computed(() => currentVideo.value?.id ?? "");

const isPlaying = ref(true);
const isH5 = uni.getSystemInfoSync().uniPlatform === "web";
const isMuted = ref(isH5);
const progress = ref(0);
const loading = ref(false);
/** 首屏加载失败（且无任何缓存可展示）时显示重试入口，避免死在纯黑页 */
const loadFailed = ref(false);
/** 超过 2.5s 仍未出内容时切换安抚文案，降低用户误判为卡死的概率 */
const slowLoading = ref(false);
let slowLoadingTimer: ReturnType<typeof setTimeout> | null = null;
const loadingHint = computed(() =>
  slowLoading.value
    ? "内容较多，正在努力加载，请稍等片刻…"
    : "马上就好，先逛逛其他页面也不会打断加载",
);
function retryLoad() {
  loadFailed.value = false;
  loadVideos();
}

/** 「附近」：当前用户坐标；未授权/取不到为 null（不等于请求失败，需展示授权引导） */
const userLocation = ref<{ lat: number; lng: number } | null>(null);
const locationDenied = ref(false);
/** 后端在无坐标/半径无结果时回退全站热门，此标记用于文案提示，避免用户误以为是附近内容 */
const nearbyFallback = ref(false);

/**
 * 取一次定位坐标。H5 不支持逆地理（geocode），拿不到城市名属正常路径，
 * 这里只要经纬度；失败不抛错、不阻塞列表请求。
 */
function locateOnce(): Promise<{ lat: number; lng: number } | null> {
  if (userLocation.value) return Promise.resolve(userLocation.value);
  return new Promise((resolve) => {
    uni.getLocation({
      type: "gcj02",
      success: (res: any) => {
        const lat = Number(res.latitude);
        const lng = Number(res.longitude);
        if (Number.isFinite(lat) && Number.isFinite(lng)) {
          locationDenied.value = false;
          userLocation.value = { lat, lng };
          resolve(userLocation.value);
          return;
        }
        resolve(null);
      },
      fail: () => {
        // 未开权限/系统拒绝：标记后由页面给出可点击的授权引导
        locationDenied.value = true;
        resolve(null);
      },
    });
  });
}

/** 未授权时的授权引导：小程序走设置页，其它端直接重试定位 */
function openLocationSetting(): void {
  const openSetting = (uni as any).openSetting;
  if (typeof openSetting === "function") {
    openSetting({
      success: () => {
        locationDenied.value = false;
        userLocation.value = null;
        channels.location.list.value = [];
        loadVideos();
      },
      fail: () => {
        locationDenied.value = true;
      },
    });
    return;
  }
  locationDenied.value = false;
  userLocation.value = null;
  channels.location.list.value = [];
  loadVideos();
}

/** 距离标签：绝不展示 "lng,lat" 坐标串（locationName 为空时的展示兜底） */
function formatDistance(distance?: number): string {
  if (distance === undefined || distance === null || !Number.isFinite(distance)) return "";
  if (distance < 500) return "500m 内";
  if (distance < 1000) return `${Math.round(distance / 100) * 100}m`;
  return `${(distance / 1000).toFixed(1)}km`;
}

let lastPlayedIndex = -1;

let lastTapTime = 0;
let singleTapTimer: ReturnType<typeof setTimeout> | null = null;
let doubleTapCooldownUntil = 0;

const instance = getCurrentInstance();

function getCtx(index: number) {
  const video = displayList.value[index];
  if (!video) return null;
  return uni.createVideoContext("vid-" + video.id, instance?.proxy as any);
}

function playAt(index: number) {
  const video = displayList.value[index];
  if (!video) return;
  lastPlayedIndex = index;
  getCtx(index)?.play();
}

function pauseAt(index: number) {
  getCtx(index)?.pause();
}

function unlockAudio() {
  if (isH5 && isMuted.value) {
    isMuted.value = false;
    document.querySelectorAll("video").forEach((v) => (v.muted = false));
  }
}

watch(swiperIndex, () => {
  lastTapTime = 0;
  if (singleTapTimer) {
    clearTimeout(singleTapTimer);
    singleTapTimer = null;
  }
});

function onSwiperChange(e: any) {
  const index = e.detail.current;
  const old = swiperIndex.value;
  if (old !== index) pauseAt(old);
  swiperIndex.value = index;
  isPlaying.value = true;
  progress.value = 0;
  unlockAudio();
  playAt(index);
  if (index >= displayList.value.length - 2) {
    loadMoreVideos();
  }
}

function onVideoPlay(idx: number) {
  if (idx !== swiperIndex.value) return;
  isPlaying.value = true;
  // 播放上报：仅在 play 事件触发时上报一次/视频（不随 timeupdate 上报），
  // 后端另有 Redis「用户×视频」去重兜底，双层保证防刷。
  const video = displayList.value[idx];
  if (video && !reportedPlayIds.has(video.id)) {
    reportedPlayIds.add(video.id);
    reportVideoPlay(video.id).catch(() => {
      // 上报失败不影响播放
    });
  }
}

function onVideoPause(idx: number) {
  if (idx !== swiperIndex.value || idx !== lastPlayedIndex) return;
  isPlaying.value = false;
}

const erroredVideoIds = ref(new Set<string>());
const videoRetryTick = ref<Record<string, number>>({});
/** 本次会话已上报播放的视频，避免同一视频反复播放时重复请求 */
const reportedPlayIds = new Set<string>();

function onVideoError(video: Video) {
  erroredVideoIds.value = new Set(erroredVideoIds.value).add(video.id);
}

function retryVideo(video: Video) {
  const next = new Set(erroredVideoIds.value);
  next.delete(video.id);
  erroredVideoIds.value = next;
  videoRetryTick.value[video.id] = (videoRetryTick.value[video.id] || 0) + 1;
  nextTick(() => playAt(swiperIndex.value));
}

const emptyStateTitle = computed(() => {
  switch (activeChannel.value) {
    case "follow": return "还没有关注的作者";
    case "hot": return "暂无热门视频";
    case "location": return "暂无附近的视频";
    default: return "暂无视频";
  }
});
const emptyStateDesc = computed(() => {
  switch (activeChannel.value) {
    case "follow": return "去推荐页关注喜欢的人吧";
    case "hot": return "精彩内容正在路上";
    case "location": return "开启定位后看看附近的人吧";
    default: return "精彩内容即将上线";
  }
});

const floatHearts = ref<{ id: number; x: number; y: number }[]>([]);

const showShare = ref(false);
const shareVideo = ref<Video | null>(null);

function onSearch(): void {
  uni.navigateTo({ url: "/pages/video/search" });
}

async function switchChannel(ch: ChannelKey) {
  if (activeChannel.value === ch) return;
  activeChannel.value = ch;
  swiperIndex.value = 0;
  isPlaying.value = true;
  progress.value = 0;
  const chState = channels[ch];
  erroredVideoIds.value = new Set();
  // 切频道前先 hydration 该频道缓存，避免每次切回都重新黑屏 4~5s
  if (chState.list.value.length === 0) {
    const cached = readVideoCache(ch);
    if (cached.length) chState.list.value = arrangeForChannel(ch, cached);
  }
  if (chState.list.value.length === 0) {
    await loadVideos();
  } else {
    playAt(0);
  }
}

/**
 * 按作者打散：避免视频流顶部连续多条都来自同一作者（如最新 12 条全是某用户），
 * 导致用户误以为"所有视频都是自己"。轮转排列，保证相邻视频作者不同（作者数≥2 时）。
 */
function interleaveByAuthor<T extends { userId: string | number }>(list: T[]): T[] {
  if (!list || list.length <= 2) return list;
  const byAuthor = new Map<string | number, T[]>();
  for (const v of list) {
    const k = v.userId;
    if (!byAuthor.has(k)) byAuthor.set(k, []);
    byAuthor.get(k)!.push(v);
  }
  const queues = Array.from(byAuthor.values()).sort((a, b) => b.length - a.length);
  const result: T[] = [];
  const maxLen = Math.max(...queues.map((q) => q.length));
  for (let i = 0; i < maxLen; i++) {
    for (const q of queues) {
      if (i < q.length) result.push(q[i]);
    }
  }
  return result;
}

/** 合并下一页时已打散列表，并消除跨页边界处的同作者相邻 */
function mergeInterleaved<T extends { id: string | number; userId: string | number }>(current: T[], next: T[]): T[] {
  if (!next.length) return current;
  const interleaved = interleaveByAuthor(next);
  const last = current[current.length - 1];
  if (last && interleaved.length && interleaved[0].userId === last.userId) {
    interleaved.push(interleaved.shift() as T);
  }
  return appendUnique(current, interleaved);
}

/** 推荐/附近必须保持后端顺序（热度分/距离），二次打散会破坏排序与稳定分页 */
const KEEP_BACKEND_ORDER: ChannelKey[] = ["recommend", "location"];

function arrangeForChannel(key: ChannelKey, items: Video[]): Video[] {
  return KEEP_BACKEND_ORDER.indexOf(key) >= 0 ? items : interleaveByAuthor(items);
}

function mergeForChannel(key: ChannelKey, current: Video[], next: Video[]): Video[] {
  return KEEP_BACKEND_ORDER.indexOf(key) >= 0 ? appendUnique(current, next) : mergeInterleaved(current, next);
}

const TAP_GAP = 280;

function onVideoTap(video: Video, e: any) {
  unlockAudio();
  const now = Date.now();
  if (now < doubleTapCooldownUntil) return;
  if (now - lastTapTime < TAP_GAP) {
    if (singleTapTimer) {
      clearTimeout(singleTapTimer);
      singleTapTimer = null;
    }
    lastTapTime = 0;
    doubleTapCooldownUntil = now + TAP_GAP;
    doubleLike(video, e);
  } else {
    lastTapTime = now;
    singleTapTimer = setTimeout(() => {
      singleTapTimer = null;
      togglePlay();
    }, TAP_GAP);
  }
}

function togglePlay() {
  const idx = swiperIndex.value;
  if (isPlaying.value) {
    isPlaying.value = false;
    pauseAt(idx);
  } else {
    isPlaying.value = true;
    unlockAudio();
    playAt(idx);
  }
}

function doubleLike(video: Video, e: any) {
  spawnHeart(e);
  if (video.hasLiked !== 1) {
    handleLike(video);
  }
}

function spawnHeart(e: any) {
  let x: number;
  let y: number;
  if (e && e.detail && typeof e.detail.x === "number") {
    x = e.detail.x;
    y = e.detail.y;
  } else if (e && typeof e.clientX === "number") {
    x = e.clientX;
    y = e.clientY;
  } else {
    x = uni.upx2px(375);
    y = uni.upx2px(600);
  }
  const id = Date.now() + Math.floor(Math.random() * 1000);
  floatHearts.value.push({ id, x: x - 24, y: y - 24 });
  setTimeout(() => {
    floatHearts.value = floatHearts.value.filter((h) => h.id !== id);
  }, 800);
}

function onTimeUpdate(e: any) {
  const cur = e?.detail?.currentTime || 0;
  const dur = e?.detail?.duration || 0;
  const next = dur > 0 ? Math.min(100, Math.round((cur / dur) * 100)) : 0;
  if (next !== Math.round(progress.value)) {
    progress.value = next;
  }
}

async function handleLike(video: Video) {
  const wasLiked = video.hasLiked === 1;
  try {
    if (wasLiked) {
      video.hasLiked = 0;
      video.likeCount = Math.max(0, video.likeCount - 1);
      const count = await dislikeVideo(video.id);
      if (typeof count === "number") video.likeCount = count;
    } else {
      video.hasLiked = 1;
      video.likeCount += 1;
      const count = await likeVideo(video.id);
      if (typeof count === "number") video.likeCount = count;
    }
  } catch {
    video.hasLiked = wasLiked ? 1 : 0;
    video.likeCount += wasLiked ? 1 : -1;
  }
}

async function handleFocus(video: Video) {
  const wasFocused = video.hasFocus === 1;
  try {
    if (wasFocused) {
      video.hasFocus = 0;
      await unfocusVideoUser(video.userId);
      if (activeChannel.value === "follow") {
        const chState = channels["follow"];
        chState.list.value = chState.list.value.filter((v) => v.userId !== video.userId);
        const safeIdx = Math.min(swiperIndex.value, Math.max(0, chState.list.value.length - 1));
        swiperIndex.value = -1;
        swiperIndex.value = safeIdx;
        if (chState.list.value.length > 0) playAt(safeIdx);
      }
    } else {
      video.hasFocus = 1;
      await focusVideoUser(video.userId);
    }
    const focused = wasFocused ? 0 : 1;
    for (const ch of Object.values(channels)) {
      for (const v of ch.list.value) {
        if (v.userId === video.userId && v !== video) v.hasFocus = focused;
      }
    }
  } catch {
    video.hasFocus = wasFocused ? 1 : 0;
  }
}

function showComments(video: Video) {
  activeCommentVideoId.value = video.id;
  commentVisible.value = true;
}

function onVideoCommented() {
  const target = displayList.value.find((v) => v.id === activeCommentVideoId.value);
  if (target) target.commentCount += 1;
}

function openShare(video: Video) {
  shareVideo.value = video;
  showShare.value = true;
}

function doShare(channel: "wechat" | "moments" | "copy") {
  showShare.value = false;
  if (channel === "copy") {
    uni.setClipboardData({
      data: shareVideo.value?.videoUrl || "",
      success: () => info("链接已复制"),
    });
  } else {
    info("该分享方式即将上线");
  }
}

async function loadVideos() {
  const token = ++loadToken;
  const ch = activeChannel.value;
  const chState = channels[ch];
  const apiFn = getChannelApi(ch);

  swiperIndex.value = 0;
  // 1. 先用首屏缓存渲染（启动预取命中 / 二次进入 / 切回频道时 0 秒出画面）
  if (chState.list.value.length === 0) {
    const cached = readVideoCache(ch);
    if (cached.length) {
      chState.list.value = interleaveByAuthor(cached);
      isPlaying.value = true;
      nextTick(() => playAt(0));
    }
  }
  // 2. 有内容就后台刷新、不遮挡画面；完全无内容才显示可见加载层
  const blocking = displayList.value.length === 0;
  loading.value = blocking;
  loadFailed.value = false;
  slowLoading.value = false;
  if (slowLoadingTimer) clearTimeout(slowLoadingTimer);
  if (blocking) {
    slowLoadingTimer = setTimeout(() => {
      if (token === loadToken) slowLoading.value = true;
    }, 2500);
  }
  erroredVideoIds.value = new Set();

  try {
    chState.page.value = 1;
    chState.hasMore.value = true;
    const query: Record<string, any> = { page: 1, pagesize: PAGE_SIZE };
    if (ch === "location") {
      // 附近：先取坐标再请求；未授权时也继续请求，由后端回退全站热门（绝不静默空列表）
      nearbyFallback.value = false;
      const loc = await locateOnce();
      if (loc) {
        query.lat = loc.lat;
        query.lng = loc.lng;
      }
    }
    // 3. 复用 App 冷启动时的在途预取，避免同一份数据重复等 4~5s
    const inflight = ch === "recommend" ? getPrefetchPromise() : null;
    const res = inflight
      ? { items: await inflight }
      : await (apiFn as any)(query);
    if (token !== loadToken || activeChannel.value !== ch) return;
    nearbyFallback.value = Boolean((res as any).fallback);
    const items = res.items || [];
    if (items.length) {
      chState.list.value = arrangeForChannel(ch, items);
      writeVideoCache(ch, items);
      isPlaying.value = true;
      playAt(0);
    } else if (displayList.value.length === 0) {
      // 空结果由 empty-state 承担，不误报为网络失败
      chState.list.value = [];
    }
  } catch {
    // request 层统一 toast；缓存也拿不到时才给可重试的加载层
    if (token === loadToken && displayList.value.length === 0) {
      loadFailed.value = true;
    }
  } finally {
    if (token === loadToken) {
      loading.value = false;
      slowLoading.value = false;
      if (slowLoadingTimer) {
        clearTimeout(slowLoadingTimer);
        slowLoadingTimer = null;
      }
    }
  }
}

function appendUnique<T extends { id: string | number }>(target: T[], items: T[]): T[] {
  const seen = new Set(target.map((v) => v.id));
  return target.concat(items.filter((v) => !seen.has(v.id)));
}

async function loadMoreVideos() {
  if (loadingMore.value) return;
  const channel = activeChannel.value;
  const chState = channels[channel];
  if (!chState.hasMore.value) return;
  const token = loadToken;
  const apiFn = getChannelApi(channel);

  loadingMore.value = true;
  try {
    const nextPage = chState.page.value + 1;
    const query: Record<string, any> = { page: nextPage, pagesize: PAGE_SIZE };
    if (channel === "location" && userLocation.value) {
      query.lat = userLocation.value.lat;
      query.lng = userLocation.value.lng;
    }
    const res = await (apiFn as any)(query);
    if (token !== loadToken || activeChannel.value !== channel) return;
    if (res.items && res.items.length > 0) {
      const before = chState.list.value.length;
      chState.list.value = mergeForChannel(channel, chState.list.value, res.items);
      // 本页全为重复视频时同样推进页码，避免反复请求同一页
      chState.page.value = nextPage;
      if (chState.list.value.length === before) {
        chState.hasMore.value = false;
      }
    } else {
      chState.hasMore.value = false;
    }
  } catch {
    // request 层统一 toast
  } finally {
    loadingMore.value = false;
  }
}

const showSwipeHint = ref(false);
watch(
  () => [currentVideo.value?.id, swiperIndex.value] as const,
  ([id, idx]) => {
    if (id && idx === 0) {
      showSwipeHint.value = true;
      setTimeout(() => {
        showSwipeHint.value = false;
      }, 4000);
    } else {
      showSwipeHint.value = false;
    }
  },
);

let wasPlayingBeforeHide = false;

onShow(() => {
  // 冷启动 / 缓存命中：先渲染本地首屏，再决定是否后台刷新
  if (displayList.value.length === 0) {
    const cached = readVideoCache(activeChannel.value);
    if (cached.length) {
      channels[activeChannel.value].list.value = arrangeForChannel(activeChannel.value, cached);
    }
  }
  if (displayList.value.length === 0) {
    loadVideos();
  } else if (wasPlayingBeforeHide) {
    wasPlayingBeforeHide = false;
    isPlaying.value = true;
    playAt(swiperIndex.value);
  }
});

onHide(() => {
  wasPlayingBeforeHide = isPlaying.value;
  if (isPlaying.value) {
    isPlaying.value = false;
    const video = currentVideo.value;
    if (video) {
      const ctx = uni.createVideoContext("vid-" + video.id, instance?.proxy as any);
      ctx?.pause();
    }
  }
});

onShareAppMessage(() => ({
  title: "心飞交友 · 遇见心动的人",
  path: "/pages/match/index",
}));
</script>

<style lang="scss" scoped>
.video-page {
  height: calc(100vh - var(--tab-bar-height, 0px));
  background: var(--bg-page);
  position: relative;
}

/* ===== 首屏加载态 =====
   视频页是深色底，旧的暗色骨架几乎与背景同色，4~5s 内用户只看到纯黑。
   这里用品牌渐变光晕 + 旋转指示 + 明确文案，让等待「看得见、有解释、可重试」。 */
.video-loading {
  position: absolute;
  inset: 0;
  z-index: 25;
  display: flex;
  align-items: center;
  justify-content: center;
  background: #0d0b14;
  overflow: hidden;
}

.video-loading__glow {
  position: absolute;
  width: 620rpx;
  height: 620rpx;
  border-radius: 50%;
  background: radial-gradient(circle, rgba(255, 77, 141, 0.28) 0%, rgba(138, 92, 246, 0.16) 38%, rgba(13, 11, 20, 0) 72%);
  animation: loadingGlow 3.2s ease-in-out infinite;
}

.video-loading__content {
  position: relative;
  display: flex;
  flex-direction: column;
  align-items: center;
  padding: 0 72rpx;
  text-align: center;
}

.video-loading__spinner {
  width: 76rpx;
  height: 76rpx;
  margin-bottom: 40rpx;
  border-radius: 50%;
  border: 5rpx solid rgba(255, 255, 255, 0.16);
  border-top-color: #ff4d8d;
  border-right-color: #8a5cf6;
  animation: loadingSpin 0.9s linear infinite;
}

.video-loading__title {
  font-size: var(--fs-lg, 32rpx);
  font-weight: 600;
  color: #fff;
  letter-spacing: 1rpx;
}

.video-loading__desc {
  margin-top: 16rpx;
  font-size: var(--fs-sm, 24rpx);
  line-height: 1.6;
  color: rgba(255, 255, 255, 0.62);
}

.video-loading__retry {
  margin-top: 44rpx;
  padding: 16rpx 56rpx;
  border-radius: 999rpx;
  font-size: var(--fs-md, 28rpx);
  color: #fff;
  background: linear-gradient(135deg, #ff4d8d, #8a5cf6);
}

@keyframes loadingSpin {
  to { transform: rotate(360deg); }
}

@keyframes loadingGlow {
  0%, 100% { transform: scale(0.92); opacity: 0.75; }
  50% { transform: scale(1.08); opacity: 1; }
}

@media (prefers-reduced-motion: reduce) {
  .video-loading__glow,
  .video-loading__spinner { animation: none !important; }
}

/* ===== 顶部 Tab ===== */
.top-tabs {
  position: absolute;
  top: calc(env(safe-area-inset-top, 0px) + 16rpx);
  left: 0;
  right: 0;
  display: flex;
  align-items: stretch;
  height: var(--category-bar-h);
  padding: 0 var(--space-md);
  z-index: 30;

  .tab-group {
    flex: 1;
    display: flex;
    justify-content: center;
    align-items: stretch;
    gap: var(--space-xl);
  }

  .tab {
    position: relative;
    display: flex;
    align-items: center;
    /* 非选中：高亮白降至 60%（叠在深色画面上呈中性灰），字重 400 作次级层级 */
    font-size: var(--fs-lg);
    font-weight: 400;
    color: var(--text-inverse);
    opacity: 0.6;
    padding: var(--space-xs) var(--space-xs);
    transition: opacity 0.2s ease, font-size 0.2s ease;

    /* 选中：三通道区分——字重 700 + 纯白（opacity 1）+ 底部渐变条 */
    &.active {
      opacity: 1;
      font-weight: 700;
      font-size: var(--fs-xl);

      &::after {
        content: "";
        position: absolute;
        left: 50%;
        bottom: 0;
        transform: translateX(-50%);
        width: 48rpx;
        height: 8rpx;
        border-radius: var(--radius-pill);
        /* 视频页例外：深色画面上用品牌渐变，暗色主题下由 App.vue 覆盖为更亮的一档 */
        background: var(--gradient-brand);
      }
    }
  }
}

.search-btn {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 56rpx;
  height: 56rpx;
  flex-shrink: 0;

  .search-ico {
    width: 48rpx;
    height: 48rpx;
    color: var(--text-inverse);
  }
}

.video-swiper {
  height: calc(100vh - var(--tab-bar-height, 0px));
}

.video-item {
  position: relative;
  height: 100%;

  .video-player,
  .cover {
    position: absolute;
    inset: 0;
    width: 100%;
    height: 100%;
  }

  .cover-residue {
    position: absolute;
    inset: 0;
    width: 100%;
    height: 100%;
    z-index: 1;
  }
}

/* 透明点击层 */
.tap-layer {
  position: absolute;
  inset: 0;
  z-index: 5;
}

/* 中央播放指示 */
.center-state {
  position: absolute;
  inset: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  z-index: 6;
  pointer-events: none;

  .play-icon {
    width: 96rpx;
    height: 96rpx;
    color: var(--text-inverse);
    opacity: 0.85;
    filter: drop-shadow(0 4rpx 16rpx rgba(0, 0, 0, 0.4));
  }
}

/* 播放失败浮层 */
.video-error {
  position: absolute;
  inset: 0;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: var(--space-md);
  z-index: 7;
  background: rgba(0, 0, 0, 0.6);

  .video-error-title {
    font-size: var(--fs-md);
    color: var(--text-inverse);
    text-shadow: 0 2rpx 8rpx rgba(0, 0, 0, 0.5);
  }

  .video-error-retry {
    padding: var(--space-md) 48rpx;
    border: 2rpx solid var(--text-inverse);
    border-radius: var(--radius-pill);
    font-size: var(--fs-base);
    color: var(--text-inverse);
    background: rgba(255, 255, 255, 0.12);
    opacity: 0.9;
  }
}

/* 双击飞心（抖音式大爱心跳弹） */
.float-heart {
  position: absolute;
  z-index: 20;
  pointer-events: none;
  transform: translate(-50%, -50%);
  animation: floatUp 0.8s cubic-bezier(0.22, 1, 0.36, 1) forwards;

  .float-heart__ico {
    width: 96rpx;
    height: 96rpx;
    color: var(--color-primary);
    filter: drop-shadow(0 4rpx 16rpx rgba(254, 60, 114, 0.4));
  }
}

@keyframes floatUp {
  0% {
    transform: translate(-50%, -50%) scale(0) rotate(-15deg);
    opacity: 1;
  }
  30% {
    transform: translate(-50%, -70%) scale(1.4) rotate(5deg);
    opacity: 1;
  }
  60% {
    transform: translate(-50%, -100%) scale(1.1) rotate(-3deg);
    opacity: 0.9;
  }
  100% {
    transform: translate(-50%, -160%) scale(0.8) rotate(0deg);
    opacity: 0;
  }
}

/* ===== 右侧操作栏 ===== */
.side-bar {
  position: absolute;
  right: var(--space-md);
  bottom: calc(env(safe-area-inset-bottom, 0px) + 36rpx);
  z-index: 10;
  display: flex;
  flex-direction: column;
  align-items: center;

  .side-stack {
    display: flex;
    flex-direction: column;
    align-items: center;
    gap: 36rpx;
  }

  .side-btn {
    position: relative;
    display: flex;
    flex-direction: column;
    align-items: center;
    gap: var(--space-xs);

    .side-ico {
      width: 80rpx;
      height: 80rpx;
      display: flex;
      align-items: center;
      justify-content: center;
    }

    .side-ico__ph {
      width: 64rpx;
      height: 64rpx;
      color: var(--text-inverse);
    }

    .side-ico__ph--liked {
      color: var(--color-primary);
    }

    .like-pop {
      animation: likePop 0.35s ease;
    }

    @keyframes likePop {
      0% { transform: scale(1); }
      35% { transform: scale(1.35); }
      70% { transform: scale(0.9); }
      100% { transform: scale(1); }
    }

    .side-count {
      font-size: var(--fs-sm);
      color: var(--text-inverse);
      text-shadow: 0 2rpx 8rpx rgba(0, 0, 0, 0.5);
      font-weight: 500;
    }

    .side-avatar {
      width: 84rpx;
      height: 84rpx;
      border-radius: 50%;
      border: 4rpx solid var(--text-inverse);
    }

    .follow-plus {
      position: absolute;
      bottom: -8rpx;
      left: 50%;
      transform: translateX(-50%);
      width: 32rpx;
      height: 32rpx;
      background: var(--color-primary);
      border-radius: 50%;
      display: flex;
      align-items: center;
      justify-content: center;
      box-shadow: var(--shadow-primary);

      .follow-plus__ico {
        width: 20rpx;
        height: 20rpx;
        color: var(--text-inverse);
      }
    }
  }
}

/* ===== 底部信息区 ===== */
.video-info {
  position: absolute;
  left: var(--space-md);
  /* 右侧操作栏固定占位：不用固定 150rpx，改用 min() 兼顾宽屏
     （窄屏下 150rpx 会吃掉约 47% 宽度，信息区被压得只剩几十字） */
  right: min(150rpx, 20vw);
  /* 让长昵称/文案能正常折行而不溢出 */
  min-width: 0;
  bottom: calc(env(safe-area-inset-bottom, 0px) + 40rpx);
  z-index: 10;

  .author {
    font-size: var(--fs-xl);
    font-weight: 700;
    color: var(--text-inverse);
    display: block;
    margin-bottom: var(--space-sm);
    text-shadow: 0 2rpx 8rpx rgba(0, 0, 0, 0.5);
  }

  .desc {
    font-size: var(--fs-base);
    color: var(--text-inverse);
    opacity: 0.9;
    line-height: 1.5;
    display: block;
    text-shadow: 0 2rpx 8rpx rgba(0, 0, 0, 0.5);
  }

  .location-label {
    display: block;
    margin-bottom: var(--space-xs);
    font-size: var(--fs-sm);
    color: var(--text-inverse);
    opacity: 0.85;
    text-shadow: 0 2rpx 8rpx rgba(0, 0, 0, 0.5);
  }

  .music-row {
    display: flex;
    align-items: center;
    margin-top: var(--space-sm);

    .music-disc {
      flex-shrink: 0;
      width: 56rpx;
      height: 56rpx;
      border-radius: 50%;
      background: var(--bg-page);
      border: 4rpx solid var(--text-inverse);
      display: flex;
      align-items: center;
      justify-content: center;
      overflow: hidden;
      margin-right: 12rpx;

      .disc-img {
        width: 36rpx;
        height: 36rpx;
        border-radius: 50%;
      }

      &.disc-paused .disc-img {
        animation-play-state: paused;
      }
    }

    .music-track {
      flex: 1;
      overflow: hidden;
      /* 音轨宽度跟随父容器自适应；min-width:0 允许在 flex 中收缩，
         否则长音频名会把信息区撑破（BUG-09 收口） */
      min-width: 0;
      max-width: 360rpx;

      .music-text {
        display: inline-block;
        white-space: nowrap;
        font-size: var(--fs-sm);
        color: var(--text-inverse);
        opacity: 0.9;
        padding-left: 100%;
      }
    }
  }
}

.video-item.is-active .disc-img {
  animation: spin 5s linear infinite;
}

.video-item.is-active .music-text {
  animation: marquee 8s linear infinite;
}

@keyframes spin {
  to { transform: rotate(360deg); }
}

@keyframes marquee {
  0% { transform: translateX(0); }
  100% { transform: translateX(-100%); }
}

/* ===== 进度条 ===== */
.progress-bar {
  position: absolute;
  left: 0;
  right: 0;
  bottom: 0;
  height: 4rpx;
  background: rgba(255, 255, 255, 0.2);
  z-index: 6;
  pointer-events: none;

  .progress-inner {
    height: 100%;
    background: var(--gradient-brand);
    transition: width 0.2s linear;
  }
}

/* ===== 顶部/底部暗角 ===== */
.top-scrim {
  position: absolute;
  top: 0;
  left: 0;
  right: 0;
  height: 200rpx;
  background: linear-gradient(to bottom, rgba(0, 0, 0, 0.5), transparent);
  z-index: 8;
  pointer-events: none;
}

.bottom-scrim {
  position: absolute;
  left: 0;
  right: 0;
  bottom: 0;
  height: 440rpx;
  background: linear-gradient(to top, rgba(0, 0, 0, 0.62), transparent);
  z-index: 8;
  pointer-events: none;
}

/* ===== 上滑提示 ===== */
.swipe-hint {
  position: absolute;
  left: 50%;
  transform: translateX(-50%);
  bottom: calc(env(safe-area-inset-bottom, 0px) + 280rpx);
  z-index: 11;
  display: flex;
  flex-direction: column;
  align-items: center;
  pointer-events: none;
  animation: hintBounce 1.6s ease-in-out infinite;

  .swipe-triangle {
    width: 36rpx;
    height: 18rpx;
    margin-bottom: 2rpx;
  }

  .swipe-text {
    font-size: var(--fs-sm);
    color: var(--text-inverse);
    opacity: 0.9;
    text-shadow: 0 2rpx 6rpx rgba(0, 0, 0, 0.5);
  }
}

@keyframes hintBounce {
  0%, 100% { transform: translateX(-50%) translateY(0); }
  50% { transform: translateX(-50%) translateY(-10rpx); }
}

@media (prefers-reduced-motion: reduce) {
  .swipe-hint { animation: none; }
  .music-text { animation: none; }
}

/* ===== 附近：回退提示与授权引导 ===== */
.nearby-tip {
  position: absolute;
  top: calc(env(safe-area-inset-top, 0px) + 100rpx);
  left: var(--space-md);
  right: var(--space-md);
  z-index: 20;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--space-sm);
  padding: 16rpx 20rpx;
  border-radius: 16rpx;
  background: rgba(0, 0, 0, 0.55);

  &__text {
    flex: 1;
    min-width: 0;
    font-size: var(--fs-sm);
    color: var(--text-inverse);
    opacity: 0.9;
  }

  &__btn {
    flex-shrink: 0;
    padding: 8rpx 20rpx;
    border-radius: 999rpx;
    font-size: var(--fs-sm);
    color: var(--text-inverse);
    background: var(--brand-primary);
  }
}

.nearby-auth {
  position: absolute;
  inset: 0;
  z-index: 15;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: var(--space-sm);
  padding: 0 48rpx;
  text-align: center;

  &__title {
    font-size: var(--fs-xl);
    font-weight: 700;
    color: var(--text-inverse);
  }

  &__desc {
    font-size: var(--fs-base);
    color: var(--text-inverse);
    opacity: 0.8;
  }

  &__btn {
    margin-top: var(--space-sm);
    padding: 16rpx 40rpx;
    border-radius: 999rpx;
    font-size: var(--fs-base);
    color: var(--text-inverse);
    background: var(--brand-primary);
  }
}

/* ===== 底部分享面板 ===== */
.share-mask {
  position: fixed;
  inset: 0;
  background: rgba(0, 0, 0, 0.5);
  z-index: 999;
  display: flex;
  align-items: flex-end;

  .share-panel-inner {
    width: 100%;
    background: var(--bg-card);
    border-radius: var(--radius-xl) var(--radius-xl) 0 0;
    padding: var(--space-lg) var(--space-lg) calc(var(--space-lg) + env(safe-area-inset-bottom));
    animation: slideUp 0.25s ease;

    .share-title {
      text-align: center;
      font-size: var(--fs-md);
      font-weight: 600;
      color: var(--text-primary);
      margin-bottom: var(--space-lg);
    }

    .share-grid {
      display: flex;
      justify-content: space-around;
      margin-bottom: var(--space-md);

      .share-item {
        display: flex;
        flex-direction: column;
        align-items: center;
        gap: 12rpx;
        min-height: 48rpx;

        .share-ico {
          width: 96rpx;
          height: 96rpx;
          border-radius: 50%;
          display: flex;
          align-items: center;
          justify-content: center;
          color: var(--text-inverse);
          font-size: 36rpx;

          &.ico-wechat { background: #07c160; }
          &.ico-moments { background: #ff8200; }
          &.ico-copy { background: var(--color-primary); }
        }

        .share-label {
          font-size: var(--fs-sm);
          color: var(--text-secondary);
        }
      }
    }

    .share-cancel {
      display: flex;
      align-items: center;
      justify-content: center;
      font-size: var(--fs-md);
      color: var(--text-hint);
      padding: var(--space-md) 0;
      min-height: 88rpx;
      border-top: 1rpx solid var(--border-faint);
      margin-top: var(--space-xs);
    }
  }
}

@keyframes slideUp {
  from { transform: translateY(100%); }
  to { transform: translateY(0); }
}
</style>
