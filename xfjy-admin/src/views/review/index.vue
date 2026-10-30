<template>
  <div class="page-container review-workbench">
    <!-- 搜索栏 -->
    <div class="search-bar">
      <el-form :inline="true" :model="query" @submit.prevent>
        <el-form-item label="类型">
          <el-select v-model="query.type" placeholder="全部" clearable style="width: 120px">
            <el-option label="动态" value="POST" />
            <el-option label="图片" value="PHOTO" />
            <el-option label="视频" value="VIDEO" />
          </el-select>
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="query.status" placeholder="全部" clearable style="width: 120px">
            <el-option label="待审核" value="PENDING" />
            <el-option label="已通过" value="APPROVED" />
            <el-option label="已驳回" value="REJECTED" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" :icon="Search" @click="handleSearch">查询</el-button>
          <el-button :icon="Refresh" @click="handleReset">重置</el-button>
        </el-form-item>
      </el-form>
    </div>

    <!-- 快捷键提示条 -->
    <div class="shortcut-bar">
      <el-icon class="shortcut-icon"><InfoFilled /></el-icon>
      <span><kbd>A</kbd> 通过当前项</span>
      <span><kbd>R</kbd> 驳回当前项</span>
      <span><kbd>←</kbd><kbd>→</kbd>（或 <kbd>J</kbd><kbd>K</kbd>）切换上一条 / 下一条</span>
      <span class="shortcut-tip">处理后自动跳转下一条待审内容</span>
    </div>

    <!-- 工作台：左队列 + 右详情 -->
    <div class="workbench">
      <!-- 左侧：待审核队列 -->
      <div class="queue-pane">
        <div class="queue-toolbar">
          <el-checkbox
            :model-value="allPendingSelected"
            :indeterminate="pendingIndeterminate"
            @change="toggleSelectAllPending"
          >
            本页全选待审
          </el-checkbox>
          <span class="queue-count">待审 {{ pendingRows.length }} 项</span>
          <el-button
            size="small"
            type="success"
            :disabled="selectedIds.length === 0"
            @click="handleBatchApprove"
          >
            批量通过{{ selectedIds.length ? `(${selectedIds.length})` : '' }}
          </el-button>
          <el-button
            size="small"
            type="danger"
            :disabled="selectedIds.length === 0"
            @click="handleBatchReject"
          >
            批量驳回{{ selectedIds.length ? `(${selectedIds.length})` : '' }}
          </el-button>
        </div>

        <div v-loading="loading" class="queue-list">
          <div
            v-for="row in tableData"
            :key="row.id"
            :data-id="row.id"
            class="queue-item"
            :class="{ active: row.id === currentId, processed: row.status !== 'PENDING' }"
            @click="selectItem(row)"
          >
            <el-checkbox
              :model-value="selectedIds.includes(row.id)"
              :disabled="row.status !== 'PENDING'"
              class="queue-checkbox"
              @click.stop
              @change="(val: CheckboxValue) => toggleRow(row, val)"
            />
            <div class="queue-item-body">
              <div class="queue-item-top">
                <el-tag :type="typeColor(row.type)" size="small">{{ typeLabel(row.type) }}</el-tag>
                <span class="queue-user">{{ row.userNickname }}</span>
                <span class="queue-id">#{{ row.id }}</span>
                <span class="queue-time">{{ formatShortTime(row.submittedAt) }}</span>
              </div>
              <div class="queue-content">{{ row.content || mediaSummary(row) }}</div>
              <div v-if="row.status !== 'PENDING'" class="queue-result">
                <el-tag :type="statusColor(row.status)" size="small">{{ statusLabel(row.status) }}</el-tag>
                <span v-if="row.reviewer" class="text-muted">审核人：{{ row.reviewer }}</span>
                <span v-if="row.reviewedAt" class="text-muted">{{ row.reviewedAt }}</span>
              </div>
            </div>
          </div>

          <!-- 空态：区分「队列已清空」「无筛选结果」「加载失败」 -->
          <div v-if="!loading && tableData.length === 0" class="queue-empty">
            <el-empty v-if="loadFailed" description="加载失败">
              <el-button type="primary" size="small" @click="loadData">点击重试</el-button>
            </el-empty>
            <el-empty v-else :description="query.status === 'PENDING' ? '队列已清空，太棒了' : '暂无符合条件的内容'" />
          </div>
        </div>

        <div class="pagination-wrapper">
          <el-pagination
            v-model:current-page="query.page"
            v-model:page-size="query.pageSize"
            :total="total"
            :page-sizes="[10, 20, 50]"
            size="small"
            layout="total, sizes, prev, pager, next"
            @size-change="handleSizeChange"
            @current-change="loadData"
          />
        </div>
      </div>

      <!-- 右侧：详情面板 -->
      <div class="detail-pane">
        <template v-if="currentItem">
          <div class="detail-header">
            <el-avatar :size="44" :src="currentItem.userAvatar">
              <el-icon><UserFilled /></el-icon>
            </el-avatar>
            <div class="detail-user">
              <div class="detail-name">{{ currentItem.userNickname }}</div>
              <div class="text-muted">用户 ID：{{ currentItem.userId }} · 内容 #{{ currentItem.id }}</div>
            </div>
            <el-tag :type="typeColor(currentItem.type)">{{ typeLabel(currentItem.type) }}</el-tag>
            <span class="detail-time">{{ currentItem.submittedAt }}</span>
          </div>

          <div class="detail-body">
            <section class="detail-section">
              <h4>内容</h4>
              <p class="detail-content">{{ currentItem.content || '（无文字内容）' }}</p>
            </section>

            <section v-if="currentItem.medias?.length" class="detail-section">
              <h4>媒体（{{ currentItem.medias.length }}）</h4>
              <video
                v-if="currentItem.type === 'VIDEO'"
                controls
                :src="currentItem.medias[0]"
                style="max-width: 100%; max-height: 320px; border-radius: 8px; background: #000"
              />
              <!-- 非视频：横向滚动多图，每张图都带完整预览列表，点击可在大图查看器内左右切换 -->
              <div v-else class="media-row">
                <el-image
                  v-for="(url, index) in currentItem.medias"
                  :key="index"
                  :src="url"
                  :preview-src-list="currentItem.medias"
                  :initial-index="index"
                  fit="contain"
                  preview-teleported
                  class="media-img"
                />
              </div>
            </section>

            <!-- 已处理：灰色只读区 -->
            <section v-if="currentItem.status !== 'PENDING'" class="detail-section review-result">
              <h4>审核结果</h4>
              <el-tag :type="statusColor(currentItem.status)" size="small">{{ statusLabel(currentItem.status) }}</el-tag>
              <p v-if="currentItem.reviewer" class="text-muted">审核人：{{ currentItem.reviewer }}</p>
              <p v-if="currentItem.reviewedAt" class="text-muted">审核时间：{{ currentItem.reviewedAt }}</p>
              <p v-if="currentItem.rejectReason" class="text-muted">驳回原因：{{ currentItem.rejectReason }}</p>
            </section>
          </div>

          <div class="detail-footer">
            <template v-if="currentItem.status === 'PENDING'">
              <el-button type="success" :loading="submitting" @click="handleApprove(currentItem)">
                通过（A）
              </el-button>
              <el-button type="danger" :disabled="submitting" @click="handleReject(currentItem)">
                驳回（R）
              </el-button>
            </template>
            <el-tag v-else :type="statusColor(currentItem.status)" size="large">
              已处理 · {{ statusLabel(currentItem.status) }}
            </el-tag>
          </div>
        </template>

        <div v-else-if="!loading" class="detail-empty">
          <el-empty
            :description="loadFailed ? '加载失败，请在左侧重试' : query.status === 'PENDING' ? '队列已清空' : '请选择左侧内容查看详情'"
          />
        </div>
      </div>
    </div>

    <!-- 驳回弹窗（单条 / 批量复用） -->
    <el-dialog v-model="rejectDialogVisible" :title="batchMode ? `批量驳回（${selectedIds.length} 项）` : '驳回内容'" width="440px">
      <el-form label-width="80px" @submit.prevent>
        <el-form-item label="驳回原因">
          <el-select v-model="rejectReason" placeholder="选择或输入驳回原因" filterable allow-create style="width: 100%">
            <el-option label="含违规图片" value="含违规图片" />
            <el-option label="含不当言论" value="含不当言论" />
            <el-option label="涉嫌广告推广" value="涉嫌广告推广" />
            <el-option label="内容低俗" value="内容低俗" />
            <el-option label="其他" value="其他" />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="rejectDialogVisible = false">取消</el-button>
        <el-button type="danger" @click="confirmReject">确认驳回</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, computed, onMounted, onUnmounted, watch, nextTick } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Search, Refresh } from '@element-plus/icons-vue'
