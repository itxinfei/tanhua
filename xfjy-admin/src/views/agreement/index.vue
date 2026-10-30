<template>
  <div class="page-container">
    <div class="search-bar">
      <el-form :inline="true" :model="query" @submit.prevent>
        <el-form-item label="标题">
          <el-input v-model="query.title" placeholder="协议标题" clearable style="width: 180px" @keyup.enter="handleSearch" />
        </el-form-item>
        <el-form-item label="类型">
          <el-select v-model="query.type" placeholder="全部" clearable style="width: 120px">
            <el-option label="用户协议" value="USER" />
            <el-option label="隐私政策" value="PRIVACY" />
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
          <el-button v-perm="['SUPER_ADMIN','ADMIN']" type="success" :icon="Plus" @click="handleAdd">新增协议</el-button>
        </el-form-item>
      </el-form>
    </div>

    <div class="table-container">
      <el-table :data="tableData" v-loading="loading" stripe border style="width: 100%" empty-text="暂无协议数据">
        <el-table-column prop="id" label="ID" width="70" align="center" />
        <el-table-column prop="title" label="标题" min-width="160" show-overflow-tooltip />
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
        <el-table-column prop="version" label="版本" width="70" align="center" />
        <el-table-column prop="publisher" label="发布人" width="100" />
        <el-table-column prop="publishedAt" label="发布时间" width="170">
          <template #default="{ row }">{{ row.publishedAt || '-' }}</template>
        </el-table-column>
        <el-table-column prop="created" label="创建时间" width="170" />
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

    <el-dialog v-model="formVisible" :title="editingId ? '编辑协议' : '新增协议'" width="720px">
      <el-form ref="formRef" :model="form" :rules="formRules" label-width="80px">
        <el-form-item label="类型" prop="type">
          <el-select v-model="form.type" placeholder="请选择协议类型" style="width: 100%">
            <el-option label="用户协议" value="USER" />
            <el-option label="隐私政策" value="PRIVACY" />
          </el-select>
        </el-form-item>
        <el-form-item label="标题" prop="title">
          <el-input v-model="form.title" placeholder="请输入协议标题" maxlength="100" show-word-limit />
        </el-form-item>
        <el-form-item label="正文" prop="content">
          <el-input
            v-model="form.content"
            type="textarea"
            :rows="14"
            placeholder="请输入协议正文，支持多段落（每段一行）。上线前请替换【运营主体名称】【客服邮箱】【客服电话】等占位符"
            maxlength="20000"
            show-word-limit
          />
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
  getAgreementPageApi,
  createAgreementApi,
  updateAgreementApi,
  publishAgreementApi,
  offlineAgreementApi,
  deleteAgreementApi,
} from '@/api/agreement'
import type { AgreementItem, AgreementQuery, AgreementForm } from '@/api/agreement'

const loading = ref(false)
const tableData = ref<AgreementItem[]>([])
const total = ref(0)

const query = reactive<AgreementQuery>({
  page: 1,
  pageSize: 10,
  title: '',
  type: '',
  status: '',
})

function getMockAgreements(page: number, pageSize: number): { total: number; list: AgreementItem[] } {
  const data: AgreementItem[] = [
    { id: 1, title: '用户协议', content: '心飞交友用户协议正文……', type: 'USER', status: 'PUBLISHED', version: 1, publisher: '超级管理员', publishedAt: '2026-10-30 10:00:00', created: '2026-10-30 09:00:00' },
    { id: 2, title: '隐私政策', content: '心飞交友隐私政策正文……', type: 'PRIVACY', status: 'PUBLISHED', version: 1, publisher: '超级管理员', publishedAt: '2026-10-30 10:05:00', created: '2026-10-30 09:10:00' },
  ]
  const start = (page - 1) * pageSize
  return { total: data.length, list: data.slice(start, start + pageSize) }
}

async function loadData() {
  loading.value = true
  try {
    const res = await getAgreementPageApi(query)
    tableData.value = res.list
    total.value = res.total
  } catch {
    if (import.meta.env.DEV) {
      const mock = getMockAgreements(query.page, query.pageSize)
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

function handleSizeChange() {
  query.page = 1
  loadData()
}

function typeLabel(type: string) {
  const map: Record<string, string> = { USER: '用户协议', PRIVACY: '隐私政策' }
  return map[type] || type
}

function typeColor(type: string) {
  const map: Record<string, string> = { USER: 'primary', PRIVACY: 'warning' }
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

const formVisible = ref(false)
const formRef = ref<FormInstance>()
const editingId = ref<number | null>(null)

const form = reactive<AgreementForm>({
  title: '',
  content: '',
  type: 'USER',
})

const formRules: FormRules = {
  type: [{ required: true, message: '请选择协议类型', trigger: 'change' }],
  title: [{ required: true, message: '请输入协议标题', trigger: 'blur' }],
  content: [{ required: true, message: '请输入协议正文', trigger: 'blur' }],
}

function handleAdd() {
  editingId.value = null
  form.title = ''
  form.content = ''
  form.type = 'USER'
  formVisible.value = true
}

function handleEdit(row: AgreementItem) {
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
        await updateAgreementApi(editingId.value, form)
        ElMessage.success('更新成功')
      } else {
        await createAgreementApi(form)
        ElMessage.success('创建成功')
      }
      formVisible.value = false
      loadData()
    } catch {
      // 错误已由拦截器处理
    }
  })
}

async function handlePublish(row: AgreementItem) {
  try {
    await ElMessageBox.confirm(`确定发布协议「${row.title}」吗？同类型已发布的旧版本将自动下线。`, '提示', { type: 'success' })
    await publishAgreementApi(row.id)
    ElMessage.success('发布成功')
    loadData()
  } catch {
    // 取消
  }
}

async function handleOffline(row: AgreementItem) {
  try {
    await ElMessageBox.confirm(`确定下线协议「${row.title}」吗？`, '提示', { type: 'warning' })
    await offlineAgreementApi(row.id)
    ElMessage.success('已下线')
    loadData()
  } catch {
    // 取消
  }
}

async function handleDelete(row: AgreementItem) {
  try {
    await ElMessageBox.confirm(`确定删除协议「${row.title}」吗？删除后不可恢复`, '警告', { type: 'error' })
    await deleteAgreementApi(row.id)
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
