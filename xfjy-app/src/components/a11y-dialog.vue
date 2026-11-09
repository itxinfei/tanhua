<template>
  <view
    v-if="visible"
    ref="rootRef"
    class="a11y-dialog"
    :class="`a11y-dialog--${placement}`"
    role="dialog"
    aria-modal="true"
    :aria-label="title || null"
    tabindex="-1"
    @click="handleMaskTap"
    @keydown="handleKeydown"
    @touchmove.stop
  >
    <view
      class="a11y-dialog__panel"
      @click.stop
    >
      <slot />
    </view>
  </view>
</template>

<script setup lang="ts">
import { ref, watch, nextTick } from "vue";

interface Props {
  visible?: boolean;
  /** 对话框无障碍标题（读屏在进入弹窗时朗读） */
  title?: string;
  /** 点击遮罩是否关闭，默认关闭 */
  maskClosable?: boolean;
  /**
   * 面板在遮罩中的对齐方式：
   *  - center（默认）：居中弹窗
   *  - bottom：底部抽屉（面板贴底、顶部圆角）
   */
  placement?: "center" | "bottom";
}

const props = withDefaults(defineProps<Props>(), {
  visible: false,
  title: "",
  maskClosable: true,
  placement: "center",
});

const emit = defineEmits<{ (e: "close"): void }>();

const rootRef = ref<any>(null);
/** 触发弹窗的上一个焦点元素，关闭后用于归还焦点（WCAG 2.4.3 Focus Order） */
let lastFocused: any = null;

/** 平台降级：小程序无 DOM，所有原生焦点操作必须可失败 */
function safeFocus(el: any): void {
  try {
    el?.focus?.();
  } catch {
    // 不支持 focus 的平台静默降级
  }
}

function resolveEl(): any {
  try {
    return rootRef.value?.$el || rootRef.value || null;
  } catch {
    return null;
  }
}

watch(
  () => props.visible,
  async (visible) => {
    await nextTick();
    if (visible) {
      try {
        lastFocused = document?.activeElement || null;
      } catch {
        lastFocused = null;
      }
      // 打开时把焦点移入对话框，保证读屏立即朗读且键盘落点在弹窗内
      safeFocus(resolveEl());
    } else {
      // 关闭时焦点归还触发元素
      safeFocus(lastFocused);
      lastFocused = null;
    }
  }
);

function close(): void {
  emit("close");
}

function handleMaskTap(): void {
  if (!props.maskClosable) return;
  close();
}

/** 弹窗内可聚焦元素选择器：含 a11y-button 渲染的 view[tabindex] */
const FOCUSABLE_SELECTOR =
  'a[href], button:not([disabled]), input:not([disabled]), select:not([disabled]), textarea:not([disabled]), [tabindex]:not([tabindex="-1"])';

/** 收集弹窗内当前可见的可聚焦元素；无 DOM 环境（小程序）返回空数组静默降级 */
function getFocusableEls(): any[] {
  const root = resolveEl();
  if (!root?.querySelectorAll) return [];
  try {
    return Array.from(root.querySelectorAll(FOCUSABLE_SELECTOR)).filter(
      (el: any) => el.getClientRects?.().length > 0,
    );
  } catch {
    return [];
  }
}

/**
 * 键盘交互（H5 / App-webview 端生效，小程序不派发 keydown 自动降级）：
 * Esc 关闭；Tab / Shift+Tab 焦点圈定在弹窗内循环（WAI-ARIA dialog focus trap）
 */
function handleKeydown(e: any): void {
  const key: string = e?.key || "";
  const keyCode: number = e?.keyCode || 0;
  if (key === "Escape" || key === "Esc" || keyCode === 27) {
    e?.stopPropagation?.();
    close();
    return;
  }
  if (key !== "Tab" && keyCode !== 9) return;
  const root = resolveEl();
  const items = getFocusableEls();
  if (items.length === 0) {
    // 弹窗内无可聚焦控件时，焦点钉在弹窗容器本身，避免 Tab 跑到背景页
    e?.preventDefault?.();
    safeFocus(root);
    return;
  }
  const first = items[0];
  const last = items[items.length - 1];
  const active = typeof document !== "undefined" ? document.activeElement : null;
  const inside = !!root?.contains?.(active);
  if (e?.shiftKey) {
    if (!inside || active === first) {
      e?.preventDefault?.();
      safeFocus(last);
    }
  } else if (!inside || active === last) {
    e?.preventDefault?.();
    safeFocus(first);
  }
}
</script>

<style lang="scss" scoped>
/* 遮罩与定位收在组件内：调用方不再需要各自补 position/inset/background/z-index，
   避免新调用方漏传 class 时弹窗原地行内展开、role=dialog 语义与渲染不符。
   placement 只切换对齐轴，不重复遮罩声明。 */
.a11y-dialog {
  position: fixed;
  inset: 0;
  z-index: 999;
  display: flex;
  background: rgba(0, 0, 0, 0.6);
  box-sizing: border-box;

  &--center {
    align-items: center;
    justify-content: center;
  }

  &--bottom {
    align-items: flex-end;
    justify-content: stretch;
  }

  &:focus-visible,
  &:focus {
    outline: none;
  }
}

/* 占满遮罩宽度：保证调用方的面板 width:100% 仍能正确解析 */
.a11y-dialog__panel {
  width: 100%;
}
</style>
