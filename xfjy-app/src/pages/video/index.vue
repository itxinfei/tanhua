<template>
  <view class="video-page">
    <!-- 顶部 关注 / 推荐 Tab（role=tablist 语义 + 可键盘操作） -->
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
        <uni-icons type="search" size="28" color="#fff"></uni-icons>
      </a11y-button>
    </view>

    <swiper
      class="video-swiper"
      vertical
      :current="swiperIndex"
      @change="onSwiperChange"
    >
      <swiper-item v-for="(video, idx) in displayList" :key="video.id">
        <view class="video-item">
          <!-- 当前及相邻 item 常驻 video 节点（±1 窗口），滑入即有缓存节点，避免每次滑动销毁重建原生播放器；远处的 item 退回封面图控制内存 -->
          <template v-if="Math.abs(idx - swiperIndex) <= 1">
            <video
              :id="'vid-' + video.id"
              class="video-player"
              :src="video.videoUrl"
              :autoplay="idx === swiperIndex"
              :paused="!isPlaying"
              loop
              :muted="isMuted"
              :controls="false"
              object-fit="cover"
              @timeupdate="idx === swiperIndex ? onTimeUpdate($event) : null"
              @play="onVideoPlay(idx)"
              @pause="onVideoPause(idx)"
              @error="onVideoError"
            />
            <!-- 余像：窗口内非当前 item 用封面盖住未起播的黑块 -->
            <image
              v-if="idx !== swiperIndex"
              class="cover-residue"
              :src="video.cover"
              mode="aspectFill"
              aria-hidden="true"
            />
          </template>
          <image v-else class="cover" :src="video.cover" mode="aspectFill" lazy-load :aria-label="`${video.nickname} 的视频封面`" />

          <!-- 透明点击层：单击暂停/播放，双击点赞（纯手势层，另有显式按钮） -->
          <view class="tap-layer" aria-hidden="true" @click="onVideoTap(video, $event)" />

          <!-- 中央播放/暂停指示（暂停时显示大播放键） -->
          <view v-if="currentId === video.id && !isPlaying" class="center-state" aria-hidden="true">
            <text class="play-icon">▶</text>
          </view>

          <!-- 双击飞出的爱心（纯装饰动画） -->
          <view
            v-for="h in floatHearts"
            :key="h.id"
            class="float-heart"
            :style="{ left: h.x + 'px', top: h.y + 'px' }"
            aria-hidden="true"
          ><uni-icons type="heart-filled" size="20" color="#ff5e9c"></uni-icons></view>

          <!-- 右侧操作栏（完全模仿抖音：头像+关注 / 点赞 / 评论 / 分享 / 静音 / 旋转唱片），底缘与左侧文字区对齐 -->
          <view class="side-bar">
            <view class="side-stack">
              <!-- 作者头像 + 关注（抖音置于栏顶） -->
              <a11y-button
                class="side-btn btn-feedback follow-btn"
                :label="video.hasFocus ? `取消关注 ${video.nickname}` : `关注 ${video.nickname}`"
                @tap="handleFocus(video)"
              >
                <image class="side-avatar" :src="video.avatar" mode="aspectFill" lazy-load aria-hidden="true" />
                <view v-if="!video.hasFocus" class="follow-plus" aria-hidden="true">+</view>
              </a11y-button>

              <a11y-button
                class="side-btn btn-feedback"
                :label="video.hasLiked === 1 ? `取消点赞，当前 ${video.likeCount} 赞` : `点赞，当前 ${video.likeCount} 赞`"
                @tap="handleLike(video)"
              >
                <view class="icon-circle" :class="{ liked: video.hasLiked === 1 }">
                  <uni-icons class="icon-text" :type="video.hasLiked === 1 ? 'heart-filled' : 'heart'" :color="video.hasLiked === 1 ? '#ff5e9c' : '#fff'" size="26"></uni-icons>
                </view>
                <text class="side-count">{{ video.likeCount }}</text>
              </a11y-button>

              <a11y-button class="side-btn btn-feedback" :label="`查看评论，共 ${video.commentCount} 条`" @tap="showComments(video)">
                <view class="icon-circle">
                  <uni-icons class="icon-text" type="chatbubble" color="#fff" size="26"></uni-icons>
                </view>
                <text class="side-count">{{ video.commentCount }}</text>
              </a11y-button>

              <a11y-button class="side-btn btn-feedback" :label="`分享 ${video.nickname} 的视频`" @tap="openShare(video)">
                <view class="icon-circle">
                  <text class="icon-text" aria-hidden="true">↗</text>
                </view>
              </a11y-button>

              <a11y-button
                class="side-btn btn-feedback"
                :label="isMuted ? '取消静音' : '静音'"
                @tap="toggleMute"
              >
                <view class="icon-circle">
                  <uni-icons class="icon-text" :type="isMuted ? 'sound' : 'sound-filled'" color="#fff" size="26"></uni-icons>
                </view>
              </a11y-button>
            </view>

            <!-- 旋转音乐唱片（抖音式，置于栏底） -->
            <view
              class="side-btn music-disc"
              :class="{ 'disc-paused': !isPlaying && currentId === video.id }"
              aria-hidden="true"
            >
              <image lazy-load class="disc-img" :src="video.cover" mode="aspectFill" />
              <text class="disc-note">♪</text>
            </view>
          </view>

          <!-- 底部信息 + 滚动音乐条 -->
          <view class="video-info">
            <text class="author">@{{ video.nickname }}</text>
            <text class="desc ellipsis-2">{{ video.signature }}</text>
            <view class="music-row">
              <text class="music-ico">♪</text>
              <view class="music-track">
                <text class="music-text">{{ video.nickname }} 创作的原声 · {{ video.signature || '小视频' }}</text>
              </view>
            </view>
          </view>

          <!-- 进度条（role=progressbar：读屏可获知播放进度，而非仅视觉呈现） -->
          <view v-if="currentId === video.id" class="progress-bar" role="progressbar" aria-label="播放进度" :aria-valuenow="Math.round(progress)" aria-valuemin="0" aria-valuemax="100">
            <view class="progress-inner" :style="{ width: progress + '%' }" />
          </view>
        </view>
      </swiper-item>
    </swiper>

    <!-- 顶部/底部暗角渐变：保证白字在任意视频上都清晰（抖音式层次） -->
    <view class="top-scrim" aria-hidden="true"></view>
    <view class="bottom-scrim" aria-hidden="true"></view>

    <!-- 上滑提示（纯装饰，仅首条有效视频上短暂显示；弱化动效偏好下自动静止） -->
    <view v-if="showSwipeHint" class="swipe-hint" aria-hidden="true">
      <text class="swipe-arrow">︿</text>
      <text class="swipe-text">上滑看更多</text>
    </view>

    <!-- 骨架屏（首屏加载中）：占位元素，对读屏隐藏 -->
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
    />

    <!-- 底部分享面板（对话框语义 + Esc 关闭 + 焦点管理） -->
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
import { ref, computed, watch, getCurrentInstance } from "vue";
import { onShow, onHide, onShareAppMessage } from "@dcloudio/uni-app";
import MatchPopup from "@/components/match-popup.vue";
import {
  getVideoList,
  getFollowVideoList,
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

/** 分页大小 */
const PAGE_SIZE = 10;

/** 推荐流与关注流各自独立的列表与分页 */
const recommendVideos = ref<Video[]>([]);
const recommendPage = ref(1);
const followVideos = ref<Video[]>([]);
const followPage = ref(1);

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
/** 暂未接入后端的频道：展示占位空状态 */
const PLACEHOLDER_CHANNELS: ChannelKey[] = ["hot", "friend", "location"];

/** 当前 Tab 展示的列表：关注 = 关注流接口返回，推荐 = 推荐流接口返回 */
const displayList = computed<Video[]>(() => {
  switch (activeChannel.value) {
    case "follow":
      return followVideos.value;
    case "recommend":
      return recommendVideos.value;
    default:
      return [];
  }
});

/** 评论抽屉状态 */
const commentVisible = ref(false);
const activeCommentVideoId = ref("");

/** 分页加载守卫：防止快速滑动触发重复请求；到底后不再重发 */
const loadingMore = ref(false);
const recommendHasMore = ref(true);
const followHasMore = ref(true);

/** 请求代际令牌：切频道/重载后，旧请求的响应直接丢弃 */
let loadToken = 0;

/** 当前播放视频：始终由 swiperIndex 单一推导，不再平行维护 currentId */
const currentVideo = computed<Video | undefined>(() => displayList.value[swiperIndex.value]);
const currentId = computed(() => currentVideo.value?.id ?? "");

const isPlaying = ref(true);
/** 是否静音：H5 自动播放策略要求无声才能 autoplay，小程序可有声 */
const isH5 = uni.getSystemInfoSync().uniPlatform === "web";
const isMuted = ref(isH5);
const progress = ref(0);
const loading = ref(false);

/** video 的 play/pause 事件会滞后于 index 切换到达，用此标记丢弃来自旧视频的滞后事件 */
let lastPlayedIndex = -1;

/** 单击/双击判定 */
let lastTapTime = 0;
let singleTapTimer: ReturnType<typeof setTimeout> | null = null;

/** uni-app 的上下文仍用于 onShow/onHide 等显式恢复场景，暂停/切换交由 autoplay + paused 托管 */
const instance = getCurrentInstance();

function playAt(index: number) {
  const video = displayList.value[index];
  if (!video) return;
  lastPlayedIndex = index;
  const ctx = uni.createVideoContext("vid-" + video.id, instance?.proxy as any);
  ctx?.play();
}

// 位置变化时退出残留的点击判定与待触发的单击计时（滑切自带"播放新视频"语义）
watch(swiperIndex, () => {
  lastTapTime = 0;
  if (singleTapTimer) {
    clearTimeout(singleTapTimer);
    singleTapTimer = null;
  }
});

/** 滑动切换：video 节点常驻，无需重建，只同步播放态与预加载 */
function onSwiperChange(e: any) {
  const index = e.detail.current;
  swiperIndex.value = index;
  isPlaying.value = true;
  progress.value = 0;
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
  // 切走的旧 video 卸载/切换时会补发滞后 pause 事件，丢弃，否则会误重置新视频状态
  if (idx !== swiperIndex.value || idx !== lastPlayedIndex) return;
  isPlaying.value = false;
}

function onVideoError() {
  uni.showToast({ title: "视频加载失败，可上滑再看或稍后重试", icon: "none" });
}

/** 空状态文案随频道变化 */
const emptyStateTitle = computed(() => {
  if (PLACEHOLDER_CHANNELS.includes(activeChannel.value)) {
    const label = CHANNELS.find((c) => c.key === activeChannel.value)?.label ?? "";
    return `「${label}」频道即将上线`;
  }
  return activeChannel.value === "follow" ? "还没有关注的作者" : "暂无视频";
});
const emptyStateDesc = computed(() =>
  PLACEHOLDER_CHANNELS.includes(activeChannel.value)
    ? "更多精彩内容正在筹备中，先看看推荐吧"
    : activeChannel.value === "follow"
    ? "去推荐页关注喜欢的人吧"
    : "精彩内容即将上线"
);

/** 双击飞心 */
const floatHearts = ref<{ id: number; x: number; y: number }[]>([]);

/** 分享面板 */
const showShare = ref(false);
const shareVideo = ref<Video | null>(null);

/** 切换静音 */
function toggleMute() {
  isMuted.value = !isMuted.value;
}

/** 搜索（暂未上线） */
function onSearch(): void {
  uni.showToast({ title: "搜索功能即将上线", icon: "none" });
}

/**
 * 切换顶部 Tab，重置到首条（currentId 由 swiperIndex 推导，无需手工维护）
 */
async function switchChannel(ch: ChannelKey) {
  if (activeChannel.value === ch) return;
  activeChannel.value = ch;
  swiperIndex.value = 0;
  isPlaying.value = true;
  progress.value = 0;
  if (ch === "follow" && followVideos.value.length === 0) {
    await loadVideos();
  } else if (ch === "recommend" && recommendVideos.value.length === 0) {
    await loadVideos();
  }
}

/**
 * 透明层点击：单击立即暂停/播放，250ms 内第二次点击撤销单击并视为双击（点赞）
 */
function onVideoTap(video: Video, e: any) {
  const now = Date.now();
  if (now - lastTapTime < 250) {
    if (singleTapTimer) {
      clearTimeout(singleTapTimer);
      singleTapTimer = null;
      // 撤销第一次单击造成的暂停，恢复播放
      isPlaying.value = true;
    }
    lastTapTime = 0;
    doubleLike(video, e);
  } else {
    lastTapTime = now;
    isPlaying.value = !isPlaying.value;
    singleTapTimer = setTimeout(() => {
      singleTapTimer = null;
    }, 250);
  }
}

/**
 * 双击点赞 + 飞心动画
 */
function doubleLike(video: Video, e: any) {
  spawnHeart(e);
  if (video.hasLiked !== 1) {
    handleLike(video);
  }
}

/**
 * 在点击位置生成飞出的爱心
 */
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
  }, 700);
}

