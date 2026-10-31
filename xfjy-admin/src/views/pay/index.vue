<template>
  <div class="page-container">
    <!-- 汇总卡片 -->
    <el-row :gutter="16" style="margin-bottom: 16px">
      <el-col :xs="12" :sm="6">
        <StatCard label="总订单数" :value="summary.total" icon="Document" icon-bg="linear-gradient(135deg, var(--xfjy-primary-deep), var(--xfjy-primary))" />
      </el-col>
      <el-col :xs="12" :sm="6">
        <StatCard label="已支付" :value="summary.paidCount" icon="CircleCheck" icon-bg="linear-gradient(135deg, #00b894, #00d6b8)" />
      </el-col>
      <el-col :xs="12" :sm="6">
        <StatCard label="待支付" :value="summary.pendingCount" icon="Clock" icon-bg="linear-gradient(135deg, #f59e0b, #fbbf24)" />
      </el-col>
      <el-col :xs="12" :sm="6">
        <StatCard label="总收入" :value="summary.totalAmount" suffix="分" icon="Wallet" icon-bg="linear-gradient(135deg, var(--xfjy-primary), var(--xfjy-primary-soft))" />
      </el-col>
    </el-row>

    <SearchBar :model="query" @search="handleSearch" @reset="handleReset">
      <el-form-item label="订单号">
        <el-input v-model="query.orderNo" placeholder="订单号" clearable style="width: 200px" @keyup.enter="handleSearch" />
      </el-form-item>
      <el-form-item label="状态">
        <el-select v-model="query.status" placeholder="全部" clearable style="width: 120px">
          <el-option label="待支付" value="PENDING" />
          <el-option label="已支付" value="PAID" />
          <el-option label="已过期" value="EXPIRED" />
          <el-option label="已取消" value="CANCELLED" />
        </el-select>
      </el-form-item>
      <el-form-item label="用户ID">
        <el-input v-model.number="query.userId" placeholder="用户ID" clearable style="width: 120px" @keyup.enter="handleSearch" />
      </el-form-item>
      <el-form-item label="时间范围">
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
      <template #extra-actions>
        <el-button type="success" :icon="Download" @click="handleExport">导出</el-button>
      </template>
    </SearchBar>

    <TableCard>
      <el-table :data="tableData" v-loading="loading" stripe border style="width: 100%" empty-text="暂无订单数据">
        <el-table-column v-if="!isMobile" prop="id" label="ID" width="70" align="center" />
        <el-table-column label="订单号" width="280">
          <template #default="{ row }">
            <div class="order-no-cell">
              <span class="order-no-text">{{ row.orderNo }}</span>
              <el-button link type="primary" size="small" :icon="CopyDocument" @click="copyOrderNo(row.orderNo)" />
            </div>
          </template>
        </el-table-column>
        <el-table-column v-if="!isMobile" prop="userId" label="用户ID" width="90" align="center" />
        <el-table-column prop="packageName" label="套餐" width="110" />
        <el-table-column label="金额" width="100" align="right">
          <template #default="{ row }">¥{{ formatAmount(row.amount) }}</template>
        </el-table-column>
        <el-table-column label="状态" width="90" align="center">
          <template #default="{ row }">
            <StatusTag :status="row.status" :mapping="payStatusMapping" />
          </template>
        </el-table-column>
        <el-table-column v-if="!isMobile" label="支付方式" width="100" align="center">
          <template #default="{ row }">{{ payTypeLabel(row.payType) }}</template>
        </el-table-column>
        <el-table-column prop="paidTime" label="支付时间" width="170">
          <template #default="{ row }">{{ row.paidTime || '-' }}</template>
        </el-table-column>
        <el-table-column v-if="!isMobile" prop="created" label="创建时间" width="170" />
        <el-table-column label="操作" width="80" fixed="right" align="center">
          <template #default="{ row }">
            <el-button link type="primary" size="small" @click="showDetail(row)">详情</el-button>
          </template>
        </el-table-column>
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

    <!-- 详情弹窗 -->
    <el-dialog v-model="detailVisible" title="订单详情" width="560px">
      <el-descriptions :column="2" border v-if="currentOrder">
        <el-descriptions-item label="订单ID">{{ currentOrder.id }}</el-descriptions-item>
        <el-descriptions-item label="订单号">{{ currentOrder.orderNo }}</el-descriptions-item>
        <el-descriptions-item label="用户ID">{{ currentOrder.userId }}</el-descriptions-item>
        <el-descriptions-item label="套餐ID">{{ currentOrder.packageId }}</el-descriptions-item>
        <el-descriptions-item label="套餐名称">{{ currentOrder.packageName }}</el-descriptions-item>
        <el-descriptions-item label="金额">¥{{ formatAmount(currentOrder.amount) }}</el-descriptions-item>
        <el-descriptions-item label="状态">
          <StatusTag :status="currentOrder.status" :mapping="payStatusMapping" />
        </el-descriptions-item>
        <el-descriptions-item label="支付方式">{{ payTypeLabel(currentOrder.payType) }}</el-descriptions-item>
        <el-descriptions-item label="支付时间">{{ currentOrder.paidTime || '-' }}</el-descriptions-item>
        <el-descriptions-item label="会员到期">{{ currentOrder.expireTime || '-' }}</el-descriptions-item>
        <el-descriptions-item label="创建时间" :span="2">{{ currentOrder.created }}</el-descriptions-item>
        <el-descriptions-item label="更新时间" :span="2">{{ currentOrder.updated }}</el-descriptions-item>
      </el-descriptions>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { Download, CopyDocument } from '@element-plus/icons-vue'
