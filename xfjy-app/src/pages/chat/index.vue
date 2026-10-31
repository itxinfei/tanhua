<template>
  <view class="chat-page">
    <!-- 通知入口 -->
    <view class="notice-bar">
      <a11y-button class="notice-item btn-feedback" v-for="item in noticeItems" :key="item.type" :label="item.unread > 0 ? `${item.label}，${item.unread} 条未读` : item.label" @tap="goNotifications(item.type)">
        <view class="notice-icon-wrap">
          <view class="notice-icon-bg" :style="{ background: item.color }">
            <uni-icons class="notice-icon" :type="item.uniIcon" size="22" color="#fff"></uni-icons>
          </view>
          <!-- 角标数字由父级 aria-label 承载，此处对读屏隐藏避免重复朗读 -->
          <view v-if="item.unread > 0" class="badge pulse" aria-hidden="true">{{ item.unread > 99 ? '99+' : item.unread }}</view>
        </view>
        <text class="notice-label">{{ item.label }}</text>
      </a11y-button>
    </view>

    <!-- 聊天列表 -->
    <empty-state title="还没有聊天" desc="去匹配页滑动，遇见新朋友吧" />
</template>

<script setup lang="ts">
import { ref, computed } from "vue";
import EmptyState from "@/components/empty-state.vue";
import { onShow, onPullDownRefresh } from "@dcloudio/uni-app";
import MatchPopup from "@/components/match-popup.vue";
import A11yButton from "@/components/a11y-button.vue";
import { getContacts, type Contact } from "@/api/chat";
import { useChatStore, type NoticeType } from "@/store/chat";
import { loginEasemob, getConversations } from "@/utils/easemob";

const chatStore = useChatStore();

/** 聊天列表项（联系人 + 环信会话摘要） */
interface ChatListItem extends Contact {
  lastMessage: string;
  unreadCount: number;
}

/** 通知入口图标、文案与背景色配置 */
const NOTICE_META: Record<
  NoticeType,
  { uniIcon: string; label: string; color: string }
> = {
  like: { uniIcon: "heart-filled", label: "喜欢", color: "#ff5e9c" },
  comment: { uniIcon: "chatbubble", label: "评论", color: "#4a90d9" },
  love: { uniIcon: "star-filled", label: "心动", color: "#ff5e9c" },
  announcement: { uniIcon: "notification", label: "公告", color: "#4cd964" },
};

/** tabBar 消息页索引（见 pages.json tabBar.list） */
const TAB_BAR_CHAT_INDEX = 1;
/** 角标最大显示数字，超过显示 99+ */
const BADGE_MAX_COUNT = 99;

/** 通知入口（未读数来自 store 本地存储方案） */
const noticeItems = computed(() =>
  (Object.keys(NOTICE_META) as NoticeType[]).map((type) => ({
    type,
    uniIcon: NOTICE_META[type].uniIcon,
    label: NOTICE_META[type].label,
    color: NOTICE_META[type].color,
    unread: chatStore.noticeUnreads[type],
  }))
);

/** 聊天列表 */
const chatList = ref<ChatListItem[]>([]);

/**
 * 初始化环信并合并会话摘要到联系人列表
 * 登录失败静默降级，列表仅展示联系人基础信息
 */
async function initEasemobAndMerge(): Promise<void> {
  try {
    const credential = await chatStore.loadHuanXinUser();
    const ok = await loginEasemob(credential.username, credential.token);
    if (!ok) return;
    const sessions = await getConversations();
    const sessionMap = new Map(
      sessions.map((s) => [s.conversationId, s])
    );
    chatList.value = chatList.value.map((item) => {
      const session = sessionMap.get(item.userId);
      return session
        ? {
            ...item,
            lastMessage: session.lastMessage,
            unreadCount: session.unreadCount,
          }
        : item;
    });
  } catch {
    // 环信不可用，静默降级为纯联系人展示
  }
}

/**
 * 同步 tabBar 消息角标（聊天会话未读总数）
 */
