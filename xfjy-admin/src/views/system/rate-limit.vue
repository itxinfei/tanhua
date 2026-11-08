<template>
  <div class="page-container">
    <PageHeader
      title="限流配置"
      subtitle="网关 IP 限流参数，改配置约 30 秒内全节点生效，无需重启服务"
    />

    <TableCard
      title="IP 限流参数"
      :skeleton="loading"
    >
      <el-alert
        class="scope-alert"
        type="info"
        :closable="false"
        show-icon
        title="限流范围说明"
        description="仅限发布类写操作：发动态、发评论、发视频、举报。播放、点赞、关注等互动接口不限流。"
      />
      <el-form
        :model="form"
        label-width="160px"
        class="config-form"
      >
        <el-form-item label="窗口内最大请求数">
          <el-input-number
            v-model="form.limit"
            :min="1"
            :max="100"
            :step="1"
            controls-position="right"
          />
          <span class="field-hint">统计窗口内同一 IP 允许的最大请求次数，超出返回 429</span>
        </el-form-item>
        <el-form-item label="统计窗口（秒）">
          <el-input-number
            v-model="form.windowSeconds"
            :min="10"
            :max="3600"
            :step="10"
            controls-position="right"
          />
          <span class="field-hint">固定窗口大小，窗口结束后计数自动清零</span>
        </el-form-item>
        <el-form-item>
          <el-button
            type="primary"
            :loading="saving"
            @click="handleSave"
          >
            保存配置
          </el-button>
          <el-button @click="loadData">
            重置
          </el-button>
        </el-form-item>
      </el-form>
    </TableCard>
  </div>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { PageHeader, TableCard } from '@/components'
import { getRateLimitConfigApi, updateRateLimitConfigApi } from '@/api/sysConfig'

const loading = ref(false)
const saving = ref(false)

const form = reactive({
  limit: 5,
  windowSeconds: 60,
})

async function loadData() {
  loading.value = true
  try {
    const config = await getRateLimitConfigApi().catch(() => null)
    if (config) {
      form.limit = config.limit
      form.windowSeconds = config.windowSeconds
    }
  } finally {
    loading.value = false
  }
}

async function handleSave() {
  if (saving.value) return
  saving.value = true
  try {
    await updateRateLimitConfigApi({ limit: form.limit, windowSeconds: form.windowSeconds })
    ElMessage.success('限流配置已保存，约 30 秒内生效')
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
.scope-alert {
  margin-bottom: var(--xfjy-space-5);
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