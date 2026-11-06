<template>
  <view class="result-page">
    <!-- 顶部：返回 + 可编辑输入框 + 搜索 -->
    <view class="result-header">
      <a11y-button class="icon-btn" label="返回" @tap="goBack">
        <view class="ico i-ph-arrow-left" aria-hidden="true" />
      </a11y-button>
      <view class="search-box">
        <view class="search-box-ico i-ph-magnifying-glass" aria-hidden="true" />
        <input
          v-model="inputKeyword"
          class="search-input"
          type="text"
          confirm-type="search"
          :maxlength="KEYWORD_MAX"
          placeholder="搜索视频、用户"
          placeholder-class="search-placeholder"
          @confirm="submitSearch"
        />
        <a11y-button v-if="inputKeyword" class="clear-btn" label="清空输入" @tap="inputKeyword = ''">
          <view class="ico-sm i-ph-x-circle" aria-hidden="true" />
        </a11y-button>
      </view>
      <a11y-button class="header-action" label="搜索" @tap="submitSearch">
        <text class="header-action-text">搜索</text>
      </a11y-button>
    </view>

    <!-- 结果分类：综合 / 视频 / 用户（a11y-button 统一交互壳，键盘可达） -->
    <view class="result-tabs" role="tablist" aria-label="搜索结果分类">
      <a11y-button
        v-for="tab in TABS"
        :key="tab.key"
        class="result-tab"
        :class="{ active: activeTab === tab.key }"
        role="tab"
        :checked="activeTab === tab.key"
        :label="tab.label"
        @tap="activeTab = tab.key"
      >
        <text class="result-tab-text">{{ tab.label }}</text>
        <view v-if="activeTab === tab.key" class="tab-line" aria-hidden="true" />
      </a11y-button>
    </view>

    <!-- 用户 Tab：后端暂无用户搜索接口，明确标注未实现，不给假数据 -->
    <view v-if="activeTab === 'user'" class="state-wrap">
      <empty-state
        icon="person"
        title="用户搜索本期未实现"
        desc="后端尚未提供按昵称检索用户的接口，这里不展示占位数据"
      />
      <view class="reco">
        <text class="reco-title">换个视频关键词试试</text>
        <view class="chips">
          <a11y-button
            v-for="word in recoWords"
            :key="word"
            class="chip"
            :label="`搜索 ${word}`"
            @tap="searchWord(word)"
          >
            <text class="chip-text">{{ word }}</text>
          </a11y-button>
        </view>
      </view>
    </view>

    <view v-else class="result-body">
      <!-- 综合 Tab：用户维度未实现，用一行说明如实告知 -->
      <view v-if="activeTab === 'comprehensive' && !showError" class="tab-note">
        <view class="tab-note-ico i-ph-info" aria-hidden="true" />
        <text class="tab-note-text">用户搜索本期未实现，当前仅展示视频结果</text>
      </view>

      <!-- 请求失败：独立错误态 + 重试，不伪装成空态 -->
      <view v-if="showError" class="state-wrap">
        <empty-state icon="info" title="搜索失败" :desc="errorText" />
        <a11y-button class="retry-btn" label="重新搜索" @tap="retry">
          <text class="retry-text">重新搜索</text>
        </a11y-button>
      </view>

      <!-- 首屏加载：骨架屏，避免整屏闪白 -->
      <view v-else-if="loading && list.length === 0" class="waterfall">
        <view v-for="n in 4" :key="`skeleton-${n}`" class="card skeleton">
          <view class="cover-wrap skeleton-cover" />
          <view class="skeleton-line" />
          <view class="skeleton-line short" />
        </view>
      </view>

      <!-- 空结果：说明 + 推荐词，不留空白页 -->
      <view v-else-if="!loading && list.length === 0" class="state-wrap">
        <empty-state icon="search" title="没有找到相关内容" desc="换个关键词试试，或看看这些热门搜索" />
        <view class="reco">
          <view class="chips center">
            <a11y-button
              v-for="word in recoWords"
              :key="word"
              class="chip"
              :label="`搜索 ${word}`"
              @tap="searchWord(word)"
            >
              <text class="chip-text">{{ word }}</text>
            </a11y-button>
          </view>
        </view>
      </view>

      <!-- 双列瀑布流 -->
      <view v-else class="waterfall">
        <view v-for="item in list" :key="item.id" class="card">
          <view class="cover-wrap">
            <image v-if="item.cover" class="cover" :src="item.cover" mode="aspectFill" lazy-load />
            <view v-else class="cover cover-empty" aria-hidden="true" />
            <view class="cover-mask" aria-hidden="true" />
            <view class="cover-meta">
              <view class="author">
                <view class="meta-ico i-ph-user" aria-hidden="true" />
                <text class="author-name">{{ item.authorName || "用户" }}</text>
              </view>
              <view class="like">
                <view class="meta-ico i-ph-heart" aria-hidden="true" />
                <text class="like-num">{{ formatCount(item.likeCount) }}</text>
              </view>
            </view>
          </view>
          <view class="card-title">
            <text
              v-for="(seg, idx) in titleSegments(item.title)"
              :key="`${item.id}-seg-${idx}`"
              class="title-seg"
              :class="{ hit: seg.hit }"
            >{{ seg.text }}</text>
          </view>
        </view>
      </view>

      <view v-if="list.length > 0" class="list-foot">
        <text v-if="loading" class="foot-text">加载中…</text>
        <text v-else-if="hasMore" class="foot-text">上拉加载更多</text>
        <text v-else class="foot-text">没有更多了</text>
      </view>
    </view>
  </view>
