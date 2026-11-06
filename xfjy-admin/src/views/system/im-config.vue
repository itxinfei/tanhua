<template>
  <div class="page-container">
    <PageHeader title="IM 通道配置" subtitle="当前使用开源 WuKongIM，环信通道已下线" />

    <!-- 通道状态 -->
    <TableCard title="即时通讯通道">
      <div v-loading="loading" class="provider-section">
        <div class="current-status">
          <el-tag type="success" size="large">当前通道：WuKongIM（开源）</el-tag>
        </div>

        <el-alert
          type="info"
          :closable="false"
          show-icon
          title="通道说明"
          description="即时通讯统一使用自建 WuKongIM（Apache-2.0 开源协议），零外部依赖。App 端通过 /wukongim/user 端点获取连接凭证。"
        />
      </div>
    </TableCard>

    <!-- 全部配置列表 -->
    <TableCard title="全部系统配置">
      <el-table :data="configList" border stripe>
        <el-table-column label="配置键" prop="configKey" width="200" />
        <el-table-column label="配置值" prop="configValue" width="160">
          <template #default="{ row }">
            <el-tag size="small">{{ row.configValue }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="说明" prop="description" min-width="200" show-overflow-tooltip />
        <el-table-column v-if="!isMobile" label="更新时间" prop="updated" width="180" />
        <template #empty>
          <TableEmpty description="暂无配置数据" />
        </template>
      </el-table>
    </TableCard>
  </div>
</template>

<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { PageHeader, TableCard, TableEmpty } from '@/components'
import { useResponsive } from '@/composables/useResponsive'
import { getSysConfigListApi, type SysConfigItem } from '@/api/sysConfig'

// 响应式列可见性：移动端隐藏次要列（更新时间）
const { isMobile } = useResponsive()

const loading = ref(false)
const configList = ref<SysConfigItem[]>([])

async function loadData() {
  loading.value = true
  try {
    configList.value = await getSysConfigListApi().catch(() => [] as SysConfigItem[])
  } catch {
    ElMessage.error('配置加载失败，请检查后端服务')
  } finally {
    loading.value = false
  }
}

onMounted(() => {
  loadData()
})
</script>

<style scoped>
.provider-section {
  display: flex;
  flex-direction: column;
  gap: 20px;
  padding: 8px 0;
}

.current-status {
  display: flex;
  align-items: center;
}
</style>