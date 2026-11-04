<template>
  <view class="social-page">
    <!-- 顶级分区：圈子 / 消息 -->
    <view class="seg-bar" role="tablist" aria-label="社区分区">
      <a11y-button
        class="seg"
        :class="{ active: topTab === 'circle' }"
        role="tab"
        :checked="topTab === 'circle'"
        label="圈子"
        @tap="switchTopTab('circle')"
      >圈子</a11y-button>
      <a11y-button
        class="seg"
        :class="{ active: topTab === 'message' }"
        role="tab"
        :checked="topTab === 'message'"
        label="消息"
        @tap="switchTopTab('message')"
      >
        消息
        <view v-if="totalUnread > 0" class="seg-badge" aria-hidden="true">{{ totalUnread > 99 ? '99+' : totalUnread }}</view>
      </a11y-button>
    </view>

    <!-- 圈子视图 -->
    <view v-show="topTab === 'circle'" class="tab-view">
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
      </view>

      <scroll-view
        scroll-y
        class="feed-list"
        refresher-enabled
        :refresher-triggered="refreshing"
        @refresherrefresh="onRefresh"
        @scrolltolower="loadMore"
      >
        <skeleton v-if="feedList.length === 0 && loading" variant="feed" :count="SKELETON_CARD_COUNT" :grid-cols="GRID_COLUMN_COUNT" />
        <empty-state v-if="feedList.length === 0 && !loading" icon="camera" title="暂无动态" desc="快来发布第一条，分享你的生活" />
        <view v-for="item in feedList" :key="item.id" class="post-card">
          <view class="post-header">
            <image class="avatar" :src="item.avatar" mode="aspectFill" lazy-load :aria-label="item.nickname + ' 的头像'" />
            <view class="user-info">
              <text class="nickname">{{ item.nickname }}</text>
              <text class="meta">{{ item.createDate }} · {{ item.location || '未知位置' }}</text>
            </view>
          </view>
          <view v-if="item.textContent" class="post-content ellipsis-2">{{ item.textContent }}</view>
          <view v-if="item.tags && item.tags.length" class="tag-row">
            <view v-for="(tag, i) in item.tags" :key="i" class="tag-chip" @tap="goTag(tag)"># {{ tag }}</view>
          </view>
          <view v-if="item.imageContent && item.imageContent.length" class="image-grid" :class="'grid-' + Math.min(item.imageContent.length, GRID_COLUMN_COUNT)">
            <image
              v-for="(img, i) in item.imageContent.slice(0, MAX_GRID_IMAGE_COUNT)"
              :key="i"
              class="grid-image"
              :src="img"
              mode="aspectFill"
              lazy-load
              :aria-label="'动态图片 ' + (i + 1) + '，点击查看大图'"
              @click="previewImage(item.imageContent, i)"
            />
          </view>
          <view class="action-row">
            <!-- 计数为 0 时给"抢首赞/抢首评"引导，而非空白让人以为功能缺失 -->
            <a11y-button class="action btn-feedback" :label="item.hasLiked ? '取消点赞，当前 ' + (item.likeCount || 0) + ' 赞' : '点赞，当前 ' + (item.likeCount || 0) + ' 赞'" @tap="handleLike(item)">
              <view class="action-icon" :class="{ liked: item.hasLiked, 'heart-beat': item.hasLiked, 'i-ph-heart-fill': item.hasLiked, 'i-ph-heart': !item.hasLiked }" aria-hidden="true" />
              <text class="count">{{ item.likeCount > 0 ? item.likeCount : '抢首赞' }}</text>
            </a11y-button>
            <a11y-button class="action btn-feedback" :label="'查看评论，共 ' + (item.commentCount || 0) + ' 条'" @tap="goDetail(item)">
              <view class="action-icon i-ph-chat-circle-dots" aria-hidden="true" />
              <text class="count">{{ item.commentCount > 0 ? item.commentCount : '抢首评' }}</text>
            </a11y-button>
            <a11y-button class="action btn-feedback" :label="item.hasLoved ? '取消心动' : '心动'" @tap="handleLove(item)">
              <view class="action-icon" :class="{ loved: item.hasLoved, 'i-ph-star-fill': item.hasLoved, 'i-ph-star': !item.hasLoved }" aria-hidden="true" />
            </a11y-button>
          </view>
        </view>
        <view v-if="loading" class="loading">加载中...</view>
      </scroll-view>
    </view>

    <!-- 消息视图（原 chat/index 内容） -->
    <view v-show="topTab === 'message'" class="tab-view">
      <view class="notice-bar">
        <a11y-button class="notice-item btn-feedback" v-for="item in noticeItems" :key="item.type" :label="item.unread > 0 ? item.label + '，' + item.unread + ' 条未读' : item.label" @tap="goNotifications(item.type)">
          <view class="notice-icon-wrap">
            <view class="notice-icon-bg" :style="{ background: item.color }">
              <view class="notice-icon" :class="item.iconClass" aria-hidden="true" />
            </view>
            <view v-if="item.unread > 0" class="badge pulse" aria-hidden="true">{{ item.unread > 99 ? '99+' : item.unread }}</view>
          </view>
          <text class="notice-label">{{ item.label }}</text>
        </a11y-button>
      </view>

      <scroll-view scroll-y class="msg-list">
        <empty-state v-if="chatList.length === 0" title="还没有聊天" desc="去匹配页滑动，遇见新朋友吧" />
        <a11y-button
          v-for="item in chatList"
          :key="item.userId"
          class="chat-item btn-feedback"
          :label="chatItemLabel(item)"
          @tap="goChatDetail(item)"
        >
          <view class="avatar-wrap">
            <image class="avatar" :src="item.avatar" mode="aspectFill" lazy-load :aria-label="item.nickname + ' 的头像'" />
            <view v-if="item.online" class="online-dot" aria-hidden="true"></view>
          </view>
          <view class="chat-info">
            <view class="top-row">
              <text class="nickname">{{ item.nickname }}</text>
            </view>
            <view class="bottom-row">
              <text class="last-msg">{{ item.lastMessage || item.city || '暂无消息' }}</text>
              <view v-if="item.unreadCount > 0" class="unread-badge" aria-hidden="true">{{ item.unreadCount > 99 ? '99+' : item.unreadCount }}</view>
            </view>
          </view>
        </a11y-button>
      </scroll-view>
    </view>

    <MatchPopup />
  </view>
