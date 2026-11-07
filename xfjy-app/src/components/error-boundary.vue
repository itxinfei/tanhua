<template>
  <!-- 错误边界：捕获子组件渲染异常，防止白屏 -->
  <!-- MP 端不支持 <slot v-if>，须用 <template> 包裹（H5 两种写法均可） -->
  <template v-if="!hasError">
    <slot />
  </template>
  <view
    v-else
    class="error-boundary"
  >
    <!-- 图标统一 UnoCSS Icons + Phosphor（AGENTS.md §7.1）：mask + currentColor，尺寸由 font-size 控制 -->
    <text
      class="i-ph-warning-circle error-icon"
      aria-hidden="true"
    />
    <text class="error-title">
      {{ displayTitle }}
    </text>
    <text class="error-message">
      {{ displayMessage }}
    </text>
    <view class="error-actions">
      <a11y-button
        variant="primary"
        label="重新加载"
        @tap.stop="onRetry"
      >
        <text>重新加载</text>
      </a11y-button>
      <a11y-button
        variant="ghost"
        label="反馈问题"
        @tap.stop="onReport"
      >
        <text>反馈问题</text>
      </a11y-button>
    </view>
    <a11y-button
      v-if="traceId"
      class="trace-btn"
      label="复制错误码"
      @tap.stop="copyTraceId"
    >
      <text class="trace-id">
        Trace: {{ traceId }}（点击复制错误码）
      </text>
    </a11y-button>
  </view>
</template>

<script setup lang="ts">
import { ref, onErrorCaptured, computed, onUnmounted } from "vue";
import { info } from "@/utils/ux";
import { reportError } from "@/utils/logger";

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

/**
 * 事件契约：
 * - error：捕获到渲染异常时上报，父级可用于埋点/日志
 * - retry：用户点「重新加载」或 autoRetry 倒计时结束时触发。
 *   父级必须监听 @retry 并重新拉取数据；组件自身不掌握父级数据源、无法自行重取，
 *   否则只会清除错误态（hasError=false），错误页消失但数据不会刷新，子组件仍无内容可渲染
 */
const emit = defineEmits<{
  (e: "error", err: unknown): void;
  (e: "retry"): void;
}>();

const hasError = ref(false);
const errorInfo = ref<unknown>(null);
const traceId = ref("");

/** autoRetry 定时器句柄：组件卸载时必须清理，避免销毁后仍触发 onRetry */
let retryTimer: ReturnType<typeof setTimeout> | null = null;

function clearRetryTimer(): void {
  if (retryTimer !== null) {
    clearTimeout(retryTimer);
    retryTimer = null;
  }
}

// 卸载清理：防止 3s 倒计时未结束就离开页面后，定时器在组件外触发重试
onUnmounted(clearRetryTimer);

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
  reportError("[ErrorBoundary]", err);

  // 生成 traceId（未来可接 Sentry / 自建上报）
  try {
    traceId.value = `ERR-${Date.now().toString(36).toUpperCase()}`;
  } catch {
    // Date.now() 与 Number.toString() 按 ECMA 规范不会抛错，此 catch 仅为防御性兜底，无异常需要上报告警
  }

  emit("error", err);

  if (props.autoRetry) {
    // 先清旧句柄：防御异常被多次捕获时叠加出多个定时器
    clearRetryTimer();
    retryTimer = setTimeout(() => {
      retryTimer = null;
      onRetry();
    }, 3000);
  }

  return false; // 阻止向上传播
});

/**
 * 清除错误态并通知父级重试。
 * 契约：组件无法自行重取父级数据，父级必须监听 @retry 重新拉取，
 * 否则错误态消失但页面数据不会刷新。
 */
function onRetry(): void {
  // 手动重试时取消尚未触发的 autoRetry 倒计时，避免重复重试
  clearRetryTimer();
  hasError.value = false;
  errorInfo.value = null;
  emit("retry");
}

function onReport(): void {
  // 三级兜底链路：客服会话页 → 客服首页（两者均为普通页，switchTab 跳普通页必失败，禁用）
  // → reLaunch 匹配首页（tabBar 页，最后兜底，清空页面栈保证用户不被困在错误页）
  uni.navigateTo({
    url: "/pages/kefu/chat",
    fail: () => {
      uni.navigateTo({
        url: "/pages/kefu/index",
        fail: () => {
          uni.reLaunch({ url: "/pages/match/index" });
        },
      });
    },
  });
}

function copyTraceId(): void {
  if (!traceId.value) return;
  uni.setClipboardData({
    data: traceId.value,
    success: () => {
      info("错误码已复制");
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
  /* 预警语义色，明暗双模式 token，禁止写死 hex */
  color: var(--warning-text);
}
.error-title {
  font-size: 36rpx;
  font-weight: 600;
  color: var(--text-primary);
}
.error-message {
  font-size: 26rpx;
  color: var(--text-hint-weak);
  text-align: center;
  line-height: 1.6;
}
.error-actions {
  display: flex;
  gap: 24rpx;
  margin-top: 16rpx;
}
/* Trace 行原为 text @click，无最小热区且无读屏入口 */
.trace-btn {
  margin-top: 16rpx;
}
.trace-id {
  font-size: 22rpx;
  color: var(--text-hint-weak);
  font-family: monospace;
}
</style>
