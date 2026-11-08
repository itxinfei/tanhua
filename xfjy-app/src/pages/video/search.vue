<template>
  <view class="search-page">
    <!-- 顶部：返回 + 输入框 + 取消（对齐抖音搜索页的操作顺序） -->
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
          v-model="keyword"
          class="search-input"
          type="text"
          :focus="autoFocus"
          confirm-type="search"
          :maxlength="KEYWORD_MAX"
          placeholder="搜索视频、用户"
          placeholder-class="search-placeholder"
          aria-label="搜索视频、用户"
          @confirm="submit(keyword)"
        >
        <a11y-button
          v-if="keyword"
          class="clear-btn"
          label="清空输入"
          @tap="keyword = ''"
        >
          <view
            class="ico-sm i-ph-x-circle"
            aria-hidden="true"
          />
        </a11y-button>
      </view>
      <a11y-button
        class="header-action"
        label="取消搜索"
        @tap="goBack"
      >
        <text>取消</text>
      </a11y-button>
    </view>

    <view class="suggest-body">
      <!-- 搜索历史：本地存储，单条删除 + 全部清空 -->
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
          <!-- chip 主体与删除按钮拆成兄弟节点：嵌套按钮在 HTML 中非法，
             且外层 view 直接 @tap 无键盘/读屏入口（§7.1） -->
          <view
            v-for="word in history"
            :key="word"
            class="chip"
          >
            <a11y-button
              class="chip-main"
              :label="`搜索 ${word}`"
              @tap="submit(word)"
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

      <!-- 热搜榜：后端带名次，前三名强调 -->
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
          @tap="submit(item.word)"
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
import { ref, onMounted } from "vue";
import A11yButton from "@/components/a11y-button.vue";
import { confirm } from "@/utils/ux";
import { safeGoBack } from "@/utils/nav";
import { getHotSearchWords, type HotSearchWord } from "@/api/video-search";

/** 与后端 VideoSearchService.KEYWORD_MAX 一致 */
const KEYWORD_MAX = 30;
/** 搜索历史本地存储 key（与旧版本不兼容时直接换代） */
const HISTORY_KEY = "xfjy_video_search_history_v1";
const HISTORY_MAX = 10;

/** 后端热搜接口不可用时的兜底词（与后端 FALLBACK_HOT_WORDS 保持同一份） */
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

const keyword = ref("");
const autoFocus = ref(true);
const history = ref<string[]>([]);
const hotWords = ref<HotSearchWord[]>([]);
const hotFromFallback = ref(false);

onMounted(() => {
  history.value = readHistory();
  loadHotWords();
});

function readHistory(): string[] {
  try {
    const raw = uni.getStorageSync(HISTORY_KEY);
    if (Array.isArray(raw)) {
      return raw.filter((item) => typeof item === "string" && item.trim()).slice(0, HISTORY_MAX);
    }
    return [];
  } catch (e) {
    return [];
  }
}

function writeHistory(words: string[]): void {
  history.value = words;
  try {
    uni.setStorageSync(HISTORY_KEY, words);
  } catch (e) {
    // 本地存储写满/被禁用时只影响历史展示，不阻断搜索
  }
}

async function loadHotWords(): Promise<void> {
  try {
    const list = await getHotSearchWords();
    if (Array.isArray(list) && list.length > 0) {
      hotWords.value = list;
      hotFromFallback.value = false;
      return;
    }
    throw new Error("empty hot words");
  } catch (e) {
    hotWords.value = FALLBACK_WORDS.map((word, index) => ({
      rank: index + 1,
      word,
      heat: null,
    }));
    hotFromFallback.value = true;
  }
}

function goBack(): void {
  // 无历史栈（直达/分享/H5 刷新）时兜底回视频 tab，避免 navigateBack 静默失效
  safeGoBack("/pages/video/index");
}

/** 提交搜索：写入历史后跳结果页 */
function submit(rawWord: string): void {
  const word = (rawWord || "").trim().replace(/\s+/g, " ");
  if (!word) return;
  const next = [word, ...history.value.filter((item) => item !== word)].slice(0, HISTORY_MAX);
  writeHistory(next);
  uni.navigateTo({
    url: `/pages/video/search-result?keyword=${encodeURIComponent(word)}`,
  });
}

function removeHistory(word: string): void {
  writeHistory(history.value.filter((item) => item !== word));
}

async function clearHistory(): Promise<void> {
  const ok = await confirm("确定清空全部搜索历史吗？");
  if (ok) writeHistory([]);
}
</script>

<style lang="scss" scoped>
.search-page {
  min-height: 100vh;
  background-color: var(--bg-page);
}

.search-header {
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
    /* 宽度扩到 88rpx：仅靠图标宽会形成竖条热区，不足 44px */
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

/* 热区 88rpx：原纯文字无最小尺寸，且直接 @tap 无键盘入口 */
.header-action {
  flex-shrink: 0;
  min-height: 88rpx;
  display: flex;
  align-items: center;
  font-size: var(--fs-sm);
  color: var(--text-secondary);
}

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

/* 热区 88rpx：原 48rpx 难以点中 */
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
}

/* chip 容器只做布局；可点击主体与删除按钮各自承担热区 */
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

  /* 热区 88rpx：原 32rpx（≈19px）远低于 44px 可点击尺寸 */
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

/* 热区 88rpx：原 84rpx 略低 */
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
</style>