/**
 * 进度条更新
 */
function onTimeUpdate(e: any) {
  const cur = e?.detail?.currentTime || 0;
  const dur = e?.detail?.duration || 0;
  progress.value = dur > 0 ? Math.min(100, (cur / dur) * 100) : 0;
}

/**
 * 点赞/取消点赞（乐观更新，失败回滚）
 */
async function handleLike(video: Video) {
  const wasLiked = video.hasLiked === 1;
  try {
    if (wasLiked) {
      video.hasLiked = 0;
      video.likeCount = Math.max(0, video.likeCount - 1);
      await dislikeVideo(video.id);
    } else {
      video.hasLiked = 1;
      video.likeCount += 1;
      await likeVideo(video.id);
    }
  } catch {
    video.hasLiked = wasLiked ? 1 : 0;
    video.likeCount += wasLiked ? 1 : -1;
  }
}

/**
 * 关注/取消关注作者（乐观更新，失败回滚）
 */
async function handleFocus(video: Video) {
  const wasFocused = video.hasFocus === 1;
  try {
    if (wasFocused) {
      video.hasFocus = 0;
      await unfocusVideoUser(video.userId);
      // 关注流中取关：移除该作者的全部视频（同作者可能有多条），并修正播放位置
      if (activeChannel.value === "follow") {
        followVideos.value = followVideos.value.filter((v) => v.userId !== video.userId);
        const safeIdx = Math.min(swiperIndex.value, Math.max(0, followVideos.value.length - 1));
        // 先归 -1 再赋值，保证即使索引数值不变 watch 也能感知"切换到了新的第 safeIdx 条"
        swiperIndex.value = -1;
        swiperIndex.value = safeIdx;
        if (followVideos.value.length > 0) playAt(safeIdx);
      }
    } else {
      video.hasFocus = 1;
      await focusVideoUser(video.userId);
    }
  } catch {
    video.hasFocus = wasFocused ? 1 : 0;
  }
}

