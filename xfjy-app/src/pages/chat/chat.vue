<template>
  <view class="chat-room">
    <!-- 消息列表 -->
    <scroll-view
      scroll-y
      class="msg-list"
      :scroll-into-view="scrollToId"
      :scroll-with-animation="true"
    >
      <!-- 对方正在输入提示（点击可打开对方资料页） -->
      <view
        v-if="peerTyping"
        class="typing-indicator"
      >
        <a11y-button
          class="typing-btn"
          :label="`${nickname} 正在输入，点击查看资料`"
          @tap="openPeerProfile"
        >
          <image
            lazy-load
            class="typing-avatar"
            :src="resolveAvatar(peerAvatar)"
            mode="aspectFill"
            aria-hidden="true"
          />
          <view class="typing-bubble">
            <view
              class="typing-dots"
              aria-hidden="true"
            >
              <text class="dot" /><text class="dot" /><text class="dot" />
            </view>
          </view>
          <text class="typing-text">
            {{ nickname }} 正在输入…
          </text>
        </a11y-button>
      </view>
      <template
        v-for="(msg, i) in messageList"
        :key="msg.id"
      >
        <!-- 时间分隔线：首条或间隔超过阈值时显示 -->
        <view
          v-if="showTimeDivider(i)"
          class="time-divider"
        >
          <text class="time-text">
            {{ formatChatTime(msg.timestamp) }}
          </text>
        </view>
        <!-- 单条消息 -->
        <view
          :id="'msg-' + msg.id"
          class="msg-item"
          :class="{ mine: msg.isMine }"
        >
          <!-- 对方头像：点击打开对方资料页（用户想查看对方资料） -->
          <a11y-button
            v-if="!msg.isMine"
            class="avatar-btn"
            :label="`查看 ${nickname} 的资料`"
            @tap="openPeerProfile"
          >
            <image
              lazy-load
              class="avatar"
              :src="resolveAvatar(msg.avatar)"
              mode="aspectFill"
              aria-hidden="true"
            />
          </a11y-button>
          <view
            class="bubble-wrap"
            :class="{ mine: msg.isMine }"
          >
            <!-- 发送中转圈：纯状态动画，对读屏隐藏 -->
            <view
              v-if="msg.isMine && msg.status === 'sending'"
              class="status-loading"
              aria-hidden="true"
            />
            <!-- 发送失败：点击重发 -->
            <a11y-button
              v-else-if="msg.isMine && msg.status === 'failed'"
              class="status-failed"
              label="发送失败，点击重发"
              @tap="resendMessage(msg)"
            >
              <text
                class="failed-icon"
                aria-hidden="true"
              >
                !
              </text>
            </a11y-button>
            <view
              class="bubble pop-in"
              :class="{ mine: msg.isMine }"
            >
              <text v-if="msg.type === 'text'">
                {{ msg.content }}
              </text>
              <a11y-button
                v-else-if="msg.type === 'image'"
                class="msg-image-wrap"
                label="查看图片"
                @tap="previewImg(msg.content)"
              >
                <image
                  lazy-load
                  class="msg-image"
                  :src="msg.content"
                  mode="widthFix"
                  aria-hidden="true"
                />
              </a11y-button>
              <!-- 语音：点击播放，播放中显示波动态 -->
              <a11y-button
                v-else-if="msg.type === 'voice'"
                class="msg-voice"
                :label="playingVoiceId === msg.id ? '暂停语音' : `播放语音，${msg.extra?.duration || 0} 秒`"
                @tap="toggleVoicePlay(msg)"
              >
                <text
                  class="voice-icon"
                  aria-hidden="true"
                >
                  {{ playingVoiceId === msg.id ? "❚❚" : "▶" }}
                </text>
                <view
                  class="voice-wave"
                  :class="{ playing: playingVoiceId === msg.id }"
                  aria-hidden="true"
                >
                  <text
                    v-for="n in 4"
                    :key="n"
                    class="wave-bar"
                  />
                </view>
                <text class="voice-duration">
                  {{ msg.extra?.duration || 0 }}"
                </text>
              </a11y-button>
              <!-- 位置：点击打开地图查看 -->
              <a11y-button
                v-else-if="msg.type === 'location'"
                class="msg-location"
                :label="`查看位置：${msg.content}`"
                @tap="openLocation(msg)"
              >
                <uni-icons
                  class="location-icon"
                  type="location"
                  size="16"
                />
                <text class="location-address">
                  {{ msg.content }}
                </text>
              </a11y-button>
              <!-- 名片：点击跳转对方主页 -->
              <a11y-button
                v-else-if="msg.type === 'card'"
                class="msg-card"
                :label="`查看 ${msg.content} 的个人名片`"
                @tap="openCard(msg)"
              >
                <image
                  lazy-load
                  class="card-avatar"
                  :src="resolveAvatar(msg.extra?.avatar)"
                  mode="aspectFill"
                  aria-hidden="true"
                />
                <view class="card-info">
                  <text class="card-name">
                    {{ msg.content }}
                  </text>
                  <text class="card-hint">
                    个人名片
                  </text>
                </view>
              </a11y-button>
            </view>
          </view>
          <!-- 我的头像：点击打开自己的资料页 -->
          <a11y-button
            v-if="msg.isMine"
            class="avatar-btn"
            label="查看我的资料"
            @tap="openMyProfile"
          >
            <image
              lazy-load
              class="avatar"
              :src="resolveAvatar(msg.avatar)"
              mode="aspectFill"
              aria-hidden="true"
            />
          </a11y-button>
        </view>
      </template>

      <!-- 历史拉取失败：与「真的没有历史消息」的空白态区分；重试只重拉历史，不重跑 IM 登录。
           消息列表非空（如失败后仍发出消息）即按门控自然隐藏，无需额外清理逻辑 -->
      <empty-state
        v-if="messageList.length === 0 && historyLoadFailed"
        role="alert"
        icon="info"
        title="暂时没能获取历史消息"
        desc="服务可能开小差了，检查网络后重试"
      >
        <a11y-button
          class="history-retry"
          label="重新加载历史消息"
          @tap="retryLoadHistory"
        >
          重新加载
        </a11y-button>
      </empty-state>
    </scroll-view>

    <!-- 输入区 -->
    <view class="input-area safe-area-bottom">
      <view class="input-bar">
        <!-- 语音/键盘切换 -->
        <a11y-button
          class="icon-btn"
          :label="isVoiceMode ? '切换到键盘输入' : '切换到语音输入'"
          @tap="toggleVoiceMode"
        >
          <text aria-hidden="true">
            {{ isVoiceMode ? "⌨" : "🎤" }}
          </text>
        </a11y-button>
        <!-- 语音模式：按住说话 -->
        <view
          v-if="isVoiceMode"
          class="voice-btn btn-feedback"
          role="button"
          :aria-label="voiceCancelled ? '松开手指，取消发送' : isVoicePressing ? '松开 发送' : '按住 说话'"
          tabindex="0"
          @touchstart="onVoiceStart"
          @touchmove="onVoiceMove"
          @touchend="onVoiceEnd"
          @touchcancel="onVoiceCancel"
          @keydown.enter="onVoiceStart"
          @keydown.space.prevent="onVoiceStart"
          @keyup="onVoiceEnd"
        >
          <text>{{ voiceCancelled ? "松开手指，取消发送" : isVoicePressing ? "松开 发送" : "按住 说话" }}</text>
        </view>
        <!-- 文字模式：输入框 + 表情 -->
        <template v-else>
          <input
            v-model="inputText"
            class="text-input"
            :class="{ focus: isInputFocus }"
            placeholder="说点什么..."
            placeholder-class="ph"
            aria-label="聊天输入框"
            confirm-type="send"
            @confirm="sendText"
            @focus="onInputFocus"
            @blur="isInputFocus = false"
          >
          <a11y-button
            class="icon-btn"
            role="tab"
            :checked="activePanel === 'emoji'"
            label="表情面板"
            @tap="togglePanel('emoji')"
          >
            <text
              :class="{ 'icon-active': activePanel === 'emoji' }"
              aria-hidden="true"
            >
              😊
            </text>
          </a11y-button>
        </template>
        <!-- 更多面板 -->
        <a11y-button
          class="icon-btn"
          role="tab"
          :checked="activePanel === 'more'"
          label="更多功能面板"
          @tap="togglePanel('more')"
        >
          <uni-icons
            :class="{ 'icon-active': activePanel === 'more' }"
            type="plus"
            size="24"
            color="#767676"
          />
        </a11y-button>
      </view>

      <!-- 表情面板 -->
      <view
        v-if="activePanel === 'emoji'"
        class="panel emoji-panel"
      >
        <scroll-view
          scroll-y
          class="emoji-scroll"
        >
          <view class="emoji-grid">
            <a11y-button
              v-for="emoji in EMOJI_LIST"
              :key="emoji"
              class="emoji-item"
              :label="`插入表情 ${emoji}`"
              @tap="insertEmoji(emoji)"
            >
              <text aria-hidden="true">
                {{ emoji }}
              </text>
            </a11y-button>
          </view>
        </scroll-view>
      </view>

      <!-- 更多面板 -->
      <view
        v-if="activePanel === 'more'"
        class="panel more-panel"
      >
        <view class="more-grid">
          <a11y-button
            v-for="action in QUICK_ACTIONS"
            :key="action.label"
            class="more-item"
            :label="action.label"
            @tap="action.handler"
          >
            <view
              class="more-icon"
              :style="{ background: action.bg }"
            >
              <uni-icons
                :type="action.uniIcon"
                size="24"
                color="#fff"
              />
            </view>
            <text class="more-label">
              {{ action.label }}
            </text>
          </a11y-button>
        </view>
      </view>
    </view>
  </view>
