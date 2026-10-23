<template>
  <view class="circle-page">
    <!-- 顶部 Tab -->
    <view class="tab-bar">
      <view
        v-for="tab in tabs"
        :key="tab.key"
        class="tab-item"
        :class="{ active: activeTab === tab.key }"
        @click="switchTab(tab.key)"
      >
        <text>{{ tab.label }}</text>
      </view>
      <!-- 发布按钮 -->
      <view class="publish-btn" @click="goPublish">
        <text>+</text>
      </view>
    </view>

    <!-- 动态列表 -->
    <scroll-view scroll-y class="feed-list" @scrolltolower="loadMore">
      <view v-if="feedList.length === 0" class="empty">
        <text class="empty-text">暂无动态，快来发布第一条吧</text>
      </view>
      <view v-for="item in feedList" :key="item.id" class="post-card">
        <!-- 头部 -->
        <view class="post-header">
          <image class="avatar" :src="item.avatar" mode="aspectFill" />
          <view class="user-info">
            <text class="nickname">{{ item.nickname }}</text>
            <text class="meta">{{ item.createDate }} · {{ item.location || '未知位置' }}</text>
          </view>
        </view>
        <!-- 文字内容 -->
        <view v-if="item.textContent" class="post-content ellipsis-2">{{ item.textContent }}</view>
        <!-- 九宫格图片 -->
        <view v-if="item.medias && item.medias.length" class="image-grid" :class="`grid-${Math.min(item.medias.length, 3)}`">
          <image
            v-for="(img, i) in item.medias.slice(0, 9)"
            :key="i"
            class="grid-image"
            :src="img"
            mode="aspectFill"
            @click="previewImage(item.medias, i)"
          />
        </view>
        <!-- 互动栏 -->
        <view class="action-row">
          <view class="action" @click="handleLike(item)">
            <text :class="{ liked: item.hasLiked }">{{ item.hasLiked ? '❤️' : '🤍' }}</text>
            <text class="count">{{ item.loveLikeCount || '' }}</text>
          </view>
          <view class="action" @click="goDetail(item)">
            <text>💬</text>
            <text class="count">{{ item.commentCount || '' }}</text>
          </view>
          <view class="action" @click="handleLove(item)">
            <text :class="{ loved: item.hasLoved }">{{ item.hasLoved ? '🌟' : '✨' }}</text>
          </view>
        </view>
      </view>
      <!-- 加载更多 -->
      <view v-if="loading" class="loading">加载中...</view>
    </scroll-view>
  </view>
</template>

<script setup lang="ts">
import { computed } from "vue";
import { onShow } from "@dcloudio/uni-app";
import { useCircleStore } from "@/store/circle";
import type { Movement } from "@/api/circle";

const circleStore = useCircleStore();

const tabs = [
  { key: "friend", label: "好友" },
  { key: "recommend", label: "推荐" },
] as const;

/** 当前 Tab */
const activeTab = computed(() => circleStore.activeTab);
/** 当前 Tab 的动态列表 */
const feedList = computed<Movement[]>(() =>
  circleStore.activeTab === "friend"
    ? circleStore.friendMovements
    : circleStore.recommendMovements
);
/** 加载状态 */
const loading = computed(() => circleStore.loading);

/**
 * 切换 Tab（目标 Tab 无数据时才加载）
 */
function switchTab(key: "friend" | "recommend") {
  if (circleStore.activeTab === key) return;
  circleStore.switchTab(key);
  const targetList = key === "friend" ? circleStore.friendMovements : circleStore.recommendMovements;
  if (targetList.length === 0) {
    loadFeed();
  }
}

function goPublish() {
  uni.navigateTo({ url: "/pages/circle/publish" });
}

function goDetail(item: Movement) {
  uni.navigateTo({ url: `/pages/circle/detail?id=${item.id}` });
}

function previewImage(images: string[], index: number) {
  uni.previewImage({ urls: images, current: images[index] });
}

