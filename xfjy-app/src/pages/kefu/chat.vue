<template>
  <view class="chat-room">
    <!-- 消息列表 -->
    <scroll-view
      scroll-y
      class="msg-list"
      :scroll-into-view="scrollToId"
      :scroll-with-animation="true"
    >
      <!-- VIP 专属客服通道标识（exclusive_service 权益） -->
      <view
        v-if="isVip"
        class="vip-banner"
      >
        <uni-icons
          class="vip-crown"
          type="vip"
          size="28"
        />
        <text class="vip-text">
          VIP 专属客服通道 · 优先服务
        </text>
      </view>
      <!-- 客服欢迎语 -->
      <view class="welcome-tip">
        <text>您好，我是心飞交友在线客服，很高兴为您服务 🎧</text>
      </view>
      <template
        v-for="msg in messageList"
        :key="msg.id"
      >
        <view
          v-if="showTimeDivider(msg)"
          class="time-divider"
        >
          <text class="time-text">
            {{ formatChatTime(msg.timestamp) }}
          </text>
        </view>
        <view
          :id="'msg-' + msg.id"
          class="msg-item"
          :class="{ mine: msg.isMine }"
        >
          <!-- 装饰性 emoji 头像，语义由上下文承载 -->
          <view
            v-if="!msg.isMine"
            class="agent-avatar"
            aria-hidden="true"
          >
            <uni-icons
              type="headphones"
              size="40"
              color="var(--color-primary)"
            />
          </view>
          <view
            class="bubble-wrap"
            :class="{ mine: msg.isMine }"
          >
            <view
              v-if="msg.isMine && msg.status === 'sending'"
              class="status-loading"
              aria-hidden="true"
            />
            <!-- 失败：红点提示 + 点击重发 -->
            <a11y-button
              v-else-if="msg.isMine && msg.status === 'failed'"
              class="status-failed"
              :label="`发送失败，重新发送：${msg.content}`"
              @tap="retrySend(msg)"
            >
              <text>!</text>
            </a11y-button>
            <view
              class="bubble pop-in"
              :class="{ mine: msg.isMine }"
            >
              <text>{{ msg.content }}</text>
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
              :src="myAvatar"
              mode="aspectFill"
              aria-hidden="true"
            />
          </a11y-button>
        </view>
      </template>

      <!-- 历史拉取失败：与「真的没有历史消息」的欢迎语/快捷问题空态区分；重试只重拉历史，不重跑 IM 登录。
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
      <!-- 快捷问题（仅新会话显示，发送首条后隐藏） -->
      <scroll-view
        v-if="messageList.length === 0"
        scroll-x
        class="quick-bar"
        :show-scrollbar="false"
      >
        <view class="quick-list">
          <a11y-button
            v-for="q in QUICK_QUESTIONS"
            :key="q"
            class="quick-chip"
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
        >
        <a11y-button
          class="send-btn"
          :class="{ disabled: !inputText.trim() || sending }"
          :disabled="!inputText.trim() || sending"
          label="发送消息"
          @tap="sendText"
        >
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
import EmptyState from "@/components/empty-state.vue";
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
/** 历史消息是否已填充（填充前的实时消息进 pending 缓冲，填充后实时追加）。
 *  原 onLoad 局部变量提升为 ref：重试 loadHistory 与消息回调需共享同一状态 */
const historyLoaded = ref(false);
/** 历史拉取失败标记：与「真的没有历史消息」的欢迎语/快捷问题空态区分，驱动错误空态 + 重试（S3） */
const historyLoadFailed = ref(false);
/** 在途实时消息缓冲：历史填充前到达的消息先缓存，loadHistory 合并后清空，供重试复用 */
let pendingMessages: ReceivedMessage[] = [];

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

/**
 * 拉取历史消息并与在途缓冲合并（独立函数，失败可单独重试）。
 * 重试只重拉历史：不重跑 IM 登录、不重复注册 onMessageReceived（监听在 onLoad 只注册一次）。
 */
