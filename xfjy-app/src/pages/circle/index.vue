<template>
  <view class="circle-page">
    <!-- 顶部 Tab（role=tablist 语义 + 可键盘操作） -->
    <view class="tab-bar" role="tablist" aria-label="圈子频道">
      <a11y-button
        v-for="tab in tabs"
        :key="tab.key"
        class="tab-item"
        role="tab"
        :checked="activeTab === tab.key"
        :label="tab.label"
        @tap="switchTab(tab.key)"
      >
        <text>{{ tab.label }}</text>
      </a11y-button>
      <!-- 发布按钮（原为无标签的 + 符号） -->
      <a11y-button class="publish-btn btn-feedback" label="发布动态" @tap="goPublish">
        <text aria-hidden="true">+</text>
      </a11y-button>
    </view>

    <!-- 动态列表 -->
    <scroll-view
      scroll-y
      class="feed-list"
      refresher-enabled
      :refresher-triggered="refreshing"
      @refresherrefresh="onRefresh"
      @scrolltolower="loadMore"
    >
      <!-- 骨架屏（首屏加载中） -->
      <!-- 骨架屏（首屏加载中）：占位元素，对读屏隐藏 -->
      <view v-if="feedList.length === 0 && loading" class="skeleton-feed" aria-hidden="true">
        <view v-for="i in SKELETON_CARD_COUNT" :key="i" class="skeleton-card">
          <view class="skeleton-header">
            <view class="skeleton skel-avatar" />
            <view class="skeleton skel-name" />
          </view>
          <view class="skeleton skel-text" />
          <view class="skeleton skel-text skel-short" />
          <view class="skeleton skel-grid-row">
            <view v-for="j in GRID_COLUMN_COUNT" :key="j" class="skeleton skel-grid" />
          </view>
        </view>
      </view>
      <!-- 空状态 -->
      <view v-if="feedList.length === 0 && !loading" class="empty-state">
        <view class="empty-icon">
          <uni-icons type="camera" size="48" color="#cccccc"></uni-icons>
        </view>
        <text class="empty-title">暂无动态</text>
        <text class="empty-desc">快来发布第一条，分享你的生活</text>
      </view>
      <view v-for="item in feedList" :key="item.id" class="post-card">
        <!-- 头部 -->
        <view class="post-header">
          <image class="avatar" :src="item.avatar" mode="aspectFill" lazy-load :aria-label="`${item.nickname} 的头像`" />
          <view class="user-info">
            <text class="nickname">{{ item.nickname }}</text>
            <text class="meta">{{ item.createDate }} · {{ item.location || '未知位置' }}</text>
          </view>
        </view>
        <!-- 文字内容 -->
        <view v-if="item.textContent" class="post-content ellipsis-2">{{ item.textContent }}</view>
        <!-- 话题标签（点击进入话题筛选） -->
        <view v-if="item.tags && item.tags.length" class="tag-row">
          <view v-for="(tag, i) in item.tags" :key="i" class="tag-chip" @tap="goTag(tag)"># {{ tag }}</view>
        </view>
        <!-- 九宫格图片 -->
        <view v-if="item.imageContent && item.imageContent.length" class="image-grid" :class="`grid-${Math.min(item.imageContent.length, GRID_COLUMN_COUNT)}`">
          <image
            v-for="(img, i) in item.imageContent.slice(0, MAX_GRID_IMAGE_COUNT)"
            :key="i"
            class="grid-image"
            :src="img"
            mode="aspectFill"
            lazy-load
            :aria-label="`动态图片 ${i + 1}，点击查看大图`"
            @click="previewImage(item.imageContent, i)"
          />
        </view>
        <!-- 互动栏 -->
        <view class="action-row">
          <a11y-button class="action btn-feedback" :label="item.hasLiked ? `取消点赞，当前 ${item.likeCount || 0} 赞` : `点赞，当前 ${item.likeCount || 0} 赞`" @tap="handleLike(item)">
            <uni-icons class="action-icon" :class="{ liked: item.hasLiked, 'heart-beat': item.hasLiked }" :type="item.hasLiked ? 'heart-filled' : 'heart'" :color="item.hasLiked ? '#c2185b' : '#767676'" size="18"></uni-icons>
            <text class="count">{{ item.likeCount || '' }}</text>
          </a11y-button>
          <a11y-button class="action btn-feedback" :label="`查看评论，共 ${item.commentCount || 0} 条`" @tap="goDetail(item)">
            <uni-icons class="action-icon" type="chatbubble" color="#767676" size="18"></uni-icons>
            <text class="count">{{ item.commentCount || '' }}</text>
          </a11y-button>
          <a11y-button class="action btn-feedback" :label="item.hasLoved ? '取消心动' : '心动'" @tap="handleLove(item)">
            <uni-icons class="action-icon" :class="{ loved: item.hasLoved }" :type="item.hasLoved ? 'star-filled' : 'star'" :color="item.hasLoved ? '#c2185b' : '#767676'" size="18"></uni-icons>
          </a11y-button>
        </view>
      </view>
      <!-- 加载更多 -->
      <view v-if="loading" class="loading">加载中...</view>
    </scroll-view>
    <!-- 匹配成功弹窗（全局，由 match store 驱动） -->
    <MatchPopup />
  </view>