function updateTabBarBadge(): void {
  const totalUnread = chatList.value.reduce(
    (sum, item) => sum + (item.unreadCount || 0),
    0
  );
  if (totalUnread > 0) {
    uni.setTabBarBadge({
      index: TAB_BAR_CHAT_INDEX,
      text: totalUnread > BADGE_MAX_COUNT ? "99+" : String(totalUnread),
    });
  } else {
    uni.removeTabBarBadge({ index: TAB_BAR_CHAT_INDEX });
  }
}

/**
 * 加载联系人列表并合并环信会话
 */
async function loadContacts(): Promise<void> {
  try {
    const res = await getContacts({ page: 1, pagesize: 50 });
    chatList.value = (res.items || []).map((c) => ({
      ...c,
      lastMessage: "",
      unreadCount: 0,
    }));
    await initEasemobAndMerge();
    updateTabBarBadge();
  } catch {
    // 请求失败由 request 层统一 toast
  }
}

/**
 * 跳转通知页（已读标记由通知页在列表加载后执行，误触返回不清零）
 * @param type 通知类型
 */
function goNotifications(type: string): void {
  uni.navigateTo({ url: `/pages/chat/notifications?type=${type}` });
}

/**
 * 聊天列表项的无障碍标签：合并昵称/最后消息/未读数/在线态
 *  (读屏一次性朗读，避免用户逐个焦点去拼信息)
 */
function chatItemLabel(contact: ChatListItem): string {
  const parts = [contact.nickname];
  if (contact.age) parts.push(`${contact.age}岁`);
  parts.push(contact.lastMessage || contact.city || "暂无消息");
  if (contact.unreadCount > 0) {
    parts.push(`${contact.unreadCount > BADGE_MAX_COUNT ? "99+" : contact.unreadCount} 条未读`);
  }
  if (contact.online) parts.push("在线");
  return parts.join("，");
}

/**
 * 跳转聊天详情页
 * @param contact 联系人
 */
function goChatDetail(contact: ChatListItem): void {
  uni.navigateTo({
    url: `/pages/chat/chat?userId=${contact.userId}&nickname=${encodeURIComponent(contact.nickname)}`,
  });
}

onShow(() => {
  loadContacts();
  chatStore.loadNoticeCounts();
});

/** 下拉刷新：重新拉取联系人与通知未读数 */
onPullDownRefresh(async () => {
  try {
    await Promise.all([loadContacts(), chatStore.loadNoticeCounts()]);
  } finally {
    uni.stopPullDownRefresh();
  }
});
</script>

<style lang="scss" scoped>
.chat-page {
  min-height: 100vh;
  background: #f5f5f5;
  padding-bottom: 24rpx;
}

.notice-bar {
  display: flex;
  justify-content: space-around;
  padding: 24rpx 0;
  background: #fff;
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
        color: #fff;
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
        color: #fff;
        background: linear-gradient(135deg, var(--color-primary-deep), var(--color-primary));
        border-radius: 16rpx;
        padding: 0 8rpx;
        border: 2rpx solid #fff;
      }
    }

    .notice-label {
      font-size: 24rpx;
      color: #666;
    }
  }
}

.chat-list {
  background: #fff;

  .chat-item {
    display: flex;
    align-items: center;
    padding: 24rpx 32rpx;
    border-bottom: 1rpx solid #f0f0f0;

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
      background: #4cd964;
      border-radius: 50%;
      border: 4rpx solid #fff;
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
          color: #333;
        }

        /* #ccc on #fff ≈1.6:1 → #767676 */
        .time {
          font-size: 22rpx;
          color: #767676;
        }
      }

      .bottom-row {
        display: flex;
        justify-content: space-between;
        align-items: center;
        margin-top: 8rpx;

        /* #999 on #fff ≈2.85:1 → #767676（≈4.54:1） */
        .last-msg {
          font-size: 26rpx;
          color: #767676;
          flex: 1;
        }

        .unread-badge {
          min-width: 32rpx;
          height: 32rpx;
          line-height: 32rpx;
          text-align: center;
          font-size: 20rpx;
          color: #fff;
          background: linear-gradient(135deg, var(--color-primary-deep), var(--color-primary));
          border-radius: 16rpx;
          padding: 0 8rpx;
          margin-left: 16rpx;
        }
      }
    }
  }
}
</style>