<template>
  <view class="social-page">
    <!-- 断网/恢复全局提示条（fixed 定位自管理显隐，S1） -->
    <network-status-bar />
    <!-- 顶栏：标题 + 消息入口（custom 导航后直接贴状态栏，需自行避让）。
         消息为独立页 pages/chat/index（原内嵌消息视图已拆出），红点 = 各类通知未读之和 -->
    <view class="nav-bar">
      <text class="nav-title">
        圈子
      </text>
      <a11y-button
        class="msg-entry"
        :label="noticeBadge > 0 ? '消息，' + noticeBadge + ' 条未读' : '消息'"
        @tap="goMessages"
      >
        <view
          class="i-ph-chat-circle-dots"
          aria-hidden="true"
        />
        <view
          v-if="noticeBadge > 0"
          class="msg-badge"
          aria-hidden="true"
        >
          {{ noticeBadge > 99 ? '99+' : noticeBadge }}
        </view>
      </a11y-button>
    </view>

    <view
      class="tab-bar"
      role="tablist"
      aria-label="圈子频道"
    >
      <a11y-button
        v-for="tab in tabs"
        :key="tab.key"
        class="tab-item"
        :class="{ active: activeTab === tab.key }"
        role="tab"
        :checked="activeTab === tab.key"
        :label="tab.label"
        @tap="switchTab(tab.key)"
      >
        <text>{{ tab.label }}</text>
      </a11y-button>
    </view>

    <!-- S6：错误边界捕获动态列表渲染异常，@retry 重新拉取 feed -->
    <error-boundary @retry="loadFeed">
    <scroll-view
      scroll-y
      class="feed-list"
      refresher-enabled
      :refresher-triggered="refreshing"
      @refresherrefresh="onRefresh"
      @scrolltolower="loadMore"
    >
      <skeleton
        v-if="feedList.length === 0 && loading"
        variant="feed"
        :count="SKELETON_CARD_COUNT"
        :grid-cols="GRID_COLUMN_COUNT"
      />
      <!-- 空状态：确实没有动态 -->
      <empty-state
        v-if="feedList.length === 0 && !loading && !loadFailed"
        icon="camera"
        title="暂无动态"
        desc="快来发布第一条，分享你的生活"
      />
      <!-- 加载失败：与「真的没动态」严格区分，给明确原因 + 可重试入口。
           后端异常时旧版把故障误报成「暂无动态」，用户无从判断也无法恢复。 -->
      <empty-state
        v-if="feedList.length === 0 && !loading && loadFailed"
        role="alert"
        icon="info"
        title="暂时没能获取动态"
        desc="服务可能开小差了，检查网络后重试"
      >
        <a11y-button
          class="feed-retry"
          label="重新加载动态"
          @tap="loadFeed()"
        >
          重新加载
        </a11y-button>
      </empty-state>
      <view
        v-for="item in feedList"
        :key="item.id"
        class="post-card"
      >
        <view class="post-header">
          <smart-image
            class="avatar"
            :src="item.avatar"
            mode="aspectFill"
            lazy-load
            :aria-label="item.nickname + ' 的头像'"
          />
          <view class="user-info">
            <text class="nickname">
              {{ item.nickname }}
            </text>
            <text class="meta">
              {{ item.createDate }} · {{ item.location || '未知位置' }}
            </text>
          </view>
        </view>
        <view
          v-if="item.textContent"
          class="post-content ellipsis-2"
        >
          {{ item.textContent }}
        </view>
        <view
          v-if="item.tags && item.tags.length"
          class="tag-row"
        >
          <a11y-button
            v-for="(tag, i) in item.tags"
            :key="i"
            class="tag-chip"
            :label="`查看话题 ${tag}`"
            @tap="goTag(tag)"
          >
            <text># {{ tag }}</text>
          </a11y-button>
        </view>
        <!-- filter(Boolean)：后端可能返回含空字符串的 imageContent（如 [""]），
             空串渲染为占位块但点击预览会因 uni.previewImage 收到空 URL 报错 -->
        <view
          v-if="item.imageContent?.filter(Boolean).length"
          class="image-grid"
          :class="'grid-' + Math.min(item.imageContent.filter(Boolean).length, GRID_COLUMN_COUNT)"
        >
          <smart-image
            v-for="(img, i) in item.imageContent.filter(Boolean).slice(0, MAX_GRID_IMAGE_COUNT)"
            :key="i"
            class="grid-image"
            :src="img"
            :fallback="''"
            mode="aspectFill"
            lazy-load
            :aria-label="'动态图片 ' + (i + 1) + '，点击查看大图'"
            clickable
            @click="previewImage(item.imageContent.filter(Boolean), i)"
          />
        </view>
        <view class="action-row">
          <!-- 计数为 0 时给"抢首赞/抢首评"引导，而非空白让人以为功能缺失 -->
          <a11y-button
            class="action"
            :label="item.hasLiked ? '取消点赞，当前 ' + (item.likeCount || 0) + ' 赞' : '点赞，当前 ' + (item.likeCount || 0) + ' 赞'"
            @tap="handleLike(item)"
          >
            <view
              class="action-icon"
              :class="{ liked: item.hasLiked, 'heart-beat': item.hasLiked, 'i-ph-heart-fill': item.hasLiked, 'i-ph-heart': !item.hasLiked }"
              aria-hidden="true"
            />
            <text class="count">
              {{ item.likeCount > 0 ? item.likeCount : '抢首赞' }}
            </text>
          </a11y-button>
          <a11y-button
            class="action"
            :label="'查看评论，共 ' + (item.commentCount || 0) + ' 条'"
            @tap="goDetail(item)"
          >
            <view
              class="action-icon i-ph-chat-circle-dots"
              aria-hidden="true"
            />
            <text class="count">
              {{ item.commentCount > 0 ? item.commentCount : '抢首评' }}
            </text>
          </a11y-button>
          <a11y-button
            class="action"
            :label="item.hasLoved ? '取消心动' : '心动'"
            @tap="handleLove(item)"
          >
            <view
              class="action-icon"
              :class="{ loved: item.hasLoved, 'i-ph-star-fill': item.hasLoved, 'i-ph-star': !item.hasLoved }"
              aria-hidden="true"
            />
          </a11y-button>
        </view>
      </view>
      <view
        v-if="loading"
        class="loading"
      >
        加载中…
      </view>
      <!-- P2-4：全部加载完给明确终点，避免用户反复上拉以为卡了 -->
      <view
        v-else-if="!hasMore && feedList.length > 0"
        class="loading"
      >
        没有更多了
      </view>
    </scroll-view>
    </error-boundary>

    <MatchPopup />
  </view>