/**
 * 点赞/取消点赞（乐观更新由 store 处理）
 */
async function handleLike(item: Movement) {
  try {
    await circleStore.toggleLike(item.id);
  } catch {
    // 请求失败由 request 层统一 toast
  }
}

/**
 * 喜欢/取消喜欢（乐观更新由 store 处理）
 */
async function handleLove(item: Movement) {
  try {
    await circleStore.toggleLove(item.id);
  } catch {
    // 请求失败由 request 层统一 toast
  }
}

/**
 * 加载当前 Tab 首页动态
 */
async function loadFeed() {
  try {
    if (circleStore.activeTab === "friend") {
      await circleStore.loadFriendMovements();
    } else {
      await circleStore.loadRecommendMovements();
    }
  } catch {
    // 请求失败由 request 层统一 toast
  }
}

/**
 * 上拉加载更多
 */
async function loadMore() {
  try {
    if (circleStore.activeTab === "friend") {
      await circleStore.loadMoreFriendMovements();
    } else {
      await circleStore.loadMoreRecommendMovements();
    }
  } catch {
    // 请求失败由 request 层统一 toast
  }
}

onShow(() => {
  if (feedList.value.length === 0) loadFeed();
});
</script>

<style lang="scss" scoped>
.circle-page {
  height: 100vh;
  display: flex;
  flex-direction: column;
  background: #f5f5f5;
}

.tab-bar {
  display: flex;
  align-items: center;
  background: #fff;
  padding: 0 32rpx;
  height: 88rpx;
  position: relative;

  .tab-item {
    padding: 0 32rpx;
    font-size: 30rpx;
    color: #999;
    position: relative;

    &.active {
      color: #333;
      font-weight: bold;

      &::after {
        content: '';
        position: absolute;
        bottom: -16rpx;
        left: 50%;
        transform: translateX(-50%);
        width: 48rpx;
        height: 6rpx;
        background: #ff5e8e;
        border-radius: 3rpx;
      }
    }
  }

  .publish-btn {
    position: absolute;
    right: 32rpx;
    width: 64rpx;
    height: 64rpx;
    background: linear-gradient(135deg, #ff5e8e, #ff8fab);
    color: #fff;
    font-size: 40rpx;
    display: flex;
    align-items: center;
    justify-content: center;
    border-radius: 50%;
  }
}

.feed-list {
  flex: 1;
  padding: 16rpx;
}

.post-card {
  background: #fff;
  border-radius: 24rpx;
  padding: 24rpx;
  margin-bottom: 16rpx;

  .post-header {
    display: flex;
    align-items: center;

    .avatar {
      width: 72rpx;
      height: 72rpx;
      border-radius: 50%;
    }

    .user-info {
      margin-left: 16rpx;

      .nickname {
        font-size: 28rpx;
        font-weight: 500;
        display: block;
      }

      .meta {
        font-size: 22rpx;
        color: #ccc;
        margin-top: 4rpx;
        display: block;
      }
    }
  }

  .post-content {
    font-size: 28rpx;
    line-height: 1.6;
    margin: 16rpx 0;
    color: #333;
  }

  .image-grid {
    display: flex;
    flex-wrap: wrap;
    gap: 8rpx;
    margin: 16rpx 0;

    .grid-image {
      width: 200rpx;
      height: 200rpx;
      border-radius: 8rpx;
    }
  }

  .action-row {
    display: flex;
    gap: 48rpx;
    padding-top: 16rpx;
    border-top: 1rpx solid #f5f5f5;

    .action {
      display: flex;
      align-items: center;
      gap: 8rpx;
      font-size: 28rpx;

      .count {
        font-size: 24rpx;
        color: #999;
      }
    }
  }
}

.empty {
  display: flex;
  justify-content: center;
  padding: 120rpx 0;

  .empty-text {
    font-size: 28rpx;
    color: #999;
  }
}

.loading {
  text-align: center;
  padding: 24rpx;
  color: #999;
  font-size: 24rpx;
}
</style>