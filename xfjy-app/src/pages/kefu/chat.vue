<template>
  <view class="chat-room">
    <!-- 消息列表：错误边界兜底渲染异常，重试重拉历史（与好友聊天页一致） -->
    <error-boundary @retry="retryLoadHistory">
    <scroll-view
      scroll-y
      class="msg-list"
      :scroll-into-view="scrollToId"
      :scroll-with-animation="false"
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
        <text class="i-ph-headphones welcome-icon" aria-hidden="true" />
        <text>您好，我是心飞交友在线客服，很高兴为您服务</text>
      </view>
      <template
        v-for="(msg, index) in messageList"
        :key="msg.id"
      >
        <view
          v-if="showTimeDivider(index)"
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
              class="bubble"
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
            <smart-image
              lazy-load
              class="avatar"
              :src="myAvatar"
              mode="aspectFill"
              :aria-hidden="true"
            />
          </a11y-button>
        </view>
      </template>

      <!-- IM 登录失败：消息无法收发，必须显式提示而非静默降级 -->
      <empty-state
        v-if="imLoginFailed"
        role="alert"
        icon="info"
        title="客服连接失败"
        desc="消息服务暂时不可用，请检查网络后重试"
      >
        <a11y-button
          class="history-retry"
          label="重新连接客服"
          @tap="retryIMLogin"
        >
          重新连接
        </a11y-button>
      </empty-state>

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
    </error-boundary>

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
          placeholder="请描述您的问题…"
          maxlength="500"
          aria-label="客服聊天输入框"
          confirm-type="send"
          @confirm="sendText"
          @focus="onInputFocus"
          @blur="isInputFocus = false"
        >
        <!-- 表情按钮 -->
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
        <!-- 发送按钮：始终显示，无内容时禁用 -->
        <a11y-button
          class="send-btn"
          variant="primary"
          :class="{ disabled: sending || !inputText.trim() }"
          :disabled="sending || !inputText.trim()"
          label="发送消息"
          @tap="sendText"
        >
          <text>发送</text>
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
              @tap="sendEmoji(emoji)"
            >
              <text aria-hidden="true">
                {{ emoji }}
              </text>
            </a11y-button>
          </view>
        </scroll-view>
      </view>

    </view>
  </view>
</template>

<script setup lang="ts">
import { ref, computed, nextTick, onUnmounted } from "vue";
import { onLoad } from "@dcloudio/uni-app";
import A11yButton from "@/components/a11y-button.vue";
import SmartImage from "@/components/smart-image.vue";
import EmptyState from "@/components/empty-state.vue";
import ErrorBoundary from "@/components/error-boundary.vue";
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
import { warn } from "@/utils/logger";
import { resolveAvatar, DEFAULT_AVATAR } from "@/utils/avatar";

/** 时间分隔线最小间隔（分钟） */
const TIME_GAP_MINUTES = 5;

/** 表情面板数据（与好友聊天页统一 24 个 emoji） */
const EMOJI_LIST: string[] = [
  "😊", "😂", "❤️", "👍", "😍", "🥰", "😘", "💕",
  "🌹", "🎉", "🔥", "✨", "😭", "😅", "🤔", "😎",
  "🙌", "💪", "🙏", "🤝", "💋", "🎁", "☕", "🌙",
];

/** 底部面板类型 */
type PanelType = "none" | "emoji";

/**
 * 更多面板快捷操作配置（表驱动）。
 *
 * 原「图片 / 拍照」入口已移除：客服场景仅支持文本，点击后只会弹
 * 「暂不支持发送图片」toast，属无效转化路径（P1 转化断点）。
 * 若后续后端支持图片消息，在此表驱动恢复即可。
 */
interface QuickAction { icon: string; label: string; bg: string; handler: () => void; }
const QUICK_ACTIONS: QuickAction[] = [];

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
const sendingMsgs = new Set<string>();

/** 是否有消息在途（仅用于发送按钮禁用态，不阻断消息投递） */
const sending = computed(() => sendingMsgs.size > 0);
/** IM 登录失败：消息无法收发，需提示用户而非静默降级 */
const imLoginFailed = ref(false);
const isInputFocus = ref(false);
const scrollToId = ref("");
const messageList = ref<Message[]>([]);
/** 是否 VIP（exclusive_service 专属客服标识） */
const isVip = ref(false);
/** 底部面板类型（表情/更多/无） */
const activePanel = ref<PanelType>("none");
/** 历史消息是否已填充（填充前的实时消息进 pending 缓冲，填充后实时追加）。
 *  原 onLoad 局部变量提升为 ref：重试 loadHistory 与消息回调需共享同一状态 */
