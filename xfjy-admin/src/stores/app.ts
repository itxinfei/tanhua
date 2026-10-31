import { defineStore } from 'pinia'
import { ref } from 'vue'

export const useAppStore = defineStore('app', () => {
  const sidebarCollapsed = ref(false)
  const device = ref<'desktop' | 'mobile'>('desktop')

  /** 局部刷新计数器：配合 layout 的 route key，仅销毁重建当前内容区组件 */
  const refreshKey = ref(0)

  function toggleSidebar() {
    sidebarCollapsed.value = !sidebarCollapsed.value
  }

  function setDevice(d: 'desktop' | 'mobile') {
    device.value = d
  }

  /** 局部刷新当前页：key 自增触发当前组件销毁重建并重新拉取数据，侧边栏/顶栏/登录态保留 */
  function refreshCurrentPage() {
    refreshKey.value++
  }

  return { sidebarCollapsed, device, refreshKey, toggleSidebar, setDevice, refreshCurrentPage }
})