function showComments(video: Video) {
  activeCommentVideoId.value = video.id;
  commentVisible.value = true;
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
      success: () => uni.showToast({ title: "链接已复制", icon: "none" }),
    });
  } else {
    // 未接入真实微信分享，不再谎报"已分享"
    uni.showToast({ title: "该分享方式即将上线", icon: "none" });
  }
}


/**
 * 加载视频列表首页（重载即重置滑动位置，并作废在途请求）
 */
async function loadVideos() {
  const token = ++loadToken;
  swiperIndex.value = 0;
  loading.value = true;
  try {
    if (activeChannel.value === "follow") {
      followPage.value = 1;
      followHasMore.value = true;
      const res = await getFollowVideoList({ page: 1, pagesize: PAGE_SIZE });
      if (token !== loadToken || activeChannel.value !== "follow") return;
      followVideos.value = res.items || [];
    } else {
      recommendPage.value = 1;
      recommendHasMore.value = true;
      const res = await getVideoList({ page: 1, pagesize: PAGE_SIZE });
      if (token !== loadToken || activeChannel.value !== "recommend") return;
      recommendVideos.value = res.items || [];
    }
    if (displayList.value.length > 0) {
      isPlaying.value = true;
      playAt(0);
    }
  } catch {
    // 请求失败由 request 层统一 toast
  } finally {
    if (token === loadToken) loading.value = false;
  }
}

