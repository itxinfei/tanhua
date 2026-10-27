<template>
  <view class="chat-room">
    <!-- 消息列表 -->
    <scroll-view scroll-y class="msg-list" :scroll-into-view="scrollToId" :scroll-with-animation="true">
      <!-- VIP 专属客服通道标识（exclusive_service 权益） -->
      <view v-if="isVip" class="vip-banner">
        <text class="vip-crown">👑</text>
        <text class="vip-text">VIP 专属客服通道 · 优先服务</text>
      </view>
      <!-- 客服欢迎语 -->
      <view class="welcome-tip">
        <text>您好，我是心飞交友在线客服，很高兴为您服务 🎧</text>
      </view>
      <template v-for="msg in messageList" :key="msg.id">
        <view v-if="showTimeDivider(msg)" class="time-divider">
          <text class="time-text">{{ formatChatTime(msg.timestamp) }}</text>
        </view>
        <view :id="'msg-' + msg.id" class="msg-item" :class="{ mine: msg.isMine }">
          <view class="agent-avatar" v-if="!msg.isMine">🎧</view>
          <view class="bubble-wrap" :class="{ mine: msg.isMine }">
            <view v-if="msg.isMine && msg.status === 'sending'" class="status-loading" />
            <view class="bubble pop-in" :class="{ mine: msg.isMine }">
              <text>{{ msg.content }}</text>
            </view>
          </view>
          <image v-if="msg.isMine" class="avatar" :src="myAvatar" mode="aspectFill" />
        </view>
      </template>
    </scroll-view>

    <!-- 输入区 -->
    <view class="input-area safe-area-bottom">
      <!-- 快捷问题（仅新会话显示，发送首条后隐藏） -->
      <scroll-view v-if="messageList.length === 0" scroll-x class="quick-bar" :show-scrollbar="false">
        <view class="quick-list">
          <view
            v-for="q in QUICK_QUESTIONS"
            :key="q"
            class="quick-chip btn-feedback"
            @click="sendQuick(q)"
          >
            <text>{{ q }}</text>
          </view>
        </view>
      </scroll-view>
      <view class="input-bar">
        <input
          v-model="inputText"
          class="text-input"
          :class="{ focus: isInputFocus }"
          placeholder="请描述您的问题..."
          confirm-type="send"
          @confirm="sendText"
          @focus="isInputFocus = true"
          @blur="isInputFocus = false"
        />
        <view class="send-btn btn-feedback" :class="{ disabled: !inputText.trim() || sending }" @click="sendText">
          <text>发送</text>
        </view>
      </view>
    </view>
  </view>
</template>

<script setup lang="ts">
import { ref, nextTick, onUnmounted } from "vue";
import { onLoad } from "@dcloudio/uni-app";
import { useUserStore } from "@/store/user";
import { getHuanXinUser } from "@/api/chat";
import { getAgentApi } from "@/api/kefu";
import { getVipStatus } from "@/api/mine";
import {
  loginEasemob,
  onMessageReceived,
  sendTextMessage,
  getChatHistory,
} from "@/utils/easemob";

/** 默认头像 */
const DEFAULT_AVATAR = "/static/logo/logo-icon-transparent.png";
/** 时间分隔线最小间隔（分钟） */
const TIME_GAP_MINUTES = 5;
/** 快捷问题预设（与 FAQ 种子数据呼应，引导首次提问） */
const QUICK_QUESTIONS: string[] = [
  "如何修改绑定手机？",
  "VIP会员有哪些权益？",
  "如何注销账号？",
  "举报后会如何处理？",
];

/** 消息发送状态 */
type MsgStatus = "sending" | "sent" | "read";
/** 聊天消息结构（客服会话仅文本） */
interface Message {
  id: string;
  content: string;
  isMine: boolean;
  timestamp: number;
  status: MsgStatus;
}

const userStore = useUserStore();
const myAvatar = ref(DEFAULT_AVATAR);
const agentId = ref("");
const inputText = ref("");
const sending = ref(false);
const isInputFocus = ref(false);
const scrollToId = ref("");
const messageList = ref<Message[]>([]);
/** 是否 VIP（exclusive_service 专属客服标识） */
const isVip = ref(false);

/** 是否显示时间分隔线（与上一条间隔超过阈值） */
function showTimeDivider(msg: Message): boolean {
  const idx = messageList.value.indexOf(msg);
  if (idx <= 0) return false;
  return msg.timestamp - messageList.value[idx - 1].timestamp > TIME_GAP_MINUTES * 60 * 1000;
}

/** 格式化聊天时间：今天 HH:mm，昨天"昨天 HH:mm"，更早 MM-DD HH:mm */
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
  if (!text || sending.value) return;
  await doSend(text);
  inputText.value = "";
}

/** 点击快捷问题直接发送 */
async function sendQuick(text: string): Promise<void> {
  if (sending.value) return;
  await doSend(text);
}

/** 实际发送逻辑：追加消息并通过环信 SDK 发送 */
async function doSend(text: string): Promise<void> {
  sending.value = true;
  const msg: Message = {
    id: Date.now().toString(),
    content: text,
    isMine: true,
    timestamp: Date.now(),
    status: "sending",
  };
  messageList.value.push(msg);
  scrollToBottom();
  const ok = await sendTextMessage(agentId.value, text);
  msg.status = ok ? "sent" : "sending";
  if (!ok) {
    uni.showToast({ title: "发送失败，请稍后重试", icon: "none" });
  }
  sending.value = false;
}

/** 接收客服新消息回调（onUnmounted 注销） */
let unregisterMessage: (() => void) | null = null;

