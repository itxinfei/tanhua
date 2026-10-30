<template>
  <el-drawer
    :model-value="modelValue"
    title="用户详情"
    size="720px"
    direction="rtl"
    :destroy-on-close="true"
    @update:model-value="handleModelValueChange"
  >
    <div v-loading="detailLoading">
      <!-- 基本信息 -->
      <el-tabs v-model="activeTab" class="detail-tabs">
        <el-tab-pane label="基本信息" name="basic">
          <div v-if="detail" class="tab-content">
            <div class="user-profile">
              <el-avatar :size="72" :src="detail.avatar">
                <el-icon :size="36"><UserFilled /></el-icon>
              </el-avatar>
              <div class="profile-info">
                <div class="profile-name">
                  {{ detail.nickname }}
                  <el-tag :type="detail.gender === '女' ? 'danger' : 'primary'" size="small">{{ detail.gender }}</el-tag>
                  <el-tag v-if="detail.vipLevel > 0" type="warning" size="small">VIP{{ detail.vipLevel }}</el-tag>
                  <el-tag :type="detail.status === 'NORMAL' ? 'success' : 'danger'" size="small">
                    {{ detail.status === 'NORMAL' ? '正常' : '已封禁' }}
                  </el-tag>
                </div>
                <div class="profile-meta">
                  <span>ID: {{ detail.id }}</span>
                  <span>手机: {{ detail.mobile }}</span>
                  <span>年龄: {{ detail.age }}</span>
                  <span>城市: {{ detail.city }}</span>
                </div>
                <div class="profile-meta">
                  <span>学历: {{ detail.edu || '—' }}</span>
                  <span>行业: {{ detail.industry || '—' }}</span>
                  <span>收入: {{ detail.income || '—' }}</span>
                  <span>婚姻: {{ detail.marriage || '—' }}</span>
                </div>
                <div class="profile-meta">
                  <span>注册: {{ detail.createdAt }}</span>
                  <span>最后活跃: {{ detail.lastActiveAt }}</span>
                </div>
                <div v-if="detail.signature" class="profile-signature">签名: {{ detail.signature }}</div>
                <div v-if="detail.freezeReason" class="freeze-reason">封禁原因: {{ detail.freezeReason }}</div>
              </div>
            </div>
          </div>
        </el-tab-pane>

        <!-- 数据统计 -->
        <el-tab-pane label="数据统计" name="stats">
          <div v-if="detail" class="tab-content">
            <el-row :gutter="12" class="stat-row">
              <el-col :span="8" v-for="stat in stats" :key="stat.label">
                <div class="stat-box">
                  <div class="stat-num">{{ stat.value }}</div>
                  <div class="stat-text">{{ stat.label }}</div>
                </div>
              </el-col>
            </el-row>
            <el-alert
              v-if="statsLoadingNote"
              type="info"
              :closable="false"
              show-icon
              title="提示"
              description="动态数、粉丝数、关注数、匹配数暂未接入 MongoDB 计数，当前显示 0。被举报数来自 MySQL 举报表。"
              style="margin-top: 16px"
            />
          </div>
        </el-tab-pane>

        <!-- 照片墙 -->
        <el-tab-pane label="照片墙" name="photos">
          <div class="tab-content">
            <div class="photo-wall">
              <el-image
                v-for="(photo, i) in detail?.photos || []"
                :key="i"
                :src="photo"
                fit="cover"
                preview-teleported
                hide-on-click-modal
                style="width: 110px; height: 110px; border-radius: 8px; margin: 4px"
                :preview-src-list="detail?.photos"
                :initial-index="i"
              />
            </div>
            <el-empty v-if="!detail?.photos?.length" description="暂无照片" :image-size="60" />
          </div>
        </el-tab-pane>

        <!-- 个人标签 -->
        <el-tab-pane label="个人标签" name="tags">
          <div class="tab-content">
            <div class="tag-list">
              <el-tag v-for="tag in detail?.tags || []" :key="tag" effect="plain" style="margin: 4px">{{ tag }}</el-tag>
            </div>
            <el-empty v-if="!detail?.tags?.length" description="暂无标签" :image-size="60" />
          </div>
        </el-tab-pane>

        <!-- 聊天记录 -->
        <el-tab-pane label="聊天记录" name="chat">
          <div class="tab-content chat-tab-content">
            <div class="chat-container">
              <!-- 左侧会话列表 -->
              <div class="chat-session-panel">
                <div class="session-header">会话列表</div>
                <div class="session-list">
                  <div
                    v-for="session in chatSessions"
                    :key="session.contactId"
                    class="session-item"
                    :class="{ active: session.contactId === activeContactId }"
                    @click="loadChatMessages(session.contactId)"
                  >
                    <el-avatar :size="36" :src="session.avatar" class="session-avatar">
                      <el-icon :size="18"><UserFilled /></el-icon>
                    </el-avatar>
                    <div class="session-info">
                      <div class="session-contact">
                        <span class="session-name">{{ session.nickname || session.contactId }}</span>
                        <span class="session-id">#{{ session.contactId }}</span>
                      </div>
                      <div class="session-msg">{{ session.lastMessage || '暂无消息' }}</div>
                      <div class="session-time">{{ formatTime(session.lastTime) }}</div>
                    </div>
                  </div>
                  <el-empty v-if="!chatSessions.length && !chatSessionsLoading" description="暂无会话" :image-size="50" />
                  <div v-if="chatSessionsLoading" class="loading-tip">加载中...</div>
                </div>
              </div>
              <!-- 右侧聊天气泡 -->
              <div class="chat-message-panel">
                <template v-if="activeContactId">
                  <div class="message-header">与 {{ activeContactNickname }} 的聊天记录</div>
                  <div ref="messageListRef" class="message-list">
                    <div
                      v-for="(msg, i) in chatMessages"
                      :key="i"
                      class="message-item"
                      :class="{ mine: msg.from === String(props.userId) }"
                    >
                      <div class="message-time">{{ formatTime(msg.time) }}</div>
                      <div class="bubble">{{ msg.content }}</div>
                    </div>
                    <el-empty v-if="!chatMessages.length && !chatMessagesLoading" description="暂无消息" :image-size="50" />
                  </div>
                </template>
                <el-empty v-else description="选择左侧会话查看聊天记录" :image-size="50" />
              </div>
            </div>
          </div>
        </el-tab-pane>

        <!-- 发送的图片和视频 -->
        <el-tab-pane label="发送的图片和视频" name="content">
          <div class="tab-content">
            <el-tabs v-model="contentSubTab" class="content-sub-tabs">
              <!-- 动态 -->
              <el-tab-pane label="动态" name="movement">
                <el-table :data="movementData" v-loading="movementLoading" stripe size="small" style="width: 100%">
                  <el-table-column label="图片" width="100" align="center">
                    <template #default="{ row }">
                      <el-image
                        v-if="row.medias && row.medias.length"
                        :src="row.medias[0]"
                        fit="cover"
                        preview-teleported
                        hide-on-click-modal
                        style="width: 48px; height: 48px; border-radius: 4px"
                        :preview-src-list="row.medias"
                      />
                      <span v-else class="text-muted">—</span>
                    </template>
                  </el-table-column>
                  <el-table-column prop="text" label="内容" min-width="120" show-overflow-tooltip />
                  <el-table-column prop="created" label="时间" width="100" />
                </el-table>
                <el-empty v-if="!movementData.length && !movementLoading" description="暂无动态" :image-size="50" />
              </el-tab-pane>
              <!-- 视频 -->
              <el-tab-pane label="视频" name="video">
                <el-table :data="videoData" v-loading="videoLoading" stripe size="small" style="width: 100%">
                  <el-table-column label="封面" width="100" align="center">
                    <template #default="{ row }">
                      <el-image
                        :src="row.picUrl"
                        fit="cover"
                        preview-teleported
                        hide-on-click-modal
                        style="width: 48px; height: 48px; border-radius: 4px"
                      />
                    </template>
                  </el-table-column>
                  <el-table-column prop="text" label="描述" min-width="120" show-overflow-tooltip />
                  <el-table-column prop="created" label="时间" width="100" />
                </el-table>
                <el-empty v-if="!videoData.length && !videoLoading" description="暂无视频" :image-size="50" />
              </el-tab-pane>
              <!-- 评论 -->
              <el-tab-pane label="评论" name="comment">
                <el-table :data="commentData" v-loading="commentLoading" stripe size="small" style="width: 100%">
                  <el-table-column prop="content" label="评论内容" min-width="150" show-overflow-tooltip />
                  <el-table-column prop="created" label="时间" width="100" />
                </el-table>
                <el-empty v-if="!commentData.length && !commentLoading" description="暂无评论" :image-size="50" />
              </el-tab-pane>
            </el-tabs>
          </div>
        </el-tab-pane>

        <!-- 被举报记录 -->
        <el-tab-pane label="被举报记录" name="reports">
          <div class="tab-content">
            <el-table :data="reportData" v-loading="reportLoading" stripe size="small" style="width: 100%">
              <el-table-column prop="id" label="ID" width="60" align="center" />
              <el-table-column label="举报人" width="100">
                <template #default="{ row }">
                  <div class="user-cell">
                    <el-avatar :size="24" :src="row.reporterAvatar">
                      <el-icon><UserFilled /></el-icon>
                    </el-avatar>
                    <span>{{ row.reporterNickname }}</span>
                  </div>
                </template>
              </el-table-column>
              <el-table-column label="类型" width="70" align="center">
                <template #default="{ row }">
                  <el-tag :type="reportTypeColor(row.targetType)" size="small">{{ reportTypeLabel(row.targetType) }}</el-tag>
                </template>
              </el-table-column>
              <el-table-column prop="reason" label="原因" width="100" show-overflow-tooltip />
              <el-table-column prop="description" label="描述" min-width="120" show-overflow-tooltip />
              <el-table-column label="状态" width="80" align="center">
                <template #default="{ row }">
                  <el-tag :type="reportStatusColor(row.status)" size="small">{{ reportStatusLabel(row.status) }}</el-tag>
                </template>
              </el-table-column>
              <el-table-column prop="createdAt" label="举报时间" width="150" />
            </el-table>
            <div v-if="reportData.length" class="pagination-wrapper">
              <el-pagination
                v-model:current-page="reportQuery.page"
                v-model:page-size="reportQuery.pageSize"
                :total="reportTotal"
                :page-sizes="[10, 20]"
                layout="total, prev, pager, next"
                @current-change="loadReports"
              />
            </div>
          </div>
        </el-tab-pane>

        <!-- 账号操作 -->
        <el-tab-pane label="账号操作" name="actions">
          <div v-if="detail" class="tab-content">
            <el-card shadow="never">
              <div class="action-bar">
                <div class="action-info">
                  <el-avatar :size="48" :src="detail.avatar">
                    <el-icon><UserFilled /></el-icon>
                  </el-avatar>
                  <div>
                    <div class="action-name">{{ detail.nickname }}（{{ detail.mobile }}）</div>
                    <div class="action-status">
                      <el-tag :type="detail.status === 'NORMAL' ? 'success' : 'danger'" size="small">
                        {{ detail.status === 'NORMAL' ? '正常' : '已封禁' }}
                      </el-tag>
                    </div>
                  </div>
                </div>
                <el-space wrap>
                  <el-button v-if="detail.status === 'NORMAL'" type="danger" @click="handleFreeze">封禁账号</el-button>
                  <el-button v-else type="success" @click="handleUnfreeze">解封账号</el-button>
                  <el-button type="warning" @click="handleResetPwd">重置密码</el-button>
                </el-space>
              </div>
            </el-card>
          </div>
        </el-tab-pane>
      </el-tabs>
    </div>
  </el-drawer>
