<template>
  <!-- 评论抽屉：补齐 dialog 语义、Esc 关闭、打开时移入焦点、关闭后归还焦点 -->
  <a11y-dialog class="drawer-mask" :visible="visible" title="评论" @close="close">
    <view class="drawer-panel">
      <view class="drawer-header">
        <text class="drawer-title">评论{{ total > 0 ? ` ${total}` : '' }}</text>
        <a11y-button class="drawer-close" label="关闭评论" @tap="close">
          <text>×</text>
        </a11y-button>
      </view>
      <scroll-view class="comment-list" scroll-y @scrolltolower="loadMore">
        <view v-if="loading && list.length === 0" class="empty">加载中...</view>
        <view v-else-if="list.length === 0" class="empty">暂无评论，快来抢沙发</view>
        <view v-for="c in list" :key="c.id" class="comment-item">
          <image lazy-load v-if="c.avatar" class="avatar" :src="c.avatar" mode="aspectFill" :aria-label="`${c.nickname} 的头像`" />
          <view v-else class="avatar avatar-ph" aria-hidden="true">{{ c.nickname ? c.nickname.charAt(0) : '?' }}</view>
          <view class="comment-body">
            <text class="nickname">{{ c.nickname }}</text>
            <text class="content">{{ c.content }}</text>
            <text class="date">{{ formatDate(c.createDate) }}</text>
          </view>
          <a11y-button
            class="like-box"
            :label="c.hasLiked === 1 ? `已点赞，共 ${c.likeCount} 个赞，点击取消点赞` : `点赞，共 ${c.likeCount} 个赞`"
            @tap="toggleLike(c)"
          >
            <uni-icons class="like-icon" :type="c.hasLiked === 1 ? 'heart-filled' : 'heart'" size="32" :color="c.hasLiked === 1 ? 'var(--color-primary-text)' : 'var(--text-hint)'"></uni-icons>
            <text class="like-count">{{ c.likeCount > 0 ? formatCount(c.likeCount) : '' }}</text>
          </a11y-button>
        </view>
        <view v-if="loading && list.length > 0" class="load-more">加载中...</view>
        <view v-else-if="!hasMore && list.length > 0" class="load-more">没有更多了</view>
      </scroll-view>
      <view class="input-bar">
        <!-- placeholder 不算 label，需显式 aria-label -->
        <input v-model="text" placeholder="说点什么..." placeholder-class="ph" confirm-type="send" aria-label="评论内容" @confirm="send" />
        <a11y-button class="send-btn" :class="{ active: text.trim() }" @tap="send">
          <text>发送</text>
        </a11y-button>
      </view>
    </view>
  </a11y-dialog>
</template>

<script setup lang="ts">
import { ref, computed, watch } from "vue";
import A11yDialog from "@/components/a11y-dialog.vue";
import A11yButton from "@/components/a11y-button.vue";
import { getVideoComments, publishVideoComment, likeVideoComment, dislikeVideoComment } from "@/api/video";
import { formatCount } from "@/utils/format";
import type { Comment } from "@/api/circle";

const props = defineProps<{ visible: boolean; videoId: string }>();
const emit = defineEmits<{ (e: "close"): void }>();

/**
 * 后端部分日期字符串含异常分隔符（如 "2026年10月31??19:22"），
 * 在前端兜底清洗：将连续问号替换为空格，去除多余空白
 */
function formatDate(value?: string): string {
  if (!value) return "";
  return value.replace(/\?+/g, " ").replace(/\s+/g, " ").trim();
}

const PAGE_SIZE = 10;
const list = ref<Comment[]>([]);
const page = ref(1);
const pages = ref(1);
const total = ref(0);
const loading = ref(false);
const text = ref("");

const hasMore = computed(() => page.value < pages.value);

/** 请求代际令牌：reload 递增，在途旧请求返回时丢弃，避免关开后重开列表错乱 */
let loadToken = 0;

watch(() => props.visible, (v) => { if (v && props.videoId) reload(); });

function close() { emit("close"); }

async function reload() {
  loadToken++;
  page.value = 1;
  list.value = [];
  await load();
}

