<template>
  <div class="page-container">
    <PageHeader title="操作日志" :subtitle="`共 ${total} 条记录`" />

    <SearchBar :model="query" @search="handleSearch" @reset="handleReset">
      <el-form-item label="模块">
        <el-select v-model="query.module" placeholder="模块" clearable style="width: 140px">
          <el-option
            v-for="m in moduleOptions"
            :key="m.value"
            :label="m.label"
            :value="m.value"
          />
        </el-select>
      </el-form-item>
      <el-form-item label="操作账号">
        <el-input v-model="query.adminUsername" placeholder="操作账号" clearable style="width: 160px" @keyup.enter="handleSearch" />
      </el-form-item>
      <el-form-item label="时间范围">
        <el-date-picker
          v-model="dateRange"
          type="daterange"
          range-separator="至"
          start-placeholder="开始日期"
          end-placeholder="结束日期"
          value-format="YYYY-MM-DD"
          style="width: 260px"
        />
      </el-form-item>
      <template #extra-actions>
        <el-button-group>
          <el-button size="small" @click="setDateRange(7)">近7天</el-button>
          <el-button size="small" @click="setDateRange(30)">近30天</el-button>
          <el-button size="small" @click="setDateRange(90)">近90天</el-button>
        </el-button-group>
        <el-button type="success" :icon="Download" @click="handleExport">导出</el-button>
      </template>
    </SearchBar>

    <TableCard :skeleton="loading && !tableData.length">
      <el-table v-loading="loading" :data="tableData" border stripe>
      <el-table-column v-if="!isMobile" label="ID" prop="id" width="70" align="center" />
      <el-table-column label="操作人" width="130">
        <template #default="{ row }">
          <div class="admin-cell">
            <div class="admin-name">{{ row.adminUsername }}</div>
            <div class="admin-id">ID: {{ row.adminId }}</div>
          </div>
        </template>
      </el-table-column>
      <el-table-column label="模块" width="110" align="center">
        <template #default="{ row }">
          <el-tag size="small" type="info">{{ moduleLabel(row.module) }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="动作" width="120" align="center">
        <template #default="{ row }">
          <el-tag :type="actionTagType(row.action)" size="small">
            {{ actionLabel(row.action) }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column v-if="!isMobile" label="目标ID" prop="targetId" width="100" align="center" show-overflow-tooltip>
        <template #default="{ row }">
          {{ row.targetId || '-' }}
        </template>
      </el-table-column>
      <el-table-column label="详情" prop="detail" min-width="220" show-overflow-tooltip>
        <template #default="{ row }">
          {{ row.detail || '-' }}
        </template>
      </el-table-column>
      <el-table-column v-if="!isMobile" label="IP" prop="ip" width="130" show-overflow-tooltip>
        <template #default="{ row }">
          {{ row.ip || '-' }}
        </template>
      </el-table-column>
      <el-table-column v-if="!isMobile" label="操作时间" prop="created" width="170" />
      <el-table-column label="操作" width="80" fixed="right" align="center">
        <template #default="{ row }">
          <el-button link type="primary" size="small" @click="showDetail(row)">详情</el-button>
        </template>
      </el-table-column>
        <template #empty>
          <TableEmpty description="暂无操作日志" hint="管理员执行操作后日志会出现在这里" />
        </template>
      </el-table>
      <template #pagination>
        <el-pagination
          v-model:current-page="query.page"
          v-model:page-size="query.pageSize"
          :total="total"
          :page-sizes="[10, 20, 50]"
          layout="total, sizes, prev, pager, next, jumper"
          @size-change="handleSizeChange"
          @current-change="loadData"
        />
      </template>
    </TableCard>

    <!-- 详情弹窗 -->
    <el-dialog v-model="detailVisible" title="操作日志详情" width="600px">
      <el-descriptions :column="1" border v-if="currentLog">
        <el-descriptions-item label="ID">{{ currentLog.id }}</el-descriptions-item>
        <el-descriptions-item label="操作人">{{ currentLog.adminUsername }}（ID: {{ currentLog.adminId }}）</el-descriptions-item>
        <el-descriptions-item label="模块">
          <el-tag size="small" type="info">{{ moduleLabel(currentLog.module) }}</el-tag>
        </el-descriptions-item>
        <el-descriptions-item label="动作">
          <el-tag :type="actionTagType(currentLog.action)" size="small">{{ actionLabel(currentLog.action) }}</el-tag>
        </el-descriptions-item>
        <el-descriptions-item label="目标ID">{{ currentLog.targetId || '-' }}</el-descriptions-item>
        <el-descriptions-item label="详情">
          <pre style="margin: 0; white-space: pre-wrap; word-break: break-all;">{{ currentLog.detail || '-' }}</pre>
        </el-descriptions-item>
        <el-descriptions-item label="IP">{{ currentLog.ip || '-' }}</el-descriptions-item>
        <el-descriptions-item label="操作时间">{{ currentLog.created }}</el-descriptions-item>
      </el-descriptions>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { onMounted, ref, reactive } from 'vue'
import { ElMessage } from 'element-plus'
import { Download } from '@element-plus/icons-vue'
import { PageHeader, SearchBar, TableCard, TableEmpty } from '@/components'
import { useResponsive } from '@/composables/useResponsive'
import { getLogPageApi, getLogDetailApi, exportLogsApi } from '@/api/log'
import type { OperationLogItem } from '@/api/log'

const loading = ref(false)
const tableData = ref<OperationLogItem[]>([])
const total = ref(0)
const query = reactive({ module: '', adminUsername: '', page: 1, pageSize: 10 })
const dateRange = ref<[string, string] | null>(null)

// 响应式列可见性：移动端隐藏次要列（ID/目标ID/IP/操作时间）
const { isMobile } = useResponsive()

const moduleOptions = [
  { label: '用户管理', value: 'USER' },
  { label: '审核管理', value: 'REVIEW' },
  { label: '举报管理', value: 'REPORT' },
  { label: '评论管理', value: 'COMMENT' },
  { label: '公告管理', value: 'NOTICE' },
  { label: '协议管理', value: 'AGREEMENT' },
  { label: '支付管理', value: 'PAY_ORDER' },
  { label: '敏感词管理', value: 'SENSITIVE_WORD' },
  { label: '会员管理', value: 'VIP' },
  { label: '客服管理', value: 'KEFU' },
  { label: '动态管理', value: 'MOVEMENT' },
  { label: '视频管理', value: 'VIDEO' },
  { label: '账号管理', value: 'ACCOUNT' },
  { label: '系统管理', value: 'ADMIN' },
  { label: '个人中心', value: 'PROFILE' },
  { label: '数据看板', value: 'DASHBOARD' }
]

async function loadData() {
  loading.value = true
  try {
    const params: any = { page: query.page, pageSize: query.pageSize }
    if (query.module) params.module = query.module
    if (query.adminUsername.trim()) params.adminUsername = query.adminUsername.trim()
    if (dateRange.value && dateRange.value.length === 2) {
      params.startDate = dateRange.value[0]
      params.endDate = dateRange.value[1]
    }
    const res = await getLogPageApi(params)
    tableData.value = res.list || []
    total.value = res.total || 0
  } catch {
    // 拦截器已统一提示
  } finally {
    loading.value = false
  }
}

function handleSearch() {
  query.page = 1
  loadData()
}

function handleReset() {
  query.module = ''
  query.adminUsername = ''
  dateRange.value = null
  query.page = 1
  loadData()
}

/** 切换每页条数时回到第 1 页 */
function handleSizeChange() {
  query.page = 1
  loadData()
}

/** 设置日期快捷项 */
function setDateRange(days: number) {
  const end = new Date()
  const start = new Date()
  start.setDate(start.getDate() - days)

  const fmt = (d: Date) => {
    const y = d.getFullYear()
    const m = String(d.getMonth() + 1).padStart(2, '0')
    const day = String(d.getDate()).padStart(2, '0')
    return `${y}-${m}-${day}`
  }

  dateRange.value = [fmt(start), fmt(end)]
  handleSearch()
}

/** 导出日志 */
async function handleExport() {
  try {
    const params: { module?: string; adminUsername?: string; startDate?: string; endDate?: string } = {}
    if (query.module) params.module = query.module
    if (query.adminUsername.trim()) params.adminUsername = query.adminUsername.trim()
    if (dateRange.value && dateRange.value.length === 2) {
      params.startDate = dateRange.value[0]
      params.endDate = dateRange.value[1]
    }

    const blob = await exportLogsApi(params)
    const url = window.URL.createObjectURL(blob)
    const link = document.createElement('a')
    link.href = url
    link.download = `operation-logs-${new Date().toISOString().slice(0, 10)}.csv`
    link.click()
    window.URL.revokeObjectURL(url)
    ElMessage.success('导出成功')
  } catch {
    ElMessage.error('导出失败')
  }
}

/** 详情弹窗 */
const detailVisible = ref(false)
const currentLog = ref<OperationLogItem | null>(null)

async function showDetail(row: OperationLogItem) {
  try {
    const detail = await getLogDetailApi(row.id)
    currentLog.value = detail
    detailVisible.value = true
  } catch {
    ElMessage.error('加载详情失败')
  }
}

function moduleLabel(module: string): string {
  // 兼容历史日志里的旧枚举值
  const legacy: Record<string, string> = { PAY: 'PAY_ORDER', SENSITIVE: 'SENSITIVE_WORD' }
  const value = legacy[module] || module
  const found = moduleOptions.find((m) => m.value === value)
  return found ? found.label : module || '-'
}

function actionLabel(action: string): string {
  const map: Record<string, string> = {
    CREATE: '新增',
    UPDATE: '修改',
    DELETE: '删除',
    FREEZE: '冻结',
    UNFREEZE: '解冻',
    APPROVE: '通过',
    REJECT: '驳回',
    HANDLE: '处理',
    PUBLISH: '发布',
    RESET_PASSWORD: '重置密码',
    TOGGLE_STATUS: '切换状态',
    LOGIN: '登录',
    LOGOUT: '登出'
  }
  return map[action] || action || '-'
}

function actionTagType(action: string): 'success' | 'warning' | 'danger' | 'primary' | 'info' {
  if (action === 'DELETE') return 'danger'
  if (action === 'CREATE' || action === 'APPROVE') return 'success'
  if (action === 'REJECT' || action === 'FREEZE') return 'warning'
  if (action === 'UPDATE' || action === 'HANDLE') return 'primary'
  return 'info'
}

onMounted(() => {
  loadData()
})
</script>

<style scoped>
.admin-name {
  font-size: var(--xfjy-font-size-sm);
  color: var(--xfjy-text-primary);
  line-height: var(--xfjy-line-height);
}

.admin-id {
  font-size: var(--xfjy-font-size-xs);
  color: var(--xfjy-text-secondary);
}
</style>