async function loadHistory(): Promise<void> {
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
  const buffered: Message[] = pendingMessages.map((msg) => ({
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
    console.warn(`[kefu-chat] 历史消息重试失败，agentId=${agentId.value}, err=${String(err)}`);
    fail("历史消息加载失败，请稍后重试");
  }
}

onLoad(async () => {
  myAvatar.value = resolveAvatar(userStore.userInfo?.avatar);

  // 查询 VIP 状态（exclusive_service 专属客服标识，失败静默不阻断）
  try {
    const vip = await getVipStatus();
    isVip.value = (vip?.level ?? 0) > 0;
  } catch {
    // VIP 状态查询失败不阻断客服会话
  }

  try {
    // 1. 获取客服 IM 账号
    const agent = await getAgentApi();
    agentId.value = agent.agentId;

    // 2. 以「用户本人」身份连接 WuKongIM（消息发给客服账号 kefu0001）；
    //    严禁以客服身份登录——会顶掉 admin 工作台且消息 from_uid 错乱
    const ok = await ensureSelfIM();
    if (!ok) return;

    // 3. 先注册消息到达回调再拉历史：历史是最后一次性赋值的，
    //    在途实时消息先进 pending 缓冲，避免注册晚于拉取导致消息丢失。
    //    监听全程只注册这一次，重试历史拉取不会重复注册。
    unregisterMessage = onMessageReceived((msg) => {
      if (msg.from !== agentId.value) return;
      if (historyLoaded.value) {
        messageList.value.push({
          id: `recv-${msg.time}`,
          content: msg.content,
          isMine: false,
          timestamp: msg.time,
          status: "read",
        });
        scrollToBottom();
      } else {
        pendingMessages.push(msg);
      }
    });

    // 4. 拉取与客服的历史消息（单独内层 try/catch：失败只置错误空态，
    //    不影响此前已完成的 IM 登录与消息监听；外层 catch 仍兜 IM 登录失败）
    try {
      await loadHistory();
    } catch (err) {
      historyLoadFailed.value = true;
      console.warn(`[kefu-chat] 历史消息拉取失败，agentId=${agentId.value}, err=${String(err)}`);
      fail("历史消息加载失败，请稍后重试");
    }
  } catch {
    // IM（WuKongIM）登录失败静默降级
  }
});

/** 点击我的头像：打开自己的资料页 */
function openMyProfile(): void {
  const myId = String(userStore.userInfo?.id || "");
  if (!myId) return;
  uni.navigateTo({ url: `/pages/match/detail?id=${myId}` });
}

onUnmounted(() => {
  if (unregisterMessage) {
    unregisterMessage();
    unregisterMessage = null;
  }
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

.vip-banner { display: flex; align-items: center; justify-content: center; gap: 8rpx;
  margin: 24rpx 24rpx 0; padding: 16rpx 24rpx;
  background: linear-gradient(135deg, var(--color-primary-deep), var(--color-primary)); border-radius: 20rpx;
  box-shadow: 0 4rpx 16rpx rgba(var(--color-primary-rgb), 0.25);
  .vip-crown { font-size: 28rpx; color: var(--text-inverse); }
  .vip-text { font-size: 24rpx; color: var(--text-inverse); letter-spacing: 1rpx; font-weight: 500; }
}

.welcome-tip { display: flex; justify-content: center; margin: 24rpx 0;
  text { font-size: 24rpx; color: var(--text-hint); background: rgba(0, 0, 0, 0.05); padding: 10rpx 28rpx; border-radius: 24rpx; }
}

/* flex:1 配 min-height:0：纵向 flex 子项默认 min-height:auto，
   消息增多时会撑破容器、把输入区挤出视口 */
.msg-list { flex: 1 1 auto; min-height: 0; min-width: 0; padding: 12rpx 24rpx; }

/* 历史加载失败重试按钮：default 变体零样式，这里补品牌色，颜色全走 CSS 变量适配暗色 */
.history-retry { margin-top: 32rpx; padding: 16rpx 56rpx; border-radius: var(--radius-pill);
  font-size: 28rpx; font-weight: 600; color: var(--text-inverse); background: var(--color-primary);
}

.time-divider { display: flex; justify-content: center; margin: 16rpx 0 24rpx;
  .time-text { font-size: 22rpx; color: var(--text-hint); background: rgba(0, 0, 0, 0.05); padding: 6rpx 24rpx; border-radius: 24rpx; }
}

.msg-item { display: flex; align-items: flex-start; margin-bottom: 28rpx; gap: 16rpx;
  &.mine { flex-direction: row-reverse; }
  /* 头像：76rpx 已 ≥44px 热区基线；外层 a11y-button 补足可点击语义与键盘可达 */
  .avatar-btn { flex-shrink: 0; display: flex; }
  .avatar { width: 76rpx; height: 76rpx; border-radius: 16rpx; flex-shrink: 0; background: var(--border-light); }
  .agent-avatar { width: 76rpx; height: 76rpx; border-radius: 16rpx; flex-shrink: 0; background: var(--bg-icon-soft); display: flex; align-items: center; justify-content: center; font-size: 40rpx; }
  .bubble-wrap { display: flex; align-items: center; gap: 12rpx; max-width: 70%;
    &.mine { flex-direction: row-reverse; }
  }
  .status-loading { width: 28rpx; height: 28rpx; border: 3rpx solid var(--border); border-top-color: var(--color-primary); border-radius: 50%; animation: spin 0.8s linear infinite; flex-shrink: 0; }
  /* 热区：88rpx 基线；原 36rpx 红点（≈22px）远低于 44px 可点击尺寸 */
  .status-failed { width: 88rpx; height: 88rpx; border-radius: 50%; background: var(--color-danger); display: flex; align-items: center; justify-content: center; flex-shrink: 0;
    text { font-size: 24rpx; color: var(--text-inverse); font-weight: bold; line-height: 1; }
  }
  .bubble { position: relative; max-width: 70%; padding: 20rpx 24rpx; border-radius: 24rpx; background: var(--bg-card); font-size: 28rpx; line-height: 1.5; color: var(--text-primary); box-shadow: 0 2rpx 8rpx rgba(0, 0, 0, 0.04);
    &::before { content: ""; position: absolute; top: 24rpx; width: 0; height: 0; border-top: 10rpx solid transparent; border-bottom: 10rpx solid transparent; }
    &:not(.mine) { border-top-left-radius: 6rpx;
      &::before { left: -14rpx; border-right: 14rpx solid var(--bg-card); }
    }
    /* color 用 --text-inverse：原 --bg-card 在暗色模式下是深色，文字会消失 */
    &.mine { background: var(--color-primary); color: var(--text-inverse); border-top-right-radius: 6rpx;
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
  &.focus { background: var(--bg-card); box-shadow: 0 0 0 3rpx rgba(var(--color-primary-rgb), 0.35); }
}
/* 发送按钮：禁用态仅 opacity 会让对比度跌破阈值 → 改用不透明深灰背景（WCAG 1.4.1 不依赖单一视觉线索） */
.send-btn { padding: 0 36rpx; height: 72rpx; background: var(--color-primary); border-radius: 36rpx; display: flex; align-items: center; justify-content: center; flex-shrink: 0; box-shadow: 0 4rpx 14rpx rgba(var(--color-primary-rgb), 0.3);
  text { color: var(--text-inverse); font-size: 28rpx; font-weight: 500; }
  &.disabled { background: var(--border); box-shadow: none; }
}
</style>
