<template>
  <view class="chat-room">
    <!-- 消息列表 -->
    <scroll-view scroll-y class="msg-list" :scroll-into-view="scrollToId" :scroll-with-animation="true">
      <template v-for="(msg, i) in messageList" :key="msg.id">
        <!-- 时间分隔线：首条或间隔超过阈值时显示 -->
        <view v-if="showTimeDivider(i)" class="time-divider">
          <text class="time-text">{{ formatChatTime(msg.timestamp) }}</text>
        </view>
        <!-- 单条消息 -->
        <view :id="'msg-' + msg.id" class="msg-item" :class="{ mine: msg.isMine }">
          <image v-if="!msg.isMine" class="avatar" :src="msg.avatar || DEFAULT_AVATAR" mode="aspectFill" />
          <view class="bubble-wrap" :class="{ mine: msg.isMine }">
            <view v-if="msg.isMine && msg.status === 'sending'" class="status-loading" />
            <view class="bubble pop-in" :class="{ mine: msg.isMine }">
              <text v-if="msg.type === 'text'">{{ msg.content }}</text>
              <image v-else-if="msg.type === 'image'" class="msg-image" :src="msg.content" mode="widthFix" @click="previewImg(msg.content)" />
              <!-- 语音：点击播放，播放中显示波动态 -->
              <view v-else-if="msg.type === 'voice'" class="msg-voice" @click="toggleVoicePlay(msg)">
                <text class="voice-icon">{{ playingVoiceId === msg.id ? "❚❚" : "▶" }}</text>
                <view class="voice-wave" :class="{ playing: playingVoiceId === msg.id }">
                  <text v-for="n in 4" :key="n" class="wave-bar" />
                </view>
                <text class="voice-duration">{{ msg.extra?.duration || 0 }}"</text>
              </view>
              <!-- 位置：点击打开地图查看 -->
              <view v-else-if="msg.type === 'location'" class="msg-location" @click="openLocation(msg)">
                <text class="location-icon">📍</text>
                <text class="location-address">{{ msg.content }}</text>
              </view>
              <!-- 名片：点击跳转对方主页 -->
              <view v-else-if="msg.type === 'card'" class="msg-card" @click="openCard(msg)">
                <image class="card-avatar" :src="msg.extra?.avatar || DEFAULT_AVATAR" mode="aspectFill" />
                <view class="card-info">
                  <text class="card-name">{{ msg.content }}</text>
                  <text class="card-hint">个人名片</text>
                </view>
              </view>
            </view>
          </view>
          <image v-if="msg.isMine" class="avatar" :src="msg.avatar || DEFAULT_AVATAR" mode="aspectFill" />
        </view>
      </template>
    </scroll-view>

    <!-- 输入区 -->
    <view class="input-area safe-area-bottom">
      <view class="input-bar">
        <!-- 语音/键盘切换 -->
        <view class="icon-btn btn-feedback" @click="toggleVoiceMode">
          <text>{{ isVoiceMode ? "⌨" : "🎤" }}</text>
        </view>
        <!-- 语音模式：按住说话 -->
        <view v-if="isVoiceMode" class="voice-btn btn-feedback" @touchstart="onVoiceStart" @touchend="onVoiceEnd">
          <text>{{ isVoicePressing ? "松开 发送" : "按住 说话" }}</text>
        </view>
        <!-- 文字模式：输入框 + 表情 -->
        <template v-else>
          <input v-model="inputText" class="text-input" :class="{ focus: isInputFocus }" placeholder="说点什么..." confirm-type="send" @confirm="sendText" @focus="isInputFocus = true" @blur="isInputFocus = false" />
          <view class="icon-btn btn-feedback" @click="togglePanel('emoji')">
            <text :class="{ 'icon-active': activePanel === 'emoji' }">😊</text>
          </view>
        </template>
        <!-- 更多面板 -->
        <view class="icon-btn btn-feedback" @click="togglePanel('more')">
          <text :class="{ 'icon-active': activePanel === 'more' }">➕</text>
        </view>
      </view>

      <!-- 表情面板 -->
      <view v-if="activePanel === 'emoji'" class="panel emoji-panel">
        <scroll-view scroll-y class="emoji-scroll">
          <view class="emoji-grid">
            <view v-for="emoji in EMOJI_LIST" :key="emoji" class="emoji-item btn-feedback" @click="insertEmoji(emoji)">
              <text>{{ emoji }}</text>
            </view>
          </view>
        </scroll-view>
      </view>

      <!-- 更多面板 -->
      <view v-if="activePanel === 'more'" class="panel more-panel">
        <view class="more-grid">
          <view v-for="action in QUICK_ACTIONS" :key="action.label" class="more-item btn-feedback" @click="action.handler">
            <view class="more-icon" :style="{ background: action.bg }"><text>{{ action.icon }}</text></view>
            <text class="more-label">{{ action.label }}</text>
          </view>
        </view>
      </view>
    </view>
  </view>
