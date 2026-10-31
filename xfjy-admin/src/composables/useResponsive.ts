/**
 * 响应式列可见性 composable
 *
 * 背景：Element Plus 2.14.7 无 el-table-column 的 `responsive`/`visual-hide` prop
 * （2.19+ 才引入），CSS display:none 隐藏列会导致 colgroup 保留宽度、表头与
 * 正文错位。因此改用 v-if：隐藏的列不进 DOM，表格按剩余可见列正确重算宽度。
 *
 * 用法：
 *   const { isMobile } = useResponsive()
 *   <el-table-column v-if="!isMobile" prop="city" label="城市" />
 *
 * 断点语义（与 style.css 媒体查询一致）：
 *   isMobile  : 视口 < 768px（手机）
 *   isTablet  : 768px ≤ 视口 < 992px
 *   isDesktop : 视口 ≥ 992px
 */
import { ref, computed, onMounted, onUnmounted } from 'vue'

/** 断点定义，与 style.css --xfjy-bp-* 保持一致 */
const BREAKPOINTS = {
  mobile: 768,
  tablet: 992,
} as const

/** 全局单例宽度状态（所有组件共享，避免每个表格各自监听 resize） */
const windowWidth = ref(typeof window !== 'undefined' ? window.innerWidth : 1920)

/** resize 回调集合（多个组件复用同一监听器） */
let resizeHandler: (() => void) | null = null
let watcherCount = 0

function handleResize() {
  windowWidth.value = window.innerWidth
}

/** 创建响应式可见性集合（组件 setup 中调用，自动注册/注销全局监听） */
export function useResponsive() {
  onMounted(() => {
    watcherCount++
    if (!resizeHandler) {
      resizeHandler = handleResize
      window.addEventListener('resize', resizeHandler)
    }
  })

  onUnmounted(() => {
    watcherCount--
    // 最后一个使用者注销时移除全局监听，避免内存泄漏
    if (watcherCount <= 0 && resizeHandler) {
      window.removeEventListener('resize', resizeHandler)
      resizeHandler = null
      watcherCount = 0
    }
  })

  return {
    isMobile: computed(() => windowWidth.value < BREAKPOINTS.mobile),
    isTablet: computed(
      () => windowWidth.value >= BREAKPOINTS.mobile && windowWidth.value < BREAKPOINTS.tablet
    ),
    isDesktop: computed(() => windowWidth.value >= BREAKPOINTS.tablet),
  }
}
