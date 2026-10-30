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
          <el-button v-perm="['SUPER_ADMIN','ADMIN']" type="warning" :icon="Upload" @click="handleBatchImport">批量导入</el-button>
          <el-button v-perm="['SUPER_ADMIN','ADMIN']" :icon="Download" @click="handleExport">导出</el-button>
          <el-button v-perm="['SUPER_ADMIN','ADMIN']" type="danger" :icon="RefreshRight" @click="handleRefresh">刷新词库</el-button>
        </el-form-item>
      </el-form>
    </div>

    <!-- 命中测试 -->
    <div class="test-box">
      <el-input
        v-model="testText"
        type="textarea"
        :rows="3"
        placeholder="输入待检测文本进行敏感词命中测试"
        style="flex: 1"
      />
      <el-button type="primary" :icon="Promotion" @click="handleTest" style="margin-left: 12px">测试</el-button>
    </div>
    <div v-if="testResult" class="test-result">
      <el-alert :type="testResult.hitWords.length > 0 ? 'warning' : 'success'" :closable="false">
        <template #title>
          <div v-if="testResult.hitWords.length > 0">
            <div>命中 {{ testResult.hitWords.length }} 个敏感词，最高级别：{{ levelLabel(testResult.maxLevel) }}</div>
            <div style="margin-top: 4px">
              <el-tag v-for="word in testResult.hitWords" :key="word" size="small" style="margin-right: 6px">{{ word }}</el-tag>
            </div>
          </div>
          <div v-else>未命中敏感词</div>
        </template>
        <div v-if="testResult.filteredText !== testText" style="margin-top: 8px; color: #909399">
          过滤后文本：{{ testResult.filteredText || '(空)' }}
        </div>
      </el-alert>
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

    <!-- 批量导入弹窗 -->
    <el-dialog v-model="batchImportVisible" title="批量导入敏感词" width="560px">
      <el-form label-width="80px">
        <el-form-item label="词条列表">
          <el-input
            v-model="batchImportText"
            type="textarea"
            :rows="8"
            placeholder="每行一个敏感词，或用逗号/空格分隔"
          />
          <div class="batch-import-tip">支持换行、逗号、空格分隔，自动去重</div>
        </el-form-item>
        <el-form-item label="分类">
          <el-select v-model="batchImportCategory" placeholder="请选择分类" style="width: 100%">
            <el-option label="政治" value="POLITICS" />
            <el-option label="色情" value="PORN" />
            <el-option label="暴力" value="VIOLENCE" />
            <el-option label="广告" value="AD" />
            <el-option label="其他" value="OTHER" />
          </el-select>
        </el-form-item>
        <el-form-item label="级别">
          <el-radio-group v-model="batchImportLevel">
            <el-radio :value="1">高危（拦截）</el-radio>
            <el-radio :value="2">中危（人工审核）</el-radio>
            <el-radio :value="3">低危（过滤替换）</el-radio>
          </el-radio-group>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="batchImportVisible = false">取消</el-button>
        <el-button type="primary" @click="confirmBatchImport">导入</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import type { FormInstance, FormRules } from 'element-plus'
import { Search, Refresh, Plus, RefreshRight, Upload, Download, Promotion } from '@element-plus/icons-vue'
import {
  getSensitiveWordPageApi,
  createSensitiveWordApi,
  updateSensitiveWordApi,
  toggleSensitiveWordApi,
  deleteSensitiveWordApi,
  refreshSensitiveWordApi,
  testSensitiveWordApi,
  batchImportSensitiveWordsApi,
  exportSensitiveWordsApi,
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

// 命中测试
const testText = ref('')
const testResult = ref<{
  action: string
  filteredText: string
  hitWords: string[]
  maxLevel: number
} | null>(null)

async function handleTest() {
  if (!testText.value.trim()) {
    ElMessage.warning('请输入待检测文本')
    return
  }
  try {
    testResult.value = await testSensitiveWordApi(testText.value)
  } catch {
    // 错误已由拦截器处理
  }
}

// 批量导入
const batchImportVisible = ref(false)
const batchImportText = ref('')
const batchImportCategory = ref('OTHER')
const batchImportLevel = ref(3)

function handleBatchImport() {
  batchImportText.value = ''
  batchImportCategory.value = 'OTHER'
  batchImportLevel.value = 3
  batchImportVisible.value = true
}

async function confirmBatchImport() {
  // 按换行、逗号、空格拆分，过滤空字符串并去重
  const words = [...new Set(
    batchImportText.value
      .split(/[\n,，\s]+/)
      .map((w) => w.trim())
      .filter((w) => w.length > 0)
  )]

  if (words.length === 0) {
    ElMessage.warning('请输入敏感词，每行一个或用逗号/空格分隔')
    return
  }

  try {
    const res = await batchImportSensitiveWordsApi(words, batchImportCategory.value, batchImportLevel.value)
    ElMessage.success(`导入完成：成功 ${res.success} 个，失败 ${res.failed} 个`)
    batchImportVisible.value = false
    loadData()
  } catch {
    // 错误已由拦截器处理
  }
}

// 导出
async function handleExport() {
  try {
    const blob = await exportSensitiveWordsApi()
    const url = window.URL.createObjectURL(blob)
    const link = document.createElement('a')
    link.href = url
    link.download = `sensitive-words-${new Date().toISOString().slice(0, 10)}.csv`
    link.click()
    window.URL.revokeObjectURL(url)
    ElMessage.success('导出成功')
  } catch {
    ElMessage.error('导出失败')
  }
}

onMounted(() => {
  loadData()
})
</script>

<style scoped>
.test-box {
  display: flex;
  margin-bottom: 12px;
}

.test-result {
  margin-bottom: 16px;
}

.batch-import-tip {
  font-size: 12px;
  color: #909399;
  margin-top: 4px;
}
</style>