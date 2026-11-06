<template>
  <view
    class="a11y-radio-group"
    role="radiogroup"
    :aria-label="label || null"
  >
    <view
      v-for="opt in normalizedOptions"
      :key="String(opt.value)"
      class="a11y-radio"
      :class="[optionClass, opt.cls, { active: isSelected(opt) }]"
      role="radio"
      :aria-checked="isSelected(opt) ? 'true' : 'false'"
      :aria-label="opt.label"
      :tabindex="isSelected(opt) ? 0 : -1"
      @click.stop="select(opt)"
      @keydown="handleKeydown($event, opt)"
    >
      <slot
        name="option"
        :option="opt"
        :selected="isSelected(opt)"
      >
        <text>{{ opt.label }}</text>
      </slot>
    </view>
  </view>
</template>

<script setup lang="ts">
import { computed } from "vue";

interface RadioOption {
  /** 选项值 */
  value: any;
  /** 读屏朗读的标签（必填，纯图标选项尤其需要） */
  label: string;
  /** 附加到选项根节点的额外 class（如 male / female） */
  cls?: string;
  [key: string]: any;
}

interface Props {
  /** 单选组整体标签（如「性别」「套餐」） */
  label?: string;
  /** 选项列表：声明为 readonly 以兼容调用方 as const 常量数组 */
  options: readonly RadioOption[];
  /** 当前选中值，配合 update:modelValue 使用 */
  modelValue: any;
  /** 每个选项的基础 class（通常传入原有样式类，如 gender-opt btn-feedback） */
  optionClass?: string;
  disabled?: boolean;
}

const props = withDefaults(defineProps<Props>(), {
  label: "",
  optionClass: "",
  disabled: false,
});

const emit = defineEmits<{ (e: "update:modelValue", value: any): void }>();

const normalizedOptions = computed(() => props.options || []);

function isSelected(opt: RadioOption): boolean {
  return props.modelValue === opt.value;
}

function select(opt: RadioOption): void {
  if (props.disabled || isSelected(opt)) return;
  emit("update:modelValue", opt.value);
}

/**
 * 键盘支持：Space/Enter 选中当前项。
 * 小程序端 view 不派发 keydown，静默降级。
 */
function handleKeydown(e: any, opt: RadioOption): void {
  if (props.disabled) return;
  const key: string = e?.key || "";
  const keyCode: number = e?.keyCode || 0;
  const isEnter = key === "Enter" || keyCode === 13;
  const isSpace = key === " " || key === "Spacebar" || keyCode === 32;
  if (!isEnter && !isSpace) return;
  e?.preventDefault?.();
  select(opt);
}
</script>

<style lang="scss" scoped>
.a11y-radio-group {
  display: contents;
}

.a11y-radio {
  /* 仅键盘导航显示焦点环；:focus 兜底会让鼠标点击也出框，已移除 */
  &:focus-visible {
    outline: 2px solid var(--color-primary);
    outline-offset: 2px;
  }
}
</style>
