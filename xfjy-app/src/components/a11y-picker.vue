<template>
  <view
    v-if="visible"
    class="picker-mask"
    @click="handleCancel"
    @touchmove.stop.prevent
  >
    <view
      class="picker-panel"
      @click.stop
    >
      <!-- 标题栏：按钮必须用 a11y-button（§7.1），default 变体承载自定义样式 -->
      <view class="picker-header">
        <a11y-button
          class="picker-btn picker-cancel"
          label="取消选择"
          @tap="handleCancel"
        >
          <text>取消</text>
        </a11y-button>
        <view class="picker-title">
          {{ title }}
        </view>
        <a11y-button
          class="picker-btn picker-confirm"
          label="完成选择"
          @tap="handleConfirm"
        >
          <text>完成</text>
        </a11y-button>
      </view>

      <!-- 选项列表 -->
      <scroll-view
        scroll-y
        class="picker-list"
        :show-scrollbar="false"
      >
        <!-- 手动输入选项（仅 showInput 时显示）：a11y-button 承载点击与读屏 -->
        <a11y-button
          v-if="showInput"
          class="picker-item"
          :class="{ active: manualMode }"
          :label="manualMode ? `手动输入（已选中），点击切换` : '手动输入，点击切换'"
          @tap="startManualInput"
        >
          <text class="picker-item-label">
            手动输入
          </text>
          <text
            v-if="manualMode"
            class="picker-check"
          >
            ✓
          </text>
        </a11y-button>

        <!-- 选项 -->
        <a11y-button
          v-for="opt in normalizedOptions"
          :key="String(opt.value)"
          class="picker-item"
          :class="{ active: !manualMode && tempValue === opt.value }"
          :label="!manualMode && tempValue === opt.value ? `${opt.label}（已选中）` : opt.label"
          @tap="selectOption(opt)"
        >
          <text class="picker-item-label">
            {{ opt.label }}
          </text>
          <text
            v-if="!manualMode && tempValue === opt.value"
            class="picker-check"
          >
            ✓
          </text>
        </a11y-button>
      </scroll-view>

      <!-- 手动输入区域 -->
      <view
        v-if="manualMode && showInput"
        class="manual-input-area"
      >
        <input
          ref="manualInputRef"
          v-model="manualValue"
          class="manual-input"
          :placeholder="inputPlaceholder"
          :maxlength="maxlength"
          confirm-type="done"
          aria-label="手动输入内容"
          @confirm="handleConfirm"
        >
        <a11y-button
          v-if="manualValue"
          class="manual-clear"
          label="清除输入内容"
          @tap="manualValue = ''"
        >
          <text>×</text>
        </a11y-button>
      </view>
    </view>
  </view>
</template>

<script setup lang="ts">
import { ref, computed, watch, nextTick } from "vue";
import A11yButton from "@/components/a11y-button.vue";

interface Options {
  value: string;
  label: string;
}

const props = withDefaults(
  defineProps<{
    visible: boolean;
    title?: string;
    options?: readonly Options[];
    modelValue?: string;
    /** 是否显示"手动输入"选项 */
    showInput?: boolean;
    /** 手动输入占位文本 */
    inputPlaceholder?: string;
    /** 手动输入最大长度 */
    maxlength?: number;
  }>(),
  {
    title: "请选择",
    options: () => [],
    modelValue: "",
    showInput: false,
    inputPlaceholder: "",
    maxlength: 20,
  }
);

const emit = defineEmits<{
  (e: "update:modelValue", value: string): void;
  (e: "update:visible", value: boolean): void;
  (e: "confirm", value: string): void;
  (e: "cancel"): void;
}>();

const normalizedOptions = computed(() => props.options || []);

/** 手动输入框 ref：进入手动模式后自动聚焦（#72） */
const manualInputRef = ref<HTMLInputElement | null>(null);

/** 临时选中值（未确认前不影响外部） */
const tempValue = ref("");
/** 是否处于手动输入模式 */
const manualMode = ref(false);
/** 手动输入的值 */
const manualValue = ref("");

/** 同步外部 value 到内部 temp */
function syncFromProp(): void {
  const v = props.modelValue || "";
  // 检查当前值是否匹配已知选项
  const matched = normalizedOptions.value.some((o) => o.value === v);
  if (matched || !v) {
    tempValue.value = v;
    manualMode.value = false;
    manualValue.value = "";
  } else {
    // 当前值不在选项中，进入手动输入模式
    manualMode.value = true;
    manualValue.value = v;
    tempValue.value = "";
  }
}

watch(
  () => props.visible,
  async (v) => {
    if (v) {
      syncFromProp();
      // 下次 tick 后如果进入手动模式，聚焦 input（#72）
      await nextTick();
      if (manualMode.value && props.showInput) {
        manualInputRef.value?.focus();
      }
    }
  }
);

function selectOption(opt: Options): void {
  manualMode.value = false;
  tempValue.value = opt.value;
  // 单选模式：点击即视为选中，用户需点"完成"确认
}

