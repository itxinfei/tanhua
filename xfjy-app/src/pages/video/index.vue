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
            :src="video.url"
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
              <text>{{ video.liked ? '❤️' : '🤍' }}</text>
              <text class="side-count">{{ video.likeCount }}</text>
            </view>
            <view class="side-btn" @click="showComments(video)">
              <text>💬</text>
              <text class="side-count">{{ video.commentCount }}</text>
            </view>
            <view class="side-btn" @click="handleFocus(video)">
              <image class="side-avatar" :src="video.avatar" mode="aspectFill" />
              <view v-if="!video.followed" class="follow-plus">+</view>
            </view>
            <view class="side-btn" @click="handleShare">
              <text>↗</text>
            </view>
          </view>

          <!-- 底部信息 -->
          <view class="video-info">
            <text class="author">@{{ video.nickname }}</text>
            <text class="desc ellipsis-2">{{ video.description }}</text>
            <text class="location" v-if="video.location">📍 {{ video.location }}</text>
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

interface VideoItem {
  id: string;
  url: string;
  cover: string;
  nickname: string;
  avatar: string;
  description: string;
  location: string;
  liked: boolean;
  followed: boolean;
  likeCount: number;
  commentCount: number;
}

const videoList = ref<VideoItem[]>([]);
const currentId = ref("");

function onSwiperChange(e: any) {
  const index = e.detail.current;
  if (videoList.value[index]) {
    currentId.value = videoList.value[index].id;
  }
}

async function handleLike(video: VideoItem) {
  video.liked = !video.liked;
  video.likeCount += video.liked ? 1 : -1;
  // TODO: 对接 POST /smallVideos/{id}/like | dislike
}

async function handleFocus(video: VideoItem) {
  video.followed = !video.followed;
  // TODO: 对接 POST /smallVideos/{id}/userFocus | userUnFocus
}

function showComments(video: VideoItem) {
  // TODO: 弹出半屏评论抽屉
  uni.showToast({ title: "评论功能开发中", icon: "none" });
}

function handleShare() {
  uni.showToast({ title: "分享功能开发中", icon: "none" });
}

async function loadVideos() {
  // TODO: 对接 GET /smallVideos
}

onShow(() => {
  if (videoList.value.length === 0) loadVideos();
});
</script>

<style lang="scss" scoped>
.video-page {
  height: 100vh;
  background: #000;
  position: relative;
}

.video-swiper {
  height: 100vh;
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

  .location {
    font-size: 22rpx;
    color: rgba(255, 255, 255, 0.6);
    margin-top: 8rpx;
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