onLoad(async () => {
  myAvatar.value = userStore.userInfo?.avatar || DEFAULT_AVATAR;

  // 查询 VIP 状态（exclusive_service 专属客服标识，失败静默不阻断）
  try {
    const vip = await getVipStatus();
    isVip.value = (vip?.level ?? 0) > 0;
  } catch {}

  try {
    // 1. 获取客服环信账号
    const agent = await getAgentApi();
    agentId.value = agent.agentId;

    // 2. 环信 SDK 登录
    const credential = await getHuanXinUser();
    if (!credential.username || !credential.token) return;
    const ok = await loginEasemob(credential.username, credential.token);
    if (!ok) return;

    // 3. 拉取与客服的历史消息
    const history = await getChatHistory(agentId.value);
    const myId = credential.username;
    messageList.value = history.map((msg, i) => ({
      id: `hist-${i}-${msg.time}`,
      content: msg.content,
      isMine: msg.from === myId,
      timestamp: msg.time,
      status: "read" as MsgStatus,
    }));
    scrollToBottom();

    // 4. 注册消息到达回调：仅处理客服消息
    unregisterMessage = onMessageReceived((msg) => {
      if (msg.from !== agentId.value) return;
      messageList.value.push({
        id: `recv-${msg.time}`,
        content: msg.content,
        isMine: false,
        timestamp: msg.time,
        status: "read",
      });
      scrollToBottom();
    });
  } catch {
    // 环信登录失败静默降级
  }
});

onUnmounted(() => {
  if (unregisterMessage) {
    unregisterMessage();
    unregisterMessage = null;
  }
});
</script>

<style lang="scss" scoped>
.chat-room { height: 100vh; display: flex; flex-direction: column; background: #f5f5f5; }

.vip-banner { display: flex; align-items: center; justify-content: center; gap: 8rpx;
  margin: 24rpx 24rpx 0; padding: 16rpx 0;
  background: linear-gradient(90deg, #2b1a0e, #4a3220); border-radius: 16rpx;
  .vip-crown { font-size: 28rpx; }
  .vip-text { font-size: 24rpx; color: #e8c88a; letter-spacing: 1rpx; }
}

.welcome-tip { display: flex; justify-content: center; margin: 24rpx 0;
  text { font-size: 24rpx; color: #999; background: rgba(0, 0, 0, 0.05); padding: 10rpx 28rpx; border-radius: 24rpx; }
}

.msg-list { flex: 1; padding: 12rpx 24rpx; }

.time-divider { display: flex; justify-content: center; margin: 16rpx 0 24rpx;
  .time-text { font-size: 22rpx; color: #bbb; background: rgba(0, 0, 0, 0.05); padding: 6rpx 24rpx; border-radius: 24rpx; }
}

.msg-item { display: flex; align-items: flex-start; margin-bottom: 28rpx; gap: 16rpx;
  &.mine { flex-direction: row-reverse; }
  .avatar { width: 76rpx; height: 76rpx; border-radius: 16rpx; flex-shrink: 0; background: #eee; }
  .agent-avatar { width: 76rpx; height: 76rpx; border-radius: 16rpx; flex-shrink: 0; background: #fff0f5; display: flex; align-items: center; justify-content: center; font-size: 40rpx; }
  .bubble-wrap { display: flex; align-items: center; gap: 12rpx; max-width: 480rpx;
    &.mine { flex-direction: row-reverse; }
  }
  .status-loading { width: 28rpx; height: 28rpx; border: 3rpx solid #e0e0e0; border-top-color: #ff5e8e; border-radius: 50%; animation: spin 0.8s linear infinite; flex-shrink: 0; }
  .bubble { position: relative; padding: 20rpx 24rpx; border-radius: 20rpx; background: #fff; font-size: 28rpx; line-height: 1.5; color: #333; box-shadow: 0 2rpx 8rpx rgba(0, 0, 0, 0.04);
    &::before { content: ""; position: absolute; top: 28rpx; width: 0; height: 0; border-top: 10rpx solid transparent; border-bottom: 10rpx solid transparent; }
    &:not(.mine) { border-top-left-radius: 6rpx;
      &::before { left: -16rpx; border-right: 16rpx solid #fff; }
    }
    &.mine { background: linear-gradient(135deg, #ff5e8e, #ff8fab); color: #fff; border-top-right-radius: 6rpx;
      &::before { right: -16rpx; border-left: 16rpx solid #ff5e8e; }
    }
  }
}

@keyframes spin { to { transform: rotate(360deg); } }

.input-area { background: #fff; border-top: 1rpx solid #eee; }

.quick-bar { background: #fafafa; border-bottom: 1rpx solid #f0f0f0; }
.quick-list { display: flex; gap: 16rpx; padding: 16rpx 20rpx; }
.quick-chip { flex-shrink: 0; background: #fff; border: 1rpx solid #ffb3c9; border-radius: 32rpx; padding: 12rpx 28rpx;
  text { font-size: 24rpx; color: #ff5e8e; white-space: nowrap; }
}

.input-bar { display: flex; align-items: center; gap: 16rpx; padding: 14rpx 16rpx; }
.text-input { flex: 1; height: 72rpx; background: #f5f5f5; border-radius: 36rpx; padding: 0 28rpx; font-size: 28rpx; transition: all 0.2s;
  &.focus { background: #fff; box-shadow: 0 0 0 3rpx rgba(255, 94, 142, 0.35); }
}
.send-btn { padding: 0 36rpx; height: 72rpx; background: #ff5e8e; border-radius: 36rpx; display: flex; align-items: center; justify-content: center; flex-shrink: 0;
  text { color: #fff; font-size: 28rpx; font-weight: 500; }
  &.disabled { opacity: 0.5; }
}
</style>