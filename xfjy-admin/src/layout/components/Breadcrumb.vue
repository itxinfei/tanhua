<template>
  <el-breadcrumb separator="/">
    <el-breadcrumb-item :to="{ path: '/dashboard' }">
      <el-icon><HomeFilled /></el-icon>
      首页
    </el-breadcrumb-item>
    <el-breadcrumb-item v-for="item in breadcrumbs" :key="item.path">
      {{ item.title }}
    </el-breadcrumb-item>
  </el-breadcrumb>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { useRoute } from 'vue-router'

const route = useRoute()

interface Crumb {
  path: string
  title: string
}

const breadcrumbs = computed<Crumb[]>(() => {
  const matched = route.matched.filter((item) => item.meta?.title)
  // 去重（Layout 路由可能匹配多次）
  const result: Crumb[] = []
  const seen = new Set<string>()
  for (const item of matched) {
    if (item.meta?.title && !seen.has(item.path)) {
      seen.add(item.path)
      result.push({ path: item.path, title: item.meta.title as string })
    }
  }
  // 移除第一项（首页已单独渲染）
  if (result.length > 0 && result[0].title === '数据看板') {
    return []
  }
  return result
})
</script>

<style scoped>
.el-breadcrumb {
  font-size: 14px;
}
</style>