const historyLoaded = ref(false);
/** 历史拉取失败标记：与「真的没有历史消息」的欢迎语/快捷问题空态区分，驱动错误空态 + 重试（S3） */
const historyLoadFailed = ref(false);
/** 在途实时消息缓冲：历史填充前到达的消息先缓存，loadHistory 合并后清空，供重试复用 */
let pendingMessages: ReceivedMessage[] = [];

/**
 * 是否显示时间分隔线（与好友聊天页统一：首条必显，后续间隔超过阈值才显示）
 * @param index 当前消息索引
 */
function showTimeDivider(index: number): boolean {
  if (index === 0) return true;
  const prev = messageList.value[index - 1];
  const curr = messageList.value[index];
  return curr.timestamp - prev.timestamp > TIME_GAP_MINUTES * 60 * 1000;
}

/** 格式化聊天时间：今天 HH:mm，昨天"昨天 HH:mm"，同年更早 MM-DD HH:mm，跨年 YYYY-MM-DD HH:mm（P2-5） */
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

/** 发送文字消息：不再被全局 sending 阻断，允许多条消息排队投递 */
async function sendText(): Promise<void> {
  const text = inputText.value.trim();
  if (!text) return;
  inputText.value = "";
  await sendMessage(text);
}

/** 点击快捷问题直接发送 */
async function sendQuick(text: string): Promise<void> {
  await sendMessage(text);
}

/** 插入表情到输入框 */
function sendEmoji(emoji: string): void {
  inputText.value += emoji;
}

/** 切换底部面板（同一面板再次点击则收起） */
function togglePanel(panel: "emoji"): void {
  if (activePanel.value !== panel) {
    // 开面板先收键盘，避免面板与软键盘互相遮挡
    uni.hideKeyboard();
    isInputFocus.value = false;
  }
  activePanel.value = activePanel.value === panel ? "none" : panel;
}

/** 输入框聚焦：收起底部面板，避免键盘与面板同屏 */
function onInputFocus(): void {
  isInputFocus.value = true;
  activePanel.value = "none";
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

/** 失败重发：复用原气泡（同一消息在途时忽略，不同消息可并行） */
async function retrySend(msg: Message): Promise<void> {
  if (msg.status === "sending") return;
  await deliver(msg);
}

/** 实际投递：成功置 sent，失败置 failed（可点击重发）。
 *  锁粒度为「单条消息」而非全局：sendingMsgs 记录在途消息 id，
 *  避免全局锁导致连发第二条被静默丢弃、重发点击无反应。
 */
async function deliver(msg: Message): Promise<void> {
  if (sendingMsgs.has(msg.id)) return;
  sendingMsgs.add(msg.id);
  msg.status = "sending";
  try {
    const ok = await sendTextMessage(agentId.value, msg.content);
    msg.status = ok ? "sent" : "failed";
    if (!ok) {
      fail("发送失败，点击气泡旁的 ! 重发");
    }
  } finally {
    sendingMsgs.delete(msg.id);
  }
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
  // 用 id 去重而非 timestamp：同一毫秒可有多条消息，timestamp 去重会误丢
  const seen = new Set<string>();
  messageList.value = merged.filter((item) => {
    if (seen.has(item.id)) return false;
    seen.add(item.id);
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
    warn(`[kefu-chat] 历史消息重试失败，agentId=${agentId.value}, err=${String(err)}`);
    fail("历史消息加载失败，请稍后重试");
  }
}

/**
 * IM 登录 + 消息监听 + 历史拉取（可重试）。
 * 抽为独立函数供 onLoad 首次调用与 retryIMLogin 重试复用。
 */
async function reconnectIM(): Promise<void> {
  // 重试前先注销旧监听，避免重复注册导致消息被处理两次
  if (unregisterMessage) {
    unregisterMessage();
    unregisterMessage = null;
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
      warn(`[kefu-chat] 历史消息拉取失败，agentId=${agentId.value}, err=${String(err)}`);
      fail("历史消息加载失败，请稍后重试");
    }
  } catch {
    // IM（WuKongIM）登录失败：不再静默，置错误态让模板显示重试入口
    imLoginFailed.value = true;
    fail("客服连接失败，请重试");
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

  await reconnectIM();
});

/** 重试 IM 登录：复用 reconnectIM 重跑登录+监听+历史拉取 */
function retryIMLogin(): void {
  imLoginFailed.value = false;
  void reconnectIM();
}

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
  /* 底部让出全局 tabbar：H5 由 global-tabbar 写入其实测高度，输入栏落在底栏上方；
     小程序端无此变量，0px 兜底，chat-room 仍满屏（二级页原生无 tabBar，行为同现状） */
  bottom: var(--tab-bar-height, 0px);
  z-index: 997;
  display: flex;
  flex-direction: column;
  background: var(--bg-sub);
}

