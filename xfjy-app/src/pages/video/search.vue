<template>
  <view class="search-page">
    <!-- 顶部：返回 + 常驻输入框 + 搜索按钮 -->
    <view class="search-header">
      <a11y-button
        class="icon-btn"
        label="返回"
        @tap="goBack"
      >
        <view
          class="ico i-ph-arrow-left"
          aria-hidden="true"
        />
      </a11y-button>
      <view class="search-box">
        <view
          class="search-box-ico i-ph-magnifying-glass"
          aria-hidden="true"
        />
        <input
          v-model="inputKeyword"
          class="search-input"
          type="text"
          :focus="autoFocus"
          confirm-type="search"
          :maxlength="KEYWORD_MAX"
          placeholder="搜索视频、用户"
          placeholder-class="search-placeholder"
          aria-label="搜索视频、用户"
          @confirm="submitSearch"
        >
        <a11y-button
          v-if="inputKeyword"
          class="clear-btn"
          label="清空输入"
          @tap="clearInput"
        >
          <view
            class="ico-sm i-ph-x-circle"
            aria-hidden="true"
          />
        </a11y-button>
      </view>
      <a11y-button
        class="header-action"
        label="搜索"
        @tap="submitSearch"
      >
        <text class="header-action-text">
          搜索
        </text>
      </a11y-button>
    </view>

    <!-- 结果区：有关键词时展示 tab + 瀑布流 -->
    <view
      v-if="keyword"
      class="result-section"
    >
      <!-- 结果分类：综合 / 视频 / 用户 -->
      <view
        class="result-tabs"
        role="tablist"
        aria-label="搜索结果分类"
      >
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
          <text class="result-tab-text">
            {{ tab.label }}
          </text>
          <view
            v-if="activeTab === tab.key"
            class="tab-line"
            aria-hidden="true"
          />
        </a11y-button>
      </view>

      <!-- 用户 Tab：后端暂无用户搜索接口，明确标注未实现 -->
      <view
        v-if="activeTab === 'user'"
        class="state-wrap"
      >
        <empty-state
          icon="person"
          title="用户搜索本期未实现"
          desc="后端尚未提供按昵称检索用户的接口，这里不展示占位数据"
        />
        <view class="reco">
          <text class="reco-title">
            换个视频关键词试试
          </text>
          <view class="chips">
            <a11y-button
              v-for="word in recoWords"
              :key="word"
              class="chip"
              :label="`搜索 ${word}`"
              @tap="searchWord(word)"
            >
              <text class="chip-text">
                {{ word }}
              </text>
            </a11y-button>
          </view>
        </view>
      </view>

      <view
        v-else
        class="result-body"
      >
        <!-- 综合 Tab：用户维度未实现，用一行说明如实告知 -->
        <view
          v-if="activeTab === 'comprehensive' && !showError"
          class="tab-note"
        >
          <view
            class="tab-note-ico i-ph-info"
            aria-hidden="true"
          />
          <text class="tab-note-text">
            用户搜索本期未实现，当前仅展示视频结果
          </text>
        </view>

        <!-- 请求失败：独立错误态 + 重试 -->
        <view
          v-if="showError"
          class="state-wrap"
        >
          <empty-state
            icon="info"
            title="搜索失败"
            :desc="errorText"
          />
          <a11y-button
            class="retry-btn"
            variant="primary"
            label="重新搜索"
            @tap="retry"
          >
            <text class="retry-text">
              重新搜索
            </text>
          </a11y-button>
        </view>

        <!-- 首屏加载：骨架屏 -->
        <view
          v-else-if="loading && list.length === 0"
          class="waterfall"
        >
          <view
            v-for="n in 4"
            :key="`skeleton-${n}`"
            class="card skeleton"
          >
            <view class="cover-wrap skeleton-cover" />
            <view class="skeleton-line" />
            <view class="skeleton-line short" />
          </view>
        </view>

        <!-- 空结果：说明 + 推荐词 -->
        <view
          v-else-if="!loading && list.length === 0"
          class="state-wrap"
        >
          <empty-state
            icon="search"
            title="没有找到相关内容"
            desc="换个关键词试试，或看看这些热门搜索"
          />
          <view class="reco">
            <view class="chips center">
              <a11y-button
                v-for="word in recoWords"
                :key="word"
                class="chip"
                :label="`搜索 ${word}`"
                @tap="searchWord(word)"
              >
                <text class="chip-text">
                  {{ word }}
                </text>
              </a11y-button>
            </view>
          </view>
        </view>

        <!-- 双列瀑布流 -->
        <view
          v-else
          class="waterfall"
        >
          <a11y-button
            v-for="item in list"
            :key="item.id"
            class="card"
            :label="cardAriaLabel(item)"
            @tap="openVideo(item)"
          >
            <view class="cover-wrap">
              <smart-image
                v-if="item.cover"
                class="cover"
                :src="item.cover"
                :fallback="''"
                mode="aspectFill"
                lazy-load
              />
              <view
                v-else
                class="cover cover-empty"
                aria-hidden="true"
              />
              <view
                class="cover-mask"
                aria-hidden="true"
              />
              <view class="cover-meta">
                <view class="author">
                  <view
                    class="meta-ico i-ph-user"
                    aria-hidden="true"
                  />
                  <text class="author-name">
                    {{ item.authorName || "用户" }}
                  </text>
                </view>
                <view class="like">
                  <view
                    class="meta-ico i-ph-heart"
                    aria-hidden="true"
                  />
                  <text class="like-num">
                    {{ formatCount(item.likeCount) }}
                  </text>
                </view>
              </view>
            </view>
            <view class="card-title">
              <text
                v-for="(seg, idx) in titleSegments(item.title)"
                :key="`${item.id}-seg-${idx}`"
                class="title-seg"
                :class="{ hit: seg.hit }"
              >
                {{ seg.text }}
              </text>
            </view>
          </a11y-button>
        </view>

        <view
          v-if="list.length > 0"
          class="list-foot"
        >
          <text
            v-if="loading"
            class="foot-text"
          >
            加载中…
          </text>
          <text
            v-else-if="hasMore"
            class="foot-text"
          >
            上拉加载更多
          </text>
          <text
            v-else
            class="foot-text"
          >
            没有更多了
          </text>
        </view>
      </view>
    </view>

    <!-- 建议区：无关键词时展示历史 + 热搜 -->
    <view
      v-else
      class="suggest-body"
    >
      <view
        v-if="history.length > 0"
        class="block"
      >
        <view class="block-head">
          <text class="block-title">
            搜索历史
          </text>
          <text class="block-note">
            {{ history.length }}/{{ HISTORY_MAX }}
          </text>
          <a11y-button
            class="block-action"
            label="清空搜索历史"
            @tap="clearHistory"
          >
            <view
              class="ico-sm i-ph-trash"
              aria-hidden="true"
            />
          </a11y-button>
        </view>
        <view class="chips">
          <view
            v-for="word in history"
            :key="word"
            class="chip"
          >
            <a11y-button
              class="chip-main"
              :label="`搜索 ${word}`"
              @tap="searchWord(word)"
            >
              <text class="chip-text">
                {{ word }}
              </text>
            </a11y-button>
            <a11y-button
              class="chip-del"
              :label="`删除历史 ${word}`"
              @tap="removeHistory(word)"
            >
              <view
                class="ico-xs i-ph-x"
                aria-hidden="true"
              />
            </a11y-button>
          </view>
        </view>
      </view>

      <view class="block">
        <view class="block-head">
          <text class="block-title">
            热搜榜
          </text>
          <text
            v-if="hotFromFallback"
            class="block-note"
          >
            离线推荐
          </text>
        </view>
        <a11y-button
          v-for="item in hotWords"
          :key="`${item.rank}-${item.word}`"
          class="hot-row"
          :label="`搜索热搜词 ${item.word}，排名第 ${item.rank}`"
          @tap="searchWord(item.word)"
        >
          <text
            class="hot-rank"
            :class="{ top: item.rank <= 3 }"
          >
            {{ item.rank }}
          </text>
          <text
            class="hot-word"
            :class="{ top: item.rank <= 3 }"
          >
            {{ item.word }}
          </text>
          <view
            v-if="item.rank <= 3"
            class="hot-flag"
            aria-hidden="true"
          >
            <text class="hot-flag-text">
              热
            </text>
          </view>
        </a11y-button>
      </view>
    </view>
  </view>
