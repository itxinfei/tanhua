<template>
  <!-- 统一底部导航组件：替代原 custom-tabbar.vue（发布页）与 global-tabbar.ts 手写 DOM（H5 全局注入）
       单一数据源：config/tab-config.ts；视觉规格：BEM + CSS 语义变量（见 style） -->
  <view
    class="app-tabbar"
    role="tablist"
    aria-label="主导航"
  >
    <view class="app-tabbar__inner">
      <!-- 固定 5 项（4 tab + 发布键），显式写死。
           高亮走 Vue 响应式，但 icon/label 放在 a11y-button 之外（作为兄弟节点）：
           uni-h5 Vue 3.5 的 updateSlots 只处理子组件 slot，普通 DOM 变化走 patchElement
           不触发 updateSlots，从而规避「Cannot assign to read only property '_'」。
           a11y-button 只作为点击热区（空 slot），保证 88rpx 热区与无障碍语义。
           布局：匹配 | 圈子 | [+] | 视频 | 我的 -->
      <view class="app-tabbar__item">
        <a11y-button
          class="app-tabbar__hit"
          role="tab"
          :checked="false"
          label="切换到匹配"
          @tap="go(TABS[0])"
        />
        <image
          class="app-tabbar__icon"
          :src="TABS[0].icon"
          mode="aspectFit"
          aria-hidden="true"
        />
        <text class="app-tabbar__label">
          {{ TABS[0].text }}
        </text>
      </view>
      <view class="app-tabbar__item">
        <a11y-button
          class="app-tabbar__hit"
          role="tab"
          :checked="false"
          label="切换到圈子"
          @tap="go(TABS[1])"
        />
        <image
          class="app-tabbar__icon"
          :src="TABS[1].icon"
          mode="aspectFit"
          aria-hidden="true"
        />
        <text class="app-tabbar__label">
          {{ TABS[1].text }}
        </text>
      </view>
      <!-- 中央发布键：抖音式加号圆 -->
      <view class="app-tabbar__item app-tabbar__mid">
        <a11y-button
          class="app-tabbar__hit"
          role="tab"
          :checked="false"
          label="打开发布"
          @tap="goPublish"
        />
        <view
          class="app-tabbar__plus"
          aria-hidden="true"
        >
          <view class="app-tabbar__plus-circle">
            <view class="app-tabbar__plus-h" />
            <view class="app-tabbar__plus-v" />
          </view>
        </view>
        <!-- 与小程序端一致补「发布」文字，新用户不再把中央键当成装饰 -->
        <text class="app-tabbar__label app-tabbar__mid-label">
          发布
        </text>
      </view>
      <view class="app-tabbar__item">
        <a11y-button
          class="app-tabbar__hit"
          role="tab"
          :checked="false"
          label="切换到视频"
          @tap="go(TABS[2])"
        />
        <image
          class="app-tabbar__icon"
          :src="TABS[2].icon"
          mode="aspectFit"
          aria-hidden="true"
        />
        <text class="app-tabbar__label">
          {{ TABS[2].text }}
        </text>
      </view>
      <view class="app-tabbar__item">
        <a11y-button
          class="app-tabbar__hit"
          role="tab"
          :checked="false"
          label="切换到我的"
          @tap="go(TABS[3])"
        />
        <image
          class="app-tabbar__icon"
          :src="TABS[3].icon"
          mode="aspectFit"
          aria-hidden="true"
        />
        <text class="app-tabbar__label">
          {{ TABS[3].text }}
        </text>
      </view>
    </view>
  </view>
</template>

<script setup lang="ts">
import { ref, onMounted, watch, getCurrentInstance } from "vue";
import A11yButton from "@/components/a11y-button.vue";
import { TABS, PUBLISH_PAGE } from "@/config/tab-config";

/** 当前选中 tab 索引：0-3 对应 4 个 tab，-1 表示发布页（mid 发布键高亮） */
const props = defineProps<{ active?: number }>();

/** 当前高亮索引（仅内部状态，模板不绑定：全局游离实例响应式不可靠） */
const activeIndex = ref(props.active ?? -1);

/** 页面内实例根元素缓存（挂载后赋值，供 applyActiveClass 定位） */
let localRoot: HTMLElement | null = null;

