<template>
  <div class="page-header">
    <div class="page-header__main">
      <h1 class="page-header__title">{{ title }}</h1>
      <p v-if="subtitle" class="page-header__subtitle">{{ subtitle }}</p>
    </div>
    <div v-if="$slots.actions" class="page-header__actions">
      <slot name="actions" />
    </div>
  </div>
</template>

<script setup lang="ts">
defineProps<{
  title: string
  subtitle?: string
}>()
</script>

<style scoped>
.page-header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: var(--xfjy-space-4);
}

.page-header__title {
  font-size: var(--xfjy-font-size-lg);
  font-weight: 600;
  color: var(--xfjy-text-primary);
  line-height: 1.4;
}

.page-header__subtitle {
  font-size: var(--xfjy-font-size-sm);
  color: var(--xfjy-text-secondary);
  margin-top: var(--xfjy-space-1);
}

.page-header__actions {
  display: flex;
  align-items: center;
  gap: var(--xfjy-space-2);
  flex-shrink: 0;
}
</style>
