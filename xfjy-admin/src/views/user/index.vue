<template>
  <div class="page-container">
    <!-- 搜索栏 -->
    <div class="search-bar">
      <el-form :inline="true" :model="query" @submit.prevent>
        <el-form-item label="关键词">
          <el-input v-model="query.keyword" placeholder="手机号/昵称" clearable style="width: 180px" @keyup.enter="handleSearch" />
        </el-form-item>
        <el-form-item label="性别">
          <el-select v-model="query.gender" placeholder="全部" clearable style="width: 100px">
            <el-option label="男" value="男" />
            <el-option label="女" value="女" />
          </el-select>
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="query.status" placeholder="全部" clearable style="width: 120px">
            <el-option label="正常" value="NORMAL" />
            <el-option label="已封禁" value="FROZEN" />
          </el-select>
        </el-form-item>
        <el-form-item label="注册时间">
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
        <el-form-item>
          <el-button type="primary" :icon="Search" @click="handleSearch">查询</el-button>
          <el-button :icon="Refresh" @click="handleReset">重置</el-button>
        </el-form-item>
      </el-form>
    </div>

    <!-- 批量操作栏 -->
    <div class="batch-bar" v-if="selectedRows.length > 0">
      <span class="batch-count">已选 {{ selectedRows.length }} 项</span>
      <el-button v-perm="['SUPER_ADMIN','ADMIN']" type="danger" size="small" @click="handleBatchFreeze">批量封禁</el-button>
      <el-button v-perm="['SUPER_ADMIN','ADMIN']" type="success" size="small" @click="handleBatchUnfreeze">批量解封</el-button>
      <el-button size="small" @click="selectedRows = []">取消选择</el-button>
    </div>

    <!-- 表格 -->
    <div class="table-container">
      <el-table :data="tableData" v-loading="loading" stripe border style="width: 100%" @selection-change="handleSelectionChange">
        <el-table-column type="selection" width="50" align="center" :selectable="() => true" />
        <el-table-column type="index" label="#" width="50" align="center" />
        <el-table-column label="头像" width="70" align="center">
          <template #default="{ row }">
            <el-avatar :size="36" :src="row.avatar">
              <el-icon><UserFilled /></el-icon>
            </el-avatar>
          </template>
        </el-table-column>
        <el-table-column prop="nickname" label="昵称" min-width="120" show-overflow-tooltip />
        <el-table-column prop="mobile" label="手机号" width="130" />
        <el-table-column prop="gender" label="性别" width="70" align="center">
          <template #default="{ row }">
            <el-tag :type="row.gender === '女' ? 'danger' : 'primary'" size="small">{{ row.gender }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="age" label="年龄" width="70" align="center" />
        <el-table-column prop="city" label="城市" width="90" />
        <el-table-column label="VIP" width="70" align="center">
          <template #default="{ row }">
            <el-tag v-if="row.vipLevel > 0" type="warning" size="small">V{{ row.vipLevel }}</el-tag>
            <span v-else class="text-muted">-</span>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="90" align="center">
          <template #default="{ row }">
            <el-tag :type="row.status === 'NORMAL' ? 'success' : 'danger'" size="small">
              {{ row.status === 'NORMAL' ? '正常' : '已封禁' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="createdAt" label="注册时间" width="170" />
        <el-table-column prop="lastActiveAt" label="最后活跃" width="170" />
        <el-table-column label="操作" width="220" fixed="right" align="center">
          <template #default="{ row }">
            <el-button link type="primary" size="small" @click="goDetail(row.id)">详情</el-button>
            <el-button v-if="row.status === 'NORMAL'" v-perm="['SUPER_ADMIN','ADMIN']" link type="danger" size="small" @click="handleFreeze(row)">封禁</el-button>
            <el-button v-else v-perm="['SUPER_ADMIN','ADMIN']" link type="success" size="small" @click="handleUnfreeze(row)">解封</el-button>
            <el-button v-perm="['SUPER_ADMIN','ADMIN']" link type="warning" size="small" @click="handleResetPwd(row)">重置密码</el-button>
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

    <!-- 封禁弹窗 -->
    <el-dialog v-model="freezeDialogVisible" title="封禁用户" width="440px">
      <el-form label-width="80px">
        <el-form-item label="用户">{{ freezeTarget?.nickname }}（{{ freezeTarget?.mobile }}）</el-form-item>
        <el-form-item label="封禁原因">
          <el-select v-model="freezeReason" placeholder="选择或输入封禁原因" filterable allow-create style="width: 100%">
            <el-option label="发布违规内容" value="发布违规内容" />
            <el-option label="涉嫌欺诈" value="涉嫌欺诈" />
            <el-option label="骚扰其他用户" value="骚扰其他用户" />
            <el-option label="恶意注册" value="恶意注册" />
            <el-option label="其他" value="其他" />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="freezeDialogVisible = false">取消</el-button>
        <el-button type="danger" @click="confirmFreeze">确认封禁</el-button>
      </template>
    </el-dialog>

    <!-- 批量封禁弹窗 -->
    <el-dialog v-model="batchFreezeDialogVisible" title="批量封禁用户" width="440px">
      <el-form label-width="80px">
        <el-form-item label="数量">
          <span>{{ selectedRows.length }} 个用户</span>
        </el-form-item>
        <el-form-item label="封禁原因">
          <el-select v-model="batchFreezeReason" placeholder="选择或输入封禁原因" filterable allow-create style="width: 100%">
            <el-option label="发布违规内容" value="发布违规内容" />
            <el-option label="涉嫌欺诈" value="涉嫌欺诈" />
            <el-option label="骚扰其他用户" value="骚扰其他用户" />
            <el-option label="恶意注册" value="恶意注册" />
            <el-option label="其他" value="其他" />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="batchFreezeDialogVisible = false">取消</el-button>
        <el-button type="danger" @click="confirmBatchFreeze">确认封禁</el-button>
      </template>
    </el-dialog>

    <!-- 用户详情抽屉 -->
    <UserDetailDrawer v-model="detailVisible" :user-id="detailUserId" />
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Search, Refresh } from '@element-plus/icons-vue'
import { getUserPageApi, freezeUserApi, unfreezeUserApi, resetUserPasswordApi } from '@/api/user'
import type { UserItem, UserQuery } from '@/api/user'
import request from '@/api/request'
import UserDetailDrawer from './detail.vue'

const route = useRoute()
const loading = ref(false)
const tableData = ref<UserItem[]>([])
const total = ref(0)
const selectedRows = ref<UserItem[]>([])
const dateRange = ref<[string, string] | null>(null)

const query = reactive<UserQuery>({
  page: 1,
  pageSize: 10,
  keyword: '',
  gender: '',
  status: '',
  startDate: '',
  endDate: '',
})

function handleDateChange(val: [string, string] | null) {
  if (val) {
    query.startDate = val[0]
    query.endDate = val[1]
  } else {
    query.startDate = ''
    query.endDate = ''
  }
}

function handleSelectionChange(rows: UserItem[]) {
  selectedRows.value = rows
}

async function loadData() {
  loading.value = true
  try {
    const res = await getUserPageApi(query)
    tableData.value = res.list
    total.value = res.total
  } catch {
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
  query.keyword = ''
  query.gender = ''
  query.status = ''
  query.page = 1
  loadData()
}

/** 切换每页条数时回到第 1 页，避免停留在不存在的页码 */
function handleSizeChange() {
  query.page = 1
  loadData()
}

// 详情抽屉
const detailVisible = ref(false)
const detailUserId = ref(0)

function goDetail(id: number) {
  detailUserId.value = id
  detailVisible.value = true
}

// 封禁
const freezeDialogVisible = ref(false)
const freezeTarget = ref<UserItem | null>(null)
const freezeReason = ref('')

function handleFreeze(row: UserItem) {
  freezeTarget.value = row
  freezeReason.value = ''
  freezeDialogVisible.value = true
}

async function confirmFreeze() {
  if (!freezeReason.value) {
    ElMessage.warning('请选择或输入封禁原因')
    return
  }
  if (!freezeTarget.value) return
  try {
    await freezeUserApi(freezeTarget.value.id, freezeReason.value)
    ElMessage.success('已封禁')
    freezeTarget.value.status = 'FROZEN'
    freezeDialogVisible.value = false
  } catch {
    // 封禁失败：不提示成功、不改本地状态、不关弹窗，让用户重试
    ElMessage.error('封禁失败，请稍后重试')
  }
}

async function handleUnfreeze(row: UserItem) {
  try {
    await ElMessageBox.confirm(`确定解封用户「${row.nickname}」吗？`, '提示', { type: 'warning' })
    await unfreezeUserApi(row.id)
    ElMessage.success('已解封')
    row.status = 'NORMAL'
  } catch {
    // 取消
  }
}

async function handleResetPwd(row: UserItem) {
  try {
    await ElMessageBox.confirm(`确定重置用户「${row.nickname}」的密码吗？`, '提示', { type: 'warning' })
    await resetUserPasswordApi(row.id)
    ElMessage.success('密码已重置为默认密码')
  } catch {
    // 取消
  }
}

// 批量封禁
const batchFreezeDialogVisible = ref(false)
const batchFreezeReason = ref('')

function handleBatchFreeze() {
  batchFreezeReason.value = ''
  batchFreezeDialogVisible.value = true
}

async function confirmBatchFreeze() {
  if (!batchFreezeReason.value) {
    ElMessage.warning('请选择或输入封禁原因')
    return
  }
  const ids = selectedRows.value.map(r => r.id)
  try {
    const res: any = await request.put('/admin/users/batch-freeze', { userIds: ids, reason: batchFreezeReason.value })
    const { success, failed } = res
    ElMessage.success(`批量封禁完成：成功 ${success}，失败 ${failed}`)
    batchFreezeDialogVisible.value = false
    selectedRows.value = []
    loadData()
  } catch {
    ElMessage.error('批量封禁失败')
  }
}

async function handleBatchUnfreeze() {
  const ids = selectedRows.value.map(r => r.id)
  try {
    await ElMessageBox.confirm(`确定解封选中的 ${ids.length} 个用户吗？`, '提示', { type: 'warning' })
    const res: any = await request.put('/admin/users/batch-unfreeze', { userIds: ids })
    const { success, failed } = res
    ElMessage.success(`批量解封完成：成功 ${success}，失败 ${failed}`)
    selectedRows.value = []
    loadData()
  } catch {
    // 取消
  }
}

onMounted(() => {
  loadData()
  // 从举报处理跳转过来时自动打开详情抽屉
  const detailUserIdParam = route.query.detailUserId
  if (detailUserIdParam) {
    const id = Number(detailUserIdParam)
    if (id) {
      detailUserId.value = id
      detailVisible.value = true
    }
  }
})
</script>

<style scoped>
.text-muted {
  color: #c0c4cc;
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
</style>