.vip-banner { display: flex; align-items: center; justify-content: center; gap: 8rpx;
  margin: 24rpx 24rpx 0; padding: 16rpx 24rpx;
  /* 用 --gradient-brand（同源主色渐变）替代已废弃的 --color-primary-deep 历史紫 */
  background: var(--gradient-brand); border-radius: 20rpx;
  box-shadow: 0 4rpx 16rpx rgba(var(--color-primary-rgb), 0.25);
  .vip-crown { font-size: 28rpx; color: var(--text-inverse); }
  .vip-text { font-size: 24rpx; color: var(--text-inverse); letter-spacing: 1rpx; font-weight: 500; }
}

.welcome-tip { display: flex; justify-content: center; align-items: center; gap: 8rpx; margin: 24rpx 0;
  .welcome-icon { font-size: 28rpx; color: var(--color-primary); }
  text { font-size: 24rpx; color: var(--text-hint); background: var(--bg-card); padding: 10rpx 28rpx; border-radius: 24rpx; }
}

/* flex:1 配 min-height:0：纵向 flex 子项默认 min-height:auto，
   消息增多时会撑破容器、把输入区挤出视口 */
.msg-list { flex: 1 1 auto; min-height: 0; min-width: 0; padding: 24rpx 24rpx 12rpx; }

/* 历史加载失败重试按钮：default 变体零样式，这里补品牌色，颜色全走 CSS 变量适配暗色 */
.history-retry { margin-top: 32rpx; padding: 16rpx 56rpx; border-radius: var(--radius-pill);
  font-size: 28rpx; font-weight: 600; color: var(--text-inverse); background: var(--color-primary);
}

.time-divider { display: flex; justify-content: center; margin: 16rpx 0 24rpx;
  .time-text { font-size: 22rpx; color: var(--text-hint); background: var(--bg-card); padding: 6rpx 24rpx; border-radius: 24rpx; }
}

/* DOM 顺序 [对方头像, bubble-wrap, 我的头像]，普通 row：
   对方 → 头像在左、bubble 在右，整组靠左（默认 flex-start）；
   我的 → bubble 在左、头像在右，整组靠右（flex-end）。
   禁止加 row-reverse：DOM 顺序已含位置语义，反转后头像与 bubble 左右互换。 */
.msg-item { display: flex; align-items: flex-start; margin-bottom: 28rpx; gap: 16rpx;
  &.mine { justify-content: flex-end; }
  /* 头像：76rpx 已 ≥44px 热区基线；外层 a11y-button 补足可点击语义与键盘可达 */
  .avatar-btn { flex-shrink: 0; display: flex; }
  /* 圆形头像（探探式，两聊天页统一），含客服图标占位 */
  .avatar { width: 76rpx; height: 76rpx; border-radius: 50%; flex-shrink: 0; background: var(--border-light); }
  .agent-avatar { width: 76rpx; height: 76rpx; border-radius: 50%; flex-shrink: 0; background: var(--bg-icon-soft); display: flex; align-items: center; justify-content: center; font-size: 40rpx; }
  .bubble-wrap { display: flex; align-items: center; gap: 12rpx; max-width: 480rpx; min-width: 0;
    /* 我的消息：status 图标（转圈/失败）应在 bubble 与头像之间，
       DOM [status, bubble] + row-reverse → [bubble, status]，status 靠右（近头像） */
    &.mine { flex-direction: row-reverse; }
  }
  .status-loading { width: 28rpx; height: 28rpx; border: 3rpx solid var(--border); border-top-color: var(--color-primary); border-radius: 50%; animation: spin 0.8s linear infinite; flex-shrink: 0; }
  /* 热区：88rpx 基线；原 36rpx 红点（≈22px）远低于 44px 可点击尺寸 */
  .status-failed { width: 88rpx; height: 88rpx; border-radius: 50%; background: var(--color-danger); display: flex; align-items: center; justify-content: center; flex-shrink: 0;
    text { font-size: 24rpx; color: var(--text-inverse); font-weight: bold; line-height: 1; }
  }
  /* 探探式气泡（与好友聊天页统一）：无三角尾巴，靠不对称圆角做指向——
     客服头像在左→左上角收小 8rpx；我的头像在右→右上角收小；其余三角 32rpx 大圆角。
     min-width:0 + 断行：长文本/连续英文/URL 收在气泡内，防止撑破 flex 容器致页面横向滚动。
     扁平无阴影，靠白/粉底与灰底的色差区分（对标探探） */
  .bubble { position: relative; min-width: 0; overflow-wrap: anywhere; padding: 20rpx 24rpx; border-radius: 8rpx 32rpx 32rpx 32rpx; background: var(--bg-card); font-size: 28rpx; line-height: 1.5; color: var(--text-primary);
    /* 我的消息：纯品牌粉实心（无渐变）+ 白字，右上角收小靠头像侧 */
    &.mine { background: var(--color-primary); color: var(--text-inverse); border-radius: 32rpx 8rpx 32rpx 32rpx; }
  }
}

