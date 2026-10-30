<template>
  <div class="page-container">
    <!-- 搜索栏 -->
    <div class="search-bar">
      <el-form :inline="true" :model="query" @submit.prevent>
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
        <el-form-item>
          <el-button type="primary" :icon="Search" @click="handleSearch">查询</el-button>
          <el-button :icon="Refresh" @click="handleReset">重置</el-button>
        </el-form-item>
      </el-form>
    </div>

    <!-- 表格 -->
    <div class="table-container">
      <el-table :data="tableData" v-loading="loading" stripe border style="width: 100%" empty-text="暂无订单数据">
        <el-table-column prop="id" label="ID" width="70" align="center" />
        <el-table-column prop="orderNo" label="订单号" width="220" show-overflow-tooltip />
        <el-table-column prop="userId" label="用户ID" width="90" align="center" />
        <el-table-column prop="packageName" label="套餐" width="110" />
        <el-table-column label="金额" width="100" align="right">
          <template #default="{ row }">¥{{ formatAmount(row.amount) }}</template>
        </el-table-column>
        <el-table-column label="状态" width="90" align="center">
          <template #default="{ row }">
            <el-tag :type="statusColor(row.status)" size="small">{{ statusLabel(row.status) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="支付方式" width="100" align="center">
          <template #default="{ row }">{{ payTypeLabel(row.payType) }}</template>
        </el-table-column>
        <el-table-column prop="paidTime" label="支付时间" width="170">
          <template #default="{ row }">{{ row.paidTime || '-' }}</template>
        </el-table-column>
        <el-table-column prop="created" label="创建时间" width="170" />
        <el-table-column label="操作" width="80" fixed="right" align="center">
          <template #default="{ row }">
            <el-button link type="primary" size="small" @click="showDetail(row)">详情</el-button>
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
          <el-tag :type="statusColor(currentOrder.status)" size="small">{{ statusLabel(currentOrder.status) }}</el-tag>
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
import { Search, Refresh } from '@element-plus/icons-vue'
import { getPayOrderPageApi } from '@/api/pay'
import type { PayOrderItem, PayOrderQuery } from '@/api/pay'

const loading = ref(false)
const tableData = ref<PayOrderItem[]>([])
const total = ref(0)

const query = reactive<PayOrderQuery>({
  page: 1,
  pageSize: 10,
  orderNo: '',
  status: '',
  userId: undefined,
})

async function loadData() {
  loading.value = true
  try {
    // userId 非数字时忽略该筛选条件，避免传 NaN 给后端
    const params = { ...query }
    if (params.userId != null && Number.isNaN(Number(params.userId))) {
      params.userId = undefined
    }
    const res = await getPayOrderPageApi(params)
    tableData.value = res.list
    total.value = res.total
  } catch {
    // 生产环境显示空列表避免假数据误导，开发环境无后端时也无 mock（订单数据敏感）
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
  query.orderNo = ''
  query.status = ''
  query.userId = undefined
  query.page = 1
  loadData()
}

/** 切换每页条数时回到第 1 页 */
function handleSizeChange() {
  query.page = 1
  loadData()
}

/** 金额分转元 */
function formatAmount(amount: number) {
  return (amount / 100).toFixed(2)
}

function statusLabel(status: string) {
  const map: Record<string, string> = { PENDING: '待支付', PAID: '已支付', EXPIRED: '已过期', CANCELLED: '已取消' }
  return map[status] || status
}

function statusColor(status: string) {
  const map: Record<string, string> = { PENDING: 'warning', PAID: 'success', EXPIRED: 'info', CANCELLED: 'danger' }
  return map[status] || 'info'
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