<template>
  <view class="video-page">
    <!-- 顶部 关注 / 推荐 Tab（role=tablist 语义 + 可键盘操作） -->
    <view class="top-tabs" role="tablist" aria-label="视频频道">
      <view class="tab-group">
        <a11y-button class="tab" role="tab" :checked="activeTab === 'follow'" label="关注" @tap="switchTab('follow')">关注</a11y-button>
        <a11y-button class="tab" role="tab" :checked="activeTab === 'recommend'" label="推荐" @tap="switchTab('recommend')">推荐</a11y-button>
      </view>
      <a11y-button class="publish-btn btn-feedback" label="发布视频" @tap="chooseAndPublish">＋ 发布</a11y-button>
    </view>

    <swiper
      class="video-swiper"
      vertical
      :current="swiperIndex"
      @change="onSwiperChange"
    >
      <swiper-item v-for="video in displayList" :key="video.id">
        <view class="video-item">
          <!-- 视频播放器 / 封面占位 -->
          <video
            v-if="currentId === video.id"
            :id="'vid-' + video.id"
            class="video-player"
            :src="video.videoUrl"
            autoplay
            loop
            :muted="isMuted"
            :controls="false"
            object-fit="cover"
            @timeupdate="onTimeUpdate"
            @play="isPlaying = true"
            @pause="isPlaying = false"
          />
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

          <!-- 右侧操作栏（完全模仿抖音：头像+关注 / 点赞 / 评论 / 分享 / 静音 / 旋转唱片） -->
          <view class="side-bar">
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
                <uni-icons class="icon-text" :type="video.hasLiked === 1 ? 'heart-filled' : 'heart'" :color="video.hasLiked === 1 ? '#fe2c55' : '#fff'" size="26"></uni-icons>
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

    <!-- 上滑提示（纯装饰，弱化动效偏好下自动静止） -->
    <view class="swipe-hint" aria-hidden="true">
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
      :title="activeTab === 'follow' ? '还没有关注的作者' : '暂无视频'"
      :desc="activeTab === 'follow' ? '去推荐页关注喜欢的人吧' : '精彩内容即将上线'"
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
import { ref, computed, watch, nextTick, getCurrentInstance } from "vue";
import { onShow, onHide, onShareAppMessage } from "@dcloudio/uni-app";
import MatchPopup from "@/components/match-popup.vue";
import {
  getVideoList,
  getFollowVideoList,
  likeVideo,
  dislikeVideo,
  focusVideoUser,
  unfocusVideoUser,
  publishVideo,
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

const currentId = ref("");
const swiperIndex = ref(0);
const activeTab = ref<"follow" | "recommend">("recommend");
const isPlaying = ref(true);
/** 是否静音：默认关闭（抖音式有声），可手动切换 */
const isMuted = ref(false);
const progress = ref(0);
const loading = ref(false);

/** 评论抽屉状态 */
const commentVisible = ref(false);
const activeCommentVideoId = ref("");

/** uni-app 的 :paused 属性不可靠，改用视频上下文控制播放 */
const instance = getCurrentInstance();

function applyPlayState() {
  if (!currentId.value) return;
  const ctx = uni.createVideoContext("vid-" + currentId.value, instance?.proxy as any);
  if (!ctx) return;
  if (isPlaying.value) {
    ctx.play();
  } else {
    ctx.pause();
  }
}

watch(isPlaying, applyPlayState);

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

/** 分页加载守卫：防止快速滑动触发重复请求；到底后不再重发 */
const loadingMore = ref(false);
const recommendHasMore = ref(true);
const followHasMore = ref(true);

/**
 * swiper 切换：更新当前视频并重置播放态，接近底部预加载
 */
function onSwiperChange(e: any) {
  const index = e.detail.current;
  swiperIndex.value = index;
  const video = displayList.value[index];
  if (video) {
    // 切走前先暂停上一个视频，避免其音频残留（swiper 复用节点时尤为关键）
    if (currentId.value && currentId.value !== video.id) {
      const prevCtx = uni.createVideoContext("vid-" + currentId.value, instance?.proxy as any);
      if (prevCtx) prevCtx.pause();
    }
    currentId.value = video.id;
    isPlaying.value = true;
    progress.value = 0;
    // 新视频节点此刻才渲染（autoplay 兜底），渲染完成后上下文才可取到
    nextTick(applyPlayState);
    if (index >= displayList.value.length - 2) {
      loadMoreVideos();
    }
  }
}

/** 切换静音 */
function toggleMute() {
  isMuted.value = !isMuted.value;
}

/**
 * 切换顶部 Tab，重置到首条
 */
async function switchTab(tab: "follow" | "recommend") {
  if (activeTab.value === tab || loading.value) return;
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

/** 发布流程：选视频 → 封面兜底 → 上传发布 → 刷新列表 */
function chooseAndPublish(): void {
  uni.chooseVideo({
    sourceType: ["album", "camera"],
    compressed: true,
    success: (res) => {
      const videoPath = res.tempFilePath;
      if (!videoPath) {
        uni.showToast({ title: "未选择视频", icon: "none" });
        return;
      }
      // 部分平台不返回封面（H5），用默认封面兜底；thumbTempFilePath 运行时存在但类型定义缺失
      const thumb = (res as { thumbTempFilePath?: string }).thumbTempFilePath;
      const thumbnailPath = thumb || "/static/logo/logo-horizontal.jpg";
      uni.showLoading({ title: "发布中", mask: true });
      publishVideo({ thumbnailPath, videoPath })
        .then(() => {
          uni.hideLoading();
          uni.showToast({ title: "发布成功", icon: "success" });
          loadVideos();
        })
        .catch(() => {
          uni.hideLoading();
          // request 层已统一 toast 失败原因
        });
    },
  });
}

/**
 * 加载视频列表首页
 */
async function loadVideos() {
  loading.value = true;
  try {
    if (activeTab.value === "follow") {
      followPage.value = 1;
      followHasMore.value = true;
      const res = await getFollowVideoList({ page: 1, pagesize: PAGE_SIZE });
      followVideos.value = res.items || [];
    } else {
      recommendPage.value = 1;
      recommendHasMore.value = true;
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
  if (loadingMore.value) return;
  if (activeTab.value === "follow" ? !followHasMore.value : !recommendHasMore.value) return;
  loadingMore.value = true;
  try {
    if (activeTab.value === "follow") {
      const nextPage = followPage.value + 1;
      const res = await getFollowVideoList({ page: nextPage, pagesize: PAGE_SIZE });
      if (res.items && res.items.length > 0) {
        followVideos.value.push(...res.items);
        followPage.value = nextPage;
      } else {
        followHasMore.value = false;
      }
    } else {
      const nextPage = recommendPage.value + 1;
      const res = await getVideoList({ page: nextPage, pagesize: PAGE_SIZE });
      if (res.items && res.items.length > 0) {
        recommendVideos.value.push(...res.items);
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

/** 记录离开页面前的播放状态，返回时恢复 */
let wasPlayingBeforeHide = false;

onShow(() => {
  if (displayList.value.length === 0) {
    loadVideos();
  } else if (wasPlayingBeforeHide) {
    wasPlayingBeforeHide = false;
    isPlaying.value = true;
    nextTick(applyPlayState);
  }
});

onHide(() => {
  // 离开页面（切后台/跳转其他 tab）时暂停，避免声音继续播放
  wasPlayingBeforeHide = isPlaying.value;
  if (isPlaying.value) {
    isPlaying.value = false;
    applyPlayState();
  }
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
  align-items: center;
  padding: 0 24rpx;
  z-index: 30;

  .tab-group {
    flex: 1;
    display: flex;
    justify-content: center;
    align-items: center;
    gap: 48rpx;
  }

  .publish-btn {
    flex-shrink: 0;
    margin-left: 16rpx;
    font-size: 26rpx;
    color: #fff;
    background: linear-gradient(90deg, var(--color-primary-deep), var(--color-primary));
    padding: 8rpx 24rpx;
    border-radius: 28rpx;
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
      background: #fe2c55;
      color: #fff;
      border-radius: 50%;
      font-size: 24rpx;
      box-shadow: 0 4rpx 12rpx rgba(254, 44, 85, 0.5);
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

/* 上滑提示（抖音式微动效） */
.swipe-hint {
  position: absolute;
  left: 50%;
  transform: translateX(-50%);
  bottom: calc(env(safe-area-inset-bottom, 0px) + 24rpx);
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
          &.ico-copy { background: #ff5e9c; }
        }

        .share-label {
          font-size: 24rpx;
          color: #666;
        }
      }
    }

    /* #999 → #767676；触摸目标加高 */
    .share-cancel {
      text-align: center;
      font-size: 30rpx;
      color: #767676;
      padding: 24rpx 0;
      min-height: 48rpx;
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