</template>

<script setup lang="ts">
import { ref, computed } from "vue";
import EmptyState from "@/components/empty-state.vue";
import NetworkStatusBar from "@/components/network-status-bar.vue";
import Skeleton from "@/components/skeleton.vue";
import { onShow, onShareAppMessage } from "@dcloudio/uni-app";
import MatchPopup from "@/components/match-popup.vue";
import A11yButton from "@/components/a11y-button.vue";
import SmartImage from "@/components/smart-image.vue";
import ErrorBoundary from "@/components/error-boundary.vue";
import { useCircleStore, CIRCLE_NEED_REFRESH_KEY } from "@/store/circle";
import type { Movement } from "@/api/circle";
import { useChatStore } from "@/store/chat";
import { onSystemNoticeReceived } from "@/utils/wukongim";

const circleStore = useCircleStore();
const chatStore = useChatStore();

/* ---------------- 圈子 ---------------- */
const MAX_GRID_IMAGE_COUNT = 9;
const GRID_COLUMN_COUNT = 3;
const SKELETON_CARD_COUNT = 3;

const tabs = [
  { key: "friend", label: "好友" },
  { key: "recommend", label: "推荐" },
] as const;

const activeTab = computed(() => circleStore.activeTab);
const feedList = computed<Movement[]>(() =>
  circleStore.activeTab === "friend"
    ? circleStore.friendMovements
    : circleStore.recommendMovements
);
const loading = computed(() => circleStore.loading);
const loadFailed = computed(() => circleStore.loadFailed);
/** 当前 Tab 是否还有更多（P2-4：滑到底显示「没有更多了」） */
const hasMore = computed(() =>
  circleStore.activeTab === "friend" ? circleStore.friendHasMore : circleStore.recommendHasMore
);
const refreshing = ref(false);

