<template>
  <view class="chat-room">
    <!-- 消息列表 -->
    <!-- S6：错误边界捕获消息列表渲染异常，@retry 走带 try/catch 的安全重试，避免裸 async 重抛 -->
    <error-boundary @retry="retryLoadHistory">
    <scroll-view
      scroll-y
      class="msg-list"
      :scroll-into-view="scrollToId"
      :scroll-with-animation="false"
      @scrolltoupper="loadMoreHistory"
    >
      <!-- 首屏加载：list 骨架屏替代空白，提前感知消息结构 -->
      <skeleton
        v-if="historyLoading && messageList.length === 0"
        class="msg-skeleton"
        variant="list"
        :count="6"
      />
      <!-- 续拉更旧历史指示（M-K4） -->
      <view
        v-if="loadingMore || noMoreHistory"
        class="load-more-history"
        role="status"
      >
        <text>{{ loadingMore ? '加载中…' : '没有更多了' }}</text>
      </view>
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
          <smart-image
            lazy-load
            class="typing-avatar"
            :src="peerAvatar"
            mode="aspectFill"
            :aria-hidden="true"
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
            <smart-image
              lazy-load
              class="avatar"
              :src="msg.avatar"
              mode="aspectFill"
              :aria-hidden="true"
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
              :label="`发送失败，重新发送：${msg.content || '消息'}`"
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
              class="bubble"
              :class="{ mine: msg.isMine }"
            >
              <text v-if="msg.type === 'text'">
                {{ msg.content }}
              </text>
              <a11y-button
                v-else-if="msg.type === 'image'"
                class="msg-image-wrap"
                :label="msg.status === 'sending' ? '图片上传中' : '查看图片'"
                @tap="previewImg(msg.content)"
              >
                <smart-image
                  lazy-load
                  class="msg-image"
                  :src="msg.content"
                  mode="widthFix"
                  :aria-hidden="true"
                />
                <!-- 上传中遮罩：图片本地预览期间显示半透明遮罩 + 提示，避免误以为已发送成功 -->
                <view
                  v-if="msg.status === 'sending'"
                  class="image-uploading-mask"
                  aria-hidden="true"
                >
                  <text class="image-uploading-text">上传中…</text>
                </view>
              </a11y-button>
              <!-- 语音：点击播放，播放中显示波动态 -->
              <a11y-button
                v-else-if="msg.type === 'voice'"
                class="msg-voice"
                :label="playingVoiceId === msg.id ? '暂停语音' : `播放语音，${msg.extra?.duration || 0} 秒`"
                @tap="toggleVoicePlay(msg)"
              >
                <!-- Phosphor 单色图标替代 emoji 字形（§7.1：图标只用 i-ph-*），尺寸由 .voice-icon 的 font-size 经 1em 机制控制 -->
                <view
                  class="voice-icon"
                  :class="playingVoiceId === msg.id ? 'i-ph-pause-fill' : 'i-ph-play-fill'"
                  aria-hidden="true"
                />
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
                  {{ msg.extra?.duration || 0 }}秒
                </text>
                <!-- 播放进度条：仅播放中显示，宽度按进度百分比 -->
                <view
                  v-if="playingVoiceId === msg.id"
                  class="voice-progress"
                  aria-hidden="true"
                >
                  <view
                    class="voice-progress-bar"
                    :style="{ transform: 'scaleX(' + (voicePlayDuration > 0 ? voicePlayProgress / voicePlayDuration : 0) + ')' }"
                  />
                </view>
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
                <smart-image
                  lazy-load
                  class="card-avatar"
                  :src="msg.extra?.avatar"
                  mode="aspectFill"
                  :aria-hidden="true"
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
            <smart-image
              lazy-load
              class="avatar"
              :src="msg.avatar"
              mode="aspectFill"
              :aria-hidden="true"
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

      <!-- IM 连接失败：消息无法收发，显式提示 + 重连入口（对齐客服页，禁止静默降级） -->
      <empty-state
        v-if="imLoginFailed"
        role="alert"
        icon="info"
        title="消息服务连接失败"
        desc="暂时无法收发消息，请检查网络后重试"
      >
        <a11y-button
          class="history-retry"
          label="重新连接消息服务"
          @tap="retryIMLogin"
        >
          重新连接
        </a11y-button>
      </empty-state>
    </scroll-view>
    </error-boundary>

    <!-- 输入区 -->
    <view class="input-area safe-area-bottom">
      <view class="input-bar">
        <!-- 语音/键盘切换 -->
        <a11y-button
          class="icon-btn"
          :label="isVoiceMode ? '切换到键盘输入' : '切换到语音输入'"
          @tap="toggleVoiceMode"
        >
          <view
            :class="isVoiceMode ? 'i-ph-keyboard-fill' : 'i-ph-microphone-fill'"
            aria-hidden="true"
          />
        </a11y-button>
        <!-- 语音模式：点击开始/停止录音（a11y-button 保证 88rpx 热区与键盘可达） -->
        <a11y-button
          v-if="isVoiceMode"
          class="voice-btn"
          :class="{ recording: isVoicePressing }"
        :label="isVoicePressing ? '停止录音' : '点击说话'"
        @tap="onVoiceClick"
      >
        <text v-if="!isVoicePressing">点击说话</text>
        <text v-else>{{ voiceSeconds }}s{{ voiceSeconds >= VOICE_WARN_SECONDS ? '（即将自动结束）' : '' }}</text>
      </a11y-button>
        <!-- 文字模式：输入框 + 表情 -->
        <template v-else>
          <input
            v-model="inputText"
            class="text-input"
            :class="{ focus: isInputFocus }"
            placeholder="说点什么…"
            placeholder-class="ph"
            maxlength="500"
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
            <view
              class="i-ph-smiley"
              :class="{ 'icon-active': activePanel === 'emoji' }"
              aria-hidden="true"
            />
          </a11y-button>
        </template>
        <!-- 文字模式有输入内容时加号让位为「发送」（微信式交互，布局不跳动）；
             语音模式或空内容保持更多面板入口 -->
        <a11y-button
          v-if="!isVoiceMode && inputText.trim()"
          class="send-btn"
          variant="primary"
          label="发送消息"
          @tap="sendText"
        >
          <text>发送</text>
        </a11y-button>
        <a11y-button
          v-else
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
            color="var(--text-hint)"
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
          <!-- 最近使用表情：最多 8 个，点击直接插入 -->
          <view
            v-if="recentEmojis.length > 0"
            class="emoji-recent-row"
          >
            <text class="emoji-recent-label">最近使用</text>
            <view class="emoji-recent-list">
              <a11y-button
                v-for="emoji in recentEmojis"
                :key="`recent-${emoji}`"
                class="emoji-item emoji-recent-item"
                :label="`插入最近表情 ${emoji}`"
                @tap="insertEmoji(emoji)"
              >
                <text aria-hidden="true">
                  {{ emoji }}
                </text>
              </a11y-button>
            </view>
          </view>
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
                color="var(--text-inverse)"
              />
            </view>
            <text class="more-label">
              {{ action.label }}
            </text>
          </a11y-button>
        </view>
      </view>
    </view>

    <!-- 名片选择弹窗：替代 uni.showActionSheet，支持完整好友列表 -->
    <a11y-dialog
      :visible="cardPickerVisible"
      title="选择好友"
      @close="cardPickerVisible = false"
    >
      <view class="card-picker">
        <a11y-button
          v-for="c in cardContacts"
          :key="c.userId"
          class="card-contact-item"
          :label="`选择 ${c.nickname || c.userId} 发送名片`"
          @tap="selectCardContact(c)"
        >
          <smart-image
            class="card-contact-avatar"
            :src="resolveAvatar(c.avatar)"
            mode="aspectFill"
            :aria-hidden="true"
          />
          <text class="card-contact-name">{{ c.nickname || c.userId }}</text>
        </a11y-button>
        <view
          v-if="cardContacts.length === 0"
          class="card-picker-empty"
        >
          <text>暂无好友可分享</text>
        </view>
      </view>
    </a11y-dialog>
  </view>
