<template>
  <view class="video-page">
    <!-- 顶部 Tab（role=tablist 语义 + 可键盘操作） -->
    <view class="top-tabs" role="tablist" aria-label="视频频道">
      <view class="tab-group">
        <a11y-button
          v-for="ch in CHANNELS"
          :key="ch.key"
          class="tab"
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
                class="side-btn btn-feedback follow-btn"
                :label="video.hasFocus ? `取消关注 ${video.nickname}` : `关注 ${video.nickname}`"
                @tap="handleFocus(video)"
              >
                <image class="side-avatar" :src="video.avatar" mode="aspectFill" lazy-load aria-hidden="true" />
                <view v-if="!video.hasFocus" class="follow-plus" aria-hidden="true">
                  <view class="follow-plus__ico i-ph-plus-bold" />
                </view>
              </a11y-button>

              <a11y-button
                class="side-btn btn-feedback"
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
                <text class="side-count">{{ formatCount(video.likeCount) }}</text>
              </a11y-button>

              <a11y-button class="side-btn btn-feedback" :label="`查看评论，共 ${video.commentCount} 条`" @tap="showComments(video)">
                <view class="side-ico">
                  <view class="side-ico__ph i-ph-chat-circle-dots-bold" aria-hidden="true" />
                </view>
                <text class="side-count">{{ formatCount(video.commentCount) }}</text>
              </a11y-button>

              <a11y-button class="side-btn btn-feedback" :label="`分享 ${video.nickname} 的视频`" @tap="openShare(video)">
                <view class="side-ico">
                  <view class="side-ico__ph i-ph-arrow-bend-up-right-bold" aria-hidden="true" />
                </view>
              </a11y-button>
            </view>
          </view>

          <!-- 底部信息 + 滚动音乐条 -->
          <view class="video-info">
            <text class="author">@{{ video.nickname }}</text>
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

    <!-- 上滑提示（抖音式 SVG 三角 + 呼吸动画） -->
    <view v-if="showSwipeHint" class="swipe-hint" aria-hidden="true">
      <svg class="swipe-triangle" viewBox="0 0 24 12" aria-hidden="true">
        <path d="M12 0L24 12H0z" fill="var(--text-inverse)" opacity="0.9"/>
      </svg>
      <text class="swipe-text">上滑看更多</text>
    </view>

    <!-- 骨架屏 -->
    <skeleton v-if="displayList.length === 0 && loading" variant="video" />

    <!-- 空状态 -->
    <empty-state
      v-if="displayList.length === 0 && !loading"
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
  getFriendVideoList,
  getLocationVideoList,
  likeVideo,
  dislikeVideo,
  focusVideoUser,
  unfocusVideoUser,
  type Video,
} from "@/api/video";
import VideoCommentDrawer from "@/components/video-comment-drawer.vue";
import A11yButton from "@/components/a11y-button.vue";
import EmptyState from "@/components/empty-state.vue";
import Skeleton from "@/components/skeleton.vue";
import A11yDialog from "@/components/a11y-dialog.vue";
import { formatCount } from "@/utils/format";
import { info } from "@/utils/ux";

/** 分页大小 */
const PAGE_SIZE = 10;

/** 5 个频道各自独立的列表与分页 */
const channels = {
  recommend: { list: ref<Video[]>([]), page: ref(1), hasMore: ref(true) },
  follow: { list: ref<Video[]>([]), page: ref(1), hasMore: ref(true) },
  hot: { list: ref<Video[]>([]), page: ref(1), hasMore: ref(true) },
  friend: { list: ref<Video[]>([]), page: ref(1), hasMore: ref(true) },
  location: { list: ref<Video[]>([]), page: ref(1), hasMore: ref(true) },
} as const;

