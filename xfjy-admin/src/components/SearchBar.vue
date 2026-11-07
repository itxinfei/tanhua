<template>
  <div class="search-bar">
    <el-form :inline="true" :model="model" @submit.prevent>
      <slot />
      <el-form-item>
        <el-button type="primary" :icon="Search" @click="emit('search')">查询</el-button>
        <el-button :icon="Refresh" @click="emit('reset')">重置</el-button>
        <slot name="extra-actions" />
      </el-form-item>
    </el-form>
  </div>
</template>

<script setup lang="ts">
import { Search, Refresh } from '@element-plus/icons-vue'

defineProps<{
  model: Record<string, any>
}>()

const emit = defineEmits<{
  search: []
  reset: []
}>()
</script>

<style scoped>
.search-bar {
  background: var(--xfjy-bg-card);
  border: 1px solid var(--xfjy-border);
  border-radius: var(--xfjy-radius-lg);
  padding: var(--xfjy-space-3) var(--xfjy-space-4);
  box-shadow: var(--xfjy-shadow-card);
}

.search-bar :deep(.el-form-item) {
  margin-bottom: 0;
  margin-right: var(--xfjy-space-3);
}

.search-bar :deep(.el-input__wrapper),
.search-bar :deep(.el-select .el-input__wrapper) {
  border-radius: var(--xfjy-radius-sm);
}
</style>
