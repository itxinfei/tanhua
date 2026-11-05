<template>
  <view
    class="a11y-btn"
    :class="[
      variant !== 'default' ? `a11y-btn--${variant}` : '',
      variant !== 'default' && size === 'sm' ? 'a11y-btn--sm' : '',
      variant !== 'default' && shape === 'default' ? 'a11y-btn--rect' : '',
      block ? 'a11y-btn--block' : '',
      disabled || loading ? 'a11y-btn--disabled' : '',
    ]"
    :role="role"
    :aria-label="label || null"
    :aria-checked="ariaChecked"
    :aria-disabled="disabled || loading ? 'true' : null"
    :aria-busy="loading ? 'true' : null"
    :tabindex="disabled ? -1 : 0"
    @click="handleTap"
    @keydown="handleKeydown"
  >
    <!-- loading 指示器：border 旋转，颜色取 currentColor 自动适配各变体 -->
    <view v-if="loading" class="a11y-btn__spinner" aria-hidden="true" />
    <slot />
  </view>
</template>

<script setup lang="ts">
import { computed } from "vue";

interface Props {
  /**
   * 语义角色，默认 button
   * checkbox / radio / tab / switch / menuitem 等需同步传 checked
   */
  role?: string;
  /** 无障碍标签：纯图标按钮（‹ ⋯ ♥ ✕）必须传，否则读屏无法识别用途 */
  label?: string;
  /** 禁用：同时阻断点击与键盘触发 */
  disabled?: boolean;
  /** 选中态：配合 checkbox / tab 等角色 */
  checked?: boolean;
  /**
   * 视觉层级：
   * default 零样式（仅行为壳，样式由外部 class 控制，存量用法默认）；
   * primary 品牌渐变主按钮；ghost 描边次按钮；text 文本按钮；danger 危险按钮
   */
  variant?: "default" | "primary" | "ghost" | "text" | "danger";
  /** 尺寸：md=88rpx 标准热区（默认），sm=56rpx（输入框内验证码等紧凑场景）；仅视觉变体生效 */
  size?: "md" | "sm";
  /** 形状：pill 胶囊（默认）/ default 24rpx 圆角矩形；仅视觉变体生效 */
  shape?: "pill" | "default";
  /** 撑满父容器宽度 */
  block?: boolean;
  /** 加载态：显示 spinner 并阻断点击（替代原生 button 的 :loading） */
  loading?: boolean;
}

const props = withDefaults(defineProps<Props>(), {
  role: "button",
  label: "",
  disabled: false,
  checked: false,
  variant: "default",
  size: "md",
  shape: "pill",
  block: false,
  loading: false,
});

const emit = defineEmits<{ (e: "tap" | "click"): void }>();

/** 需要上报选中态的角色（对应 WCAG 4.1.2 Name, Role, Value） */
const CHECKABLE_ROLES = ["checkbox", "radio", "tab", "switch", "menuitemcheckbox"];
const isCheckable = computed(() => CHECKABLE_ROLES.includes(props.role));

/** aria-checked 属性值（computed 确保响应式更新） */
const ariaChecked = computed(() => (isCheckable.value ? (props.checked ? "true" : "false") : null));

/** 统一的激活动作（点击与键盘共用），disabled/loading 时静默 */
function activate(): void {
  if (props.disabled || props.loading) return;
  emit("tap");
  emit("click");
}

function handleTap(): void {
  activate();
}

/**
 * 键盘触发：Enter(13) / Space(32)。
 * 小程序端 view 不派发 keydown，此处静默降级；H5 与 App-webview 端生效。
 */
function handleKeydown(e: any): void {
  if (props.disabled || props.loading) return;
  const key: string = e?.key || "";
  const keyCode: number = e?.keyCode || 0;
  const isEnter = key === "Enter" || keyCode === 13;
  const isSpace = key === " " || key === "Spacebar" || keyCode === 32;
  if (!isEnter && !isSpace) return;
  // 阻止 Space 滚动页面
  e?.preventDefault?.();
  activate();
}
</script>