</template>

<script setup lang="ts">
import { ref, nextTick, onUnmounted } from "vue";
import { onLoad, onHide } from "@dcloudio/uni-app";
import A11yButton from "@/components/a11y-button.vue";
import EmptyState from "@/components/empty-state.vue";
import { useUserStore } from "@/store/user";
import { uploadChatImage, uploadChatAudio, getContacts } from "@/api/chat";
import { getUserProfile } from "@/api/mine";
import {
  ensureSelfIM,
  onMessageReceived,
  sendTextMessage,
  sendStructuredMessage,
  getChatHistory,
  type ReceivedMessage,
} from "@/utils/wukongim";
import { info, fail } from "@/utils/ux";
import { resolveAvatar, DEFAULT_AVATAR } from "@/utils/avatar";

/** 时间分隔线最小间隔（分钟） */
const TIME_GAP_MINUTES = 5;

/** 表情面板数据 */
const EMOJI_LIST: string[] = [
  "😊", "😂", "❤️", "👍", "😍", "🥰", "😘", "💕",
  "🌹", "🎉", "🔥", "✨", "😭", "😅", "🤔", "😎",
  "🙌", "💪", "🙏", "🤝", "💋", "🎁", "☕", "🌙",
];

/** 更多面板快捷操作配置（表驱动） */
interface QuickAction { uniIcon: string; label: string; bg: string; handler: () => void; }
const QUICK_ACTIONS: QuickAction[] = [
  { uniIcon: "image", label: "图片", bg: "var(--color-primary)", handler: chooseImage },
  { uniIcon: "camera", label: "拍照", bg: "var(--info-blue)", handler: () => showTodoToast("拍照") },
  { uniIcon: "location", label: "位置", bg: "var(--success)", handler: chooseLocation },
  { uniIcon: "person", label: "名片", bg: "var(--color-primary-violet)", handler: chooseCardContact },
];