</template>

<script setup lang="ts">
import { computed, ref } from "vue";
import { onLoad, onPullDownRefresh, onReachBottom } from "@dcloudio/uni-app";
import A11yButton from "@/components/a11y-button.vue";
import EmptyState from "@/components/empty-state.vue";
import { formatCount } from "@/utils/format";
import {
  getHotSearchWords,
  searchVideos,
  type VideoSearchItem,
  type HotSearchWord,
} from "@/api/video-search";

/** 与后端 VideoSearchService.KEYWORD_MAX 一致 */
const KEYWORD_MAX = 30;
/** 每页条数，与后端 PAGE_SIZE_MAX=30 之内 */
const PAGE_SIZE = 10;

const TABS = [
  { key: "comprehensive", label: "综合" },
  { key: "video", label: "视频" },
  { key: "user", label: "用户" },
] as const;

type TabKey = (typeof TABS)[number]["key"];

/** 后端热搜不可用时的兜底推荐词（与后端 FALLBACK_HOT_WORDS 同源） */
const FALLBACK_WORDS = ["交友", "同城", "唱歌", "舞蹈", "旅行", "美食", "宠物", "健身"];

const keyword = ref("");
const inputKeyword = ref("");
const activeTab = ref<TabKey>("comprehensive");
const list = ref<VideoSearchItem[]>([]);
const page = ref(0);
const pages = ref(0);
const hasMore = ref(false);
const loading = ref(false);
const errorText = ref("");
const recoWords = ref<string[]>(FALLBACK_WORDS.slice(0, 6));

const showError = computed(() => errorText.value !== "" && list.value.length === 0);

onLoad((options) => {
  const raw = options?.keyword ? decodeURIComponent(String(options.keyword)) : "";
  const word = raw.trim();
  inputKeyword.value = word;
  keyword.value = word;
  if (!word) {
    uni.navigateBack();
    return;
  }
  loadRecoWords();
  load(1, false);
});

onPullDownRefresh(() => {
  if (!keyword.value) {
    uni.stopPullDownRefresh();
    return;
  }
  load(1, false).finally(() => uni.stopPullDownRefresh());
});

onReachBottom(() => {
  if (hasMore.value && !loading.value && !showError.value) {
    load(page.value + 1, true);
  }
});

async function load(targetPage: number, append: boolean): Promise<void> {
  if (loading.value) return;
  loading.value = true;
  // 首屏重试时清掉旧错误，避免错误态与加载态互相遮挡
  if (!append) errorText.value = "";

  try {
    const res = await searchVideos({
      keyword: keyword.value,
      page: targetPage,
      pageSize: PAGE_SIZE,
    });
    const items = Array.isArray(res.items) ? res.items : [];
    // 加载更多期间保留上一页内容，只有拿到新数据才替换
    list.value = append ? list.value.concat(items) : items;
    page.value = targetPage;
    pages.value = res.pages || 0;
    hasMore.value = items.length > 0 && page.value < pages.value;
  } catch (e) {
    if (append || list.value.length > 0) {
      // 加载更多失败：保留已有内容，只提示
      uni.showToast({ title: "加载失败，请稍后重试", icon: "none" });
    } else {
      errorText.value = "网络或服务暂时不可用，请稍后重试";
    }
  } finally {
    loading.value = false;
  }
}

function retry(): void {
  load(1, false);
}

function submitSearch(): void {
  const word = (inputKeyword.value || "").trim().replace(/\s+/g, " ");
  if (!word) {
    uni.showToast({ title: "请输入搜索内容", icon: "none" });
    return;
  }
  if (word === keyword.value) {
    // 同词重复提交：回到顶部并重新拉第一页
    uni.pageScrollTo({ scrollTop: 0, duration: 0 });
    if (!loading.value) load(1, false);
    return;
  }
  keyword.value = word;
  inputKeyword.value = word;
  list.value = [];
  page.value = 0;
  pages.value = 0;
  hasMore.value = false;
  errorText.value = "";
  activeTab.value = "comprehensive";
  uni.pageScrollTo({ scrollTop: 0, duration: 0 });
  load(1, false);
}

