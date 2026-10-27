<template>
  <div class="page-container">
    <!-- 搜索栏 -->
    <div class="search-bar">
      <el-form :inline="true" :model="query" @submit.prevent>
        <el-form-item label="状态">
          <el-select v-model="query.status" placeholder="全部" clearable style="width: 130px">
            <el-option label="待处理" :value="0" />
            <el-option label="已回复" :value="1" />
            <el-option label="已关闭" :value="2" />
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
        <el-table-column prop="userId" label="用户ID" width="90" align="center" />
        <el-table-column prop="content" label="反馈内容" min-width="220" show-overflow-tooltip />
        <el-table-column label="联系方式" width="150" show-overflow-tooltip>
          <template #default="{ row }">
            <span>{{ row.contact || '-' }}</span>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="90" align="center">
          <template #default="{ row }">
            <el-tag :type="statusColor(row.status)" size="small">{{ statusLabel(row.status) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="反馈时间" width="170">
          <template #default="{ row }">
            <span>{{ fmtDate(row.created) }}</span>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="150" fixed="right" align="center">
          <template #default="{ row }">
            <el-button v-if="row.status !== 2" link type="primary" size="small" @click="openReply(row)">
              {{ row.status === 1 ? '再次回复' : '回复' }}
            </el-button>
            <el-button v-if="row.status !== 2" link type="warning" size="small" @click="handleClose(row)">
              关闭
            </el-button>
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

    <!-- 回复弹窗 -->
    <el-dialog v-model="replyVisible" title="回复反馈" width="520px">
      <el-descriptions :column="2" border v-if="currentItem" class="reply-context">
        <el-descriptions-item label="反馈ID">{{ currentItem.id }}</el-descriptions-item>
        <el-descriptions-item label="用户ID">{{ currentItem.userId }}</el-descriptions-item>
        <el-descriptions-item label="反馈内容" :span="2">{{ currentItem.content }}</el-descriptions-item>
        <el-descriptions-item v-if="currentItem.contact" label="联系方式" :span="2">
          {{ currentItem.contact }}
        </el-descriptions-item>
      </el-descriptions>
      <el-input
        v-model="replyText"
        type="textarea"
        :rows="4"
        maxlength="500"
        show-word-limit
        placeholder="请输入回复内容，用户可在App「我的反馈」中查看"
        style="margin-top: 16px"
      />
      <template #footer>
        <el-button @click="replyVisible = false">取消</el-button>
        <el-button type="primary" :loading="replySubmitting" @click="confirmReply">确认回复</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Search, Refresh } from '@element-plus/icons-vue'
import { getFeedbackPageApi, replyFeedbackApi, closeFeedbackApi } from '@/api/kefu'
import type { FeedbackItem, FeedbackQuery } from '@/api/kefu'

const loading = ref(false)
const tableData = ref<FeedbackItem[]>([])
const total = ref(0)

const query = reactive<FeedbackQuery>({
  page: 1,
  pageSize: 10,
  status: undefined,
})

async function loadData() {
  loading.value = true
  try {
    const res = await getFeedbackPageApi(query)
    tableData.value = res.list
    total.value = res.total
  } catch {
    // 错误已由拦截器处理
  } finally {
    loading.value = false
  }
}

function handleSearch() {
  query.page = 1
  loadData()
}

function handleReset() {
  query.status = undefined
  query.page = 1
  loadData()
}

function statusLabel(status: number) {
  return ['待处理', '已回复', '已关闭'][status] || '未知'
}

function statusColor(status: number) {
  return (['warning', 'success', 'info'] as const)[status] || 'info'
}

/** 兼容后端 Date 序列化的多种格式（时间戳 / ISO 字符串 / 已格式化字符串） */
function fmtDate(val: string | number): string {
  if (val == null) return '-'
  if (typeof val === 'number') {
    const d = new Date(val)
    const pad = (n: number) => String(n).padStart(2, '0')
    return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())} ${pad(d.getHours())}:${pad(d.getMinutes())}`
  }
  return val
}

// 回复
const replyVisible = ref(false)
const replyText = ref('')
const replySubmitting = ref(false)
const currentItem = ref<FeedbackItem | null>(null)

function openReply(row: FeedbackItem) {
  currentItem.value = row
  replyText.value = row.reply || ''
  replyVisible.value = true
}

async function confirmReply() {
  if (!replyText.value.trim()) {
    ElMessage.warning('请输入回复内容')
    return
  }
  if (!currentItem.value) return
  replySubmitting.value = true
  try {
    await replyFeedbackApi(currentItem.value.id, replyText.value.trim())
    ElMessage.success('回复成功')
    replyVisible.value = false
    loadData()
  } catch {
    // 错误已由拦截器处理
  } finally {
    replySubmitting.value = false
  }
}

// 关闭
async function handleClose(row: FeedbackItem) {
  try {
    await ElMessageBox.confirm('确认关闭该反馈？关闭后不再处理。', '提示', {
      type: 'warning',
      confirmButtonText: '确认关闭',
      cancelButtonText: '取消',
    })
    await closeFeedbackApi(row.id)
    ElMessage.success('已关闭')
    loadData()
  } catch {
    // 用户取消或请求失败，均无需额外处理（请求失败由拦截器提示）
  }
}

onMounted(() => {
  loadData()
})
</script>

<style scoped>
.reply-context {
  margin-bottom: 4px;
}
</style>