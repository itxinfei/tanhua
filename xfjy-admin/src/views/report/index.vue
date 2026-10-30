<template>
  <div class="page-container">
    <!-- 搜索栏 -->
    <div class="search-bar">
      <el-form :inline="true" :model="query" @submit.prevent>
        <el-form-item label="状态">
          <el-select v-model="query.status" placeholder="全部" clearable style="width: 130px">
            <el-option label="待处理" value="PENDING" />
            <el-option label="处理中" value="PROCESSING" />
            <el-option label="已处理" value="RESOLVED" />
            <el-option label="已驳回" value="DISMISSED" />
          </el-select>
        </el-form-item>
        <el-form-item label="举报类型">
          <el-select v-model="query.targetType" placeholder="全部" clearable style="width: 120px">
            <el-option label="用户" value="USER" />
            <el-option label="动态" value="POST" />
            <el-option label="评论" value="COMMENT" />
            <el-option label="私信" value="MESSAGE" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" :icon="Search" @click="handleSearch">查询</el-button>
          <el-button :icon="Refresh" @click="handleReset">重置</el-button>
        </el-form-item>
      </el-form>
    </div>

    <!-- 表格 -->
    <div class="table-container">
      <!-- 批量处理栏：仅当选中行时可见 -->
      <div v-if="selectedIds.length > 0" class="batch-bar">
        <span class="batch-count">已选 {{ selectedIds.length }} 项</span>
        <el-button type="danger" size="small" @click="openBatchHandle('RESOLVED')">批量处理</el-button>
        <el-button size="small" @click="openBatchHandle('DISMISSED')">批量驳回</el-button>
        <el-button link size="small" @click="clearSelection">清空选择</el-button>
      </div>

      <el-table
        ref="tableRef"
        :data="tableData"
        v-loading="loading"
        stripe
        border
        style="width: 100%"
        row-key="id"
        empty-text="暂无举报数据"
        @selection-change="handleSelectionChange"
      >
        <el-table-column type="selection" width="44" align="center" :selectable="isSelectable" reserve-selection />
        <el-table-column prop="id" label="ID" width="70" align="center" />
        <el-table-column label="举报人" width="120">
          <template #default="{ row }">
            <div class="user-cell">
              <el-avatar :size="24" :src="row.reporterAvatar">
                <el-icon><UserFilled /></el-icon>
              </el-avatar>
              <span>{{ row.reporterNickname }}</span>
            </div>
          </template>
        </el-table-column>
        <el-table-column label="被举报对象" width="160">
          <template #default="{ row }">
            <el-tag :type="targetTypeColor(row.targetType)" size="small">{{ targetTypeLabel(row.targetType) }}</el-tag>
            <span style="margin-left: 6px">{{ row.targetUserNickname }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="reason" label="举报原因" width="120" show-overflow-tooltip />
        <el-table-column prop="description" label="详细描述" min-width="200" show-overflow-tooltip />
        <el-table-column label="状态" width="90" align="center">
          <template #default="{ row }">
            <el-tag :type="statusColor(row.status)" size="small">{{ statusLabel(row.status) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="处理人" width="100" align="center">
          <template #default="{ row }">
            <span v-if="row.handler">{{ row.handler }}</span>
            <span v-else class="text-muted">—</span>
          </template>
        </el-table-column>
        <el-table-column prop="createdAt" label="举报时间" width="170" />
        <el-table-column label="操作" width="170" fixed="right" align="center">
          <template #default="{ row }">
            <el-button link type="primary" size="small" @click="showDetail(row)">详情</el-button>
            <template v-if="row.status === 'PENDING' || row.status === 'PROCESSING'">
              <el-button link type="danger" size="small" @click="openHandle(row, 'RESOLVED')">确认违规</el-button>
              <el-button link type="warning" size="small" @click="openHandle(row, 'DISMISSED')">驳回</el-button>
            </template>
          </template>
        </el-table-column>
      </el-table>

      <div class="pagination-wrapper">
        <el-pagination
          v-model:current-page="query.page"
          v-model:page-size="query.pageSize"
          :total="total"
          :page-sizes="[10, 20, 50]"
          layout="total, sizes, prev, pager, next, jumper"
          @size-change="handleSizeChange"
          @current-change="loadData"
        />
      </div>
    </div>

    <!-- 详情抽屉：右侧滑出，展示举报人/被举报对象/证据图片 -->
    <el-drawer v-model="detailVisible" title="举报详情" direction="rtl" size="480px">
      <div v-loading="detailLoading" class="detail-body">
        <template v-if="currentReport">
          <!-- 举报人信息 -->
          <div class="detail-section">
            <div class="detail-section-title">举报人</div>
            <div class="reporter-row">
              <el-avatar :size="40" :src="currentReport.reporterAvatar">
                <el-icon><UserFilled /></el-icon>
              </el-avatar>
              <div class="reporter-meta">
                <div class="reporter-name">{{ currentReport.reporterNickname }}</div>
                <div class="reporter-id">ID: {{ currentReport.reporterId }}</div>
              </div>
            </div>
          </div>

          <!-- 被举报对象：可点击跳转对应详情 -->
          <div class="detail-section">
            <div class="detail-section-title">被举报对象</div>
            <el-descriptions :column="1" border size="small">
              <el-descriptions-item label="对象类型">
                <el-tag :type="targetTypeColor(currentReport.targetType)" size="small">
                  {{ targetTypeLabel(currentReport.targetType) }}
                </el-tag>
              </el-descriptions-item>
              <el-descriptions-item label="目标用户">
                {{ currentReport.targetUserNickname }}（ID: {{ currentReport.targetUserId }}）
              </el-descriptions-item>
              <el-descriptions-item label="目标ID">{{ currentReport.targetId }}</el-descriptions-item>
            </el-descriptions>
            <el-button
              class="jump-btn"
              type="primary"
              link
              size="small"
              @click="jumpToTarget(currentReport)"
            >
              查看被举报内容本体
            </el-button>
          </div>

          <!-- 举报原因/描述 -->
          <div class="detail-section">
            <div class="detail-section-title">举报内容</div>
            <div class="detail-field"><span class="field-label">原因：</span>{{ currentReport.reason }}</div>
            <div class="detail-field"><span class="field-label">描述：</span>{{ currentReport.description || '无' }}</div>
            <div class="detail-field">
              <span class="field-label">状态：</span>
              <el-tag :type="statusColor(currentReport.status)" size="small">{{ statusLabel(currentReport.status) }}</el-tag>
            </div>
            <div class="detail-field"><span class="field-label">举报时间：</span>{{ currentReport.createdAt }}</div>
          </div>

          <!-- 证据图片组：缩略图 + 点击大图预览，多图横向滚动 -->
          <div class="detail-section">
            <div class="detail-section-title">证据图片</div>
            <div v-if="currentReport.evidence && currentReport.evidence.length > 0" class="evidence-scroll">
              <el-image
                v-for="(url, idx) in currentReport.evidence"
                :key="idx"
                class="evidence-thumb"
                :src="url"
                :preview-src-list="currentReport.evidence"
                :initial-index="idx"
                fit="cover"
                preview-teleported
                hide-on-click-modal
              />
            </div>
            <el-empty v-else description="无证据图片" :image-size="60" />
          </div>

          <!-- 处理回显：已处理时展示 handler/handledAt/handleResult -->
          <div v-if="currentReport.status === 'RESOLVED' || currentReport.status === 'DISMISSED'" class="detail-section">
            <div class="detail-section-title">处理信息</div>
            <div class="detail-field"><span class="field-label">处理人：</span>{{ currentReport.handler || '—' }}</div>
            <div class="detail-field"><span class="field-label">处理时间：</span>{{ currentReport.handledAt || '—' }}</div>
            <div class="detail-field">
              <span class="field-label">处理结果：</span>
              <el-tag :type="currentReport.status === 'RESOLVED' ? 'success' : 'info'" size="small">
                {{ statusLabel(currentReport.status) }}
              </el-tag>
              <span v-if="currentReport.handleResult" class="handle-result-text">{{ currentReport.handleResult }}</span>
            </div>
          </div>
        </template>
      </div>
    </el-drawer>

    <!-- 处理弹窗：备注必填，RESOLVED 提示封禁目标用户 -->
    <el-dialog v-model="handleDialogVisible" :title="handleDialogTitle" width="480px">
      <el-form ref="handleFormRef" :model="handleForm" :rules="handleRules" label-width="80px">
        <el-form-item label="处理方式" prop="result">
          <el-radio-group v-model="handleForm.result">
            <el-radio value="RESOLVED">确认违规（封禁对方）</el-radio>
            <el-radio value="DISMISSED">驳回举报（无违规）</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item v-if="handleForm.result === 'RESOLVED'" class="handle-tip">
          <el-alert type="error" :closable="false" show-icon>
            确认违规后，系统将对被举报目标用户执行封禁操作。
          </el-alert>
        </el-form-item>
        <el-form-item label="处理备注" prop="note">
          <el-input
            v-model="handleForm.note"
            type="textarea"
            :rows="3"
            placeholder="请输入处理备注（必填）"
            maxlength="200"
            show-word-limit
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="handleDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="handleSubmitting" @click="confirmHandle">确认处理</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, computed, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, type FormInstance, type FormRules } from 'element-plus'
import { Search, Refresh, UserFilled } from '@element-plus/icons-vue'
import {
  getReportPageApi,
  getReportDetailApi,
  handleReportApi,
  batchHandleReportApi,
} from '@/api/report'
import type { ReportItem, ReportQuery } from '@/api/report'

const router = useRouter()

const loading = ref(false)
const tableData = ref<ReportItem[]>([])
const total = ref(0)

const query = reactive<ReportQuery>({
  page: 1,
  pageSize: 10,
  status: 'PENDING',
  targetType: '',
})

// 仅 PENDING / PROCESSING 可勾选（可处理状态）
function isSelectable(row: ReportItem): boolean {
  return row.status === 'PENDING' || row.status === 'PROCESSING'
}

// 批量选择
const tableRef = ref()
const selectedRows = ref<ReportItem[]>([])
const selectedIds = computed(() => selectedRows.value.map((r) => r.id))

function handleSelectionChange(rows: ReportItem[]) {
  selectedRows.value = rows
}

function clearSelection() {
  tableRef.value?.clearSelection()
}

async function loadData() {
  loading.value = true
  try {
    const res = await getReportPageApi(query)
    tableData.value = res.list
    total.value = res.total
  } catch {
    // 接口失败只置空列表，不造假数据兜底
    tableData.value = []
    total.value = 0
  } finally {
    loading.value = false
  }
}

function handleSearch() {
  query.page = 1
  clearSelection()
  loadData()
}

function handleReset() {
  query.status = 'PENDING'
  query.targetType = ''
  query.page = 1
  clearSelection()
  loadData()
}

/** 切换每页条数时回到第 1 页 */
function handleSizeChange() {
  query.page = 1
  loadData()
}

function targetTypeLabel(type: string): string {
  const map: Record<string, string> = { USER: '用户', POST: '动态', COMMENT: '评论', MESSAGE: '私信' }
  return map[type] || type
}

// targetType 标签配色：USER 突出用 danger 其余按类型区分
function targetTypeColor(type: string): string {
  const map: Record<string, string> = { USER: 'danger', POST: 'primary', COMMENT: 'warning', MESSAGE: 'info' }
  return map[type] || 'info'
}

function statusLabel(status: string): string {
  const map: Record<string, string> = { PENDING: '待处理', PROCESSING: '处理中', RESOLVED: '已处理', DISMISSED: '已驳回' }
  return map[status] || status
}

// 状态 tag 配色：红色仅用于「确认违规」动作按钮，PENDING 改为 warning 不再用 danger
function statusColor(status: string): string {
  const map: Record<string, string> = {
    PENDING: 'warning',
    PROCESSING: 'primary',
    RESOLVED: 'success',
    DISMISSED: 'info',
  }
  return map[status] || 'info'
}

// 被举报内容本体跳转：POST/COMMENT → 内容管理对应页，USER → 用户列表页打开详情抽屉
function jumpToTarget(report: ReportItem) {
  switch (report.targetType) {
    case 'USER':
      router.push({ path: '/user/list', query: { detailUserId: String(report.targetUserId) } })
      break
    case 'POST':
      ElMessage.info(`即将查看动态管理，目标动态ID：${report.targetId}`)
      router.push({ path: '/content/movement', query: { targetId: String(report.targetId) } })
      break
    case 'COMMENT':
      ElMessage.info(`即将查看评论管理，目标评论ID：${report.targetId}`)
      router.push({ path: '/content/comment', query: { targetId: String(report.targetId) } })
      break
    case 'MESSAGE':
      ElMessage.info('私信内容请前往客服工作台核实')
      router.push('/service/workbench')
      break
    default:
      ElMessage.warning('未知举报对象类型，无法跳转')
  }
}

// 详情抽屉：点击「详情」调 getReportDetailApi 拿最新数据
const detailVisible = ref(false)
const detailLoading = ref(false)
const currentReport = ref<ReportItem | null>(null)

async function showDetail(row: ReportItem) {
  detailVisible.value = true
  detailLoading.value = true
  currentReport.value = null
  try {
    currentReport.value = await getReportDetailApi(row.id)
  } catch {
    // 接口失败回退到行数据，避免抽屉空白
    currentReport.value = row
    ElMessage.warning('详情接口获取失败，已展示列表数据')
  } finally {
    detailLoading.value = false
  }
}

// 处理弹窗：单条与批量共用
const handleDialogVisible = ref(false)
const handleSubmitting = ref(false)
const handleFormRef = ref<FormInstance>()
const handleMode = ref<'single' | 'batch'>('single')
const handleTarget = ref<ReportItem | null>(null)

const handleForm = reactive<{ result: 'RESOLVED' | 'DISMISSED'; note: string }>({
  result: 'RESOLVED',
  note: '',
})

const handleRules: FormRules = {
  result: [{ required: true, message: '请选择处理方式', trigger: 'change' }],
  // 备注必填：非空且非纯空格
  note: [
    { required: true, message: '请输入处理备注', trigger: 'blur' },
    {
      validator: (_rule, value: string, callback) => {
        if (!value || !value.trim()) {
          callback(new Error('处理备注不能为空'))
        } else {
          callback()
        }
      },
      trigger: 'blur',
    },
  ],
}

const handleDialogTitle = computed(() => {
  const action = handleForm.result === 'RESOLVED' ? '确认违规' : '驳回举报'
  return handleMode.value === 'batch' ? `批量${action}（${selectedIds.value.length} 项）` : action
})

// 单条处理入口：result 预设
function openHandle(row: ReportItem, result: 'RESOLVED' | 'DISMISSED') {
  handleMode.value = 'single'
  handleTarget.value = row
  handleForm.result = result
  handleForm.note = ''
  handleDialogVisible.value = true
}

// 批量处理入口
function openBatchHandle(result: 'RESOLVED' | 'DISMISSED') {
  if (selectedIds.value.length === 0) {
    ElMessage.warning('请先勾选待处理的举报')
    return
  }
  handleMode.value = 'batch'
  handleTarget.value = null
  handleForm.result = result
  handleForm.note = ''
  handleDialogVisible.value = true
}

async function confirmHandle() {
  if (!handleFormRef.value) return
  await handleFormRef.value.validate(async (valid) => {
    if (!valid) return
    handleSubmitting.value = true
    try {
      if (handleMode.value === 'batch') {
        await batchHandleReportApi(selectedIds.value, handleForm.result, handleForm.note.trim())
        ElMessage.success(`批量${handleForm.result === 'RESOLVED' ? '处理' : '驳回'}成功，共 ${selectedIds.value.length} 项`)
      } else {
        if (!handleTarget.value) return
        await handleReportApi(handleTarget.value.id, handleForm.result, handleForm.note.trim())
        ElMessage.success(handleForm.result === 'RESOLVED' ? '已处理，目标用户已封禁' : '举报已驳回')
      }
      handleDialogVisible.value = false
      clearSelection()
      loadData()
    } catch {
      // 错误已由拦截器处理
    } finally {
      handleSubmitting.value = false
    }
  })
}

onMounted(() => {
  loadData()
})
</script>

<style scoped>
.user-cell {
  display: flex;
  align-items: center;
  gap: 6px;
}

.text-muted {
  color: var(--el-text-color-placeholder);
}

/* 批量处理栏 */
.batch-bar {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-bottom: 10px;
  padding: 8px 12px;
  background: var(--el-color-warning-light-9);
  border: 1px solid var(--el-color-warning-light-7);
  border-radius: 8px;
}

.batch-count {
  font-size: 13px;
  color: var(--el-color-warning-dark-2);
  margin-right: auto;
}

/* 详情抽屉 */
.detail-body {
  padding: 0 4px;
}

.detail-section {
  margin-bottom: 22px;
}

.detail-section-title {
  font-size: 14px;
  font-weight: 600;
  color: var(--el-text-color-primary);
  margin-bottom: 10px;
  padding-left: 8px;
  border-left: 3px solid #409eff;
  line-height: 1.2;
}

.reporter-row {
  display: flex;
  align-items: center;
  gap: 12px;
}

.reporter-meta {
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.reporter-name {
  font-size: 14px;
  font-weight: 500;
  color: var(--el-text-color-primary);
}

.reporter-id {
  font-size: 12px;
  color: var(--el-text-color-secondary);
}

.jump-btn {
  margin-top: 8px;
}

.detail-field {
  font-size: 13px;
  line-height: 1.8;
  color: var(--el-text-color-regular);
  word-break: break-all;
}

.field-label {
  color: var(--el-text-color-secondary);
}

.handle-result-text {
  margin-left: 8px;
}

/* 证据图片：横向滚动缩略图 */
.evidence-scroll {
  display: flex;
  gap: 8px;
  overflow-x: auto;
  padding-bottom: 6px;
}

.evidence-thumb {
  flex: 0 0 auto;
  width: 96px;
  height: 96px;
  border-radius: 8px;
  border: 1px solid var(--el-border-color-light);
  cursor: pointer;
}

.handle-tip {
  margin-bottom: 0;
}
</style>
