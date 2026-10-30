<template>
  <div class="page-container">
    <!-- 搜索栏 -->
    <div class="search-bar">
      <el-input
        v-model="searchKeyword"
        placeholder="搜索评论内容"
        clearable
        style="width: 240px"
        @keyup.enter="handleSearch"
      />
      <el-input
        v-model="searchUserId"
        placeholder="用户ID"
        clearable
        style="width: 140px"
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
        @change="handleDateChange"
      />
      <el-button type="primary" @click="handleSearch">查询</el-button>
      <el-button @click="handleReset">重置</el-button>
    </div>

    <!-- 批量操作栏 -->
    <div class="batch-bar" v-if="selectedRows.length > 0">
      <span class="batch-count">已选 {{ selectedRows.length }} 项</span>
      <el-button v-perm="['SUPER_ADMIN','ADMIN']" type="danger" size="small" @click="handleBatchDelete">批量删除</el-button>
      <el-button size="small" @click="selectedRows = []">取消选择</el-button>
    </div>

    <!-- 表格 -->
    <el-table
      v-loading="loading"
      :data="tableData"
      border
      stripe
      :row-class-name="rowClassName"
      empty-text="暂无评论数据"
      @selection-change="handleSelectionChange"
    >
      <el-table-column type="selection" width="50" align="center" />
      <el-table-column label="评论人" width="160">
        <template #default="{ row }">
          <div class="user-cell">
            <el-avatar :size="32" :src="row.userAvatar">
              <el-icon><UserFilled /></el-icon>
            </el-avatar>
            <div class="user-text">
              <div class="user-name">{{ row.userNickname }}</div>
              <div class="user-id">ID: {{ row.userId }}</div>
            </div>
          </div>
        </template>
      </el-table-column>
      <el-table-column label="评论内容" min-width="300">
        <template #default="{ row }">
          <div class="text-content">{{ row.content || '(空)' }}</div>
        </template>
      </el-table-column>
      <el-table-column label="所属动态ID" width="220" show-overflow-tooltip>
        <template #default="{ row }">
          <span class="publish-id">{{ row.publishId || '-' }}</span>
        </template>
      </el-table-column>
      <el-table-column label="发布时间" width="170">
        <template #default="{ row }">
          {{ formatDateTime(row.created) }}
        </template>
      </el-table-column>
      <el-table-column label="操作" width="140" fixed="right" align="center">
        <template #default="{ row }">
          <el-button type="primary" link size="small" @click="handleDetail(row)">详情</el-button>
          <el-button v-perm="['SUPER_ADMIN','ADMIN']" type="danger" link size="small" @click="handleDelete(row)">删除</el-button>
        </template>
      </el-table-column>
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

    <!-- 评论详情弹窗 -->
    <el-dialog v-model="detailVisible" title="评论详情" width="560px" destroy-on-close>
      <template v-if="detailRow">
        <!-- 评论人 -->
        <div class="detail-author">
          <el-avatar :size="44" :src="detailRow.userAvatar">
            <el-icon><UserFilled /></el-icon>
          </el-avatar>
          <div class="detail-author-info">
            <div class="detail-author-name">{{ detailRow.userNickname }}</div>
            <div class="detail-author-id">ID: {{ detailRow.userId }} · {{ formatDateTime(detailRow.created) }}</div>
          </div>
        </div>

        <!-- 评论内容 -->
        <div class="detail-content">{{ detailRow.content || '(空)' }}</div>

        <!-- 所属动态 -->
        <el-descriptions :column="1" border class="detail-desc">
          <el-descriptions-item label="所属动态ID">{{ detailRow.publishId || '-' }}</el-descriptions-item>
        </el-descriptions>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { getCommentPageApi, deleteCommentApi } from '@/api/comment'
import type { CommentItem } from '@/api/comment'
import request from '@/api/request'
import { formatDateTime } from '@/utils/format'

const route = useRoute()
const loading = ref(false)
const tableData = ref<CommentItem[]>([])
const total = ref(0)
const page = ref(1)
const pageSize = ref(10)
const searchKeyword = ref('')
const searchUserId = ref('')
/** 举报跳转携带的目标评论ID，用于本页高亮定位 */
const highlightId = ref<string | null>(null)
const detailVisible = ref(false)
const detailRow = ref<CommentItem | null>(null)
const selectedRows = ref<CommentItem[]>([])
const dateRange = ref<[string, string] | null>(null)

