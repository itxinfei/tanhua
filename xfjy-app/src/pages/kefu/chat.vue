<template>
  <view class="chat-room">
    <!-- 消息列表 -->
    <scroll-view scroll-y class="msg-list" :scroll-into-view="scrollToId" :scroll-with-animation="true">
      <!-- VIP 专属客服通道标识（exclusive_service 权益） -->
      <view v-if="isVip" class="vip-banner">
        <uni-icons class="vip-crown" type="vip" size="28"></uni-icons>
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
        <!-- 装饰性 emoji 头像，语义由上下文承载 -->
        <view class="agent-avatar" v-if="!msg.isMine" aria-hidden="true"><uni-icons type="headphones" size="40" color="var(--color-primary)"></uni-icons></view>
        <view class="bubble-wrap" :class="{ mine: msg.isMine }">
          <view v-if="msg.isMine && msg.status === 'sending'" class="status-loading" aria-hidden="true" />
          <!-- 失败：红点提示 + 点击重发 -->
          <a11y-button
            v-else-if="msg.isMine && msg.status === 'failed'"
            class="status-failed"
            :label="`发送失败，重新发送：${msg.content}`"
            @tap="retrySend(msg)"
          >
            <text>!</text>
          </a11y-button>
          <view class="bubble pop-in" :class="{ mine: msg.isMine }">
            <text>{{ msg.content }}</text>
          </view>
        </view>
        <image lazy-load v-if="msg.isMine" class="avatar" :src="myAvatar" mode="aspectFill" aria-label="我的头像" />
        </view>
      </template>
    </scroll-view>

    <!-- 输入区 -->
    <view class="input-area safe-area-bottom">
      <!-- 快捷问题（仅新会话显示，发送首条后隐藏） -->
      <scroll-view v-if="messageList.length === 0" scroll-x class="quick-bar" :show-scrollbar="false">
        <view class="quick-list">
          <a11y-button
            v-for="q in QUICK_QUESTIONS"
            :key="q"
            class="quick-chip btn-feedback"
            :label="`发送快捷问题：${q}`"
            @tap="sendQuick(q)"
          >
            <text>{{ q }}</text>
          </a11y-button>
        </view>
      </scroll-view>
      <view class="input-bar">
        <input
          v-model="inputText"
          class="text-input"
          :class="{ focus: isInputFocus }"
          placeholder="请描述您的问题..."
          aria-label="客服聊天输入框"
          confirm-type="send"
          @confirm="sendText"
          @focus="isInputFocus = true"
          @blur="isInputFocus = false"
        />
        <a11y-button class="send-btn btn-feedback" :class="{ disabled: !inputText.trim() || sending }" :disabled="!inputText.trim() || sending" label="发送消息" @tap="sendText">
          <text>发送</text>
        </a11y-button>
      </view>
    </view>
  </view>
</template>

<script setup lang="ts">
import { ref, nextTick, onUnmounted } from "vue";
import { onLoad } from "@dcloudio/uni-app";
import A11yButton from "@/components/a11y-button.vue";
import { useUserStore } from "@/store/user";
import { getAgentApi } from "@/api/kefu";
import { getVipStatus } from "@/api/mine";
import {
  ensureSelfIM,
  onMessageReceived,
  sendTextMessage,
  getChatHistory,
  type ReceivedMessage,
} from "@/utils/wukongim";
import { fail } from "@/utils/ux";
import { resolveAvatar, DEFAULT_AVATAR } from "@/utils/avatar";

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
type MsgStatus = "sending" | "sent" | "read" | "failed";
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
  inputText.value = "";
  await sendMessage(text);
}

/** 点击快捷问题直接发送 */
async function sendQuick(text: string): Promise<void> {
  if (sending.value) return;
  await sendMessage(text);
}

/** 本地消息自增序号（避免 Date.now() 同毫秒撞 id） */
let msgSeq = 0;

/** 发送新消息：追加气泡 → SDK 发送 → 按结果置 sent/failed */
async function sendMessage(text: string): Promise<void> {
  const msg: Message = {
    id: `local-${Date.now()}-${++msgSeq}`,
    content: text,
    isMine: true,
    timestamp: Date.now(),
    status: "sending",
  };
  messageList.value.push(msg);
  scrollToBottom();
  await deliver(msg);
}

