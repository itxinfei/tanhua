<template>
  <div class="workbench">
    <!-- 移动端 tab 切换栏（桌面端隐藏） -->
    <div v-if="isMobile" class="mobile-tabs">
      <el-button :type="activePane === 'session' ? 'primary' : 'default'" size="small" @click="activePane = 'session'">会话</el-button>
      <el-button :type="activePane === 'chat' ? 'primary' : 'default'" size="small" @click="activePane = 'chat'">聊天</el-button>
    </div>
    <!-- 左侧会话列表 -->
    <div v-show="!isMobile || activePane === 'session'" class="session-panel">
      <div class="panel-header">
        <span>在线会话</span>
        <el-badge :value="totalUnread" :hidden="totalUnread === 0" class="unread-total" />
      </div>
      <div class="session-list">
        <div
          v-for="s in sessions"
          :key="s.conversationId"
          class="session-item"
          :class="{ active: s.conversationId === activeId }"
          @click="openSession(s.conversationId)"
        >
          <div class="session-top">
            <div class="user-wrap">
              <img
                v-if="displayLogo(s.conversationId)"
                class="user-avatar"
                :src="displayLogo(s.conversationId)"
                alt=""
              />
              <span v-else class="user-avatar placeholder">{{ displayName(s.conversationId).charAt(0) }}</span>
              <span class="user-id">{{ displayName(s.conversationId) }}</span>
            </div>
            <span class="last-time">{{ fmtShort(s.lastTime) }}</span>
          </div>
          <div class="session-bottom">
            <span class="last-msg">{{ s.lastMessage || '暂无消息' }}</span>
            <span v-if="s.unreadCount > 0" class="unread-badge">{{ s.unreadCount }}</span>
          </div>
        </div>
        <el-empty v-if="sessions.length === 0 && !loading" description="暂无会话" :image-size="60" />
        <div v-if="loading" class="empty-tip">连接客服通道中...</div>
      </div>
    </div>
    <!-- 右侧聊天窗 -->
    <div v-show="!isMobile || activePane === 'chat'" class="chat-panel">
      <div v-if="!activeId" class="chat-empty">选择左侧会话开始回复</div>
      <template v-else>
        <div class="chat-header">{{ displayName(activeId) }}</div>
        <div ref="msgListRef" class="msg-list">
          <div
            v-for="msg in messages"
            :key="msg.id"
            class="msg-item"
            :class="{ mine: msg.isMine }"
          >
            <!-- 图片消息：C 端 JSON 契约 {type:'image',url}，content 即图片 URL -->
            <el-image
              v-if="msg.type === 'image'"
              class="media-bubble"
              :src="msg.content"
              :preview-src-list="[msg.content]"
              fit="cover"
              preview-teleported
            />
            <!-- 语音消息：占位气泡，点击播放（content 为语音 URL） -->
            <div
              v-else-if="msg.type === 'voice'"
              class="bubble voice-bubble"
              @click="playVoice(msg)"
            >
              <el-icon><Microphone /></el-icon>
              <span>语音 {{ voiceDuration(msg) }}″</span>
            </div>
            <!-- 文本及其他消息 -->
            <div v-else class="bubble">{{ msg.content }}</div>
          </div>
        </div>
        <div class="input-bar">
          <el-input
            v-model="inputText"
            placeholder="输入回复内容..."
            @keyup.enter="send"
          />
          <el-button type="primary" :disabled="!inputText.trim() || sending" @click="send">
            发送
          </el-button>
        </div>
      </template>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, nextTick, onMounted, onUnmounted } from 'vue'
import { ElMessage } from 'element-plus'
import { Microphone } from '@element-plus/icons-vue'
import { getAgentCredentialApi, getKefuUsersInfoApi, type KefuUserInfo } from '@/api/kefu'
import { useResponsive } from '@/composables/useResponsive'
import {
  loginAgent,
  logout,
  getConversations,
  onMessageReceived,
  sendTextMessage,
  getChatHistory,
  markConversationRead,
  type ChatSession,
  type ReceivedMessage,
} from '@/utils/wukongim'