<style lang="scss" scoped>
.a11y-btn {
  /* WCAG 2.4.7 Focus Visible：键盘聚焦必须可见 */
  &:focus-visible {
    outline: 2px solid var(--color-primary);
    outline-offset: 2rpx;
  }

  /* 不再使用 :focus 兜底：鼠标点击也会触发 :focus，导致点击出现色块边框；
     键盘可达性由 :focus-visible 保证（H5/APP-webview），小程序端无 outline 概念 */

  /* 按压反馈：组件级统一真源，所有变体共享。
     transition 只写这一处，避免各变体重复声明导致行为不一致；
     缩放幅度由 --press-scale 提供，默认变体与视觉变体共用同一机制。 */
  transition:
    transform 0.15s ease-out,
    opacity 0.12s ease-out,
    background-color 0.12s ease-out;

  &:active {
    transform: scale(var(--press-scale, 0.96));
    opacity: 0.88;
  }

  &--disabled,
  &--loading {
    cursor: not-allowed;

    /* 禁用/加载中不响应按压，避免误导用户「可以点」 */
    &:active {
      transform: none;
      opacity: 0.4;
    }
  }
}

/* ===== 视觉变体（variant !== default 时生效）：全站按钮单一真源，页面不再各写一套 ===== */
.a11y-btn--primary,
.a11y-btn--ghost,
.a11y-btn--text,
.a11y-btn--danger {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: var(--space-xs);
  box-sizing: border-box;
  /* 最小点击热区 88rpx（≈44px，WCAG 2.5.5） */
  min-height: 88rpx;
  padding: 0 var(--space-lg);
  font-size: var(--fs-base);
  font-weight: 600;
  line-height: 1.2;
  white-space: nowrap;
  border: 2rpx solid transparent;
  border-radius: var(--radius-pill);
  /* 视觉变体（有底色/描边）按压幅度略大：带底色的按钮缩放时更易被察觉。
     transition 已由 .a11y-btn 统一提供，此处只改幅度，不重复声明。 */
  --press-scale: 0.94;
}

/* 紧凑尺寸：输入框内嵌按钮等 */
.a11y-btn--sm {
  min-height: 56rpx;
  padding: 0 var(--space-md);
  font-size: var(--fs-sm);
  font-weight: 500;
}

/* 圆角矩形形状（默认是胶囊） */
.a11y-btn--rect {
  border-radius: var(--radius-lg);
}

.a11y-btn--block {
  display: flex;
  width: 100%;
}

/* 主按钮：品牌渐变填充 */
.a11y-btn--primary {
  color: var(--text-inverse);
  background: var(--gradient-brand);
  box-shadow: var(--shadow-primary);
}

/* 次按钮：主色描边 */
.a11y-btn--ghost {
  color: var(--color-primary);
  background: transparent;
  border-color: var(--color-primary);
}

/* 文本按钮：主色文字无底无边 */
.a11y-btn--text {
  color: var(--color-primary);
  background: transparent;
  box-shadow: none;
}

/* 危险按钮：危险色填充 */
.a11y-btn--danger {
  color: var(--text-inverse);
  background: var(--color-danger);
  box-shadow: 0 8rpx 24rpx rgba(229, 72, 77, 0.3);
}

/* 禁用/加载：视觉变体降透明度并取消按压反馈（default 变体样式由外部自理，不强制） */
.a11y-btn--primary.a11y-btn--disabled,
.a11y-btn--ghost.a11y-btn--disabled,
.a11y-btn--text.a11y-btn--disabled,
.a11y-btn--danger.a11y-btn--disabled {
  opacity: 0.4;

  &:active {
    transform: none;
  }
}

/* loading 转圈：currentColor 自动适配各变体文字色 */
.a11y-btn__spinner {
  width: 28rpx;
  height: 28rpx;
  border: 3rpx solid currentColor;
  border-top-color: transparent;
  border-radius: 50%;
  opacity: 0.85;
  animation: a11y-btn-spin 0.7s linear infinite;
}

.a11y-btn--sm .a11y-btn__spinner {
  width: 22rpx;
  height: 22rpx;
  border-width: 2rpx;
}

@keyframes a11y-btn-spin {
  to {
    transform: rotate(360deg);
  }
}
</style>
