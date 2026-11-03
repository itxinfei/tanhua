<template>
  <view
    v-if="visible"
    class="picker-mask"
    @click="handleCancel"
    @touchmove.stop.prevent
  >
    <view class="picker-panel" @click.stop>
      <!-- 标题栏 -->
      <view class="picker-header">
        <view class="picker-btn picker-cancel" @click="handleCancel">取消</view>
        <view class="picker-title">{{ title }}</view>
        <view class="picker-btn picker-confirm" @click="handleConfirm">完成</view>
      </view>

      <!-- 选项列表 -->
      <scroll-view scroll-y class="picker-list" :show-scrollbar="false">
        <!-- 手动输入选项（仅 showInput 时显示） -->
        <view
          v-if="showInput"
          class="picker-item"
          :class="{ active: manualMode }"
          @click="startManualInput"
        >
          <text class="picker-item-label">手动输入</text>
          <text v-if="manualMode" class="picker-check">✓</text>
        </view>

        <!-- 选项 -->
        <view
          v-for="opt in normalizedOptions"
          :key="String(opt.value)"
          class="picker-item"
          :class="{ active: !manualMode && tempValue === opt.value }"
          @click="selectOption(opt)"
        >
          <text class="picker-item-label">{{ opt.label }}</text>
          <text v-if="!manualMode && tempValue === opt.value" class="picker-check">✓</text>
        </view>
      </scroll-view>

      <!-- 手动输入区域 -->
      <view v-if="manualMode && showInput" class="manual-input-area">
        <input
          v-model="manualValue"
          class="manual-input"
          :placeholder="inputPlaceholder"
          :maxlength="maxlength"
          confirm-type="done"
          @confirm="handleConfirm"
        />
        <view class="manual-clear" v-if="manualValue" @click="manualValue = ''">×</view>
      </view>
    </view>
  </view>
</template>

<script setup lang="ts">
import { ref, computed, watch, nextTick } from "vue";

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
      // 下次 tick 后如果进入手动模式，聚焦 input
      await nextTick();
      if (manualMode.value && props.showInput) {
        // uni-app 小程序端 input focus 通过 ref 触发；H5 自动聚焦
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

.picker-btn {
  font-size: 28rpx;
  padding: 8rpx 16rpx;
  min-width: 80rpx;
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

.picker-item {
  display: flex;
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
    background: rgba(254, 60, 114, 0.05);
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
  height: 80rpx;
  background: var(--bg-sub);
  border-radius: 12rpx;
  padding: 0 60rpx 0 24rpx;
  font-size: 28rpx;
  color: var(--text-primary);
  border: 2rpx solid var(--border);
}

.manual-input:focus {
  border-color: var(--color-primary);
}

.manual-clear {
  position: absolute;
  right: 48rpx;
  width: 48rpx;
  height: 48rpx;
  display: flex;
  align-items: center;
  justify-content: center;
  border-radius: 50%;
  background: var(--border);
  font-size: 28rpx;
  color: var(--text-inverse);
}
</style>