</template>

<script setup lang="ts">
import { ref, reactive, computed, watch, nextTick } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { UserFilled } from '@element-plus/icons-vue'
import {
  getUserDetailApi,
  freezeUserApi,
  unfreezeUserApi,
  resetUserPasswordApi,
  getUserChatSessionsApi,
  getUserChatMessagesApi,
} from '@/api/user'
import type { UserDetail, ChatSession, ChatMessage } from '@/api/user'
import { getMovementPageApi, type MovementItem } from '@/api/movement'
import { getVideoPageApi, type VideoItem } from '@/api/video'
import { getCommentPageApi, type CommentItem } from '@/api/comment'
import { getReportPageApi, type ReportItem } from '@/api/report'

const props = defineProps<{
  modelValue: boolean
  userId: number
}>()

const emit = defineEmits<{
  'update:modelValue': [value: boolean]
}>()

const visible = computed({
  get: () => props.modelValue,
  set: (val: boolean) => emit('update:modelValue', val),
})

function handleModelValueChange(val: boolean) {
  visible.value = val
}

// ── 基本信息 ──
const activeTab = ref('basic')
const detailLoading = ref(false)
const detail = ref<UserDetail>()

const stats = computed(() => [
  { label: '动态数', value: detail.value?.postCount ?? 0 },
  { label: '粉丝数', value: detail.value?.followerCount ?? 0 },
  { label: '关注数', value: detail.value?.followingCount ?? 0 },
  { label: '匹配数', value: detail.value?.matchCount ?? 0 },
  { label: '被举报数', value: detail.value?.reportCount ?? 0 },
  { label: 'VIP等级', value: detail.value?.vipLevel ?? 0 },
])

