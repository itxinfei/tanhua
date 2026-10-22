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
        <el-form-item>
          <el-button type="primary" :icon="Search" @click="handleSearch">查询</el-button>
          <el-button :icon="Refresh" @click="handleReset">重置</el-button>
        </el-form-item>
      </el-form>
    </div>

    <!-- 表格 -->
    <div class="table-container">
      <el-table :data="tableData" v-loading="loading" stripe border style="width: 100%">
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
            <el-button v-if="row.status === 'NORMAL'" link type="danger" size="small" @click="handleFreeze(row)">封禁</el-button>
            <el-button v-else link type="success" size="small" @click="handleUnfreeze(row)">解封</el-button>
            <el-button link type="warning" size="small" @click="handleResetPwd(row)">重置密码</el-button>
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
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Search, Refresh } from '@element-plus/icons-vue'
import { getUserPageApi, freezeUserApi, unfreezeUserApi, resetUserPasswordApi } from '@/api/user'
import type { UserItem, UserQuery } from '@/api/user'

const router = useRouter()
const loading = ref(false)
const tableData = ref<UserItem[]>([])
const total = ref(0)

const query = reactive<UserQuery>({
  page: 1,
  pageSize: 10,
  keyword: '',
  gender: '',
  status: '',
})

// Mock 数据
function getMockUsers(page: number, pageSize: number): { total: number; list: UserItem[] } {
  const cities = ['北京', '上海', '广州', '深圳', '杭州', '成都', '武汉', '南京']
  const nicknames = ['小鹿乱撞', '星辰大海', '柠檬精', '温柔的风', '夜空守望', '甜甜圈', '流浪猫', '月光宝盒', '向日葵', '北极星', '暖阳', '清欢', '南风知我意', '一叶知秋', '夏至未至', '半夏时光', '浅笑安然', '岁月静好', '浮生若梦', '烟雨江南']
  const list: UserItem[] = []
  const start = (page - 1) * pageSize
  for (let i = 0; i < pageSize; i++) {
    const idx = start + i
    if (idx >= 200) break
    const gender = idx % 3 === 0 ? '女' : '男'
    list.push({
      id: 10001 + idx,
      mobile: `138${String(10000000 + idx * 137).slice(0, 8)}`,
      nickname: nicknames[idx % nicknames.length] + (idx > 19 ? idx : ''),
      avatar: '',
      gender,
      age: 18 + (idx % 20),
      city: cities[idx % cities.length],
      status: idx % 15 === 0 ? 'FROZEN' : 'NORMAL',
      vipLevel: idx % 4 === 0 ? (idx % 3) + 1 : 0,
      createdAt: `2026-${String((idx % 10) + 1).padStart(2, '0')}-${String((idx % 28) + 1).padStart(2, '0')} 10:${String(idx % 60).padStart(2, '0')}:00`,
      lastActiveAt: `2026-10-${String((idx % 22) + 1).padStart(2, '0')} 15:30:00`,
    })
  }
  return { total: 200, list }
}

async function loadData() {
  loading.value = true
  try {
    const res = await getUserPageApi(query)
    tableData.value = res.list
    total.value = res.total
  } catch {
    const mock = getMockUsers(query.page, query.pageSize)
    tableData.value = mock.list
    total.value = mock.total
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

function goDetail(id: number) {
  router.push(`/user/detail/${id}`)
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
  } catch {
    ElMessage.success('已封禁（开发模式）')
  }
  freezeTarget.value.status = 'FROZEN'
  freezeDialogVisible.value = false
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

onMounted(() => {
  loadData()
})
</script>

<style scoped>
.text-muted {
  color: #c0c4cc;
}
</style>