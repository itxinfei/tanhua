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
      <div class="session-filter">
        <el-input v-model="sessionSearch" placeholder="搜索会话" clearable size="small" :prefix-icon="Search" />
        <el-checkbox v-model="onlyUnread" size="small">仅未读</el-checkbox>
      </div>
      <div class="session-list">
        <div
          v-for="s in filteredSessions"
          :key="s.conversationId"
          class="session-item"
          :class="{ active: s.conversationId === activeId, resolved: isResolved(s.conversationId) }"
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
            <el-tag v-if="isResolved(s.conversationId)" size="small" type="info" class="resolved-tag">已解决</el-tag>
            <span v-else-if="s.unreadCount > 0" class="unread-badge">{{ s.unreadCount }}</span>
          </div>
        </div>
        <el-empty v-if="filteredSessions.length === 0 && !loading" description="暂无会话" :image-size="60" />
        <div v-if="loading" class="empty-tip">连接客服通道中...</div>
      </div>
    </div>
    <!-- 右侧聊天窗 -->
    <div v-show="!isMobile || activePane === 'chat'" class="chat-panel">
      <div v-if="!activeId" class="chat-empty">选择左侧会话开始回复</div>
      <template v-else>
        <div class="chat-header">
          <span class="chat-header__name">{{ displayName(activeId) }}</span>
          <el-button
            v-if="!isResolved(activeId)"
            link
            type="primary"
            size="small"
            @click="markResolved(activeId)"
          >标记已解决</el-button>
          <el-tag v-else size="small" type="success">已解决</el-tag>
        </div>
        <div class="chat-body">
          <div class="chat-main">
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
          <input ref="imageInputRef" type="file" accept="image/*" style="display: none" @change="handleImageChange" />
          <el-popover v-model:visible="faqPopoverVisible" width="320" trigger="click" @show="loadFaqList">
            <template #reference>
              <el-button :icon="Collection" title="FAQ 快捷回复" />
            </template>
            <div v-loading="faqLoading" class="faq-list">
              <div v-for="faq in faqList" :key="faq.id" class="faq-item" @click="applyFaq(faq)">
                <div class="faq-item__q">{{ faq.question }}</div>
                <div class="faq-item__a">{{ faq.answer }}</div>
              </div>
              <el-empty v-if="!faqLoading && faqList.length === 0" description="暂无 FAQ" :image-size="40" />
            </div>
          </el-popover>
          <el-button :icon="Picture" title="发送图片" :disabled="sending" @click="imageInputRef?.click()" />
          <el-input
            v-model="inputText"
            placeholder="输入回复内容..."
            @keyup.enter="send"
          />
          <el-button type="primary" :disabled="!inputText.trim() || sending" @click="send">
            发送
          </el-button>
        </div>
          </div>
          <aside class="user-aside">
            <div class="user-aside__section">
              <img
                v-if="displayLogo(activeId)"
                class="user-aside__avatar"
                :src="displayLogo(activeId)"
                alt=""
              />
              <span v-else class="user-aside__avatar placeholder">{{ displayName(activeId).charAt(0) }}</span>
              <div class="user-aside__name">{{ displayName(activeId) }}</div>
              <div class="user-aside__id">ID: {{ activeId }}</div>
            </div>
            <div class="user-aside__section">
              <el-button
                v-perm="['SUPER_ADMIN','ADMIN']"
                link
                type="primary"
                size="small"
                @click="viewUserDetail"
              >查看用户详情</el-button>
            </div>
          </aside>
        </div>
      </template>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, nextTick, onMounted, onUnmounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { Microphone, Search, Collection, Picture } from '@element-plus/icons-vue'
import { getAgentCredentialApi, getKefuUsersInfoApi, getFaqPageApi, type KefuUserInfo, type FaqItem } from '@/api/kefu'
import { uploadImageApi } from '@/api/upload'
import { useResponsive } from '@/composables/useResponsive'
import {
  loginAgent,
  logout,
  getConversations,
  onMessageReceived,
  sendTextMessage,
  sendImageMessage,
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
    // 拦截器已统一提示
  } finally {
    sending.value = false
  }
}

// ---- 会话筛选：搜索 + 仅未读 ----
const sessionSearch = ref('')
const onlyUnread = ref(false)
const filteredSessions = computed(() => {
  let list = sessions.value
  if (onlyUnread.value) list = list.filter((s) => s.unreadCount > 0)
  const kw = sessionSearch.value.trim()
  if (kw) list = list.filter((s) => displayName(s.conversationId).includes(kw))
  return list
})