/** 顶栏消息入口红点：各类通知未读之和（会话未读在消息页内展示，此处不重复拉 IM） */
const noticeBadge = computed(() =>
  Object.values(chatStore.noticeUnreads).reduce((sum, n) => sum + n, 0)
);

function switchTab(key: "friend" | "recommend") {
  if (circleStore.activeTab === key) return;
  circleStore.switchTab(key);
  const targetList = key === "friend" ? circleStore.friendMovements : circleStore.recommendMovements;
  if (targetList.length === 0) {
    loadFeed();
  }
}

function goMessages(): void {
  uni.navigateTo({ url: "/pages/chat/index" });
}

function goDetail(item: Movement) {
  uni.navigateTo({ url: "/pages/circle/detail?id=" + item.id });
}

function goTag(tag: string) {
  uni.navigateTo({ url: "/pages/circle/tag?tag=" + encodeURIComponent(tag) });
}

function previewImage(images: string[], index: number) {
  uni.previewImage({ urls: images, current: images[index] });
}

async function handleLike(item: Movement) {
  try {
    await circleStore.toggleLike(item.id);
  } catch {
    // 请求失败由 request 层统一 toast
  }
}

async function handleLove(item: Movement) {
  try {
    await circleStore.toggleLove(item.id);
  } catch {
    // 请求失败由 request 层统一 toast
  }
}

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

async function onRefresh(): Promise<void> {
  refreshing.value = true;
  try {
    await loadFeed();
  } finally {
    refreshing.value = false;
  }
}

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

/** IM 系统通知（点赞/评论旁路提醒）：实时 toast + 刷新红点。
 *  tabBar 页面存活期间持续监听，无需注销；消息页不重复监听避免双 toast。 */
onSystemNoticeReceived((notice) => {
  uni.showToast({ title: notice.content, icon: "none" });
  chatStore.loadNoticeCounts();
});

/* ---------------- 生命周期 ---------------- */
onShow(() => {
  // 发布成功返回时强制刷新圈子列表
  if (uni.getStorageSync(CIRCLE_NEED_REFRESH_KEY) === "1") {
    uni.removeStorageSync(CIRCLE_NEED_REFRESH_KEY);
    loadFeed();
    return;
  }
  // 数据为空时才加载，避免每次 onShow 都重新请求导致页面闪动
  if (feedList.value.length === 0) {
    loadFeed();
  }
  // 顶栏红点每次 onShow 都重算（4 个 pagesize=1 的 count 请求，开销可忽略）：
  // 只在首次刷新会让角标长期停留在旧值，新通知时亮时不亮
  chatStore.loadNoticeCounts();
});

onShareAppMessage(() => ({
  title: "心飞交友 · 遇见心动的人",
  path: "/pages/match/index",
}));
</script>

<style lang="scss" scoped>
.social-page {
  height: calc(100vh - var(--window-bottom, 0px));
  display: flex;
  flex-direction: column;
  background: var(--bg-sub);
}

/* 顶栏：标题 + 消息入口（custom 导航直接贴状态栏，需自行避让） */
.nav-bar {
  display: flex;
  align-items: center;
  height: calc(var(--category-bar-h) + env(safe-area-inset-top, 0px));
  padding: 0 24rpx;
  padding-top: env(safe-area-inset-top, 0px);
  background: var(--bg-card);
  border-bottom: 1rpx solid var(--border-light);

  .nav-title {
    flex: 1;
    text-align: center;
    font-size: 34rpx;
    font-weight: 700;
    color: var(--text-primary);
  }

  .msg-entry {
    position: relative;
    display: flex;
    align-items: center;
    justify-content: center;
    width: 88rpx;
    height: 88rpx;
    margin-right: -24rpx;

    .i-ph-chat-circle-dots {
      font-size: 40rpx;
      color: var(--text-primary);
    }

    .msg-badge {
      position: absolute;
      top: 8rpx;
      right: 0;
      min-width: 30rpx;
      height: 30rpx;
      line-height: 30rpx;
      text-align: center;
      font-size: 20rpx;
      color: var(--text-inverse);
      background: var(--gradient-brand);
      border-radius: var(--radius-pill);
      padding: 0 8rpx;
      border: 2rpx solid var(--text-inverse);
    }
  }
}