function searchWord(word: string): void {
  inputKeyword.value = word;
  submitSearch();
}

async function loadRecoWords(): Promise<void> {
  try {
    const hot: HotSearchWord[] = await getHotSearchWords();
    if (Array.isArray(hot) && hot.length > 0) {
      recoWords.value = hot.slice(0, 6).map((item) => item.word);
    }
  } catch (e) {
    recoWords.value = FALLBACK_WORDS.slice(0, 6);
  }
}

function goBack(): void {
  uni.navigateBack();
}

interface TitleSegment {
  text: string;
  hit: boolean;
}

/** 把标题按关键词切成高亮/普通片段（大小写不敏感，精确按原始文本切片保留大小写） */
function titleSegments(title: string): TitleSegment[] {
  const text = title || "";
  const kw = keyword.value.trim();
  if (!kw) return [{ text, hit: false }];

  const lowerText = text.toLowerCase();
  const lowerKw = kw.toLowerCase();
  const segments: TitleSegment[] = [];
  let cursor = 0;
  let index = lowerText.indexOf(lowerKw, cursor);

  while (index >= 0) {
    if (index > cursor) {
      segments.push({ text: text.slice(cursor, index), hit: false });
    }
    segments.push({ text: text.slice(index, index + kw.length), hit: true });
    cursor = index + kw.length;
    index = lowerText.indexOf(lowerKw, cursor);
  }
  if (cursor < text.length) {
    segments.push({ text: text.slice(cursor), hit: false });
  }
  return segments.length > 0 ? segments : [{ text, hit: false }];
}
</script>

<style lang="scss" scoped>
.result-page {
  min-height: 100vh;
  background-color: var(--bg-page);
  padding-bottom: env(safe-area-inset-bottom);
}

.result-header {
  position: sticky;
  top: 0;
  z-index: 10;
  display: flex;
  align-items: center;
  gap: var(--space-sm);
  height: var(--category-bar-h);
  padding: env(safe-area-inset-top) var(--space-md) 0;
  background-color: var(--bg-card);
  box-sizing: content-box;
}

.icon-btn {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 56rpx;
  height: 56rpx;
  flex-shrink: 0;

  .ico {
    width: 44rpx;
    height: 44rpx;
    color: var(--text-primary);
  }
}

.search-box {
  display: flex;
  align-items: center;
  gap: var(--space-xs);
  flex: 1;
  min-width: 0;
  height: 64rpx;
  padding: 0 var(--space-sm);
  border-radius: var(--radius-pill);
  background-color: var(--bg-sub);

  .search-box-ico {
    width: 32rpx;
    height: 32rpx;
    color: var(--text-hint);
    flex-shrink: 0;
  }

  .search-input {
    flex: 1;
    min-width: 0;
    height: 64rpx;
    font-size: var(--fs-md);
    color: var(--text-primary);
  }

  .clear-btn {
    display: flex;
    align-items: center;
    justify-content: center;
    flex-shrink: 0;

    .ico-sm {
      width: 32rpx;
      height: 32rpx;
      color: var(--text-hint);
    }
  }
}

.search-placeholder {
  color: var(--text-hint);
  font-size: var(--fs-sm);
}

/* 搜索按钮：改用 a11y-button 承载，热区 ≥88rpx（原 <text> 仅 29.7×21.6px，用户需精准点中小字） */
.header-action {
  flex-shrink: 0;
  min-height: 88rpx;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 0 20rpx;
}

.header-action-text {
  font-size: var(--fs-sm);
  color: var(--color-primary-text);
  font-weight: 600;
}

.result-tabs {
  position: sticky;
  top: calc(var(--category-bar-h) + env(safe-area-inset-top));
  z-index: 9;
  display: flex;
  align-items: center;
  gap: var(--space-lg);
  /* 条高统一走全局 token（§7.1），与视频频道条/分类条对齐 */
  height: var(--category-bar-h);
  padding: 0 var(--space-md);
  background-color: var(--bg-card);
  border-bottom: 1px solid var(--border-color);
}

.result-tab {
  position: relative;
  display: flex;
  align-items: center;
  height: 100%;

  .result-tab-text {
    font-size: var(--fs-md);
    color: var(--text-secondary);
  }

  &.active .result-tab-text {
    color: var(--text-primary);
    font-weight: 600;
  }

  .tab-line {
    position: absolute;
    left: 50%;
    bottom: 0;
    transform: translateX(-50%);
    /* 选中指示统一规格（§7.1）：宽 48rpx、高 8rpx、药丸、贴 bottom；
       浅底页面用主色纯色，不用 gradient-brand（那是视频深色画面频道条的例外） */
    width: 48rpx;
    height: 8rpx;
    border-radius: var(--radius-pill);
    background: var(--color-primary);
  }
}