</template>

<script setup lang="ts">
import { ref, nextTick, onUnmounted } from "vue";
import { onLoad } from "@dcloudio/uni-app";
import { useUserStore } from "@/store/user";
import { getHuanXinUser, uploadChatImage, uploadChatAudio, getContacts } from "@/api/chat";
import { getUserProfile } from "@/api/mine";
import {
  loginEasemob,
  onMessageReceived,
  sendTextMessage,
  sendStructuredMessage,
  getChatHistory,
} from "@/utils/easemob";

/** 默认头像路径 */
const DEFAULT_AVATAR = "/static/logo/logo-icon-transparent.png";
/** 时间分隔线最小间隔（分钟） */
const TIME_GAP_MINUTES = 5;

/** 表情面板数据 */
const EMOJI_LIST: string[] = [
  "😊", "😂", "❤️", "👍", "😍", "🥰", "😘", "💕",
  "🌹", "🎉", "🔥", "✨", "😭", "😅", "🤔", "😎",
  "🙌", "💪", "🙏", "🤝", "💋", "🎁", "☕", "🌙",
];

/** 更多面板快捷操作配置（表驱动） */
interface QuickAction { icon: string; label: string; bg: string; handler: () => void; }
const QUICK_ACTIONS: QuickAction[] = [
  { icon: "🖼️", label: "图片", bg: "#ff5e9c", handler: chooseImage },
  { icon: "📸", label: "拍照", bg: "#4a90d9", handler: () => showTodoToast("拍照") },
  { icon: "📍", label: "位置", bg: "#4cd964", handler: chooseLocation },
  { icon: "👤", label: "名片", bg: "#a78bfa", handler: chooseCardContact },
];

/** 消息发送状态 */
type MsgStatus = "sending" | "sent" | "read";
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

/** 语音消息最长录制时长（ms） */
const MAX_VOICE_DURATION_MS = 60000;
/** 有效语音最短时长（ms），过短视为误触 */
const MIN_VOICE_DURATION_MS = 1000;

/** 录音管理器（单例） */
const recorderManager = uni.getRecorderManager();
/** 正在播放的语音消息 id（空=无） */
const playingVoiceId = ref("");
/** 音频播放上下文（同一时刻仅一个） */
let audioContext: UniApp.InnerAudioContext | null = null;
/** 录音起始时间戳（计算有效时长用） */
let voiceStartAt = 0;

// 录音完成：上传 → 以 JSON 管道发送（type:"voice", url, duration）
recorderManager.onStop((res) => {
  const duration = Date.now() - voiceStartAt;
  if (duration < MIN_VOICE_DURATION_MS) {
    uni.showToast({ title: "说话时间太短", icon: "none" });
    return;
  }
  if (!res.tempFilePath) return;
  pushMessage("voice", res.tempFilePath, { duration: Math.round(duration / 1000) });
});

/** 语音按钮按下：开始录音 */
function onVoiceStart(): void {
  isVoicePressing.value = true;
  voiceStartAt = Date.now();
  try {
    recorderManager.start({ format: "mp3", duration: MAX_VOICE_DURATION_MS });
  } catch {
    isVoicePressing.value = false;
    uni.showToast({ title: "当前设备不支持录音", icon: "none" });
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

/** 发送文字消息 */
async function sendText(): Promise<void> {
  const text = inputText.value.trim();
  if (!text) return;
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
  });
}
/**
 * 追加消息并通过环信 SDK 发送（失败时标记状态保持 sending）
 * 图片/语音：先本地预览，OSS 上传成功后发 URL，成功后把气泡替换为远程地址
 * @param type 消息类型
 * @param content 消息内容（图片/语音为本地临时路径）
 * @param extra 结构化附加字段
 */