/**
 * 发布页等场景通过 :active prop 传入选中态（全局注入实例不传 prop，仅由 setActive 驱动）。
 */
watch(
  () => props.active,
  (val) => {
    if (val !== undefined) {
      activeIndex.value = val;
      applyActiveClass(val);
    }
  }
);

/** 由 global-tabbar.ts 在路由变化时调用，更新高亮。 */
function setActive(i: number): void {
  activeIndex.value = i;
  applyActiveClass(i);
}

/**
 * 直接切换 DOM 高亮（不依赖 Vue 响应式）。
 *
 * 为什么必须用 DOM：全局游离实例（createApp 挂载在 uni-app 页面树外）的 Vue 响应式
 * 不触发重渲染，改 activeIndex 后模板不更新；且若把高亮绑定写在 a11y-button 的
 * slot 内，重渲染会触发 uni-h5 Vue 3.5 的 updateSlots 对只读 slot 对象做
 * Object.assign，报「Cannot assign to read only property '_'」。
 * 故 icon/label 放在 a11y-button 之外（slot 为空），高亮完全由本函数承担。
 *
 * 根元素定位：全局实例用硬编码 id（global-tabbar.ts 挂载容器），
 * 页面内实例（发布页）用挂载时缓存的 localRoot。
 */
function applyActiveClass(i: number): void {
  // 优先用挂载时缓存的 localRoot（页面内实例与全局实例各自缓存），
  // 避免全局实例存在时误操作到全局底栏（发布页场景）
  const root =
    localRoot ?? document.getElementById("xfjy-global-tabbar");
  if (!root) return;
  const items = root.querySelectorAll<HTMLElement>(".app-tabbar__item");
  items.forEach((item, idx) => {
    // DOM 顺序：0匹配 1圈子 2发布 3视频 4我的
    // 发布键（idx=2）仅在 i === -1 时高亮；普通 tab 用 tabIdx 与 i 比较
    const isPublish = idx === 2;
    const tabIdx = idx < 2 ? idx : idx - 1;
    const isActive = isPublish ? i === -1 : tabIdx === i;
    const hit = item.querySelector<HTMLElement>(".app-tabbar__hit");
    const label = item.querySelector<HTMLElement>(".app-tabbar__label");
    const icon = item.querySelector<HTMLElement>(".app-tabbar__icon");
    hit?.setAttribute("aria-checked", isActive ? "true" : "false");
    label?.classList.toggle("app-tabbar__label--active", isActive);
    // 发布键使用独立的 --active class（app-tabbar__mid-label--active），需同步切换
    if (idx === 2) {
      label?.classList.toggle("app-tabbar__mid-label--active", isActive);
    }
    if (icon && tabIdx >= 0 && TABS[tabIdx]) {
      const target = isActive ? TABS[tabIdx].iconActive : TABS[tabIdx].icon;
      const img = icon.querySelector<HTMLImageElement>("img");
      if (img) img.src = target;
    }
  });
}

/** 页面内实例挂载后缓存根元素并应用高亮（全局实例由 global-tabbar.ts 首次 refresh 触发） */
onMounted(() => {
  const instance = getCurrentInstance();
  const el = instance?.proxy?.$el as HTMLElement | null;
  localRoot = el?.closest(".app-tabbar") as HTMLElement | null;
  applyActiveClass(activeIndex.value);
});

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
    position: relative;
    flex: 1;
    display: flex;
    flex-direction: column;
    align-items: center;
    justify-content: center;
    gap: 4rpx;
    -webkit-tap-highlight-color: rgba(0, 0, 0, 0);
    box-sizing: border-box;
  }

  /* 点击热区：a11y-button 撑满整个 item，保证 88rpx 热区与无障碍语义。
     icon/label 作为兄弟节点叠在热区之上（pointer-events: none 避免拦截点击）。 */
  &__hit {
    position: absolute;
    inset: 0;
    width: 100%;
    height: 100%;
    min-height: 0;
  }

  &__icon {
    width: 28px;
    height: 28px;
    pointer-events: none;
  }

  &__label {
    font-size: 10px;
    line-height: 1;
    color: var(--text-secondary);
    pointer-events: none;

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
  pointer-events: none;
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
