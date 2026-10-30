<template>
  <div class="page-container">
    <!-- 搜索栏 -->
    <div class="search-bar">
      <el-input
        v-model="searchKeyword"
        placeholder="搜索视频文字内容"
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
    <el-table v-loading="loading" :data="tableData" border stripe empty-text="暂无视频数据" @selection-change="handleSelectionChange">
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
      <el-table-column label="封面" width="100" align="center">
        <template #default="{ row }">
          <el-image
            v-if="row.picUrl"
            :src="row.picUrl"
            :preview-src-list="[row.picUrl]"
            fit="cover"
            class="cover-thumb"
            preview-teleported
          />
          <span v-else class="no-media">无封面</span>
        </template>
      </el-table-column>
      <el-table-column label="文字" min-width="200">
        <template #default="{ row }">
          <div class="text-content">{{ row.text || '(无文字)' }}</div>
        </template>
      </el-table-column>
      <el-table-column label="视频" width="110" align="center">
        <template #default="{ row }">
          <el-button
            v-if="row.videoUrl"
            type="primary"
            link
            size="small"
            @click="handlePreview(row)"
          >
            <el-icon><VideoPlay /></el-icon>&nbsp;播放
          </el-button>
          <span v-else class="no-media">无视频</span>
        </template>
      </el-table-column>
      <el-table-column label="位置" width="120" show-overflow-tooltip>
        <template #default="{ row }">
          {{ row.locationName || '-' }}
        </template>
      </el-table-column>
      <el-table-column label="互动" width="110" align="center">
        <template #default="{ row }">
          <span class="stat-text">赞 {{ row.likeCount }}</span>
          <span class="stat-text">评 {{ row.commentCount }}</span>
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

    <!-- 视频预览弹窗 -->
    <el-dialog v-model="previewVisible" title="视频预览" width="480px" destroy-on-close>
      <video
        v-if="previewUrl"
        :src="previewUrl"
        controls
        autoplay
        style="width: 100%; max-height: 480px; background: #000; border-radius: 4px"
      />
    </el-dialog>

    <!-- 视频详情弹窗 -->
    <el-dialog v-model="detailVisible" title="视频详情" width="620px" destroy-on-close>
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

        <!-- 视频播放 -->
        <div v-if="detailRow.videoUrl" class="detail-video-wrap">
          <video
            :src="detailRow.videoUrl"
            :poster="detailRow.picUrl"
            controls
            style="width: 100%; max-height: 360px; background: #000; border-radius: 6px"
          />
        </div>

        <!-- 文字内容 -->
        <div v-if="detailRow.text" class="detail-text">{{ detailRow.text }}</div>

        <!-- 位置 + 互动 -->
        <el-descriptions :column="2" border class="detail-desc">
          <el-descriptions-item label="位置">{{ detailRow.locationName || '-' }}</el-descriptions-item>
          <el-descriptions-item label="互动">
            赞 {{ detailRow.likeCount }} · 评 {{ detailRow.commentCount }}
          </el-descriptions-item>
        </el-descriptions>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { getVideoPageApi, deleteVideoApi } from '@/api/video'
import type { VideoItem } from '@/api/video'
import request from '@/api/request'
import { formatDateTime } from '@/utils/format'

const loading = ref(false)
const tableData = ref<VideoItem[]>([])
const total = ref(0)
const page = ref(1)
const pageSize = ref(10)
const searchKeyword = ref('')
const searchUserId = ref('')
const previewVisible = ref(false)
const previewUrl = ref('')
const detailVisible = ref(false)
const detailRow = ref<VideoItem | null>(null)
const selectedRows = ref<VideoItem[]>([])
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
    const res = await getVideoPageApi(params)
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

function handleSelectionChange(rows: VideoItem[]) {
  selectedRows.value = rows
}

async function handleBatchDelete() {
  const ids = selectedRows.value.map(r => r.id)
  try {
    await ElMessageBox.confirm(
      `确定要删除选中的 ${ids.length} 个视频吗？删除后视频主表、相册、时间线中的记录将一并移除，不可恢复。`,
      '批量删除确认',
      { confirmButtonText: '确定删除', cancelButtonText: '取消', type: 'warning' }
    )
    const res: any = await request.delete('/admin/videos/batch', { data: ids })
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

function handlePreview(row: VideoItem) {
  previewUrl.value = row.videoUrl
  previewVisible.value = true
}

function handleDetail(row: VideoItem) {
  detailRow.value = row
  detailVisible.value = true
}

async function handleDelete(row: VideoItem) {
  try {
    await ElMessageBox.confirm(
      `确定要删除该视频吗？删除后视频主表、相册、时间线中的记录将一并移除，不可恢复。`,
      '删除确认',
      { confirmButtonText: '确定删除', cancelButtonText: '取消', type: 'warning' }
    )
    await deleteVideoApi(row.id)
    ElMessage.success('视频已删除')
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

.cover-thumb {
  width: 56px;
  height: 40px;
  border-radius: 4px;
}

.text-content {
  font-size: 13px;
  color: #606266;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}

.no-media {
  font-size: 12px;
  color: #c0c4cc;
}

.stat-text {
  font-size: 12px;
  color: #606266;
  margin-right: 6px;
}

.pagination-bar {
  display: flex;
  justify-content: flex-end;
  margin-top: 16px;
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
  color: #303133;
}

.detail-author-id {
  font-size: 12px;
  color: #909399;
  margin-top: 2px;
}

.detail-video-wrap {
  margin-bottom: 16px;
}

.detail-text {
  font-size: 14px;
  color: #303133;
  line-height: 1.7;
  margin-bottom: 16px;
  white-space: pre-wrap;
}

.detail-desc {
  margin-top: 4px;
}
</style>