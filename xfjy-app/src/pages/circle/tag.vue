<template>
  <view class="tag-page">
    <view class="tag-header">
      <text class="hash">#</text>
      <text class="tag-name">{{ tag }}</text>
    </view>

    <empty-state
      v-if="!loading && list.length === 0"
      title="暂无相关动态"
      desc="换个话题看看吧"
    />

    <view
      v-for="item in list"
      :key="item.id"
      class="post-card"
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
        <image lazy-load
          v-for="(img, i) in item.imageContent.slice(0, 9)"
          :key="i"
          class="grid-image"
          :src="img"
          mode="aspectFill"
          @tap.stop="preview(item.imageContent, i)"
        />
      </view>
    </view>

    <view v-if="hasMore" class="loading" @tap="loadMore">加载更多</view>
  </view>
</template>

<script setup lang="ts">
import { ref, onMounted } from "vue";
import EmptyState from "@/components/empty-state.vue";
import { onLoad } from "@dcloudio/uni-app";
import { searchMovementsByTag } from "@/api/circle";
import type { Movement } from "@/api/circle";

const tag = ref("");
const list = ref<Movement[]>([]);
const page = ref(1);
const pageSize = 10;
const hasMore = ref(false);
const loading = ref(false);

async function load(reset = false): Promise<void> {
  if (loading.value) return;
  loading.value = true;
  try {
    const current = reset ? 1 : page.value;
    const res = await searchMovementsByTag(tag.value, { page: current, pagesize: pageSize });
    const items = res.items || [];
    list.value = reset ? items : list.value.concat(items);
    hasMore.value = (res.pages || 1) > current;
    if (!reset) page.value = current + 1;
  } catch {
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
    .grid-image {
      width: 200rpx;
      height: 200rpx;
      border-radius: 8rpx;
    }
  }
}

.loading {
  text-align: center;
  padding: 24rpx;
  color: var(--color-primary);
  font-size: 26rpx;
}

