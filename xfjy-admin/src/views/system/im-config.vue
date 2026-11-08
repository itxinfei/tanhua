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

    <!-- 地图服务：Key 由后台维护，改完 30 秒内生效，无需重新打包前端 -->
    <TableCard title="地图服务">
      <el-form :model="mapForm" label-width="160px" class="config-form">
        <el-form-item label="地图功能开关">
          <el-switch v-model="mapForm.enabled" active-value="1" inactive-value="0" />
          <span class="field-hint">关闭后前端隐藏地图选点入口，定位降级为手动选城市</span>
        </el-form-item>
        <el-form-item label="前端 JS API Key">
          <el-input v-model="mapForm.jsKey" placeholder="高德「Web端(JS API)」类型 Key" clearable />
          <span class="field-hint">用于地图展示与选点，会下发到前端（本身即公开信息）</span>
        </el-form-item>
        <el-form-item label="JS API 安全密钥">
          <el-input
            v-model="mapForm.securityJsCode"
            placeholder="JS API 2.0 安全密钥，与 JS Key 配套"
            clearable
          />
          <span class="field-hint">高德 2021-12 后新建的 JS Key 必须配套安全密钥，否则鉴权失败</span>
        </el-form-item>
        <el-form-item label="Web 服务 Key">
          <el-input v-model="mapForm.webKey" placeholder="高德「Web服务」类型 Key" clearable />
          <span class="field-hint">用于服务端逆地理编码与 IP 定位，<strong>不会下发前端</strong></span>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" :loading="mapSaving" @click="handleSaveMap">保存地图配置</el-button>
          <el-button @click="loadData">重置</el-button>
        </el-form-item>
      </el-form>
    </TableCard>

    <!-- 全部配置列表 -->
    <TableCard title="全部系统配置" :skeleton="loading && !configList.length">
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
const mapSaving = ref(false)
const configList = ref<SysConfigItem[]>([])

/** 地图服务配置：Key 由后台维护，服务端 30 秒内生效 */
const mapForm = reactive({
  enabled: '1',
  jsKey: '',
  securityJsCode: '',
  webKey: '',
})

/** 地图配置键 → 表单字段 */
const MAP_KEY_TO_FIELD: Record<string, keyof typeof mapForm> = {
  'map.enabled': 'enabled',
  'map.amap.js-key': 'jsKey',
  'map.amap.security-js-code': 'securityJsCode',
  'map.amap.web-key': 'webKey',
}

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
    const mapField = MAP_KEY_TO_FIELD[item.configKey]
    if (mapField) {
      mapForm[mapField] = item.configValue ?? ''
    }
  }
  // 旧库可能缺 map.enabled 行，缺省视为启用
  if (!mapForm.enabled) {
    mapForm.enabled = '1'
  }
}

/** 保存地图配置：四项一起写，避免只改一半导致 Key 与开关状态错位 */
async function handleSaveMap(): Promise<void> {
  mapSaving.value = true
  try {
    for (const [key, field] of Object.entries(MAP_KEY_TO_FIELD)) {
      const existing = configList.value.find((item) => item.configKey === key)
      await updateSysConfigApi({
        configKey: key,
        configValue: String(mapForm[field] ?? ''),
        description: existing?.description,
      })
    }
    ElMessage.success('地图配置已保存，约 30 秒内生效')
    await loadData()
  } catch {
    // 拦截器已统一提示
  } finally {
    mapSaving.value = false
  }
}

async function loadData() {
  loading.value = true
  try {
    // 内层 .catch 兜底返回空数组，错误已由拦截器统一提示
    configList.value = await getSysConfigListApi().catch(() => [] as SysConfigItem[])
    applyConfigToForm(configList.value)
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
    // 拦截器已统一提示
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
