<template>
  <div class="page-container">
    <PageHeader title="系统配置" subtitle="运行时可调，改配置约 30 秒内全节点生效，无需重启服务" />

    <!-- IM 通道状态 -->
    <TableCard title="即时通讯通道">
      <div class="provider-section">
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

    <!-- 视频运营参数：产品可直接调整推荐/热点/附近的运营计划 -->
    <TableCard title="视频运营参数">
      <el-form :model="videoForm" label-width="160px" class="config-form">
        <el-form-item label="推荐候选池上限">
          <el-input-number
            v-model="videoForm.recommendPoolLimit"
            :min="10"
            :max="1000"
            :step="10"
            controls-position="right"
          />
          <span class="field-hint">推荐流一次聚合的最大视频数，影响推荐覆盖范围</span>
        </el-form-item>
        <el-form-item label="推荐缓存有效期（秒）">
          <el-input-number
            v-model="videoForm.recommendCacheSeconds"
            :min="30"
            :max="3600"
            :step="30"
            controls-position="right"
          />
          <span class="field-hint">推荐排序结果缓存 TTL，过期后自动重建</span>
        </el-form-item>
        <el-form-item label="推荐重建锁 TTL（秒）">
          <el-input-number
            v-model="videoForm.recommendLockSeconds"
            :min="1"
            :max="60"
            :step="1"
            controls-position="right"
          />
          <span class="field-hint">缓存击穿防护锁，防并发回源</span>
        </el-form-item>
        <el-form-item label="热点候选池上限">
          <el-input-number
            v-model="videoForm.hotCandidateLimit"
            :min="20"
            :max="500"
            :step="20"
            controls-position="right"
          />
          <span class="field-hint">热点流取最新 N 条再排序，N 越大覆盖越广但性能越低</span>
        </el-form-item>
        <el-form-item label="附近半径（公里）">
          <el-input-number
            v-model="videoForm.nearbyRadiusKm"
            :min="1"
            :max="50"
            :step="1"
            controls-position="right"
          />
          <span class="field-hint">附近流 geo 查询半径，超出则回退全站热门</span>
        </el-form-item>
        <el-form-item label="附近最多返回条数">
          <el-input-number
            v-model="videoForm.nearbyMaxResults"
            :min="10"
            :max="200"
            :step="10"
            controls-position="right"
          />
          <span class="field-hint">附近流总条数上限，超出即视为没有更多</span>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" :loading="saving" @click="handleSave">保存配置</el-button>
          <el-button @click="loadData">重置</el-button>
        </el-form-item>
      </el-form>
    </TableCard>

    <!-- 全部配置列表 -->
    <TableCard title="全部系统配置">
      <el-table :data="configList" border stripe>
        <el-table-column label="配置键" prop="configKey" width="220" />
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
import { onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { PageHeader, TableCard, TableEmpty } from '@/components'
import { useResponsive } from '@/composables/useResponsive'
import { getSysConfigListApi, updateSysConfigApi, type SysConfigItem } from '@/api/sysConfig'

// 响应式列可见性：移动端隐藏次要列（更新时间）
const { isMobile } = useResponsive()

const loading = ref(false)
const saving = ref(false)
const configList = ref<SysConfigItem[]>([])

/** 视频运营参数表单：值从配置列表读取，未配置时用后端默认值 */
const videoForm = reactive({
  recommendPoolLimit: 300,
  recommendCacheSeconds: 600,
  recommendLockSeconds: 5,
  hotCandidateLimit: 100,
  nearbyRadiusKm: 5,
  nearbyMaxResults: 50,
})

/** 配置键 → 表单字段映射 */
const KEY_TO_FIELD: Record<string, keyof typeof videoForm> = {
  'video.recommend.pool-limit': 'recommendPoolLimit',
  'video.recommend.cache-seconds': 'recommendCacheSeconds',
  'video.recommend.lock-seconds': 'recommendLockSeconds',
  'video.hot.candidate-limit': 'hotCandidateLimit',
  'video.nearby.radius-km': 'nearbyRadiusKm',
  'video.nearby.max-results': 'nearbyMaxResults',
}

function applyConfigToForm(list: SysConfigItem[]): void {
  for (const item of list) {
    const field = KEY_TO_FIELD[item.configKey]
    if (field) {
      const parsed = Number(item.configValue)
      if (Number.isFinite(parsed)) {
        videoForm[field] = parsed
      }
    }
  }
}

async function loadData() {
  loading.value = true
  try {
    configList.value = await getSysConfigListApi().catch(() => [] as SysConfigItem[])
    applyConfigToForm(configList.value)
  } catch {
    ElMessage.error('配置加载失败，请检查后端服务')
  } finally {
    loading.value = false
  }
}

async function handleSave() {
  saving.value = true
  try {
    const entries = Object.entries(KEY_TO_FIELD)
    for (const [key, field] of entries) {
      const value = String(videoForm[field])
      const existing = configList.value.find((item) => item.configKey === key)
      await updateSysConfigApi({
        configKey: key,
        configValue: value,
        description: existing?.description,
      })
    }
    ElMessage.success('配置已保存，约 30 秒内全节点生效')
    await loadData()
  } catch {
    ElMessage.error('保存失败，请检查后端服务')
  } finally {
    saving.value = false
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
  gap: var(--xfjy-space-5);
  padding: var(--xfjy-space-2) 0;
}

.current-status {
  display: flex;
  align-items: center;
}

.config-form {
  max-width: 720px;
  padding: var(--xfjy-space-2) 0;
}

.field-hint {
  margin-left: var(--xfjy-space-3);
  font-size: var(--xfjy-font-size-sm);
  color: var(--el-text-color-secondary);
}
</style>
