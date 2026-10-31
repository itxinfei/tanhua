<template>
  <div class="page-container">
    <PageHeader title="IM 通道配置" subtitle="默认使用开源 WuKongIM，可切换回环信第三方" />

    <!-- 通道切换 -->
    <TableCard title="即时通讯通道">
      <div v-loading="loading" class="provider-section">
        <el-radio-group v-model="selectedProvider" class="provider-group">
          <el-radio value="wukongim" border class="provider-option">
            <div class="option-content">
              <span class="option-title">WuKongIM（开源）</span>
              <span class="option-desc">自建部署，零外部依赖，Apache-2.0 开源协议</span>
            </div>
          </el-radio>
          <el-radio value="huanxin" border class="provider-option">
            <div class="option-content">
              <span class="option-title">环信（第三方）</span>
              <span class="option-desc">SaaS 云服务，需要有效的 client-id / client-secret</span>
            </div>
          </el-radio>
        </el-radio-group>

        <div class="current-status">
          <el-tag :type="currentProvider === 'wukongim' ? 'success' : 'warning'" size="large">
            当前通道：{{ currentProvider === 'wukongim' ? 'WuKongIM（开源）' : '环信（第三方）' }}
          </el-tag>
        </div>

        <div class="action-bar">
          <el-button
            type="primary"
            :loading="submitting"
            :disabled="selectedProvider === currentProvider"
            @click="handleSwitch"
          >
            保存并切换
          </el-button>
          <span v-if="selectedProvider !== currentProvider" class="dirty-hint">
            有未保存的更改
          </span>
        </div>

        <el-alert
          type="info"
          :closable="false"
          show-icon
          title="切换说明"
          description="切换后约 30 秒内全节点生效（Redis 缓存 TTL），无需重启服务。App 端下次获取 IM 用户信息时将返回新通道标识，前端据此选择对应 SDK 连接。"
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
      </el-table>
    </TableCard>
  </div>
</template>

<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { PageHeader, TableCard } from '@/components'
import { useResponsive } from '@/composables/useResponsive'
import {
  getImProviderApi,
  getSysConfigListApi,
  switchImProviderApi,
  type ImProvider,
  type SysConfigItem
} from '@/api/sysConfig'

// 响应式列可见性：移动端隐藏次要列（更新时间）
const { isMobile } = useResponsive()

const loading = ref(false)
const submitting = ref(false)
const currentProvider = ref<ImProvider>('wukongim')
const selectedProvider = ref<ImProvider>('wukongim')
const configList = ref<SysConfigItem[]>([])

async function loadData() {
  loading.value = true
  try {
    const [providerRes, listRes] = await Promise.all([
      getImProviderApi(),
      getSysConfigListApi().catch(() => [] as SysConfigItem[])
    ])
    currentProvider.value = providerRes.provider
    selectedProvider.value = providerRes.provider
    configList.value = listRes
  } catch {
    ElMessage.error('配置加载失败，请检查后端服务')
  } finally {
    loading.value = false
  }
}

async function handleSwitch() {
  const target = selectedProvider.value
  const label = target === 'wukongim' ? 'WuKongIM（开源）' : '环信（第三方）'
  try {
    await ElMessageBox.confirm(
      `确定将 IM 通道切换为「${label}」吗？切换后约 30 秒内全节点生效。`,
      '切换确认',
      { confirmButtonText: '确定切换', cancelButtonText: '取消', type: 'warning' }
    )
  } catch {
    return
  }

  submitting.value = true
  try {
    const res = await switchImProviderApi(target)
    if (res.success) {
      currentProvider.value = target
      ElMessage.success(res.message || '切换成功')
      await getSysConfigListApi().then((list) => (configList.value = list)).catch(() => {})
    } else {
      ElMessage.error(res.message || '切换失败')
    }
  } catch {
    // 拦截器已提示
  } finally {
    submitting.value = false
  }
}

onMounted(() => {
  loadData()
})
</script>

<style scoped>
.card-title {
  font-size: var(--xfjy-font-size-base, 14px);
  font-weight: 600;
}

.provider-section {
  display: flex;
  flex-direction: column;
  gap: 20px;
  padding: 8px 0;
}

.provider-group {
  display: flex;
  flex-direction: column;
  gap: 12px;
  align-items: flex-start;
}

.provider-option {
  height: auto;
  padding: 12px 16px;
  margin-right: 0;
}

.option-content {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.option-title {
  font-weight: 600;
  font-size: 14px;
}

.option-desc {
  font-size: 12px;
  color: var(--xfjy-text-secondary);
}

.current-status {
  display: flex;
  align-items: center;
}

.action-bar {
  display: flex;
  align-items: center;
  gap: 12px;
}

.dirty-hint {
  font-size: 12px;
  color: var(--xfjy-chart-4);
}
</style>