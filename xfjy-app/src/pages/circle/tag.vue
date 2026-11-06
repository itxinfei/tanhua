<template>
  <view class="tag-page">
    <view class="tag-header">
      <text class="hash">#</text>
      <text class="tag-name">{{ tag }}</text>
    </view>

    <empty-state
      v-if="!loading && list.length === 0 && !loadFailed"
      title="暂无相关动态"
      desc="换个话题看看吧"
    />

    <!-- 加载失败：与「真的没动态」严格区分，给明确原因 + 可重试入口 -->
    <empty-state
      v-if="!loading && list.length === 0 && loadFailed"
      role="alert"
      icon="info"
      title="暂时没能获取话题动态"
      desc="服务可能开小差了，检查网络后重试"
    >
      <a11y-button class="tag-retry" label="重新加载话题动态" @tap="load(true)">重新加载</a11y-button>
    </empty-state>

    <a11y-button
      v-for="item in list"
      :key="item.id"
      class="post-card"
      :label="`查看动态：${item.nickname}，${item.textContent ? item.textContent.slice(0, 20) : '无文字内容'}`"
      @tap="goDetail(item)"
    >
      <view class="post-header">
        <image lazy-load class="avatar" :src="item.avatar" mode="aspectFill" :aria-label="`${item.nickname} 的头像`" />
        <view class="user-info">
          <text class="nickname">{{ item.nickname }}</text>
          <text class="meta">{{ item.createDate }}</text>
        </view>
      </view>
      <view class="post-content">{{ item.textContent }}</view>
      <view v-if="item.imageContent && item.imageContent.length" class="image-grid">
        <a11y-button
          v-for="(img, i) in item.imageContent.slice(0, 9)"
          :key="i"
          class="grid-img"
          :label="`查看第 ${i + 1} 张配图`"
          stop
          @tap="preview(item.imageContent, i)"
        >
          <image lazy-load class="grid-img-inner" :src="img" mode="aspectFill" aria-hidden="true" />
        </a11y-button>
      </view>
    </a11y-button>

    <a11y-button v-if="hasMore" class="loading" label="加载更多" @tap="loadMore">
      <text>加载更多</text>
    </a11y-button>
  </view>
</template>

<script setup lang="ts">
import { ref, onMounted } from "vue";
import EmptyState from "@/components/empty-state.vue";
import { onLoad } from "@dcloudio/uni-app";
import A11yButton from "@/components/a11y-button.vue";
import { searchMovementsByTag } from "@/api/circle";
import type { Movement } from "@/api/circle";

const tag = ref("");
const list = ref<Movement[]>([]);
const page = ref(1);
const pageSize = 10;
const hasMore = ref(false);
const loading = ref(false);
const loadFailed = ref(false);

async function load(reset = false): Promise<void> {
  if (loading.value) return;
  loading.value = true;
  if (reset) loadFailed.value = false;
  try {
    const current = reset ? 1 : page.value;
    const res = await searchMovementsByTag(tag.value, { page: current, pagesize: pageSize });
    const items = res.items || [];
    list.value = reset ? items : list.value.concat(items);
    hasMore.value = (res.pages || 1) > current;
    if (!reset) page.value = current + 1;
  } catch {
    // 首屏失败标记：页面据此显示错误态+重试，而非误报「暂无相关动态」
    if (reset) loadFailed.value = true;
    // request 层统一 toast
  } finally {
    loading.value = false;
  }
}

function loadMore(): void {
  if (hasMore.value) load(false);
}

function goDetail(item: Movement): void {
  uni.navigateTo({ url: `/pages/circle/detail?id=${item.id}` });
}

function preview(images: string[], index: number): void {
  uni.previewImage({ urls: images, current: images[index] });
}

onLoad((options: any) => {
  tag.value = decodeURIComponent(options?.tag || "");
  if (tag.value) load(true);
});
</script>

<style lang="scss" scoped>
.tag-page {
  min-height: 100vh;
  background: var(--bg-sub);
  padding: 24rpx;
}

.tag-header {
  display: flex;
  align-items: center;
  gap: 8rpx;
  padding: 16rpx 8rpx 24rpx;
  .hash {
    font-size: 36rpx;
    color: var(--color-primary);
    font-weight: bold;
  }
  .tag-name {
    font-size: 32rpx;
    font-weight: bold;
    color: var(--text-primary);
  }
}

.post-card {
  background: var(--bg-card);
  border-radius: 24rpx;
  padding: 24rpx;
  margin-bottom: 16rpx;
  box-shadow: 0 2rpx 12rpx rgba(0, 0, 0, 0.04);

  .post-header {
    display: flex;
    align-items: center;
    .avatar {
      width: 64rpx;
      height: 64rpx;
      border-radius: 50%;
    }
    .user-info {
      margin-left: 16rpx;
      .nickname {
        font-size: 28rpx;
        font-weight: 500;
        display: block;
      }
      .meta {
        font-size: 22rpx;
        color: var(--text-hint);
        margin-top: 4rpx;
        display: block;
      }
    }
  }

  .post-content {
    font-size: 28rpx;
    line-height: 1.6;
    margin: 16rpx 0;
    color: var(--text-primary);
  }

  .image-grid {
    display: flex;
    flex-wrap: wrap;
    gap: 8rpx;
    .grid-img {
      flex: 1 1 calc(33.333% - 8rpx);
      max-width: calc(33.333% - 8rpx);
      aspect-ratio: 1 / 1;
      border-radius: 12rpx;
      overflow: hidden;
    }
    .grid-img-inner { width: 100%; height: 100%; display: block; }
  }
}

/* 加载更多：热区 ≥88rpx（44px），原 padding 24rpx 仅 37px 高 */
.loading {
  display: flex;
  align-items: center;
  justify-content: center;
  min-height: 88rpx;
  padding: 0 24rpx;
  color: var(--color-primary);
  font-size: 26rpx;
  text-align: center;
}

/* 加载失败态的重试按钮（置于 empty-state 的 slot 内） */
.tag-retry {
  margin-top: 32rpx;
  padding: 16rpx 56rpx;
  border-radius: var(--radius-pill);
  font-size: 28rpx;
  font-weight: 600;
  color: var(--text-inverse);
  background: var(--gradient-brand);
}
</style>