import {
  getReviewPageApi,
  approveReviewApi,
  rejectReviewApi,
  batchApproveApi,
  batchRejectApi,
} from '@/api/review'
import type { ReviewItem, ReviewQuery } from '@/api/review'
import { formatShortTime } from '@/utils/format'

const loading = ref(false)
const loadFailed = ref(false)
const submitting = ref(false)
const tableData = ref<ReviewItem[]>([])
const total = ref(0)
const selectedIds = ref<number[]>([])
const currentId = ref<number | null>(null)

const query = reactive<ReviewQuery>({
  page: 1,
  pageSize: 10,
  type: '',
  status: 'PENDING',
})

const currentItem = computed<ReviewItem | null>(
  () => tableData.value.find((item) => item.id === currentId.value) ?? null,
)

const pendingRows = computed(() => tableData.value.filter((item) => item.status === 'PENDING'))
const allPendingSelected = computed(
  () => pendingRows.value.length > 0 && pendingRows.value.every((item) => selectedIds.value.includes(item.id)),
)
const pendingIndeterminate = computed(
  () => selectedIds.value.length > 0 && !allPendingSelected.value,
)

async function loadData() {
  loading.value = true
  loadFailed.value = false
  try {
    const res = await getReviewPageApi(query)
    tableData.value = res.list
    total.value = res.total
    selectedIds.value = []
    // 翻页 / 重新查询后默认落在本页第一条，保证键盘流不断档
    currentId.value = res.list[0]?.id ?? null
  } catch {
    // 拦截器已统一提示错误；这里只置空，不造假数据
    tableData.value = []
    total.value = 0
    selectedIds.value = []
    currentId.value = null
    loadFailed.value = true
  } finally {
    loading.value = false
  }
}

