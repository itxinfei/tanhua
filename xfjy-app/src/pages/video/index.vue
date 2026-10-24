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
          <image v-else class="cover" :src="video.cover" mode="aspectFill" />

          <!-- 右侧操作栏 -->
          <view class="side-bar">
            <view class="side-btn" @click="handleLike(video)">
              <text>{{ video.hasLiked ? '❤️' : '🤍' }}</text>
              <text class="side-count">{{ video.likeCount }}</text>
            </view>
            <view class="side-btn" @click="showComments(video)">
              <text>💬</text>
              <text class="side-count">{{ video.commentCount }}</text>
            </view>
            <view class="side-btn" @click="handleFocus(video)">
              <image class="side-avatar" :src="video.avatar" mode="aspectFill" />
              <view v-if="!video.hasFocus" class="follow-plus">+</view>
            </view>
            <view class="side-btn" @click="handleShare">
              <text>↗</text>
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

    <!-- 空状态 -->
    <view v-if="videoList.length === 0" class="empty">
      <text class="empty-text">暂无视频</text>
    </view>
  </view>
</template>

<script setup lang="ts">
import { ref } from "vue";
import { onShow } from "@dcloudio/uni-app";
import {
  getVideoList,
  likeVideo,
  dislikeVideo,
  focusVideoUser,
  unfocusVideoUser,
  type Video,
} from "@/api/video";

/** 分页大小 */
const PAGE_SIZE = 10;

const videoList = ref<Video[]>([]);
const currentId = ref("");
const page = ref(1);

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

function showComments(_video: Video) {
  // TODO: 弹出半屏评论抽屉（后续接入 getVideoComments）
  uni.showToast({ title: "评论功能开发中", icon: "none" });
}

function handleShare() {
  uni.showToast({ title: "分享功能开发中", icon: "none" });
}

/**
 * 加载视频列表首页
 */
async function loadVideos() {
  try {
    page.value = 1;
    const res = await getVideoList({ page: 1, pagesize: PAGE_SIZE });
    videoList.value = res.items || [];
    if (videoList.value.length > 0) {
      currentId.value = videoList.value[0].id;
    }
  } catch {
    // 请求失败由 request 层统一 toast
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

    font-size: 52rpx;

    .side-count {
      font-size: 24rpx;
      color: #fff;
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

.empty {
  position: absolute;
  inset: 0;
  display: flex;
  align-items: center;
  justify-content: center;

  .empty-text {
    color: #666;
    font-size: 28rpx;
  }
}
</style>