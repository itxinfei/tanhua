<template>
  <view class="video-page">
    <swiper
      class="video-swiper"
      vertical
      circular
      @change="onSwiperChange"
    >
      <swiper-item v-for="video in videoList" :key="video.id">
        <view class="video-item">
          <!-- 视频播放器 -->
          <video
            v-if="currentId === video.id"
            class="video-player"
            :src="video.videoUrl"
            autoplay
            loop
            :controls="false"
            object-fit="cover"
          />
          <!-- 封面占位 -->
          <image v-else class="cover" :src="video.cover" mode="aspectFill" lazy-load />

          <!-- 右侧操作栏 -->
          <view class="side-bar">
            <view class="side-btn btn-feedback" @click="handleLike(video)" :aria-label="video.hasLiked === 1 ? '取消点赞' : '点赞'">
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
            <view class="side-btn btn-feedback" @click="handleFocus(video)" :aria-label="video.hasFocus ? '取消关注' : '关注作者'">
              <image class="side-avatar" :src="video.avatar" mode="aspectFill" lazy-load />
              <view v-if="!video.hasFocus" class="follow-plus">+</view>
            </view>
            <view class="side-btn btn-feedback" @click="handleShare" aria-label="分享视频">
              <view class="icon-circle">
                <text class="icon-text">↗</text>
              </view>
            </view>
          </view>

          <!-- 底部信息 -->
          <view class="video-info">
            <text class="author">@{{ video.nickname }}</text>
            <text class="desc ellipsis-2">{{ video.signature }}</text>
          </view>
        </view>
      </swiper-item>
    </swiper>

    <!-- 骨架屏（首屏加载中，黑底深色骨架） -->
    <view v-if="videoList.length === 0 && loading" class="skeleton-video">
      <view class="skeleton skel-full" />
      <view class="skeleton skel-author" />
      <view class="skeleton skel-desc" />
    </view>

    <!-- 空状态 -->
    <view v-if="videoList.length === 0 && !loading" class="empty-overlay">
      <view class="empty-state">
        <view class="empty-icon">
          <text>🎬</text>
        </view>
        <text class="empty-title">暂无视频</text>
        <text class="empty-desc">精彩内容即将上线</text>
      </view>
    </view>

    <!-- 评论抽屉 -->
    <video-comment-drawer
      :visible="commentVisible"
      :video-id="activeCommentVideoId"
      @close="commentVisible = false"
    />
  </view>
</template>

<script setup lang="ts">
import { ref } from "vue";
import { onShow, onShareAppMessage } from "@dcloudio/uni-app";
import {
  getVideoList,
  likeVideo,
  dislikeVideo,
  focusVideoUser,
  unfocusVideoUser,
  type Video,
} from "@/api/video";
import VideoCommentDrawer from "@/components/video-comment-drawer.vue";

/** 分页大小 */
const PAGE_SIZE = 10;

const videoList = ref<Video[]>([]);
const currentId = ref("");
const page = ref(1);
const loading = ref(false);

/** 评论抽屉状态 */
const commentVisible = ref(false);
const activeCommentVideoId = ref("");

/**
 * 滑动切换视频，接近底部时预加载下一页
 */
function onSwiperChange(e: any) {
  const index = e.detail.current;
  const video = videoList.value[index];
  if (video) {
    currentId.value = video.id;
    if (index >= videoList.value.length - 2) {
      loadMoreVideos();
    }
  }
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
    // 回滚本地状态
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

function handleShare() {
  uni.showToast({ title: "点击右上角 ··· 分享给好友", icon: "none" });
}

/**
 * 加载视频列表首页
 */
async function loadVideos() {
  loading.value = true;
  try {
    page.value = 1;
    const res = await getVideoList({ page: 1, pagesize: PAGE_SIZE });
    videoList.value = res.items || [];
    if (videoList.value.length > 0) {
      currentId.value = videoList.value[0].id;
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
    const nextPage = page.value + 1;
    const res = await getVideoList({ page: nextPage, pagesize: PAGE_SIZE });
    if (res.items && res.items.length > 0) {
      videoList.value.push(...res.items);
      page.value = nextPage;
    }
  } catch {
    // 请求失败由 request 层统一 toast
  }
}

onShow(() => {
  if (videoList.value.length === 0) loadVideos();
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

.side-bar {
  position: absolute;
  right: 24rpx;
  bottom: 200rpx;
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
      background: rgba(255, 255, 255, 0.15);
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
      width: 80rpx;
      height: 80rpx;
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
}

.video-info {
  position: absolute;
  left: 24rpx;
  right: 140rpx;
  bottom: 120rpx;
  z-index: 10;

  .author {
    font-size: 32rpx;
    font-weight: bold;
    color: #fff;
    display: block;
    margin-bottom: 12rpx;
  }

  .desc {
    font-size: 26rpx;
    color: rgba(255, 255, 255, 0.9);
    line-height: 1.5;
    display: block;
  }
}

/* 空状态遮罩（黑底适配，覆盖全局 empty-state 颜色） */
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

/* 骨架屏（黑底深色骨架，覆盖全局浅色 skeleton） */
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
</style>