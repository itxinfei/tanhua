<template>
  <div class="page-container">
    <PageHeader title="动态管理" :subtitle="`共 ${total} 条记录`" />

    <SearchBar :model="query" @search="handleSearch" @reset="handleReset">
      <el-form-item label="关键词">
        <el-input v-model="searchKeyword" placeholder="搜索动态文字内容" clearable style="width: 200px" @keyup.enter="handleSearch" />
      </el-form-item>
      <el-form-item label="用户ID">
        <el-input v-model="searchUserId" placeholder="用户ID" clearable style="width: 120px" @keyup.enter="handleSearch" />
      </el-form-item>
      <el-form-item label="可见范围">
        <el-select v-model="searchSeeType" placeholder="全部" clearable style="width: 120px">
          <el-option label="公开" :value="0" />
          <el-option label="好友" :value="1" />
          <el-option label="私密" :value="2" />
        </el-select>
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
        empty-text="暂无动态数据"
        @selection-change="handleSelectionChange"
      >
        <el-table-column type="selection" width="50" align="center" />
        <el-table-column label="发布者" width="160">
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
        <el-table-column label="内容" min-width="260">
          <template #default="{ row }">
            <div class="text-content">{{ row.text || '(无文字)' }}</div>
          </template>
        </el-table-column>
        <el-table-column label="图片" width="180">
          <template #default="{ row }">
            <div v-if="row.medias && row.medias.length" class="media-list">
              <el-image
                v-for="(url, idx) in row.medias.slice(0, 3)"
                :key="idx"
                :src="url"
                :preview-src-list="row.medias"
                :initial-index="idx"
                fit="cover"
                class="media-thumb"
                preview-teleported
              />
              <span v-if="row.medias.length > 3" class="media-more">+{{ row.medias.length - 3 }}</span>
            </div>
            <span v-else class="no-media">无图片</span>
          </template>
        </el-table-column>
        <el-table-column v-if="!isMobile" label="位置" width="120" show-overflow-tooltip>
          <template #default="{ row }">
            {{ row.locationName || '-' }}
          </template>
        </el-table-column>
        <el-table-column label="可见范围" width="90" align="center">
          <template #default="{ row }">
            <StatusTag :status="String(row.seeType)" :mapping="seeTypeMapping" />
          </template>
        </el-table-column>
        <el-table-column v-if="!isMobile" label="互动" width="140" align="center">
          <template #default="{ row }">
            <span class="stat-text">赞 {{ row.likeCount }}</span>
            <span class="stat-text">评 {{ row.commentCount }}</span>
            <span class="stat-text">喜 {{ row.loveCount }}</span>
          </template>
        </el-table-column>
        <el-table-column v-if="!isMobile" label="举报" width="90" align="center">
          <template #default="{ row }">
            <el-badge :value="row.reportCount" :hidden="row.reportCount === 0" type="danger">
              <span class="stat-text">举报</span>
            </el-badge>
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

    <!-- 动态详情弹窗 -->
    <el-dialog v-model="detailVisible" title="动态详情" width="600px" destroy-on-close>
      <template v-if="detailRow">
        <!-- 发布者 -->
        <div class="detail-author">
          <el-avatar :size="44" :src="detailRow.userAvatar">
            <el-icon><UserFilled /></el-icon>
          </el-avatar>
          <div class="detail-author-info">
            <div class="detail-author-name">{{ detailRow.userNickname }}</div>
            <div class="detail-author-id">ID: {{ detailRow.userId }} · {{ formatDateTime(detailRow.created) }}</div>
          </div>
        </div>

        <!-- 文字内容 -->
        <div v-if="detailRow.text" class="detail-text">{{ detailRow.text }}</div>

        <!-- 图片列表 -->
        <div v-if="detailRow.medias && detailRow.medias.length" class="detail-medias">
          <el-image
            v-for="(url, idx) in detailRow.medias"
            :key="idx"
            :src="url"
            :preview-src-list="detailRow.medias"
            :initial-index="idx"
            fit="cover"
            class="detail-media-item"
            preview-teleported
          />
        </div>

        <!-- 位置 + 互动 -->
        <el-descriptions :column="2" border class="detail-desc">
          <el-descriptions-item label="位置">{{ detailRow.locationName || '-' }}</el-descriptions-item>
          <el-descriptions-item label="互动">
            赞 {{ detailRow.likeCount }} · 评 {{ detailRow.commentCount }} · 喜 {{ detailRow.loveCount }}
          </el-descriptions-item>
        </el-descriptions>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { PageHeader, SearchBar, TableCard, StatusTag } from '@/components'
import { useResponsive } from '@/composables/useResponsive'
import { getMovementPageApi, deleteMovementApi } from '@/api/movement'
import type { MovementItem } from '@/api/movement'
import request from '@/api/request'
import { formatDateTime } from '@/utils/format'