// 响应式：移动端单栏 tab 切换（会话列表 / 聊天）
const { isMobile } = useResponsive()
const activePane = ref<'session' | 'chat'>('session')

/** 客服 IM 账号（消息归属判断：from === agentId 为对方用户、!== 为客服自己），登录后由后端凭证下发 */
const agentId = ref('')

interface ChatMessage {
  id: string
  /** WuKongIM 频道内消息序号 */
  messageSeq: number
  content: string
  type: ReceivedMessage['type']
  extra?: ReceivedMessage['extra']
  isMine: boolean
  timestamp: number
}

const loading = ref(false)
const sessions = ref<ChatSession[]>([])
const activeId = ref('')
const messages = ref<ChatMessage[]>([])
const inputText = ref('')
const sending = ref(false)
const msgListRef = ref<HTMLElement | null>(null)

const totalUnread = computed(() =>
  sessions.value.reduce((sum, s) => sum + s.unreadCount, 0)
)

/** 格式化会话时间：今天 HH:mm，更早 MM-DD HH:mm */
function fmtShort(ts: number): string {
  if (!ts) return ''
  const d = new Date(ts)
  const hh = String(d.getHours()).padStart(2, '0')
  const mm = String(d.getMinutes()).padStart(2, '0')
  const now = new Date()
  if (d.toDateString() === now.toDateString()) return `${hh}:${mm}`
  const MM = String(d.getMonth() + 1).padStart(2, '0')
  const DD = String(d.getDate()).padStart(2, '0')
  return `${MM}-${DD} ${hh}:${mm}`
}

/** 滚动到消息底部 */
function scrollToBottom(): void {
  nextTick(() => {
    if (msgListRef.value) {
      msgListRef.value.scrollTop = msgListRef.value.scrollHeight
    }
  })
}

/** 用户信息缓存（conversationId 数值 -> 用户信息），会话列表展示昵称/头像 */
const userMap = ref<Map<number, KefuUserInfo>>(new Map())

/** 会话展示名：有昵称用昵称，否则回退"用户{id}" */
function displayName(id: string): string {
  const info = userMap.value.get(Number(id))
  return info?.nickname || `用户${id}`
}

/** 会话头像：有 logo 用 logo，否则返回 undefined（模板用首字占位；img src 不接受 null） */
function displayLogo(id: string): string | undefined {
  return userMap.value.get(Number(id))?.logo || undefined
}

/** 批量补查缺失的用户信息（会话刷新后调用，失败静默降级） */
async function ensureUserInfos(): Promise<void> {
  const missing: number[] = []
  for (const s of sessions.value) {
    const num = Number(s.conversationId)
    if (!Number.isNaN(num) && !userMap.value.has(num)) {
      missing.push(num)
    }
  }
  if (missing.length === 0) return
  try {
    const list = await getKefuUsersInfoApi(missing)
    for (const info of list) {
      userMap.value.set(info.userId, info)
    }
  } catch {
    // 查询失败静默降级，展示回退"用户{id}"
  }
}

/** 刷新会话列表（顺带补查新增会话的用户信息） */
async function refreshSessions(): Promise<void> {
  sessions.value = await getConversations()
  await ensureUserInfos()
}

/** 打开会话：清零未读并加载历史消息 */
async function openSession(conversationId: string): Promise<void> {
  activeId.value = conversationId
  // 移动端打开会话后自动切换到聊天 tab
  if (isMobile.value) {
    activePane.value = 'chat'
  }
  // 设为当前打开会话（后续实时消息不计未读）并清空本地未读数
  markConversationRead(conversationId)
  try {
    const history = await getChatHistory(conversationId)
    messages.value = history.map((msg) => ({
      id: `hist-${msg.messageSeq}-${msg.time}`,
      messageSeq: msg.messageSeq,
      content: msg.content,
      type: msg.type,
      extra: msg.extra,
      isMine: msg.from === agentId.value,
      timestamp: msg.time,
    }))
    scrollToBottom()
  } catch {
    messages.value = []
    ElMessage.error('加载聊天记录失败')
  }
}