/** 消息发送状态 */
type MsgStatus = "sending" | "sent" | "read" | "failed";
/** 聊天消息结构 */
interface Message {
  id: string;
  type: "text" | "image" | "voice" | "location" | "card";
  content: string;
  /** 结构化附加字段（语音时长/位置经纬度/名片信息） */
  extra?: Record<string, any>;
  isMine: boolean;
  avatar: string;
  timestamp: number;
  status: MsgStatus;
}

/** 底部面板类型 */
type PanelType = "none" | "emoji" | "more";

const userStore = useUserStore();
const userId = ref("");
const nickname = ref("");
const peerAvatar = ref(DEFAULT_AVATAR);
const myAvatar = ref(DEFAULT_AVATAR);
const inputText = ref("");
const scrollToId = ref("");
const messageList = ref<Message[]>([]);
const activePanel = ref<PanelType>("none");
const isVoiceMode = ref(false);
const isVoicePressing = ref(false);
const isInputFocus = ref(false);

/** 对方正在输入提示：本地模拟（WuKongIM SDK 不支持 typing 信令） */
const peerTyping = ref(false);
/** 模拟输入动画时长（ms）：对方消息到达前短暂显示「正在输入」 */
const PEER_TYPING_DURATION_MS = 1500;
let peerTypingTimer: ReturnType<typeof setTimeout> | null = null;

/** 显示对方正在输入（带自动消失） */
function showPeerTyping(): void {
  peerTyping.value = true;
  if (peerTypingTimer) clearTimeout(peerTypingTimer);
  peerTypingTimer = setTimeout(() => {
    peerTyping.value = false;
    peerTypingTimer = null;
  }, PEER_TYPING_DURATION_MS);
}

/** 语音消息最长录制时长（ms） */
const MAX_VOICE_DURATION_MS = 60000;
/** 有效语音最短时长（ms），过短视为误触 */
const MIN_VOICE_DURATION_MS = 1000;

/**
 * 录音管理器（单例）。
 * H5 下 uni.getRecorderManager 不受支持：调用会告警并返回 undefined，
 * 因此必须先做能力检测，缺失能力时保持 null，所有调用点统一用可选链保护。
 */
function createRecorderManager(): UniApp.RecorderManager | null {
  try {
    const manager = typeof uni.getRecorderManager === "function" ? uni.getRecorderManager() : null;
    return manager && typeof manager.onStop === "function" ? manager : null;
  } catch {
    return null;
  }
}
const recorderManager = createRecorderManager();
/** 正在播放的语音消息 id（空=无） */
const playingVoiceId = ref("");
/** 音频播放上下文（同一时刻仅一个） */
let audioContext: UniApp.InnerAudioContext | null = null;
/** 录音起始时间戳（计算有效时长用） */
let voiceStartAt = 0;
/** 上滑取消阈值（px）：手指上移超过此距离进入取消区 */
const VOICE_CANCEL_THRESHOLD = 80;
/** 录音中手指是否已上滑至取消区（控制提示文案） */
const voiceCancelled = ref(false);
/** 本次录音是否取消发送：onStop 同步读取（M-K2） */
let voiceCancelFlag = false;
/** 录音按下时手指 Y 起始坐标（计算上滑距离） */
let voiceTouchStartY = 0;

// 录音完成：上传 → 以 JSON 管道发送（type:"voice", url, duration）
function onRecorderStop(res: any): void {
  const cancelled = voiceCancelFlag;
  voiceCancelFlag = false;
  if (cancelled) {
    info("已取消");
    return;
  }
  const duration = Date.now() - voiceStartAt;
  if (duration < MIN_VOICE_DURATION_MS) {
    info("说话时间太短");
    return;
  }
  if (!res.tempFilePath) return;
  pushMessage("voice", res.tempFilePath, { duration: Math.round(duration / 1000) });
}
recorderManager?.onStop(onRecorderStop);

// 录音异常：麦克风权限被拒等场景必须显式提示，否则松手只会走到 onStop 的「说话时间太短」（M-K1）
recorderManager?.onError((err: any) => {
  isVoicePressing.value = false;
  voiceCancelled.value = false;
  voiceCancelFlag = true; // 让可能迟到的 onStop 不发送
  const msg = String(err?.errMsg || err?.message || "");
  if (msg.includes("auth") || msg.includes("permission") || msg.includes("deny")) {
    fail("麦克风权限被拒，请在设置中开启");
    // #ifdef MP-WEIXIN
    uni.openSetting({});
    // #endif
  } else {
    fail("录音失败，请重试");
  }
});

/** 语音按钮按下：开始录音 */
function onVoiceStart(e: any): void {
  if (!recorderManager) {
    info("当前环境不支持录音");
    return;
  }
  isVoicePressing.value = true;
  voiceCancelled.value = false;
  voiceCancelFlag = false;
  voiceStartAt = Date.now();
  voiceTouchStartY = e?.touches?.[0]?.clientY ?? 0;
  try {
    recorderManager.start({ format: "mp3", duration: MAX_VOICE_DURATION_MS });
  } catch {
    isVoicePressing.value = false;
    info("当前设备不支持录音");
  }
}

