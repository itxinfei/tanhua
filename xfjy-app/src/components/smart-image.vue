<template>
  <image
    v-if="displaySrc"
    :src="displaySrc"
    :mode="mode"
    :lazy-load="lazyLoad"
    :aria-label="ariaLabel || undefined"
    :aria-hidden="ariaHidden || undefined"
    :role="clickable ? 'button' : undefined"
    :tabindex="clickable ? 0 : undefined"
    @error="onError"
    @click="emit('click', $event)"
    @keydown="onKeydown"
  />
  <view
    v-else
    class="smart-image-placeholder"
    :aria-label="ariaLabel || undefined"
    :aria-hidden="ariaHidden || undefined"
    :role="clickable ? 'button' : undefined"
    :tabindex="clickable ? 0 : undefined"
    @click="emit('click', $event)"
    @keydown="onKeydown"
  />
</template>

<script setup lang="ts">
/**
 * 带加载失败兜底的图片组件（S4）。
 * 全站 <image> 无 @error 兜底：MinIO URL 失效即裂图/黑屏，匹配卡片图片是唯一视觉主体。
 * 统一封装：@error 切 fallback（默认头像 logo），fallback 再失败不再循环。
 * class 经 $attrs 自动透传到根 image，替换时直接把 <image class="x"> 改 <smart-image class="x">。
 */
import { ref, watch } from "vue";
import { DEFAULT_AVATAR } from "@/utils/avatar";

interface Props {
  /** 图片地址（空值直接走 fallback） */
  src?: string;
  /** 加载失败兜底图（默认头像 logo） */
  fallback?: string;
  /** 裁剪模式，默认 aspectFill */
  mode?: string;
  /** 图片懒加载，默认开启（H5 映射 loading=lazy，视口内图片仍立即加载；首屏关键图可显式 :lazy-load="false"） */
  lazyLoad?: boolean;
  /** 无障碍标签；为空时不输出该属性（避免读屏读空） */
  ariaLabel?: string;
  ariaHidden?: boolean;
  /**
   * 是否可点击（预览大图/进详情等）。
   * 仅可点击图片输出 role=button + tabindex 并支持键盘激活，纯展示图片默认 false，
   * 避免把全站装饰性图片污染进键盘 Tab 序列与读屏无障碍树（WCAG 2.1.1）。
   */
  clickable?: boolean;
}

const props = withDefaults(defineProps<Props>(), {
  src: "",
  fallback: DEFAULT_AVATAR,
  mode: "aspectFill",
  lazyLoad: true,
  ariaLabel: "",
  ariaHidden: false,
  clickable: false,
});

const emit = defineEmits<{ (e: "click", event: any): void }>();

/**
 * 键盘激活：Enter(13) / Space(32) 等价点击。
 * 小程序端 view/image 不派发 keydown，静默降级；H5 与 App-webview 生效（同 a11y-button）。
 */
function onKeydown(e: any): void {
  if (!props.clickable) return;
  const key: string = e?.key || "";
  const keyCode: number = e?.keyCode || 0;
  const isEnter = key === "Enter" || keyCode === 13;
  const isSpace = key === " " || key === "Spacebar" || keyCode === 32;
  if (!isEnter && !isSpace) return;
  e?.preventDefault?.();
  emit("click", e);
}

/** 当前展示地址：src 优先，失败切 fallback */
const displaySrc = ref(props.src || props.fallback);
/** 是否已切到 fallback（防 fallback 再失败时回切 src 死循环） */
let usingFallback = !props.src;

watch(
  () => props.src,
  (v) => {
    displaySrc.value = v || props.fallback;
    usingFallback = !v;
  },
);

function onError(): void {
  if (!usingFallback) {
    usingFallback = true;
    displaySrc.value = props.fallback;
  } else {
    // fallback 也加载失败：置空走占位底色块，避免反复重试
    displaySrc.value = "";
  }
}
</script>

<style scoped>
.smart-image-placeholder {
  width: 100%;
  height: 100%;
  /* --color-fill 在 token 中不存在，实际恒为 #f5f5f5；暗色下是刺眼亮灰块。
     改用 --bg-sub（亮 #f5f5f5 / 暗 #262626） */
  background-color: var(--bg-sub);
}
</style>