</template>

<script setup lang="ts">
import { ref, nextTick, onUnmounted } from "vue";
import { onLoad, onHide } from "@dcloudio/uni-app";
import A11yButton from "@/components/a11y-button.vue";
import A11yDialog from "@/components/a11y-dialog.vue";
import SmartImage from "@/components/smart-image.vue";
import ErrorBoundary from "@/components/error-boundary.vue";
import EmptyState from "@/components/empty-state.vue";
import Skeleton from "@/components/skeleton.vue";
import { useUserStore } from "@/store/user";
import { useUnreadStore } from "@/store/unread";
import { showNotification } from "@/utils/notification";
import { uploadChatImage, uploadChatAudio, getContacts } from "@/api/chat";
import { getUserProfile } from "@/api/mine";
import {
  ensureSelfIM,
  isIMConnected,
  onMessageReceived,
  sendTextMessage,
  sendStructuredMessage,
  getChatHistory,
  getChatHistoryBefore,
  type ReceivedMessage,
} from "@/utils/wukongim";
import { info, fail } from "@/utils/ux";
import {
  resolveAddressName,
  resolveCurrentLocation,
  locationDisplayName,
} from "@/utils/location";
import { warn } from "@/utils/logger";
import { resolveAvatar, DEFAULT_AVATAR } from "@/utils/avatar";

/** 时间分隔线最小间隔（分钟） */
const TIME_GAP_MINUTES = 5;

/** 表情面板数据 */
const EMOJI_LIST: string[] = [
  "😊", "😂", "❤️", "👍", "😍", "🥰", "😘", "💕",
  "🌹", "🎉", "🔥", "✨", "😭", "😅", "🤔", "😎",
  "🙌", "💪", "🙏", "🤝", "💋", "🎁", "☕", "🌙",
];
/** 最近使用表情（最多 8 个，持久化到 storage） */
const recentEmojis = ref<string[]>([]);