/** 录音中手指移动：上滑超过阈值进入取消区（M-K2） */
function onVoiceMove(e: any): void {
  if (!isVoicePressing.value) return;
  const y = e?.touches?.[0]?.clientY ?? voiceTouchStartY;
  voiceCancelled.value = voiceTouchStartY - y > VOICE_CANCEL_THRESHOLD;
}

/**
 * 判断是否显示时间分隔线
 * @param index 当前消息索引
 * @returns 首条消息或与上一条间隔超过阈值时返回 true
 */
function showTimeDivider(index: number): boolean {
  if (index === 0) return true;
  const prev = messageList.value[index - 1];
  const curr = messageList.value[index];
  return curr.timestamp - prev.timestamp > TIME_GAP_MINUTES * 60 * 1000;
}

/**
 * 格式化聊天时间戳：今天显示 HH:mm，昨天显示"昨天 HH:mm"，更早显示 MM-DD HH:mm
 * @param ts 毫秒时间戳
 */
function formatChatTime(ts: number): string {
  const date = new Date(ts);
  const now = new Date();
  const hh = String(date.getHours()).padStart(2, "0");
  const mm = String(date.getMinutes()).padStart(2, "0");
  if (date.toDateString() === now.toDateString()) return `${hh}:${mm}`;
  const yesterday = new Date(now);
  yesterday.setDate(now.getDate() - 1);
  if (date.toDateString() === yesterday.toDateString()) return `昨天 ${hh}:${mm}`;
  const MM = String(date.getMonth() + 1).padStart(2, "0");
  const DD = String(date.getDate()).padStart(2, "0");
  return `${MM}-${DD} ${hh}:${mm}`;
}

/** 滚动到最新消息 */
function scrollToBottom(): void {
  nextTick(() => {
    const last = messageList.value[messageList.value.length - 1];
    if (last) scrollToId.value = "msg-" + last.id;
  });
}

/** 单条文本消息最大长度：超长会撑破气泡布局且可能触发后端限制（BUG-07 收口） */
const MAX_MESSAGE_LENGTH = 2000;

/** 发送文字消息 */
async function sendText(): Promise<void> {
  const text = inputText.value.trim();
  if (!text) return;
  if (text.length > MAX_MESSAGE_LENGTH) {
    fail(`单条消息最多 ${MAX_MESSAGE_LENGTH} 字`);
    return;
  }
  pushMessage("text", text);
  inputText.value = "";
  activePanel.value = "none";
}

/** 从相册选图并发送 */
function chooseImage(): void {
  uni.chooseImage({
    count: 1,
    success: (res) => {
      pushMessage("image", res.tempFilePaths[0]);
      activePanel.value = "none";
    },
    fail: (err: any) => {
      // 区分用户取消（静默）与授权拒绝（提示 + MP 端引导打开设置页）
      const msg = String(err?.errMsg || "");
      if (msg.includes("cancel")) return;
      info("需要相册权限才能发送图片");
      const openSetting = (uni as any).openSetting;
      if (typeof openSetting === "function") {
        openSetting({});
      }
    },
  });
}
/** 本地消息自增序号（避免 Date.now() 同毫秒撞 id） */
let localMsgSeq = 0;
/** 接收消息自增序号（同毫秒多条消息 key 防撞） */
let recvMsgSeq = 0;

/**
 * 追加消息并通过 IM（WuKongIM）发送
 * 图片/语音：先本地预览，OSS 上传成功后发 URL，成功后把气泡替换为远程地址
 * 任何失败（上传 reject、发送失败）都标记为 failed，可点击气泡重发
 */
async function pushMessage(
  type: Message["type"],
  content: string,
  extra?: Record<string, any>
): Promise<void> {
  const msg: Message = {
    id: `local-${Date.now()}-${++localMsgSeq}`,
    type,
    content,
    extra,
    isMine: true,
    avatar: myAvatar.value,
    timestamp: Date.now(),
    status: "sending",
  };
  messageList.value.push(msg);
  scrollToBottom();
  await deliverMessage(msg);
}

/**
 * 投递单条消息（上传媒体 → IM 发送），统一处理失败标记
 * @param msg 待发送消息（发送中/失败均可重入）
 */
async function deliverMessage(msg: Message): Promise<void> {
  msg.status = "sending";
  try {
    let ok = false;
    if (msg.type === "image" || msg.type === "voice") {
      // 媒体消息：本地路径先上传 OSS（已是 http 链接则跳过，重发场景）
      if (!/^https?:\/\//.test(msg.content)) {
        const url =
          msg.type === "voice" ? await uploadChatAudio(msg.content) : await uploadChatImage(msg.content);
        if (!url) {
          msg.status = "failed";
          fail("上传失败，点击气泡重试");
          return;
        }
        msg.content = url;
      }
      const payload =
        msg.type === "voice"
          ? { type: "voice", url: msg.content, duration: msg.extra?.duration || 0 }
          : { type: "image", url: msg.content };
      ok = await sendStructuredMessage(userId.value, payload);
    } else if (msg.type === "location" || msg.type === "card") {
      // 位置/名片：结构化 JSON 承载（与接收端解析契约一致）
      const payload =
        msg.type === "location"
          ? { type: "location", latitude: msg.extra?.latitude, longitude: msg.extra?.longitude, address: msg.content }
          : { type: "card", userId: msg.extra?.userId, nickname: msg.content, avatar: msg.extra?.avatar || "" };
      ok = await sendStructuredMessage(userId.value, payload);
    } else {
      ok = await sendTextMessage(userId.value, msg.content);
    }
    msg.status = ok ? "sent" : "failed";
    if (!ok) {
      fail("发送失败，点击气泡重试");
    }
  } catch {
    // 上传/网络异常（upload reject 等）不能让气泡永远停在 sending
    msg.status = "failed";
    fail("发送失败，点击气泡重试");
  }
}

