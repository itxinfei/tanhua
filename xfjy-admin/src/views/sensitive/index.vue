<template>
  <div class="page-container">
    <!-- 搜索栏 -->
    <div class="search-bar">
      <el-form :inline="true" :model="query" @submit.prevent>
        <el-form-item label="敏感词">
          <el-input v-model="query.word" placeholder="关键词" clearable style="width: 160px" @keyup.enter="handleSearch" />
        </el-form-item>
        <el-form-item label="分类">
          <el-select v-model="query.category" placeholder="全部" clearable style="width: 120px">
            <el-option label="政治" value="POLITICS" />
            <el-option label="色情" value="PORN" />
            <el-option label="暴力" value="VIOLENCE" />
            <el-option label="广告" value="AD" />
            <el-option label="其他" value="OTHER" />
          </el-select>
        </el-form-item>
        <el-form-item label="级别">
          <el-select v-model="query.level" placeholder="全部" clearable style="width: 110px">
            <el-option label="高危" :value="1" />
            <el-option label="中危" :value="2" />
            <el-option label="低危" :value="3" />
          </el-select>
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="query.status" placeholder="全部" clearable style="width: 110px">
            <el-option label="启用" :value="1" />
            <el-option label="停用" :value="0" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" :icon="Search" @click="handleSearch">查询</el-button>
          <el-button :icon="Refresh" @click="handleReset">重置</el-button>
          <el-button v-perm="['SUPER_ADMIN','ADMIN']" type="success" :icon="Plus" @click="handleAdd">新增</el-button>
          <el-button v-perm="['SUPER_ADMIN','ADMIN']" type="warning" :icon="RefreshRight" @click="handleRefresh">刷新词库</el-button>
        </el-form-item>
      </el-form>
    </div>

    <!-- 表格 -->
    <div class="table-container">
      <el-table :data="tableData" v-loading="loading" stripe border style="width: 100%" empty-text="暂无敏感词数据">
        <el-table-column prop="id" label="ID" width="70" align="center" />
        <el-table-column prop="word" label="敏感词" min-width="160" show-overflow-tooltip />
        <el-table-column label="分类" width="90" align="center">
          <template #default="{ row }">
            <el-tag :type="categoryColor(row.category)" size="small">{{ categoryLabel(row.category) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="级别" width="80" align="center">
          <template #default="{ row }">
            <el-tag :type="levelColor(row.level)" size="small" effect="plain">{{ levelLabel(row.level) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="80" align="center">
          <template #default="{ row }">
            <el-tag :type="row.status === 1 ? 'success' : 'info'" size="small">{{ row.status === 1 ? '启用' : '停用' }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="updated" label="更新时间" width="170" />
        <el-table-column label="操作" width="200" fixed="right" align="center">
          <template #default="{ row }">
            <el-button v-perm="['SUPER_ADMIN','ADMIN']" link type="primary" size="small" @click="handleEdit(row)">编辑</el-button>
            <el-button v-if="row.status === 1" v-perm="['SUPER_ADMIN','ADMIN']" link type="warning" size="small" @click="handleToggle(row, 0)">停用</el-button>
            <el-button v-else v-perm="['SUPER_ADMIN','ADMIN']" link type="success" size="small" @click="handleToggle(row, 1)">启用</el-button>
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
    <el-dialog v-model="formVisible" :title="editingId ? '编辑敏感词' : '新增敏感词'" width="480px">
      <el-form ref="formRef" :model="form" :rules="formRules" label-width="80px">
        <el-form-item label="敏感词" prop="word">
          <el-input v-model="form.word" placeholder="请输入敏感词" maxlength="30" show-word-limit />
        </el-form-item>
        <el-form-item label="分类" prop="category">
          <el-select v-model="form.category" placeholder="请选择分类" style="width: 100%">
            <el-option label="政治" value="POLITICS" />
            <el-option label="色情" value="PORN" />
            <el-option label="暴力" value="VIOLENCE" />
            <el-option label="广告" value="AD" />
            <el-option label="其他" value="OTHER" />
          </el-select>
        </el-form-item>
        <el-form-item label="级别" prop="level">
          <el-radio-group v-model="form.level">
            <el-radio :value="1">高危（拦截）</el-radio>
            <el-radio :value="2">中危（人工审核）</el-radio>
            <el-radio :value="3">低危（过滤替换）</el-radio>
          </el-radio-group>
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
import { Search, Refresh, Plus, RefreshRight } from '@element-plus/icons-vue'
import {
  getSensitiveWordPageApi,
  createSensitiveWordApi,
  updateSensitiveWordApi,
  toggleSensitiveWordApi,
  deleteSensitiveWordApi,
  refreshSensitiveWordApi,
} from '@/api/sensitive'
import type { SensitiveWordItem, SensitiveWordQuery, SensitiveWordForm } from '@/api/sensitive'

const loading = ref(false)
const tableData = ref<SensitiveWordItem[]>([])
const total = ref(0)

const query = reactive<SensitiveWordQuery>({
  page: 1,
  pageSize: 10,
  word: '',
  category: '',
  level: undefined,
  status: undefined,
})

async function loadData() {
  loading.value = true
  try {
    const res = await getSensitiveWordPageApi(query)
    tableData.value = res.list
    total.value = res.total
  } catch {
    // 生产环境显示空列表，词库变更需后端可用
    tableData.value = []
    total.value = 0
  } finally {
    loading.value = false
  }
}

function handleSearch() {
  query.page = 1
  loadData()
}

function handleReset() {
  query.word = ''
  query.category = ''
  query.level = undefined
  query.status = undefined
  query.page = 1
  loadData()
}

/** 切换每页条数时回到第 1 页 */
function handleSizeChange() {
  query.page = 1
  loadData()
}

function categoryLabel(category: string) {
  const map: Record<string, string> = { POLITICS: '政治', PORN: '色情', VIOLENCE: '暴力', AD: '广告', OTHER: '其他' }
  return map[category] || category
}

function categoryColor(category: string) {
  const map: Record<string, string> = { POLITICS: 'danger', PORN: 'warning', VIOLENCE: 'danger', AD: 'primary', OTHER: 'info' }
  return map[category] || 'info'
}

function levelLabel(level: number) {
  const map: Record<number, string> = { 1: '高危', 2: '中危', 3: '低危' }
  return map[level] || String(level)
}

function levelColor(level: number) {
  const map: Record<number, string> = { 1: 'danger', 2: 'warning', 3: 'info' }
  return map[level] || 'info'
}

// 新增/编辑
const formVisible = ref(false)
const formRef = ref<FormInstance>()
const editingId = ref<number | null>(null)

const form = reactive<SensitiveWordForm>({
  word: '',
  category: 'OTHER',
  level: 3,
})

const formRules: FormRules = {
  word: [{ required: true, message: '请输入敏感词', trigger: 'blur' }],
  category: [{ required: true, message: '请选择分类', trigger: 'change' }],
  level: [{ required: true, message: '请选择级别', trigger: 'change' }],
}

function handleAdd() {
  editingId.value = null
  form.word = ''
  form.category = 'OTHER'
  form.level = 3
  formVisible.value = true
}

function handleEdit(row: SensitiveWordItem) {
  editingId.value = row.id
  form.word = row.word
  form.category = row.category
  form.level = row.level
  formVisible.value = true
}

async function confirmSave() {
  if (!formRef.value) return
  await formRef.value.validate(async (valid) => {
    if (!valid) return
    try {
      if (editingId.value) {
        await updateSensitiveWordApi(editingId.value, form)
        ElMessage.success('更新成功，词库已刷新')
      } else {
        await createSensitiveWordApi(form)
        ElMessage.success('新增成功，词库已刷新')
      }
      formVisible.value = false
      loadData()
    } catch {
      // 错误已由拦截器处理
    }
  })
}

async function handleToggle(row: SensitiveWordItem, status: number) {
  try {
    await ElMessageBox.confirm(
      `确定${status === 1 ? '启用' : '停用'}敏感词「${row.word}」吗？词库将同步刷新。`,
      '提示',
      { type: 'warning' }
    )
    await toggleSensitiveWordApi(row.id, status)
    ElMessage.success(status === 1 ? '已启用，词库已刷新' : '已停用，词库已刷新')
    loadData()
  } catch {
    // 取消或失败（失败已由拦截器提示）
  }
}

async function handleDelete(row: SensitiveWordItem) {
  try {
    await ElMessageBox.confirm(`确定删除敏感词「${row.word}」吗？`, '警告', { type: 'error' })
    await deleteSensitiveWordApi(row.id)
    ElMessage.success('已删除，词库已刷新')
    loadData()
  } catch {
    // 取消
  }
}

async function handleRefresh() {
  try {
    await refreshSensitiveWordApi()
    ElMessage.success('词库刷新成功')
  } catch {
    // 错误已由拦截器处理
  }
}

onMounted(() => {
  loadData()
})
</script>