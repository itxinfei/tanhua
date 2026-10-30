<template>
  <div class="page-container">
    <!-- 搜索栏 -->
    <div class="search-bar">
      <el-input
        v-model="searchKeyword"
        placeholder="搜索用户名或昵称"
        clearable
        style="width: 240px"
        @keyup.enter="handleSearch"
      />
      <el-button type="primary" @click="handleSearch">查询</el-button>
      <el-button @click="handleReset">重置</el-button>
      <el-button
        v-if="isSuperAdmin"
        type="success"
        @click="openCreateDialog"
      >
        <el-icon><Plus /></el-icon>&nbsp;新增管理员
      </el-button>
    </div>

    <!-- 表格 -->
    <el-table v-loading="loading" :data="tableData" border stripe>
      <el-table-column label="ID" prop="id" width="70" align="center" />
      <el-table-column label="用户名" prop="username" width="140" />
      <el-table-column label="昵称" prop="nickname" width="140" />
      <el-table-column label="角色" width="120" align="center">
        <template #default="{ row }">
          <el-tag :type="roleTagType(row.role)" size="small">
            {{ roleLabel(row.role) }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="状态" width="90" align="center">
        <template #default="{ row }">
          <el-tag :type="row.status === 'NORMAL' ? 'success' : 'danger'" size="small">
            {{ row.status === 'NORMAL' ? '启用' : '禁用' }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="创建时间" prop="created" width="170" />
      <el-table-column label="操作" width="240" fixed="right" align="center">
        <template #default="{ row }">
          <template v-if="isSuperAdmin">
            <el-button type="primary" link size="small" @click="openEditDialog(row)">编辑</el-button>
            <el-button
              v-if="row.role !== 'SUPER_ADMIN'"
              :type="row.status === 'NORMAL' ? 'warning' : 'success'"
              link
              size="small"
              @click="handleToggleStatus(row)"
            >
              {{ row.status === 'NORMAL' ? '禁用' : '启用' }}
            </el-button>
            <el-button
              v-if="row.role !== 'SUPER_ADMIN'"
              type="danger"
              link
              size="small"
              @click="handleResetPassword(row)"
            >
              重置密码
            </el-button>
          </template>
          <span v-else class="no-op">仅超管可操作</span>
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
        @size-change="loadData"
        @current-change="loadData"
      />
    </div>

    <!-- 新增弹窗 -->
    <el-dialog v-model="createVisible" title="新增管理员" width="440px" destroy-on-close>
      <el-form ref="createFormRef" :model="createForm" :rules="createRules" label-width="80px">
        <el-form-item label="用户名" prop="username">
          <el-input v-model="createForm.username" placeholder="登录用户名" />
        </el-form-item>
        <el-form-item label="密码" prop="password">
          <el-input v-model="createForm.password" type="password" show-password placeholder="至少6位" />
        </el-form-item>
        <el-form-item label="昵称" prop="nickname">
          <el-input v-model="createForm.nickname" placeholder="显示昵称" />
        </el-form-item>
        <el-form-item label="角色" prop="role">
          <el-select v-model="createForm.role" style="width: 100%">
            <el-option label="管理员" value="ADMIN" />
            <el-option label="运营" value="OPERATOR" />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="createVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="handleCreate">确定</el-button>
      </template>
    </el-dialog>

    <!-- 编辑弹窗 -->
    <el-dialog v-model="editVisible" title="编辑管理员" width="440px" destroy-on-close>
      <el-form ref="editFormRef" :model="editForm" :rules="editRules" label-width="80px">
        <el-form-item label="昵称" prop="nickname">
          <el-input v-model="editForm.nickname" placeholder="显示昵称" />
        </el-form-item>
        <el-form-item label="角色" prop="role">
          <el-select v-model="editForm.role" style="width: 100%">
            <el-option label="管理员" value="ADMIN" />
            <el-option label="运营" value="OPERATOR" />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="editVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="handleEdit">确定</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox, type FormInstance, type FormRules } from 'element-plus'
import {
  getAdminPageApi,
  createAdminApi,
  updateAdminApi,
  toggleAdminStatusApi,
  resetAdminPasswordApi
} from '@/api/admin'
import type { AdminAccount } from '@/api/admin'
import { useAuthStore } from '@/stores/auth'

const authStore = useAuthStore()
const isSuperAdmin = computed(() => authStore.adminInfo?.role === 'SUPER_ADMIN')

const loading = ref(false)
const submitting = ref(false)
const tableData = ref<AdminAccount[]>([])
const total = ref(0)
const page = ref(1)
const pageSize = ref(10)
const searchKeyword = ref('')

const createVisible = ref(false)
const editVisible = ref(false)
const createFormRef = ref<FormInstance>()
const editFormRef = ref<FormInstance>()

const createForm = reactive({ username: '', password: '', nickname: '', role: 'ADMIN' })
const editForm = reactive({ id: 0, nickname: '', role: 'ADMIN', avatar: '' })

const createRules: FormRules = {
  username: [{ required: true, message: '请输入用户名', trigger: 'blur' }],
  password: [
    { required: true, message: '请输入密码', trigger: 'blur' },
    { min: 6, message: '密码至少6位', trigger: 'blur' }
  ],
  nickname: [{ required: true, message: '请输入昵称', trigger: 'blur' }],
  role: [{ required: true, message: '请选择角色', trigger: 'change' }]
}

const editRules: FormRules = {
  nickname: [{ required: true, message: '请输入昵称', trigger: 'blur' }],
  role: [{ required: true, message: '请选择角色', trigger: 'change' }]
}

async function loadData() {
  loading.value = true
  try {
    const params: any = { page: page.value, pageSize: pageSize.value }
    if (searchKeyword.value.trim()) params.keyword = searchKeyword.value.trim()
    const res = await getAdminPageApi(params)
    tableData.value = res.list || []
    total.value = res.total || 0
  } catch {
    // 拦截器已提示
  } finally {
    loading.value = false
  }
}

function handleSearch() {
  page.value = 1
  loadData()
}

function handleReset() {
  searchKeyword.value = ''
  page.value = 1
  loadData()
}

function openCreateDialog() {
  createForm.username = ''
  createForm.password = ''
  createForm.nickname = ''
  createForm.role = 'ADMIN'
  createVisible.value = true
}

async function handleCreate() {
  const valid = await createFormRef.value?.validate().catch(() => false)
  if (!valid) return
  submitting.value = true
  try {
    await createAdminApi({ ...createForm })
    ElMessage.success('管理员已创建')
    createVisible.value = false
    loadData()
  } catch {
    // 拦截器已提示
  } finally {
    submitting.value = false
  }
}

function openEditDialog(row: AdminAccount) {
  editForm.id = row.id
  editForm.nickname = row.nickname
  editForm.role = row.role
  editForm.avatar = row.avatar || ''
  editVisible.value = true
}

async function handleEdit() {
  const valid = await editFormRef.value?.validate().catch(() => false)
  if (!valid) return
  submitting.value = true
  try {
    await updateAdminApi(editForm.id, {
      nickname: editForm.nickname,
      role: editForm.role,
      avatar: editForm.avatar
    })
    ElMessage.success('管理员已更新')
    editVisible.value = false
    loadData()
  } catch {
    // 拦截器已提示
  } finally {
    submitting.value = false
  }
}

async function handleToggleStatus(row: AdminAccount) {
  const nextStatus = row.status === 'NORMAL' ? 'DISABLED' : 'NORMAL'
  const actionText = nextStatus === 'DISABLED' ? '禁用' : '启用'
  try {
    await ElMessageBox.confirm(
      `确定要${actionText}管理员「${row.username}」吗？`,
      `${actionText}确认`,
      { confirmButtonText: `确定${actionText}`, cancelButtonText: '取消', type: 'warning' }
    )
    await toggleAdminStatusApi(row.id, nextStatus)
    ElMessage.success(`已${actionText}`)
    loadData()
  } catch {
    // 取消
  }
}

async function handleResetPassword(row: AdminAccount) {
  try {
    const { value } = await ElMessageBox.prompt(
      `请输入管理员「${row.username}」的新密码（至少6位）`,
      '重置密码',
      {
        confirmButtonText: '确定重置',
        cancelButtonText: '取消',
        inputPattern: /^.{6,}$/,
        inputErrorMessage: '密码至少6位',
        inputType: 'password'
      }
    )
    await resetAdminPasswordApi(row.id, value)
    ElMessage.success('密码已重置')
  } catch {
    // 取消
  }
}

function roleLabel(role: string): string {
  const map: Record<string, string> = {
    SUPER_ADMIN: '超级管理员',
    ADMIN: '管理员',
    OPERATOR: '运营'
  }
  return map[role] || role
}

function roleTagType(role: string): 'danger' | 'primary' | 'info' {
  if (role === 'SUPER_ADMIN') return 'danger'
  if (role === 'ADMIN') return 'primary'
  return 'info'
}

onMounted(() => {
  authStore.restoreAdminInfo()
  loadData()
})
</script>

<style scoped>
.search-bar {
  display: flex;
  gap: 12px;
  margin-bottom: 16px;
}

.no-op {
  font-size: 12px;
  color: #c0c4cc;
}

.pagination-bar {
  display: flex;
  justify-content: flex-end;
  margin-top: 16px;
}
</style>