/** 发送回复 */
async function send(): Promise<void> {
  const text = inputText.value.trim()
  if (!text || sending.value) return
  sending.value = true
  try {
    const message = await sendTextMessage(activeId.value, text)
    if (message) {
      messages.value.push({
        id: `send-${message.messageSeq}-${Date.now()}`,
        messageSeq: message.messageSeq,
        content: text,
        type: 'text',
        isMine: true,
        timestamp: Date.now(),
      })
      inputText.value = ''
      scrollToBottom()
      refreshSessions()
    } else {
      ElMessage.error('发送失败，请稍后重试')
    }
  } catch {
    ElMessage.error('发送失败，请检查网络')
  } finally {
    sending.value = false
  }
}

/** 语音时长（秒，来自结构化消息 extra.duration） */
function voiceDuration(msg: ChatMessage): number {
  return Number(msg.extra?.duration ?? 0)
}

/** 播放语音（content 即语音 URL；播放失败提示） */
function playVoice(msg: ChatMessage): void {
  const url = msg.type === 'voice' ? msg.content : ''
  if (!url) {
    ElMessage.warning('语音地址不可用')
    return
  }
  try {
    const audio = new Audio(url)
    audio.play().catch(() => ElMessage.warning('语音播放失败'))
  } catch {
    ElMessage.warning('语音播放失败')
  }
}

/** 消息到达回调（onUnmounted 注销） */
let unregisterMessage: (() => void) | null = null

onMounted(async () => {
  loading.value = true
  try {
    const credential = await getAgentCredentialApi()
    if (!credential.token || !credential.wsUrl) {
      ElMessage.warning('客服通道不可用，无法接入工作台')
      return
    }
    const ok = await loginAgent(credential.username, credential.token, credential.wsUrl)
    if (!ok) {
      ElMessage.error('客服通道登录失败')
      return
    }
    // 记录当前客服 IM 账号（由后端凭证下发），用于消息归属判断
    agentId.value = credential.username
    await refreshSessions()
    // 实时消息：当前会话追加展示，其余会话刷新会话列表（未读由 SDK 累计）
    unregisterMessage = onMessageReceived((msg: ReceivedMessage) => {
      if (msg.from === activeId.value) {
        messages.value.push({
          id: `recv-${msg.messageSeq}-${msg.time}`,
          messageSeq: msg.messageSeq,
          content: msg.content,
          type: msg.type,
          extra: msg.extra,
          isMine: false,
          timestamp: msg.time,
        })
        scrollToBottom()
      }
      refreshSessions()
    })
  } catch {
    ElMessage.error('工作台初始化失败')
  } finally {
    loading.value = false
  }
})

onUnmounted(() => {
  if (unregisterMessage) {
    unregisterMessage()
    unregisterMessage = null
  }
  logout()
})
</script>

<style lang="scss" scoped>
.workbench {
  display: flex;
  height: calc(100vh - 100px);
  background: var(--xfjy-bg-card);
  border-radius: var(--xfjy-radius-md);
  overflow: hidden;
}

