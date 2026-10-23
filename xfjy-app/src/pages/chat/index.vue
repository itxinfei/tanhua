<template>
  <view class="chat-page">
    <!-- 通知入口 -->
    <view class="notice-bar">
      <view class="notice-item" v-for="item in noticeItems" :key="item.type" @click="goNotifications(item.type)">
        <view class="notice-icon-wrap">
          <text class="notice-icon">{{ item.icon }}</text>
          <view v-if="item.unread > 0" class="badge">{{ item.unread > 99 ? '99+' : item.unread }}</view>
        </view>
        <text class="notice-label">{{ item.label }}</text>
      </view>
    </view>

    <!-- 聊天列表 -->
    <view class="chat-list">
      <view v-if="chatList.length === 0" class="empty">
        <text class="empty-text">还没有聊天，去匹配页滑动遇见新朋友吧</text>
      </view>
      <view
        v-for="contact in chatList"
        :key="contact.userId"
        class="chat-item"
        @click="goChatDetail(contact)"
      >
        <image class="avatar" :src="contact.avatar" mode="aspectFill" />
        <view class="chat-info">
          <view class="top-row">
            <text class="nickname">{{ contact.nickname }}</text>
            <text v-if="contact.age" class="time">{{ contact.age }}岁</text>
          </view>
          <view class="bottom-row">
            <text class="last-msg ellipsis">{{ contact.lastMessage || contact.city || '打个招呼吧' }}</text>
            <view v-if="contact.unreadCount > 0" class="unread-badge">{{ contact.unreadCount > 99 ? '99+' : contact.unreadCount }}</view>
          </view>
        </view>
      </view>
    </view>
  </view>
</template>

<script setup lang="ts">
import { ref, computed } from "vue";
import { onShow } from "@dcloudio/uni-app";
import { getContacts, type Contact } from "@/api/chat";
import { useChatStore, type NoticeType } from "@/store/chat";
import { loginEasemob, getConversations } from "@/utils/easemob";

const chatStore = useChatStore();

/** 聊天列表项（联系人 + 环信会话摘要） */
interface ChatListItem extends Contact {
  lastMessage: string;
  unreadCount: number;
}

/** 通知入口图标与文案配置 */
const NOTICE_META: Record<NoticeType, { icon: string; label: string }> = {
  like: { icon: "♥", label: "喜欢" },
  comment: { icon: "💬", label: "评论" },
  love: { icon: "🌟", label: "心动" },
  announcement: { icon: "📢", label: "公告" },
};

/** 通知入口（未读数来自 store 本地存储方案） */
const noticeItems = computed(() =>
  (Object.keys(NOTICE_META) as NoticeType[]).map((type) => ({
    type,
    icon: NOTICE_META[type].icon,
    label: NOTICE_META[type].label,
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
  } catch {
    // 请求失败由 request 层统一 toast
  }
}

/**
 * 跳转通知页并标记已读
 * @param type 通知类型
 */
function goNotifications(type: string): void {
  chatStore.markNoticeRead(type as NoticeType);
  uni.navigateTo({ url: `/pages/chat/notifications?type=${type}` });
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
</script>

<style lang="scss" scoped>
.chat-page {
  min-height: 100vh;
  background: #f5f5f5;
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

      .notice-icon {
        font-size: 44rpx;
      }

      .badge {
        position: absolute;
        top: -8rpx;
        right: -16rpx;
        min-width: 32rpx;
        height: 32rpx;
        line-height: 32rpx;
        text-align: center;
        font-size: 20rpx;
        color: #fff;
        background: #ff3b30;
        border-radius: 16rpx;
        padding: 0 8rpx;
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

  .empty {
    display: flex;
    justify-content: center;
    padding: 120rpx 0;

    .empty-text {
      font-size: 28rpx;
      color: #999;
    }
  }

  .chat-item {
    display: flex;
    align-items: center;
    padding: 24rpx 32rpx;
    border-bottom: 1rpx solid #f0f0f0;

    .avatar {
      width: 96rpx;
      height: 96rpx;
      border-radius: 50%;
      flex-shrink: 0;
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

        .time {
          font-size: 22rpx;
          color: #ccc;
        }
      }

      .bottom-row {
        display: flex;
        justify-content: space-between;
        align-items: center;
        margin-top: 8rpx;

        .last-msg {
          font-size: 26rpx;
          color: #999;
          flex: 1;
        }

        .unread-badge {
          min-width: 32rpx;
          height: 32rpx;
          line-height: 32rpx;
          text-align: center;
          font-size: 20rpx;
          color: #fff;
          background: #ff3b30;
          border-radius: 16rpx;
          padding: 0 8rpx;
          margin-left: 16rpx;
        }
      }
    }
  }
}
</style>