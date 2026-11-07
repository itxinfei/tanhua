<template>
  <image
    v-if="displaySrc"
    :src="displaySrc"
    :mode="mode"
    :lazy-load="lazyLoad"
    :aria-label="ariaLabel || undefined"
    :aria-hidden="ariaHidden || undefined"
    @error="onError"
    @click="emit('click', $event)"
  />
  <view
    v-else
    class="smart-image-placeholder"
    :aria-label="ariaLabel || undefined"
    :aria-hidden="ariaHidden || undefined"
    @click="emit('click', $event)"
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
  lazyLoad?: boolean;
  /** 无障碍标签；为空时不输出该属性（避免读屏读空） */
  ariaLabel?: string;
  ariaHidden?: boolean;
}

const props = withDefaults(defineProps<Props>(), {
  src: "",
  fallback: DEFAULT_AVATAR,
  mode: "aspectFill",
  lazyLoad: false,
  ariaLabel: "",
  ariaHidden: false,
});

const emit = defineEmits<{ (e: "click", event: any): void }>();

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
  background-color: var(--color-fill, #f5f5f5);
}
</style>