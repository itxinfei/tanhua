/**
 * 语音评论录音封装（uni.getRecorderManager）
 *
 * 对标设计：docs/视频页抖音对标设计.md §7.2
 * - 单条时长：最短 1 秒、最长 60 秒；不足 1 秒回调 null（提示"说话时间太短"）
 * - 格式 mp3：后端 /upload/audio 白名单允许 .mp3（H5/App 均支持）
 * - 上滑取消：由 UI 层判定手势后调用 cancel()，松手不产出文件
 * - 权限失败：onError 提示引导，不静默失败
 */

export interface RecordResult {
  /** 本地临时文件路径（可送 /upload/audio 上传） */
  filePath: string;
  /** 时长（秒，整数） */
  duration: number;
}

export interface VoiceRecorderCallbacks {
  /** 录音真正开始（权限通过、start 事件触发） */
  onStart?: () => void;
  /** 每秒回调当前已录秒数（驱动倒计时/波形 UI） */
  onTick?: (seconds: number) => void;
  /** 结束：result 为 null 表示取消或时长不足 */
  onStop?: (result: RecordResult | null) => void;
  /** 录音错误（权限被拒等），message 已是面向用户的中文文案 */
  onError?: (message: string) => void;
}

/** 单条限制（秒） */
export const VOICE_MIN_SECONDS = 1;
export const VOICE_MAX_SECONDS = 60;

export interface VoiceRecorder {
  /** 开始录音（重复调用忽略） */
  start: () => void;
  /** 正常结束并发送 */
  stop: () => void;
  /** 取消（松手不产出文件） */
  cancel: () => void;
}

/** 取录音管理器；H5 下该能力不存在（uni.getRecorderManager 会返回 undefined），返回 null */
function getRecorderManager(): UniApp.RecorderManager | null {
  try {
    const manager = typeof uni.getRecorderManager === "function" ? uni.getRecorderManager() : null;
    return manager && typeof manager.onStop === "function" ? manager : null;
  } catch {
    return null;
  }
}

export function createVoiceRecorder(callbacks: VoiceRecorderCallbacks): VoiceRecorder {
  const manager = getRecorderManager();
  if (!manager) {
    // 能力缺失时降级为空实现，避免调用方拿到 undefined 后崩溃
    return {
      start: () => callbacks.onError?.("当前环境不支持录音"),
      stop: () => {},
      cancel: () => {},
    };
  }
  let recording = false;
  let cancelled = false;
  let startedAt = 0;
  let tickTimer: ReturnType<typeof setInterval> | null = null;
  let stopBound = false;

  const clearTick = () => {
    if (tickTimer) {
      clearInterval(tickTimer);
      tickTimer = null;
    }
  };

  if (!stopBound) {
    stopBound = true;
    manager.onStart(() => {
      recording = true;
      cancelled = false;
      startedAt = Date.now();
      callbacks.onStart?.();
      tickTimer = setInterval(() => {
        const seconds = Math.floor((Date.now() - startedAt) / 1000);
        callbacks.onTick?.(seconds);
        // 达到上限自动停止并发送
        if (seconds >= VOICE_MAX_SECONDS) {
          manager.stop();
        }
      }, 250);
    });

    manager.onStop((res: any) => {
      recording = false;
      clearTick();
      const duration = Math.floor((Date.now() - startedAt) / 1000);
      if (cancelled) {
        callbacks.onStop?.(null);
        return;
      }
      if (duration < VOICE_MIN_SECONDS || !res?.tempFilePath) {
        callbacks.onStop?.(null);
        return;
      }
      callbacks.onStop?.({ filePath: res.tempFilePath, duration });
    });

    manager.onError((err: any) => {
      recording = false;
      clearTick();
      // H5 端权限拒绝/App scope.record 被拒都会走到这里
      const msg = /deny|denied|permission/i.test(String(err?.errMsg ?? err ?? ""))
        ? "需要麦克风权限，请在系统设置中允许后重试"
        : "录音失败，请重试";
      callbacks.onError?.(msg);
    });
  }

  return {
    start() {
      if (recording) return;
      cancelled = false;
      manager.start({
        duration: (VOICE_MAX_SECONDS + 1) * 1000,
        format: "mp3",
        sampleRate: 16000,
        numberOfChannels: 1,
      });
    },
    stop() {
      if (!recording) return;
      manager.stop();
    },
    cancel() {
      if (!recording) return;
      cancelled = true;
      manager.stop();
    },
  };
}

/**
 * 全局互斥语音播放器：同一时刻只播放一条语音。
 * 视频评论、聊天语音等所有语音条共用，播放新条自动停旧条。
 */
export interface VoicePlayerHandle {
  stop: () => void;
}

let currentAudio: UniApp.InnerAudioContext | null = null;
let currentUrl: string | null = null;

export function playVoice(
  url: string,
  handlers: {
    onEnded?: () => void;
    onStop?: () => void;
    /** 播放进度回调（当前秒），供语音条进度展示 */
    onProgress?: (currentSeconds: number) => void;
  }
): VoicePlayerHandle | null {
  stopVoice();
  const audio = uni.createInnerAudioContext();
  audio.src = url;
  currentAudio = audio;
  currentUrl = url;
  let ended = false;
  audio.onTimeUpdate(() => {
    handlers.onProgress?.(Math.floor(audio.currentTime || 0));
  });
  audio.onEnded(() => {
    ended = true;
    cleanup();
    handlers.onEnded?.();
  });
  audio.onStop(() => {
    if (!ended) {
      cleanup();
      handlers.onStop?.();
    }
  });
  audio.onError(() => {
    cleanup();
    handlers.onStop?.();
  });
  audio.play();

  function cleanup() {
    if (currentAudio === audio) {
      currentAudio = null;
      currentUrl = null;
    }
    try {
      audio.destroy();
    } catch {
      // 已销毁则忽略
    }
  }

  return {
    stop() {
      ended = true;
      if (currentAudio === audio) {
        currentAudio = null;
        currentUrl = null;
      }
      try {
        audio.stop();
        audio.destroy();
      } catch {
        // 忽略重复销毁
      }
      handlers.onStop?.();
    },
  };
}

/** 停止当前正在播放的语音（关闭抽屉/离开页面时调用） */
export function stopVoice(): void {
  if (currentAudio) {
    const audio = currentAudio;
    currentAudio = null;
    currentUrl = null;
    try {
      audio.stop();
      audio.destroy();
    } catch {
      // 忽略
    }
  }
  currentUrl = null;
}

/** 当前是否正在播放某条语音（语音条切换播放/暂停态用） */
export function isVoicePlaying(url: string): boolean {
  return currentUrl === url;
}
