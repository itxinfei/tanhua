<template>
  <el-empty :description="resolvedDescription" :image-size="80">
    <slot />
  </el-empty>
</template>

<script setup lang="ts">
import { computed, inject, type Ref } from 'vue'

const props = defineProps<{
  /** 空态描述文案；不传则自动继承最近 TableCard 的 empty-text，再退化为「暂无数据」 */
  description?: string
}>()

// 自动继承最近 TableCard 的 emptyText 配置（若存在），实现「一页一处配置」
const tableCardCtx = inject<{ emptyText: Ref<string | undefined> } | null>('xfjyTableEmpty', null)

const resolvedDescription = computed(
  () => props.description ?? tableCardCtx?.emptyText.value ?? '暂无数据'
)
</script>