<template>
  <view
    class="a11y-btn"
    :class="{ 'a11y-btn--disabled': disabled }"
    :role="role"
    :aria-label="label || null"
    :aria-checked="isCheckable ? (checked ? 'true' : 'false') : null"
    :aria-disabled="disabled ? 'true' : null"
    :tabindex="disabled ? -1 : 0"
    @click.stop="handleTap"
    @keydown="handleKeydown"
  >
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
}

const props = withDefaults(defineProps<Props>(), {
  role: "button",
  label: "",
  disabled: false,
  checked: false,
});

const emit = defineEmits<{ (e: "tap"): void }>();

/** 需要上报选中态的角色（对应 WCAG 4.1.2 Name, Role, Value） */
const CHECKABLE_ROLES = ["checkbox", "radio", "tab", "switch", "menuitemcheckbox"];
const isCheckable = computed(() => CHECKABLE_ROLES.includes(props.role));

/** 统一的激活动作（点击与键盘共用），disabled 时静默 */
function activate(): void {
  if (props.disabled) return;
  emit("tap");
}

function handleTap(): void {
  activate();
}

/**
 * 键盘触发：Enter(13) / Space(32)。
 * 小程序端 view 不派发 keydown，此处静默降级；H5 与 App-webview 端生效。
 */
function handleKeydown(e: any): void {
  if (props.disabled) return;
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


.a11y-btn {
  /* WCAG 2.4.7 Focus Visible：键盘聚焦必须可见 */
  &:focus-visible {
    outline: 2px solid var(--color-primary);
    outline-offset: 2px;
  }

  /* 小程序端不支持 focus-visible，用 :focus 兜底 */
  &:focus {
    outline: 2px solid var(--color-primary);
    outline-offset: 2px;
  }

  &--disabled {
    cursor: not-allowed;
  }
}

