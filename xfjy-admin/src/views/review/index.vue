<template>
  <div class="page-container">
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

    <!-- 批量操作栏 -->
    <div class="batch-bar" v-if="selectedIds.length > 0">
      <span>已选择 {{ selectedIds.length }} 项</span>
      <el-button type="success" size="small" @click="handleBatchApprove">批量通过</el-button>
      <el-button type="danger" size="small" @click="handleBatchReject">批量驳回</el-button>
    </div>

    <!-- 表格 -->
    <div class="table-container">
      <el-table
        :data="tableData"
        v-loading="loading"
        stripe
        border
        style="width: 100%"
        @selection-change="handleSelectionChange"
      >
        <el-table-column type="selection" width="50" align="center" :selectable="(row: ReviewItem) => row.status === 'PENDING'" />
        <el-table-column prop="id" label="ID" width="70" align="center" />
        <el-table-column label="类型" width="80" align="center">
          <template #default="{ row }">
            <el-tag :type="typeColor(row.type)" size="small">{{ typeLabel(row.type) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="发布者" width="140">
          <template #default="{ row }">
            <div class="user-cell">
              <el-avatar :size="28" :src="row.userAvatar">
                <el-icon><UserFilled /></el-icon>
              </el-avatar>
              <span>{{ row.userNickname }}</span>
            </div>
          </template>
        </el-table-column>
        <el-table-column prop="content" label="内容" min-width="200" show-overflow-tooltip />
        <el-table-column label="媒体" width="100" align="center">
          <template #default="{ row }">
            <el-image
              v-if="row.medias?.length"
              :src="row.medias[0]"
              fit="cover"
              style="width: 60px; height: 60px; border-radius: 4px"
              :preview-src-list="row.medias"
              preview-teleported
            />
            <span v-else class="text-muted">无</span>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="90" align="center">
          <template #default="{ row }">
            <el-tag :type="statusColor(row.status)" size="small">{{ statusLabel(row.status) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="submittedAt" label="提交时间" width="170" />
        <el-table-column label="操作" width="160" fixed="right" align="center">
          <template #default="{ row }">
            <template v-if="row.status === 'PENDING'">
              <el-button link type="success" size="small" @click="handleApprove(row)">通过</el-button>
              <el-button link type="danger" size="small" @click="handleReject(row)">驳回</el-button>
            </template>
            <span v-else class="text-muted">已处理</span>
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

    <!-- 驳回弹窗 -->
    <el-dialog v-model="rejectDialogVisible" :title="batchMode ? '批量驳回' : '驳回内容'" width="440px">
      <el-form label-width="80px">
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
import { ref, reactive, onMounted } from 'vue'
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

const loading = ref(false)
const tableData = ref<ReviewItem[]>([])
const total = ref(0)
const selectedIds = ref<number[]>([])

const query = reactive<ReviewQuery>({
  page: 1,
  pageSize: 10,
  type: '',
  status: 'PENDING',
})

// Mock 数据
function getMockReviews(page: number, pageSize: number): { total: number; list: ReviewItem[] } {
  const contents = [
    '今天天气真好，出去走走~',
    '新拍的照片分享给大家',
    '周末去爬山了，风景超美',
    '刚学会做蛋糕，第一次尝试',
    '推荐一部好看的电影《星际穿越》',
    '今天心情不太好，想找人聊聊',
    '健身打卡第30天，坚持就是胜利',
    '猫咪日常：又在键盘上睡觉了',
    '周末聚餐，朋友们好久不见',
    '旅行vlog：大理三日游',
  ]
  const nicknames = ['小鹿乱撞', '星辰大海', '柠檬精', '温柔的风', '夜空守望', '甜甜圈', '流浪猫', '月光宝盒']
  const types: ReviewItem['type'][] = ['POST', 'PHOTO', 'VIDEO']
  const statuses: ReviewItem['status'][] = ['PENDING', 'APPROVED', 'REJECTED']
  const list: ReviewItem[] = []
  const start = (page - 1) * pageSize
  for (let i = 0; i < pageSize; i++) {
    const idx = start + i
    if (idx >= 50) break
    list.push({
      id: 20001 + idx,
      type: types[idx % 3],
      userId: 10001 + idx,
      userNickname: nicknames[idx % nicknames.length],
      userAvatar: '',
      content: contents[idx % contents.length],
      medias: [],
      status: (query.status as ReviewItem['status']) || statuses[idx % 3],
      submittedAt: `2026-10-${String((idx % 22) + 1).padStart(2, '0')} ${String(idx % 24).padStart(2, '0')}:30:00`,
    })
  }
  return { total: 50, list }
}

async function loadData() {
  loading.value = true
  try {
    const res = await getReviewPageApi(query)
    tableData.value = res.list
    total.value = res.total
  } catch {
    // Mock 兜底仅开发环境生效，生产环境显示空列表避免假数据误导
    if (import.meta.env.DEV) {
      const mock = getMockReviews(query.page, query.pageSize)
      tableData.value = mock.list
      total.value = mock.total
    } else {
      tableData.value = []
      total.value = 0
    }
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

function handleSelectionChange(rows: ReviewItem[]) {
  selectedIds.value = rows.map((r) => r.id)
}

function typeLabel(type: string) {
  const map: Record<string, string> = { POST: '动态', PHOTO: '图片', VIDEO: '视频' }
  return map[type] || type
}

function typeColor(type: string) {
  const map: Record<string, string> = { POST: 'primary', PHOTO: 'success', VIDEO: 'warning' }
  return map[type] || 'info'
}

function statusLabel(status: string) {
  const map: Record<string, string> = { PENDING: '待审核', APPROVED: '已通过', REJECTED: '已驳回' }
  return map[status] || status
}

function statusColor(status: string) {
  const map: Record<string, string> = { PENDING: 'warning', APPROVED: 'success', REJECTED: 'danger' }
  return map[status] || 'info'
}

// 通过
async function handleApprove(row: ReviewItem) {
  try {
    await ElMessageBox.confirm('确定通过该内容审核吗？', '提示', { type: 'success' })
    await approveReviewApi(row.id)
    ElMessage.success('已通过')
    row.status = 'APPROVED'
  } catch {
    // 取消
  }
}

// 驳回
const rejectDialogVisible = ref(false)
const rejectReason = ref('')
const batchMode = ref(false)
const rejectTarget = ref<ReviewItem | null>(null)

function handleReject(row: ReviewItem) {
  batchMode.value = false
  rejectTarget.value = row
  rejectReason.value = ''
  rejectDialogVisible.value = true
}

function handleBatchReject() {
  batchMode.value = true
  rejectTarget.value = null
  rejectReason.value = ''
  rejectDialogVisible.value = true
}

async function confirmReject() {
  if (!rejectReason.value) {
    ElMessage.warning('请选择或输入驳回原因')
    return
  }
  try {
    if (batchMode.value) {
      await batchRejectApi(selectedIds.value, rejectReason.value)
      ElMessage.success(`已批量驳回 ${selectedIds.value.length} 项`)
      loadData()
    } else {
      if (!rejectTarget.value) return
      await rejectReviewApi(rejectTarget.value.id, rejectReason.value)
      ElMessage.success('已驳回')
      rejectTarget.value.status = 'REJECTED'
    }
    rejectDialogVisible.value = false
  } catch {
    // 错误已由拦截器处理
  }
}

// 批量通过
async function handleBatchApprove() {
  try {
    await ElMessageBox.confirm(`确定批量通过 ${selectedIds.value.length} 项内容吗？`, '提示', { type: 'success' })
    await batchApproveApi(selectedIds.value)
    ElMessage.success(`已批量通过 ${selectedIds.value.length} 项`)
    loadData()
  } catch {
    // 取消
  }
}

onMounted(() => {
  loadData()
})
</script>

<style scoped>
.batch-bar {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 10px 20px;
  background: #ecf5ff;
  border-radius: 4px;
  margin-bottom: 12px;
  color: #409eff;
  font-size: 13px;
}

.user-cell {
  display: flex;
  align-items: center;
  gap: 8px;
}

.text-muted {
  color: #c0c4cc;
  font-size: 12px;
}
</style>