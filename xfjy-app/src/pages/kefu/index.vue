<template>
  <view class="kefu-page">
    <!-- FAQ 搜索框 -->
    <view class="search-bar">
      <view class="search-input-wrap">
        <text class="search-icon">🔍</text>
        <input
          v-model="keyword"
          class="search-input"
          placeholder="搜索常见问题关键词"
          :maxlength="30"
          confirm-type="search"
        />
        <text v-if="keyword" class="search-clear" @click="keyword = ''">✕</text>
      </view>
    </view>

    <scroll-view class="faq-scroll" scroll-y>
      <view v-if="groupedList.length === 0 && !loading" class="empty-state">
        <view class="empty-icon"><text>💬</text></view>
        <text class="empty-title">{{ keyword ? '未找到相关问题' : '暂无常见问题' }}</text>
        <text class="empty-desc">{{ keyword ? '换个关键词，或直接咨询在线客服' : '可直接提交意见反馈联系我们' }}</text>
      </view>

      <view v-for="group in groupedList" :key="group.category" class="faq-group">
        <view class="group-title">
          <text class="group-tag">{{ group.category }}</text>
        </view>
        <view
          v-for="item in group.items"
          :key="item.id"
          class="faq-item shadow-card"
          @click="toggleItem(item.id)"
        >
          <view class="faq-question">
            <text class="q-text">{{ item.question }}</text>
            <text class="q-arrow" :class="{ expanded: expandedId === item.id }">›</text>
          </view>
          <view v-if="expandedId === item.id" class="faq-answer">
            <text>{{ item.answer }}</text>
          </view>
        </view>
      </view>

      <view v-if="loading" class="loading-tip">
        <text>加载中...</text>
      </view>
    </scroll-view>

    <!-- 底部入口：在线咨询 + 意见反馈 -->
    <view class="bottom-bar safe-area-bottom">
      <view class="entry-btn btn-feedback consult" @click="goChat">
        <text>🎧 在线咨询</text>
      </view>
      <view class="entry-btn btn-feedback" @click="goFeedback">
        <text>意见反馈</text>
      </view>
    </view>
  </view>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from "vue";
import { getFaqListApi, type FaqItem } from "@/api/kefu";

const faqList = ref<FaqItem[]>([]);
const loading = ref(false);
/** 搜索关键词（本地过滤 question/answer） */
const keyword = ref("");
/** 当前展开的 FAQ id（手风琴：同时只展开一个） */
const expandedId = ref<number | null>(null);

/** 按关键词过滤后的列表 */
const filteredList = computed(() => {
  const kw = keyword.value.trim().toLowerCase();
  if (!kw) return faqList.value;
  return faqList.value.filter(
    (item) =>
      item.question.toLowerCase().includes(kw) ||
      item.answer.toLowerCase().includes(kw)
  );
});

/** 按分类分组（基于过滤后列表） */
const groupedList = computed(() => {
  const map = new Map<string, FaqItem[]>();
  for (const item of filteredList.value) {
    const key = item.category || "其他";
    if (!map.has(key)) {
      map.set(key, []);
    }
    map.get(key)!.push(item);
  }
  return Array.from(map.entries()).map(([category, items]) => ({ category, items }));
});

onMounted(() => {
  loadFaqList();
});

async function loadFaqList(): Promise<void> {
  loading.value = true;
  try {
    faqList.value = await getFaqListApi();
  } catch {
    // 错误提示由 request 封装统一处理
  } finally {
    loading.value = false;
  }
}

/** 展开/收起 FAQ（手风琴） */
function toggleItem(id: number): void {
  expandedId.value = expandedId.value === id ? null : id;
}

/** 跳转在线客服聊天页 */
function goChat(): void {
  uni.navigateTo({ url: "/pages/kefu/chat" });
}

/** 跳转意见反馈页 */
function goFeedback(): void {
  uni.navigateTo({ url: "/pages/kefu/feedback" });
}
</script>

<style lang="scss" scoped>
.kefu-page {
  height: 100vh;
  background: #f5f5f5;
  display: flex;
  flex-direction: column;
}

.search-bar {
  padding: 16rpx 24rpx 4rpx;
  background: #f5f5f5;

  .search-input-wrap {
    display: flex;
    align-items: center;
    background: #fff;
    border-radius: 36rpx;
    padding: 0 24rpx;
    height: 72rpx;
    gap: 12rpx;

    .search-icon {
      font-size: 28rpx;
      flex-shrink: 0;
    }

    .search-input {
      flex: 1;
      font-size: 26rpx;
      color: #333;
    }

    .search-clear {
      font-size: 28rpx;
      color: #ccc;
      padding: 8rpx;
      flex-shrink: 0;
    }
  }
}

.faq-scroll {
  flex: 1;
  padding: 20rpx 24rpx;
}

.faq-group {
  margin-top: 24rpx;

  &:first-child {
    margin-top: 0;
  }

  .group-title {
    padding: 8rpx 8rpx 16rpx;

    .group-tag {
      font-size: 26rpx;
      color: #ff5e8e;
      font-weight: bold;
    }
  }
}

.faq-item {
  background: #fff;
  border-radius: 20rpx;
  padding: 28rpx 24rpx;
  margin-top: 16rpx;

  &:first-of-type {
    margin-top: 0;
  }

  .faq-question {
    display: flex;
    align-items: center;
    justify-content: space-between;

    .q-text {
      font-size: 30rpx;
      color: #333;
      font-weight: 500;
      flex: 1;
    }

    .q-arrow {
      font-size: 36rpx;
      color: #ccc;
      transition: transform 0.2s;
      margin-left: 16rpx;

      &.expanded {
        transform: rotate(90deg);
      }
    }
  }

  .faq-answer {
    margin-top: 20rpx;
    padding-top: 20rpx;
    border-top: 1rpx solid #f0f0f0;

    text {
      font-size: 26rpx;
      color: #666;
      line-height: 1.7;
    }
  }
}

.empty-state {
  display: flex;
  flex-direction: column;
  align-items: center;
  padding: 160rpx 0;

  .empty-icon {
    font-size: 80rpx;
  }

  .empty-title {
    font-size: 32rpx;
    color: #333;
    font-weight: bold;
    margin-top: 24rpx;
  }

  .empty-desc {
    font-size: 26rpx;
    color: #999;
    margin-top: 12rpx;
  }
}

.loading-tip {
  display: flex;
  justify-content: center;
  padding: 24rpx 0;

  text {
    font-size: 24rpx;
    color: #ccc;
  }
}

.bottom-bar {
  padding: 20rpx 24rpx;
  background: #fff;
  border-top: 1rpx solid #f0f0f0;
  display: flex;
  gap: 20rpx;

  .entry-btn {
    flex: 1;
    background: #fff0f5;
    border: 1rpx solid #ffb3c9;
    border-radius: 44rpx;
    text-align: center;
    padding: 22rpx 0;

    text {
      color: #ff5e8e;
      font-size: 28rpx;
      font-weight: 500;
    }

    &.consult {
      background: #ff5e8e;
      border-color: #ff5e8e;

      text {
        color: #fff;
      }
    }
  }
}
</style>