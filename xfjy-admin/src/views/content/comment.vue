<template>
  <div class="page-container">
    <PageHeader title="评论管理" :subtitle="`共 ${total} 条记录`" />

    <SearchBar :model="query" @search="handleSearch" @reset="handleReset">
      <el-form-item label="关键词">
        <el-input v-model="searchKeyword" placeholder="搜索评论内容" clearable style="width: 200px" @keyup.enter="handleSearch" />
      </el-form-item>
      <el-form-item label="用户ID">
        <el-input v-model="searchUserId" placeholder="用户ID" clearable style="width: 120px" @keyup.enter="handleSearch" />
      </el-form-item>
      <el-form-item label="发布时间">
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
      </el-form-item>
    </SearchBar>

    <TableCard>
      <template #batch v-if="selectedRows.length > 0">
        <span class="batch-count">已选 {{ selectedRows.length }} 项</span>
        <el-button v-perm="['SUPER_ADMIN','ADMIN']" type="danger" size="small" @click="handleBatchDelete">批量删除</el-button>
        <el-button size="small" @click="selectedRows = []">取消选择</el-button>
      </template>
      <el-table
        v-loading="loading"
        :data="tableData"
        border
        stripe
        :row-class-name="rowClassName"
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
        <el-table-column v-if="!isMobile" label="所属动态ID" width="220" show-overflow-tooltip>
          <template #default="{ row }">
            <span class="publish-id">{{ row.publishId || '-' }}</span>
          </template>
        </el-table-column>
        <el-table-column v-if="!isMobile" label="发布时间" width="170">
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
        <template #empty>
          <TableEmpty description="暂无评论数据" />
        </template>
      </el-table>
      <template #pagination>
        <el-pagination
          v-model:current-page="page"
          v-model:page-size="pageSize"
          :total="total"
          :page-sizes="[10, 20, 50]"
          layout="total, sizes, prev, pager, next, jumper"
          @size-change="handleSizeChange"
          @current-change="loadData"
        />
      </template>
    </TableCard>

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
import { PageHeader, SearchBar, TableCard, TableEmpty } from '@/components'
import { useResponsive } from '@/composables/useResponsive'
import { getCommentPageApi, deleteCommentApi } from '@/api/comment'
import type { CommentItem } from '@/api/comment'
import request from '@/api/request'
import { formatDateTime } from '@/utils/format'

const route = useRoute()
// 响应式列可见性：移动端隐藏次要列（所属动态ID/发布时间）
const { isMobile } = useResponsive()
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

const query = {
  keyword: '',
  userId: '',
  startDate: '',
  endDate: '',
}

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
  } catch (err) {
    ElMessage.error('数据加载失败，请检查后端服务')
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
  dateRange.value = null
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
.batch-count {
  font-size: var(--xfjy-font-size-sm);
  color: var(--xfjy-text-regular);
  margin-right: auto;
}

.user-cell {
  display: flex;
  align-items: center;
  gap: var(--xfjy-space-2);
}

.user-name {
  font-size: var(--xfjy-font-size-sm);
  color: var(--xfjy-text-primary);
  line-height: 1.4;
}

.user-id {
  font-size: var(--xfjy-font-size-xs);
  color: var(--xfjy-text-secondary);
}

.text-content {
  font-size: var(--xfjy-font-size-sm);
  color: var(--xfjy-text-regular);
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}

.publish-id {
  font-size: var(--xfjy-font-size-xs);
  color: var(--xfjy-text-secondary);
  font-family: monospace;
}

/* 举报跳转定位行高亮：!important 覆盖 EP el-table 行 hover 背景优先级 */
:deep(.el-table .highlight-row) {
  background-color: var(--xfjy-primary-50) !important;
}

/* 评论详情弹窗 */
.detail-author {
  display: flex;
  align-items: center;
  gap: var(--xfjy-space-3);
  margin-bottom: var(--xfjy-space-4);
}

.detail-author-name {
  font-size: 15px;
  font-weight: 600;
  color: var(--xfjy-text-primary);
}

.detail-author-id {
  font-size: var(--xfjy-font-size-xs);
  color: var(--xfjy-text-secondary);
  margin-top: 2px;
}

.detail-content {
  font-size: var(--xfjy-font-size-base);
  color: var(--xfjy-text-primary);
  line-height: 1.7;
  margin-bottom: var(--xfjy-space-4);
  white-space: pre-wrap;
  padding: var(--xfjy-space-3) var(--xfjy-space-4);
  background: var(--xfjy-bg-hover);
  border-radius: var(--xfjy-radius-md);
}

.detail-desc {
  margin-top: var(--xfjy-space-1);
}
</style>