async function pushMessage(
  type: Message["type"],
  content: string,
  extra?: Record<string, any>
): Promise<void> {
  const msg: Message = {
    id: Date.now().toString(),
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
  if (type === "image" || type === "voice") {
    // 媒体消息：OSS 上传 → 环信发 URL（JSON 文本承载）
    const url = type === "voice" ? await uploadChatAudio(content) : await uploadChatImage(content);
    if (!url) {
      msg.status = "sending";
      uni.showToast({ title: "上传失败，请稍后重试", icon: "none" });
      return;
    }
    msg.content = url;
    const payload =
      type === "voice"
        ? { type: "voice", url, duration: extra?.duration || 0 }
        : { type: "image", url };
    const ok = await sendStructuredMessage(userId.value, payload);
    msg.status = ok ? "sent" : "sending";
    if (!ok) {
      uni.showToast({ title: "发送失败，请稍后重试", icon: "none" });
    }
    return;
  }
  if (type === "location" || type === "card") {
    // 位置/名片：结构化 JSON 承载（与接收端解析契约一致）
    const payload =
      type === "location"
        ? { type: "location", latitude: extra?.latitude, longitude: extra?.longitude, address: content }
        : { type: "card", userId: extra?.userId, nickname: content, avatar: extra?.avatar || "" };
    const ok = await sendStructuredMessage(userId.value, payload);
    msg.status = ok ? "sent" : "sending";
    if (!ok) {
      uni.showToast({ title: "发送失败，请稍后重试", icon: "none" });
    }
    return;
  }
  const ok = await sendTextMessage(userId.value, content);
  msg.status = ok ? "sent" : "sending";
  if (!ok) {
    uni.showToast({ title: "发送失败，请稍后重试", icon: "none" });
  }
}

/** 插入表情到输入框 */
function insertEmoji(emoji: string): void {
  inputText.value += emoji;
}

/** 切换语音/文字输入模式 */
function toggleVoiceMode(): void {
  isVoiceMode.value = !isVoiceMode.value;
  activePanel.value = "none";
}

/** 切换底部面板（同一面板再次点击则收起） */
function togglePanel(panel: PanelType): void {
  activePanel.value = activePanel.value === panel ? "none" : panel;
  isVoiceMode.value = false;
}

/** 语音按钮松开：停止录音，onStop 回调中上传发送 */
function onVoiceEnd(): void {
  isVoicePressing.value = false;
  recorderManager.stop();
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
        uni.showToast({ title: "当前平台暂不支持选点", icon: "none" });
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
      uni.showToast({ title: "暂无好友可分享", icon: "none" });
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
  uni.navigateTo({ url: `/pages/match/detail?userId=${cardUserId}` });
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
    uni.showToast({ title: "语音播放失败", icon: "none" });
  });
}

/** 预览图片消息 */
function previewImg(url: string): void {
  uni.previewImage({ urls: [url] });
}

/** 未完成功能占位提示 */
function showTodoToast(feature: string): void {
  uni.showToast({ title: `${feature}功能开发中`, icon: "none" });
}

/** 接收对方新消息回调（onUnmounted 注销） */
let unregisterMessage: (() => void) | null = null;

onLoad(async (options: any) => {
  userId.value = options?.userId || "";
  nickname.value = options?.nickname || "聊天";
  uni.setNavigationBarTitle({ title: nickname.value });

  // 当前用户头像
  myAvatar.value = userStore.userInfo?.avatar || DEFAULT_AVATAR;

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

  // 环信 SDK 登录 → 拉历史 → 注册消息接收
  try {
    const credential = await getHuanXinUser();
    if (credential.username && credential.token) {
      const ok = await loginEasemob(credential.username, credential.token);
      if (ok) {
        // 拉取历史消息填充列表
        const history = await getChatHistory(userId.value);
        const myId = credential.username;
        messageList.value = history.map((msg, i) => ({
          id: `hist-${i}-${msg.time}`,
          type: msg.type,
          content: msg.content,
          extra: msg.extra,
          isMine: msg.from === myId,
          avatar: msg.from === myId ? myAvatar.value : peerAvatar.value,
          timestamp: msg.time,
          status: "read" as MsgStatus,
        }));
        scrollToBottom();

        // 注册消息到达回调：仅处理当前会话对方的消息
        unregisterMessage = onMessageReceived((msg) => {
          if (msg.from !== userId.value) return;
          messageList.value.push({
            id: `recv-${msg.time}`,
            type: msg.type,
            content: msg.content,
            extra: msg.extra,
            isMine: false,
            avatar: peerAvatar.value,
            timestamp: msg.time,
            status: "read",
          });
          scrollToBottom();
        });
      }
    }
  } catch {
    // 环信登录失败静默降级（可发但不达，列表为空）
  }
});

