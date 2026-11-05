<template>
  <view class="agreement-page">
    <view class="title">{{ title }}</view>
    <scroll-view scroll-y class="content">
      <text class="paragraph" v-for="(p, i) in paragraphs" :key="i">{{ p }}</text>
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
  min-height: 100vh;
  background: var(--bg-card);
  display: flex;
  flex-direction: column;
}

.title {
  font-size: 34rpx;
  font-weight: bold;
  color: var(--text-primary);
  text-align: center;
  padding: 32rpx 32rpx 16rpx;
}

.content {
  flex: 1;
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
}
</style>
