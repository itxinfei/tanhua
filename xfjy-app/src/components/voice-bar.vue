<template>
  <!-- 语音条：播放/暂停图标 + 时长 + 进度；宽度随时长分级，互斥播放由全局 playVoice 管理 -->
  <a11y-button
    class="voice-bar"
    :class="{ playing: isPlaying }"
    :label="isPlaying ? `暂停语音，时长 ${duration} 秒` : `播放语音，时长 ${duration} 秒`"
    @tap="toggle"
  >
    <view class="voice-icon" aria-hidden="true">
      <uni-icons :type="isPlaying ? 'sound-filled' : 'play-right-filled'" size="18" color="#fff"></uni-icons>
    </view>
    <view class="voice-wave" aria-hidden="true">
      <view
        v-for="i in barCount"
        :key="i"
        class="wave-bar"
        :class="{ active: isPlaying && i <= activeBars }"
      ></view>
    </view>
    <text class="voice-duration">{{ displaySeconds }}"</text>
  </a11y-button>
</template>

<script setup lang="ts">
import { ref, computed, onUnmounted } from "vue";
import A11yButton from "@/components/a11y-button.vue";
import { playVoice, stopVoice, type VoicePlayerHandle } from "@/utils/recorder";

const props = defineProps<{
  /** 音频地址（MinIO URL） */
  audioUrl: string;
  /** 时长（秒） */
  duration: number;
}>();

const isPlaying = ref(false);
const currentSecond = ref(0);
let handle: VoicePlayerHandle | null = null;

/** 播放中实时秒数（播放结束回退总时长） */
const displaySeconds = computed(() => (isPlaying.value ? Math.max(currentSecond.value, 1) : props.duration));

/** 波形条数量按时长分级：1-10s 8 条，10-30s 12 条，30s+ 16 条 */
const barCount = computed(() => {
  if (props.duration <= 10) return 8;
  if (props.duration <= 30) return 12;
  return 16;
});
/** 播放进度映射到已点亮的波形条数 */
const activeBars = computed(() => {
  const sec = displaySeconds.value;
  return Math.min(barCount.value, Math.max(1, Math.round((sec / props.duration) * barCount.value)));
});

function toggle() {
  if (isPlaying.value) {
    // 暂停：销毁当前播放（下次点击从头播，符合语音条短音频习惯）
    handle?.stop();
    handle = null;
    isPlaying.value = false;
    currentSecond.value = 0;
    return;
  }
  handle = playVoice(props.audioUrl, {
    onProgress: (sec) => {
      currentSecond.value = sec;
    },
    onEnded: () => {
      handle = null;
      isPlaying.value = false;
      currentSecond.value = 0;
    },
    onStop: () => {
      handle = null;
      isPlaying.value = false;
      currentSecond.value = 0;
    },
  });
  isPlaying.value = !!handle;
}

// 组件卸载（抽屉关闭/翻页）时停止播放，避免声音泄漏
onUnmounted(() => {
  handle?.stop();
  handle = null;
});

// 抽屉关闭时由父级统一调用 stopVoice 兜底
defineExpose({ stop: () => { handle?.stop(); handle = null; isPlaying.value = false; } });

// 引用避免未使用告警：stopVoice 由抽屉统一调用
void stopVoice;
</script>

<style lang="scss" scoped>
.voice-bar {
  display: inline-flex;
  align-items: center;
  gap: 12rpx;
  padding: 12rpx 24rpx;
  border-radius: 999rpx;
  background: var(--bg-sub);
  transition: background 0.2s ease;

  &.playing {
    background: var(--color-primary-soft, var(--bg-icon-soft));
  }

  .voice-icon {
    display: flex;
    align-items: center;
    justify-content: center;
  }

  .voice-wave {
    display: flex;
    align-items: flex-end;
    gap: 4rpx;
    height: 24rpx;

    .wave-bar {
      width: 4rpx;
      border-radius: 2rpx;
      background: var(--text-hint);
      height: 40%;

      &.active {
        background: var(--color-primary);
        height: 100%;
      }
    }
  }

  .voice-duration {
    font-size: 24rpx;
    color: var(--text-secondary);
  }
}
</style>