/** 点击失败气泡：重发 */
function resendMessage(msg: Message): void {
  if (msg.status !== "failed") return;
  deliverMessage(msg);
}

/** 插入表情到输入框 */
function insertEmoji(emoji: string): void {
  inputText.value += emoji;
}

/** 切换语音/文字输入模式 */
function toggleVoiceMode(): void {
  if (!recorderManager) {
    info("当前环境不支持录音");
    return;
  }
  isVoiceMode.value = !isVoiceMode.value;
  activePanel.value = "none";
  // 切到语音模式时输入框被替换，主动收键盘避免残留在屏（M-K3）
  if (isVoiceMode.value) uni.hideKeyboard();
}

/** 切换底部面板（同一面板再次点击则收起） */
function togglePanel(panel: PanelType): void {
  if (activePanel.value !== panel) {
    // 开面板先收键盘，避免面板与软键盘互相遮挡（M-K3）
    uni.hideKeyboard();
    isInputFocus.value = false;
  }
  activePanel.value = activePanel.value === panel ? "none" : panel;
  isVoiceMode.value = false;
}

/** 输入框聚焦：收起底部面板，避免键盘与面板同屏（M-K3） */
function onInputFocus(): void {
  isInputFocus.value = true;
  activePanel.value = "none";
}

/** 语音按钮松开：停止录音，onStop 回调中上传发送 */
function onVoiceEnd(): void {
  if (!isVoicePressing.value) return;
  isVoicePressing.value = false;
  voiceCancelFlag = voiceCancelled.value;
  voiceCancelled.value = false;
  recorderManager?.stop();
}

/** 触摸被系统中断（来电等）：取消本次录音，不发送（M-K2） */
function onVoiceCancel(): void {
  if (!isVoicePressing.value) return;
  isVoicePressing.value = false;
  voiceCancelled.value = false;
  voiceCancelFlag = true;
  recorderManager?.stop();
}

/** 选位置并发送（uni.chooseLocation；平台不支持时给出提示） */
function chooseLocation(): void {
  activePanel.value = "none";
  uni.chooseLocation({
    success: (res) => {
      pushMessage("location", res.name || res.address || "位置", {
        latitude: res.latitude,
        longitude: res.longitude,
      });
    },
    fail: (err) => {
      const msg = (err as { errMsg?: string }).errMsg || "";
      // 用户主动取消不算失败，其余（如未配置地图 key）明确提示
      if (!msg.includes("cancel")) {
        info("当前平台暂不支持选点");
      }
    },
  });
}

/** 点击位置气泡：打开地图查看 */
function openLocation(msg: Message): void {
  const lat = msg.extra?.latitude;
  const lng = msg.extra?.longitude;
  if (lat === undefined || lng === undefined) return;
  uni.openLocation({ latitude: lat, longitude: lng, name: msg.content });
}

/** 选联系人并发送名片（从好友列表选取） */
async function chooseCardContact(): Promise<void> {
  activePanel.value = "none";
  try {
    const result = await getContacts({ page: 1, pagesize: 50 });
    const contacts = result.items || [];
    if (contacts.length === 0) {
      info("暂无好友可分享");
      return;
    }
    uni.showActionSheet({
      itemList: contacts.map((c) => c.nickname || c.userId).slice(0, 6),
      success: (res) => {
        const picked = contacts[res.tapIndex];
        if (!picked) return;
        pushMessage("card", picked.nickname || picked.userId, {
          userId: picked.userId,
          avatar: picked.avatar,
        });
      },
    });
  } catch {
    // request 层统一 toast
  }
}

/** 点击名片气泡：跳转对方主页 */
function openCard(msg: Message): void {
  const cardUserId = msg.extra?.userId;
  if (!cardUserId) return;
  uni.navigateTo({ url: `/pages/match/detail?id=${cardUserId}` });
}

/** 点击对方头像：打开对方资料页 */
function openPeerProfile(): void {
  if (!userId.value) return;
  uni.navigateTo({ url: `/pages/match/detail?id=${userId.value}` });
}

/** 点击我的头像：打开自己的资料页 */
function openMyProfile(): void {
  const myId = String(userStore.userInfo?.id || "");
  if (!myId) return;
  uni.navigateTo({ url: `/pages/match/detail?id=${myId}` });
}

/**
 * 播放/暂停语音消息（同一时刻只播一条）
 */
function toggleVoicePlay(msg: Message): void {
  if (playingVoiceId.value === msg.id) {
    audioContext?.stop();
    playingVoiceId.value = "";
    return;
  }
  audioContext?.destroy();
  const ctx = uni.createInnerAudioContext();
  audioContext = ctx;
  ctx.src = msg.content;
  playingVoiceId.value = msg.id;
  ctx.play();
  ctx.onEnded(() => {
    playingVoiceId.value = "";
  });
  ctx.onError(() => {
    playingVoiceId.value = "";
    fail("语音播放失败");
  });
}

/** 预览图片消息 */
function previewImg(url: string): void {
  uni.previewImage({ urls: [url] });
}

