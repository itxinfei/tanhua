<template>
  <!-- 统一骨架屏：信息流卡片(feed) / 视频全屏(video) / 卡片堆叠(match)。占位元素对读屏隐藏 -->
  <view
    class="skeleton-root"
    :class="rootClass"
    aria-hidden="true"
  >
    <!-- feed：信息流卡片占位（circle 首页） -->
    <template v-if="variant === 'feed'">
      <view
        v-for="i in count"
        :key="i"
        class="sk-card"
      >
        <view class="sk-head">
          <view class="skeleton sk-avatar" />
          <view class="skeleton sk-name" />
        </view>
        <view class="skeleton sk-text" />
        <view class="skeleton sk-text sk-short" />
        <view class="skeleton sk-grid-row">
          <view
            v-for="j in gridCols"
            :key="j"
            class="skeleton sk-grid"
          />
        </view>
      </view>
    </template>

    <!-- video：视频页全屏占位（暗色背景，始终使用暗色骨架） -->
    <template v-else-if="variant === 'video'">
      <view class="skeleton skeleton--dark sk-full" />
      <view class="skeleton skeleton--dark sk-author" />
      <view class="skeleton skeleton--dark sk-desc" />
    </template>

    <!-- match：卡片页占位（探花首页，与真实卡片同几何：全屏单卡铺底） -->
    <template v-else-if="variant === 'match'">
      <view class="skeleton sk-match-card" />
    </template>
  </view>
</template>

<script setup lang="ts">
import { computed, withDefaults } from 'vue';

const props = withDefaults(
  defineProps<{
    /** 骨架形态：feed=信息流卡片 / video=视频全屏 / match=卡片页全屏 */
    variant?: 'feed' | 'video' | 'match';
    /** feed 形态下的占位卡片数量 */
    count?: number;
    /** feed 形态九宫格列数 */
    gridCols?: number;
  }>(),
  {
    variant: 'feed',
    count: 3,
    gridCols: 3,
  }
);

const rootClass = computed(() => {
  if (props.variant === 'video') return 'sk-video';
  if (props.variant === 'match') return 'sk-match';
  return '';
});
</script>

<style lang="scss" scoped>
.skeleton-root {
  /* 容器本身透明，仅承载布局；闪烁动画由全局 .skeleton 提供 */
}

/* ===== feed：信息流卡片 ===== */
.sk-card {
  background: var(--bg-card);
  border-radius: 24rpx;
  padding: 24rpx;
  margin-bottom: 16rpx;

  .sk-head {
    display: flex;
    align-items: center;
    margin-bottom: 20rpx;

    .sk-avatar {
      width: 72rpx;
      height: 72rpx;
      border-radius: 50%;
      flex-shrink: 0;
    }

    .sk-name {
      width: 180rpx;
      height: 28rpx;
      margin-left: 16rpx;
    }
  }

  .sk-text {
    width: 100%;
    height: 26rpx;
    margin-bottom: 12rpx;

    &.sk-short {
      width: 60%;
    }
  }

  .sk-grid-row {
    display: flex;
    gap: 8rpx;
    height: 200rpx;
    margin-top: 8rpx;

    .sk-grid {
      flex: 1;
      height: 100%;
      border-radius: 8rpx;
    }
  }
}

/* ===== video：全屏占位（绝对定位铺满父容器） ===== */
.sk-video {
  position: absolute;
  inset: 0;
  z-index: 20;

  .sk-full {
    position: absolute;
    inset: 0;
    border-radius: 0;
  }

  .sk-author {
    position: absolute;
    left: 24rpx;
    bottom: 140rpx;
    width: 200rpx;
    height: 32rpx;
  }

  .sk-desc {
    position: absolute;
    left: 24rpx;
    bottom: 90rpx;
    width: 400rpx;
    height: 26rpx;
  }
}

/* ===== match：卡片堆叠占位（与真实卡片同几何：全屏铺底） ===== */
.sk-match {
  position: absolute;
  inset: 0;

  .sk-match-card {
    position: absolute;
    inset: 0;
    border-radius: 0;
  }
}
</style>
