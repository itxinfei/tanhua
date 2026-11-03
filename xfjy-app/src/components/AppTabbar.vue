<template>
  <!-- 统一底部导航组件：替代原 custom-tabbar.vue（发布页）与 global-tabbar.ts 手写 DOM（H5 全局注入）
       单一数据源：config/tab-config.ts；视觉规格：BEM + CSS 语义变量（见 style） -->
  <view class="app-tabbar" role="tablist" aria-label="主导航">
    <view class="app-tabbar__inner">
      <template v-for="(tab, i) in TABS" :key="tab.pagePath">
        <!-- 发布键插入在第 3 个 tab 之前（i === 2），布局：匹配 | 圈子 | [+] | 视频 | 我的 -->
        <a11y-button
          v-if="i === 2"
          class="app-tabbar__item app-tabbar__mid btn-feedback"
          role="tab"
          :checked="active === -1"
          :label="active === -1 ? '当前在发布页' : '打开发布'"
          @tap="goPublish"
        >
          <view class="app-tabbar__plus" aria-hidden="true">
            <view class="app-tabbar__plus-circle">
              <view class="app-tabbar__plus-h"></view>
              <view class="app-tabbar__plus-v"></view>
            </view>
          </view>
        </a11y-button>
        <a11y-button
          class="app-tabbar__item btn-feedback"
          role="tab"
          :checked="active === i"
          :label="`切换到${tab.text}`"
          @tap="go(tab)"
        >
          <image
            class="app-tabbar__icon"
            :src="active === i ? tab.iconActive : tab.icon"
            mode="aspectFit"
            aria-hidden="true"
          />
          <text
            class="app-tabbar__label"
            :class="{ 'app-tabbar__label--active': active === i }"
          >{{ tab.text }}</text>
        </a11y-button>
      </template>
    </view>
  </view>
</template>

<script setup lang="ts">
import { ref } from "vue";
import A11yButton from "@/components/a11y-button.vue";
import { TABS, PUBLISH_PAGE } from "@/config/tab-config";

/** 当前选中 tab 索引：0-3 对应 4 个 tab，-1 表示发布页（mid 发布键高亮） */
const props = defineProps<{ active?: number }>();

/** 当前高亮索引（ref 以便 global-tabbar.ts 路由变化时通过 setActive 更新） */
const activeIndex = ref(props.active ?? -1);

/** 由 global-tabbar.ts 在路由变化时调用，更新高亮（替代原手写 DOM 切换 class） */
function setActive(i: number): void {
  activeIndex.value = i;
}

defineExpose({ setActive });

function go(tab: (typeof TABS)[number]): void {
  uni.switchTab({ url: `/${tab.pagePath}` });
}

function goPublish(): void {
  uni.navigateTo({ url: `/${PUBLISH_PAGE}` });
}
</script>

<style lang="scss" scoped>
/* 统一视觉规格（与 config/tab-config.ts 常量对齐）：
   底栏高度 50px · 图标 28px · 文字 10px · 实色白底 · 品牌色 1px 发丝线 · 抖音式中央发布键
   图标为主的入口 2.8 倍（28px/10px），符合 uniapp skill 规范（2~2.4 倍） */
.app-tabbar {
  /* 局部定义 CSS 变量：tabbar 挂在 body 上（不在 page 内），无法继承 App.vue page 级别的变量 */
  --color-primary: #fe3c72;
  --text-secondary: #666666;

  position: fixed;
  left: 0;
  right: 0;
  bottom: 0;
  z-index: 998;
  display: flex;
  align-items: stretch;
  box-sizing: border-box;
  min-height: 50px;
  background: var(--bg-card);
  padding-bottom: env(safe-area-inset-bottom);

  /* 顶部品牌色发丝线（对标抖音） */
  &::before {
    content: "";
    position: absolute;
    left: 0;
    top: 0;
    width: 100%;
    height: 1px;
    background: var(--color-primary);
  }

  &__inner {
    display: flex;
    flex: 1;
    height: 50px;
  }

  &__item {
    flex: 1;
    display: flex;
    flex-direction: column;
    align-items: center;
    justify-content: center;
    gap: 4rpx;
    -webkit-tap-highlight-color: rgba(0, 0, 0, 0);
    box-sizing: border-box;
  }

  &__icon {
    width: 28px;
    height: 28px;
  }

  &__label {
    font-size: 10px;
    line-height: 1;
    color: var(--text-secondary);

    &--active {
      color: var(--color-primary);
    }
  }

  /* 居中发布键：第 3 位，5 等分布局，与两侧 tab 等宽；品牌色圆形底 + 白色加号 */
  &__mid {
    .app-tabbar__plus {
      position: relative;
      width: 44px;
      height: 44px;
    }

    .app-tabbar__plus-circle {
      position: relative;
      width: 100%;
      height: 100%;
      border-radius: 50%;
      background: var(--color-primary);
      box-shadow: 0 4rpx 12rpx rgba(255, 94, 156, 0.4);
      display: flex;
      align-items: center;
      justify-content: center;
    }

    .app-tabbar__plus-h,
    .app-tabbar__plus-v {
      position: absolute;
      top: 50%;
      left: 50%;
      border-radius: 2px;
      background: var(--bg-card);
      transform: translate(-50%, -50%);
    }

    .app-tabbar__plus-h {
      width: 18px;
      height: 3px;
    }

    .app-tabbar__plus-v {
      width: 3px;
      height: 18px;
    }
  }
}

/* 暗色模式：与 App.vue 全局变量层对齐 */
@media (prefers-color-scheme: dark) {
  .app-tabbar {
    /* 暗色模式变量值与 App.vue page 级别保持一致 */
    --color-primary: #ff8eb9;
    --text-secondary: #aaaaaa;

    background: rgba(26, 26, 26, 0.82);
    box-shadow: 0 -8px 28px rgba(0, 0, 0, 0.35);

    &__label {
      color: #8c8c8c;

      &--active {
        color: var(--color-primary);
      }
    }

    .app-tabbar__plus-circle {
      background: var(--color-primary);
      box-shadow: 0 4rpx 12rpx rgba(255, 142, 185, 0.4);
    }
  }
}
</style>