// ---- 已解决标记（纯前端状态，刷新后重置） ----
const resolvedIds = ref<string[]>([])
function isResolved(id: string): boolean {
  return resolvedIds.value.includes(id)
}
function markResolved(id: string): void {
  if (id && !isResolved(id)) {
    resolvedIds.value = [...resolvedIds.value, id]
  }
}

// ---- 用户侧栏：跳转用户详情 ----
const router = useRouter()
function viewUserDetail(): void {
  if (!activeId.value) return
  router.push({ path: '/user/list', query: { keyword: activeId.value } })
}

// ---- FAQ 快捷回复 ----
const faqPopoverVisible = ref(false)
const faqList = ref<FaqItem[]>([])
const faqLoading = ref(false)
async function loadFaqList(): Promise<void> {
  if (faqList.value.length > 0) return
  faqLoading.value = true
  try {
    const res = await getFaqPageApi({ page: 1, pageSize: 50, status: 1 })
    faqList.value = res.list
  } catch {
    // 拦截器已统一提示
  } finally {
    faqLoading.value = false
  }
}
function applyFaq(faq: FaqItem): void {
  inputText.value = inputText.value.trim()
    ? `${inputText.value}\n${faq.answer}`
    : faq.answer
  faqPopoverVisible.value = false
}

