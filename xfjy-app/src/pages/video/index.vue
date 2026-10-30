<template>
  <view class="video-page">
    <!-- 顶部 关注 / 推荐 Tab（抖音标志性） -->
    <view class="top-tabs">
      <view
        class="tab"
        :class="{ active: activeTab === 'follow' }"
        @click="switchTab('follow')"
      >关注</view>
      <view
        class="tab"
        :class="{ active: activeTab === 'recommend' }"
        @click="switchTab('recommend')"
      >推荐</view>
    </view>

    <swiper
      class="video-swiper"
      vertical
      circular
      :current="swiperIndex"
      @change="onSwiperChange"
    >
      <swiper-item v-for="video in displayList" :key="video.id">
        <view class="video-item">
          <!-- 视频播放器 / 封面占位 -->
          <video
            v-if="currentId === video.id"
            class="video-player"
            :src="video.videoUrl"
            autoplay
            loop
            :controls="false"
            object-fit="cover"
            :paused="!isPlaying"
            @timeupdate="onTimeUpdate"
          />
          <image v-else class="cover" :src="video.cover" mode="aspectFill" lazy-load />

          <!-- 透明点击层：单击暂停/播放，双击点赞 -->
          <view class="tap-layer" @click="onVideoTap(video, $event)" />

          <!-- 中央播放/暂停指示（暂停时显示大播放键） -->
          <view v-if="currentId === video.id && !isPlaying" class="center-state">
            <text class="play-icon">▶</text>
          </view>

          <!-- 双击飞出的爱心 -->
          <view
            v-for="h in floatHearts"
            :key="h.id"
            class="float-heart"
            :style="{ left: h.x + 'px', top: h.y + 'px' }"
          >❤</view>

          <!-- 右侧操作栏（抖音式：点赞/评论/关注/唱片/分享） -->
          <view class="side-bar">
            <view
              class="side-btn btn-feedback"
              @click="handleLike(video)"
              :aria-label="video.hasLiked === 1 ? '取消点赞' : '点赞'"
            >
              <view class="icon-circle" :class="{ liked: video.hasLiked === 1 }">
                <text class="icon-text">{{ video.hasLiked === 1 ? '♥' : '♡' }}</text>
              </view>
              <text class="side-count">{{ video.likeCount }}</text>
            </view>

            <view class="side-btn btn-feedback" @click="showComments(video)" aria-label="查看评论">
              <view class="icon-circle">
                <text class="icon-text">💬</text>
              </view>
              <text class="side-count">{{ video.commentCount }}</text>
            </view>

            <view
              class="side-btn btn-feedback follow-btn"
              @click="handleFocus(video)"
              :aria-label="video.hasFocus ? '取消关注' : '关注作者'"
            >
              <image class="side-avatar" :src="video.avatar" mode="aspectFill" lazy-load />
              <view v-if="!video.hasFocus" class="follow-plus">+</view>
            </view>

            <!-- 旋转音乐唱片 -->
            <view
              class="side-btn music-disc"
              :class="{ 'disc-paused': !isPlaying && currentId === video.id }"
            >
              <image class="disc-img" :src="video.cover" mode="aspectFill" />
              <text class="disc-note">♪</text>
            </view>

            <view class="side-btn btn-feedback" @click="openShare(video)" aria-label="分享视频">
              <view class="icon-circle">
                <text class="icon-text">↗</text>
              </view>
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

          <!-- 进度条 -->
          <view v-if="currentId === video.id" class="progress-bar">
            <view class="progress-inner" :style="{ width: progress + '%' }" />
          </view>
        </view>
      </swiper-item>
    </swiper>

    <!-- 骨架屏（首屏加载中） -->
    <view v-if="displayList.length === 0 && loading" class="skeleton-video">
      <view class="skeleton skel-full" />
      <view class="skeleton skel-author" />
      <view class="skeleton skel-desc" />
    </view>

    <!-- 空状态 -->
    <view v-if="displayList.length === 0 && !loading" class="empty-overlay">
      <view class="empty-state">
        <view class="empty-icon"><text>{{ activeTab === 'follow' ? '👀' : '🎬' }}</text></view>
        <text class="empty-title">{{ activeTab === 'follow' ? '还没有关注的作者' : '暂无视频' }}</text>
        <text class="empty-desc">{{ activeTab === 'follow' ? '去推荐页关注喜欢的人吧' : '精彩内容即将上线' }}</text>
      </view>
    </view>

    <!-- 评论抽屉 -->
    <video-comment-drawer
      :visible="commentVisible"
      :video-id="activeCommentVideoId"
      @close="commentVisible = false"
    />

    <!-- 底部分享面板 -->
    <view v-if="showShare" class="share-mask" @click="showShare = false">
      <view class="share-panel" @click.stop>
        <view class="share-title">分享给好友</view>
        <view class="share-grid">
          <view class="share-item" @click="doShare('wechat')">
            <view class="share-ico ico-wechat">微</view>
            <text class="share-label">微信</text>
          </view>
          <view class="share-item" @click="doShare('moments')">
            <view class="share-ico ico-moments">友</view>
            <text class="share-label">朋友圈</text>
          </view>
          <view class="share-item" @click="doShare('copy')">
            <view class="share-ico ico-copy">链</view>
            <text class="share-label">复制链接</text>
          </view>
        </view>
        <view class="share-cancel" @click="showShare = false">取消</view>
      </view>
    </view>
    <!-- 匹配成功弹窗（全局，由 match store 驱动） -->
    <MatchPopup />
  </view>