function handleSearch() {
  query.page = 1
  loadData()
}

function handleReset() {
  query.type = ''
  query.status = 'PENDING'
  query.page = 1
  loadData()
}

/** 切换每页条数时回到第 1 页 */
function handleSizeChange() {
  query.page = 1
  loadData()
}

function selectItem(row: ReviewItem) {
  currentId.value = row.id
}

// 切换选中项后，保证其在左侧滚动区域内可见
watch(currentId, (id) => {
  if (id === null) return
  nextTick(() => {
    document
      .querySelector(`.queue-item[data-id="${id}"]`)
      ?.scrollIntoView({ block: 'nearest' })
  })
})

/**
 * 单条处理完后的队列推进逻辑：
 * PENDING 视图下本地移除已处理项（不整列表刷新、不跳回第 1 页）；
 * 本页清空但服务端仍有后续页时自动续页；否则清空选中，进入「队列已清空」空态。
 */
async function advanceAfterProcess(processedId: number) {
  const idx = tableData.value.findIndex((item) => item.id === processedId)
  const hasMorePages = query.page < Math.max(1, Math.ceil(total.value / query.pageSize))

  if (query.status === 'PENDING' && idx > -1) {
    tableData.value.splice(idx, 1)
    total.value = Math.max(0, total.value - 1)
    selectedIds.value = selectedIds.value.filter((id) => id !== processedId)
  }

  if (tableData.value.length > 0) {
    const nextIdx = idx < 0 ? 0 : Math.min(idx, tableData.value.length - 1)
    currentId.value = tableData.value[nextIdx].id
  } else if (hasMorePages) {
    query.page += 1
    await loadData()
  } else {
    currentId.value = null
  }
}

function stepSelect(delta: -1 | 1) {
  const list = tableData.value
  if (list.length === 0) return
  const idx = list.findIndex((item) => item.id === currentId.value)
  const nextIdx =
    idx === -1 ? (delta > 0 ? 0 : list.length - 1) : Math.min(Math.max(idx + delta, 0), list.length - 1)
  currentId.value = list[nextIdx].id
}

// 单条通过：高频操作不弹确认框，成功后轻提示并自动跳下一条
async function handleApprove(row: ReviewItem | null) {
  if (!row || submitting.value || row.status !== 'PENDING') return
  submitting.value = true
  try {
    await approveReviewApi(row.id)
    ElMessage.success('已通过')
    await advanceAfterProcess(row.id)
  } catch {
    // 错误已由拦截器统一提示，保留在当前项允许重试
  } finally {
    submitting.value = false
  }
}

// 驳回
const rejectDialogVisible = ref(false)
const rejectReason = ref('')
const batchMode = ref(false)
const rejectTarget = ref<ReviewItem | null>(null)

function handleReject(row: ReviewItem | null) {
  if (!row || submitting.value || row.status !== 'PENDING') return
  batchMode.value = false
  rejectTarget.value = row
  rejectReason.value = ''
  rejectDialogVisible.value = true
}

