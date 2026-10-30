<template>
  <div class="not-found">
    <!-- 背景星星 -->
    <div class="stars">
      <span v-for="n in 20" :key="n" class="star" :style="starStyle(n)"></span>
    </div>

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
          <circle class="dot dot-1" cx="48" cy="140" r="5" fill="#f5576c" />
          <circle class="dot dot-2" cx="278" cy="140" r="4" fill="#43e97b" />
          <circle class="dot dot-3" cx="290" cy="100" r="3" fill="#fa709a" />
          <defs>
            <linearGradient id="planetGrad" x1="0%" y1="0%" x2="100%" y2="100%">
              <stop offset="0%" style="stop-color: var(--xfjy-primary-deep)" />
              <stop offset="100%" style="stop-color: var(--xfjy-primary)" />
            </linearGradient>
            <linearGradient id="moonGrad" x1="0%" y1="0%" x2="100%" y2="100%">
              <stop offset="0%" style="stop-color: var(--xfjy-primary-soft)" />
              <stop offset="100%" style="stop-color: var(--el-color-primary-light-7)" />
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

// 生成随机分布的星星样式
function starStyle(n: number) {
  const seed = n * 137.5
  const left = (seed * 7.3) % 100
  const top = (seed * 3.7) % 100
  const size = 2 + ((seed * 1.3) % 3)
  const delay = ((seed * 0.7) % 3).toFixed(1)
  return {
    left: left + '%',
    top: top + '%',
    width: size + 'px',
    height: size + 'px',
    animationDelay: `-${delay}s`,
  }
}
</script>

<style scoped>
.not-found {
  height: 100vh;
  display: flex;
  align-items: center;
  justify-content: center;
  background: linear-gradient(160deg, #f5f7ff 0%, #f0f2f5 60%, var(--el-color-primary-light-9) 100%);
  position: relative;
  overflow: hidden;
}

/* 背景星星 */
.stars {
  position: absolute;
  inset: 0;
  pointer-events: none;
}

.star {
  position: absolute;
  border-radius: 50%;
  background: #c5cae9;
  opacity: 0.5;
  animation: twinkle 3s ease-in-out infinite;
}

@keyframes twinkle {
  0%, 100% { opacity: 0.15; transform: scale(0.8); }
  50% { opacity: 0.6; transform: scale(1.2); }
}

/* 内容区 */
.nf-content {
  position: relative;
  z-index: 1;
  text-align: center;
}

.nf-illustration {
  animation: floatY 4s ease-in-out infinite;
}

.planet-main {
  animation: rotateSlow 12s linear infinite;
  transform-origin: 160px 100px;
}

.satellite {
  animation: floatY 3s ease-in-out infinite reverse;
}

.float-search {
  animation: floatY 5s ease-in-out infinite;
  animation-delay: -1s;
}

.dot-1 { animation: floatY 3.5s ease-in-out infinite; animation-delay: -0.5s; }
.dot-2 { animation: floatY 4.5s ease-in-out infinite; animation-delay: -1.5s; }
.dot-3 { animation: floatY 3s ease-in-out infinite; animation-delay: -2s; }

@keyframes floatY {
  0%, 100% { transform: translateY(0); }
  50% { transform: translateY(-12px); }
}

@keyframes rotateSlow {
  from { transform: rotate(0deg); }
  to { transform: rotate(360deg); }
}

.nf-code {
  margin-top: 8px;
  font-size: 96px;
  font-weight: 800;
  line-height: 1;
  background: linear-gradient(135deg, var(--xfjy-primary-deep), var(--xfjy-primary));
  -webkit-background-clip: text;
  background-clip: text;
  -webkit-text-fill-color: transparent;
  letter-spacing: 4px;
}

.nf-title {
  margin-top: 12px;
  font-size: 22px;
  font-weight: 600;
  color: #303133;
}

.nf-desc {
  margin-top: 8px;
  font-size: 14px;
  color: #909399;
}

.nf-actions {
  margin-top: 32px;
  display: flex;
  justify-content: center;
  gap: 12px;
}

.mr-1 {
  margin-right: 4px;
}
</style>