.result-body {
  padding: var(--space-md);
}

.tab-note {
  display: flex;
  align-items: center;
  gap: var(--space-xs);
  margin-bottom: var(--space-sm);
  padding: var(--space-sm) var(--space-md);
  border-radius: var(--radius-md);
  background-color: var(--bg-card);

  .tab-note-ico {
    width: 28rpx;
    height: 28rpx;
    flex-shrink: 0;
    color: var(--text-hint);
  }

  .tab-note-text {
    font-size: var(--fs-xs);
    color: var(--text-hint);
  }
}

.waterfall {
  display: flex;
  flex-wrap: wrap;
  justify-content: space-between;
}

.card {
  width: calc((100% - var(--space-sm)) / 2);
  margin-bottom: var(--space-sm);
  border-radius: var(--radius-md);
  background-color: var(--bg-card);
  overflow: hidden;
}

.cover-wrap {
  position: relative;
  width: 100%;
  padding-top: 133.33%;
  background-color: var(--bg-sub);
  overflow: hidden;

  .cover {
    position: absolute;
    top: 0;
    left: 0;
    width: 100%;
    height: 100%;
  }

  .cover-empty {
    background-color: var(--bg-sub);
  }

  .cover-mask {
    position: absolute;
    left: 0;
    right: 0;
    bottom: 0;
    height: 40%;
    background: var(--bg-deep);
    opacity: 0.55;
  }

  .cover-meta {
    position: absolute;
    left: var(--space-xs);
    right: var(--space-xs);
    bottom: var(--space-xs);
    display: flex;
    align-items: center;
    justify-content: space-between;
    gap: var(--space-xs);

    .author,
    .like {
      display: flex;
      align-items: center;
      gap: 4rpx;
      min-width: 0;
    }

    .meta-ico {
      width: 24rpx;
      height: 24rpx;
      flex-shrink: 0;
      color: var(--text-inverse);
    }

    .author-name {
      max-width: 160rpx;
      overflow: hidden;
      white-space: nowrap;
      text-overflow: ellipsis;
      font-size: var(--fs-xs);
      color: var(--text-inverse);
    }

    .like-num {
      font-size: var(--fs-xs);
      color: var(--text-inverse);
    }
  }
}

.card-title {
  display: -webkit-box;
  -webkit-box-orient: vertical;
  -webkit-line-clamp: 2;
  overflow: hidden;
  padding: var(--space-xs) var(--space-sm);
  min-height: 76rpx;

  .title-seg {
    font-size: var(--fs-sm);
    line-height: 1.5;
    color: var(--text-primary);
    word-break: break-all;

    &.hit {
      color: var(--color-primary);
      font-weight: 600;
    }
  }
}

.skeleton-cover {
  background-color: var(--bg-sub);
}

.skeleton-line {
  height: 20rpx;
  margin: var(--space-xs) var(--space-sm) 0;
  border-radius: var(--radius-sm);
  background-color: var(--bg-sub);

  &.short {
    width: 50%;
    margin-bottom: var(--space-sm);
  }
}

.list-foot {
  display: flex;
  align-items: center;
  justify-content: center;
  padding: var(--space-md) 0 var(--space-lg);

  .foot-text {
    font-size: var(--fs-xs);
    color: var(--text-hint);
  }
}

.state-wrap {
  padding: var(--space-md) 0;
}

.retry-btn {
  display: flex;
  align-items: center;
  justify-content: center;
  align-self: center;
  margin: 0 auto;
  width: 240rpx;
  height: 72rpx;
  border-radius: var(--radius-pill);
  background: var(--gradient-brand);

  .retry-text {
    font-size: var(--fs-sm);
    color: var(--text-inverse);
  }
}

.reco {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: var(--space-sm);
  margin-top: var(--space-md);

  .reco-title {
    font-size: var(--fs-xs);
    color: var(--text-hint);
  }
}

.chips {
  display: flex;
  flex-wrap: wrap;
  gap: var(--space-sm);

  &.center {
    justify-content: center;
  }

  .chip {
    display: flex;
    align-items: center;
    /* 热区 88rpx：原 56rpx 低于 44px 基线 */
    min-height: 88rpx;
    padding: 0 var(--space-md);
    box-sizing: border-box;
    border-radius: var(--radius-pill);
    background-color: var(--bg-card);

    .chip-text {
      font-size: var(--fs-sm);
      color: var(--text-secondary);
    }
  }
}
</style>