// ---- 图片发送 ----
const imageInputRef = ref<HTMLInputElement | null>(null)
async function handleImageChange(e: Event): Promise<void> {
  const input = e.target as HTMLInputElement
  const file = input.files?.[0]
  input.value = '' // 允许重复选择同一文件
  if (!file || !activeId.value) return
  sending.value = true
  try {
    const res = await uploadImageApi(file)
    if (res.status !== 'done' || !res.name) {
      ElMessage.error('图片上传失败')
      return
    }
    const message = await sendImageMessage(activeId.value, res.name)
    if (message) {
      messages.value.push({
        id: `send-${message.messageSeq}-${Date.now()}`,
        messageSeq: message.messageSeq,
        content: res.name,
        type: 'image',
        isMine: true,
        timestamp: Date.now(),
      })
      scrollToBottom()
      refreshSessions()
    } else {
      ElMessage.error('发送失败，请稍后重试')
    }
  } catch {
    // 拦截器已统一提示
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
.session-filter {
  display: flex;
  align-items: center;
  gap: var(--xfjy-space-3);
  padding: var(--xfjy-space-2) var(--xfjy-space-4);
  border-bottom: 1px solid var(--xfjy-border-light);
  .el-input { flex: 1; }
}
.session-list { flex: 1; overflow-y: auto; }
.session-item {
  padding: var(--xfjy-space-3) var(--xfjy-space-4);
  cursor: pointer;
  border-bottom: 1px solid var(--xfjy-border-light);
  &:hover { background: var(--xfjy-bg-hover); }
  &.active { background: var(--xfjy-primary-50); }
  &.resolved { opacity: 0.55; }
}
.session-top {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: var(--xfjy-space-1);
  .user-wrap { display: flex; align-items: center; gap: var(--xfjy-space-2); min-width: 0; }
  .user-avatar {
    width: 32px; height: 32px; border-radius: 50%; flex-shrink: 0; object-fit: cover;
    &.placeholder {
      /* 占位头像内含文字，白字 on 主色 3.44:1 不达标，走深档 6.82:1 */
      background: var(--xfjy-primary-dark); color: var(--xfjy-text-inverse); font-size: var(--xfjy-font-size-sm);
      display: flex; align-items: center; justify-content: center;
    }
  }
  .user-id {
    font-size: var(--xfjy-font-size-base); font-weight: 500; color: var(--xfjy-text-primary);
    overflow: hidden; text-overflow: ellipsis; white-space: nowrap;
  }
  .last-time { font-size: var(--xfjy-font-size-xs); color: var(--xfjy-text-secondary); flex-shrink: 0; }
}
.session-bottom {
  display: flex;
  justify-content: space-between;
  align-items: center;
  .last-msg {
    font-size: var(--xfjy-font-size-sm); color: var(--xfjy-text-secondary);
    overflow: hidden; text-overflow: ellipsis; white-space: nowrap;
    max-width: 180px;
  }
  .unread-badge {
    /* 底色走 deep 档：白字 on #c43232 = 5.45:1（旧 --xfjy-danger #f53f3f 仅 3.71:1） */
    background: var(--xfjy-danger-deep); color: var(--xfjy-text-inverse);
    /* 角标微尺寸（11px 字号 / 18px 高度）为徽标光学规格，不归 8px 栅格（AGENTS.md §11 存量豁免） */
    font-size: 11px; min-width: 18px; height: 18px; line-height: 18px;
    border-radius: var(--xfjy-radius-sm); text-align: center; padding: 0 var(--xfjy-space-1);
  }
}
.empty-tip {
  padding: 40px 0; text-align: center; color: var(--xfjy-text-secondary); font-size: var(--xfjy-font-size-sm);
}

/* 右侧聊天窗 */
.chat-panel { flex: 1; display: flex; flex-direction: column; min-width: 0; }
.chat-body { flex: 1; display: flex; overflow: hidden; min-height: 0; }
.chat-main { flex: 1; display: flex; flex-direction: column; min-width: 0; }

/* 用户信息侧栏 */
.user-aside {
  width: 200px;
  flex-shrink: 0;
  border-left: 1px solid var(--xfjy-border-light);
  padding: var(--xfjy-space-4);
  display: flex;
  flex-direction: column;
  gap: var(--xfjy-space-4);
  &__section {
    display: flex;
    flex-direction: column;
    align-items: center;
    gap: var(--xfjy-space-2);
  }
  &__avatar {
    width: 56px;
    height: 56px;
    border-radius: 50%;
    object-fit: cover;
    &.placeholder {
      display: flex;
      align-items: center;
      justify-content: center;
      background: var(--xfjy-primary-soft);
      color: var(--xfjy-text-inverse);
      font-size: 24px;
      font-weight: 600;
    }
  }
  &__name {
    font-size: var(--xfjy-font-size-sm);
    font-weight: 600;
    color: var(--xfjy-text-primary);
    text-align: center;
    word-break: break-all;
  }
  &__id {
    font-size: var(--xfjy-font-size-xs);
    color: var(--xfjy-text-secondary);
  }
}
.chat-empty {
  flex: 1; display: flex; align-items: center; justify-content: center;
  color: var(--xfjy-text-secondary); font-size: var(--xfjy-font-size-base);
}
.chat-header {
  padding: var(--xfjy-space-4) var(--xfjy-space-5);
  font-size: 15px; font-weight: 600;
  border-bottom: 1px solid var(--xfjy-border-light);
  display: flex;
  align-items: center;
  justify-content: space-between;
}
.msg-list {
  flex: 1; overflow-y: auto; padding: var(--xfjy-space-4) var(--xfjy-space-5);
  display: flex; flex-direction: column; gap: var(--xfjy-space-3);
}
.msg-item {
  display: flex;
  &.mine { justify-content: flex-end; }
  .bubble {
    max-width: 60%;
    /* stylelint-disable-next-line declaration-property-value-disallowed-list -- 聊天气泡内距，气泡高度按内容自然撑开，见 AGENTS.md §11 存量豁免 */
    padding: 10px 14px;
    border-radius: var(--xfjy-radius-lg);
    font-size: var(--xfjy-font-size-base);
    line-height: 1.5;
    background: var(--xfjy-bg-hover);
    color: var(--xfjy-text-primary);
  }
  &.mine .bubble {
    /* 气泡是正文文本容器，白字 on 主色 3.44:1 不达标，走深档 6.82:1 */
    background: var(--xfjy-primary-dark);
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
  padding: var(--xfjy-space-3) var(--xfjy-space-5);
  border-top: 1px solid var(--xfjy-border-light);
  display: flex;
  align-items: center;
  gap: var(--xfjy-space-3);
  .el-input { flex: 1; }
}

/* FAQ 快捷回复列表 */
.faq-list {
  max-height: 300px;
  overflow-y: auto;
}
.faq-item {
  padding: var(--xfjy-space-2) var(--xfjy-space-3);
  border-radius: var(--xfjy-radius-md);
  cursor: pointer;
  &:hover { background: var(--xfjy-bg-hover); }
  &__q {
    font-size: var(--xfjy-font-size-sm);
    font-weight: 600;
    color: var(--xfjy-text-primary);
  }
  &__a {
    margin-top: var(--xfjy-space-1);
    font-size: var(--xfjy-font-size-xs);
    color: var(--xfjy-text-secondary);
    display: -webkit-box;
    -webkit-line-clamp: 2;
    -webkit-box-orient: vertical;
    overflow: hidden;
  }
}

.resolved-tag {
  flex-shrink: 0;
}

/* 移动端 tab 切换栏 */
.mobile-tabs {
  display: none;
  gap: var(--xfjy-space-2);
  padding: var(--xfjy-space-2) var(--xfjy-space-3);
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
  .user-aside {
    display: none;
  }
}
</style>