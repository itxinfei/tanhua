<template>
  <el-container class="layout-container">
    <!-- 侧边栏 -->
    <el-aside :width="appStore.sidebarCollapsed ? '54px' : '210px'" class="layout-aside">
      <Sidebar />
    </el-aside>

    <el-container>
      <!-- 顶栏 -->
      <el-header class="layout-header">
        <Header />
      </el-header>

      <!-- 主内容区：keep-alive 缓存列表页状态（搜索条件/分页），切回不丢；
           :key 绑定路由+refreshKey，顶栏刷新按钮触发局部重建当前组件而非整页 reload -->
      <el-main class="layout-main">
        <router-view v-slot="{ Component }">
          <transition name="fade" mode="out-in">
            <keep-alive :max="12">
              <component :is="Component" :key="route.fullPath + '-' + appStore.refreshKey" />
            </keep-alive>
          </transition>
        </router-view>
      </el-main>
    </el-container>
  </el-container>
</template>

<script setup lang="ts">
import Sidebar from './components/Sidebar.vue'
import Header from './components/Header.vue'
import { useRoute } from 'vue-router'
import { useAppStore } from '@/stores/app'

const appStore = useAppStore()
const route = useRoute()
</script>

<style scoped>
.layout-container {
  height: 100vh;
}

.layout-aside {
  background-color: var(--xfjy-sidebar-bg);
  border-right: 1px solid var(--xfjy-sidebar-border);
  transition: width 0.28s;
  overflow: hidden;
}

.layout-header {
  background: var(--xfjy-header-bg);
  border-bottom: 1px solid var(--xfjy-border);
  display: flex;
  align-items: center;
  padding: 0 var(--xfjy-space-5);
  height: var(--xfjy-header-height);
  box-shadow: var(--xfjy-header-shadow);
  z-index: 10;
}

.layout-main {
  background-color: var(--xfjy-bg-page);
  padding: 0;
  overflow-y: auto;
}

.fade-enter-active,
.fade-leave-active {
  transition: opacity 0.2s ease;
}

.fade-enter-from,
.fade-leave-to {
  opacity: 0;
}
</style>