/** 未完成功能占位提示 */
function showTodoToast(feature: string): void {
  info(`${feature}功能开发中`);
}

/** 接收对方新消息回调（onUnmounted 注销） */
let unregisterMessage: (() => void) | null = null;
/** 历史消息是否已填充（填充前的实时消息进 pending 缓冲，填充后实时追加） */
const historyLoaded = ref(false);
/** 历史拉取失败标记：与「真的没有历史消息」的空白态区分，驱动错误空态 + 重试（S3） */
const historyLoadFailed = ref(false);
/** 在途实时消息缓冲：历史填充前到达的消息先缓存，loadHistory 合并后清空。
 *  提升为组件级变量，供重试 loadHistory 时复用同一缓冲（重试不重跑 IM 登录/监听注册） */
let pendingMessages: ReceivedMessage[] = [];

/** IM 实时消息 → 本地消息结构 */
function toReceivedItem(msg: ReceivedMessage): Message {
  return {
    id: `recv-${msg.time}-${++recvMsgSeq}`,
    type: msg.type,
    content: msg.content,
    extra: msg.extra,
    isMine: false,
    avatar: peerAvatar.value,
    timestamp: msg.time,
    status: "read",
  };
}

/** 追加一条实时到达消息并滚到底 */
function appendReceived(msg: ReceivedMessage): void {
  messageList.value.push(toReceivedItem(msg));
  scrollToBottom();
}

/**
 * 收到对方消息：即时渲染上屏，「正在输入」仅作装饰并行显示、不阻塞消息（M-K5）。
 * 原实现首条消息压 1.5s 等 typing 动画结束才上屏，期间第二条立即追加 → 顺序倒置。
 */
function appendReceivedWithTyping(msg: ReceivedMessage): void {
  appendReceived(msg);
  showPeerTyping();
}

/**
 * 拉取历史消息并与在途缓冲合并（独立函数，失败可单独重试）。
 * 重试只重拉历史：不重跑 IM 登录、不重复注册 onMessageReceived（监听在 onLoad 只注册一次）。
 */
async function loadHistory(): Promise<void> {
  const history = await getChatHistory(userId.value);
  // WuKongIM 直连登录后无凭证对象，我的 uid 即当前登录用户 ID（消息归属判断用）
  const myId = String(userStore.userInfo?.id ?? "");
  const historyItems: Message[] = history.map((msg, i) => ({
    id: `hist-${i}-${msg.time}`,
    type: msg.type,
    content: msg.content,
    extra: msg.extra,
    isMine: msg.from === myId,
    avatar: msg.from === myId ? myAvatar.value : peerAvatar.value,
    timestamp: msg.time,
    status: "read" as MsgStatus,
  }));
  // 合并缓冲：按时间排序并以 timestamp 去重（历史可能已含在途消息）
  const buffered = pendingMessages.map(toReceivedItem);
  const merged = [...historyItems, ...buffered].sort((a, b) => a.timestamp - b.timestamp);
  const seen = new Set<number>();
  messageList.value = merged.filter((item) => {
    if (seen.has(item.timestamp)) return false;
    seen.add(item.timestamp);
    return true;
  });
  // 合并完成后清空缓冲并切到实时通道：后续到达消息直接追加，重试也不会重复合并
  pendingMessages = [];
  historyLoadFailed.value = false;
  historyLoaded.value = true;
  scrollToBottom();
}

/** 重试拉取历史：仅重拉历史消息，禁止重跑 IM 登录/监听注册 */
async function retryLoadHistory(): Promise<void> {
  try {
    await loadHistory();
  } catch (err) {
    // catch 不空吞：保留错误空态 + ux 反馈（IM SDK 直连不走 request 层，无统一 toast）
    historyLoadFailed.value = true;
    console.warn(`[chat] 历史消息重试失败，userId=${userId.value}, err=${String(err)}`);
    fail("历史消息加载失败，请稍后重试");
  }
}

onLoad(async (options: any) => {
  userId.value = options?.userId || "";
  nickname.value = options?.nickname || "聊天";
  uni.setNavigationBarTitle({ title: nickname.value });

  // 当前用户头像
  myAvatar.value = resolveAvatar(userStore.userInfo?.avatar);

  // 拉对方资料补头像
  if (userId.value) {
    getUserProfile(userId.value)
      .then((profile) => {
        if (profile.avatar) {
          peerAvatar.value = profile.avatar;
          // 同步更新已渲染的对方消息头像
          messageList.value.forEach((m) => {
            if (!m.isMine) m.avatar = profile.avatar;
          });
        }
      })
      .catch(() => {});
  }

  // WuKongIM 直连登录 → 注册消息接收 → 拉历史
  try {
    // 以「当前登录用户本人」身份连接（历史问题：曾以对方身份登录导致顶号、自发自收）
    const ok = await ensureSelfIM();
    if (ok) {
      // 先注册消息到达回调再拉历史：历史是最后一次性赋值的，
      // 在途实时消息先进 pending 缓冲，避免注册晚于拉取导致消息丢失。
      // 监听全程只注册这一次，重试历史拉取不会重复注册。
      unregisterMessage = onMessageReceived((msg) => {
        if (msg.from !== userId.value) return;
        if (historyLoaded.value) {
          appendReceivedWithTyping(msg);
        } else {
          pendingMessages.push(msg);
        }
      });

      // 历史拉取单独内层 try/catch：失败只置错误空态，
      // 不影响此前已完成的 IM 登录与消息监听；外层 catch 仍兜 IM 登录失败
      try {
        await loadHistory();
      } catch (err) {
        historyLoadFailed.value = true;
        console.warn(`[chat] 历史消息拉取失败，userId=${userId.value}, err=${String(err)}`);
        fail("历史消息加载失败，请稍后重试");
      }
    }
  } catch {
    // IM（WuKongIM）登录失败静默降级（可发但不达，列表为空）
  }
});