function startManualInput(): void {
  manualMode.value = true;
  tempValue.value = "";
  if (!manualValue.value) {
    manualValue.value = props.modelValue || "";
  }
  // 切到手动模式后聚焦输入框，省一次点击（#72）
  nextTick(() => manualInputRef.value?.focus());
}

function handleConfirm(): void {
  let finalValue = "";
  if (manualMode.value && props.showInput) {
    finalValue = manualValue.value.trim();
    if (!finalValue) {
      // 无内容，不改变
      emit("update:visible", false);
      emit("cancel");
      return;
    }
  } else {
    finalValue = tempValue.value;
  }
  // 未实际变更则不触发保存
  if (finalValue === (props.modelValue || "")) {
    emit("update:visible", false);
    emit("cancel");
    return;
  }
  emit("update:modelValue", finalValue);
  emit("confirm", finalValue);
  emit("update:visible", false);
}

function handleCancel(): void {
  emit("update:visible", false);
  emit("cancel");
}

/** 暴露给父组件：供 opened 事件后读取初始值 */
defineExpose({ syncFromProp });
</script>

<style lang="scss" scoped>
.picker-mask {
  position: fixed;
  inset: 0;
  background: rgba(0, 0, 0, 0.45);
  z-index: 1000;
  display: flex;
  align-items: flex-end;
  animation: fade-in 0.2s ease;
}

.picker-panel {
  width: 100%;
  background: var(--bg-card);
  border-radius: 24rpx 24rpx 0 0;
  overflow: hidden;
  animation: slide-up 0.25s cubic-bezier(0.22, 1, 0.36, 1);
}

@keyframes fade-in {
  from { opacity: 0; }
  to { opacity: 1; }
}

@keyframes slide-up {
  from { transform: translateY(100%); }
  to { transform: translateY(0); }
}

/* ===== 标题栏 ===== */
.picker-header {
  display: flex;
  align-items: center;
  padding: 28rpx 32rpx;
  border-bottom: 1rpx solid var(--border-faint);
  position: relative;
}

.picker-title {
  flex: 1;
  text-align: center;
  font-size: 32rpx;
  font-weight: 600;
  color: var(--text-primary);
}

/* a11y-button default 变体已保底 min-height: 88rpx；这里只覆写视觉（字号/颜色/内距）。
   标题栏不需要 88rpx 高，故覆写 min-height 让按钮跟随标题栏高度，热区由 padding 撑出。 */
.picker-btn {
  font-size: 28rpx;
  padding: 8rpx 16rpx;
  min-width: 80rpx;
  min-height: 88rpx;
  text-align: center;
  border-radius: 8rpx;
}

.picker-cancel {
  color: var(--text-secondary);
}

.picker-confirm {
  color: var(--color-primary);
  font-weight: 600;
}

/* ===== 列表 ===== */
.picker-list {
  max-height: 500rpx;
  min-height: 200rpx;
}

/* a11y-button default 变体是 inline-flex + justify-content:center；
   选项行需撑满宽度且左对齐，覆写 display/justify-content。热区由组件保底 88rpx。 */
.picker-item {
  display: flex;
  width: 100%;
  justify-content: flex-start;
  align-items: center;
  padding: 28rpx 32rpx;
  border-bottom: 1rpx solid var(--border-faint);
  transition: background 0.15s;
  min-height: 88rpx;

  &:active {
    background: var(--bg-sub);
  }

  &:last-child {
    border-bottom: none;
  }

  &.active {
    background: rgba(var(--color-primary-rgb), 0.05);
  }
}

.picker-item-label {
  flex: 1;
  font-size: 30rpx;
  color: var(--text-primary);

  .active & {
    color: var(--color-primary);
    font-weight: 500;
  }
}

.picker-check {
  color: var(--color-primary);
  font-size: 32rpx;
  font-weight: 700;
}

/* ===== 手动输入区 ===== */
.manual-input-area {
  display: flex;
  align-items: center;
  padding: 20rpx 32rpx 32rpx;
  border-top: 1rpx solid var(--border-faint);
  position: relative;
}

.manual-input {
  flex: 1;
  /* 热区 ≥88rpx（44px）：原 80rpx 低于基线，提至 88 */
  height: 88rpx;
  background: var(--bg-sub);
  border-radius: 12rpx;
  /* 右内距给清除键留空间：清除键 right:32rpx + width:48rpx = 80rpx */
  padding: 0 88rpx 0 24rpx;
  font-size: 28rpx;
  color: var(--text-primary);
  border: 2rpx solid var(--border);
}

.manual-input:focus {
  border-color: var(--color-primary);
}

/* 清除键：视觉仍 48rpx 圆形，但热区由 a11y-button default 变体保底 88rpx（WCAG 2.5.5）。
   用 padding 把可点击区扩到 88rpx，视觉保持小圆。 */
.manual-clear {
  position: absolute;
  right: 32rpx;
  width: 48rpx;
  height: 48rpx;
  display: flex;
  align-items: center;
  justify-content: center;
  border-radius: 50%;
  background: var(--border);
  font-size: 28rpx;
  color: var(--text-inverse);
  /* 扩大热区：88rpx - 48rpx = 40rpx，上下左右各 10rpx */
  padding: 10rpx;
}
</style>
