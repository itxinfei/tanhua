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
import { TABS, PUBLISH_PAGE } from "@/config/tab-config";

/** active 取值：发布页传 "publish"，其余场景传对应 tab 的 pagePath */
const props = defineProps<{ active?: string }>();

/** 由统一配置派生（不再重复维护 tab 列表，单一数据源：config/tab-config.ts） */
const sideTabs = TABS.map((t) => ({
  key: t.pagePath,
  text: t.text,
  path: `/${t.pagePath}`,
  icon: t.icon,
  activeIcon: t.iconActive,
}));

function isActive(key: string): boolean {
  return props.active === key;
}

function go(tab: (typeof sideTabs)[number]): void {
  if (isActive(tab.key)) return;
  uni.switchTab({ url: tab.path });
}

function goPublish(): void {
  if (isActive("publish")) return;
  uni.navigateTo({ url: `/${PUBLISH_PAGE}` });
}
</script>

<style lang="scss" scoped>
.custom-tabbar {
  position: fixed;
  left: 0;
  right: 0;
  bottom: 0;
  z-index: 998;
  /* 与原生 H5 tabBar 一致：实色白底 + 品牌色 1px 发丝线 */
  background: #ffffff;
  border-top: 1px solid #ff5e9c;
  padding-bottom: env(safe-area-inset-bottom);

  .tabbar-inner {
    display: flex;
    height: 100rpx; /* = 50px，与原生 tabbar 高度一致 */
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
    width: 56rpx; /* = 28px，与原生 .uni-tabbar__icon 一致 */
    height: 56rpx;
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
    border-radius: 5px;
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
    font-size: 20rpx; /* = 10px，与原生 tabbar 文字一致 */
    line-height: 1;
    color: #000000; /* 与 theme.json tabColor 一致 */

    &.active {
      color: #ff5e9c; /* 与 theme.json tabSelectedColor 一致 */
    }
  }
}

/* 暗色模式：与 App.vue 原生 tabbar 暗色变体一致 */
@media (prefers-color-scheme: dark) {
  .custom-tabbar {
    background: rgba(26, 26, 26, 0.82);
    border-top-color: #ff8eb9;

    .plus-h,
    .plus-v {
      background: #ffffff;
    }

    .tab-text {
      color: #999999;

      &.active {
        color: #ff5e9c;
      }
    }
  }
}
</style>