</template>

<script setup lang="ts">
import { ref, computed } from "vue";
import { onShow, onShareAppMessage } from "@dcloudio/uni-app";
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

/** 分页大小 */
const PAGE_SIZE = 10;

/** 推荐流与关注流各自独立的列表与分页 */
const recommendVideos = ref<Video[]>([]);
const recommendPage = ref(1);
const followVideos = ref<Video[]>([]);
const followPage = ref(1);

const currentId = ref("");
const swiperIndex = ref(0);
const activeTab = ref<"follow" | "recommend">("recommend");
const isPlaying = ref(true);
const progress = ref(0);
const loading = ref(false);

/** 评论抽屉状态 */
const commentVisible = ref(false);
const activeCommentVideoId = ref("");

/** 双击飞心 */
const floatHearts = ref<{ id: number; x: number; y: number }[]>([]);

/** 分享面板 */
const showShare = ref(false);
const shareVideo = ref<Video | null>(null);

/** 当前 Tab 展示的列表：关注 = 关注流接口返回，推荐 = 推荐流接口返回 */
const displayList = computed(() =>
  activeTab.value === "follow" ? followVideos.value : recommendVideos.value
);

/** 单击/双击判定 */
let lastTapTime = 0;
let singleTapTimer: ReturnType<typeof setTimeout> | null = null;

/**
 * swiper 切换：更新当前视频并重置播放态，接近底部预加载
 */
function onSwiperChange(e: any) {
  const index = e.detail.current;
  swiperIndex.value = index;
  const video = displayList.value[index];
  if (video) {
    currentId.value = video.id;
    isPlaying.value = true;
    progress.value = 0;
    if (index >= displayList.value.length - 2) {
      loadMoreVideos();
    }
  }
}

/**
 * 切换顶部 Tab，重置到首条
 */
async function switchTab(tab: "follow" | "recommend") {
  if (activeTab.value === tab) return;
  activeTab.value = tab;
  swiperIndex.value = 0;
  const list = activeTab.value === "follow" ? followVideos.value : recommendVideos.value;
  if (list.length === 0) {
    await loadVideos();
  }
  const first = displayList.value[0];
  currentId.value = first ? first.id : "";
  isPlaying.value = true;
  progress.value = 0;
}

/**
 * 透明层点击：250ms 内第二次点击视为双击（点赞），否则单击（暂停/播放）
 */
