<template>
  <!-- 错误边界：捕获子组件渲染异常，防止白屏 -->
  <slot v-if="!hasError"></slot>
  <view v-else class="error-boundary" @click="onReport">
    <text class="error-icon">⚠️</text>
    <text class="error-title">{{ displayTitle }}</text>
    <text class="error-message">{{ displayMessage }}</text>
    <view class="error-actions">
      <button class="btn btn-primary" @click.stop="onRetry">重新加载</button>
      <button class="btn btn-secondary" @click.stop="onReport">反馈问题</button>
    </view>
    <text v-if="traceId" class="trace-id" @click.stop="copyTraceId">
      Trace: {{ traceId }} (长按复制)
    </text>
  </view>
</template>

<script setup lang="ts">
import { ref, onErrorCaptured, computed } from "vue";
import { useUserStore } from "@/store/user";

interface Props {
  /** 是否生产环境（控制是否显示详细错误） */
  production?: boolean;
  /** 自定义标题 */
  title?: string;
  /** 错误时是否自动重试（默认 false） */
  autoRetry?: boolean;
}

const props = withDefaults(defineProps<Props>(), {
  production: import.meta.env.PROD,
  title: "页面加载异常",
  autoRetry: false,
});

const emit = defineEmits<{
  (e: "error", err: unknown): void;
  (e: "retry"): void;
}>();

const hasError = ref(false);
const errorInfo = ref<unknown>(null);
const traceId = ref("");

const displayTitle = computed(() => props.title);
const displayMessage = computed(() => {
  if (props.production) {
    return "请稍后再试，或点击反馈问题";
  }
  const err = errorInfo.value;
  if (err instanceof Error) return err.message;
  return String(err || "未知错误");
});

onErrorCaptured((err) => {
  hasError.value = true;
  errorInfo.value = err;
  console.error("[ErrorBoundary]", err);

  // 生成 traceId（未来可接 Sentry / 自建上报）
  try {
    traceId.value = `ERR-${Date.now().toString(36).toUpperCase()}`;
  } catch {
    // ignore
  }

  emit("error", err);

  if (props.autoRetry) {
    setTimeout(() => onRetry(), 3000);
  }

  return false; // 阻止向上传播
});

function onRetry(): void {
  hasError.value = false;
  errorInfo.value = null;
  emit("retry");
}

function onReport(): void {
  // 跳转到客服页
  uni.navigateTo({
    url: "/pages/kefu/chat",
    fail: () => {
      uni.switchTab({ url: "/pages/kefu/index" });
    },
  });
}

function copyTraceId(): void {
  if (!traceId.value) return;
  uni.setClipboardData({
    data: traceId.value,
    success: () => {
      uni.showToast({ title: "已复制错误码", icon: "none" });
    },
  });
}
</script>

<style scoped>
.error-boundary {
  min-height: 60vh;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 60rpx 40rpx;
  gap: 24rpx;
}
.error-icon {
  font-size: 96rpx;
  line-height: 1;
}
.error-title {
  font-size: 36rpx;
  font-weight: 600;
  color: #333;
}
.error-message {
  font-size: 26rpx;
  color: #888;
  text-align: center;
  line-height: 1.6;
}
.error-actions {
  display: flex;
  gap: 24rpx;
  margin-top: 16rpx;
}
.btn {
  padding: 16rpx 48rpx;
  font-size: 28rpx;
  border-radius: 40rpx;
  border: none;
}
.btn-primary {
  background: linear-gradient(135deg, #ff6b9d, #ff8a80);
  color: #fff;
}
.btn-secondary {
  background: #f5f5f5;
  color: #666;
}
.trace-id {
  font-size: 22rpx;
  color: #bbb;
  margin-top: 16rpx;
  font-family: monospace;
}
</style>
