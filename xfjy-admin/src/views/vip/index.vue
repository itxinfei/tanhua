<template>
  <div class="page-container">
    <el-tabs v-model="activeTab">
      <!-- 套餐管理 -->
      <el-tab-pane label="VIP 套餐" name="package">
        <div class="search-bar">
          <el-form :inline="true" :model="pkgQuery" @submit.prevent>
            <el-form-item label="名称">
              <el-input v-model="pkgQuery.name" placeholder="套餐名称" clearable style="width: 160px" @keyup.enter="loadPackages" />
            </el-form-item>
            <el-form-item label="状态">
              <el-select v-model="pkgQuery.status" placeholder="全部" clearable style="width: 110px">
                <el-option label="上架" :value="1" />
                <el-option label="下架" :value="0" />
              </el-select>
            </el-form-item>
            <el-form-item>
              <el-button type="primary" :icon="Search" @click="loadPackages">查询</el-button>
              <el-button :icon="Refresh" @click="resetPkgQuery">重置</el-button>
              <el-button type="success" :icon="Plus" @click="handlePkgAdd">新增套餐</el-button>
            </el-form-item>
          </el-form>
        </div>

        <div class="table-container">
          <el-table :data="pkgData" v-loading="pkgLoading" stripe border style="width: 100%">
            <el-table-column prop="id" label="ID" width="60" align="center" />
            <el-table-column prop="name" label="套餐名称" width="120" />
            <el-table-column label="等级" width="80" align="center">
              <template #default="{ row }">{{ levelLabel(row.level) }}</template>
            </el-table-column>
            <el-table-column label="售价" width="90" align="right">
              <template #default="{ row }">¥{{ formatAmount(row.price) }}</template>
            </el-table-column>
            <el-table-column label="原价" width="90" align="right">
              <template #default="{ row }">{{ row.originalPrice ? '¥' + formatAmount(row.originalPrice) : '-' }}</template>
            </el-table-column>
            <el-table-column prop="durationDays" label="有效天数" width="90" align="center" />
            <el-table-column label="状态" width="80" align="center">
              <template #default="{ row }">
                <el-tag :type="row.status === 1 ? 'success' : 'info'" size="small">{{ row.status === 1 ? '上架' : '下架' }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="sort" label="排序" width="70" align="center" />
            <el-table-column label="操作" width="160" fixed="right" align="center">
              <template #default="{ row }">
                <el-button link type="primary" size="small" @click="handlePkgEdit(row)">编辑</el-button>
                <el-button v-if="row.status === 1" link type="warning" size="small" @click="handlePkgToggle(row, 0)">下架</el-button>
                <el-button v-else link type="success" size="small" @click="handlePkgToggle(row, 1)">上架</el-button>
              </template>
            </el-table-column>
          </el-table>

          <div class="pagination-wrapper">
            <el-pagination
              v-model:current-page="pkgQuery.page"
              v-model:page-size="pkgQuery.pageSize"
              :total="pkgTotal"
              :page-sizes="[10, 20, 50]"
              layout="total, sizes, prev, pager, next, jumper"
              @size-change="loadPackages"
              @current-change="loadPackages"
            />
          </div>
        </div>
      </el-tab-pane>

      <!-- 权益管理 -->
      <el-tab-pane label="增值权益" name="benefit">
        <div class="search-bar">
          <el-form :inline="true" :model="benefitQuery" @submit.prevent>
            <el-form-item label="名称">
              <el-input v-model="benefitQuery.name" placeholder="权益名称" clearable style="width: 160px" @keyup.enter="loadBenefits" />
            </el-form-item>
            <el-form-item label="状态">
              <el-select v-model="benefitQuery.status" placeholder="全部" clearable style="width: 110px">
                <el-option label="启用" :value="1" />
                <el-option label="停用" :value="0" />
              </el-select>
            </el-form-item>
            <el-form-item>
              <el-button type="primary" :icon="Search" @click="loadBenefits">查询</el-button>
              <el-button :icon="Refresh" @click="resetBenefitQuery">重置</el-button>
            </el-form-item>
          </el-form>
        </div>

        <div class="table-container">
          <el-table :data="benefitData" v-loading="benefitLoading" stripe border style="width: 100%">
            <el-table-column prop="id" label="ID" width="60" align="center" />
            <el-table-column prop="serviceCode" label="权益码" width="160" show-overflow-tooltip />
            <el-table-column prop="name" label="权益名称" width="140" />
            <el-table-column label="所需等级" width="100" align="center">
              <template #default="{ row }">{{ levelLabel(row.vipLevelRequired) }}</template>
            </el-table-column>
            <el-table-column label="每日限制" width="100" align="center">
              <template #default="{ row }">{{ row.dailyLimit ?? '不限' }}</template>
            </el-table-column>
            <el-table-column prop="description" label="描述" min-width="180" show-overflow-tooltip />
            <el-table-column label="状态" width="80" align="center">
              <template #default="{ row }">
                <el-tag :type="row.status === 1 ? 'success' : 'info'" size="small">{{ row.status === 1 ? '启用' : '停用' }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column label="操作" width="160" fixed="right" align="center">
              <template #default="{ row }">
                <el-button link type="primary" size="small" @click="handleBenefitEdit(row)">编辑</el-button>
                <el-button v-if="row.status === 1" link type="warning" size="small" @click="handleBenefitToggle(row, 0)">停用</el-button>
                <el-button v-else link type="success" size="small" @click="handleBenefitToggle(row, 1)">启用</el-button>
              </template>
            </el-table-column>
          </el-table>

          <div class="pagination-wrapper">
            <el-pagination
              v-model:current-page="benefitQuery.page"
              v-model:page-size="benefitQuery.pageSize"
              :total="benefitTotal"
              :page-sizes="[10, 20, 50]"
              layout="total, sizes, prev, pager, next, jumper"
              @size-change="loadBenefits"
              @current-change="loadBenefits"
            />
          </div>
        </div>
      </el-tab-pane>
    </el-tabs>

    <!-- 套餐新增/编辑弹窗 -->
    <el-dialog v-model="pkgFormVisible" :title="pkgEditingId ? '编辑套餐' : '新增套餐'" width="520px">
      <el-form ref="pkgFormRef" :model="pkgForm" :rules="pkgFormRules" label-width="90px">
        <el-form-item label="套餐名称" prop="name">
          <el-input v-model="pkgForm.name" placeholder="如：月卡/季卡/年卡" maxlength="20" />
        </el-form-item>
        <el-form-item label="VIP等级" prop="level">
          <el-select v-model="pkgForm.level" placeholder="请选择等级" style="width: 100%">
            <el-option label="1 - 月卡会员" :value="1" />
            <el-option label="2 - 季卡会员" :value="2" />
            <el-option label="3 - 年卡会员" :value="3" />
          </el-select>
        </el-form-item>
        <el-form-item label="售价(分)" prop="price">
          <el-input-number v-model="pkgForm.price" :min="0" :step="100" style="width: 200px" />
          <span class="form-tip">¥{{ formatAmount(pkgForm.price || 0) }}</span>
        </el-form-item>
        <el-form-item label="原价(分)">
          <el-input-number v-model="pkgForm.originalPrice" :min="0" :step="100" style="width: 200px" />
          <span class="form-tip" v-if="pkgForm.originalPrice">¥{{ formatAmount(pkgForm.originalPrice) }}</span>
        </el-form-item>
        <el-form-item label="有效天数" prop="durationDays">
          <el-input-number v-model="pkgForm.durationDays" :min="1" :max="3650" style="width: 200px" />
        </el-form-item>
        <el-form-item label="排序">
          <el-input-number v-model="pkgForm.sort" :min="0" style="width: 200px" />
        </el-form-item>
        <el-form-item label="描述">
          <el-input v-model="pkgForm.description" type="textarea" :rows="3" maxlength="200" show-word-limit />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="pkgFormVisible = false">取消</el-button>
        <el-button type="primary" @click="confirmPkgSave">保存</el-button>
      </template>
    </el-dialog>

    <!-- 权益编辑弹窗 -->
    <el-dialog v-model="benefitFormVisible" title="编辑权益" width="480px">
      <el-form ref="benefitFormRef" :model="benefitForm" :rules="benefitFormRules" label-width="90px">
        <el-form-item label="权益码">
          <el-input :model-value="benefitForm.serviceCode" disabled />
        </el-form-item>
        <el-form-item label="权益名称">
          <el-input :model-value="benefitForm.name" disabled />
        </el-form-item>
        <el-form-item label="所需等级" prop="vipLevelRequired">
          <el-select v-model="benefitForm.vipLevelRequired" style="width: 100%">
            <el-option label="1 - 月卡会员" :value="1" />
            <el-option label="2 - 季卡会员" :value="2" />
            <el-option label="3 - 年卡会员" :value="3" />
          </el-select>
        </el-form-item>
        <el-form-item label="每日限制">
          <el-input-number v-model="benefitForm.dailyLimit" :min="1" style="width: 200px" placeholder="留空不限" />
          <span class="form-tip">留空表示不限次</span>
        </el-form-item>
        <el-form-item label="排序">
          <el-input-number v-model="benefitForm.sort" :min="0" style="width: 200px" />
        </el-form-item>
        <el-form-item label="描述">
          <el-input v-model="benefitForm.description" type="textarea" :rows="3" maxlength="200" show-word-limit />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="benefitFormVisible = false">取消</el-button>
        <el-button type="primary" @click="confirmBenefitSave">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import type { FormInstance, FormRules } from 'element-plus'
import { Search, Refresh, Plus } from '@element-plus/icons-vue'
import {
  getVipPackagePageApi,
  createVipPackageApi,
  updateVipPackageApi,
  toggleVipPackageApi,
  getBenefitPageApi,
  updateBenefitApi,
  toggleBenefitApi,
} from '@/api/vip'
import type { VipPackageItem, VipPackageForm, BenefitItem, BenefitForm, VipQuery } from '@/api/vip'

const activeTab = ref('package')

/** 金额分转元 */
function formatAmount(amount: number) {
  return (amount / 100).toFixed(2)
}

function levelLabel(level: number) {
  const map: Record<number, string> = { 1: '月卡', 2: '季卡', 3: '年卡' }
  return map[level] || `L${level}`
}

// ============ 套餐 ============
const pkgLoading = ref(false)
const pkgData = ref<VipPackageItem[]>([])
const pkgTotal = ref(0)
const pkgQuery = reactive<VipQuery>({ page: 1, pageSize: 10, name: '', status: undefined })

async function loadPackages() {
  pkgLoading.value = true
  try {
    const res = await getVipPackagePageApi(pkgQuery)
    pkgData.value = res.list
    pkgTotal.value = res.total
  } catch {
    pkgData.value = []
    pkgTotal.value = 0
  } finally {
    pkgLoading.value = false
  }
}

function resetPkgQuery() {
  pkgQuery.name = ''
  pkgQuery.status = undefined
  pkgQuery.page = 1
  loadPackages()
}

const pkgFormVisible = ref(false)
const pkgFormRef = ref<FormInstance>()
const pkgEditingId = ref<number | null>(null)
const pkgForm = reactive<VipPackageForm>({
  name: '',
  level: 1,
  price: 3000,
  originalPrice: undefined,
  durationDays: 30,
  description: '',
  sort: 0,
})

const pkgFormRules: FormRules = {
  name: [{ required: true, message: '请输入套餐名称', trigger: 'blur' }],
  level: [{ required: true, message: '请选择等级', trigger: 'change' }],
  price: [{ required: true, message: '请输入售价', trigger: 'blur' }],
  durationDays: [{ required: true, message: '请输入有效天数', trigger: 'blur' }],
}

function handlePkgAdd() {
  pkgEditingId.value = null
  pkgForm.name = ''
  pkgForm.level = 1
  pkgForm.price = 3000
  pkgForm.originalPrice = undefined
  pkgForm.durationDays = 30
  pkgForm.description = ''
  pkgForm.sort = 0
  pkgFormVisible.value = true
}

function handlePkgEdit(row: VipPackageItem) {
  pkgEditingId.value = row.id
  pkgForm.name = row.name
  pkgForm.level = row.level
  pkgForm.price = row.price
  pkgForm.originalPrice = row.originalPrice || undefined
  pkgForm.durationDays = row.durationDays
  pkgForm.description = row.description
  pkgForm.sort = row.sort
  pkgFormVisible.value = true
}

async function confirmPkgSave() {
  if (!pkgFormRef.value) return
  await pkgFormRef.value.validate(async (valid) => {
    if (!valid) return
    try {
      if (pkgEditingId.value) {
        // originalPrice 为 undefined 时转 null，确保清空原价能传到后端（axios 会丢弃 undefined 字段）
        await updateVipPackageApi(pkgEditingId.value, { ...pkgForm, originalPrice: pkgForm.originalPrice ?? null })
        ElMessage.success('更新成功')
      } else {
        await createVipPackageApi(pkgForm)
        ElMessage.success('新增成功')
      }
      pkgFormVisible.value = false
      loadPackages()
    } catch {
      // 错误已由拦截器处理
    }
  })
}

async function handlePkgToggle(row: VipPackageItem, status: number) {
  try {
    await toggleVipPackageApi(row.id, status)
    ElMessage.success(status === 1 ? '已上架' : '已下架')
    loadPackages()
  } catch {
    // 错误已由拦截器处理
  }
}

// ============ 权益 ============
const benefitLoading = ref(false)
const benefitData = ref<BenefitItem[]>([])
const benefitTotal = ref(0)
const benefitQuery = reactive<VipQuery>({ page: 1, pageSize: 10, name: '', status: undefined })

async function loadBenefits() {
  benefitLoading.value = true
  try {
    const res = await getBenefitPageApi(benefitQuery)
    benefitData.value = res.list
    benefitTotal.value = res.total
  } catch {
    benefitData.value = []
    benefitTotal.value = 0
  } finally {
    benefitLoading.value = false
  }
}

function resetBenefitQuery() {
  benefitQuery.name = ''
  benefitQuery.status = undefined
  benefitQuery.page = 1
  loadBenefits()
}

const benefitFormVisible = ref(false)
const benefitFormRef = ref<FormInstance>()
const benefitEditingId = ref<number | null>(null)
const benefitForm = reactive<BenefitForm & { serviceCode: string; name: string }>({
  serviceCode: '',
  name: '',
  vipLevelRequired: 1,
  dailyLimit: undefined,
  description: '',
  sort: 0,
})

const benefitFormRules: FormRules = {
  vipLevelRequired: [{ required: true, message: '请选择所需等级', trigger: 'change' }],
}

function handleBenefitEdit(row: BenefitItem) {
  benefitEditingId.value = row.id
  benefitForm.serviceCode = row.serviceCode
  benefitForm.name = row.name
  benefitForm.vipLevelRequired = row.vipLevelRequired
  benefitForm.dailyLimit = row.dailyLimit ?? undefined
  benefitForm.description = row.description
  benefitForm.sort = row.sort
  benefitFormVisible.value = true
}

async function confirmBenefitSave() {
  if (!benefitFormRef.value || !benefitEditingId.value) return
  await benefitFormRef.value.validate(async (valid) => {
    if (!valid) return
    try {
      await updateBenefitApi(benefitEditingId.value!, {
        vipLevelRequired: benefitForm.vipLevelRequired,
        dailyLimit: benefitForm.dailyLimit ?? null,
        description: benefitForm.description,
        sort: benefitForm.sort,
      })
      ElMessage.success('更新成功')
      benefitFormVisible.value = false
      loadBenefits()
    } catch {
      // 错误已由拦截器处理
    }
  })
}

async function handleBenefitToggle(row: BenefitItem, status: number) {
  try {
    await toggleBenefitApi(row.id, status)
    ElMessage.success(status === 1 ? '已启用' : '已停用')
    loadBenefits()
  } catch {
    // 错误已由拦截器处理
  }
}

onMounted(() => {
  loadPackages()
  loadBenefits()
})
</script>

<style scoped>
.form-tip {
  margin-left: 8px;
  color: #909399;
  font-size: 12px;
}
</style>