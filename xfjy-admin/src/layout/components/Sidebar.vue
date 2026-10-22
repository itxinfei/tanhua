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

    <!-- 菜单 -->
    <el-scrollbar>
      <el-menu
        :default-active="activeMenu"
        :collapse="collapsed"
        :collapse-transition="false"
        background-color="#304156"
        text-color="#bfcbd9"
        active-text-color="#409EFF"
        router
      >
        <template v-for="route in menuRoutes" :key="route.path">
          <!-- 单子菜单 -->
          <el-menu-item
            v-if="route.children?.length === 1 && !route.children[0].meta?.hidden"
            :index="route.path + '/' + (route.children[0].path || '')"
          >
            <el-icon><component :is="route.children[0].meta?.icon || route.meta?.icon" /></el-icon>
            <template #title>{{ route.children[0].meta?.title }}</template>
          </el-menu-item>

          <!-- 多子菜单 -->
          <el-sub-menu v-else :index="route.path">
            <template #title>
              <el-icon><component :is="route.meta?.icon" /></el-icon>
              <span>{{ route.meta?.title }}</span>
            </template>
            <el-menu-item
              v-for="child in route.children?.filter((c) => !c.meta?.hidden)"
              :key="child.path"
              :index="route.path + '/' + child.path"
            >
              <el-icon><component :is="child.meta?.icon" /></el-icon>
              <template #title>{{ child.meta?.title }}</template>
            </el-menu-item>
          </el-sub-menu>
        </template>
      </el-menu>
    </el-scrollbar>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useAppStore } from '@/stores/app'
import { routes } from '@/router'

const route = useRoute()
const router = useRouter()
const appStore = useAppStore()

const collapsed = computed(() => appStore.sidebarCollapsed)

// 过滤出需要显示的菜单路由（排除登录页和404）
const menuRoutes = computed(() =>
  routes.filter((r) => !r.meta?.hidden && r.path !== '/login' && r.path !== '/:pathMatch(.*)*')
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
  background: #2b2f3a;
  flex-shrink: 0;
  overflow: hidden;
}

.logo-icon {
  width: 32px;
  height: 32px;
  object-fit: contain;
}

.logo-horizontal {
  height: 32px;
  max-width: 160px;
  object-fit: contain;
}

.el-scrollbar {
  flex: 1;
}

.el-menu {
  border-right: none;
}

:deep(.el-menu-item:hover),
:deep(.el-sub-menu__title:hover) {
  background-color: #263445 !important;
}

:deep(.el-menu-item.is-active) {
  background-color: #1f2d3d !important;
}
</style>