const route = useRoute()
// 响应式列可见性：移动端隐藏次要列（位置/互动/举报/发布时间）
const { isMobile } = useResponsive()
const loading = ref(false)
const tableData = ref<MovementItem[]>([])
const total = ref(0)
const page = ref(1)
const pageSize = ref(10)
const searchKeyword = ref('')
const searchUserId = ref('')
const searchSeeType = ref<number | undefined>(undefined)
const detailVisible = ref(false)
const detailRow = ref<MovementItem | null>(null)
/** 举报跳转携带的目标动态ID，用于本页高亮定位 */
const highlightId = ref<string | null>(null)
const selectedRows = ref<MovementItem[]>([])
const dateRange = ref<[string, string] | null>(null)

const query = {
  keyword: '',
  userId: '',
  seeType: undefined as number | undefined,
  startDate: '',
  endDate: '',
}

// 可见范围映射
const seeTypeMapping = {
  '0': { label: '公开', type: 'success' as const },
  '1': { label: '好友', type: 'warning' as const },
  '2': { label: '私密', type: 'danger' as const },
}

async function loadData() {
  loading.value = true
  try {
    const params: any = { page: page.value, pageSize: pageSize.value }
    if (searchKeyword.value.trim()) params.keyword = searchKeyword.value.trim()
    const uid = Number(searchUserId.value.trim())
    if (searchUserId.value.trim() && !Number.isNaN(uid)) params.userId = uid
    if (searchSeeType.value !== undefined) params.seeType = searchSeeType.value
    if (dateRange.value) {
      params.startDate = dateRange.value[0]
      params.endDate = dateRange.value[1]
    }
    const res = await getMovementPageApi(params)
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

function handleSelectionChange(rows: MovementItem[]) {
  selectedRows.value = rows
}

async function handleBatchDelete() {
  const ids = selectedRows.value.map(r => r.id)
  try {
    await ElMessageBox.confirm(
      `确定要删除选中的 ${ids.length} 条动态吗？删除后动态主表、相册、时间线中的记录将一并移除，不可恢复。`,
      '批量删除确认',
      { confirmButtonText: '确定删除', cancelButtonText: '取消', type: 'warning' }
    )
    const res: any = await request.delete('/admin/movements/batch', { data: ids })
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
  searchSeeType.value = undefined
  page.value = 1
  loadData()
}

/** 切换每页条数时回到第 1 页 */
function handleSizeChange() {
  page.value = 1
  loadData()
}

/** 举报跳转定位行高亮 */
function rowClassName({ row }: { row: MovementItem }): string {
  return highlightId.value !== null && row.id === highlightId.value ? 'highlight-row' : ''
}

function handleDetail(row: MovementItem) {
  detailRow.value = row
  detailVisible.value = true
}

async function handleDelete(row: MovementItem) {
  try {
    await ElMessageBox.confirm(
      `确定要删除该动态吗？删除后动态主表、相册、时间线中的记录将一并移除，不可恢复。`,
      '删除确认',
      { confirmButtonText: '确定删除', cancelButtonText: '取消', type: 'warning' }
    )
    await deleteMovementApi(row.id)
    ElMessage.success('动态已删除')
    loadData()
  } catch {
    // 取消
  }
}

onMounted(() => {
  // 举报跳转联动：读取 targetId 高亮定位目标动态
  const targetId = Number(route.query.targetId)
  if (route.query.targetId && !Number.isNaN(targetId)) {
    highlightId.value = String(targetId)
    ElMessage.info(`来自举报跳转，正在定位目标动态 #${targetId}（仅高亮当前页可见行）`)
  }
  loadData()
})
</script>

<style scoped>
.batch-count {
  font-size: 13px;
  color: var(--xfjy-text-regular);
  margin-right: auto;
}

.user-cell {
  display: flex;
  align-items: center;
  gap: 8px;
}

.user-name {
  font-size: 13px;
  color: var(--xfjy-text-primary);
  line-height: 1.4;
}

.user-id {
  font-size: 12px;
  color: var(--xfjy-text-secondary);
}

.text-content {
  font-size: 13px;
  color: var(--xfjy-text-regular);
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}

.media-list {
  display: flex;
  align-items: center;
  gap: 4px;
}

.media-thumb {
  width: 44px;
  height: 44px;
  border-radius: 4px;
  flex-shrink: 0;
}

.media-more {
  font-size: 12px;
  color: var(--xfjy-text-secondary);
}

.no-media {
  font-size: 12px;
  color: var(--xfjy-text-disabled);
}

.stat-text {
  font-size: 12px;
  color: var(--xfjy-text-regular);
  margin-right: 6px;
}

/* 详情弹窗 */
.detail-author {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 16px;
}

.detail-author-name {
  font-size: 15px;
  font-weight: 600;
  color: var(--xfjy-text-primary);
}

.detail-author-id {
  font-size: 12px;
  color: var(--xfjy-text-secondary);
  margin-top: 2px;
}

.detail-text {
  font-size: 14px;
  color: var(--xfjy-text-primary);
  line-height: 1.7;
  margin-bottom: 16px;
  white-space: pre-wrap;
}

.detail-medias {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-bottom: 16px;
}

.detail-media-item {
  width: 120px;
  height: 120px;
  border-radius: 6px;
  flex-shrink: 0;
}

.detail-desc {
  margin-top: 4px;
}

/* 举报跳转定位行高亮 */
:deep(.el-table .highlight-row) {
  background-color: var(--xfjy-primary-50) !important;
}
</style>
