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

      <!-- 主内容区：keep-alive 缓存列表页状态（搜索条件/分页），切回不丢 -->
      <el-main class="layout-main">
        <router-view v-slot="{ Component }">
          <transition name="fade" mode="out-in">
            <keep-alive>
              <component :is="Component" />
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
import { useAppStore } from '@/stores/app'

const appStore = useAppStore()
</script>

<style scoped>
.layout-container {
  height: 100vh;
}

.layout-aside {
  background-color: #001529;
  transition: width 0.28s;
  overflow: hidden;
}

.layout-header {
  background: #fff;
  border-bottom: 1px solid #e6e6e6;
  display: flex;
  align-items: center;
  padding: 0 20px;
  height: 56px;
  box-shadow: 0 1px 4px rgba(0, 21, 41, 0.08);
  z-index: 10;
}

.layout-main {
  background-color: #f0f2f5;
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