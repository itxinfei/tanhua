<template>
  <view class="kefu-page">
    <!-- FAQ 搜索框 -->
    <view class="search-bar">
      <view class="search-input-wrap">
        <uni-icons
          class="search-icon"
          type="search"
          size="18"
          color="var(--text-hint)"
        />
        <input
          v-model="keyword"
          class="search-input"
          placeholder="搜索常见问题关键词"
          aria-label="搜索常见问题关键词"
          :maxlength="30"
          confirm-type="search"
        >
        <a11y-button
          v-if="keyword"
          class="search-clear"
          label="清空搜索关键词"
          @tap="keyword = ''"
        >
          <uni-icons
            type="close"
            size="18"
            color="var(--text-hint)"
          />
        </a11y-button>
      </view>
    </view>

    <scroll-view
      class="faq-scroll"
      scroll-y
    >
      <!-- 入口操作区：原固定底栏改为页面内容，避免与全局 tabbar 堆叠 -->
      <view class="entry-bar">
        <a11y-button
          class="entry-btn consult"
          label="在线咨询"
          @tap="goChat"
        >
          <text>
            <uni-icons
              type="headphones"
              size="14"
              color="var(--text-inverse)"
            /> 在线咨询
          </text>
        </a11y-button>
        <a11y-button
          class="entry-btn"
          label="意见反馈"
          @tap="goFeedback"
        >
          <text>意见反馈</text>
        </a11y-button>
      </view>

      <!-- 加载失败：与「真的暂无 FAQ / 搜索无结果」严格区分，给明确原因 + 可重试入口。
           失败时 faqList 未被赋值（仍为空），分组列表自然不渲染，错误态优先于本地搜索结果 -->
      <empty-state
        v-if="groupedList.length === 0 && !loading && faqLoadFailed"
        role="alert"
        icon="info"
        title="暂时没能获取常见问题"
        desc="服务可能开小差了，检查网络后重试"
      >
        <a11y-button
          class="faq-retry"
          label="重新加载常见问题"
          @tap="loadFaqList"
        >
          重新加载
        </a11y-button>
      </empty-state>

      <!-- 正常空态：首屏无数据或关键词搜索无结果（两种文案分支保持原样，禁止改动） -->
      <empty-state
        v-else-if="groupedList.length === 0 && !loading"
        icon="chatbubble"
        :title="keyword ? '未找到相关问题' : '暂无常见问题'"
        :desc="keyword ? '换个关键词，或直接咨询在线客服' : '可直接提交意见反馈联系我们'"
      />

      <view
        v-for="group in groupedList"
        :key="group.category"
        class="faq-group"
      >
        <view class="group-title">
          <text class="group-tag">
            {{ group.category }}
          </text>
        </view>
        <a11y-button
          v-for="item in group.items"
          :key="item.id"
          class="faq-item shadow-card"
          :label="`${item.question}，${expandedId === item.id ? '已展开答案，点击收起' : '点击展开答案'}`"
          @tap="toggleItem(item.id)"
        >
          <view class="faq-question">
            <text class="q-text">
              {{ item.question }}
            </text>
            <text
              class="q-arrow"
              :class="{ expanded: expandedId === item.id }"
              aria-hidden="true"
            >
              ›
            </text>
          </view>
          <view
            v-if="expandedId === item.id"
            class="faq-answer"
          >
            <text>{{ item.answer }}</text>
          </view>
        </a11y-button>
      </view>

      <view
        v-if="loading"
        class="loading-tip"
      >
        <text>加载中…</text>
      </view>
    </scroll-view>
  </view>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from "vue";
import EmptyState from "@/components/empty-state.vue";
import A11yButton from "@/components/a11y-button.vue";
import { getFaqListApi, type FaqItem } from "@/api/kefu";

const faqList = ref<FaqItem[]>([]);
const loading = ref(false);
/** FAQ 加载失败标记：与「真的暂无数据」区分，失败时展示错误空态 + 重试（S3 失败态/空态分离） */
const faqLoadFailed = ref(false);
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
  // 每次加载先清失败标记：成功走正常列表/空态，失败再置回 true
  faqLoadFailed.value = false;
  try {
    faqList.value = await getFaqListApi();
  } catch {
    // catch 不空吞：错误 toast 由 request 封装统一处理，这里置失败标记驱动错误空态 + 重试按钮
    faqLoadFailed.value = true;
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
  /* 高度必须同时减去导航栏（--window-top）与全局 tabbar（--tab-bar-height）：
     二级页 uni-page-body 从导航栏下方开始，若只减 tabbar 会导致底部被遮挡 44px */
  height: calc(100vh - var(--window-top, 0px) - var(--tab-bar-height, 0px));
  background: var(--bg-sub);
  display: flex;
  flex-direction: column;
}