function handleBatchReject() {
  if (selectedIds.value.length === 0) return
  batchMode.value = true
  rejectTarget.value = null
  rejectReason.value = ''
  rejectDialogVisible.value = true
}

async function confirmReject() {
  const reason = rejectReason.value.trim()
  if (!reason) {
    ElMessage.warning('请选择或输入驳回原因')
    return
  }
  try {
    if (batchMode.value) {
      const ids = [...selectedIds.value]
      await batchRejectApi(ids, reason)
      ElMessage.success(`已批量驳回 ${ids.length} 项`)
      rejectDialogVisible.value = false
      await loadData()
    } else {
      const target = rejectTarget.value
      if (!target) return
      await rejectReviewApi(target.id, reason)
      ElMessage.success('已驳回')
      rejectDialogVisible.value = false
      await advanceAfterProcess(target.id)
    }
  } catch {
    // 错误已由拦截器统一提示，弹窗保留以便重试
  }
}

// 批量通过：低频批量操作保留二次确认
async function handleBatchApprove() {
  if (selectedIds.value.length === 0) return
  const ids = [...selectedIds.value]
  try {
    await ElMessageBox.confirm(`确定批量通过 ${ids.length} 项内容吗？`, '提示', { type: 'success' })
    await batchApproveApi(ids)
    ElMessage.success(`已批量通过 ${ids.length} 项`)
    await loadData()
  } catch {
    // 用户取消或请求失败（失败已由拦截器提示）
  }
}

// ---- 本页勾选（仅 PENDING 可勾选） ----
type CheckboxValue = boolean | string | number

function toggleRow(row: ReviewItem, value: CheckboxValue) {
  const idSet = new Set(selectedIds.value)
  if (value) idSet.add(row.id)
  else idSet.delete(row.id)
  selectedIds.value = [...idSet]
}

function toggleSelectAllPending(value: CheckboxValue) {
  selectedIds.value = value ? pendingRows.value.map((item) => item.id) : []
}

// ---- 展示辅助 ----
function typeLabel(type: string) {
  const map: Record<string, string> = { POST: '动态', PHOTO: '图片', VIDEO: '视频' }
  return map[type] ?? type
}

function typeColor(type: ReviewItem['type']): 'primary' | 'success' | 'warning' {
  const map = { POST: 'primary', PHOTO: 'success', VIDEO: 'warning' } as const
  return map[type]
}

function statusLabel(status: string) {
  const map: Record<string, string> = { PENDING: '待审核', APPROVED: '已通过', REJECTED: '已驳回' }
  return map[status] ?? status
}

function statusColor(status: ReviewItem['status']): 'warning' | 'success' | 'danger' {
  const map = { PENDING: 'warning', APPROVED: 'success', REJECTED: 'danger' } as const
  return map[status]
}

function mediaSummary(row: ReviewItem) {
  return row.medias?.length ? `[${typeLabel(row.type)} · ${row.medias.length} 个媒体文件]` : '无文字内容'
}

// ---- 键盘快捷键：A 通过 / R 驳回 / ←→（J/K）切换 ----
function isEditableTarget(target: EventTarget | null): boolean {
  if (!(target instanceof HTMLElement)) return false
  const tag = target.tagName
  return tag === 'INPUT' || tag === 'TEXTAREA' || tag === 'SELECT' || target.isContentEditable
}

function onKeydown(event: KeyboardEvent) {
  // 驳回弹窗打开时不抢键，避免原因输入被拦截
  if (rejectDialogVisible.value || isEditableTarget(event.target)) return

  const key = event.key
  const item = currentItem.value
  if (item && (key === 'a' || key === 'A') && item.status === 'PENDING') {
    event.preventDefault()
    handleApprove(item)
  } else if (item && (key === 'r' || key === 'R') && item.status === 'PENDING') {
    event.preventDefault()
    handleReject(item)
  } else if (key === 'ArrowLeft' || key === 'j' || key === 'J') {
    event.preventDefault()
    stepSelect(-1)
  } else if (key === 'ArrowRight' || key === 'k' || key === 'K') {
    event.preventDefault()
    stepSelect(1)
  }
}

onMounted(() => {
  loadData()
  window.addEventListener('keydown', onKeydown)
})

onUnmounted(() => {
  window.removeEventListener('keydown', onKeydown)
})
</script>

<style scoped>
/* 工作台占满布局剩余高度（顶部导航 56px），内部左右两栏独立滚动 */
.review-workbench {
  height: calc(100vh - 56px);
  box-sizing: border-box;
  display: flex;
  flex-direction: column;
}