/* 圈子子标签 */
.tab-bar {
  display: flex;
  align-items: stretch;
  background: var(--bg-card);
  padding: 0 32rpx;
  height: var(--category-bar-h);
  position: relative;

  .tab-item {
    display: flex;
    align-items: center;
    padding: 0 32rpx;
    font-size: 30rpx;
    color: var(--text-hint);
    position: relative;

    &.active {
      color: var(--color-primary);
      font-weight: bold;

      &::after {
        content: "";
        position: absolute;
        bottom: 0;
        left: 50%;
        transform: translateX(-50%);
        width: 48rpx;
        height: 8rpx;
        background: var(--color-primary);
        border-radius: var(--radius-pill);
      }
    }
  }
}

.feed-list {
  flex: 1;
  min-height: 0;
  /* border-box：width:100% 时左右 padding 计入宽度，否则容器被撑破导致横向滚动
     （历史 bug：content-box 下 390 视口实测宽 407px，全设备横向溢出） */
  box-sizing: border-box;
  padding: 16rpx;
}

.post-card {
  background: var(--bg-card);
  border-radius: var(--radius-lg);
  padding: 20rpx;
  margin-bottom: 12rpx;
  box-shadow: var(--shadow-card);

  .post-header {
    display: flex;
    align-items: center;

    .avatar {
      width: 64rpx;
      height: 64rpx;
      border-radius: 50%;
    }

    .user-info {
      margin-left: 12rpx;

      .nickname {
        font-size: 26rpx;
        font-weight: 500;
        display: block;
      }

      .meta {
        font-size: 20rpx;
        color: var(--text-hint);
        margin-top: 2rpx;
        display: block;
      }
    }
  }

  .post-content {
    font-size: 26rpx;
    line-height: 1.5;
    margin: 12rpx 0;
    color: var(--text-primary);
  }

  .tag-row {
    display: flex;
    flex-wrap: wrap;
    gap: 8rpx;
    margin: 4rpx 0 12rpx;
  }

  .tag-chip {
    font-size: 22rpx;
    color: var(--color-primary-text);
    background: var(--bg-icon-soft);
    /* 与详情页统一：inline-block + 行高控制高度，不再用 88rpx 大热区（列表卡片非可点标签） */
    display: inline-block;
    padding: 8rpx 18rpx;
    line-height: 1.4;
    border-radius: var(--radius-lg);
  }

  .image-grid {
    display: flex;
    flex-wrap: wrap;
    gap: 6rpx;
    margin: 12rpx 0;

    .grid-image {
      /* 与详情页九宫格一致：按容器宽度等分，避免窄屏（320px）三格溢出 */
      flex: 1 1 calc(33.333% - 6rpx);
      max-width: calc(33.333% - 6rpx);
      aspect-ratio: 1 / 1;
      width: auto;
      height: auto;
      border-radius: var(--radius-sm);
    }
  }

  .action-row {
    display: flex;
    gap: 32rpx;
    padding-top: 12rpx;
    border-top: 1rpx solid var(--border-faint);

    .action {
      display: flex;
      align-items: center;
      gap: 6rpx;
      /* 热区 ≥88rpx（44px）：原 48rpx 仅 24px 高，用户难以点中点赞/评论/心动 */
      min-height: 88rpx;
      /* 「心动」仅图标无文字，宽度 22px 不足 44px，补足可点击宽度 */
      min-width: 88rpx;

      .action-icon {
        font-size: 32rpx;
        color: var(--text-hint);
        line-height: 1;

        &.liked { color: var(--color-primary-text); }
        &.loved { color: var(--color-primary-text); }
      }

      .count {
        font-size: 22rpx;
        color: var(--text-hint);
      }
    }
  }
}

.loading {
  text-align: center;
  padding: 24rpx;
  color: var(--text-hint);
  font-size: 24rpx;
}

/* 加载失败态的重试按钮（置于 empty-state 的 slot 内） */
.feed-retry {
  margin-top: 32rpx;
  padding: 16rpx 56rpx;
  border-radius: var(--radius-pill);
  font-size: 28rpx;
  font-weight: 600;
  color: var(--text-inverse);
  background: var(--gradient-brand);
}
</style>