/* 左侧会话列表 */
.session-panel {
  width: 280px;
  border-right: 1px solid var(--xfjy-border-light);
  display: flex;
  flex-direction: column;
}
.panel-header {
  padding: var(--xfjy-space-4) var(--xfjy-space-4);
  font-size: 15px;
  font-weight: 600;
  border-bottom: 1px solid var(--xfjy-border-light);
  display: flex;
  align-items: center;
  justify-content: space-between;
}
.session-list { flex: 1; overflow-y: auto; }
.session-item {
  padding: 12px 16px;
  cursor: pointer;
  border-bottom: 1px solid var(--xfjy-border-light);
  &:hover { background: var(--xfjy-bg-hover); }
  &.active { background: var(--xfjy-primary-50); }
}
.session-top {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 4px;
  .user-wrap { display: flex; align-items: center; gap: 8px; min-width: 0; }
  .user-avatar {
    width: 32px; height: 32px; border-radius: 50%; flex-shrink: 0; object-fit: cover;
    &.placeholder {
      background: var(--xfjy-primary); color: var(--xfjy-text-inverse); font-size: 13px;
      display: flex; align-items: center; justify-content: center;
    }
  }
  .user-id {
    font-size: 14px; font-weight: 500; color: var(--xfjy-text-primary);
    overflow: hidden; text-overflow: ellipsis; white-space: nowrap;
  }
  .last-time { font-size: 12px; color: var(--xfjy-text-disabled); flex-shrink: 0; }
}
.session-bottom {
  display: flex;
  justify-content: space-between;
  align-items: center;
  .last-msg {
    font-size: 13px; color: var(--xfjy-text-secondary);
    overflow: hidden; text-overflow: ellipsis; white-space: nowrap;
    max-width: 180px;
  }
  .unread-badge {
    background: var(--xfjy-danger); color: var(--xfjy-text-inverse);
    /* 角标微尺寸（11px 字号 / 18px 高度）为徽标光学规格，不归 8px 栅格（AGENTS.md §11 存量豁免） */
    font-size: 11px; min-width: 18px; height: 18px; line-height: 18px;
    border-radius: var(--xfjy-radius-sm); text-align: center; padding: 0 var(--xfjy-space-1);
  }
}
.empty-tip {
  padding: 40px 0; text-align: center; color: var(--xfjy-text-disabled); font-size: 13px;
}

/* 右侧聊天窗 */
.chat-panel { flex: 1; display: flex; flex-direction: column; }
.chat-empty {
  flex: 1; display: flex; align-items: center; justify-content: center;
  color: var(--xfjy-text-disabled); font-size: 14px;
}
.chat-header {
  padding: var(--xfjy-space-4) var(--xfjy-space-5);
  font-size: 15px; font-weight: 600;
  border-bottom: 1px solid var(--xfjy-border-light);
}
.msg-list {
  flex: 1; overflow-y: auto; padding: 16px 20px;
  display: flex; flex-direction: column; gap: 12px;
}
.msg-item {
  display: flex;
  &.mine { justify-content: flex-end; }
  .bubble {
    max-width: 60%;
    /* stylelint-disable-next-line declaration-property-value-disallowed-list -- 聊天气泡内距，气泡高度按内容自然撑开，见 AGENTS.md §11 存量豁免 */
    padding: 10px 14px;
    border-radius: var(--xfjy-radius-lg);
    font-size: 14px;
    line-height: 1.5;
    background: var(--xfjy-bg-hover);
    color: var(--xfjy-text-primary);
  }
  &.mine .bubble {
    background: var(--xfjy-primary);
    color: var(--xfjy-text-inverse);
  }
}

/* 图片消息气泡：缩略图 */
.media-bubble {
  width: 180px;
  max-width: 60%;
  border-radius: var(--xfjy-radius-lg);
  overflow: hidden;
}

/* 语音消息气泡：可点击播放 */
.voice-bubble {
  display: flex;
  align-items: center;
  /* stylelint-disable-next-line declaration-property-value-disallowed-list -- 语音图标与波形/文字的 6px 对齐，见 AGENTS.md §11 存量豁免 */
  gap: 6px;
  cursor: pointer;
  user-select: none;
}

.input-bar {
  padding: 12px 20px;
  border-top: 1px solid var(--xfjy-border-light);
  display: flex;
  gap: 12px;
  .el-input { flex: 1; }
}

/* 移动端 tab 切换栏 */
.mobile-tabs {
  display: none;
  gap: 8px;
  padding: 8px 12px;
  border-bottom: 1px solid var(--xfjy-border-light);
  background: var(--xfjy-bg-card);
}

/* 移动端：纵向堆叠，单栏 tab 切换 */
@media (max-width: 767px) {
  .workbench {
    flex-direction: column;
  }
  .mobile-tabs {
    display: flex;
  }
  .session-panel,
  .chat-panel {
    width: 100%;
    max-width: 100%;
    border-right: none;
  }
  .session-panel {
    height: 40vh;
    flex-shrink: 0;
  }
  .chat-panel {
    flex: 1;
    min-height: 0;
  }
}
</style>