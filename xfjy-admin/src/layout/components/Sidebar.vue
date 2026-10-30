<template>
  <div class="sidebar-wrapper">
    <!-- Logo -->
    <div class="sidebar-logo" @click="router.push('/dashboard')">
      <img
        v-if="collapsed"
        class="logo-icon"
        src="/logo/logo-icon-transparent.png"
        alt="心飞交友"
      />
      <img
        v-else
        class="logo-horizontal"
        src="/logo/logo-horizontal-reverse-transparent.png"
        alt="心飞交友后台"
      />
    </div>

    <!-- 菜单（若依风格：el-sub-menu 分组，折叠后 hover 弹出） -->
    <el-scrollbar>
      <el-menu
        :default-active="activeMenu"
        :collapse="collapsed"
        :collapse-transition="false"
        :unique-opened="true"
        background-color="#001529"
        text-color="rgba(255,255,255,0.65)"
        active-text-color="#ffffff"
        router
        class="sidebar-menu"
      >
        <template v-for="route in menuRoutes" :key="route.path">
          <!-- 可见子项为 0：跳过（防御空菜单） -->
          <template v-if="visibleChildrenOf(route).length > 0">
            <!-- 单可见子项：顶级菜单项（若依单路由平铺） -->
            <el-menu-item
              v-if="visibleChildrenOf(route).length === 1"
              :index="route.path + '/' + (visibleChildrenOf(route)[0].path || '')"
            >
              <el-icon><component :is="visibleChildrenOf(route)[0].meta?.icon || route.meta?.icon" /></el-icon>
              <template #title>{{ visibleChildrenOf(route)[0].meta?.title }}</template>
            </el-menu-item>

            <!-- 多可见子项：el-sub-menu 分组（若依风格，折叠后 hover 弹出 popup） -->
            <el-sub-menu v-else :index="route.path">
              <template #title>
                <el-icon><component :is="route.meta?.icon" /></el-icon>
                <span>{{ route.meta?.title }}</span>
              </template>
              <el-menu-item
                v-for="child in visibleChildrenOf(route)"
                :key="child.path"
                :index="route.path + '/' + child.path"
              >
                <el-icon><component :is="child.meta?.icon" /></el-icon>
                <template #title>{{ child.meta?.title }}</template>
              </el-menu-item>
            </el-sub-menu>
          </template>
        </template>
      </el-menu>
    </el-scrollbar>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useAppStore } from '@/stores/app'
import { useAuthStore } from '@/stores/auth'
import { routes } from '@/router'

const route = useRoute()
const router = useRouter()
const appStore = useAppStore()
const authStore = useAuthStore()

const collapsed = computed(() => appStore.sidebarCollapsed)

// 过滤出需要显示的菜单路由（排除登录页和404）
const menuRoutes = computed(() =>
  routes.filter((r) => !r.meta?.hidden && r.path !== '/login' && r.path !== '/:pathMatch(.*)*')
)

// 提取路由中非 hidden 且当前角色有权访问的可见子项
const visibleChildrenOf = (r: (typeof routes)[number]) =>
  (r.children || []).filter(
    (c) => !c.meta?.hidden && authStore.hasRole(c.meta?.roles as string[] | undefined)
  )

// 当前激活菜单
const activeMenu = computed(() => {
  return (route.meta?.activeMenu as string) || route.path
})
</script>

<style scoped>
.sidebar-wrapper {
  height: 100%;
  display: flex;
  flex-direction: column;
}

.sidebar-logo {
  height: 56px;
  display: flex;
  align-items: center;
  justify-content: center;
  cursor: pointer;
  background: #002140;
  flex-shrink: 0;
  overflow: hidden;
}

.logo-icon {
  width: 32px;
  height: 32px;
  object-fit: contain;
}

.logo-horizontal {
  height: 30px;
  max-width: 160px;
  object-fit: contain;
}

.el-scrollbar {
  flex: 1;
}

/* ==================== 若依深色主题 ==================== */
.sidebar-menu {
  border-right: none;
}

/* 顶级菜单项 hover：若依蓝色微透 */
:deep(.el-menu-item:hover) {
  background-color: rgba(64, 158, 255, 0.15) !important;
  color: #ffffff !important;
}

/* 顶级菜单项激活：若依主色背景 */
:deep(.el-menu-item.is-active) {
  background-color: #409eff !important;
  color: #ffffff !important;
}

/* sub-menu 标题 hover */
:deep(.el-sub-menu__title:hover) {
  background-color: rgba(64, 158, 255, 0.15) !important;
  color: #ffffff !important;
}

/* 展开的 sub-menu 子项区域：若依更深底色 */
:deep(.el-sub-menu .el-menu) {
  background-color: #000c17 !important;
}

/* 子菜单项 hover */
:deep(.el-sub-menu .el-menu-item:hover) {
  background-color: rgba(64, 158, 255, 0.15) !important;
}

/* 子菜单项激活 */
:deep(.el-sub-menu .el-menu-item.is-active) {
  background-color: #409eff !important;
}

/* 折叠态 popup 弹层样式（hover 弹出的子菜单面板） */
:deep(.el-menu--popup) {
  background-color: #001529 !important;
}

:deep(.el-menu--popup .el-menu-item:hover) {
  background-color: rgba(64, 158, 255, 0.15) !important;
}

:deep(.el-menu--popup .el-menu-item.is-active) {
  background-color: #409eff !important;
}

/* 折叠态下 sub-menu 标题居中 */
:deep(.el-menu--collapse .el-sub-menu__title) {
  padding: 0 18px !important;
}
</style>