/** 按 id 去重后追加，防止后端翻页数据变动导致 swiper 重复 key */
function appendUnique(target: Video[], items: Video[]): Video[] {
  const seen = new Set(target.map((v) => v.id));
  return target.concat(items.filter((v) => !seen.has(v.id)));
}

/**
 * 加载更多视频（上拉预加载）
 */
async function loadMoreVideos() {
  if (loadingMore.value) return;
  if (activeChannel.value === "follow" ? !followHasMore.value : !recommendHasMore.value) return;
  const token = loadToken;
  const channel = activeChannel.value;
  loadingMore.value = true;
  try {
    if (channel === "follow") {
      const nextPage = followPage.value + 1;
      const res = await getFollowVideoList({ page: nextPage, pagesize: PAGE_SIZE });
      if (token !== loadToken || activeChannel.value !== "follow") return;
      if (res.items && res.items.length > 0) {
        followVideos.value = appendUnique(followVideos.value, res.items);
        followPage.value = nextPage;
      } else {
        followHasMore.value = false;
      }
    } else {
      const nextPage = recommendPage.value + 1;
      const res = await getVideoList({ page: nextPage, pagesize: PAGE_SIZE });
      if (token !== loadToken || activeChannel.value !== "recommend") return;
      if (res.items && res.items.length > 0) {
        recommendVideos.value = appendUnique(recommendVideos.value, res.items);
        recommendPage.value = nextPage;
      } else {
        recommendHasMore.value = false;
      }
    }
  } catch {
    // 请求失败由 request 层统一 toast
  } finally {
    loadingMore.value = false;
  }
}

