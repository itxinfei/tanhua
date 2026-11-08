<template>
  <div class="table-skeleton" aria-busy="true" aria-label="加载中">
    <!-- 表头骨架：矮行 + 深一档底色 -->
    <div class="table-skeleton__header">
      <span
        v-for="(w, i) in columnWidths"
        :key="i"
        class="sk-bar"
        :style="{ width: w }"
      />
    </div>
    <!-- 数据行骨架：紧凑行高对齐表格 44px -->
    <div
      v-for="row in rows"
      :key="row"
      class="table-skeleton__row"
    >
      <span
        v-for="(w, i) in columnWidths"
        :key="i"
        class="sk-bar"
        :style="{ width: w, animationDelay: `${(row * columnWidths.length + i) * 60}ms` }"
      />
    </div>
  </div>
</template>

<script setup lang="ts">
/** 表格骨架屏：首屏加载时用结构化骨架替代 spinner，
 *  让用户提前感知"表格即将出现"，而非盯着旋转圆圈猜布局。 */

withDefaults(
  defineProps<{
    /** 骨架行数，默认 8 行（对齐默认分页 pageSize 感知） */
    rows?: number
  }>(),
  { rows: 8 },
)

/** 列宽组合：模拟真实表格的列宽节奏（非等宽条），更接近最终布局 */
const columnWidths = ['28%', '14%', '18%', '14%', '16%']
</script>

<style scoped>
.table-skeleton {
  padding: var(--xfjy-space-2) 0;
}

.table-skeleton__header {
  display: flex;
  gap: var(--xfjy-space-3);
  align-items: center;
  height: 36px;
  padding: 0 var(--xfjy-table-cell-padding-x);
  border-bottom: 1px solid var(--xfjy-border);
}

.table-skeleton__row {
  display: flex;
  gap: var(--xfjy-space-3);
  align-items: center;
  height: var(--xfjy-table-row-height);
  padding: 0 var(--xfjy-table-cell-padding-x);
  border-bottom: 1px solid var(--xfjy-border-light);
}

.table-skeleton__header .sk-bar {
  height: 10px;
  background: var(--xfjy-border);
  border-radius: var(--xfjy-radius-sm);
}

.table-skeleton__row .sk-bar {
  height: 12px;
  background: var(--xfjy-bg-hover);
  border-radius: var(--xfjy-radius-sm);
}

/* shimmer：微光从左扫到右，1.8s 循环，传达"正在加载"而非装饰 */
.sk-bar {
  position: relative;
  overflow: hidden;
  flex-shrink: 0;
}

.sk-bar::after {
  content: '';
  position: absolute;
  inset: 0;
  background: linear-gradient(
    90deg,
    transparent,
    rgba(255, 255, 255, 0.65),
    transparent
  );
  animation: sk-shimmer 1.8s ease-in-out infinite;
}

@keyframes sk-shimmer {
  0% {
    transform: translateX(-100%);
  }
  100% {
    transform: translateX(100%);
  }
}

/* reduced-motion：shimmer 降级为静态灰块（无扫光） */
@media (prefers-reduced-motion: reduce) {
  .sk-bar::after {
    animation: none;
  }
}
</style>