async function load() {
  if (loading.value) return;
  const token = loadToken;
  const targetPage = page.value;
  loading.value = true;
  try {
    const res = await getVideoComments(props.videoId, { page: targetPage, pagesize: PAGE_SIZE });
    if (token !== loadToken) return;
    list.value = targetPage === 1 ? (res.items || []) : list.value.concat(res.items || []);
    pages.value = res.pages || 1;
    total.value = res.counts || 0;
  } catch {
    // request 层统一 toast；失败页码回退，hasMore 恢复可重试
    if (token === loadToken && targetPage > 1) page.value = targetPage - 1;
  } finally {
    if (token === loadToken) loading.value = false;
  }
}

async function loadMore() {
  if (!hasMore.value || loading.value) return;
  page.value++;
  await load();
}

/** 发送中标记（防连点重复发评论） */
const sending = ref(false);

/** 点赞 / 取消点赞：计数以接口返回的最新值为准 */
async function toggleLike(c: Comment) {
  try {
    if (c.hasLiked === 1) {
      const count = await dislikeVideoComment(c.id);
      c.hasLiked = 0;
      if (typeof count === "number") c.likeCount = count;
    } else {
      const count = await likeVideoComment(c.id);
      c.hasLiked = 1;
      if (typeof count === "number") c.likeCount = count;
    }
  } catch {
    // request 层统一 toast
  }
}

/** 发送评论，成功后刷新列表 */
async function send() {
  const content = text.value.trim();
  if (!content || !props.videoId || sending.value) return;
  sending.value = true;
  try {
    await publishVideoComment(props.videoId, content);
    text.value = "";
    uni.showToast({ title: "评论成功", icon: "success" });
    await reload();
  } catch {
    // request 层统一 toast
  } finally {
    sending.value = false;
  }
}
</script>

<style lang="scss" scoped>
.drawer-mask { position: fixed; left: 0; top: 0; right: 0; bottom: 0; background: rgba(0, 0, 0, 0.5); z-index: 999; display: flex; align-items: flex-end; }
.drawer-panel { width: 100%; height: 70vh; background: var(--bg-card); border-radius: 32rpx 32rpx 0 0; display: flex; flex-direction: column; animation: slideUp 0.25s ease; }
@keyframes slideUp { from { transform: translateY(100%); } to { transform: translateY(0); } }
.drawer-header { display: flex; align-items: center; justify-content: space-between; padding: 24rpx 32rpx; border-bottom: 1rpx solid var(--border-faint);
  .drawer-title { font-size: 30rpx; font-weight: 600; color: var(--text-primary); }
  .drawer-close { font-size: 40rpx; color: var(--text-hint); padding: 0 12rpx; min-height: 48rpx; line-height: 1; }
}
.comment-list { flex: 1; padding: 0 32rpx; }
.empty { text-align: center; color: var(--text-hint); font-size: 26rpx; padding: 80rpx 0; }
.comment-item { display: flex; padding: 24rpx 0; border-bottom: 1rpx solid var(--border-faint);
  .avatar { width: 72rpx; height: 72rpx; border-radius: 50%; flex-shrink: 0; background: #eee; }
  .avatar-ph { display: flex; align-items: center; justify-content: center; font-size: 28rpx; color: #fff; background: var(--color-primary); }
  .comment-body { flex: 1; margin-left: 20rpx; display: flex; flex-direction: column;
    .nickname { font-size: 26rpx; color: var(--text-hint-strong); }
    .content { font-size: 28rpx; color: var(--text-primary); margin: 8rpx 0; word-break: break-all; }
    .date { font-size: 22rpx; color: var(--text-hint); }
  }
  .like-box { display: flex; flex-direction: column; align-items: center; padding: 8rpx 0 0 16rpx;
    .like-icon { font-size: 32rpx; }
    .like-count { font-size: 22rpx; color: var(--text-hint); margin-top: 4rpx; }
  }
}
.load-more { text-align: center; color: var(--text-hint); font-size: 24rpx; padding: 24rpx 0; }
.input-bar { display: flex; align-items: center; padding: 16rpx 24rpx; padding-bottom: calc(16rpx + env(safe-area-inset-bottom)); border-top: 1rpx solid var(--border-faint); background: var(--bg-card);
  input { flex: 1; height: 72rpx; background: var(--bg-sub); border-radius: 36rpx; padding: 0 28rpx; font-size: 28rpx; }
  .send-btn { margin-left: 20rpx; font-size: 28rpx; color: var(--text-hint); min-height: 48rpx; &.active { color: var(--color-primary-text); } }
}
.ph { color: var(--text-hint); }
</style>