</template>

<script setup lang="ts">
import { ref, computed } from "vue";
import { onShow, onShareAppMessage } from "@dcloudio/uni-app";
import MatchPopup from "@/components/match-popup.vue";
import A11yButton from "@/components/a11y-button.vue";
import { useCircleStore } from "@/store/circle";
import type { Movement } from "@/api/circle";

const circleStore = useCircleStore();

/** 九宫格图片最大展示数量 */
const MAX_GRID_IMAGE_COUNT = 9;
/** 九宫格列数 */
const GRID_COLUMN_COUNT = 3;
/** 骨架屏占位卡片数量 */
const SKELETON_CARD_COUNT = 3;

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
/** 下拉刷新中状态（驱动 scroll-view refresher） */
const refreshing = ref(false);

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

/** 点击话题标签：跳转话题筛选页 */
function goTag(tag: string) {
  uni.navigateTo({ url: "/pages/circle/tag?tag=" + encodeURIComponent(tag) });
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
 * 下拉刷新：重新加载当前 Tab 首页动态
 */
async function onRefresh(): Promise<void> {
  refreshing.value = true;
  try {
    await loadFeed();
  } finally {
    refreshing.value = false;
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

/** 小程序端分享配置（H5 端静默忽略） */
onShareAppMessage(() => ({
  title: "心飞交友 · 遇见心动的人",
  path: "/pages/match/index",
}));
</script>

<style lang="scss" scoped>
.circle-page {
  height: calc(100vh - var(--window-bottom, 0px));
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
    /* 未选中态 #999（≈2.85:1）→ #767676 */
    color: #767676;
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
        background: #ff5e9c;
        border-radius: 3rpx;
      }
    }
  }

  .publish-btn {
    position: absolute;
    right: 32rpx;
    width: 64rpx;
    height: 64rpx;
    background: linear-gradient(135deg, #b13bff, #ff5e9c);
    color: #fff;
    font-size: 40rpx;
    display: flex;
    align-items: center;
    justify-content: center;
    border-radius: 50%;
    box-shadow: 0 8rpx 24rpx rgba(255, 94, 142, 0.4);
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
  box-shadow: 0 2rpx 12rpx rgba(0, 0, 0, 0.04);

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

      /* #ccc ≈1.6:1 → #767676 */
      .meta {
        font-size: 22rpx;
        color: #767676;
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

  /* 话题标签（发布页勾选，落库 Movement.tags） */
  .tag-row {
    display: flex;
    flex-wrap: wrap;
    gap: 12rpx;
    margin: 4rpx 0 16rpx;
  }

  .tag-chip {
    font-size: 22rpx;
    color: #c2185b;
    background: #ffeff5;
    padding: 6rpx 18rpx;
    border-radius: 24rpx;
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
      /* 触摸目标 ≥24px */
      min-height: 48rpx;

      /* #999 → #767676 */
      .action-icon {
        font-size: 36rpx;
        color: #767676;
        line-height: 1;

        &.liked {
          color: #c2185b;
        }

        &.loved {
          color: #c2185b;
        }
      }

      .count {
        font-size: 24rpx;
        color: #767676;
      }
    }
  }
}

.loading {
  text-align: center;
  padding: 24rpx;
  color: #767676;
  font-size: 24rpx;
}

/* ===== 骨架屏 ===== */
.skeleton-card {
  background: #fff;
  border-radius: 24rpx;
  padding: 24rpx;
  margin-bottom: 16rpx;

  .skeleton-header {
    display: flex;
    align-items: center;
    margin-bottom: 20rpx;

    .skel-avatar {
      width: 72rpx;
      height: 72rpx;
      border-radius: 50%;
      flex-shrink: 0;
    }

    .skel-name {
      width: 180rpx;
      height: 28rpx;
      margin-left: 16rpx;
    }
  }

  .skel-text {
    width: 100%;
    height: 26rpx;
    margin-bottom: 12rpx;

    &.skel-short {
      width: 60%;
    }
  }

  .skel-grid-row {
    display: flex;
    gap: 8rpx;
    height: 200rpx;
    margin-top: 8rpx;

    .skel-grid {
      flex: 1;
      height: 100%;
      border-radius: 8rpx;
    }
  }
}
</style>