<template>
  <!-- 统一底部导航组件：替代原 custom-tabbar.vue（发布页）与 global-tabbar.ts 手写 DOM（H5 全局注入）
       单一数据源：config/tab-config.ts；视觉规格：BEM + CSS 语义变量（见 style） -->
  <view class="app-tabbar" role="tablist" aria-label="主导航">
    <view class="app-tabbar__inner">
      <template v-for="(tab, i) in TABS" :key="tab.pagePath">
        <!-- 发布键插入在第 3 个 tab 之前（i === 2），布局：匹配 | 圈子 | [+] | 视频 | 我的 -->
        <a11y-button
          v-if="i === 2"
          class="app-tabbar__item app-tabbar__mid"
          role="tab"
          :checked="activeIndex === -1"
          :label="activeIndex === -1 ? '当前在发布页' : '打开发布'"
          @tap="goPublish"
        >
          <view class="app-tabbar__plus" aria-hidden="true">
              <view class="app-tabbar__plus-circle">
                <view class="app-tabbar__plus-h"></view>
                <view class="app-tabbar__plus-v"></view>
              </view>
            </view>
            <!-- 与小程序端一致补「发布」文字，新用户不再把中央键当成装饰 -->
            <text
              class="app-tabbar__label app-tabbar__mid-label"
              :class="{ 'app-tabbar__mid-label--active': activeIndex === -1 }"
            >发布</text>
          </a11y-button>
        <a11y-button
          class="app-tabbar__item"
          role="tab"
          :checked="activeIndex === i"
          :label="`切换到${tab.text}`"
          @tap="go(tab)"
        >
          <image
            class="app-tabbar__icon"
            :src="activeIndex === i ? tab.iconActive : tab.icon"
            mode="aspectFit"
            aria-hidden="true"
          />
          <text
            class="app-tabbar__label"
            :class="{ 'app-tabbar__label--active': activeIndex === i }"
          >{{ tab.text }}</text>
        </a11y-button>
      </template>
    </view>
  </view>
</template>

<script setup lang="ts">
import { ref, watch } from "vue";
import A11yButton from "@/components/a11y-button.vue";
import { TABS, PUBLISH_PAGE } from "@/config/tab-config";

/** 当前选中 tab 索引：0-3 对应 4 个 tab，-1 表示发布页（mid 发布键高亮） */
const props = defineProps<{ active?: number }>();

/** 当前高亮索引（ref 以便 global-tabbar.ts 路由变化时通过 setActive 更新） */
const activeIndex = ref(props.active ?? -1);

/**
 * 发布页等场景通过 :active prop 传入选中态（全局注入实例不传 prop，仅由 setActive 驱动）；
 * prop 变化时同步到 activeIndex，保证两种挂载方式共用同一高亮数据源。
 * 历史 bug：模板曾绑定 prop active，而全局实例不传 prop 且 setActive 只改 activeIndex，
 * 导致 H5 底栏选中态（主色图标/文字）永不生效。
 */
watch(
  () => props.active,
  (val) => {
    if (val !== undefined) activeIndex.value = val;
  }
);

/**
 * 由 global-tabbar.ts 在路由变化时调用，更新高亮。
 *
 * 全局游离实例（createApp 挂载在 uni-app 页面树外）的 Vue 响应式不可靠：
 * 写 activeIndex.value 后 Vue 不触发重渲染（icon src / label class 均不更新），
 * $forceUpdate 也无效。故直接走 DOM 操作，同时切换 icon 图片与 label 高亮。
 */
function setActive(i: number): void {
  activeIndex.value = i;
  applyActiveClass(i);
}

/**
 * 直接切换 DOM 高亮（不依赖 Vue 响应式，供全局游离实例使用）。
 * uni-image 是自定义元素，外层 setAttribute("src") 不触发内部刷新，
 * 必须同时改内部 <div> 的 background-image 与 <img> 的 src。
 */