</template>

<script setup lang="ts">
import { ref, computed } from "vue";
import EmptyState from "@/components/empty-state.vue";
import Skeleton from "@/components/skeleton.vue";
import { onShow, onShareAppMessage } from "@dcloudio/uni-app";
import MatchPopup from "@/components/match-popup.vue";
import A11yButton from "@/components/a11y-button.vue";
import { useCircleStore, CIRCLE_NEED_REFRESH_KEY } from "@/store/circle";
import type { Movement } from "@/api/circle";
import { getContacts, type Contact } from "@/api/chat";
import { useChatStore, type NoticeType } from "@/store/chat";
import { ensureSelfIM, getConversations } from "@/utils/wukongim";

const circleStore = useCircleStore();
const chatStore = useChatStore();

/** 顶级分区：圈子 / 消息 */
const topTab = ref<"circle" | "message">("circle");

/* ---------------- 圈子（原有逻辑） ---------------- */
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
const refreshing = ref(false);

function switchTab(key: "friend" | "recommend") {
  if (circleStore.activeTab === key) return;
  circleStore.switchTab(key);
  const targetList = key === "friend" ? circleStore.friendMovements : circleStore.recommendMovements;
  if (targetList.length === 0) {
    loadFeed();
  }
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

/* ---------------- 消息（原 chat/index 逻辑） ---------------- */
/** 原生 tabBar 合并后「圈子」页索引（见 pages.json） */
const TAB_BAR_INDEX = 1;
const BADGE_MAX_COUNT = 99;

interface ChatListItem extends Contact {
  lastMessage: string;
  unreadCount: number;
}

const NOTICE_META: Record<NoticeType, { iconClass: string; label: string; color: string }> = {
  like: { iconClass: "i-ph-heart-fill", label: "喜欢", color: "var(--color-primary)" },
  comment: { iconClass: "i-ph-chat-circle-dots", label: "评论", color: "var(--info-blue)" },
  love: { iconClass: "i-ph-star-fill", label: "心动", color: "var(--color-primary)" },
  announcement: { iconClass: "i-ph-bell", label: "公告", color: "var(--success)" },
};

const chatList = ref<ChatListItem[]>([]);

const noticeItems = computed(() =>
  (Object.keys(NOTICE_META) as NoticeType[]).map((type) => ({
    type,
    iconClass: NOTICE_META[type].iconClass,
    label: NOTICE_META[type].label,
    color: NOTICE_META[type].color,
    unread: chatStore.noticeUnreads[type],
  }))
);

const totalUnread = computed(() =>
  chatList.value.reduce((sum, item) => sum + (item.unreadCount || 0), 0)
);


function updateTabBarBadge(): void {
  const total = totalUnread.value;
  if (total > 0) {
    uni.setTabBarBadge({ index: TAB_BAR_INDEX, text: total > BADGE_MAX_COUNT ? "99+" : String(total) });
  } else {
    uni.removeTabBarBadge({ index: TAB_BAR_INDEX });
  }
}

async function loadMessages(): Promise<void> {
try {
// 1. 确保 IM 已连接，拉取真实会话列表（含最后消息/未读数/时间）
await ensureSelfIM();
const conversations = await getConversations();
// 2. 拉取联系人资料（昵称/头像），用于合并会话数据
const contactsRes = await getContacts({ page: 1, pagesize: 50 });
const contactMap = new Map<string, Contact>(
(contactsRes.items || []).map((c) => [String(c.userId), c])
);
// 3. 合并：以会话为主，资料补充；无资料时用会话 ID 兜底
chatList.value = conversations.map((s) => {
const contact = contactMap.get(s.conversationId);
return {
userId: s.conversationId,
nickname: contact?.nickname || s.conversationId,
avatar: contact?.avatar || "",
gender: contact?.gender || "",
age: contact?.age || 0,
city: contact?.city || "",
online: false,
lastMessage: s.lastMessage,
unreadCount: s.unreadCount,
};
});
updateTabBarBadge();
} catch {
// 请求失败由 request 层统一 toast
}
}

function goNotifications(type: string): void {
  uni.navigateTo({ url: "/pages/chat/notifications?type=" + type });
}

/** 顶级分区切换：切到消息分区时立即拉取联系人/会话与通知未读（onShow 只覆盖首次展示） */
function switchTopTab(tab: "circle" | "message"): void {
  if (topTab.value === tab) return;
  topTab.value = tab;
  if (tab === "message") {
    loadMessages();
    chatStore.loadNoticeCounts();
  }
}

function goChatDetail(contact: ChatListItem): void {
  uni.navigateTo({
    url: "/pages/chat/chat?userId=" + contact.userId + "&nickname=" + encodeURIComponent(contact.nickname),
  });
}

function chatItemLabel(contact: ChatListItem): string {
  const parts = [contact.nickname];
  if (contact.age) parts.push(contact.age + "岁");
  parts.push(contact.lastMessage || contact.city || "暂无消息");
  if (contact.unreadCount > 0) {
    parts.push((contact.unreadCount > BADGE_MAX_COUNT ? "99+" : contact.unreadCount) + " 条未读");
  }
  if (contact.online) parts.push("在线");
  return parts.join("，");
}

/* ---------------- 生命周期 ---------------- */
onShow(() => {
  if (topTab.value === "circle") {
    // 发布成功返回时强制刷新；否则仅在列表为空时加载
    const needRefresh = uni.getStorageSync(CIRCLE_NEED_REFRESH_KEY) === "1";
    if (needRefresh) {
      uni.removeStorageSync(CIRCLE_NEED_REFRESH_KEY);
      loadFeed();
    } else if (feedList.value.length === 0) {
      loadFeed();
    }
  } else {
    loadMessages();
    chatStore.loadNoticeCounts();
  }
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

/* 圈子/消息视图容器：v-show 切换时保持状态，避免重建 */
.tab-view {
flex: 1;
min-height: 0;
display: flex;
flex-direction: column;
}

/* 顶级分区：圈子 / 消息（custom 导航后直接贴状态栏，需自行避让） */
.seg-bar {
  display: flex;
  align-items: stretch;
  justify-content: center;
  gap: 56rpx;
  height: calc(var(--category-bar-h) + env(safe-area-inset-top, 0px));
  padding-top: env(safe-area-inset-top, 0px);
  background: var(--bg-card);
  position: relative;
  border-bottom: 1rpx solid var(--border-light);

  .seg {
    position: relative;
    align-self: stretch;
    display: flex;
    align-items: center;
    font-size: 32rpx;
    color: var(--text-hint);
    padding: 6rpx 4rpx;

    &.active {
      color: var(--text-primary);
      font-weight: 700;

      &::after {
        content: "";
        position: absolute;
        left: 50%;
        bottom: 0;
        transform: translateX(-50%);
        width: 48rpx;
        height: 8rpx;
        background: var(--color-primary);
        border-radius: var(--radius-pill);
      }
    }
  }

  .seg-badge {
    position: absolute;
    top: -4rpx;
    right: -28rpx;
    min-width: 30rpx;
    height: 30rpx;
    line-height: 30rpx;
    text-align: center;
    font-size: 20rpx;
    color: var(--text-inverse);
    background: var(--gradient-brand);
    border-radius: 15rpx;
    padding: 0 8rpx;
    border: 2rpx solid var(--text-inverse);
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
      color: var(--text-primary);
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

      .meta {
        font-size: 22rpx;
        color: var(--text-hint);
        margin-top: 4rpx;
        display: block;
      }
    }
  }

  .post-content {
    font-size: 28rpx;
    line-height: 1.6;
    margin: 16rpx 0;
    color: var(--text-primary);
  }

  .tag-row {
    display: flex;
    flex-wrap: wrap;
    gap: 12rpx;
    margin: 4rpx 0 16rpx;
  }

  .tag-chip {
    font-size: 22rpx;
    color: var(--color-primary-text);
    background: var(--bg-icon-soft);
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
    border-top: 1rpx solid var(--border-faint);

    .action {
      display: flex;
      align-items: center;
      gap: 8rpx;
      min-height: 48rpx;

      .action-icon {
        font-size: 36rpx;
        color: var(--text-hint);
        line-height: 1;

        &.liked { color: var(--color-primary-text); }
        &.loved { color: var(--color-primary-text); }
      }

      .count {
        font-size: 24rpx;
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

/* 消息：通知入口 */
.notice-bar {
  display: flex;
  justify-content: space-around;
  padding: 24rpx 0;
  background: var(--bg-card);
  margin-bottom: 16rpx;

  .notice-item {
    display: flex;
    flex-direction: column;
    align-items: center;
    gap: 8rpx;

    .notice-icon-wrap {
      position: relative;

      .notice-icon-bg {
        width: 80rpx;
        height: 80rpx;
        border-radius: 50%;
        display: flex;
        align-items: center;
        justify-content: center;
      }

      .notice-icon {
        font-size: 36rpx;
        color: var(--text-inverse);
        line-height: 1;
      }

      .badge {
        position: absolute;
        top: -4rpx;
        right: -8rpx;
        min-width: 32rpx;
        height: 32rpx;
        line-height: 32rpx;
        text-align: center;
        font-size: 20rpx;
        color: var(--text-inverse);
        background: var(--gradient-brand);
        border-radius: 16rpx;
        padding: 0 8rpx;
        border: 2rpx solid var(--text-inverse);
      }
    }

    .notice-label {
      font-size: 24rpx;
      color: var(--text-secondary);
    }
  }
}

/* 消息：聊天列表 */
.msg-list {
  flex: 1;
  min-height: 0;
  background: var(--bg-card);

  .chat-item {
    display: flex;
    align-items: center;
    padding: 24rpx 32rpx;
    /* 会话行高真源：与 mine/settings 同一档 --row-h-md */
    min-height: var(--row-h-touch-md);
    box-sizing: border-box;
    border-bottom: 1rpx solid var(--border-light);

    .avatar-wrap {
      position: relative;
      flex-shrink: 0;
    }

    .avatar {
      width: 96rpx;
      height: 96rpx;
      border-radius: 50%;
    }

    .online-dot {
      position: absolute;
      bottom: 2rpx;
      right: 2rpx;
      width: 24rpx;
      height: 24rpx;
      background: var(--success);
      border-radius: 50%;
      border: 4rpx solid var(--text-inverse);
    }

    .chat-info {
      flex: 1;
      margin-left: 24rpx;
      overflow: hidden;

      .top-row {
        display: flex;
        justify-content: space-between;
        align-items: center;

        .nickname {
          font-size: 30rpx;
          font-weight: 500;
          color: var(--text-primary);
        }
      }

      .bottom-row {
        display: flex;
        justify-content: space-between;
        align-items: center;
        margin-top: 8rpx;

        .last-msg {
          font-size: 26rpx;
          color: var(--text-hint);
          flex: 1;
        }

        .unread-badge {
          min-width: 32rpx;
          height: 32rpx;
          line-height: 32rpx;
          text-align: center;
          font-size: 20rpx;
          color: var(--text-inverse);
          background: var(--gradient-brand);
          border-radius: 16rpx;
          padding: 0 8rpx;
          margin-left: 16rpx;
        }
      }
    }
  }
}
</style>