</template>

<script setup lang="ts">
import { computed, ref, onMounted } from "vue";
import { onLoad, onReachBottom } from "@dcloudio/uni-app";
import A11yButton from "@/components/a11y-button.vue";
import SmartImage from "@/components/smart-image.vue";
import EmptyState from "@/components/empty-state.vue";
import { formatCount } from "@/utils/format";
import { safeGoBack } from "@/utils/nav";
import { fail, info, confirm } from "@/utils/ux";
import {
  getHotSearchWords,
  searchVideos,
  type VideoSearchItem,
  type HotSearchWord,
} from "@/api/video-search";
import { useSystemStore } from "@/store/system";

const systemStore = useSystemStore();

const KEYWORD_MAX = 30;
const PAGE_SIZE = 10;
const HISTORY_KEY = "xfjy_video_search_history_v1";
const HISTORY_MAX = 10;

const FALLBACK_WORDS = [
  "交友",
  "同城",
  "唱歌",
  "舞蹈",
  "旅行",
  "美食",
  "宠物",
  "健身",
  "日常",
  "治愈",
];

const TABS = [
  { key: "comprehensive", label: "综合" },
  { key: "video", label: "视频" },
  { key: "user", label: "用户" },
] as const;

type TabKey = (typeof TABS)[number]["key"];