const statsLoadingNote = computed(() =>
  detail.value && (detail.value.postCount === 0 || detail.value.followerCount === 0)
)

async function loadDetail() {
  if (!props.userId) return
  detailLoading.value = true
  try {
    detail.value = await getUserDetailApi(props.userId)
  } catch {
    ElMessage.error('用户信息加载失败，请刷新重试')
  } finally {
    detailLoading.value = false
  }
}

// ── 聊天记录 ──
const chatSessions = ref<ChatSession[]>([])
const chatSessionsLoading = ref(false)
const activeContactId = ref('')
const chatMessages = ref<ChatMessage[]>([])
const chatMessagesLoading = ref(false)
const messageListRef = ref<HTMLElement>()

const activeContactNickname = computed(() => {
  const s = chatSessions.value.find(s => s.contactId === activeContactId.value)
  return s?.nickname || activeContactId.value
})

async function loadChatSessions() {
  if (!props.userId) return
  chatSessionsLoading.value = true
  try {
    const res = await getUserChatSessionsApi(props.userId)
    chatSessions.value = res.list || []
  } catch {
    chatSessions.value = []
  } finally {
    chatSessionsLoading.value = false
  }
}

async function loadChatMessages(contactId: string) {
  if (!props.userId) return
  activeContactId.value = contactId
  chatMessagesLoading.value = true
  chatMessages.value = []
  try {
    const res = await getUserChatMessagesApi(props.userId, contactId, 50)
    chatMessages.value = res.messages || []
    await nextTick()
    if (messageListRef.value) {
      messageListRef.value.scrollTop = messageListRef.value.scrollHeight
    }
  } catch {
    chatMessages.value = []
  } finally {
    chatMessagesLoading.value = false
  }
}