onUnmounted(() => {
  if (unregisterMessage) {
    unregisterMessage();
    unregisterMessage = null;
  }
  audioContext?.destroy();
  audioContext = null;
});
</script>

<style lang="scss" scoped>
.chat-room { height: 100vh; display: flex; flex-direction: column; background: #f5f5f5; }

/* ===== 消息列表 ===== */
.msg-list { flex: 1; padding: 24rpx 24rpx 12rpx; }

.time-divider { display: flex; justify-content: center; margin: 16rpx 0 24rpx;
  .time-text { font-size: 22rpx; color: #bbb; background: rgba(0, 0, 0, 0.05); padding: 6rpx 24rpx; border-radius: 24rpx; }
}

.msg-item { display: flex; align-items: flex-start; margin-bottom: 28rpx; gap: 16rpx;
  &.mine { flex-direction: row-reverse; }
  .avatar { width: 76rpx; height: 76rpx; border-radius: 16rpx; flex-shrink: 0; background: #eee; }
  .bubble-wrap { display: flex; align-items: center; gap: 12rpx; max-width: 480rpx;
    &.mine { flex-direction: row-reverse; }
  }
  .status-loading { width: 28rpx; height: 28rpx; border: 3rpx solid #e0e0e0; border-top-color: #ff5e9c; border-radius: 50%; animation: spin 0.8s linear infinite; flex-shrink: 0; }
  .bubble { position: relative; padding: 20rpx 24rpx; border-radius: 20rpx; background: #fff; font-size: 28rpx; line-height: 1.5; color: #333; box-shadow: 0 2rpx 8rpx rgba(0, 0, 0, 0.04);
    /* 气泡尾巴 */
    &::before { content: ""; position: absolute; top: 28rpx; width: 0; height: 0; border-top: 10rpx solid transparent; border-bottom: 10rpx solid transparent; }
    &:not(.mine) { border-top-left-radius: 6rpx;
      &::before { left: -16rpx; border-right: 16rpx solid #fff; }
    }
    &.mine { background: linear-gradient(135deg, #ff5e9c, #ff8fb1); color: #fff; border-top-right-radius: 6rpx;
      &::before { right: -16rpx; border-left: 16rpx solid #ff5e9c; }
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
      .card-avatar { width: 72rpx; height: 72rpx; border-radius: 50%; background: #f0f0f0; }
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
.input-area { background: #fff; border-top: 1rpx solid #eee; }
.input-bar { display: flex; align-items: center; gap: 12rpx; padding: 14rpx 16rpx; }
.icon-btn { width: 64rpx; height: 64rpx; display: flex; align-items: center; justify-content: center; font-size: 40rpx; border-radius: 50%; flex-shrink: 0;
  .icon-active { filter: drop-shadow(0 0 6rpx rgba(255, 94, 142, 0.6)); }
}
.text-input { flex: 1; height: 72rpx; background: #f5f5f5; border-radius: 36rpx; padding: 0 28rpx; font-size: 28rpx; transition: all 0.2s;
  &.focus { background: #fff; box-shadow: 0 0 0 3rpx rgba(255, 94, 142, 0.35); }
}
.voice-btn { flex: 1; height: 72rpx; background: #f5f5f5; border-radius: 36rpx; display: flex; align-items: center; justify-content: center; font-size: 28rpx; color: #666; transition: all 0.15s;
  &:active { background: #e8e8e8; transform: scale(0.98); }
}

/* ===== 底部面板 ===== */
.panel { height: 380rpx; background: #f7f7f7; border-top: 1rpx solid #eee; animation: slideUp 0.2s ease-out; overflow: hidden; }
@keyframes slideUp { from { transform: translateY(100%); } to { transform: translateY(0); } }

.emoji-panel .emoji-scroll { height: 100%; }
.emoji-grid { display: flex; flex-wrap: wrap; padding: 16rpx 12rpx; }
.emoji-item { width: 12.5%; height: 80rpx; display: flex; align-items: center; justify-content: center; font-size: 44rpx; }

.more-grid { display: flex; flex-wrap: wrap; padding: 36rpx 16rpx; }
.more-item { width: 25%; display: flex; flex-direction: column; align-items: center; gap: 14rpx; }
.more-icon { width: 104rpx; height: 104rpx; border-radius: 24rpx; display: flex; align-items: center; justify-content: center; font-size: 44rpx; box-shadow: 0 6rpx 16rpx rgba(0, 0, 0, 0.08); }
.more-label { font-size: 24rpx; color: #666; }
</style>