onHide(() => {
  // 离页（navigateTo 等）停止语音播放：不应在后台继续出声（M-K6）
  audioContext?.stop();
  // 离页中断在途录音，避免跑到自动时长发出非预期语音（M-K2）
  if (isVoicePressing.value) onVoiceCancel();
});

onUnmounted(() => {
  if (peerTypingTimer) {
    clearTimeout(peerTypingTimer);
    peerTypingTimer = null;
  }
  // 停止在途录音并释放麦克风（置取消标记 + offStop 拦截回调，不会误发送）
  voiceCancelFlag = true;
  recorderManager?.stop();
  // 注销录音回调：否则离页后 stop/onStop 链可能在新实例注册后重复触发
  // （offStop 运行时存在，但 @dcloudio/types 的 RecorderManager 声明缺该方法）
  (recorderManager as any)?.offStop?.(onRecorderStop);
  if (unregisterMessage) {
    unregisterMessage();
    unregisterMessage = null;
  }
  audioContext?.destroy();
  audioContext = null;
});
</script>

<style lang="scss" scoped>
/* fixed 撑满整个视口，彻底脱离 uni-page-wrapper/body 的高度限制；
   否则 uni-page-body 在 H5 中高度由内容撑开（约半屏），输入框会被推到屏幕中间甚至视口外。
   z-index 低于 tabbar(998) 避免遮挡全局底栏；padding-top 预留状态栏。 */
.chat-room {
  position: fixed;
  top: 0;
  left: 0;
  right: 0;
  bottom: 0;
  z-index: 997;
  display: flex;
  flex-direction: column;
  background: var(--bg-sub);
}

/* ===== 消息列表 =====
   flex:1 必须配 min-height:0：纵向 flex 子项默认 min-height:auto，
   消息增多时会撑破容器、把输入区挤出视口。 */
/* scroll-view 自带滚动：不再叠加 overflow-y，避免出现双层滚动容器 */
.msg-list { flex: 1 1 auto; min-height: 0; min-width: 0; padding: 24rpx 24rpx 12rpx; }

/* #bbb on 浅灰底 ≈1.9:1 → #767676 */
/* 时间分隔线底色走 --bg-card：写死 rgba(0,0,0,.05) 在暗色模式下是深灰底+灰字，
   对比度不足（--text-hint 在暗色下为 #a6a6a6） */
.time-divider { display: flex; justify-content: center; margin: 16rpx 0 24rpx;
  .time-text { font-size: 22rpx; color: var(--text-hint); background: var(--bg-card); padding: 6rpx 24rpx; border-radius: 24rpx; }
}

/* 历史加载失败重试按钮：default 变体零样式，这里补品牌色，颜色全走 CSS 变量适配暗色 */
.history-retry { margin-top: 32rpx; padding: 16rpx 56rpx; border-radius: var(--radius-pill);
  font-size: 28rpx; font-weight: 600; color: var(--text-inverse); background: var(--color-primary);
}

/* ===== 对方正在输入提示 ===== */
.typing-indicator { display: flex; align-items: center; margin-bottom: 20rpx; animation: fadeIn 0.2s ease-out; }
.typing-btn { display: flex; align-items: center; gap: 12rpx; min-height: 72rpx; padding: 8rpx 20rpx 8rpx 8rpx; background: var(--bg-card); border-radius: 40rpx; box-shadow: 0 2rpx 12rpx rgba(0, 0, 0, 0.06); }
.typing-avatar { width: 56rpx; height: 56rpx; border-radius: 50%; background: var(--border-light); flex-shrink: 0; }
.typing-bubble { display: flex; align-items: center; padding: 0 8rpx; }
.typing-dots { display: flex; align-items: center; gap: 6rpx; }
.dot { width: 12rpx; height: 12rpx; border-radius: 50%; background: var(--text-hint); animation: typingBounce 1.2s ease-in-out infinite; }
.dot:nth-child(2) { animation-delay: 0.15s; }
.dot:nth-child(3) { animation-delay: 0.3s; }
.typing-text { font-size: 24rpx; color: var(--text-hint); }
@keyframes typingBounce {
  0%, 60%, 100% { transform: translateY(0); opacity: 0.5; }
  30% { transform: translateY(-6rpx); opacity: 1; }
}
@keyframes fadeIn { from { opacity: 0; transform: translateY(-8rpx); } to { opacity: 1; transform: translateY(0); } }

