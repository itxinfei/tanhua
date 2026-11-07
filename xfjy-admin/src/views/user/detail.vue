<template>
  <el-drawer
    :model-value="modelValue"
    title="用户详情"
    :size="isMobile ? '100%' : '720px'"
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

        <!-- 资料编辑（客服代改） -->
        <el-tab-pane label="资料编辑" name="profile-edit">
          <div v-if="detail" v-loading="profileSaving" class="tab-content">
            <el-form :model="profileForm" label-width="80px" class="profile-form">
              <el-form-item label="头像">
                <div class="avatar-edit-row">
                  <el-upload
                    :show-file-list="false"
                    :http-request="uploadAvatar"
                    :before-upload="beforeAvatarUpload"
                    :disabled="avatarUploading"
                    accept="image/*"
                  >
                    <div class="avatar-edit-wrap">
                      <el-avatar
                        :size="64"
                        :src="profileForm.avatar"
                        class="avatar-edit"
                        role="button"
                        tabindex="0"
                        aria-label="更换头像"
                      >
                        <el-icon :size="28"><UserFilled /></el-icon>
                      </el-avatar>
                      <div v-if="avatarUploading" class="avatar-uploading">
                        <el-icon class="is-loading" :size="20"><Loading /></el-icon>
                      </div>
                    </div>
                  </el-upload>
                  <el-button v-if="profileForm.avatar" @click="clearAvatar">清除头像</el-button>
                </div>
              </el-form-item>

              <el-form-item label="昵称">
                <el-input v-model="profileForm.nickname" maxlength="20" show-word-limit style="max-width: 280px" />
              </el-form-item>

              <el-form-item label="性别">
                <el-radio-group v-model="profileForm.gender">
                  <el-radio value="man">男</el-radio>
                  <el-radio value="woman">女</el-radio>
                </el-radio-group>
              </el-form-item>

              <el-form-item label="签名">
                <el-input
                  v-model="profileForm.signature"
                  type="textarea"
                  :rows="2"
                  maxlength="50"
                  show-word-limit
                  style="max-width: 420px"
                />
              </el-form-item>

              <el-form-item label="城市">
                <el-input v-model="profileForm.city" maxlength="20" style="max-width: 280px" placeholder="如 北京市-朝阳区" />
              </el-form-item>

              <el-form-item label="身高">
                <el-select v-model="profileForm.height" clearable placeholder="未设置" style="width: 200px">
                  <el-option v-for="h in heightOptions" :key="h" :label="`${h}cm`" :value="h" />
                </el-select>
              </el-form-item>

              <el-form-item label="学历">
                <el-select v-model="profileForm.education" clearable placeholder="未设置" style="width: 200px">
                  <el-option v-for="e in educationOptions" :key="e" :label="e" :value="e" />
                </el-select>
              </el-form-item>

              <el-form-item label="行业">
                <el-select v-model="profileForm.profession" clearable filterable placeholder="未设置" style="width: 200px">
                  <el-option v-for="p in industryOptions" :key="p" :label="p" :value="p" />
                </el-select>
              </el-form-item>

              <el-form-item label="收入">
                <el-input v-model="profileForm.income" maxlength="20" style="max-width: 280px" placeholder="如 15000-20000" />
              </el-form-item>

              <el-form-item label="婚姻">
                <el-radio-group v-model="profileForm.marriage">
                  <el-radio :value="0">未婚</el-radio>
                  <el-radio :value="1">已婚</el-radio>
                </el-radio-group>
              </el-form-item>

              <el-form-item label="照片墙">
                <div class="photo-editor">
                  <div v-for="(photo, i) in profilePhotos" :key="`${photo}-${i}`" class="photo-edit-item">
                    <el-image
                      :src="photo"
                      fit="cover"
                      preview-teleported
                      hide-on-click-modal
                      class="photo-edit-img"
                      :preview-src-list="profilePhotos"
                      :initial-index="i"
                    />
                    <div class="photo-remove" @click="removePhoto(i)">
                      <el-icon :size="12"><Close /></el-icon>
                    </div>
                  </div>
                  <el-upload
                    v-if="profilePhotos.length < 6"
                    :show-file-list="false"
                    :http-request="uploadPhoto"
                    accept="image/*"
                  >
                    <div class="photo-add"><el-icon :size="24"><Plus /></el-icon></div>
                  </el-upload>
                </div>
                <div class="photo-tip">最多 6 张（当前 {{ profilePhotos.length }}/6），修改后需点击底部保存</div>
              </el-form-item>

              <el-form-item>
                <el-button type="primary" :loading="profileSaving" @click="handleSaveProfile">保存修改</el-button>
                <el-button @click="resetProfileForm">重置</el-button>
              </el-form-item>
            </el-form>

            <div class="profile-log-title">资料修改记录</div>
            <el-table :data="profileLogs" v-loading="profileLogsLoading" stripe size="small" style="width: 100%">
              <el-table-column prop="operatorName" label="操作人" width="120" />
              <el-table-column prop="detail" label="变更内容" min-width="220" show-overflow-tooltip />
              <el-table-column prop="ip" label="IP" width="130" />
              <el-table-column label="时间" width="150">
                <template #default="{ row }">{{ formatDateTime(row.createdAt) }}</template>
              </el-table-column>
            </el-table>
            <div v-if="profileLogs.length" class="pagination-wrapper">
              <el-pagination
                v-model:current-page="profileLogQuery.page"
                v-model:page-size="profileLogQuery.pageSize"
                :total="profileLogTotal"
                :page-sizes="[10, 20]"
                layout="total, prev, pager, next"
                @current-change="loadProfileLogs"
              />
            </div>
            <el-empty v-if="!profileLogs.length && !profileLogsLoading" description="暂无修改记录" :image-size="50" />
          </div>
        </el-tab-pane>

        <!-- 关系链（关注/粉丝/匹配，只读） -->
        <el-tab-pane label="关系链" name="relations">
          <div v-if="detail" v-loading="relationLoading" class="tab-content">
            <el-radio-group v-model="relationType" size="small" @change="onRelationTypeChange">
              <el-radio-button value="follow">关注</el-radio-button>
              <el-radio-button value="fans">粉丝</el-radio-button>
              <el-radio-button value="match">匹配</el-radio-button>
            </el-radio-group>
            <el-table :data="relationList" stripe size="small" class="relation-table">
              <el-table-column label="头像" width="80">
                <template #default="{ row }">
                  <el-avatar :size="36" :src="row.avatar || undefined">{{ (row.nickname || '?').slice(0, 1) }}</el-avatar>
                </template>
              </el-table-column>
              <el-table-column prop="nickname" label="昵称" min-width="120" show-overflow-tooltip />
              <el-table-column label="性别" width="80" align="center">
                <template #default="{ row }">
                  <el-tag :type="row.gender === '女' ? 'danger' : 'primary'" size="small">{{ row.gender }}</el-tag>
                </template>
              </el-table-column>
              <el-table-column prop="age" label="年龄" width="80" align="center" />
              <el-table-column prop="city" label="城市" width="110" show-overflow-tooltip />
              <el-table-column label="关注时间" width="150">
                <template #default="{ row }">{{ formatDateTime(row.createdAt) }}</template>
              </el-table-column>
              <el-table-column label="操作" width="92" align="center" fixed="right">
                <template #default="{ row }">
                  <el-button link type="primary" @click="viewRelationUser(row.userId)">查看详情</el-button>
                </template>
              </el-table-column>
            </el-table>
            <div v-if="relationList.length" class="pagination-wrapper">
              <el-pagination
                v-model:current-page="relationQuery.page"
                v-model:page-size="relationQuery.pageSize"
                :total="relationTotal"
                :page-sizes="[10, 20]"
                layout="total, prev, pager, next"
                @current-change="loadRelations"
              />
            </div>
            <el-empty v-if="!relationList.length && !relationLoading" :description="relationEmptyText" :image-size="50" />
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
                style="width: 110px; height: 110px; border-radius: var(--xfjy-radius-md); margin: 4px"
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
                        style="width: 48px; height: 48px; border-radius: var(--xfjy-radius-sm)"
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
                        style="width: 48px; height: 48px; border-radius: var(--xfjy-radius-sm)"
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
                  <el-button @click="handleResetPwd">重置密码</el-button>
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
import { ref, reactive, computed, watch, nextTick, h } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { UserFilled, Close, Plus, Loading } from '@element-plus/icons-vue'
import { useResponsive } from '@/composables/useResponsive'
import {
  getUserDetailApi,
  freezeUserApi,
  unfreezeUserApi,
  resetUserPasswordApi,
  getUserChatSessionsApi,
  getUserChatMessagesApi,
  updateUserProfileApi,
  getProfileLogsApi,
  getUserRelationsApi,
} from '@/api/user'
import type {
  UserDetail,
  ChatSession,
  ChatMessage,
  AdminProfileUpdate,
  ProfileLogItem,
  RelationUserItem,
  RelationType,
} from '@/api/user'
import { uploadImageApi } from '@/api/upload'
import { formatDateTime } from '@/utils/format'
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
  'view-user': [id: number]
}>()