function onVideoTap(video: Video, e: any) {
  const now = Date.now();
  if (now - lastTapTime < 250) {
    if (singleTapTimer) {
      clearTimeout(singleTapTimer);
      singleTapTimer = null;
    }
    lastTapTime = 0;
    doubleLike(video, e);
  } else {
    lastTapTime = now;
    singleTapTimer = setTimeout(() => {
      singleTapTimer = null;
      if (currentId.value === video.id) {
        isPlaying.value = !isPlaying.value;
      }
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
      // 关注流中取关：从关注列表移除该视频并修正当前播放位置
      if (activeTab.value === "follow") {
        followVideos.value = followVideos.value.filter((v) => v.id !== video.id);
        if (followVideos.value.length === 0) {
          currentId.value = "";
          swiperIndex.value = 0;
        } else {
          const safeIdx = Math.min(swiperIndex.value, followVideos.value.length - 1);
          swiperIndex.value = safeIdx;
          currentId.value = followVideos.value[safeIdx].id;
        }
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
    uni.showToast({
      title: channel === "wechat" ? "已分享到微信" : "已分享到朋友圈",
      icon: "none",
    });
  }
}

/**
 * 加载视频列表首页
 */
async function loadVideos() {
  loading.value = true;
  try {
    if (activeTab.value === "follow") {
      followPage.value = 1;
      const res = await getFollowVideoList({ page: 1, pagesize: PAGE_SIZE });
      followVideos.value = res.items || [];
    } else {
      recommendPage.value = 1;
      const res = await getVideoList({ page: 1, pagesize: PAGE_SIZE });
      recommendVideos.value = res.items || [];
    }
    const first = displayList.value[0];
    if (first) {
      currentId.value = first.id;
      isPlaying.value = true;
    }
  } catch {
    // 请求失败由 request 层统一 toast
  } finally {
    loading.value = false;
  }
}

/**
 * 加载更多视频（上拉预加载）
 */
async function loadMoreVideos() {
  try {
    if (activeTab.value === "follow") {
      const nextPage = followPage.value + 1;
      const res = await getFollowVideoList({ page: nextPage, pagesize: PAGE_SIZE });
      if (res.items && res.items.length > 0) {
        followVideos.value.push(...res.items);
        followPage.value = nextPage;
      }
    } else {
      const nextPage = recommendPage.value + 1;
      const res = await getVideoList({ page: nextPage, pagesize: PAGE_SIZE });
      if (res.items && res.items.length > 0) {
        recommendVideos.value.push(...res.items);
        recommendPage.value = nextPage;
      }
    }
  } catch {
    // 请求失败由 request 层统一 toast
  }
}

onShow(() => {
  if (displayList.value.length === 0) loadVideos();
});

/** 小程序端分享配置（H5 端静默忽略） */
onShareAppMessage(() => ({
  title: "心飞交友 · 遇见心动的人",
  path: "/pages/match/index",
}));
</script>

<style lang="scss" scoped>
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
  justify-content: center;
  gap: 48rpx;
  z-index: 30;
  pointer-events: auto;

  .tab {
    position: relative;
    font-size: 32rpx;
    color: rgba(255, 255, 255, 0.55);
    padding: 6rpx 4rpx;
    transition: color 0.2s ease;

    &.active {
      color: #fff;
      font-weight: 700;

      &::after {
        content: "";
        position: absolute;
        left: 50%;
        bottom: -8rpx;
        transform: translateX(-50%);
        width: 40rpx;
        height: 6rpx;
        border-radius: 3rpx;
        background: #fff;
      }
    }
  }
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
  color: #ff2d55;
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
  bottom: 220rpx;
  display: flex;
  flex-direction: column;
  gap: 40rpx;
  z-index: 10;

  .side-btn {
    display: flex;
    flex-direction: column;
    align-items: center;
    gap: 8rpx;

    .icon-circle {
      width: 88rpx;
      height: 88rpx;
      border-radius: 50%;
      background: rgba(255,255,255,0.15);
      backdrop-filter: blur(8px);
      -webkit-backdrop-filter: blur(8px);
      display: flex;
      align-items: center;
      justify-content: center;
      transition: transform 0.2s ease, background 0.2s ease;

      &.liked {
        background: rgba(255, 94, 142, 0.9);
      }
    }

    .icon-text {
      font-size: 44rpx;
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
      background: #ff5e8e;
      color: #fff;
      border-radius: 50%;
      font-size: 24rpx;
      box-shadow: 0 4rpx 12rpx rgba(255, 94, 142, 0.5);
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
  bottom: 120rpx;
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
    background: #fff;
    transition: width 0.2s linear;
  }
}

/* 空状态遮罩 */
.empty-overlay {
  position: absolute;
  inset: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  z-index: 20;

  .empty-state {
    .empty-icon {
      background: rgba(255, 255, 255, 0.1);
    }
    .empty-title {
      color: rgba(255, 255, 255, 0.9);
    }
    .empty-desc {
      color: rgba(255, 255, 255, 0.5);
    }
  }
}

/* 骨架屏 */
.skeleton-video {
  position: absolute;
  inset: 0;
  z-index: 20;

  .skeleton {
    background: linear-gradient(90deg, #1a1a1a 25%, #2a2a2a 50%, #1a1a1a 75%);
    background-size: 800rpx 100%;
  }

  .skel-full {
    position: absolute;
    inset: 0;
    border-radius: 0;
  }

  .skel-author {
    position: absolute;
    left: 24rpx;
    bottom: 140rpx;
    width: 200rpx;
    height: 32rpx;
  }

  .skel-desc {
    position: absolute;
    left: 24rpx;
    bottom: 90rpx;
    width: 400rpx;
    height: 26rpx;
  }
}

/* 底部分享面板 */
.share-mask {
  position: fixed;
  inset: 0;
  background: rgba(0, 0, 0, 0.5);
  z-index: 999;
  display: flex;
  align-items: flex-end;

  .share-panel {
    width: 100%;
    background: #fff;
    border-radius: 32rpx 32rpx 0 0;
    padding: 32rpx 32rpx calc(32rpx + env(safe-area-inset-bottom));
    animation: slideUp 0.25s ease;

    .share-title {
      text-align: center;
      font-size: 30rpx;
      font-weight: 600;
      color: #333;
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
          &.ico-copy { background: #ff5e8e; }
        }

        .share-label {
          font-size: 24rpx;
          color: #666;
        }
      }
    }

    .share-cancel {
      text-align: center;
      font-size: 30rpx;
      color: #999;
      padding: 20rpx 0;
      border-top: 1rpx solid #f5f5f5;
      margin-top: 8rpx;
    }
  }
}

@keyframes slideUp {
  from { transform: translateY(100%); }
  to { transform: translateY(0); }
}
</style>
