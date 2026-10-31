<template>
  <!-- 匹配成功弹窗：补齐 dialog 语义、Esc 关闭与焦点管理 -->
  <a11y-dialog
    class="match-popup"
    :visible="!!matchStore.matchPopup"
    title="匹配成功"
    @close="matchStore.closeMatchPopup()"
  >
    <view class="match-content pop-in">
      <text class="match-title gradient-text">It's a Match!</text>
      <view class="match-avatars">
        <image lazy-load class="match-avatar" :src="matchStore.matchPopup.avatar" mode="aspectFill" :aria-label="`${matchStore.matchPopup.nickname} 的头像`" />
        <uni-icons class="match-heart heart-beat" type="heart-filled" size="28" color="var(--color-primary)" aria-hidden="true"></uni-icons>
        <image lazy-load class="match-avatar" src="/static/logo/logo-icon-transparent.png" mode="aspectFill" aria-label="我的头像" />
      </view>
      <text class="match-name">你和 {{ matchStore.matchPopup.nickname }} 互相喜欢了</text>
      <view class="match-actions">
        <button class="send-msg" @click.stop="goChat">发消息</button>
        <button class="keep-swipe" @click.stop="matchStore.closeMatchPopup()">继续逛逛</button>
      </view>
    </view>
  </a11y-dialog>
</template>

<script setup lang="ts">
import { useMatchStore } from "@/store/match";
import A11yDialog from "@/components/a11y-dialog.vue";

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
      background: var(--gradient-brand-h);
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
        border: 6rpx solid var(--color-primary);
      }

      .match-heart {
        font-size: 56rpx;
        color: var(--color-primary);
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
        background: var(--gradient-brand-h);
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
