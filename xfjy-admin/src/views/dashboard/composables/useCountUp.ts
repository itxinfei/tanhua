/**
 * 数字滚动动画：source 变化时从旧值缓动到新值（easeOutCubic）
 * 用于 KPI 卡与实时在线大数字，增强"实时刷新"的感知
 */
import { ref, watch, onBeforeUnmount, type Ref } from 'vue'

export function useCountUp(source: Ref<number | null | undefined>, duration = 600) {
  const display = ref<number | null>(source.value ?? null)
  let rafId = 0

  watch(
    source,
    (to) => {
      if (rafId) cancelAnimationFrame(rafId)
      if (to === null || to === undefined) {
        display.value = null
        return
      }
      const startValue = display.value ?? 0
      const beginAt = performance.now()

      const tick = (now: number) => {
        const p = Math.min(1, (now - beginAt) / duration)
        const eased = 1 - Math.pow(1 - p, 3)
        display.value = Math.round(startValue + (to - startValue) * eased)
        if (p < 1) {
          rafId = requestAnimationFrame(tick)
        }
      }
      rafId = requestAnimationFrame(tick)
    },
    { deep: false }
  )

  onBeforeUnmount(() => {
    if (rafId) cancelAnimationFrame(rafId)
  })

  return display
}
