<template>
  <!-- 网络状态指示条：断网时黄色 / 恢复时变绿 -->
  <view v-if="visible" class="network-bar" :class="barClass" @click="onClick">
    <text class="network-icon">{{ icon }}</text>
    <text class="network-text">{{ text }}</text>
  </view>
</template>

<script setup lang="ts">
import { ref, computed, onMounted, onUnmounted } from "vue";
import { isOnline } from "@/utils/network-monitor";
import { onNetworkRestored } from "@/utils/network-monitor";

const visible = ref(false);
const wasRestored = ref(false); // 刚从断网恢复

const barClass = computed(() => ({
  "bar-offline": !isOnline.value,
  "bar-restored": wasRestored.value,
}));

const icon = computed(() => (isOnline.value ? "🟢" : "🟡"));
const text = computed(() =>
  isOnline.value ? "网络已恢复" : "网络连接已断开，部分功能可能无法使用",
);

function onClick(): void {
  if (isOnline.value) {
    visible.value = false;
    wasRestored.value = false;
  }
}

let unsubscribe: (() => void) | null = null;
let hideTimer: ReturnType<typeof setTimeout> | null = null;

onMounted(() => {
  // 监听网络恢复（刚进入页面时如果在线，不显示条）
  if (!isOnline.value) {
    visible.value = true;
  }

  // 监听网络恢复事件
  unsubscribe = onNetworkRestored(() => {
    wasRestored.value = true;
    visible.value = true; // 变绿提示
    if (hideTimer) clearTimeout(hideTimer);
    hideTimer = setTimeout(() => {
      wasRestored.value = false;
      visible.value = false;
    }, 3000);
  });
});

onUnmounted(() => {
  unsubscribe?.();
  if (hideTimer) clearTimeout(hideTimer);
});
</script>

<style scoped>
.network-bar {
  position: fixed;
  top: 0;
  left: 0;
  right: 0;
  z-index: 9999;
  padding: 12rpx 24rpx;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 8rpx;
  font-size: 24rpx;
  line-height: 1.4;
  animation: slideDown 0.2s ease-out;
}
.bar-offline {
  background-color: #fff3cd;
  color: #856404;
}
.bar-restored {
  background-color: #d4edda;
  color: #155724;
}
.network-icon {
  font-size: 28rpx;
}
.network-text {
  flex: 1;
  text-align: center;
}
@keyframes slideDown {
  from { transform: translateY(-100%); }
  to { transform: translateY(0); }
}
</style>