/** 更多面板快捷操作配置（表驱动） */
interface QuickAction { uniIcon: string; label: string; bg: string; handler: () => void; }
const QUICK_ACTIONS: QuickAction[] = [
  { uniIcon: "image", label: "图片", bg: "var(--color-primary)", handler: chooseImage },
  { uniIcon: "camera", label: "拍照", bg: "var(--info-blue)", handler: chooseFromCamera },
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
/** 语音播放进度（秒，用于显示进度条） */
const voicePlayProgress = ref(0);
/** 语音总时长（秒，用于计算进度百分比） */
const voicePlayDuration = ref(0);
/** 录音起始时间戳（计算有效时长用） */
let voiceStartAt = 0;
/** 本次录音是否取消发送：onStop 同步读取（M-K2） */
let voiceCancelFlag = false;
/** 录音计时器（每秒更新显示时长） */
let voiceTimer: ReturnType<typeof setInterval> | null = null;
/** 当前录音已录秒数（驱动按钮显示与上限提示） */
const voiceSeconds = ref(0);
/** 接近上限提示阈值（剩余 10s 时提示） */
const VOICE_WARN_SECONDS = 50;

// 录音完成：上传 → 以 JSON 管道发送（type:"voice", url, duration）
function onRecorderStop(res: any): void {
  const cancelled = voiceCancelFlag;
  voiceCancelFlag = false;
  if (cancelled) {
    // 取消发送不再 toast：用户主动停止，静默即可
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

/** 语音按钮点击：切换录音状态（a11y-button tap 交互） */
function onVoiceClick(): void {
  if (isVoicePressing.value) {
    stopVoiceRecording();
  } else {
    startVoiceRecording();
  }
}

/** 开始录音 */
function startVoiceRecording(): void {
  if (!recorderManager) {
    info("当前环境不支持录音");
    return;
  }
  isVoicePressing.value = true;
  voiceCancelFlag = false;
  voiceStartAt = Date.now();
  // 启动计时器：每秒更新已录时长，接近上限时变色提示
  voiceSeconds.value = 0;
  voiceTimer = setInterval(() => {
    voiceSeconds.value = Math.floor((Date.now() - voiceStartAt) / 1000);
  }, 1000);
  try {
    recorderManager.start({ format: "mp3", duration: MAX_VOICE_DURATION_MS });
  } catch {
    isVoicePressing.value = false;
    clearVoiceTimer();
    info("当前设备不支持录音");
  }
}

/** 清理录音计时器（停止/异常/离页统一调用） */
function clearVoiceTimer(): void {
  if (voiceTimer) {
    clearInterval(voiceTimer);
    voiceTimer = null;
  }
}

/** 停止录音 */
function stopVoiceRecording(): void {
  if (!isVoicePressing.value) return;
  isVoicePressing.value = false;
  clearVoiceTimer();
  try {
    recorderManager?.stop();
  } catch {
    // onStop 会处理异常
  }
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
 * 格式化聊天时间戳：今天显示 HH:mm，昨天显示"昨天 HH:mm"，同年更早显示 MM-DD HH:mm，跨年显示 YYYY-MM-DD HH:mm（P2-5）
 * @param ts 毫秒时间戳
 */
function formatChatTime(ts: number): string {
  const date = new Date(ts);
  // 缓存 now：避免每条消息渲染都新建 Date 实例（长列表性能优化）
  const now = new Date();
  const hh = String(date.getHours()).padStart(2, "0");
  const mm = String(date.getMinutes()).padStart(2, "0");
  // 用日期戳比较替代 toDateString()：避免字符串解析开销
  const dateDay = new Date(date.getFullYear(), date.getMonth(), date.getDate()).getTime();
  const nowDay = new Date(now.getFullYear(), now.getMonth(), now.getDate()).getTime();
  if (dateDay === nowDay) return `${hh}:${mm}`;
  const yesterdayDay = nowDay - 24 * 60 * 60 * 1000;
  if (dateDay === yesterdayDay) return `昨天 ${hh}:${mm}`;
  const MM = String(date.getMonth() + 1).padStart(2, "0");
  const DD = String(date.getDate()).padStart(2, "0");
  if (date.getFullYear() !== now.getFullYear()) {
    return `${date.getFullYear()}-${MM}-${DD} ${hh}:${mm}`;
  }
  return `${MM}-${DD} ${hh}:${mm}`;
}

/** 滚动到最新消息 */
function scrollToBottom(): void {
  nextTick(() => {
    const last = messageList.value[messageList.value.length - 1];
    if (last) scrollToId.value = "msg-" + last.id;
  });
}

/** 单条文本消息最大长度：与输入框 maxlength 统一为 500，避免输入框截断但校验仍报 2000 的矛盾 */
const MAX_MESSAGE_LENGTH = 500;

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

/** 拍照并发送（sourceType 限定 camera，失败处理与选图一致，仅提示文案不同） */
function chooseFromCamera(): void {
  uni.chooseImage({
    count: 1,
    sourceType: ["camera"],
    success: (res) => {
      pushMessage("image", res.tempFilePaths[0]);
      activePanel.value = "none";
    },
    fail: (err: any) => {
      const msg = String(err?.errMsg || "");
      if (msg.includes("cancel")) return;
      info("需要相机权限才能拍照");
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
 * 消息业务指纹：跨「历史快照」与「实时/在途缓冲」两个来源的稳定去重键。
 * 两来源本地 id 前缀不同（hist-/recv-/local-），按 id 去重会失效；同一条服务端消息的
 * 归属/时间戳/类型/内容必然一致（历史与实时都经 wukongim.toReceivedMessage 解析，字段同源），故以此为指纹。
 */
function messageFingerprint(m: Message): string {
  return `${m.isMine ? "me" : "peer"}|${m.timestamp}|${m.type}|${m.content}`;
}

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
          // 不再重复 toast：request.ts upload 底层已提示上传失败（P2-11 双 toast 去重），
          // 气泡红点 + a11y label「发送失败，重新发送」已提供重试入口
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

/** 点击失败气泡：重发（底层连接已断时先重连，避免红点永久卡死） */
async function resendMessage(msg: Message): Promise<void> {
  if (msg.status !== "failed") return;
  // 连接不在时先确保 IM 就绪：ensureSelfIM 会重建断连；仍失败则引导用页面顶部「重新连接」，
  // 不进 SDK 队列（断连期入队消息会被静默丢弃且不补发）
  if (!isIMConnected()) {
    const ok = await ensureSelfIM();
    if (!ok) {
      imLoginFailed.value = true;
      fail("消息服务未连接，请点击上方「重新连接」");
      return;
    }
  }
  await deliverMessage(msg);
}

/** 插入表情到输入框，并记录到最近使用 */
function insertEmoji(emoji: string): void {
  inputText.value += emoji;
  // 更新最近使用：去重后取前 8 个
  recentEmojis.value = [emoji, ...recentEmojis.value.filter((e) => e !== emoji)].slice(0, 8);
  // 持久化到 storage，下次进入恢复
  try {
    uni.setStorageSync("chat_recent_emojis", recentEmojis.value);
  } catch {
    // 存储失败不影响主流程
  }
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

/** 触摸被系统中断（来电等）：取消本次录音，不发送（M-K2） */
function onVoiceCancel(): void {
  if (!isVoicePressing.value) return;
  isVoicePressing.value = false;
  voiceCancelFlag = true;
  recorderManager?.stop();
}

/** 发送当前位置：设备定位优先，取不到走 IP 兜底，地址名由服务端解析（Key 后台可维护） */
async function chooseLocation(): Promise<void> {
  activePanel.value = "none";
  const loc = await resolveCurrentLocation();
  if (!loc) {
    // 明确区分「环境不支持」与「网络异常」：非 HTTPS 的 H5 拿不到定位权限
    info("当前环境无法获取位置：H5 需 HTTPS 访问并允许定位授权");
    return;
  }
  const addr = await resolveAddressName(loc.latitude, loc.longitude);
  const fallback = loc.source === "ip" ? "当前位置（城市级）" : "当前位置";
  const name = locationDisplayName(addr, fallback);
  pushMessage("location", name, {
    latitude: loc.latitude,
    longitude: loc.longitude,
  });
}

/** 点击位置气泡：打开地图查看 */
function openLocation(msg: Message): void {
  const lat = msg.extra?.latitude;
  const lng = msg.extra?.longitude;
  if (lat === undefined || lng === undefined) return;
  // #ifdef H5
  // H5 端 uni.openLocation 不支持，降级为复制地址 + 提示用户自行打开地图
  uni.setClipboardData({
    data: `${msg.content}（${lat},${lng}）`,
    success: () => info("地址已复制，可在地图应用中搜索"),
  });
  // #endif
  // #ifndef H5
  uni.openLocation({ latitude: lat, longitude: lng, name: msg.content });
  // #endif
}

/** 选联系人并发送名片（从好友列表选取） */
async function chooseCardContact(): Promise<void> {
  activePanel.value = "none";
  try {
    const result = await getContacts({ page: 1, pagesize: 50 });
    const contacts = result.items || [];
    cardContacts.value = contacts;
    cardPickerVisible.value = true;
  } catch {
    // request 层统一 toast
  }
}

/** 名片选择弹窗可见性 */
const cardPickerVisible = ref(false);
/** 名片选择联系人列表 */
const cardContacts = ref<Array<{ userId: string; nickname: string; avatar: string }>>([]);

/** 选中联系人并发送名片 */
function selectCardContact(c: { userId: string; nickname: string; avatar: string }): void {
  cardPickerVisible.value = false;
  pushMessage("card", c.nickname || c.userId, {
    userId: c.userId,
    avatar: c.avatar,
  });
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
    voicePlayProgress.value = 0;
    return;
  }
  audioContext?.destroy();
  const ctx = uni.createInnerAudioContext();
  audioContext = ctx;
  ctx.src = msg.content;
  playingVoiceId.value = msg.id;
  voicePlayProgress.value = 0;
  // 从 extra.duration 读取总时长（语音消息发送时已记录）
  voicePlayDuration.value = msg.extra?.duration || 0;
  ctx.play();
  // 播放进度更新：每 500ms 刷新一次进度条
  ctx.onTimeUpdate(() => {
    voicePlayProgress.value = ctx.currentTime || 0;
  });
  ctx.onEnded(() => {
    playingVoiceId.value = "";
    voicePlayProgress.value = 0;
  });
  ctx.onError(() => {
    playingVoiceId.value = "";
    voicePlayProgress.value = 0;
    fail("语音播放失败");
  });
}

/** 预览图片消息：传当前会话全部图片，支持左右滑动多图浏览（P2-5） */
function previewImg(url: string): void {
  const allImages = messageList.value
    .filter((m) => m.type === "image" && m.content)
    .map((m) => m.content);
  const urls = allImages.length > 0 ? allImages : [url];
  uni.previewImage({ urls, current: url });
}

/** 接收对方新消息回调（onUnmounted 注销） */
let unregisterMessage: (() => void) | null = null;
/** 历史消息是否已填充（填充前的实时消息进 pending 缓冲，填充后实时追加） */
const historyLoaded = ref(false);
/** 历史拉取失败标记：与「真的没有历史消息」的空白态区分，驱动错误空态 + 重试（S3） */
const historyLoadFailed = ref(false);
/** 历史消息加载中（驱动首屏骨架屏，避免空白感知为卡顿） */
const historyLoading = ref(true);
/** IM 连接失败标记：消息无法收发，显式错误态 + 重连入口（对齐客服页，禁止静默降级） */
const imLoginFailed = ref(false);
/** 已加载最旧消息的 seq（scrolltoupper 续拉起点，M-K4） */
const oldestSeq = ref(0);
/** 续拉加载中（防重复触发） */
const loadingMore = ref(false);
/** 没有更旧的历史（续拉返回空后置位，不再触发） */
const noMoreHistory = ref(false);
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
  historyLoading.value = true;
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
  // 合并缓冲：按时间排序
  const buffered = pendingMessages.map(toReceivedItem);
  const merged = [...historyItems, ...buffered].sort((a, b) => a.timestamp - b.timestamp);
  // 按业务指纹去重：历史(hist-)与在途(recv-)本地 id 前缀不同，按 id 无法识别同一条消息，
  // 用 归属+时间戳+类型+内容 指纹识别历史快照已包含的在途消息，避免重复显示
  const seen = new Set<string>();
  messageList.value = merged.filter((item) => {
    const fp = messageFingerprint(item);
    if (seen.has(fp)) return false;
    seen.add(fp);
    return true;
  });
  // 合并完成后清空缓冲并切到实时通道：后续到达消息直接追加，重试也不会重复合并
  pendingMessages = [];
  historyLoading.value = false;
  historyLoadFailed.value = false;
  historyLoaded.value = true;
  // 记录最旧消息 seq 作为续拉起点（history 已按时间正序，第一条最旧；M-K4）
  oldestSeq.value = history[0]?.seq || 0;
  noMoreHistory.value = false;
  scrollToBottom();
}

/** 重试拉取历史：仅重拉历史消息，禁止重跑 IM 登录/监听注册 */
async function retryLoadHistory(): Promise<void> {
  try {
    await loadHistory();
  } catch (err) {
    // catch 不空吞：保留错误空态 + ux 反馈（IM SDK 直连不走 request 层，无统一 toast）
    historyLoading.value = false;
    historyLoadFailed.value = true;
    warn(`[chat] 历史消息重试失败，userId=${userId.value}, err=${String(err)}`);
    fail("历史消息加载失败，请稍后重试");
  }
}

/** scrolltoupper 续拉更旧历史（M-K4）：加载后保持滚动位置不跳 */
async function loadMoreHistory(): Promise<void> {
  if (loadingMore.value || noMoreHistory.value || !oldestSeq.value || !historyLoaded.value) return;
  loadingMore.value = true;
  // 记录当前顶部消息 id：续拉后滚回此消息，保持视觉位置不跳
  const anchorId = messageList.value[0]?.id || "";
  try {
    const older = await getChatHistoryBefore(userId.value, oldestSeq.value);
    if (older.length === 0) {
      noMoreHistory.value = true;
      return;
    }
    const myId = String(userStore.userInfo?.id ?? "");
    const olderItems: Message[] = older.map((msg, i) => ({
      id: `hist-more-${oldestSeq.value}-${i}-${msg.time}`,
      type: msg.type,
      content: msg.content,
      extra: msg.extra,
      isMine: msg.from === myId,
      avatar: msg.from === myId ? myAvatar.value : peerAvatar.value,
      timestamp: msg.time,
      status: "read" as MsgStatus,
    }));
    // 指纹去重：续拉(hist-more-)与已加载消息(hist-/recv-)id 前缀不同，按 id 无法识别重叠，
    // 改用业务指纹，避免同一条消息在续拉后重复显示
    const existingFp = new Set(messageList.value.map(messageFingerprint));
    const deduped = olderItems.filter((m) => !existingFp.has(messageFingerprint(m)));
    if (deduped.length === 0) {
      noMoreHistory.value = true;
      return;
    }
    messageList.value = [...deduped, ...messageList.value];
    // 更新最旧 seq（续拉结果已按时间正序，第一条最旧）
    const newOldest = older[0];
    if (newOldest?.seq) oldestSeq.value = newOldest.seq;
    // 保持滚动位置：先清空再设回锚点 id，确保 scroll-into-view 重新触发
    if (anchorId) {
      scrollToId.value = "";
      nextTick(() => {
        scrollToId.value = "msg-" + anchorId;
      });
    }
  } catch (err) {
    // IM SDK 直连不走 request 层，无统一 toast
    warn(`[chat] 历史消息续拉失败，userId=${userId.value}, err=${String(err)}`);
    fail("历史消息加载失败，请稍后重试");
  } finally {
    loadingMore.value = false;
  }
}

onLoad(async (options: any) => {
  userId.value = options?.userId || "";
  nickname.value = options?.nickname || "聊天";
  uni.setNavigationBarTitle({ title: nickname.value });

  // 进入聊天页即已读：清零未读数，避免 tabbar badge 残留
  const unreadStore = useUnreadStore();
  unreadStore.clearUnread();
  // H5 全局 tabbar 由 global-tabbar.ts 管理，需同步清 DOM badge
  // #ifdef H5
  const matchItem = document.querySelector<HTMLElement>("#xfjy-global-tabbar .app-tabbar__item");
  matchItem?.querySelector(".app-tabbar__badge")?.remove();
  // #endif

  // 恢复最近使用表情
  try {
    const saved = uni.getStorageSync("chat_recent_emojis");
    if (Array.isArray(saved)) recentEmojis.value = saved.filter((e) => typeof e === "string");
  } catch {
    // 读取失败不影响主流程
  }

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

  // IM 连接 → 注册消息接收 → 拉历史（失败有错误态 + 重连入口，见 reconnectIM）
  await reconnectIM();
});

/**
 * IM 登录 + 消息监听 + 历史拉取（可重试），对齐客服页 kefu/chat.vue 范式。
 * 抽为独立函数供 onLoad 首次调用与 retryIMLogin 重试复用：
 * 登录失败显式错误态，不再静默降级导致整页消息永久发不出。
 */
async function reconnectIM(): Promise<void> {
  // 重试前先注销旧监听，避免重复注册导致消息被处理两次
  if (unregisterMessage) {
    unregisterMessage();
    unregisterMessage = null;
  }
  imLoginFailed.value = false;
  try {
    // 以「当前登录用户本人」身份连接（历史问题：曾以对方身份登录导致顶号、自发自收）
    const ok = await ensureSelfIM();
    if (!ok) {
      imLoginFailed.value = true;
      // 撒掉骨架屏：连接已失败，不再是「加载中」，避免骨架 + 错误空态 + toast 三重叠加（P1-2）
      historyLoading.value = false;
      warn(`[chat] IM 连接失败，userId=${userId.value}`);
      fail("消息服务连接失败，请重试");
      return;
    }
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
      // 页面在后台时弹系统通知（H5 Web Notification / MP 订阅消息）
      // #ifdef H5
      if (document.hidden) {
        showNotification(nickname.value, msg.content);
      }
      // #endif
    });

    // 历史拉取单独内层 try/catch：失败只置错误空态，不影响已完成的 IM 登录与消息监听
    try {
      await loadHistory();
    } catch (err) {
      historyLoadFailed.value = true;
      warn(`[chat] 历史消息拉取失败，userId=${userId.value}, err=${String(err)}`);
      fail("历史消息加载失败，请稍后重试");
    }
  } catch (err) {
    // ensureSelfIM 理论上不抛（内部全 catch），外层 catch 作防御性兜底
    imLoginFailed.value = true;
    warn(`[chat] IM 连接异常，userId=${userId.value}, err=${String(err)}`);
    fail("消息服务连接失败，请重试");
  }
}

/** 点击错误态「重新连接」：复用 reconnectIM 重跑登录+监听+历史拉取 */
function retryIMLogin(): void {
  void reconnectIM();
}

onHide(() => {
  // 离页（navigateTo 等）停止语音播放：不应在后台继续出声（M-K6）
  audioContext?.stop();
  // 离页中断在途录音，避免跑到自动时长发出非预期语音（M-K2）
  if (isVoicePressing.value) onVoiceCancel();
  // 离页清理计时器，避免页面隐藏后 setInterval 仍在跑
  clearVoiceTimer();
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
   z-index 低于 tabbar(998) 避免遮挡全局底栏。 */
.chat-room {
  position: fixed;
  top: 0;
  left: 0;
  right: 0;
  /* 底部让出全局 tabbar：H5 由 global-tabbar 写入其实测高度，输入栏落在底栏上方；
     小程序端无此变量，0px 兜底，chat-room 仍满屏（二级页原生无 tabBar，行为同现状） */
  bottom: var(--tab-bar-height, 0px);
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
  .time-text { font-size: 22rpx; color: var(--text-hint); background: var(--bg-card); padding: 8rpx 28rpx; border-radius: var(--radius-pill); box-shadow: 0 1rpx 4rpx rgba(0, 0, 0, 0.04); }
}

/* 历史加载失败重试按钮：default 变体零样式，这里补品牌色，颜色全走 CSS 变量适配暗色 */
.history-retry { margin-top: 32rpx; padding: 16rpx 56rpx; border-radius: var(--radius-pill);
  font-size: 28rpx; font-weight: 600; color: var(--text-inverse); background: var(--color-primary);
}

/* ===== 对方正在输入提示 ===== */
.typing-indicator { display: flex; align-items: center; margin-bottom: 20rpx; animation: fadeIn 0.2s ease-out; }
.load-more-history { text-align: center; padding: 16rpx 0; color: var(--text-hint); font-size: var(--fs-sm); }
.typing-btn { display: flex; align-items: center; gap: 12rpx; min-height: 72rpx; padding: 8rpx 20rpx 8rpx 8rpx; background: var(--bg-card); border-radius: var(--radius-pill); box-shadow: var(--shadow-card); }
.typing-avatar { width: 56rpx; height: 56rpx; border-radius: 50%; background: var(--border-light); flex-shrink: 0; }
.typing-bubble { display: flex; align-items: center; padding: 0 8rpx; }
.typing-dots { display: flex; align-items: center; gap: 6rpx; }
/* ease-out-quart 曲线：真实物体减速感，避免 bounce/elastic 的廉价回弹 */
.dot { width: 12rpx; height: 12rpx; border-radius: 50%; background: var(--text-hint); animation: typingPulse 1.2s cubic-bezier(0.25, 1, 0.5, 1) infinite; }
.dot:nth-child(2) { animation-delay: 0.15s; }
.dot:nth-child(3) { animation-delay: 0.3s; }
.typing-text { font-size: 24rpx; color: var(--text-hint); }
@keyframes typingPulse {
  0%, 60%, 100% { transform: translateY(0); opacity: 0.5; }
  30% { transform: translateY(-6rpx); opacity: 1; }
}
/* 头像：76rpx 已 ≥44px 热区基线；外层 a11y-button 补足可点击语义与键盘可达 */
.msg-item { display: flex; align-items: flex-start; margin-bottom: 28rpx; gap: 16rpx;
/* DOM 顺序 [对方头像, bubble-wrap, 我的头像]，普通 row：
   对方 → 头像在左、bubble 在右，整组靠左（默认 flex-start）；
   我的 → bubble 在左、头像在右，整组靠右（flex-end）。
   禁止加 row-reverse：DOM 顺序已含位置语义，反转后头像与 bubble 左右互换。 */
&.mine { justify-content: flex-end; }
.avatar-btn { flex-shrink: 0; display: flex; }
/* 圆形头像（探探式，两聊天页统一）；typing-avatar/card-avatar 本就 50% */
.avatar { width: 76rpx; height: 76rpx; border-radius: 50%; background: var(--border-light); }
  /* max-width: 75% 自适应屏幕宽度，同时保留 480rpx 上限防止宽屏过宽 */
.bubble-wrap { display: flex; align-items: center; gap: 12rpx; max-width: 75%; min-width: 0;
    /* 我的消息：status 图标（转圈/失败）应在 bubble 与头像之间，
       DOM [status, bubble] + row-reverse → [bubble, status]，status 靠右（近头像） */
    &.mine { flex-direction: row-reverse; }
  }
  .status-loading { width: 28rpx; height: 28rpx; border: 3rpx solid var(--border); border-top-color: var(--color-primary); border-radius: 50%; animation: spin 0.8s linear infinite; flex-shrink: 0; }
  /* 热区 88rpx：原 32rpx（≈19px）远低于 44px 可点击尺寸 */
  .status-failed { width: 88rpx; height: 88rpx; border-radius: 50%; background: var(--color-danger); display: flex; align-items: center; justify-content: center; flex-shrink: 0;
    .failed-icon { font-size: 22rpx; color: var(--text-inverse); font-weight: bold; line-height: 1; }
  }
  /* 探探式气泡（与客服聊天页统一）：无三角尾巴，靠不对称圆角做指向——
     对方头像在左→左上角收小 8rpx；我的头像在右→右上角收小；其余三角 32rpx 大圆角。
     min-width:0 + 断行：长文本/连续英文/URL 收在气泡内，否则会撑破 flex 容器导致页面横向滚动。
     扁平无阴影，靠白/粉底与灰底的色差区分（对标探探） */
  .bubble { position: relative; min-width: 0; overflow-wrap: anywhere; padding: 20rpx 24rpx; border-radius: var(--radius-sm) var(--radius-xl) var(--radius-xl) var(--radius-xl); background: var(--bg-card); font-size: 28rpx; line-height: 1.5; color: var(--text-primary);
    box-shadow: 0 2rpx 8rpx rgba(0, 0, 0, 0.06);
    /* 我的消息：纯品牌粉实心（无渐变）+ 白字，右上角收小靠头像侧 */
    &.mine { background: var(--color-primary); color: var(--text-inverse); border-radius: var(--radius-xl) var(--radius-sm) var(--radius-xl) var(--radius-xl); box-shadow: 0 2rpx 12rpx rgba(var(--color-primary-rgb), 0.25); }
    .msg-image { max-width: 360rpx; border-radius: var(--radius-sm); display: block; }
    /* 上传中遮罩：半透明黑底 + 居中提示，避免用户误以为已发送 */
    .image-uploading-mask { position: absolute; inset: 0; background: rgba(0, 0, 0, 0.45); border-radius: var(--radius-sm); display: flex; align-items: center; justify-content: center;
      .image-uploading-text { font-size: 24rpx; color: var(--text-inverse); }
    }

    /* 语音气泡 */
    .msg-voice { display: flex; align-items: center; gap: 12rpx; min-width: 180rpx;
      .voice-icon { font-size: 26rpx; }
      .voice-wave { display: flex; align-items: center; gap: 4rpx;
        .wave-bar { width: 4rpx; height: 16rpx; border-radius: 2rpx; background: currentColor; opacity: 0.6; transform-origin: center; }
        &.playing .wave-bar { animation: wave 0.8s ease-in-out infinite; }
      }
      .voice-duration { font-size: 24rpx; opacity: 0.85; }
      /* 播放进度条：底部细条，播放中显示 */
      .voice-progress { position: absolute; bottom: 8rpx; left: 20rpx; right: 20rpx; height: 4rpx; background: rgba(128, 128, 128, 0.3); border-radius: 2rpx; overflow: hidden;
        .voice-progress-bar { width: 100%; height: 100%; background: currentColor; border-radius: 2rpx; transform-origin: left center; transition: transform 0.3s linear; }
      }
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

/* 语音波形：原 height 动画每帧触发布局，改 scaleY 只走合成层；
   16rpx 基准 ×0.75/1.5 等价原 12rpx↔24rpx，视觉不变 */
@keyframes wave {
  0%, 100% { transform: scaleY(0.75); }
  50% { transform: scaleY(1.5); }
}

/* ===== 输入区 ===== */
.input-area { background: var(--bg-card); border-top: 1rpx solid var(--border-light); }
.input-bar { display: flex; align-items: center; gap: 12rpx; padding: 14rpx 16rpx; }
/* 88rpx 热区（§7.1 WCAG 2.5.5），原 64rpx≈32px 不达标 */
.icon-btn { width: 88rpx; height: 88rpx; display: flex; align-items: center; justify-content: center; font-size: 40rpx; border-radius: 50%; flex-shrink: 0;
  .icon-active { filter: drop-shadow(0 0 6rpx rgba(var(--color-primary-rgb), 0.6)); }
}
/* 发送按钮：与 icon-btn 同位切换，gap 已提供间距，仅防 flex 压缩 */
.send-btn { flex-shrink: 0; }
.text-input { flex: 1; height: 72rpx; background: var(--bg-sub); border-radius: var(--radius-pill); padding: 0 28rpx; font-size: 28rpx; transition: background 0.2s, box-shadow 0.2s;
  &.focus { background: var(--bg-card); box-shadow: 0 0 0 3rpx rgba(var(--color-primary-rgb), 0.2), 0 2rpx 8rpx rgba(var(--color-primary-rgb), 0.08); }
}
/* placeholder 颜色：与 change-phone/verify 等页面对齐，走 --text-hint */
.ph { color: var(--text-hint); }
/* 语音按钮：a11y-button default 变体（88rpx 热区），视觉由页面控制 */
.voice-btn { flex: 1; height: 72rpx; background: var(--bg-sub); border-radius: var(--radius-pill); display: flex; align-items: center; justify-content: center; font-size: 28rpx; color: var(--text-secondary);
  &:active { background: var(--border); }
  /* 录音中接近上限：文字变危险色提示用户主动停止 */
  &.recording {
    color: var(--color-danger);
  }
}

/* ===== 底部面板 ===== */
/* min-height 保证小屏可用，max-height 防止大屏过高挤压消息区 */
.panel { min-height: 380rpx; max-height: 50vh; background: var(--bg-page); border-top: 1rpx solid var(--border-light); animation: slideUp 0.2s ease-out; overflow: hidden; }
.emoji-panel .emoji-scroll { height: 100%; }
.emoji-grid { display: flex; flex-wrap: wrap; padding: 16rpx 12rpx; }
/* height 88rpx：满足 44px 最小可点击热区（原 80rpx≈40px 不达标），宽度 12.5% 一行 8 个不变 */
.emoji-item { width: 12.5%; height: 88rpx; display: flex; align-items: center; justify-content: center; font-size: 44rpx; }
/* 最近使用表情行 */
.emoji-recent-row { padding: 16rpx 12rpx 0;
  .emoji-recent-label { font-size: 22rpx; color: var(--text-hint); display: block; margin-bottom: 8rpx; padding-left: 4rpx; }
  .emoji-recent-list { display: flex; flex-wrap: wrap; }
  .emoji-recent-item { width: 12.5%; }
}

.more-grid { display: flex; flex-wrap: wrap; padding: 36rpx 16rpx; }
.more-item { width: 25%; display: flex; flex-direction: column; align-items: center; gap: 14rpx; }
.more-icon { width: 104rpx; height: 104rpx; border-radius: var(--radius-lg); display: flex; align-items: center; justify-content: center; font-size: 44rpx; box-shadow: var(--shadow-float); }
.more-label { font-size: 24rpx; color: var(--text-secondary); }

/* 名片选择弹窗 */
.card-picker { max-height: 60vh; overflow-y: auto; padding: 16rpx 0;
  .card-contact-item { display: flex; width: 100%; justify-content: flex-start; align-items: center; padding: 20rpx 24rpx; min-height: 88rpx;
    &:active { background: var(--bg-sub); }
  }
  .card-contact-avatar { width: 72rpx; height: 72rpx; border-radius: 50%; flex-shrink: 0; background: var(--bg-sub); }
  .card-contact-name { margin-left: 20rpx; font-size: 28rpx; color: var(--text-primary); flex: 1; min-width: 0; overflow: hidden; white-space: nowrap; text-overflow: ellipsis; }
  .card-picker-empty { text-align: center; padding: 48rpx 0; color: var(--text-hint); font-size: 26rpx; }
}
</style>
