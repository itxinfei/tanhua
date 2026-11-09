<template>
  <div class="dash-panel">
    <div class="dash-panel__head">
      <div class="dash-panel__heading">
        <span class="dash-panel__title">{{ title }}</span>
        <span
          v-if="sub"
          class="dash-panel__sub"
        >{{ sub }}</span>
      </div>
      <div
        v-if="$slots.extra"
        class="dash-panel__extra"
      >
        <slot name="extra" />
      </div>
    </div>
    <div class="dash-panel__body">
      <slot />
    </div>
  </div>
</template>

<script setup lang="ts">
defineProps<{
  title: string
  sub?: string
}>()
</script>

<style scoped>
.dash-panel {
  display: flex;
  flex-direction: column;
  height: 100%;
  padding: var(--xfjy-space-5);
  background: var(--xfjy-bg-card);
  border: 1px solid var(--xfjy-border);
  border-radius: var(--xfjy-radius-lg);
}

.dash-panel__head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: var(--xfjy-space-3);
  margin-bottom: var(--xfjy-space-4);
}

.dash-panel__heading {
  min-width: 0;
}

.dash-panel__title {
  font-size: var(--xfjy-font-size-md);
  font-weight: 600;
  color: var(--xfjy-text-primary);
}

.dash-panel__sub {
  display: block;
  margin-top: 2px;
  font-size: var(--xfjy-font-size-xs);
  color: var(--xfjy-text-secondary);
  font-variant-numeric: tabular-nums;
}

.dash-panel__extra {
  flex-shrink: 0;
}

.dash-panel__body {
  flex: 1;
  min-height: 0;
}
</style>
