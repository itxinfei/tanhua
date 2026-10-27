<template>
  <div class="workbench">
    <!-- 左侧会话列表 -->
    <div class="session-panel">
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
        <div v-if="sessions.length === 0 && !loading" class="empty-tip">暂无会话</div>
        <div v-if="loading" class="empty-tip">连接环信中...</div>
      </div>
    </div>
    <!-- 右侧聊天窗 -->
    <div class="chat-panel">
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
            <div class="bubble">{{ msg.content }}</div>
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
import { getAgentCredentialApi, getKefuUsersInfoApi, type KefuUserInfo } from '@/api/kefu'
import {
  loginEasemob,
  logoutEasemob,
  getConversations,
  onMessageReceived,
  sendTextMessage,
  getChatHistory,
  type ChatSession,
  type ReceivedMessage,
} from '@/utils/easemob'

/** 客服环信账号（消息归属判断：from === AGENT_ID 为客服发送） */
const AGENT_ID = 'kefu0001'

interface ChatMessage {
  id: string
  content: string
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

/** 会话头像：有 logo 用 logo，否则返回 null（模板用首字占位） */
function displayLogo(id: string): string | null {
  return userMap.value.get(Number(id))?.logo || null
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

/** 打开会话：加载历史消息 */
async function openSession(conversationId: string): Promise<void> {
  activeId.value = conversationId
  const history = await getChatHistory(conversationId)
  messages.value = history.map((msg, i) => ({
    id: `hist-${i}-${msg.time}`,
    content: msg.content,
    isMine: msg.from === AGENT_ID,
    timestamp: msg.time,
  }))
  scrollToBottom()
}

/** 发送回复 */
async function send(): Promise<void> {
  const text = inputText.value.trim()
  if (!text || sending.value) return
  sending.value = true
  const ok = await sendTextMessage(activeId.value, text)
  if (ok) {
    messages.value.push({
      id: `send-${Date.now()}`,
      content: text,
      isMine: true,
      timestamp: Date.now(),
    })
    inputText.value = ''
    scrollToBottom()
    refreshSessions()
  } else {
    ElMessage.error('发送失败，请稍后重试')
  }
  sending.value = false
}

/** 消息到达回调（onUnmounted 注销） */
let unregisterMessage: (() => void) | null = null

onMounted(async () => {
  loading.value = true
  try {
    const credential = await getAgentCredentialApi()
    if (!credential.token) {
      ElMessage.warning('环信不可用，无法接入工作台')
      return
    }
    const ok = await loginEasemob(credential.username, credential.token)
    if (!ok) {
      ElMessage.error('环信登录失败')
      return
    }
    await refreshSessions()
    // 实时消息：当前会话追加展示，其余会话刷新未读
    unregisterMessage = onMessageReceived((msg: ReceivedMessage) => {
      if (msg.from === activeId.value) {
        messages.value.push({
          id: `recv-${msg.time}`,
          content: msg.content,
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
  logoutEasemob()
})
</script>

<style lang="scss" scoped>
.workbench {
  display: flex;
  height: calc(100vh - 100px);
  background: #fff;
  border-radius: 8px;
  overflow: hidden;
}

/* 左侧会话列表 */
.session-panel {
  width: 280px;
  border-right: 1px solid #ebeef5;
  display: flex;
  flex-direction: column;
}
.panel-header {
  padding: 14px 16px;
  font-size: 15px;
  font-weight: 600;
  border-bottom: 1px solid #ebeef5;
  display: flex;
  align-items: center;
  justify-content: space-between;
}
.session-list { flex: 1; overflow-y: auto; }
.session-item {
  padding: 12px 16px;
  cursor: pointer;
  border-bottom: 1px solid #f5f5f5;
  &:hover { background: #f9f9fb; }
  &.active { background: #ecf5ff; }
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
      background: #409eff; color: #fff; font-size: 13px;
      display: flex; align-items: center; justify-content: center;
    }
  }
  .user-id {
    font-size: 14px; font-weight: 500; color: #303133;
    overflow: hidden; text-overflow: ellipsis; white-space: nowrap;
  }
  .last-time { font-size: 12px; color: #c0c4cc; flex-shrink: 0; }
}
.session-bottom {
  display: flex;
  justify-content: space-between;
  align-items: center;
  .last-msg {
    font-size: 13px; color: #909399;
    overflow: hidden; text-overflow: ellipsis; white-space: nowrap;
    max-width: 180px;
  }
  .unread-badge {
    background: #f56c6c; color: #fff;
    font-size: 11px; min-width: 18px; height: 18px; line-height: 18px;
    border-radius: 9px; text-align: center; padding: 0 5px;
  }
}
.empty-tip {
  padding: 40px 0; text-align: center; color: #c0c4cc; font-size: 13px;
}

/* 右侧聊天窗 */
.chat-panel { flex: 1; display: flex; flex-direction: column; }
.chat-empty {
  flex: 1; display: flex; align-items: center; justify-content: center;
  color: #c0c4cc; font-size: 14px;
}
.chat-header {
  padding: 14px 20px;
  font-size: 15px; font-weight: 600;
  border-bottom: 1px solid #ebeef5;
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
    padding: 10px 14px;
    border-radius: 12px;
    font-size: 14px;
    line-height: 1.5;
    background: #f4f4f5;
    color: #303133;
  }
  &.mine .bubble {
    background: #409eff;
    color: #fff;
  }
}
.input-bar {
  padding: 12px 20px;
  border-top: 1px solid #ebeef5;
  display: flex;
  gap: 12px;
  .el-input { flex: 1; }
}
</style>