/** 上滑提示：仅首屏有效视频显示，4 秒后自动退场，避免与底部文字区长期重叠 */
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

/** 记录离开页面前的播放状态，返回时恢复 */
let wasPlayingBeforeHide = false;

onShow(() => {
  if (
    (activeChannel.value === "follow" || activeChannel.value === "recommend") &&
    displayList.value.length === 0
  ) {
    loadVideos();
  } else if (wasPlayingBeforeHide) {
    wasPlayingBeforeHide = false;
    isPlaying.value = true;
    playAt(swiperIndex.value);
  }
});

onHide(() => {
  // 离开页面（切后台/跳转其他 tab）时暂停，避免声音继续播放；
  // paused 属性托管的是"用户单击暂停"，tab 切走时 isPlaying 变 false 也会置 paused，双保险
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

/** 小程序端分享配置（H5 端静默忽略） */
onShareAppMessage(() => ({
  title: "心飞交友 · 遇见心动的人",
  path: "/pages/match/index",
}));
</script>


.video-page {
  height: calc(100vh - var(--window-bottom, 0px));
  background: #000;
  position: relative;
}

/* 顶部 Tab */
.top-tabs {
  position: absolute;
  top: calc(env(safe-area-inset-top, 0px) + 16rpx);
  left: 0;
  right: 0;
  display: flex;
  align-items: center;
  padding: 0 24rpx;
  z-index: 30;

  .tab-group {
    flex: 1;
    display: flex;
    justify-content: center;
    align-items: center;
    gap: 36rpx;
  }

  .tab {
    position: relative;
    font-size: 30rpx;
    color: rgba(255, 255, 255, 0.6);
    padding: 6rpx 6rpx;
    transition: color 0.2s ease, font-size 0.2s ease;

    &.active {
      color: #fff;
      font-weight: 700;
      font-size: 36rpx;

      &::after {
        content: "";
        position: absolute;
        left: 50%;
        bottom: -6rpx;
        transform: translateX(-50%);
        width: 44rpx;
        height: 6rpx;
        border-radius: 3rpx;
        background: var(--bg-card);
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
}

.video-swiper {
  height: calc(100vh - var(--window-bottom, 0px));
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

  /* 窗口内非当前 item 的封面余像（盖住未起播的黑块，pointer-events 交给上层 tap-layer） */
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
    font-size: 140rpx;
    color: rgba(255, 255, 255, 0.85);
    text-shadow: 0 4rpx 20rpx rgba(0, 0, 0, 0.4);
  }
}

/* 双击飞心 */
.float-heart {
  position: absolute;
  font-size: 96rpx;
  color: var(--color-primary);
  z-index: 20;
  pointer-events: none;
  transform: translate(-50%, -50%);
  animation: floatUp 0.7s ease-out forwards;
}

@keyframes floatUp {
  0% {
    transform: translate(-50%, -50%) scale(0.4);
    opacity: 1;
  }
  60% {
    opacity: 1;
  }
  100% {
    transform: translate(-50%, -90%) scale(1.6);
    opacity: 0;
  }
}

.side-bar {
  position: absolute;
  right: 24rpx;
  bottom: calc(env(safe-area-inset-bottom, 0px) + 36rpx);
  z-index: 10;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 40rpx;

  .side-stack {
    display: flex;
    flex-direction: column;
    align-items: center;
    gap: 40rpx;
  }

  .side-btn {
    position: relative;
    display: flex;
    flex-direction: column;
    align-items: center;
    gap: 8rpx;

    .icon-circle {
      width: 88rpx;
      height: 88rpx;
      border-radius: 50%;
      display: flex;
      align-items: center;
      justify-content: center;
      transition: transform 0.2s ease;
    }

    .icon-text {
      font-size: 52rpx;
      color: #fff;
      line-height: 1;
    }

    .side-count {
      font-size: 24rpx;
      color: #fff;
      text-shadow: 0 2rpx 8rpx rgba(0, 0, 0, 0.5);
    }

    .side-avatar {
      width: 84rpx;
      height: 84rpx;
      border-radius: 50%;
      border: 4rpx solid #fff;
    }

    .follow-plus {
      position: absolute;
      bottom: -8rpx;
      left: 50%;
      transform: translateX(-50%);
      width: 32rpx;
      height: 32rpx;
      line-height: 28rpx;
      text-align: center;
      background: var(--color-primary);
      color: #fff;
      border-radius: 50%;
      font-size: 24rpx;
      box-shadow: 0 4rpx 12rpx rgba(255, 94, 156, 0.5);
    }
  }

  /* 旋转音乐唱片 */
  .music-disc {
    position: relative;
    width: 96rpx;
    height: 96rpx;
    border-radius: 50%;
    background: #111;
    border: 6rpx solid rgba(255, 255, 255, 0.9);
    display: flex;
    align-items: center;
    justify-content: center;
    overflow: hidden;

    .disc-img {
      width: 64rpx;
      height: 64rpx;
      border-radius: 50%;
      animation: spin 5s linear infinite;
    }

    &.disc-paused .disc-img {
      animation-play-state: paused;
    }

    .disc-note {
      position: absolute;
      font-size: 30rpx;
      color: #fff;
      text-shadow: 0 1rpx 4rpx rgba(0,0,0,0.6);
    }
  }
}

@keyframes spin {
  to {
    transform: rotate(360deg);
  }
}

.video-info {
  position: absolute;
  left: 24rpx;
  right: 150rpx;
  bottom: calc(env(safe-area-inset-bottom, 0px) + 40rpx);
  z-index: 10;

  .author {
    font-size: 32rpx;
    font-weight: bold;
    color: #fff;
    display: block;
    margin-bottom: 12rpx;
    text-shadow: 0 2rpx 8rpx rgba(0,0,0,0.5);
  }

  .desc {
    font-size: 26rpx;
    color: rgba(255, 255, 255, 0.9);
    line-height: 1.5;
    display: block;
    text-shadow: 0 2rpx 8rpx rgba(0,0,0,0.5);
  }

  .music-row {
    display: flex;
    align-items: center;
    margin-top: 14rpx;

    .music-ico {
      font-size: 26rpx;
      color: #fff;
      margin-right: 10rpx;
    }

    .music-track {
      flex: 1;
      overflow: hidden;
      width: 360rpx;

      .music-text {
        display: inline-block;
        white-space: nowrap;
        font-size: 24rpx;
        color: rgba(255, 255, 255, 0.9);
        padding-left: 100%;
        animation: marquee 8s linear infinite;
      }
    }
  }
}

@keyframes marquee {
  0% {
    transform: translateX(0);
  }
  100% {
    transform: translateX(-100%);
  }
}

/* 进度条 */
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
    background: var(--bg-card);
    transition: width 0.2s linear;
  }
}

/* 顶部/底部暗角渐变（提升白字可读性，纯视觉、不拦截点击） */
.top-scrim {
  position: absolute;
  top: 0;
  left: 0;
  right: 0;
  height: 200rpx;
  background: linear-gradient(to bottom, rgba(0, 0, 0, 0.5), rgba(0, 0, 0, 0));
  z-index: 8;
  pointer-events: none;
}

.bottom-scrim {
  position: absolute;
  left: 0;
  right: 0;
  bottom: 0;
  height: 440rpx;
  background: linear-gradient(to top, rgba(0, 0, 0, 0.62), rgba(0, 0, 0, 0));
  z-index: 8;
  pointer-events: none;
}

/* 上滑提示（抖音式微动效；bottom 抬高避开左侧文字区与右侧唱片） */
.swipe-hint {
  position: absolute;
  left: 50%;
  transform: translateX(-50%);
  bottom: calc(env(safe-area-inset-bottom, 0px) + 280rpx);
  z-index: 11;
  display: flex;
  flex-direction: column;
  align-items: center;
  color: rgba(255, 255, 255, 0.9);
  pointer-events: none;
  animation: hintBounce 1.6s ease-in-out infinite;

  .swipe-arrow {
    font-size: 30rpx;
    line-height: 1;
    text-shadow: 0 2rpx 6rpx rgba(0, 0, 0, 0.5);
  }

  .swipe-text {
    font-size: 22rpx;
    margin-top: 2rpx;
    text-shadow: 0 2rpx 6rpx rgba(0, 0, 0, 0.5);
  }
}

@keyframes hintBounce {
  0%, 100% { transform: translateX(-50%) translateY(0); }
  50% { transform: translateX(-50%) translateY(-10rpx); }
}

/* 尊重系统「减弱动效」偏好 */
@media (prefers-reduced-motion: reduce) {
  .swipe-hint { animation: none; }
  .music-text { animation: none; }
}

/* 底部分享面板 */
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
    border-radius: 32rpx 32rpx 0 0;
    padding: 32rpx 32rpx calc(32rpx + env(safe-area-inset-bottom));
    animation: slideUp 0.25s ease;

    .share-title {
      text-align: center;
      font-size: 30rpx;
      font-weight: 600;
      color: var(--text-primary);
      margin-bottom: 32rpx;
    }

    .share-grid {
      display: flex;
      justify-content: space-around;
      margin-bottom: 24rpx;

      .share-item {
        display: flex;
        flex-direction: column;
        align-items: center;
        gap: 12rpx;
        /* 触摸目标 ≥24px */
        min-height: 48rpx;

        .share-ico {
          width: 96rpx;
          height: 96rpx;
          border-radius: 50%;
          display: flex;
          align-items: center;
          justify-content: center;
          color: #fff;
          font-size: 36rpx;

          &.ico-wechat { background: #07c160; }
          &.ico-moments { background: #ff8200; }
          &.ico-copy { background: var(--color-primary); }
        }

        .share-label {
          font-size: 24rpx;
          color: var(--text-secondary);
        }
      }
    }

    /* #999 → #767676；触摸目标加高 */
    .share-cancel {
      text-align: center;
      font-size: 30rpx;
      color: var(--text-hint);
      padding: 24rpx 0;
      min-height: 48rpx;
      border-top: 1rpx solid var(--border-faint);
      margin-top: 8rpx;
    }
  }
}

@keyframes slideUp {
  from { transform: translateY(100%); }
  to { transform: translateY(0); }
}