/** 按频道 key 获取对应的 API 请求函数 */
function getChannelApi(key: ChannelKey) {
  switch (key) {
    case "recommend": return getVideoList;
    case "follow": return getFollowVideoList;
    case "hot": return getHotVideoList;
    case "friend": return getFriendVideoList;
    case "location": return getLocationVideoList;
  }
}

const swiperIndex = ref(0);
type ChannelKey = "hot" | "friend" | "follow" | "location" | "recommend";
const CHANNELS = [
  { key: "hot", label: "热点" },
  { key: "friend", label: "朋友" },
  { key: "follow", label: "关注" },
  { key: "location", label: "定位" },
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
}

function onVideoPause(idx: number) {
  if (idx !== swiperIndex.value || idx !== lastPlayedIndex) return;
  isPlaying.value = false;
}

const erroredVideoIds = ref(new Set<string>());
const videoRetryTick = ref<Record<string, number>>({});

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
    case "friend": return "还没有好友";
    case "hot": return "暂无热门视频";
    case "location": return "暂无附近的视频";
    default: return "暂无视频";
  }
});
const emptyStateDesc = computed(() => {
  switch (activeChannel.value) {
    case "follow": return "去推荐页关注喜欢的人吧";
    case "friend": return "去推荐页关注喜欢的人吧";
    case "hot": return "精彩内容正在路上";
    case "location": return "开启定位后看看附近的人吧";
    default: return "精彩内容即将上线";
  }
});

const floatHearts = ref<{ id: number; x: number; y: number }[]>([]);

const showShare = ref(false);
const shareVideo = ref<Video | null>(null);

function onSearch(): void {
  info("搜索功能即将上线");
}

async function switchChannel(ch: ChannelKey) {
  if (activeChannel.value === ch) return;
  activeChannel.value = ch;
  swiperIndex.value = 0;
  isPlaying.value = true;
  progress.value = 0;
  const chState = channels[ch];
  erroredVideoIds.value = new Set();
  if (chState.list.value.length === 0) {
    await loadVideos();
  } else {
    playAt(0);
  }
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
  loading.value = true;
  erroredVideoIds.value = new Set();
  try {
    chState.page.value = 1;
    chState.hasMore.value = true;
    const res = await apiFn({ page: 1, pagesize: PAGE_SIZE });
    if (token !== loadToken || activeChannel.value !== ch) return;
    chState.list.value = res.items || [];
    if (displayList.value.length > 0) {
      isPlaying.value = true;
      playAt(0);
    }
  } catch {
    // request 层统一 toast
  } finally {
    if (token === loadToken) loading.value = false;
  }
}

function appendUnique(target: Video[], items: Video[]): Video[] {
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
    const res = await apiFn({ page: nextPage, pagesize: PAGE_SIZE });
    if (token !== loadToken || activeChannel.value !== channel) return;
    if (res.items && res.items.length > 0) {
      chState.list.value = appendUnique(chState.list.value, res.items);
      chState.page.value = nextPage;
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

/* ===== 顶部 Tab ===== */
.top-tabs {
  position: absolute;
  top: calc(env(safe-area-inset-top, 0px) + 16rpx);
  left: 0;
  right: 0;
  display: flex;
  align-items: center;
  padding: 0 var(--space-md);
  z-index: 30;

  .tab-group {
    flex: 1;
    display: flex;
    justify-content: center;
    align-items: center;
    gap: var(--space-xl);
  }

  .tab {
    position: relative;
    font-size: var(--fs-lg);
    color: var(--text-inverse);
    opacity: 0.6;
    padding: var(--space-xs) var(--space-xs);
    transition: opacity 0.2s ease, font-size 0.2s ease;

    &.active {
      opacity: 1;
      font-weight: 700;
      font-size: var(--fs-xl);

      &::after {
        content: "";
        position: absolute;
        left: 50%;
        bottom: -6rpx;
        transform: translateX(-50%);
        width: 44rpx;
        height: 6rpx;
        border-radius: 3rpx;
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
  right: 150rpx;
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
      width: 360rpx;

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
