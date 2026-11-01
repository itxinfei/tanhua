<template>
  <!-- 品牌按钮：primary 渐变 / secondary 浅底 / ghost 描边；支持胶囊与禁用态 -->
  <view
    class="xf-btn"
    :class="[
      `xf-btn--${type}`,
      `xf-btn--${shape}`,
      { 'xf-btn--block': block, 'xf-btn--disabled': disabled },
    ]"
    role="button"
    :aria-label="label || null"
    :aria-disabled="disabled ? 'true' : null"
    @click.stop="handleTap"
  >
    <slot />
  </view>
</template>

<script setup lang="ts">
const props = withDefaults(
  defineProps<{
    /** 视觉类型：primary 渐变主按钮 / secondary 浅底 / ghost 描边 */
    type?: "primary" | "secondary" | "ghost";
    /** 形状：default 圆角 / pill 胶囊 */
    shape?: "default" | "pill";
    /** 是否撑满父容器宽度 */
    block?: boolean;
    /** 禁用态 */
    disabled?: boolean;
    /** 无障碍标签（图标按钮建议传） */
    label?: string;
  }>(),
  {
    type: "primary",
    shape: "default",
    block: false,
    disabled: false,
    label: "",
  }
);

const emit = defineEmits<{ (e: "tap"): void }>();

function handleTap(): void {
  if (props.disabled) return;
  emit("tap");
}
</script>

<style lang="scss" scoped>
.xf-btn {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  height: 88rpx;
  padding: 0 var(--space-lg);
  font-size: var(--fs-base);
  font-weight: 600;
  border-radius: var(--radius-lg);
  box-sizing: border-box;
  transition: transform 0.15s ease, opacity 0.15s ease;

  &:active {
    transform: scale(0.97);
  }

  &:focus-visible {
    outline: 2px solid var(--color-primary);
    outline-offset: 2px;
  }

  &--pill {
    border-radius: var(--radius-pill);
  }

  &--block {
    display: flex;
    width: 100%;
  }

  &--primary {
    color: var(--text-inverse);
    background: var(--gradient-brand);
    box-shadow: var(--shadow-primary);
  }

  &--secondary {
    color: var(--color-primary);
    background: var(--bg-icon-soft);
  }

  &--ghost {
    color: var(--color-primary);
    background: transparent;
    border: 2rpx solid var(--color-primary);
  }

  &--disabled {
    opacity: 0.4;

    &:active {
      transform: none;
    }
  }
}
</style>
