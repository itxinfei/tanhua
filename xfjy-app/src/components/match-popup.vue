<template>
  <view v-if="matchStore.matchPopup" class="match-popup" @click="matchStore.closeMatchPopup()">
    <view class="match-content pop-in">
      <text class="match-title gradient-text">It's a Match!</text>
      <view class="match-avatars">
        <image class="match-avatar" :src="matchStore.matchPopup.avatar" mode="aspectFill" />
        <text class="match-heart heart-beat">♥</text>
        <image class="match-avatar" src="/static/logo/logo-icon-transparent.png" mode="aspectFill" />
      </view>
      <text class="match-name">你和 {{ matchStore.matchPopup.nickname }} 互相喜欢了</text>
      <view class="match-actions">
        <button class="send-msg" @click.stop="goChat">发消息</button>
        <button class="keep-swipe" @click.stop="matchStore.closeMatchPopup()">继续逛逛</button>
      </view>
    </view>
  </view>
</template>

<script setup lang="ts">
import { useMatchStore } from "@/store/match";

const matchStore = useMatchStore();

/** 跳转与匹配对象的聊天页 */
function goChat(): void {
  const userId = matchStore.matchPopup?.userId;
  if (userId === undefined) return;
  matchStore.closeMatchPopup();
  uni.navigateTo({ url: `/pages/chat/chat?userId=${userId}` });
}
</script>

<style lang="scss" scoped>
.match-popup {
  position: fixed;
  inset: 0;
  background: rgba(0, 0, 0, 0.75);
  display: flex;
  align-items: center;
  justify-content: center;
  z-index: 999;

  .match-content {
    display: flex;
    flex-direction: column;
    align-items: center;

    .match-title {
      font-size: 64rpx;
      font-weight: 900;
      background: linear-gradient(90deg, #ff5e8e, #ffd700);
      -webkit-background-clip: text;
      background-clip: text;
      -webkit-text-fill-color: transparent;
      margin-bottom: 48rpx;
    }

    .match-avatars {
      display: flex;
      align-items: center;
      gap: 32rpx;
      margin-bottom: 32rpx;

      .match-avatar {
        width: 160rpx;
        height: 160rpx;
        border-radius: 50%;
        border: 6rpx solid #ff5e8e;
      }

      .match-heart {
        font-size: 56rpx;
        color: #ff5e8e;
      }
    }

    .match-name {
      font-size: 30rpx;
      color: #fff;
      margin-bottom: 48rpx;
    }

    .match-actions {
      display: flex;
      gap: 32rpx;

      .send-msg {
        background: linear-gradient(90deg, #ff5e8e, #ff8fab);
        color: #fff;
        font-size: 30rpx;
        padding: 0 48rpx;
        height: 88rpx;
        line-height: 88rpx;
        border-radius: 44rpx;
        border: none;
      }

      .keep-swipe {
        background: transparent;
        color: #fff;
        font-size: 30rpx;
        padding: 0 48rpx;
        height: 88rpx;
        line-height: 88rpx;
        border-radius: 44rpx;
        border: 2rpx solid rgba(255, 255, 255, 0.5);
      }
    }
  }
}
</style>
