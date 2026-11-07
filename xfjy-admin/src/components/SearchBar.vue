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

    <!-- 已选筛选 chip 摘要：非空筛选一目了然，点 × 清除单项并自动查询 -->
    <div v-if="activeChips.length" class="filter-chips">
      <span class="filter-chips-label">当前筛选：</span>
      <el-tag
        v-for="chip in activeChips"
        :key="chip.key"
        closable
        size="small"
        @close="removeChip(chip.key)"
      >
        {{ chip.label }}：{{ chip.text }}
      </el-tag>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { Search, Refresh } from '@element-plus/icons-vue'

/** 单个筛选字段的元信息：label 为中文名，options 为值→文本映射（select 选项标签） */
export interface FilterMetaItem {
  label: string
  options?: Record<string, string>
}

const props = defineProps<{
  model: Record<string, any>
  /** 可选：声明需要生成 chip 摘要的字段，未声明的字段不生成 chip */
  filterMeta?: Record<string, FilterMetaItem>
}>()

const emit = defineEmits<{
  search: []
  reset: []
}>()

const activeChips = computed(() => {
  const meta = props.filterMeta
  if (!meta) return []
  return Object.entries(meta)
    .filter(([, item]) => item)
    .map(([key, item]) => {
      const value = props.model[key]
      if (value === '' || value === null || value === undefined) return null
      const text = item.options?.[String(value)] ?? String(value)
      return { key, label: item.label, text }
    })
    .filter((chip): chip is { key: string; label: string; text: string } => chip !== null)
})

function removeChip(key: string) {
  // model 为父组件传入的 reactive 查询对象，清空筛选属性是本组件的既定契约
  // eslint-disable-next-line vue/no-mutating-props
  props.model[key] = ''
  emit('search')
}
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

.filter-chips {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: var(--xfjy-space-2);
  margin-top: var(--xfjy-space-2);
  padding-top: var(--xfjy-space-2);
  border-top: 1px dashed var(--xfjy-border-light);
}

.filter-chips-label {
  color: var(--xfjy-text-secondary);
  font-size: var(--xfjy-font-size-xs);
}
</style>