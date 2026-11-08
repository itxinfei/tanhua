<template>
  <view class="agreement-page">
    <view class="title">
      {{ title }}
    </view>
    <scroll-view
      scroll-y
      class="content"
    >
      <text
        v-for="(p, i) in paragraphs"
        :key="i"
        class="paragraph"
        :class="{ 'paragraph--heading': isHeading(p) }"
      >
        {{ p }}
      </text>
    </scroll-view>
  </view>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import { onLoad } from '@dcloudio/uni-app'
import { getAgreement } from '@/api/agreement'
import { AGREEMENT_CONTENT } from '@/config/agreement-content'

// 本地兜底文本统一在 @/config/agreement-content 维护（登录页弹窗与整页共用一份）

const title = ref('协议')
const paragraphs = ref<string[]>([])

/** 识别章节标题段（如「一、」「二、」「第一条」），用于视觉分层 */
function isHeading(text: string): boolean {
  return /^[一二三四五六七八九十]+、/.test(text) || /^第[一二三四五六七八九十]+条/.test(text)
}

onLoad((query: any) => {
  const rawType = (query && query.type) || 'user'
  const type: 'USER' | 'PRIVACY' = rawType === 'privacy' ? 'PRIVACY' : 'USER'
  const fallback = AGREEMENT_CONTENT[rawType] || AGREEMENT_CONTENT.user

  // 先渲染本地兜底文本，避免接口未就绪时白屏
  title.value = fallback.title
  paragraphs.value = fallback.paragraphs

  // 再尝试拉取后台已发布协议，成功则用线上版本覆盖
  getAgreement(type)
    .then((res) => {
      if (res && res.content) {
        title.value = res.title || fallback.title
        paragraphs.value = res.content
          .split('\n')
          .map((s: string) => s.trim())
          .filter((s: string) => s.length > 0)
      }
    })
    .catch(() => {
      // 接口失败（如后端未部署），保持本地兜底文本
    })
})
</script>

<style lang="scss" scoped>
.agreement-page {
  /* 固定高度而非 min-height：scroll-view 需父容器有界才能滚动，
     否则被内容撑高后 overflow 失效，长协议无法滚动阅读。
     原生导航页必须同时减去导航栏与全局 tabbar，否则底部溢出 44px */
  height: calc(100vh - var(--window-top, 0px) - var(--tab-bar-height, 0px));
  background: var(--bg-card);
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

.title {
  font-size: 34rpx;
  font-weight: bold;
  color: var(--text-primary);
  text-align: center;
  padding: 32rpx 32rpx 16rpx;
}

.content {
  /* uni-app H5 端 scroll-view 需要显式高度才会启用滚动；
     flex:1 只给 flex-basis，内容会撑高 clientHeight 导致 overflow 失效 */
  flex: 1;
  min-height: 0;
  height: 100%;
  padding: 0 32rpx 48rpx;
  box-sizing: border-box;
}

.paragraph {
  display: block;
  font-size: 28rpx;
  line-height: 1.8;
  color: var(--text-secondary);
  margin-bottom: 20rpx;
  text-align: justify;
  /* 长 URL/英文连续字符换行：justify 对齐会产生难看的字间距，break-all 保证可读 */
  word-break: break-all;
}

/* 章节标题段加粗 + 深色，长文阅读时提供视觉锚点（替代 TOC 导航） */
.paragraph--heading {
  font-weight: 600;
  color: var(--text-primary);
  margin-top: 12rpx;
}
</style>