.search-bar {
  padding: 16rpx 24rpx 4rpx;
  background: var(--bg-sub);

  .search-input-wrap {
    display: flex;
    align-items: center;
    background: var(--bg-card);
    border-radius: var(--radius-pill);
    padding: 0 24rpx;
    height: 76rpx;
    gap: 12rpx;
    box-shadow: 0 2rpx 12rpx rgba(0, 0, 0, 0.04);

    .search-icon {
      font-size: 28rpx;
      flex-shrink: 0;
    }

    .search-input {
      flex: 1;
      font-size: 26rpx;
      color: var(--text-primary);
    }

    /* 清除按钮：热区 ≥44px（88rpx），图标色走 --text-hint 适配暗色 */
    .search-clear {
      font-size: 28rpx;
      color: var(--text-hint);
      padding: 8rpx 12rpx;
      min-height: 88rpx;
      display: flex;
      align-items: center;
      flex-shrink: 0;
    }
  }
}

/* scroll-view 自带滚动：不再叠加 overflow，避免覆盖 uni-app 运行时设置的
   overflow-y:auto（参考 chat/chat.vue 的 .msg-list） */
.faq-scroll {
  flex: 1 1 auto;
  /* min-height:0 必须：纵向 flex 子项默认 min-height:auto，内容超高会撑破容器，
     导致 scroll-view 不滚动（页面整体被撑高、底部入口被推出视口） */
  min-height: 0;
  /* min-width:0 防止 flex 子项被内部宽条目撑破，保证右侧 24rpx 留白不溢出视口 */
  min-width: 0;
  padding: 8rpx 24rpx 24rpx;
}

.faq-group {
  background: var(--bg-card);
  border-radius: var(--radius-lg);
  margin-top: 24rpx;
  overflow: hidden;
  box-shadow: 0 2rpx 12rpx rgba(0, 0, 0, 0.04);

  &:first-child {
    margin-top: 0;
  }

  .group-title {
    padding: 24rpx 32rpx 8rpx;

    .group-tag {
      font-size: 26rpx;
      color: var(--color-primary);
      font-weight: bold;
    }
  }
}

.faq-item {
  /* border-box + 宽度 100%：padding 计入宽度，避免条目撑破滚动容器导致右侧留白丢失 */
  box-sizing: border-box;
  width: 100%;
  padding: 28rpx 32rpx;
  border-top: 1rpx solid var(--border-faint);

  &:first-of-type {
    border-top: none;
  }

  .faq-question {
    display: flex;
    align-items: center;
    justify-content: space-between;

    .q-text {
      font-size: 30rpx;
      color: var(--text-primary);
      font-weight: 500;
      flex: 1;
    }

    .q-arrow {
      font-size: 36rpx;
      color: var(--text-hint);
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
    border-top: 1rpx solid var(--border-light);

    text {
      font-size: 26rpx;
      color: var(--text-secondary);
      line-height: 1.7;
    }
  }
}

.loading-tip {
  display: flex;
  justify-content: center;
  padding: 24rpx 0;

  text {
    font-size: 24rpx;
    color: var(--text-hint);
  }
}

/* 失败空态内的重试按钮：default 变体零样式，这里补品牌色，颜色全走 CSS 变量适配暗色 */
.faq-retry {
  margin-top: 32rpx;
  padding: 16rpx 56rpx;
  border-radius: var(--radius-pill);
  font-size: 28rpx;
  font-weight: 600;
  color: var(--text-inverse);
  background: var(--color-primary);
}

/* 入口操作区：页面内容的一部分（非固定底栏），与全局 tabbar 不冲突 */
.entry-bar {
  display: flex;
  gap: 20rpx;
  padding: 16rpx 0 24rpx;

  .entry-btn {
    flex: 1;
    background: var(--bg-icon-soft);
    border: 1rpx solid var(--color-primary-mist);
    border-radius: var(--radius-pill);
    text-align: center;
    padding: 22rpx 0;

    text {
      color: var(--color-primary);
      font-size: 28rpx;
      font-weight: 500;
    }

    &.consult {
      background: var(--color-primary);
      border-color: var(--color-primary);

      text {
        color: var(--text-inverse);
      }
    }
  }
}
</style>