const inputKeyword = ref("");
const keyword = ref("");
const autoFocus = ref(true);
const activeTab = ref<TabKey>("comprehensive");
const list = ref<VideoSearchItem[]>([]);
const page = ref(0);
const pages = ref(0);
const hasMore = ref(false);
const loading = ref(false);
const errorText = ref("");
const history = ref<string[]>([]);
const hotWords = ref<HotSearchWord[]>([]);
const hotFromFallback = ref(false);
const recoWords = ref<string[]>(FALLBACK_WORDS.slice(0, 6));

const showError = computed(() => errorText.value !== "" && list.value.length === 0);

onLoad((options) => {
  const raw = options?.keyword ? decodeURIComponent(String(options.keyword)) : "";
  const word = raw.trim();
  if (word) {
    inputKeyword.value = word;
    keyword.value = word;
    load(1, false);
  }
});

onMounted(() => {
  history.value = readHistory();
  loadHotWords();
});

onReachBottom(() => {
  if (keyword.value && hasMore.value && !loading.value && !showError.value) {
    load(page.value + 1, true);
  }
});

function readHistory(): string[] {
  try {
    const raw = uni.getStorageSync(HISTORY_KEY);
    if (Array.isArray(raw)) {
      return raw.filter((item) => typeof item === "string" && item.trim()).slice(0, HISTORY_MAX);
    }
    return [];
  } catch {
    return [];
  }
}

function writeHistory(words: string[]): void {
  history.value = words;
  try {
    uni.setStorageSync(HISTORY_KEY, words);
  } catch {
    // 本地存储写满/被禁用时只影响历史展示
  }
}

async function loadHotWords(): Promise<void> {
  try {
    const list = await getHotSearchWords();
    if (Array.isArray(list) && list.length > 0) {
      hotWords.value = list;
      hotFromFallback.value = false;
      recoWords.value = list.slice(0, 6).map((item) => item.word);
      return;
    }
    throw new Error("empty hot words");
  } catch {
    hotWords.value = FALLBACK_WORDS.map((word, index) => ({
      rank: index + 1,
      word,
      heat: null,
    }));
    hotFromFallback.value = true;
  }
}

async function load(targetPage: number, append: boolean): Promise<void> {
  if (loading.value) return;
  loading.value = true;
  if (!append) errorText.value = "";

  try {
    const res = await searchVideos({
      keyword: keyword.value,
      page: targetPage,
      pageSize: PAGE_SIZE,
    });
    const items = Array.isArray(res.items) ? res.items : [];
    list.value = append ? list.value.concat(items) : items;
    page.value = targetPage;
    pages.value = res.pages || 0;
    hasMore.value = items.length > 0 && page.value < pages.value;
  } catch {
    if (append || list.value.length > 0) {
      fail("加载失败，请稍后重试");
    } else {
      errorText.value = "网络或服务暂时不可用，请稍后重试";
    }
  } finally {
    loading.value = false;
  }
}

