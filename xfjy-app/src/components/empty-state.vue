<template>
  <!-- 统一空状态：图标 + 标题 + 描述；overlay 用于整页遮罩，dark 用于深色背景 -->
  <view class="empty-state" :class="{ 'empty-overlay': overlay, dark }">
    <view v-if="icon" class="empty-icon" aria-hidden="true">
      <uni-icons :type="icon" size="48" :color="iconColor"></uni-icons>
    </view>
    <text v-if="title" class="empty-title">{{ title }}</text>
    <text v-if="desc" class="empty-desc">{{ desc }}</text>
    <slot />
  </view>
</template>

<script setup lang="ts">
withDefaults(
  defineProps<{
    /** uni-icons 图标类型，缺省则不显示图标 */
    icon?: string;
    /** 图标颜色 */
    iconColor?: string;
    /** 主标题 */
    title?: string;
    /** 次要描述 */
    desc?: string;
    /** 是否作为整页绝对定位遮罩（铺满父容器） */
    overlay?: boolean;
    /** 深色背景适配（白字 + 半透明图标底） */
    dark?: boolean;
  }>(),
  {
    iconColor: "#767676",
    overlay: false,
    dark: false,
  }
);
</script>

<style lang="scss" scoped>
.empty-state {
  display: flex;
  flex-direction: column;
  align-items: center;
  padding: 120rpx 0 60rpx;

  .empty-icon {
    width: 120rpx;
    height: 120rpx;
    border-radius: 50%;
    background: var(--bg-sub);
    display: flex;
    align-items: center;
    justify-content: center;
    margin-bottom: 24rpx;
  }

  .empty-title {
    font-size: 30rpx;
    color: var(--text-hint-strong);
    font-weight: bold;
  }

  .empty-desc {
    font-size: 24rpx;
    color: var(--text-hint);
    margin-top: 12rpx;
    text-align: center;
  }
}

/* 整页遮罩模式：铺满父容器并垂直居中 */
.empty-overlay {
  position: absolute;
  inset: 0;
  justify-content: center;
  padding: 0;
}

/* 深色背景适配（视频页等） */
.empty-state.dark {
  .empty-icon {
    background: rgba(255, 255, 255, 0.1);
  }

  .empty-title {
    color: rgba(255, 255, 255, 0.9);
  }

  .empty-desc {
    color: rgba(255, 255, 255, 0.5);
  }
}
</style>
