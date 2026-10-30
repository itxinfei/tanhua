<template>
  <div class="page-container">
    <!-- 搜索栏 -->
    <div class="search-bar">
      <el-form :inline="true" :model="query" @submit.prevent>
        <el-form-item label="分类">
          <el-select v-model="query.category" placeholder="全部分类" clearable style="width: 140px">
            <el-option v-for="cat in categories" :key="cat" :label="cat" :value="cat" />
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
          <el-button v-perm="['SUPER_ADMIN','ADMIN']" type="success" :icon="Plus" @click="openCreate">新增FAQ</el-button>
        </el-form-item>
      </el-form>
    </div>

    <!-- 表格 -->
    <div class="table-container">
      <el-table :data="tableData" v-loading="loading" stripe border style="width: 100%" empty-text="暂无FAQ数据">
        <el-table-column prop="id" label="ID" width="70" align="center" />
        <el-table-column prop="category" label="分类" width="110" align="center">
          <template #default="{ row }">
            <el-tag size="small" type="primary">{{ row.category || '未分类' }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="question" label="问题" min-width="200" show-overflow-tooltip />
        <el-table-column prop="answer" label="回答" min-width="260" show-overflow-tooltip />
        <el-table-column prop="sort" label="排序" width="80" align="center" />
        <el-table-column label="状态" width="90" align="center">
          <template #default="{ row }">
            <el-switch
              :model-value="row.status === 1"
              @change="(val: boolean) => handleToggle(row, val)"
            />
          </template>
        </el-table-column>
        <el-table-column label="操作" width="130" fixed="right" align="center">
          <template #default="{ row }">
            <el-button v-perm="['SUPER_ADMIN','ADMIN']" link type="primary" size="small" @click="openEdit(row)">编辑</el-button>
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
    <el-dialog v-model="formVisible" :title="formData.id ? '编辑FAQ' : '新增FAQ'" width="560px">
      <el-form ref="formRef" :model="formData" :rules="formRules" label-width="80px">
        <el-form-item label="分类" prop="category">
          <el-select v-model="formData.category" placeholder="选择分类" style="width: 100%">
            <el-option v-for="cat in categories" :key="cat" :label="cat" :value="cat" />
          </el-select>
        </el-form-item>
        <el-form-item label="问题" prop="question">
          <el-input v-model="formData.question" maxlength="200" show-word-limit placeholder="FAQ问题（不可与其他问题重复）" />
        </el-form-item>
        <el-form-item label="回答" prop="answer">
          <el-input v-model="formData.answer" type="textarea" :rows="5" maxlength="1000" show-word-limit placeholder="FAQ回答内容" />
        </el-form-item>
        <el-form-item label="排序" prop="sort">
          <el-input-number v-model="formData.sort" :min="0" :max="9999" />
          <span class="sort-tip">数字越小越靠前</span>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="formVisible = false">取消</el-button>
        <el-button type="primary" :loading="formSubmitting" @click="submitForm">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { ElMessage, ElMessageBox, type FormInstance, type FormRules } from 'element-plus'
import { Search, Refresh, Plus } from '@element-plus/icons-vue'
import {
  getFaqPageApi,
  createFaqApi,
  updateFaqApi,
  toggleFaqApi,
  deleteFaqApi,
} from '@/api/kefu'
import type { FaqItem, FaqQuery, FaqForm } from '@/api/kefu'

/** 分类选项（与 init-kefu.sql 种子数据保持一致） */
const categories = ['账号安全', '会员权益', '匹配交友', '聊天互动', '举报投诉']

const loading = ref(false)
const tableData = ref<FaqItem[]>([])
const total = ref(0)

const query = reactive<FaqQuery>({
  page: 1,
  pageSize: 10,
  category: undefined,
  status: undefined,
})

async function loadData() {
  loading.value = true
  try {
    const res = await getFaqPageApi(query)
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
  query.category = undefined
  query.status = undefined
  query.page = 1
  loadData()
}

/** 切换每页条数时回到第 1 页 */
function handleSizeChange() {
  query.page = 1
  loadData()
}

// 启停
async function handleToggle(row: FaqItem, val: boolean) {
  const next = val ? 1 : 0
  try {
    await toggleFaqApi(row.id, next)
    row.status = next
    ElMessage.success(next === 1 ? '已启用' : '已停用')
  } catch {
    // 错误已由拦截器处理，switch 状态由 model-value 驱动自动回退
  }
}

// 删除
async function handleDelete(row: FaqItem) {
  try {
    await ElMessageBox.confirm(`确认删除问题「${row.question}」？`, '提示', {
      type: 'warning',
      confirmButtonText: '确认删除',
      cancelButtonText: '取消',
    })
    await deleteFaqApi(row.id)
    ElMessage.success('删除成功')
    loadData()
  } catch {
    // 用户取消或请求失败，均无需额外处理
  }
}

// 新增/编辑
const formVisible = ref(false)
const formSubmitting = ref(false)
const formRef = ref<FormInstance>()
const formData = reactive<FaqForm>({
  id: undefined,
  category: '',
  question: '',
  answer: '',
  sort: 0,
})

const formRules: FormRules = {
  question: [{ required: true, message: '请输入问题', trigger: 'blur' }],
  answer: [{ required: true, message: '请输入回答', trigger: 'blur' }],
}

function openCreate() {
  formData.id = undefined
  formData.category = ''
  formData.question = ''
  formData.answer = ''
  formData.sort = 0
  formVisible.value = true
}

function openEdit(row: FaqItem) {
  formData.id = row.id
  formData.category = row.category || ''
  formData.question = row.question
  formData.answer = row.answer
  formData.sort = row.sort
  formVisible.value = true
}

async function submitForm() {
  if (!formRef.value) return
  await formRef.value.validate(async (valid) => {
    if (!valid) return
    formSubmitting.value = true
    try {
      if (formData.id) {
        await updateFaqApi(formData.id, formData)
        ElMessage.success('编辑成功')
      } else {
        await createFaqApi(formData)
        ElMessage.success('新增成功')
      }
      formVisible.value = false
      loadData()
    } catch {
      // 错误已由拦截器处理（含问题重复等业务错误）
    } finally {
      formSubmitting.value = false
    }
  })
}

onMounted(() => {
  loadData()
})
</script>

<style scoped>
.sort-tip {
  margin-left: 10px;
  font-size: 12px;
  color: #999;
}
</style>