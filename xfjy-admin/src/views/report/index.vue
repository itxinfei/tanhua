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
      <el-table :data="tableData" v-loading="loading" stripe border style="width: 100%">
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
        <el-table-column prop="createdAt" label="举报时间" width="170" />
        <el-table-column label="操作" width="160" fixed="right" align="center">
          <template #default="{ row }">
            <el-button link type="primary" size="small" @click="showDetail(row)">详情</el-button>
            <template v-if="row.status === 'PENDING'">
              <el-button link type="success" size="small" @click="handleResolve(row)">处理</el-button>
              <el-button link type="warning" size="small" @click="handleDismiss(row)">驳回</el-button>
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
          @size-change="loadData"
          @current-change="loadData"
        />
      </div>
    </div>

    <!-- 详情弹窗 -->
    <el-dialog v-model="detailVisible" title="举报详情" width="560px">
      <el-descriptions :column="2" border v-if="currentReport">
        <el-descriptions-item label="举报ID">{{ currentReport.id }}</el-descriptions-item>
        <el-descriptions-item label="举报类型">{{ targetTypeLabel(currentReport.targetType) }}</el-descriptions-item>
        <el-descriptions-item label="举报人">{{ currentReport.reporterNickname }}</el-descriptions-item>
        <el-descriptions-item label="被举报用户">{{ currentReport.targetUserNickname }}</el-descriptions-item>
        <el-descriptions-item label="举报原因" :span="2">{{ currentReport.reason }}</el-descriptions-item>
        <el-descriptions-item label="详细描述" :span="2">{{ currentReport.description }}</el-descriptions-item>
        <el-descriptions-item label="举报时间" :span="2">{{ currentReport.createdAt }}</el-descriptions-item>
        <el-descriptions-item label="状态" :span="2">
          <el-tag :type="statusColor(currentReport.status)" size="small">{{ statusLabel(currentReport.status) }}</el-tag>
        </el-descriptions-item>
        <el-descriptions-item v-if="currentReport.handleResult" label="处理结果" :span="2">
          {{ currentReport.handleResult }}
        </el-descriptions-item>
      </el-descriptions>
    </el-dialog>

    <!-- 处理弹窗 -->
    <el-dialog v-model="handleDialogVisible" title="处理举报" width="480px">
      <el-form label-width="80px">
        <el-form-item label="处理方式">
          <el-radio-group v-model="handleResult">
            <el-radio value="RESOLVED">确认违规（封禁对方）</el-radio>
            <el-radio value="DISMISSED">驳回举报（无违规）</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="处理备注">
          <el-input v-model="handleNote" type="textarea" :rows="3" placeholder="请输入处理备注" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="handleDialogVisible = false">取消</el-button>
        <el-button type="primary" @click="confirmHandle">确认处理</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { Search, Refresh } from '@element-plus/icons-vue'
import { getReportPageApi, handleReportApi } from '@/api/report'
import type { ReportItem, ReportQuery } from '@/api/report'

const loading = ref(false)
const tableData = ref<ReportItem[]>([])
const total = ref(0)

const query = reactive<ReportQuery>({
  page: 1,
  pageSize: 10,
  status: 'PENDING',
  targetType: '',
})

// Mock 数据
function getMockReports(page: number, pageSize: number): { total: number; list: ReportItem[] } {
  const reasons = ['骚扰行为', '虚假信息', '不当言论', '诈骗嫌疑', '违规内容', '冒充他人']
  const descriptions = [
    '该用户频繁发送不当信息，已多次警告无效',
    '资料照片与本人严重不符，涉嫌使用网络图片',
    '在评论区发布人身攻击言论',
    '以恋爱为名要求转账，涉嫌诈骗',
    '发布低俗内容，影响平台氛围',
    '冒充他人身份进行社交',
  ]
  const nicknames = ['小鹿乱撞', '星辰大海', '柠檬精', '温柔的风', '夜空守望', '甜甜圈', '流浪猫', '月光宝盒']
  const targetTypes: ReportItem['targetType'][] = ['USER', 'POST', 'COMMENT', 'MESSAGE']
  const statuses: ReportItem['status'][] = ['PENDING', 'RESOLVED', 'DISMISSED']
  const list: ReportItem[] = []
  const start = (page - 1) * pageSize
  for (let i = 0; i < pageSize; i++) {
    const idx = start + i
    if (idx >= 30) break
    list.push({
      id: 30001 + idx,
      reporterId: 10001 + idx,
      reporterNickname: nicknames[idx % nicknames.length],
      reporterAvatar: '',
      targetUserId: 10001 + (idx + 3) % 20,
      targetUserNickname: nicknames[(idx + 3) % nicknames.length],
      targetType: targetTypes[idx % 4],
      targetId: 20001 + idx,
      reason: reasons[idx % reasons.length],
      description: descriptions[idx % descriptions.length],
      evidence: [],
      status: (query.status as ReportItem['status']) || statuses[idx % 3],
      createdAt: `2026-10-${String((idx % 22) + 1).padStart(2, '0')} ${String(idx % 24).padStart(2, '0')}:00:00`,
    })
  }
  return { total: 30, list }
}

async function loadData() {
  loading.value = true
  try {
    const res = await getReportPageApi(query)
    tableData.value = res.list
    total.value = res.total
  } catch {
    const mock = getMockReports(query.page, query.pageSize)
    tableData.value = mock.list
    total.value = mock.total
  } finally {
    loading.value = false
  }
}

function handleSearch() {
  query.page = 1
  loadData()
}

function handleReset() {
  query.status = 'PENDING'
  query.targetType = ''
  query.page = 1
  loadData()
}

function targetTypeLabel(type: string) {
  const map: Record<string, string> = { USER: '用户', POST: '动态', COMMENT: '评论', MESSAGE: '私信' }
  return map[type] || type
}

function targetTypeColor(type: string) {
  const map: Record<string, string> = { USER: 'danger', POST: 'primary', COMMENT: 'warning', MESSAGE: 'info' }
  return map[type] || 'info'
}

function statusLabel(status: string) {
  const map: Record<string, string> = { PENDING: '待处理', PROCESSING: '处理中', RESOLVED: '已处理', DISMISSED: '已驳回' }
  return map[status] || status
}

function statusColor(status: string) {
  const map: Record<string, string> = { PENDING: 'danger', PROCESSING: 'warning', RESOLVED: 'success', DISMISSED: 'info' }
  return map[status] || 'info'
}

// 详情
const detailVisible = ref(false)
const currentReport = ref<ReportItem | null>(null)

function showDetail(row: ReportItem) {
  currentReport.value = row
  detailVisible.value = true
}

// 处理
const handleDialogVisible = ref(false)
const handleResult = ref<'RESOLVED' | 'DISMISSED'>('RESOLVED')
const handleNote = ref('')
const handleTarget = ref<ReportItem | null>(null)

function handleResolve(row: ReportItem) {
  handleTarget.value = row
  handleResult.value = 'RESOLVED'
  handleNote.value = ''
  handleDialogVisible.value = true
}

function handleDismiss(row: ReportItem) {
  handleTarget.value = row
  handleResult.value = 'DISMISSED'
  handleNote.value = ''
  handleDialogVisible.value = true
}

async function confirmHandle() {
  if (!handleTarget.value) return
  try {
    await handleReportApi(handleTarget.value.id, handleResult.value, handleNote.value)
    ElMessage.success(handleResult.value === 'RESOLVED' ? '已处理，目标用户已封禁' : '举报已驳回')
    handleTarget.value.status = handleResult.value
    handleDialogVisible.value = false
  } catch {
    // 错误已由拦截器处理
  }
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
</style>