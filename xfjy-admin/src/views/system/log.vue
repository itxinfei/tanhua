<template>
  <div class="page-container">
    <!-- 筛选栏 -->
    <div class="search-bar">
      <el-select
        v-model="searchModule"
        placeholder="模块"
        clearable
        style="width: 140px"
      >
        <el-option
          v-for="m in moduleOptions"
          :key="m.value"
          :label="m.label"
          :value="m.value"
        />
      </el-select>
      <el-input
        v-model="searchAdmin"
        placeholder="操作账号"
        clearable
        style="width: 160px"
        @keyup.enter="handleSearch"
      />
      <el-date-picker
        v-model="dateRange"
        type="daterange"
        range-separator="至"
        start-placeholder="开始日期"
        end-placeholder="结束日期"
        value-format="YYYY-MM-DD"
        style="width: 260px"
      />
      <el-button type="primary" @click="handleSearch">查询</el-button>
      <el-button @click="handleReset">重置</el-button>
    </div>

    <!-- 表格 -->
    <el-table v-loading="loading" :data="tableData" border stripe empty-text="暂无操作日志">
      <el-table-column label="ID" prop="id" width="70" align="center" />
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
      <el-table-column label="目标ID" prop="targetId" width="100" align="center" show-overflow-tooltip>
        <template #default="{ row }">
          {{ row.targetId || '-' }}
        </template>
      </el-table-column>
      <el-table-column label="详情" prop="detail" min-width="220" show-overflow-tooltip>
        <template #default="{ row }">
          {{ row.detail || '-' }}
        </template>
      </el-table-column>
      <el-table-column label="IP" prop="ip" width="130" show-overflow-tooltip>
        <template #default="{ row }">
          {{ row.ip || '-' }}
        </template>
      </el-table-column>
      <el-table-column label="操作时间" prop="created" width="170" />
    </el-table>

    <!-- 分页 -->
    <div class="pagination-bar">
      <el-pagination
        v-model:current-page="page"
        v-model:page-size="pageSize"
        :total="total"
        :page-sizes="[10, 20, 50]"
        layout="total, sizes, prev, pager, next, jumper"
        @size-change="handleSizeChange"
        @current-change="loadData"
      />
    </div>
  </div>
</template>

<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { getLogPageApi } from '@/api/log'
import type { OperationLogItem } from '@/api/log'

const loading = ref(false)
const tableData = ref<OperationLogItem[]>([])
const total = ref(0)
const page = ref(1)
const pageSize = ref(10)
const searchModule = ref('')
const searchAdmin = ref('')
const dateRange = ref<[string, string] | null>(null)

const moduleOptions = [
  { label: '用户管理', value: 'USER' },
  { label: '审核管理', value: 'REVIEW' },
  { label: '举报管理', value: 'REPORT' },
  { label: '公告管理', value: 'NOTICE' },
  { label: '支付管理', value: 'PAY' },
  { label: '敏感词管理', value: 'SENSITIVE' },
  { label: '会员管理', value: 'VIP' },
  { label: '客服管理', value: 'KEFU' },
  { label: '动态管理', value: 'MOVEMENT' },
  { label: '视频管理', value: 'VIDEO' },
  { label: '账号管理', value: 'ACCOUNT' },
  { label: '个人中心', value: 'PROFILE' },
  { label: '数据看板', value: 'DASHBOARD' }
]

async function loadData() {
  loading.value = true
  try {
    const params: any = { page: page.value, pageSize: pageSize.value }
    if (searchModule.value) params.module = searchModule.value
    if (searchAdmin.value.trim()) params.adminUsername = searchAdmin.value.trim()
    if (dateRange.value && dateRange.value.length === 2) {
      params.startDate = dateRange.value[0]
      params.endDate = dateRange.value[1]
    }
    const res = await getLogPageApi(params)
    tableData.value = res.list || []
    total.value = res.total || 0
  } catch {
    // 拦截器已提示
  } finally {
    loading.value = false
  }
}

function handleSearch() {
  page.value = 1
  loadData()
}

function handleReset() {
  searchModule.value = ''
  searchAdmin.value = ''
  dateRange.value = null
  page.value = 1
  loadData()
}

/** 切换每页条数时回到第 1 页 */
function handleSizeChange() {
  page.value = 1
  loadData()
}

function moduleLabel(module: string): string {
  const found = moduleOptions.find((m) => m.value === module)
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
.search-bar {
  display: flex;
  gap: 12px;
  margin-bottom: 16px;
  flex-wrap: wrap;
}

.admin-name {
  font-size: 13px;
  color: #303133;
  line-height: 1.4;
}

.admin-id {
  font-size: 12px;
  color: #909399;
}

.pagination-bar {
  display: flex;
  justify-content: flex-end;
  margin-top: 16px;
}
</style>