.shortcut-bar {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 16px;
  margin-bottom: 12px;
  padding: 8px 16px;
  background: #ecf5ff;
  border: 1px solid #d9ecff;
  border-radius: 8px;
  color: #409eff;
  font-size: 13px;
}

.shortcut-icon {
  font-size: 15px;
}

.shortcut-tip {
  color: #909399;
}

kbd {
  display: inline-block;
  padding: 1px 6px;
  margin: 0 2px;
  background: #fff;
  border: 1px solid #dcdfe6;
  border-bottom-width: 2px;
  border-radius: 4px;
  color: #303133;
  font-family: inherit;
  font-size: 12px;
  line-height: 1.6;
}

.workbench {
  flex: 1;
  min-height: 0;
  display: flex;
  gap: 12px;
}

/* 左侧队列 */
.queue-pane {
  width: 400px;
  flex-shrink: 0;
  display: flex;
  flex-direction: column;
  background: #fff;
  border-radius: 8px;
  border: 1px solid #ebeef5;
  overflow: hidden;
}

.queue-toolbar {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 10px 12px;
  border-bottom: 1px solid #ebeef5;
}

.queue-count {
  color: #909399;
  font-size: 12px;
  margin-right: auto;
}

.queue-list {
  flex: 1;
  min-height: 0;
  overflow-y: auto;
}

.queue-empty {
  padding: 40px 0;
}

.queue-item {
  display: flex;
  gap: 8px;
  padding: 10px 12px;
  cursor: pointer;
  border-bottom: 1px solid #f2f6fc;
  border-left: 3px solid transparent;
  transition: background-color 0.15s;
}

.queue-item:hover {
  background: #f5f7fa;
}

.queue-item.active {
  background: #ecf5ff;
  border-left-color: #409eff;
}

.queue-item.processed {
  opacity: 0.75;
}

.queue-checkbox {
  margin-top: 2px;
}

.queue-item-body {
  flex: 1;
  min-width: 0;
}

.queue-item-top {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 4px;
}

.queue-user {
  color: #303133;
  font-size: 13px;
  font-weight: 500;
  max-width: 120px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.queue-id {
  color: #c0c4cc;
  font-size: 12px;
}

.queue-time {
  margin-left: auto;
  color: #c0c4cc;
  font-size: 12px;
  flex-shrink: 0;
}

.queue-content {
  color: #606266;
  font-size: 13px;
  line-height: 1.5;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}

.queue-result {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 8px;
  margin-top: 6px;
}

.pagination-wrapper {
  padding: 8px;
  border-top: 1px solid #ebeef5;
  display: flex;
  justify-content: center;
}

/* 右侧详情 */
.detail-pane {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  background: #fff;
  border-radius: 8px;
  border: 1px solid #ebeef5;
  overflow: hidden;
}

.detail-header {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 16px 20px;
  border-bottom: 1px solid #ebeef5;
}

.detail-user {
  flex: 1;
  min-width: 0;
}

.detail-name {
  color: #303133;
  font-size: 15px;
  font-weight: 600;
  margin-bottom: 2px;
}

.detail-time {
  color: #909399;
  font-size: 12px;
  flex-shrink: 0;
}

.detail-body {
  flex: 1;
  min-height: 0;
  overflow-y: auto;
  padding: 20px;
}

.detail-section {
  margin-bottom: 24px;
}

.detail-section h4 {
  margin: 0 0 10px;
  color: #303133;
  font-size: 14px;
}

.detail-content {
  margin: 0;
  color: #303133;
  font-size: 14px;
  line-height: 1.8;
  white-space: pre-wrap;
  word-break: break-word;
}

.media-row {
  display: flex;
  gap: 10px;
  overflow-x: auto;
  padding-bottom: 6px;
}

.media-img {
  width: 200px;
  height: 200px;
  flex-shrink: 0;
  border-radius: 8px;
  background: #f5f7fa;
}

.review-result {
  padding: 14px 16px;
  background: #f5f7fa;
  border-radius: 8px;
}

.review-result h4 {
  margin-top: 0;
}

.review-result p {
  margin: 8px 0 0;
  font-size: 13px;
  line-height: 1.6;
}

.detail-footer {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 14px 20px;
  border-top: 1px solid #ebeef5;
}

.detail-footer .el-button {
  min-width: 110px;
}

.detail-empty {
  flex: 1;
  display: flex;
  align-items: center;
  justify-content: center;
}

.text-muted {
  color: #909399;
  font-size: 12px;
}
</style>