function applyActiveClass(i: number): void {
  const root = document.getElementById("xfjy-global-tabbar");
  if (!root) return;
  const items = root.querySelectorAll<HTMLElement>(".app-tabbar__item");
  items.forEach((el, idx) => {
    // 模板中发布键插在 i===2 之前，实际 DOM 顺序：0匹配 1圈子 2发布 3视频 4我的
    // 发布键（idx=2）仅在 i === -1 时高亮；普通 tab 用 tabIdx 与 i 比较
    const isPublish = idx === 2;
    const tabIdx = idx < 2 ? idx : idx - 1;
    const isActive = isPublish ? i === -1 : tabIdx === i;
    const label = el.querySelector<HTMLElement>(".app-tabbar__label");
    const icon = el.querySelector<HTMLElement>(".app-tabbar__icon");
    el.setAttribute("aria-checked", isActive ? "true" : "false");
    label?.classList.toggle("app-tabbar__label--active", isActive);
    // 发布键使用独立的 --active class（app-tabbar__mid-label--active），需同步切换
    if (idx === 2) {
      label?.classList.toggle("app-tabbar__mid-label--active", isActive);
    }
    if (icon && tabIdx >= 0 && TABS[tabIdx]) {
      const target = isActive ? TABS[tabIdx].iconActive : TABS[tabIdx].icon;
      const div = icon.querySelector<HTMLElement>("div");
      const img = icon.querySelector<HTMLImageElement>("img");
      if (div) div.style.backgroundImage = `url("${target}")`;
      if (img) img.src = target;
    }
  });
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
   底栏高度 50px · 图标 28px · 文字 10px · 实色白底 · 顶部 1px 浅灰分割线 · 抖音式中央发布键
   图标为主的入口 2.8 倍（28px/10px），符合 uniapp skill 规范（2~2.4 倍） */
.app-tabbar {
  /* 局部定义 CSS 变量：tabbar 挂在 body 上（不在 page 内），无法继承 App.vue page 级别的变量。
     必须把页面用到的变量全部在此重复声明，否则 var() 解析为空（历史 bug：底栏背景与发布键
     白色加号都因 --bg-card 未定义而变成透明，只看到一个红圆）。
     暗色值与 App.vue 的 @media (prefers-color-scheme: dark) 保持一致。 */
  --color-primary: #fe3c72;
  --bg-card: #ffffff;
  --text-secondary: #666666;
  --border-light: #f0f0f0;

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

  /* 顶部分割线：与页面卡片/输入区的 1rpx 浅灰分割线同一视觉语言（--border-light），
     不用品牌色——红线在浅底上过于跳脱，且底栏顶部需要的是「与内容区分隔」而非强调 */
  &::before {
    content: "";
    position: absolute;
    left: 0;
    top: 0;
    width: 100%;
    height: 1px;
    background: var(--border-light);
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

  /* 居中发布键：第 3 位，5 等分布局，与两侧 tab 等宽；品牌色圆形底 + 白色加号
     注意：plus 系列元素位于 a11y-button 子组件内部，scoped 样式无法穿透，
     故 plus 系列样式移到下方非 scoped 全局样式块（见 <style lang="scss">） */
  &__mid {
    /* 布局占位，样式见全局块 */
  }
}

/* 暗色模式：与 App.vue 全局变量层对齐 */
@media (prefers-color-scheme: dark) {
  .app-tabbar {
    /* 暗色模式变量值与 App.vue page 级别保持一致 */
    --color-primary: #ff8eb9;
    --bg-card: #2c2c2c;
    --text-secondary: #aaaaaa;
    --border-light: #3a3a3a;

    background: rgba(26, 26, 26, 0.82);
    box-shadow: 0 -8px 28px rgba(0, 0, 0, 0.35);

    &__label {
      color: #8c8c8c;

      &--active {
        color: var(--color-primary);
      }
    }
  }
}
</style>

<!-- 发布键样式（非 scoped）：plus 系列元素是 a11y-button 子组件的 slot 内容，
     scoped 样式无法穿透子组件边界，必须用全局样式块。
     注意：uni-app 的 CSS 编译器对「同一规则内多个嵌套选择器」处理有 bug
     （会拆成不同前缀结构，导致部分声明丢失），故此处全部使用扁平的独立顶层选择器，
     不写嵌套、不写多选择器共用规则。 -->
<style lang="scss">
/* 圆形缩至 34px，为下方「发布」文字留出空间（底栏内容总高 50px） */
.app-tabbar__plus {
  position: relative;
  width: 34px;
  height: 34px;
  display: flex;
  align-items: center;
  justify-content: center;
}

.app-tabbar__plus-circle {
  position: relative;
  width: 34px;
  height: 34px;
  border-radius: 50%;
  background: var(--color-primary);
  box-shadow: 0 4rpx 12rpx rgba(var(--color-primary-rgb), 0.4);
  display: flex;
  align-items: center;
  justify-content: center;
}

/* 中央发布键文字：与其它 tab 标签同字号，选中（发布页）时高亮 */
.app-tabbar__mid-label {
  color: var(--text-secondary);
  font-size: 10px;
  line-height: 1;
  margin-top: 2px;
}

.app-tabbar__mid-label--active.app-tabbar__label {
  color: var(--color-primary);
  font-weight: 600;
}

.app-tabbar__plus-h {
  position: absolute;
  top: 50%;
  left: 50%;
  width: 18px;
  height: 3px;
  border-radius: 2px;
  background: var(--bg-card);
  transform: translate(-50%, -50%);
}

.app-tabbar__plus-v {
  position: absolute;
  top: 50%;
  left: 50%;
  width: 3px;
  height: 18px;
  border-radius: 2px;
  background: var(--bg-card);
  transform: translate(-50%, -50%);
}

@media (prefers-color-scheme: dark) {
  .app-tabbar__plus-circle {
    background: var(--color-primary);
    box-shadow: 0 4rpx 12rpx rgba(255, 142, 185, 0.4);
  }
}
</style>