function formatTime(ts: number): string {
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

// ── 发送的图片和视频 ──
const contentSubTab = ref('movement')
const movementData = ref<MovementItem[]>([])
const movementLoading = ref(false)
const videoData = ref<VideoItem[]>([])
const videoLoading = ref(false)
const commentData = ref<CommentItem[]>([])
const commentLoading = ref(false)

async function loadMovements() {
  if (!props.userId) return
  movementLoading.value = true
  try {
    const res = await getMovementPageApi({ page: 1, pageSize: 20, userId: props.userId })
    movementData.value = res.list || []
  } catch {
    movementData.value = []
  } finally {
    movementLoading.value = false
  }
}

async function loadVideos() {
  if (!props.userId) return
  videoLoading.value = true
  try {
    const res = await getVideoPageApi({ page: 1, pageSize: 20, userId: props.userId })
    videoData.value = res.list || []
  } catch {
    videoData.value = []
  } finally {
    videoLoading.value = false
  }
}

async function loadComments() {
  if (!props.userId) return
  commentLoading.value = true
  try {
    const res = await getCommentPageApi({ page: 1, pageSize: 20, userId: props.userId })
    commentData.value = res.list || []
  } catch {
    commentData.value = []
  } finally {
    commentLoading.value = false
  }
}

// ── 被举报记录 ──
const reportData = ref<ReportItem[]>([])
const reportLoading = ref(false)
const reportTotal = ref(0)
const reportQuery = reactive({ page: 1, pageSize: 10 })

async function loadReports() {
  if (!props.userId) return
  reportLoading.value = true
  try {
    const res = await getReportPageApi({
      page: reportQuery.page,
      pageSize: reportQuery.pageSize,
      targetUserId: props.userId,
    })
    reportData.value = res.list || []
    reportTotal.value = res.total
  } catch {
    reportData.value = []
    reportTotal.value = 0
  } finally {
    reportLoading.value = false
  }
}

function reportTypeLabel(type: string): string {
  const map: Record<string, string> = { USER: '用户', POST: '动态', COMMENT: '评论', MESSAGE: '私信' }
  return map[type] || type
}

function reportTypeColor(type: string): string {
  const map: Record<string, string> = { USER: 'danger', POST: 'primary', COMMENT: 'warning', MESSAGE: 'info' }
  return map[type] || 'info'
}

function reportStatusLabel(status: string): string {
  const map: Record<string, string> = { PENDING: '待处理', PROCESSING: '处理中', RESOLVED: '已处理', DISMISSED: '已驳回' }
  return map[status] || status
}

function reportStatusColor(status: string): string {
  const map: Record<string, string> = { PENDING: 'warning', PROCESSING: 'primary', RESOLVED: 'success', DISMISSED: 'info' }
  return map[status] || 'info'
}

// ── 账号操作 ──
async function handleFreeze() {
  if (!detail.value) return
  try {
    const { value: reason } = await ElMessageBox.prompt('请输入封禁原因', '封禁账号', {
      confirmButtonText: '确认封禁',
      cancelButtonText: '取消',
      inputPattern: /\S+/,
      inputErrorMessage: '封禁原因不能为空',
    })
    await freezeUserApi(detail.value.id, reason)
    ElMessage.success('已封禁')
    loadDetail()
  } catch {
    // 取消
  }
}

async function handleUnfreeze() {
  if (!detail.value) return
  try {
    await ElMessageBox.confirm('确定解封该账号吗？', '提示', { type: 'warning' })
    await unfreezeUserApi(detail.value.id)
    ElMessage.success('已解封')
    loadDetail()
  } catch {
    // 取消
  }
}

async function handleResetPwd() {
  if (!detail.value) return
  try {
    await ElMessageBox.confirm('确定重置该用户密码吗？', '提示', { type: 'warning' })
    await resetUserPasswordApi(detail.value.id)
    ElMessage.success('密码已重置')
  } catch {
    // 取消
  }
}

// ── 抽屉打开时加载数据，tab 切换时按需懒加载 ──
const loadedTabs = ref<Set<string>>(new Set())

watch(
  () => props.modelValue,
  async (val) => {
    if (!val) return
    // 抽屉打开：重置状态，加载基本信息
    activeTab.value = 'basic'
    contentSubTab.value = 'movement'
    activeContactId.value = ''
    loadedTabs.value.clear()
    await loadDetail()
    loadedTabs.value.add('basic')
  }
)

watch(activeTab, async (tab) => {
  if (!props.modelValue) return
  if (loadedTabs.value.has(tab)) return
  loadedTabs.value.add(tab)

  if (tab === 'chat') {
    await loadChatSessions()
  } else if (tab === 'content') {
    await loadMovements()
    await loadVideos()
    await loadComments()
  } else if (tab === 'reports') {
    await loadReports()
  }
})
</script>

<style scoped>
.detail-tabs {
  height: 100%;
}

.detail-tabs :deep(.el-tabs__content) {
  padding: 16px 4px 0;
  overflow-y: auto;
}

.tab-content {
  min-height: 200px;
}

/* 基本信息 */
.user-profile {
  display: flex;
  gap: 20px;
}

.profile-name {
  font-size: 18px;
  font-weight: 600;
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 8px;
}

.profile-meta {
  display: flex;
  gap: 24px;
  color: #606266;
  font-size: 13px;
  margin-bottom: 4px;
}

.profile-signature {
  color: #909399;
  font-size: 13px;
  margin-top: 8px;
}

.freeze-reason {
  color: #f56c6c;
  font-size: 13px;
  margin-top: 4px;
}

/* 数据统计 */
.stat-row {
  margin-bottom: 8px;
}

.stat-box {
  text-align: center;
  padding: 16px;
  background: #f5f7fa;
  border-radius: 8px;
}

.stat-num {
  font-size: 22px;
  font-weight: 600;
  color: #303133;
}

.stat-text {
  font-size: 12px;
  color: #909399;
  margin-top: 4px;
}

/* 照片墙 */
.photo-wall {
  display: flex;
  flex-wrap: wrap;
  gap: 4px;
}

/* 标签 */
.tag-list {
  display: flex;
  flex-wrap: wrap;
}

/* 聊天记录 */
.chat-tab-content {
  padding: 0;
}

.chat-container {
  display: flex;
  gap: 0;
  height: 480px;
  border: 1px solid #e4e7ed;
  border-radius: 8px;
  overflow: hidden;
}

.chat-session-panel {
  width: 200px;
  border-right: 1px solid #e4e7ed;
  display: flex;
  flex-direction: column;
  background: #fafafa;
}

.session-header {
  padding: 10px 12px;
  font-size: 13px;
  font-weight: 600;
  border-bottom: 1px solid #e4e7ed;
  color: #303133;
}

.session-list {
  flex: 1;
  overflow-y: auto;
}

.session-item {
  display: flex;
  gap: 8px;
  align-items: flex-start;
  padding: 10px 12px;
  cursor: pointer;
  border-bottom: 1px solid #f0f0f0;
  transition: background 0.2s;
}

.session-item:hover {
  background: #ecf5ff;
}

.session-item.active {
  background: #409eff;
  color: #fff;
}

.session-item.active .session-msg,
.session-item.active .session-time,
.session-item.active .session-id {
  color: #fff;
}

.session-avatar {
  flex-shrink: 0;
}

.session-info {
  flex: 1;
  min-width: 0;
}

.session-contact {
  display: flex;
  align-items: baseline;
  gap: 4px;
  margin-bottom: 4px;
}

.session-name {
  font-size: 13px;
  font-weight: 500;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.session-id {
  font-size: 11px;
  color: #c0c4cc;
  font-weight: 400;
  flex-shrink: 0;
}

.session-msg {
  font-size: 12px;
  color: #909399;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  margin-bottom: 2px;
}

.session-time {
  font-size: 11px;
  color: #c0c4cc;
}

.loading-tip {
  padding: 16px;
  text-align: center;
  color: #909399;
  font-size: 13px;
}

.chat-message-panel {
  flex: 1;
  display: flex;
  flex-direction: column;
}

.message-header {
  padding: 10px 12px;
  font-size: 13px;
  font-weight: 600;
  border-bottom: 1px solid #e4e7ed;
  color: #303133;
}

.message-list {
  flex: 1;
  overflow-y: auto;
  padding: 12px;
}

.message-item {
  margin-bottom: 12px;
  display: flex;
  flex-direction: column;
}

.message-item.mine {
  align-items: flex-end;
}

.message-time {
  font-size: 11px;
  color: #c0c4cc;
  margin-bottom: 4px;
}

.bubble {
  max-width: 60%;
  padding: 8px 12px;
  border-radius: 8px;
  font-size: 13px;
  line-height: 1.5;
  word-break: break-all;
  background: #f4f4f5;
  color: #303133;
}

.message-item.mine .bubble {
  background: #409eff;
  color: #fff;
}

/* 发送的图片和视频 */
.content-sub-tabs {
  margin-top: 8px;
}

/* 被举报记录 */
.user-cell {
  display: flex;
  align-items: center;
  gap: 6px;
}

.pagination-wrapper {
  margin-top: 12px;
  display: flex;
  justify-content: flex-end;
}

/* 账号操作 */
.action-bar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
}

.action-info {
  display: flex;
  align-items: center;
  gap: 12px;
}

.action-name {
  font-size: 15px;
  font-weight: 600;
}

.action-status {
  margin-top: 4px;
}

.text-muted {
  color: #c0c4cc;
}
</style>
