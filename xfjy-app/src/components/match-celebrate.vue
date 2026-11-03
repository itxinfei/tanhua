<template>
  <view
    v-if="visible"
    class="celebrate-mask"
    role="alertdialog"
    aria-label="配对成功"
    @tap.stop="emit('close')"
  >
    <view class="celebrate-card" @tap.stop>
      <!-- 双方头像交叠 -->
      <view class="avatars" aria-hidden="true">
        <image class="avatar" :src="resolveAvatar(myAvatar)" mode="aspectFill" />
        <view class="heart-badge">
          <text class="i-ph-heart-fill heart-icon" />
        </view>
        <image class="avatar" :src="resolveAvatar(otherAvatar)" mode="aspectFill" />
      </view>

      <text class="title">配对成功！</text>
      <text class="desc">你们互相喜欢，打个招呼吧</text>

      <!-- 操作区：两个按钮均为 a11y-button，热区 ≥88rpx -->
      <view class="actions">
        <a11y-button
          variant="primary"
          block
          label="打个招呼"
          @tap="emit('chat')"
        >
          <text class="i-ph-chat-circle-dots" />
          <text>打个招呼</text>
        </a11y-button>
        <a11y-button variant="ghost" block label="继续逛逛" @tap="emit('close')">
          <text>继续逛逛</text>
        </a11y-button>
      </view>
    </view>
  </view>
</template>

<script setup lang="ts">
import A11yButton from "@/components/a11y-button.vue";
import { resolveAvatar } from "@/utils/avatar";

interface Props {
  visible: boolean;
  /** 我方头像 URL */
  myAvatar: string;
  /** 配对成功对方的头像 URL */
  otherAvatar: string;
}

withDefaults(defineProps<Props>(), {
  visible: false,
  myAvatar: "",
  otherAvatar: "",
});

const emit = defineEmits<{
  (e: "close"): void;
  (e: "chat"): void;
}>();
</script>

<style lang="scss" scoped>
/* 全屏遮罩：点击空白处关闭 */
.celebrate-mask {
  position: fixed;
  inset: 0;
  z-index: 999;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 48rpx;
  background: rgba(0, 0, 0, 0.6);
  /* 遮罩层是页面级覆盖，不受页面 overflow-x 约束 */
  overflow: hidden;
}

.celebrate-card {
  width: 100%;
  max-width: 560rpx;
  box-sizing: border-box;
  padding: 64rpx 40rpx;
  display: flex;
  flex-direction: column;
  align-items: center;
  background: var(--bg-card);
  border-radius: var(--radius-lg);
  /* 点击区域在卡片内，正常纵向滚动语义，无需横向溢出保护 */
  min-width: 0;
}

.avatars {
  display: flex;
  align-items: center;
  justify-content: center;

  .avatar {
    width: 160rpx;
    height: 160rpx;
    border-radius: 50%;
    border: 6rpx solid var(--bg-card);
    object-fit: cover;
  }

  .avatar + .avatar {
    margin-left: -32rpx;
  }

  .heart-badge {
    z-index: 1;
    margin: 0 -48rpx;
    width: 72rpx;
    height: 72rpx;
    display: flex;
    align-items: center;
    justify-content: center;
    border-radius: 50%;
    background: var(--gradient-brand);

    .heart-icon {
      font-size: 40rpx;
      color: var(--text-inverse);
    }
  }
}

.title {
  margin-top: 40rpx;
  font-size: 40rpx;
  font-weight: bold;
  color: var(--color-primary);
}

.desc {
  margin-top: 16rpx;
  font-size: 28rpx;
  color: var(--text-secondary);
  text-align: center;
}

.actions {
  margin-top: 48rpx;
  width: 100%;
  display: flex;
  flex-direction: column;
  gap: 20rpx;
}
</style>
