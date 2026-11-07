<template>
  <div class="not-found">
    <div class="nf-content">
      <!-- SVG 插画：迷失的星球 -->
      <div class="nf-illustration">
        <svg viewBox="0 0 320 200" width="320" height="200" xmlns="http://www.w3.org/2000/svg">
          <!-- 大星球 -->
          <g class="planet-main">
            <circle cx="160" cy="100" r="52" fill="url(#planetGrad)" />
            <ellipse cx="160" cy="100" rx="78" ry="14" fill="none" stroke-width="3" style="stroke: var(--xfjy-primary-deep); stroke-opacity: 0.35;" />
            <!-- 星球表面纹理 -->
            <circle cx="142" cy="86" r="9" fill="rgba(255,255,255,0.18)" />
            <circle cx="176" cy="112" r="13" fill="rgba(255,255,255,0.12)" />
            <circle cx="168" cy="78" r="5" fill="rgba(255,255,255,0.15)" />
          </g>
          <!-- 小卫星 -->
          <g class="satellite">
            <circle cx="252" cy="52" r="14" fill="url(#moonGrad)" />
            <circle cx="248" cy="48" r="4" fill="rgba(255,255,255,0.25)" />
          </g>
          <!-- 漂浮的搜索图标（表达"找不到页面"） -->
          <g class="float-search">
            <circle cx="62" cy="60" r="16" fill="none" stroke-width="4" style="stroke: var(--xfjy-primary-deep)" />
            <line x1="74" y1="72" x2="86" y2="84" stroke-width="4" stroke-linecap="round" style="stroke: var(--xfjy-primary-deep)" />
            <text x="56" y="66" font-size="14" font-weight="bold" style="fill: var(--xfjy-primary-deep)">?</text>
          </g>
          <!-- 漂浮的小圆点 -->
          <circle class="dot dot-1" cx="48" cy="140" r="5" style="fill: var(--xfjy-chart-6)" />
          <circle class="dot dot-2" cx="278" cy="140" r="4" style="fill: var(--xfjy-chart-3)" />
          <circle class="dot dot-3" cx="290" cy="100" r="3" style="fill: var(--xfjy-chart-1)" />
          <defs>
            <linearGradient id="planetGrad" x1="0%" y1="0%" x2="100%" y2="100%">
              <stop offset="0%" style="stop-color: var(--xfjy-primary-deep)" />
              <stop offset="100%" style="stop-color: var(--xfjy-primary)" />
            </linearGradient>
            <linearGradient id="moonGrad" x1="0%" y1="0%" x2="100%" y2="100%">
              <stop offset="0%" style="stop-color: var(--xfjy-primary-soft)" />
              <stop offset="100%" style="stop-color: var(--xfjy-primary-200)" />
            </linearGradient>
          </defs>
        </svg>
      </div>

      <h1 class="nf-code">404</h1>
      <p class="nf-title">页面走丢了</p>
      <p class="nf-desc">抱歉，您访问的页面不存在或已被移除</p>

      <div class="nf-actions">
        <el-button type="primary" size="large" @click="router.push('/dashboard')">
          <el-icon class="mr-1"><HomeFilled /></el-icon>
          返回首页
        </el-button>
        <el-button size="large" @click="goBack">
          <el-icon class="mr-1"><Back /></el-icon>
          返回上页
        </el-button>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { useRouter } from 'vue-router'
const router = useRouter()

function goBack() {
  if (window.history.length > 1) {
    router.back()
  } else {
    router.push('/dashboard')
  }
}
</script>

<style scoped>
.not-found {
  height: 100vh;
  display: flex;
  align-items: center;
  justify-content: center;
  background: linear-gradient(160deg, var(--xfjy-bg-page) 0%, var(--xfjy-primary-50) 100%);
  position: relative;
  overflow: hidden;
}

/* 内容区 */
.nf-content {
  position: relative;
  z-index: 1;
  text-align: center;
}

.nf-code {
  margin-top: var(--xfjy-space-2);
  font-size: 96px;
  font-weight: 800;
  line-height: 1;
  color: var(--xfjy-primary);
  letter-spacing: 4px;
}

.nf-title {
  margin-top: var(--xfjy-space-3);
  font-size: 22px;
  font-weight: 600;
  color: var(--xfjy-text-primary);
}

.nf-desc {
  margin-top: var(--xfjy-space-2);
  font-size: var(--xfjy-font-size-base);
  color: var(--xfjy-text-secondary);
}

.nf-actions {
  margin-top: var(--xfjy-space-8);
  display: flex;
  justify-content: center;
  gap: var(--xfjy-space-3);
}

.mr-1 {
  margin-right: var(--xfjy-space-1);
}
</style>