async function loadData() {
  loading.value = true
  try {
    const params: any = { page: page.value, pageSize: pageSize.value }
    if (searchKeyword.value.trim()) params.keyword = searchKeyword.value.trim()
    const uid = Number(searchUserId.value.trim())
    if (searchUserId.value.trim() && !Number.isNaN(uid)) params.userId = uid
    if (dateRange.value) {
      params.startDate = dateRange.value[0]
      params.endDate = dateRange.value[1]
    }
    const res = await getCommentPageApi(params)
    tableData.value = res.list || []
    total.value = res.total || 0
  } catch {
    // 拦截器已提示
  } finally {
    loading.value = false
  }
}

function handleDateChange(val: [string, string] | null) {
  if (!val) dateRange.value = null
  loadData()
}

function handleSelectionChange(rows: CommentItem[]) {
  selectedRows.value = rows
}

async function handleBatchDelete() {
  const ids = selectedRows.value.map(r => r.id)
  try {
    await ElMessageBox.confirm(
      `确定要删除选中的 ${ids.length} 条评论吗？删除后不可恢复。`,
      '批量删除确认',
      { confirmButtonText: '确定删除', cancelButtonText: '取消', type: 'warning' }
    )
    const res: any = await request.delete('/admin/comments/batch', { data: ids })
    const { success, failed } = res
    ElMessage.success(`批量删除完成：成功 ${success}，失败 ${failed}`)
    selectedRows.value = []
    loadData()
  } catch {
    // 取消
  }
}

function handleSearch() {
  page.value = 1
  loadData()
}

function handleReset() {
  searchKeyword.value = ''
  searchUserId.value = ''
  page.value = 1
  loadData()
}

/** 切换每页条数时回到第 1 页 */
function handleSizeChange() {
  page.value = 1
  loadData()
}

/** 举报跳转定位行高亮 */
function rowClassName({ row }: { row: CommentItem }): string {
  return highlightId.value !== null && row.id === highlightId.value ? 'highlight-row' : ''
}

function handleDetail(row: CommentItem) {
  detailRow.value = row
  detailVisible.value = true
}

async function handleDelete(row: CommentItem) {
  try {
    await ElMessageBox.confirm(
      `确定要删除该评论吗？删除后不可恢复。`,
      '删除确认',
      { confirmButtonText: '确定删除', cancelButtonText: '取消', type: 'warning' }
    )
    await deleteCommentApi(row.id)
    ElMessage.success('评论已删除')
    loadData()
  } catch {
    // 取消
  }
}

onMounted(() => {
  // 举报跳转联动：读取 targetId 高亮定位目标评论
  const targetId = Number(route.query.targetId)
  if (route.query.targetId && !Number.isNaN(targetId)) {
    highlightId.value = String(targetId)
    ElMessage.info(`来自举报跳转，正在定位目标评论 #${targetId}（仅高亮当前页可见行）`)
  }
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

.batch-bar {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 8px 16px;
  margin-bottom: 12px;
  background: #f5f7fa;
  border-radius: 6px;
}

.batch-count {
  font-size: 13px;
  color: #606266;
  margin-right: auto;
}

.user-cell {
  display: flex;
  align-items: center;
  gap: 8px;
}

.user-name {
  font-size: 13px;
  color: #303133;
  line-height: 1.4;
}

.user-id {
  font-size: 12px;
  color: #909399;
}

.text-content {
  font-size: 13px;
  color: #606266;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}

.publish-id {
  font-size: 12px;
  color: #909399;
  font-family: monospace;
}

.pagination-bar {
  display: flex;
  justify-content: flex-end;
  margin-top: 16px;
}

/* 举报跳转定位行高亮 */
:deep(.el-table .highlight-row) {
  background-color: #ecf5ff !important;
}

/* 评论详情弹窗 */
.detail-author {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 16px;
}

.detail-author-name {
  font-size: 15px;
  font-weight: 600;
  color: #303133;
}

.detail-author-id {
  font-size: 12px;
  color: #909399;
  margin-top: 2px;
}

.detail-content {
  font-size: 14px;
  color: #303133;
  line-height: 1.7;
  margin-bottom: 16px;
  white-space: pre-wrap;
  padding: 12px 16px;
  background: #f5f7fa;
  border-radius: 6px;
}

.detail-desc {
  margin-top: 4px;
}
</style>