/** 失败重发：复用原气泡 */
async function retrySend(msg: Message): Promise<void> {
  if (sending.value) return;
  await deliver(msg);
}

/** 实际投递：成功置 sent，失败置 failed（可点击重发） */
async function deliver(msg: Message): Promise<void> {
  sending.value = true;
  msg.status = "sending";
  const ok = await sendTextMessage(agentId.value, msg.content);
  msg.status = ok ? "sent" : "failed";
  if (!ok) {
    fail("发送失败，点击气泡旁的 ! 重发");
  }
  sending.value = false;
}

/** 接收客服新消息回调（onUnmounted 注销） */
let unregisterMessage: (() => void) | null = null;

onLoad(async () => {
  myAvatar.value = resolveAvatar(userStore.userInfo?.avatar);

  // 查询 VIP 状态（exclusive_service 专属客服标识，失败静默不阻断）
  try {
    const vip = await getVipStatus();
    isVip.value = (vip?.level ?? 0) > 0;
  } catch {}

  try {
    // 1. 获取客服 IM 账号
    const agent = await getAgentApi();
    agentId.value = agent.agentId;

    // 2. 以「用户本人」身份连接 WuKongIM（消息发给客服账号 kefu0001）；
    //    严禁以客服身份登录——会顶掉 admin 工作台且消息 from_uid 错乱
    const ok = await ensureSelfIM();
    if (!ok) return;

    // 3. 先注册消息到达回调再拉历史：历史是最后一次性赋值的，
    //    在途实时消息先进 pending 缓冲，避免注册晚于拉取导致消息丢失
    const pending: ReceivedMessage[] = [];
    let historyLoaded = false;
    unregisterMessage = onMessageReceived((msg) => {
      if (msg.from !== agentId.value) return;
      if (historyLoaded) {
        messageList.value.push({
          id: `recv-${msg.time}`,
          content: msg.content,
          isMine: false,
          timestamp: msg.time,
          status: "read",
        });
        scrollToBottom();
      } else {
        pending.push(msg);
      }
    });

    // 4. 拉取与客服的历史消息
    const history = await getChatHistory(agentId.value);
    // WuKongIM 直连登录后无凭证对象，我的 uid 即当前登录用户 ID（消息归属判断用）
    const myId = String(userStore.userInfo?.id ?? "");
    const historyItems: Message[] = history.map((msg, i) => ({
      id: `hist-${i}-${msg.time}`,
      content: msg.content,
      isMine: msg.from === myId,
      timestamp: msg.time,
      status: "read" as MsgStatus,
    }));
    // 合并缓冲：按时间排序并以 timestamp 去重（历史可能已含在途消息）
    const buffered: Message[] = pending.map((msg) => ({
      id: `recv-${msg.time}`,
      content: msg.content,
      isMine: false,
      timestamp: msg.time,
      status: "read" as MsgStatus,
    }));
    const merged = [...historyItems, ...buffered].sort((a, b) => a.timestamp - b.timestamp);
    const seen = new Set<number>();
    messageList.value = merged.filter((item) => {
      if (seen.has(item.timestamp)) return false;
      seen.add(item.timestamp);
      return true;
    });
    historyLoaded = true;
    scrollToBottom();
  } catch {
    // IM（WuKongIM）登录失败静默降级
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
.chat-room { height: 100vh; display: flex; flex-direction: column; background: var(--bg-sub); }

.vip-banner { display: flex; align-items: center; justify-content: center; gap: 8rpx;
  margin: 24rpx 24rpx 0; padding: 16rpx 24rpx;
  background: linear-gradient(135deg, var(--color-primary-deep), var(--color-primary)); border-radius: 20rpx;
  box-shadow: 0 4rpx 16rpx rgba(255, 94, 156, 0.25);
  .vip-crown { font-size: 28rpx; color: var(--text-inverse); }
  .vip-text { font-size: 24rpx; color: var(--text-inverse); letter-spacing: 1rpx; font-weight: 500; }
}

.welcome-tip { display: flex; justify-content: center; margin: 24rpx 0;
  text { font-size: 24rpx; color: var(--text-hint); background: rgba(0, 0, 0, 0.05); padding: 10rpx 28rpx; border-radius: 24rpx; }
}

.msg-list { flex: 1; padding: 12rpx 24rpx; }

.time-divider { display: flex; justify-content: center; margin: 16rpx 0 24rpx;
  .time-text { font-size: 22rpx; color: var(--text-hint); background: rgba(0, 0, 0, 0.05); padding: 6rpx 24rpx; border-radius: 24rpx; }
}

.msg-item { display: flex; align-items: flex-start; margin-bottom: 28rpx; gap: 16rpx;
  &.mine { flex-direction: row-reverse; }
  .avatar { width: 76rpx; height: 76rpx; border-radius: 16rpx; flex-shrink: 0; background: var(--border-light); }
  .agent-avatar { width: 76rpx; height: 76rpx; border-radius: 16rpx; flex-shrink: 0; background: var(--bg-icon-soft); display: flex; align-items: center; justify-content: center; font-size: 40rpx; }
  .bubble-wrap { display: flex; align-items: center; gap: 12rpx; max-width: 70%;
    &.mine { flex-direction: row-reverse; }
  }
  .status-loading { width: 28rpx; height: 28rpx; border: 3rpx solid var(--border); border-top-color: var(--color-primary); border-radius: 50%; animation: spin 0.8s linear infinite; flex-shrink: 0; }
  .status-failed { width: 36rpx; height: 36rpx; border-radius: 50%; background: var(--color-danger); display: flex; align-items: center; justify-content: center; flex-shrink: 0;
    text { font-size: 24rpx; color: var(--text-inverse); font-weight: bold; line-height: 1; }
  }
  .bubble { position: relative; max-width: 70%; padding: 20rpx 24rpx; border-radius: 24rpx; background: var(--bg-card); font-size: 28rpx; line-height: 1.5; color: var(--text-primary); box-shadow: 0 2rpx 8rpx rgba(0, 0, 0, 0.04);
    &::before { content: ""; position: absolute; top: 24rpx; width: 0; height: 0; border-top: 10rpx solid transparent; border-bottom: 10rpx solid transparent; }
    &:not(.mine) { border-top-left-radius: 6rpx;
      &::before { left: -14rpx; border-right: 14rpx solid var(--bg-card); }
    }
    &.mine { background: var(--color-primary); color: var(--bg-card); border-top-right-radius: 6rpx;
      &::before { right: -14rpx; border-left: 14rpx solid var(--color-primary); }
    }
  }
}

@keyframes spin { to { transform: rotate(360deg); } }

.input-area { background: var(--bg-card); border-top: 1rpx solid var(--border-light); }

.quick-bar { background: var(--bg-sub); border-bottom: 1rpx solid var(--border-light); }
.quick-list { display: flex; gap: 16rpx; padding: 16rpx 20rpx; }
/* 快捷问题 chip：#fe3c72 on #fff ≈3.4:1 → #c2185b */
.quick-chip { flex-shrink: 0; background: var(--bg-card); border: 1rpx solid var(--color-primary-mist); border-radius: 32rpx; padding: 14rpx 28rpx; min-height: 48rpx;
  text { font-size: 24rpx; color: var(--color-primary-text); white-space: nowrap; }
}

.input-bar { display: flex; align-items: center; gap: 16rpx; padding: 14rpx 16rpx; }
.text-input { flex: 1; height: 72rpx; background: var(--bg-sub); border-radius: 36rpx; padding: 0 28rpx; font-size: 28rpx; transition: all 0.2s;
  &.focus { background: var(--bg-card); box-shadow: 0 0 0 3rpx rgba(255, 94, 142, 0.35); }
}
/* 发送按钮：禁用态仅 opacity 会让对比度跌破阈值 → 改用不透明深灰背景（WCAG 1.4.1 不依赖单一视觉线索） */
.send-btn { padding: 0 36rpx; height: 72rpx; background: var(--color-primary); border-radius: 36rpx; display: flex; align-items: center; justify-content: center; flex-shrink: 0; box-shadow: 0 4rpx 14rpx rgba(255, 94, 156, 0.3);
  text { color: var(--text-inverse); font-size: 28rpx; font-weight: 500; }
  &.disabled { background: var(--border); box-shadow: none; }
}
</style>