// 响应式：移动端抽屉全宽
const { isMobile } = useResponsive()

function handleModelValueChange(val: boolean) {
  emit('update:modelValue', val)
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

// ── 资料编辑（客服代改） ──
// 选项与 C 端 xfjy-app profile.vue 保持一致
const heightOptions = ['150', '155', '160', '165', '170', '175', '180', '185', '190', '195']
const educationOptions = ['高中', '大专', '本科', '硕士', '博士']
const industryOptions = [
  'IT/互联网', '金融', '教育', '医疗', '制造业', '服务业',
  '文化传媒', '房地产', '公务员', '自由职业', '学生', '其他',
]

interface ProfileFormState {
  nickname: string
  avatar: string
  signature: string
  gender: string
  city: string
  height: string
  education: string
  profession: string
  income: string
  marriage: number | undefined
}

const profileForm = reactive<ProfileFormState>({
  nickname: '', avatar: '', signature: '', gender: '',
  city: '', height: '', education: '', profession: '', income: '',
  marriage: undefined,
})
const profilePhotos = ref<string[]>([])
const profileSaving = ref(false)
const avatarUploading = ref(false)
const profileLogs = ref<ProfileLogItem[]>([])
const profileLogsLoading = ref(false)
const profileLogTotal = ref(0)
const profileLogQuery = reactive({ page: 1, pageSize: 10 })

/** 用详情数据回填编辑表单（detail 为后端当前值，也是变更对比基准） */
function fillProfileForm() {
  const d = detail.value
  if (!d) return
  profileForm.nickname = d.nickname ?? ''
  profileForm.avatar = d.avatar ?? ''
  profileForm.signature = d.signature ?? ''
  profileForm.gender = d.gender === '男' ? 'man' : d.gender === '女' ? 'woman' : ''
  profileForm.city = d.city ?? ''
  profileForm.height = d.height ?? ''
  profileForm.education = d.edu ?? ''
  profileForm.profession = d.industry ?? ''
  profileForm.income = d.income ?? ''
  profileForm.marriage = d.marriage === '已婚' ? 1 : d.marriage === '未婚' ? 0 : undefined
  profilePhotos.value = [...(d.photos || [])]
}

function resetProfileForm() {
  fillProfileForm()
}

/** el-upload 自定义头像上传（走后台 /admin/upload/image）；上限 2MB，格式与大小进入上传前拦截 */
const AVATAR_MAX_SIZE = 2 * 1024 * 1024

function beforeAvatarUpload(file: File): boolean {
  if (!file.type.startsWith('image/')) {
    ElMessage.error('仅支持图片格式')
    return false
  }
  if (file.size > AVATAR_MAX_SIZE) {
    ElMessage.error('图片不能超过 2MB')
    return false
  }
  return true
}

async function uploadAvatar(options: { file: File }) {
  if (avatarUploading.value) return
  avatarUploading.value = true
  try {
    const res = await uploadImageApi(options.file)
    profileForm.avatar = res.name
    ElMessage.success('头像已上传，点击底部保存后生效')
  } catch {
    ElMessage.error('头像上传失败，请检查网络后重试')
  } finally {
    avatarUploading.value = false
  }
}

/** el-upload 自定义照片墙上传 */
async function uploadPhoto(options: { file: File }) {
  if (profilePhotos.value.length >= 6) {
    ElMessage.warning('照片墙最多 6 张')
    return
  }
  try {
    const res = await uploadImageApi(options.file)
    profilePhotos.value.push(res.name)
    ElMessage.success('照片已加入，点击底部保存后生效')
  } catch {
    // request 层已统一提示
  }
}

function removePhoto(index: number) {
  profilePhotos.value.splice(index, 1)
}

function clearAvatar() {
  profileForm.avatar = ''
}

function diffText(label: string, oldVal: string, newVal: string, out: string[]) {
  const oldStr = (oldVal ?? '').trim()
  const newStr = (newVal ?? '').trim()
  if (oldStr !== newStr) {
    out.push(`${label}：${oldStr || '（空）'} → ${newStr || '（空）'}`)
  }
}

/** 对比表单与后端当前值，组装只含变更字段的 payload 与变更说明 */
function buildProfilePayload(): { payload: AdminProfileUpdate; diffs: string[] } {
  const d = detail.value as UserDetail
  const f = profileForm
  const payload: AdminProfileUpdate = {}
  const diffs: string[] = []

  diffText('昵称', d.nickname ?? '', f.nickname, diffs)
  if (diffs.some(x => x.startsWith('昵称'))) payload.nickname = f.nickname

  diffText('签名', d.signature ?? '', f.signature, diffs)
  if (diffs.some(x => x.startsWith('签名'))) payload.signature = f.signature

  diffText('城市', d.city ?? '', f.city, diffs)
  if (diffs.some(x => x.startsWith('城市'))) payload.city = f.city

  diffText('身高', d.height ?? '', f.height, diffs)
  if (diffs.some(x => x.startsWith('身高'))) payload.height = f.height

  diffText('学历', d.edu ?? '', f.education, diffs)
  if (diffs.some(x => x.startsWith('学历'))) payload.education = f.education

  diffText('行业', d.industry ?? '', f.profession, diffs)
  if (diffs.some(x => x.startsWith('行业'))) payload.profession = f.profession

  diffText('收入', d.income ?? '', f.income, diffs)
  if (diffs.some(x => x.startsWith('收入'))) payload.income = f.income

  const oldGender = d.gender === '男' ? 'man' : d.gender === '女' ? 'woman' : ''
  if (oldGender !== f.gender) {
    payload.gender = f.gender
    diffs.push(`性别：${oldGender === 'man' ? '男' : oldGender === 'woman' ? '女' : '（空）'} → ${f.gender === 'man' ? '男' : '女'}`)
  }

  const oldMarriage = d.marriage === '已婚' ? 1 : d.marriage === '未婚' ? 0 : undefined
  if (oldMarriage !== f.marriage) {
    if (f.marriage !== undefined) payload.marriage = f.marriage
    const marriageLabel = (v: number | undefined) => (v === 1 ? '已婚' : v === 0 ? '未婚' : '（空）')
    diffs.push(`婚姻：${marriageLabel(oldMarriage)} → ${marriageLabel(f.marriage)}`)
  }

  if ((d.avatar ?? '') !== f.avatar) {
    payload.avatar = f.avatar
    diffs.push(f.avatar ? '头像：已更换' : '头像：已清空')
  }

  const oldAlbum = JSON.stringify(d.photos || [])
  const newAlbum = JSON.stringify(profilePhotos.value)
  if (oldAlbum !== newAlbum) {
    payload.photoAlbum = newAlbum
    diffs.push(`照片墙：${(d.photos || []).length}张 → ${profilePhotos.value.length}张`)
  }

  return { payload, diffs }
}

async function handleSaveProfile() {
  if (!detail.value) return
  const nickname = profileForm.nickname.trim()
  if (nickname.length < 2 || nickname.length > 20) {
    ElMessage.warning('昵称长度需 2-20 个字符')
    return
  }
  const { payload, diffs } = buildProfilePayload()
  if (diffs.length === 0) {
    ElMessage.info('未检测到资料变更')
    return
  }
  try {
    await ElMessageBox.confirm(
      h('div', { style: 'line-height: 1.9' }, [
        '确认代用户修改以下资料？',
        h('div', diffs.map(line => h('div', line))),
      ]),
      '资料变更确认',
      { type: 'warning', confirmButtonText: '确认修改', cancelButtonText: '取消' }
    )
  } catch {
    return // 取消
  }
  profileSaving.value = true
  try {
    await updateUserProfileApi(detail.value.id, payload)
    ElMessage.success('资料已更新')
    await loadDetail()
    fillProfileForm()
    profileLogQuery.page = 1
    await loadProfileLogs()
  } catch {
    // 失败保留编辑内容，request 层已提示
  } finally {
    profileSaving.value = false
  }
}

async function loadProfileLogs() {
  if (!props.userId) return
  profileLogsLoading.value = true
  try {
    const res = await getProfileLogsApi(props.userId, {
      page: profileLogQuery.page,
      pageSize: profileLogQuery.pageSize,
    })
    profileLogs.value = res.list || []
    profileLogTotal.value = res.total
  } catch {
    profileLogs.value = []
    profileLogTotal.value = 0
  } finally {
    profileLogsLoading.value = false
  }
}

// ── 关系链（关注/粉丝/匹配，只读） ──
const relationType = ref<RelationType>('follow')
const relationList = ref<RelationUserItem[]>([])
const relationTotal = ref(0)
const relationLoading = ref(false)
const relationQuery = reactive({ page: 1, pageSize: 10 })

const relationEmptyText = computed(() => {
  if (relationType.value === 'fans') return '暂无粉丝'
  if (relationType.value === 'match') return '暂无匹配'
  return '暂未关注任何人'
})

async function loadRelations() {
  if (!props.userId) return
  relationLoading.value = true
  try {
    const res = await getUserRelationsApi(props.userId, {
      type: relationType.value,
      page: relationQuery.page,
      pageSize: relationQuery.pageSize,
    })
    relationList.value = res.list || []
    relationTotal.value = res.total
  } catch {
    relationList.value = []
    relationTotal.value = 0
  } finally {
    relationLoading.value = false
  }
}

/** 切换关系类型：回到第一页并重新加载 */
function onRelationTypeChange() {
  relationQuery.page = 1
  loadRelations()
}

/** 查看关系对方详情：通知父组件切换抽屉用户 */
function viewRelationUser(id: number) {
  emit('view-user', id)
}

// ── 抽屉打开时加载数据，tab 切换时按需懒加载 ──
const loadedTabs = ref<Set<string>>(new Set())

watch(
  [() => props.modelValue, () => props.userId],
  async ([val]) => {
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
  } else if (tab === 'profile-edit') {
    fillProfileForm()
    await loadProfileLogs()
  } else if (tab === 'relations') {
    await loadRelations()
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

.relation-table {
  margin-top: var(--xfjy-space-3);
}

/* 基本信息 */
.user-profile {
  display: flex;
  gap: var(--xfjy-space-5);
}

.profile-name {
  font-size: 18px;
  font-weight: 600;
  display: flex;
  align-items: center;
  gap: var(--xfjy-space-2);
  margin-bottom: var(--xfjy-space-2);
}

.profile-meta {
  display: flex;
  gap: var(--xfjy-space-6);
  color: var(--xfjy-text-regular);
  font-size: var(--xfjy-font-size-sm);
  margin-bottom: var(--xfjy-space-1);
}

.profile-signature {
  color: var(--xfjy-text-secondary);
  font-size: var(--xfjy-font-size-sm);
  margin-top: var(--xfjy-space-2);
}

.freeze-reason {
  color: var(--xfjy-danger);
  font-size: var(--xfjy-font-size-sm);
  margin-top: var(--xfjy-space-1);
}

/* 数据统计 */
.stat-row {
  margin-bottom: var(--xfjy-space-2);
}

.stat-box {
  text-align: center;
  padding: var(--xfjy-space-4);
  background: var(--xfjy-bg-hover);
  border-radius: var(--xfjy-radius-md);
}

.stat-num {
  font-size: 22px;
  font-weight: 600;
  color: var(--xfjy-text-primary);
}

.stat-text {
  font-size: var(--xfjy-font-size-xs);
  color: var(--xfjy-text-secondary);
  margin-top: var(--xfjy-space-1);
}

/* 照片墙 */
.photo-wall {
  display: flex;
  flex-wrap: wrap;
  gap: var(--xfjy-space-1);
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
  border: 1px solid var(--xfjy-border);
  border-radius: var(--xfjy-radius-md);
  overflow: hidden;
  background: var(--xfjy-bg-card);
}

.chat-session-panel {
  width: 200px;
  border-right: 1px solid var(--xfjy-border);
  display: flex;
  flex-direction: column;
  background: var(--xfjy-bg-header);
}

.session-header {
  padding: var(--xfjy-space-2) var(--xfjy-space-3);
  font-size: var(--xfjy-font-size-sm);
  font-weight: 600;
  border-bottom: 1px solid var(--xfjy-border);
  color: var(--xfjy-text-primary);
}

.session-list {
  flex: 1;
  overflow-y: auto;
}

.session-item {
  display: flex;
  gap: var(--xfjy-space-2);
  align-items: flex-start;
  padding: var(--xfjy-space-2) var(--xfjy-space-3);
  cursor: pointer;
  border-bottom: 1px solid var(--xfjy-border-light);
  transition: background 0.2s;
}

.session-item:hover {
  background: var(--xfjy-primary-50);
}

.session-item.active {
  background: var(--xfjy-primary);
  color: var(--xfjy-text-inverse);
}

.session-item.active .session-msg,
.session-item.active .session-time,
.session-item.active .session-id {
  color: var(--xfjy-text-inverse);
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
  gap: var(--xfjy-space-1);
  margin-bottom: var(--xfjy-space-1);
}

.session-name {
  font-size: var(--xfjy-font-size-sm);
  font-weight: 500;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.session-id {
  font-size: 11px;
  color: var(--xfjy-text-disabled);
  font-weight: 400;
  flex-shrink: 0;
}

.session-msg {
  font-size: var(--xfjy-font-size-xs);
  color: var(--xfjy-text-secondary);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  margin-bottom: 2px;
}

.session-time {
  font-size: 11px;
  color: var(--xfjy-text-disabled);
}

.loading-tip {
  padding: var(--xfjy-space-4);
  text-align: center;
  color: var(--xfjy-text-secondary);
  font-size: var(--xfjy-font-size-sm);
}

.chat-message-panel {
  flex: 1;
  display: flex;
  flex-direction: column;
}

.message-header {
  padding: var(--xfjy-space-2) var(--xfjy-space-3);
  font-size: var(--xfjy-font-size-sm);
  font-weight: 600;
  border-bottom: 1px solid var(--xfjy-border);
  color: var(--xfjy-text-primary);
}

.message-list {
  flex: 1;
  overflow-y: auto;
  padding: var(--xfjy-space-3);
}

.message-item {
  margin-bottom: var(--xfjy-space-3);
  display: flex;
  flex-direction: column;
}

.message-item.mine {
  align-items: flex-end;
}

.message-time {
  font-size: 11px;
  color: var(--xfjy-text-disabled);
  margin-bottom: var(--xfjy-space-1);
}

.bubble {
  max-width: 60%;
  padding: var(--xfjy-space-2) var(--xfjy-space-3);
  border-radius: var(--xfjy-radius-md);
  font-size: var(--xfjy-font-size-sm);
  line-height: 1.5;
  word-break: break-all;
  background: var(--xfjy-bg-hover);
  color: var(--xfjy-text-primary);
}

.message-item.mine .bubble {
  background: var(--xfjy-primary);
  color: var(--xfjy-text-inverse);
}

/* 移动端：聊天容器占满可视高度，避免固定 480px 超出抽屉 */
@media (max-width: 767px) {
  .chat-container {
    height: min(480px, 60vh);
  }
  .chat-session-panel {
    width: 140px;
  }
}

/* 发送的图片和视频 */
.content-sub-tabs {
  margin-top: var(--xfjy-space-2);
}

/* 被举报记录 */
.user-cell {
  display: flex;
  align-items: center;
  /* stylelint-disable-next-line declaration-property-value-disallowed-list -- 头像与文字间距 6px，见 AGENTS.md §11 存量豁免 */
  gap: 6px;
}

.pagination-wrapper {
  margin-top: var(--xfjy-space-3);
  display: flex;
  justify-content: flex-end;
}

/* 账号操作 */
.action-bar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--xfjy-space-4);
}

.action-info {
  display: flex;
  align-items: center;
  gap: var(--xfjy-space-3);
}

.action-name {
  font-size: 15px;
  font-weight: 600;
}

.action-status {
  margin-top: var(--xfjy-space-1);
}

.text-muted {
  color: var(--xfjy-text-disabled);
}

/* 资料编辑 */
.profile-form {
  max-width: 560px;
}

.avatar-edit-row {
  display: flex;
  align-items: center;
  gap: var(--xfjy-space-3);
}

.avatar-edit-wrap {
  position: relative;
  display: inline-flex;
  border-radius: 50%;
  cursor: pointer;
}

.avatar-edit {
  cursor: pointer;
  border: 1px solid var(--xfjy-border);
  transition: border-color 0.2s;
}

.avatar-edit-wrap:hover .avatar-edit,
.avatar-edit-wrap:focus-within .avatar-edit {
  border-color: var(--xfjy-primary);
}

.avatar-uploading {
  position: absolute;
  top: 0;
  left: 0;
  right: 0;
  bottom: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  border-radius: 50%;
  background: var(--xfjy-bg-card);
  opacity: 0.85;
}

.photo-editor {
  display: flex;
  flex-wrap: wrap;
  gap: var(--xfjy-space-2);
}

.photo-edit-item {
  position: relative;
  width: 88px;
  height: 88px;
}

.photo-edit-img {
  width: 88px;
  height: 88px;
  border-radius: var(--xfjy-radius-sm);
}

.photo-remove {
  position: absolute;
  top: -8px;
  right: -8px;
  width: 20px;
  height: 20px;
  border-radius: 50%;
  background: var(--xfjy-danger);
  color: var(--xfjy-text-inverse);
  display: flex;
  align-items: center;
  justify-content: center;
  cursor: pointer;
}

.photo-add {
  width: 88px;
  height: 88px;
  border: 1px dashed var(--xfjy-border);
  border-radius: var(--xfjy-radius-sm);
  display: flex;
  align-items: center;
  justify-content: center;
  color: var(--xfjy-text-secondary);
  cursor: pointer;
}

.photo-add:hover {
  border-color: var(--xfjy-primary);
  color: var(--xfjy-primary);
}

.photo-tip {
  margin-top: var(--xfjy-space-1);
  font-size: var(--xfjy-font-size-xs);
  color: var(--xfjy-text-secondary);
}

.profile-log-title {
  margin: var(--xfjy-space-6) 0 var(--xfjy-space-3);
  font-size: var(--xfjy-font-size-base);
  font-weight: 600;
  color: var(--xfjy-text-primary);
}
</style>
