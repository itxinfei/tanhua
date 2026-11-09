<template>
  <div class="page-container">
    <PageHeader title="客服反馈" :subtitle="`共 ${total} 条记录`" />

    <SearchBar
      :model="query"
      :filter-meta="{
        status: { label: '状态', options: { 0: '待处理', 1: '已回复', 2: '已关闭' } },
      }"
      @search="handleSearch"
      @reset="handleReset"
    >
      <el-form-item label="状态">
        <el-select v-model="query.status" placeholder="全部" clearable style="width: 130px">
          <el-option label="待处理" :value="0" />
          <el-option label="已回复" :value="1" />
          <el-option label="已关闭" :value="2" />
        </el-select>
      </el-form-item>
    </SearchBar>

    <TableCard :skeleton="loading && !tableData.length">
      <el-table v-loading="loading" :data="tableData" stripe border style="width: 100%">
        <el-table-column v-if="!isMobile" prop="id" label="ID" width="70" align="center" />
        <el-table-column v-if="!isMobile" prop="userId" label="用户ID" width="100" align="center" />
        <el-table-column prop="content" label="反馈内容" min-width="220" show-overflow-tooltip />
        <el-table-column v-if="!isMobile" label="联系方式" width="150" show-overflow-tooltip>
          <template #default="{ row }">
            <span>{{ row.contact || '-' }}</span>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="90" align="center">
          <template #default="{ row }">
            <StatusTag :status="String(row.status)" :mapping="feedbackStatusMapping" />
          </template>
        </el-table-column>
        <el-table-column v-if="!isMobile" label="反馈时间" width="170">
          <template #default="{ row }">
            <span>{{ formatDateTime(row.created) }}</span>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="150" fixed="right" align="center">
          <template #default="{ row }">
            <el-button
              v-if="row.status !== 2"
              v-perm="['SUPER_ADMIN','ADMIN','OPERATOR']"
              link
              type="primary"
              size="small"
              @click="openReply(row)"
            >
              {{ row.status === 1 ? '再次回复' : '回复' }}
            </el-button>
            <el-button
              v-if="row.status !== 2"
              v-perm="['SUPER_ADMIN','ADMIN','OPERATOR']"
              link
              type="warning"
              size="small"
              @click="handleClose(row)"
            >
              关闭
            </el-button>
          </template>
        </el-table-column>
        <template #empty>
          <TableEmpty description="暂无反馈数据" hint="反馈由 App 端用户提交，暂无数据时无需操作" />
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

    <!-- 回复弹窗 -->
    <el-dialog v-model="replyVisible" title="回复反馈" width="520px">
      <el-descriptions v-if="currentItem" :column="2" border class="reply-context">
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
        style="margin-top: var(--xfjy-space-4)"
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
import { PageHeader, SearchBar, TableCard, TableEmpty, StatusTag } from '@/components'
import { useResponsive } from '@/composables/useResponsive'
import { getFeedbackPageApi, replyFeedbackApi, closeFeedbackApi } from '@/api/kefu'
import type { FeedbackItem, FeedbackQuery } from '@/api/kefu'
import { formatDateTime } from '@/utils/format'

const loading = ref(false)
const tableData = ref<FeedbackItem[]>([])
const total = ref(0)

const query = reactive<FeedbackQuery>({
  page: 1,
  pageSize: 10,
  status: undefined,
})

// 响应式列可见性：移动端隐藏次要列（ID/用户ID/联系方式/反馈时间）
const { isMobile } = useResponsive()

async function loadData() {
  loading.value = true
  try {
    const res = await getFeedbackPageApi(query)
    tableData.value = res.list
    total.value = res.total
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
  query.status = undefined
  query.page = 1
  loadData()
}

/** 切换每页条数时回到第 1 页 */
function handleSizeChange() {
  query.page = 1
  loadData()
}

// 反馈状态映射（0=待处理 1=已回复 2=已关闭）
const feedbackStatusMapping = {
  '0': { label: '待处理', type: 'warning' as const },
  '1': { label: '已回复', type: 'success' as const },
  '2': { label: '已关闭', type: 'info' as const },
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
  margin-bottom: var(--xfjy-space-1);
}
</style>