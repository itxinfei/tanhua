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
        v-for="chat in chatList"
        :key="chat.id"
        class="chat-item"
        @click="goChatDetail(chat)"
      >
        <image class="avatar" :src="chat.avatar" mode="aspectFill" />
        <view class="chat-info">
          <view class="top-row">
            <text class="nickname">{{ chat.nickname }}</text>
            <text class="time">{{ chat.lastTime }}</text>
          </view>
          <view class="bottom-row">
            <text class="last-msg ellipsis">{{ chat.lastMessage }}</text>
            <view v-if="chat.unread > 0" class="unread-badge">{{ chat.unread > 99 ? '99+' : chat.unread }}</view>
          </view>
        </view>
      </view>
    </view>
  </view>
</template>

<script setup lang="ts">
import { ref } from "vue";
import { onShow } from "@dcloudio/uni-app";

// 通知入口
const noticeItems = ref([
  { type: "like", icon: "♥", label: "喜欢", unread: 0 },
  { type: "comment", icon: "💬", label: "评论", unread: 0 },
  { type: "love", icon: "🌟", label: "心动", unread: 0 },
  { type: "announcement", icon: "📢", label: "公告", unread: 0 },
]);

// 聊天列表
interface ChatItem {
  id: string;
  nickname: string;
  avatar: string;
  lastMessage: string;
  lastTime: string;
  unread: number;
}
const chatList = ref<ChatItem[]>([]);

function goNotifications(type: string) {
  uni.navigateTo({ url: `/pages/chat/notifications?type=${type}` });
}

function goChatDetail(chat: ChatItem) {
  uni.navigateTo({ url: `/pages/chat/chat?userId=${chat.id}&nickname=${chat.nickname}` });
}

onShow(() => {
  // TODO: 对接 GET /messages/contacts 加载聊天列表
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