import { SearchBar, TableCard, StatusTag, StatCard } from '@/components'
import { useResponsive } from '@/composables/useResponsive'
import { formatAmount } from '@/utils/format'
import { getPayOrderPageApi, getPayOrderSummaryApi, exportPayOrdersApi } from '@/api/pay'
import type { PayOrderItem, PayOrderQuery, PayOrderSummary } from '@/api/pay'

// 响应式列可见性：移动端隐藏次要列（ID/用户ID/支付方式/创建时间）
const { isMobile } = useResponsive()

const loading = ref(false)
const tableData = ref<PayOrderItem[]>([])
const total = ref(0)

const summary = reactive<PayOrderSummary>({
  total: 0,
  paidCount: 0,
  pendingCount: 0,
  totalAmount: 0,
})

const dateRange = ref<[string, string] | null>(null)

const query = reactive<PayOrderQuery>({
  page: 1,
  pageSize: 10,
  orderNo: '',
  status: '',
  userId: undefined,
  startDate: undefined,
  endDate: undefined,
})

async function loadData() {
  loading.value = true
  try {
    // userId 非数字时忽略该筛选条件，避免传 NaN 给后端
    // 表单清空时运行时为 ''（string），统一转字符串判空再转回 number
    const params = { ...query }
    const uid = String(params.userId ?? '').trim()
    if (!uid || Number.isNaN(Number(uid))) {
      params.userId = undefined
    } else {
      params.userId = Number(uid)
    }
    const res = await getPayOrderPageApi(params)
    tableData.value = res.list
    total.value = res.total
  } catch (err) {
    ElMessage.error('数据加载失败，请检查后端服务')
  } finally {
    loading.value = false
  }
}

async function loadSummary() {
  try {
    const params: { status?: string; startDate?: string; endDate?: string } = {}
    if (query.status) params.status = query.status
    if (query.startDate) params.startDate = query.startDate
    if (query.endDate) params.endDate = query.endDate
    const res = await getPayOrderSummaryApi(params)
    Object.assign(summary, res)
  } catch {
    // 汇总加载失败不阻断列表
  }
}

function handleDateChange(val: [string, string] | null) {
  if (val && val.length === 2) {
    query.startDate = val[0]
    query.endDate = val[1]
  } else {
    query.startDate = undefined
    query.endDate = undefined
  }
}

function handleSearch() {
  query.page = 1
  loadData()
  loadSummary()
}

function handleReset() {
  query.orderNo = ''
  query.status = ''
  query.userId = undefined
  query.startDate = undefined
  query.endDate = undefined
  dateRange.value = null
  query.page = 1
  loadData()
  loadSummary()
}

/** 切换每页条数时回到第 1 页 */
function handleSizeChange() {
  query.page = 1
  loadData()
}

/** 导出订单 */
async function handleExport() {
  try {
    const params: { orderNo?: string; status?: string; userId?: number; startDate?: string; endDate?: string } = {}
    if (query.orderNo) params.orderNo = query.orderNo
    if (query.status) params.status = query.status
    if (query.userId) params.userId = query.userId
    if (query.startDate) params.startDate = query.startDate
    if (query.endDate) params.endDate = query.endDate

    const blob = await exportPayOrdersApi(params)
    const url = window.URL.createObjectURL(blob)
    const link = document.createElement('a')
    link.href = url
    link.download = `pay-orders-${new Date().toISOString().slice(0, 10)}.csv`
    link.click()
    window.URL.revokeObjectURL(url)
    ElMessage.success('导出成功')
  } catch {
    ElMessage.error('导出失败')
  }
}

/** 复制订单号 */
async function copyOrderNo(orderNo: string) {
  try {
    await navigator.clipboard.writeText(orderNo)
    ElMessage.success('订单号已复制')
  } catch {
    ElMessage.error('复制失败，请手动复制')
  }
}

// 订单状态映射
const payStatusMapping = {
  PENDING: { label: '待支付', type: 'warning' as const },
  PAID: { label: '已支付', type: 'success' as const },
  EXPIRED: { label: '已过期', type: 'info' as const },
  CANCELLED: { label: '已取消', type: 'danger' as const },
}

function payTypeLabel(payType: string) {
  const map: Record<string, string> = { SANDBOX: '沙箱', ALIPAY: '支付宝', WECHAT: '微信' }
  return map[payType] || payType
}

// 详情
const detailVisible = ref(false)
const currentOrder = ref<PayOrderItem | null>(null)

function showDetail(row: PayOrderItem) {
  currentOrder.value = row
  detailVisible.value = true
}

onMounted(() => {
  loadData()
})
</script>

<style scoped>
.order-no-cell {
  display: flex;
  align-items: center;
  gap: 4px;
}

.order-no-text {
  font-family: 'Courier New', monospace;
  font-size: 13px;
}
</style>