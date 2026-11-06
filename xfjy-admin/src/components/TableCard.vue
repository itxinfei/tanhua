<template>
  <div class="table-card">
    <div v-if="title || $slots.toolbar" class="table-card__header">
      <span v-if="title" class="table-card__title">{{ title }}</span>
      <div v-if="$slots.toolbar" class="table-card__toolbar">
        <slot name="toolbar" />
      </div>
    </div>
    <div v-if="$slots.batch" class="table-card__batch">
      <slot name="batch" />
    </div>
    <div class="table-card__body">
      <slot />
    </div>
    <div v-if="$slots.pagination" class="table-card__footer">
      <slot name="pagination" />
    </div>
  </div>
</template>

<script setup lang="ts">
import { provide, toRef, type Ref } from 'vue'

const props = defineProps<{
  title?: string
  emptyText?: string
}>()

provide('xfjyTableEmpty', {
  emptyText: toRef(props, 'emptyText') as Ref<string | undefined>,
})
</script>

<style scoped>
.table-card {
  background: var(--xfjy-bg-card);
  border: 1px solid var(--xfjy-border);
  border-radius: var(--xfjy-radius-lg);
  padding: var(--xfjy-space-4);
  box-shadow: var(--xfjy-shadow-card);
}

.table-card__header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: var(--xfjy-space-3);
}

.table-card__title {
  font-size: var(--xfjy-font-size-md);
  font-weight: 600;
  color: var(--xfjy-text-primary);
}

.table-card__toolbar {
  display: flex;
  align-items: center;
  gap: var(--xfjy-space-2);
}

.table-card__batch {
  display: flex;
  align-items: center;
  gap: var(--xfjy-space-2);
  margin-bottom: var(--xfjy-space-3);
  padding: var(--xfjy-space-2) var(--xfjy-space-3);
  background: var(--xfjy-primary-50);
  border-radius: var(--xfjy-radius-sm);
}

.table-card__footer {
  display: flex;
  justify-content: flex-end;
  margin-top: var(--xfjy-space-3);
}
</style>