/* 头像：76rpx 已 ≥44px 热区基线；外层 a11y-button 补足可点击语义与键盘可达 */
.msg-item { display: flex; align-items: flex-start; margin-bottom: 28rpx; gap: 16rpx;
&.mine { flex-direction: row-reverse; }
.avatar-btn { flex-shrink: 0; display: flex; }
.avatar { width: 76rpx; height: 76rpx; border-radius: 16rpx; background: var(--border-light); }
  .bubble-wrap { display: flex; align-items: center; gap: 12rpx; max-width: 480rpx; min-width: 0;
    &.mine { flex-direction: row-reverse; }
  }
  .status-loading { width: 28rpx; height: 28rpx; border: 3rpx solid var(--border); border-top-color: var(--color-primary); border-radius: 50%; animation: spin 0.8s linear infinite; flex-shrink: 0; }
  /* 热区 88rpx：原 32rpx（≈19px）远低于 44px 可点击尺寸 */
  .status-failed { width: 88rpx; height: 88rpx; border-radius: 50%; background: var(--color-danger); display: flex; align-items: center; justify-content: center; flex-shrink: 0;
    .failed-icon { font-size: 22rpx; color: var(--text-inverse); font-weight: bold; line-height: 1; }
  }
  /* min-width:0 + 断行：长文本/连续英文/URL 才能收在气泡内，
     否则会撑破 flex 容器导致页面横向滚动 */
  .bubble { position: relative; min-width: 0; overflow-wrap: anywhere; word-break: break-word; padding: 20rpx 24rpx; border-radius: 20rpx; background: var(--bg-card); font-size: 28rpx; line-height: 1.5; color: var(--text-primary); box-shadow: 0 2rpx 8rpx rgba(0, 0, 0, 0.04);
    /* 气泡尾巴 */
    &::before { content: ""; position: absolute; top: 28rpx; width: 0; height: 0; border-top: 10rpx solid transparent; border-bottom: 10rpx solid transparent; }
    &:not(.mine) { border-top-left-radius: 6rpx;
      /* 尾巴跟随气泡底色：原 --text-inverse 恒白，与 --bg-card 暗色值不同源 */
      &::before { left: -16rpx; border-right: 16rpx solid var(--bg-card); }
    }
    /* color 用 --text-inverse：原 --bg-card 在暗色模式下是深色，文字会消失 */
    &.mine { background: var(--gradient-brand); color: var(--text-inverse); border-top-right-radius: 6rpx;
      &::before { right: -16rpx; border-left: 16rpx solid var(--color-primary); }
    }
    .msg-image { max-width: 360rpx; border-radius: 12rpx; display: block; }

    /* 语音气泡 */
    .msg-voice { display: flex; align-items: center; gap: 12rpx; min-width: 180rpx;
      .voice-icon { font-size: 26rpx; }
      .voice-wave { display: flex; align-items: center; gap: 4rpx;
        .wave-bar { width: 4rpx; height: 16rpx; border-radius: 2rpx; background: currentColor; opacity: 0.6; }
        &.playing .wave-bar { animation: wave 0.8s ease-in-out infinite; }
      }
      .voice-duration { font-size: 24rpx; opacity: 0.85; }
    }

    /* 位置气泡 */
    .msg-location { display: flex; align-items: center; gap: 10rpx; max-width: 400rpx;
      .location-icon { font-size: 30rpx; }
      .location-address { font-size: 28rpx; overflow: hidden; white-space: nowrap; text-overflow: ellipsis; }
    }

    /* 名片气泡 */
    .msg-card { display: flex; align-items: center; gap: 16rpx;
      .card-avatar { width: 72rpx; height: 72rpx; border-radius: 50%; background: var(--bg-sub); }
      .card-info { display: flex; flex-direction: column; gap: 4rpx;
        .card-name { font-size: 28rpx; }
        .card-hint { font-size: 22rpx; opacity: 0.7; }
      }
    }
  }
}

@keyframes spin { to { transform: rotate(360deg); } }
@keyframes wave {
  0%, 100% { height: 12rpx; }
  50% { height: 24rpx; }
}

/* ===== 输入区 ===== */
.input-area { background: var(--bg-card); border-top: 1rpx solid var(--border-light); }
.input-bar { display: flex; align-items: center; gap: 12rpx; padding: 14rpx 16rpx; }
.icon-btn { width: 64rpx; height: 64rpx; display: flex; align-items: center; justify-content: center; font-size: 40rpx; border-radius: 50%; flex-shrink: 0;
  .icon-active { filter: drop-shadow(0 0 6rpx rgba(var(--color-primary-rgb), 0.6)); }
}
.text-input { flex: 1; height: 72rpx; background: var(--bg-sub); border-radius: 36rpx; padding: 0 28rpx; font-size: 28rpx; transition: all 0.2s;
  &.focus { background: var(--bg-card); box-shadow: 0 0 0 3rpx rgba(var(--color-primary-rgb), 0.35); }
}
.voice-btn { flex: 1; height: 72rpx; background: var(--bg-sub); border-radius: 36rpx; display: flex; align-items: center; justify-content: center; font-size: 28rpx; color: var(--text-secondary); transition: all 0.15s;
  &:active { background: var(--border); transform: scale(0.98); }
}

/* ===== 底部面板 ===== */
.panel { height: 380rpx; background: var(--bg-page); border-top: 1rpx solid var(--border-light); animation: slideUp 0.2s ease-out; overflow: hidden; }
@keyframes slideUp { from { transform: translateY(100%); } to { transform: translateY(0); } }

.emoji-panel .emoji-scroll { height: 100%; }
.emoji-grid { display: flex; flex-wrap: wrap; padding: 16rpx 12rpx; }
.emoji-item { width: 12.5%; height: 80rpx; display: flex; align-items: center; justify-content: center; font-size: 44rpx; }

.more-grid { display: flex; flex-wrap: wrap; padding: 36rpx 16rpx; }
.more-item { width: 25%; display: flex; flex-direction: column; align-items: center; gap: 14rpx; }
.more-icon { width: 104rpx; height: 104rpx; border-radius: 24rpx; display: flex; align-items: center; justify-content: center; font-size: 44rpx; box-shadow: 0 6rpx 16rpx rgba(0, 0, 0, 0.08); }
.more-label { font-size: 24rpx; color: var(--text-secondary); }
</style>
