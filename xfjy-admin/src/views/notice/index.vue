<template>
  <div class="page-container">
    <!-- 搜索栏 -->
    <div class="search-bar">
      <el-form :inline="true" :model="query" @submit.prevent>
        <el-form-item label="标题">
          <el-input v-model="query.title" placeholder="公告标题" clearable style="width: 180px" @keyup.enter="handleSearch" />
        </el-form-item>
        <el-form-item label="类型">
          <el-select v-model="query.type" placeholder="全部" clearable style="width: 120px">
            <el-option label="系统公告" value="SYSTEM" />
            <el-option label="活动公告" value="ACTIVITY" />
            <el-option label="更新公告" value="UPDATE" />
          </el-select>
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="query.status" placeholder="全部" clearable style="width: 120px">
            <el-option label="草稿" value="DRAFT" />
            <el-option label="已发布" value="PUBLISHED" />
            <el-option label="已下线" value="OFFLINE" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" :icon="Search" @click="handleSearch">查询</el-button>
          <el-button :icon="Refresh" @click="handleReset">重置</el-button>
          <el-button v-perm="['SUPER_ADMIN','ADMIN']" type="success" :icon="Plus" @click="handleAdd">新增公告</el-button>
        </el-form-item>
      </el-form>
    </div>

    <!-- 表格 -->
    <div class="table-container">
      <el-table :data="tableData" v-loading="loading" stripe border style="width: 100%" empty-text="暂无公告数据">
        <el-table-column prop="id" label="ID" width="70" align="center" />
        <el-table-column prop="title" label="标题" min-width="200" show-overflow-tooltip />
        <el-table-column label="类型" width="100" align="center">
          <template #default="{ row }">
            <el-tag :type="typeColor(row.type)" size="small">{{ typeLabel(row.type) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="90" align="center">
          <template #default="{ row }">
            <el-tag :type="statusColor(row.status)" size="small">{{ statusLabel(row.status) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="publisher" label="发布人" width="100" />
        <el-table-column prop="publishedAt" label="发布时间" width="170">
          <template #default="{ row }">{{ row.publishedAt || '-' }}</template>
        </el-table-column>
        <el-table-column prop="createdAt" label="创建时间" width="170" />
        <el-table-column label="操作" width="240" fixed="right" align="center">
          <template #default="{ row }">
            <el-button v-perm="['SUPER_ADMIN','ADMIN']" link type="primary" size="small" @click="handleEdit(row)">编辑</el-button>
            <el-button v-if="row.status === 'DRAFT'" v-perm="['SUPER_ADMIN','ADMIN']" link type="success" size="small" @click="handlePublish(row)">发布</el-button>
            <el-button v-if="row.status === 'PUBLISHED'" v-perm="['SUPER_ADMIN','ADMIN']" link type="warning" size="small" @click="handleOffline(row)">下线</el-button>
            <el-button v-perm="['SUPER_ADMIN','ADMIN']" link type="danger" size="small" @click="handleDelete(row)">删除</el-button>
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
          @size-change="handleSizeChange"
          @current-change="loadData"
        />
      </div>
    </div>

    <!-- 新增/编辑弹窗 -->
    <el-dialog v-model="formVisible" :title="editingId ? '编辑公告' : '新增公告'" width="600px">
      <el-form ref="formRef" :model="form" :rules="formRules" label-width="80px">
        <el-form-item label="标题" prop="title">
          <el-input v-model="form.title" placeholder="请输入公告标题" maxlength="50" show-word-limit />
        </el-form-item>
        <el-form-item label="类型" prop="type">
          <el-select v-model="form.type" placeholder="请选择公告类型" style="width: 100%">
            <el-option label="系统公告" value="SYSTEM" />
            <el-option label="活动公告" value="ACTIVITY" />
            <el-option label="更新公告" value="UPDATE" />
          </el-select>
        </el-form-item>
        <el-form-item label="内容" prop="content">
          <el-input v-model="form.content" type="textarea" :rows="6" placeholder="请输入公告内容" maxlength="1000" show-word-limit />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="formVisible = false">取消</el-button>
        <el-button type="primary" @click="confirmSave">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import type { FormInstance, FormRules } from 'element-plus'
import { Search, Refresh, Plus } from '@element-plus/icons-vue'
import {
  getNoticePageApi,
  createNoticeApi,
  updateNoticeApi,
  publishNoticeApi,
  offlineNoticeApi,
  deleteNoticeApi,
} from '@/api/notice'
import type { NoticeItem, NoticeQuery, NoticeForm } from '@/api/notice'

const loading = ref(false)
const tableData = ref<NoticeItem[]>([])
const total = ref(0)

const query = reactive<NoticeQuery>({
  page: 1,
  pageSize: 10,
  title: '',
  type: '',
  status: '',
})

// Mock 数据
function getMockNotices(page: number, pageSize: number): { total: number; list: NoticeItem[] } {
  const data: NoticeItem[] = [
    { id: 1, title: '心飞交友 v2.0 新版本上线公告', content: '全新匹配算法，更快找到心仪对象', type: 'UPDATE', status: 'PUBLISHED', publisher: '超级管理员', publishedAt: '2026-10-20 10:00:00', createdAt: '2026-10-19 15:00:00' },
    { id: 2, title: '双十一脱单活动开启', content: '活动期间开通VIP享7折优惠，快来参与', type: 'ACTIVITY', status: 'PUBLISHED', publisher: '超级管理员', publishedAt: '2026-10-18 09:00:00', createdAt: '2026-10-17 14:00:00' },
    { id: 3, title: '平台安全规范更新通知', content: '为保障用户安全，平台更新了安全规范', type: 'SYSTEM', status: 'PUBLISHED', publisher: '超级管理员', publishedAt: '2026-10-15 11:00:00', createdAt: '2026-10-14 16:00:00' },
    { id: 4, title: '国庆假期活动回顾', content: '感谢大家参与国庆活动', type: 'ACTIVITY', status: 'OFFLINE', publisher: '运营专员', publishedAt: '2026-10-08 10:00:00', createdAt: '2026-10-07 09:00:00' },
    { id: 5, title: '新功能预告：视频通话', content: '即将上线1v1视频通话功能', type: 'UPDATE', status: 'DRAFT', publisher: '超级管理员', publishedAt: undefined, createdAt: '2026-10-21 17:00:00' },
  ]
  const start = (page - 1) * pageSize
  return { total: data.length, list: data.slice(start, start + pageSize) }
}

async function loadData() {
  loading.value = true
  try {
    const res = await getNoticePageApi(query)
    tableData.value = res.list
    total.value = res.total
  } catch {
    // Mock 兜底仅开发环境生效，生产环境显示空列表避免假数据误导
    if (import.meta.env.DEV) {
      const mock = getMockNotices(query.page, query.pageSize)
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
  query.title = ''
  query.type = ''
  query.status = ''
  query.page = 1
  loadData()
}

/** 切换每页条数时回到第 1 页 */
function handleSizeChange() {
  query.page = 1
  loadData()
}

function typeLabel(type: string) {
  const map: Record<string, string> = { SYSTEM: '系统公告', ACTIVITY: '活动公告', UPDATE: '更新公告' }
  return map[type] || type
}

function typeColor(type: string) {
  const map: Record<string, string> = { SYSTEM: 'primary', ACTIVITY: 'warning', UPDATE: 'success' }
  return map[type] || 'info'
}

function statusLabel(status: string) {
  const map: Record<string, string> = { DRAFT: '草稿', PUBLISHED: '已发布', OFFLINE: '已下线' }
  return map[status] || status
}

function statusColor(status: string) {
  const map: Record<string, string> = { DRAFT: 'info', PUBLISHED: 'success', OFFLINE: 'danger' }
  return map[status] || 'info'
}

// 新增/编辑
const formVisible = ref(false)
const formRef = ref<FormInstance>()
const editingId = ref<number | null>(null)

const form = reactive<NoticeForm>({
  title: '',
  content: '',
  type: 'SYSTEM',
})

const formRules: FormRules = {
  title: [{ required: true, message: '请输入公告标题', trigger: 'blur' }],
  type: [{ required: true, message: '请选择公告类型', trigger: 'change' }],
  content: [{ required: true, message: '请输入公告内容', trigger: 'blur' }],
}

function handleAdd() {
  editingId.value = null
  form.title = ''
  form.content = ''
  form.type = 'SYSTEM'
  formVisible.value = true
}

function handleEdit(row: NoticeItem) {
  editingId.value = row.id
  form.title = row.title
  form.content = row.content
  form.type = row.type
  formVisible.value = true
}

async function confirmSave() {
  if (!formRef.value) return
  await formRef.value.validate(async (valid) => {
    if (!valid) return
    try {
      if (editingId.value) {
        await updateNoticeApi(editingId.value, form)
        ElMessage.success('更新成功')
      } else {
        await createNoticeApi(form)
        ElMessage.success('创建成功')
      }
      formVisible.value = false
      loadData()
    } catch {
      // 错误已由拦截器处理
    }
  })
}

async function handlePublish(row: NoticeItem) {
  try {
    await ElMessageBox.confirm(`确定发布公告「${row.title}」吗？`, '提示', { type: 'success' })
    await publishNoticeApi(row.id)
    ElMessage.success('发布成功')
    // 重新拉取列表，保证发布时间/状态与服务端一致
    loadData()
  } catch {
    // 取消
  }
}

async function handleOffline(row: NoticeItem) {
  try {
    await ElMessageBox.confirm(`确定下线公告「${row.title}」吗？`, '提示', { type: 'warning' })
    await offlineNoticeApi(row.id)
    ElMessage.success('已下线')
    loadData()
  } catch {
    // 取消
  }
}

async function handleDelete(row: NoticeItem) {
  try {
    await ElMessageBox.confirm(`确定删除公告「${row.title}」吗？删除后不可恢复`, '警告', { type: 'error' })
    await deleteNoticeApi(row.id)
    ElMessage.success('已删除')
    loadData()
  } catch {
    // 取消
  }
}

onMounted(() => {
  loadData()
})
</script>