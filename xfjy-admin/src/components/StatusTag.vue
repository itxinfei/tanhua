<template>
  <el-tag :type="tagType" size="small" effect="light">{{ label }}</el-tag>
</template>

<script setup lang="ts">
import { computed } from 'vue'

const props = defineProps<{
  status: string
  mapping: Record<string, { label: string; type: 'success' | 'warning' | 'danger' | 'info' | 'primary' }>
}>()

const tagType = computed(() => props.mapping[props.status]?.type || 'info')
const label = computed(() => props.mapping[props.status]?.label || props.status)
</script>