function submitSearch(): void {
  const word = (inputKeyword.value || "").trim().replace(/\s+/g, " ");
  if (!word) {
    info("请输入搜索内容");
    return;
  }
  // 写入历史
  const next = [word, ...history.value.filter((item) => item !== word)].slice(0, HISTORY_MAX);
  writeHistory(next);

  if (word === keyword.value) {
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

function clearInput(): void {
  inputKeyword.value = "";
  keyword.value = "";
  list.value = [];
  errorText.value = "";
  autoFocus.value = true;
}

function removeHistory(word: string): void {
  writeHistory(history.value.filter((item) => item !== word));
}

async function clearHistory(): Promise<void> {
  const ok = await confirm("确定清空全部搜索历史吗？");
  if (ok) writeHistory([]);
}

function retry(): void {
  load(1, false);
}

function cardAriaLabel(item: VideoSearchItem): string {
  const title = item.title || "未命名视频";
  const author = item.authorName || "未知作者";
  return `播放视频：${title}，作者：${author}`;
}

function openVideo(item: VideoSearchItem): void {
  systemStore.setPendingVideoDeepLink(item.id, item);
  uni.switchTab({ url: "/pages/video/index" });
}

function goBack(): void {
  safeGoBack("/pages/video/index");
}

interface TitleSegment {
  text: string;
  hit: boolean;
}

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
.search-page {
  min-height: 100vh;
  background-color: var(--bg-page);
  padding-bottom: env(safe-area-inset-bottom);
}

.search-header {
  position: sticky;
  top: 0;
  z-index: 10;
  display: flex;
  align-items: center;
  gap: var(--space-sm);
  height: var(--category-bar-h);
  padding: env(safe-area-inset-top) var(--space-md) 0;
  background-color: var(--bg-card);
  border-bottom: 1px solid var(--border-color);
  box-sizing: content-box;
}

.icon-btn {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 88rpx;
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
    width: 88rpx;

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

/* ===== 建议区（无关键词） ===== */
.suggest-body {
  padding: var(--space-md);
}

.block {
  margin-bottom: var(--space-lg);
}

.block-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: var(--space-sm);
}

.block-title {
  font-size: var(--fs-md);
  font-weight: 600;
  color: var(--text-primary);
}

.block-note {
  font-size: var(--fs-xs);
  color: var(--text-hint);
}

.block-action {
  display: flex;
  align-items: center;
  justify-content: center;
  min-width: 88rpx;
  min-height: 88rpx;

  .ico-sm {
    width: 32rpx;
    height: 32rpx;
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
}

.chip {
  display: flex;
  align-items: center;
  gap: var(--space-xs);
  max-width: 100%;
  min-width: 0;

  .chip-main {
    display: flex;
    align-items: center;
    min-width: 0;
    min-height: 88rpx;
    padding: 0 var(--space-sm) 0 var(--space-md);
    border-radius: var(--radius-pill);
    background-color: var(--bg-card);
  }

  .chip-text {
    max-width: 240rpx;
    overflow: hidden;
    white-space: nowrap;
    text-overflow: ellipsis;
    font-size: var(--fs-sm);
    color: var(--text-secondary);
  }

  .chip-del {
    display: flex;
    align-items: center;
    justify-content: center;
    min-width: 88rpx;
    min-height: 88rpx;
    flex-shrink: 0;

    .ico-xs {
      width: 22rpx;
      height: 22rpx;
      color: var(--text-hint-weak);
    }
  }
}

.hot-row {
  display: flex;
  align-items: center;
  gap: var(--space-sm);
  min-height: 88rpx;
  border-bottom: 1px solid var(--border-color);

  &:last-child {
    border-bottom: none;
  }

  .hot-rank {
    width: 44rpx;
    flex-shrink: 0;
    text-align: center;
    font-size: var(--fs-md);
    font-weight: 600;
    color: var(--text-hint);

    &.top {
      color: var(--color-primary);
    }
  }

  .hot-word {
    flex: 1;
    min-width: 0;
    overflow: hidden;
    white-space: nowrap;
    text-overflow: ellipsis;
    font-size: var(--fs-md);
    color: var(--text-primary);

    &.top {
      font-weight: 600;
    }
  }

  .hot-flag {
    display: flex;
    align-items: center;
    justify-content: center;
    width: 36rpx;
    height: 36rpx;
    border-radius: var(--radius-sm);
    background-color: var(--bg-icon-soft);

    .hot-flag-text {
      font-size: var(--fs-xs);
      color: var(--color-primary-text);
    }
  }
}

/* ===== 结果区（有关键词） ===== */
.result-section {
  /* 结果区容器，无额外样式，仅作为 v-if 包裹层 */
}

.result-tabs {
  position: sticky;
  top: calc(var(--category-bar-h) + env(safe-area-inset-top));
  z-index: 9;
  display: flex;
  align-items: center;
  gap: var(--space-lg);
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
  display: flex;
  flex-direction: column;
  align-items: stretch;
  justify-content: flex-start;
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
  align-self: center;
  margin: 0 auto;
  width: 240rpx;

  .retry-text {
    font-size: var(--fs-sm);
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

.chips .chip {
  display: flex;
  align-items: center;
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
</style>