.input-area { background: var(--bg-card); border-top: 1rpx solid var(--border-light); }

.quick-bar { position: relative; background: var(--bg-sub); border-bottom: 1rpx solid var(--border-light); }
/* 右侧渐变遮罩：提示快捷问题条可横向滑动（仅在有溢出时由 JS 控制显示，这里用 CSS 兜底） */
.quick-bar::after { content: ""; position: absolute; right: 0; top: 0; bottom: 0; width: 80rpx; background: linear-gradient(to right, transparent, var(--bg-sub)); pointer-events: none; }
.quick-list { display: flex; gap: 16rpx; padding: 16rpx 20rpx; }
/* 快捷问题 chip：#fe3c72 on #fff ≈3.4:1 → #c2185b */
.quick-chip { flex-shrink: 0; background: var(--bg-card); border: 1rpx solid var(--color-primary-mist); border-radius: 32rpx; padding: 14rpx 28rpx; min-height: 48rpx;
  text { font-size: 24rpx; color: var(--color-primary-text); white-space: nowrap; }
}

.input-bar { display: flex; align-items: center; gap: 12rpx; padding: 14rpx 16rpx; }
/* 图标按钮：64rpx 圆形，热区由 a11y-button 组件保证 */
.icon-btn { width: 64rpx; height: 64rpx; display: flex; align-items: center; justify-content: center; font-size: 40rpx; border-radius: 50%; flex-shrink: 0;
  .icon-active { filter: drop-shadow(0 0 6rpx rgba(var(--color-primary-rgb), 0.6)); }
}
/* 发送按钮：与 icon-btn 同位切换，gap 已提供间距，仅防 flex 压缩 */
.send-btn {
  flex-shrink: 0;

  &.disabled { background: var(--border); color: var(--text-hint-strong); box-shadow: none; opacity: 1; }
}
.text-input { flex: 1; height: 72rpx; background: var(--bg-sub); border-radius: 36rpx; padding: 0 28rpx; font-size: 28rpx; transition: background 0.2s, box-shadow 0.2s;
  &.focus { background: var(--bg-card); box-shadow: 0 0 0 3rpx rgba(var(--color-primary-rgb), 0.35); }
}

/* ===== 底部面板 ===== */
.panel { height: 380rpx; background: var(--bg-page); border-top: 1rpx solid var(--border-light); animation: slideUp 0.2s ease-out; overflow: hidden; }
@keyframes slideUp { from { transform: translateY(100%); } to { transform: translateY(0); } }

.emoji-panel .emoji-scroll { height: 100%; }
.emoji-grid { display: flex; flex-wrap: wrap; padding: 16rpx 12rpx; }
/* height 88rpx：满足 44px 最小可点击热区，宽度 12.5% 一行 8 个 */
.emoji-item { width: 12.5%; height: 88rpx; display: flex; align-items: center; justify-content: center; font-size: 44rpx; }

.more-grid { display: flex; flex-wrap: wrap; padding: 36rpx 16rpx; }
.more-item { width: 25%; display: flex; flex-direction: column; align-items: center; gap: 14rpx; }
.more-icon { width: 104rpx; height: 104rpx; border-radius: 24rpx; display: flex; align-items: center; justify-content: center; font-size: 44rpx; box-shadow: var(--shadow-float); }
.more-label { font-size: 24rpx; color: var(--text-secondary); }
</style>
