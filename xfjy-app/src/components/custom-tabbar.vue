<template>
  <!-- 自定义底部导航：非 tabBar 页面（如发布页）navigateTo 打开后原生 tabBar 不显示，用本组件补齐抖音式底栏 -->
  <view class="custom-tabbar" role="tablist" aria-label="主导航">
    <view class="tabbar-inner">
      <a11y-button
        v-for="tab in sideTabs"
        :key="tab.key"
        class="tab-entry btn-feedback"
        role="tab"
        :checked="isActive(tab.key)"
        :label="`切换到${tab.text}`"
        @tap="go(tab)"
      >
        <image
          class="tab-icon"
          :src="isActive(tab.key) ? tab.activeIcon : tab.icon"
          mode="aspectFit"
          aria-hidden="true"
        />
        <text class="tab-text" :class="{ active: isActive(tab.key) }">{{ tab.text }}</text>
      </a11y-button>
    </view>
    <!-- 居中发布键（与原生 tabBar midButton 同位）：对标抖音，仅显示“+”，无图标与文字 -->
    <a11y-button
      class="tab-entry mid-entry btn-feedback"
      role="tab"
      :checked="active === 'publish'"
      :label="active === 'publish' ? '当前在发布页' : '打开发布'"
      @tap="goPublish"
    >
      <view class="mid-plus" aria-hidden="true">
        <view class="plus-h"></view>
        <view class="plus-v"></view>
      </view>
    </a11y-button>
  </view>
</template>

<script setup lang="ts">
import A11yButton from "@/components/a11y-button.vue";

type TabKey = "match" | "circle" | "publish" | "video" | "mine";

const props = defineProps<{ active?: TabKey }>();

/** 与 pages.json tabBar 配置保持一致（发布为 midButton，不在原生 list 中） */
const sideTabs: { key: TabKey; text: string; path: string; icon: string; activeIcon: string }[] = [
  { key: "match", text: "匹配", path: "/pages/match/index", icon: "/static/tabbar/match.png", activeIcon: "/static/tabbar/match-active.png" },
  { key: "circle", text: "圈子", path: "/pages/circle/index", icon: "/static/tabbar/circle.png", activeIcon: "/static/tabbar/circle-active.png" },
  { key: "video", text: "视频", path: "/pages/video/index", icon: "/static/tabbar/video.png", activeIcon: "/static/tabbar/video-active.png" },
  { key: "mine", text: "我的", path: "/pages/mine/index", icon: "/static/tabbar/mine.png", activeIcon: "/static/tabbar/mine-active.png" },
];

function isActive(key: TabKey): boolean {
  return props.active === key;
}

function go(tab: (typeof sideTabs)[number]): void {
  if (isActive(tab.key)) return;
  uni.switchTab({ url: tab.path });
}

function goPublish(): void {
  if (isActive("publish")) return;
  uni.navigateTo({ url: "/pages/circle/publish" });
}
</script>

<style lang="scss" scoped>
.custom-tabbar {
  position: fixed;
  left: 0;
  right: 0;
  bottom: 0;
  z-index: 900;
  /* 与原生 H5 tabBar 一致：毛玻璃半透明，融入页面而非硬切割 */
  background: rgba(255, 255, 255, 0.82);
  -webkit-backdrop-filter: saturate(180%) blur(18px);
  backdrop-filter: saturate(180%) blur(18px);
  border-top: 1rpx solid rgba(0, 0, 0, 0.06);
  padding-bottom: env(safe-area-inset-bottom);

  .tabbar-inner {
    display: flex;
    height: 100rpx;
  }

  .tab-entry {
    flex: 1;
    display: flex;
    flex-direction: column;
    align-items: center;
    justify-content: center;
    gap: 4rpx;
  }

  .mid-entry {
    position: absolute;
    left: 50%;
    bottom: calc(env(safe-area-inset-bottom) + 6rpx);
    transform: translateX(-50%);
    width: 120rpx;
    display: flex;
    align-items: center;
    justify-content: center;
  }

  .tab-icon {
    width: 48rpx;
    height: 48rpx;
  }

  /* 中间发布键：抖音式渐变“+”，无方块底色 */
  .mid-plus {
    position: relative;
    width: 56rpx;
    height: 56rpx;
  }

  .plus-h,
  .plus-v {
    position: absolute;
    top: 50%;
    left: 50%;
    border-radius: 8rpx;
    /* 抖音主黑实心 */
    background: #161823;
    transform: translate(-50%, -50%);
  }

  .plus-h {
    width: 56rpx;
    height: 10rpx;
  }

  .plus-v {
    width: 10rpx;
    height: 56rpx;
  }

  .tab-text {
    font-size: 20rpx;
    line-height: 1;
    color: #9aa3af;

    &.active {
      color: var(--color-primary);
    }
  }
}
</style>
