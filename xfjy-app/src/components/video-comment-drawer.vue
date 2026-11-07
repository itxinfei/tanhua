<template>
  <a11y-dialog
    class="drawer-mask"
    :visible="visible"
    title="评论"
    @close="close"
  >
    <view class="drawer-panel">
      <view class="drawer-header">
        <text class="drawer-title">
          评论{{ total > 0 ? ` ${total}` : '' }}
        </text>
        <a11y-button
          class="drawer-close"
          label="关闭评论"
          @tap="close"
        >
          <text>×</text>
        </a11y-button>
      </view>
      <scroll-view
        class="comment-list"
        scroll-y
        @scrolltolower="loadMore"
      >
        <view
          v-if="loading && list.length === 0"
          class="empty"
        >
          加载中…
        </view>
        <view
          v-else-if="list.length === 0"
          class="empty"
        >
          暂无评论，快来抢沙发
        </view>
        <view
          v-for="c in list"
          :key="c.id"
          class="comment-item"
        >
          <smart-image
            v-if="c.avatar"
            lazy-load
            class="avatar"
            :src="c.avatar"
            mode="aspectFill"
            :aria-label="`${c.nickname} 的头像`"
          />
          <view
            v-else
            class="avatar avatar-ph"
            aria-hidden="true"
          >
            {{ c.nickname ? c.nickname.charAt(0) : '?' }}
          </view>
          <view class="comment-body">
            <text class="nickname">
              {{ c.nickname }}
            </text>
            <text class="content">
              {{ c.content }}
            </text>
            <text class="date">
              {{ formatDate(c.createDate) }}
            </text>
          </view>
          <a11y-button
            class="like-box"
            :label="c.hasLiked === 1 ? `已点赞，共 ${c.likeCount} 个赞，点击取消点赞` : `点赞，共 ${c.likeCount} 个赞`"
            @tap="toggleLike(c)"
          >
            <view
              class="like-icon"
              :class="c.hasLiked === 1 ? 'i-ph-heart-fill liked' : 'i-ph-heart'"
              aria-hidden="true"
            />
            <text class="like-count">
              {{ c.likeCount > 0 ? formatCount(c.likeCount) : '' }}
            </text>
          </a11y-button>
        </view>
        <view
          v-if="loading && list.length > 0"
          class="load-more"
        >
          加载中…
        </view>
        <view
          v-else-if="!hasMore && list.length > 0"
          class="load-more"
        >
          没有更多了
        </view>
      </scroll-view>
      <view class="input-bar">
        <input
          v-model="text"
          placeholder="说点什么…"
          placeholder-class="ph"
          maxlength="500"
          confirm-type="send"
          aria-label="评论内容"
          @confirm="send"
          @input="onInput"
        >
        <a11y-button
          class="send-btn"
          :class="{ active: text.trim() }"
          @tap="send"
        >
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
import SmartImage from "@/components/smart-image.vue";
import { getVideoComments, publishVideoComment, likeVideoComment, dislikeVideoComment } from "@/api/video";
import { formatCount } from "@/utils/format";
import { info, success } from "@/utils/ux";
import type { Comment } from "@/api/circle";

const props = defineProps<{ visible: boolean; videoId: string }>();
const emit = defineEmits<{ (e: "close"): void; (e: "commented"): void }>();

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

let loadToken = 0;

watch(() => props.visible, (v) => { if (v && props.videoId) reload(); });

function close() { emit("close"); }

async function reload() {
  // 作废在途请求后再强发本次请求：旧请求的 finally 判定 token 不匹配、不会复位 loading，
  // 若这里仍被 load() 的 loading 早退保护挡住，loading 将永久为 true → 列表空 + 加载卡死。
  loadToken++;
  page.value = 1;
  list.value = [];
  await load(true);
}

async function load(force = false) {
  if (loading.value && !force) return;
  const token = loadToken;
  const targetPage = page.value;
  loading.value = true;
  try {
    const res = await getVideoComments(props.videoId, { page: targetPage, pageSize: PAGE_SIZE });
    if (token !== loadToken) return;
    list.value = targetPage === 1 ? (res.items || []) : list.value.concat(res.items || []);
    pages.value = res.pages || 1;
    total.value = res.counts || 0;
  } catch {
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

const sending = ref(false);

/** 输入事件：v-model 在 a11y-dialog v-if 切换场景下可能未正确更新 text，显式同步兜底 */
function onInput(e: any): void {
  text.value = e.detail?.value ?? "";
}

async function toggleLike(c: Comment) {
  // 乐观更新：先改本地状态，请求失败再回滚到请求前的精确值
  // （不用 += 反推，避免请求期间计数被改导致回滚错误）
  const wasLiked = c.hasLiked === 1;
  const prevCount = c.likeCount;
  try {
    if (wasLiked) {
      c.hasLiked = 0;
      c.likeCount = Math.max(0, c.likeCount - 1);
      const count = await dislikeVideoComment(c.id);
      if (typeof count === "number") c.likeCount = count;
    } else {
      c.hasLiked = 1;
      c.likeCount += 1;
      const count = await likeVideoComment(c.id);
      if (typeof count === "number") c.likeCount = count;
    }
  } catch {
    // 请求失败：回滚（request 层统一 toast）
    c.hasLiked = wasLiked ? 1 : 0;
    c.likeCount = prevCount;
  }
}

async function send() {
  const content = text.value.trim();
  if (!content) { info("说点什么再发送吧"); return; }
  if (!props.videoId || sending.value) return;
  sending.value = true;
  try {
    await publishVideoComment(props.videoId, content);
    text.value = "";
    success("评论成功");
    emit("commented");
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
.drawer-header { display: flex; align-items: center; justify-content: space-between; padding: var(--space-md) var(--space-lg); border-bottom: 1rpx solid var(--border-faint);
  .drawer-title { font-size: var(--fs-md); font-weight: 600; color: var(--text-primary); }
  .drawer-close { font-size: 40rpx; color: var(--text-hint); padding: 0 var(--space-sm); min-height: 88rpx; line-height: 1; }
}
.comment-list { flex: 1; padding: 0 var(--space-lg); }
.empty { text-align: center; color: var(--text-hint); font-size: var(--fs-base); padding: 80rpx 0; }
.comment-item { display: flex; padding: var(--space-md) 0; border-bottom: 1rpx solid var(--border-faint);
  .avatar { width: 72rpx; height: 72rpx; border-radius: 50%; flex-shrink: 0; background: var(--bg-icon-soft); }
  .avatar-ph { display: flex; align-items: center; justify-content: center; font-size: var(--fs-xl); color: var(--text-inverse); background: var(--color-primary); }
  .comment-body { flex: 1; margin-left: 20rpx; display: flex; flex-direction: column;
    .nickname { font-size: var(--fs-base); color: var(--text-hint-strong); }
    .content { font-size: var(--fs-base); color: var(--text-primary); margin: var(--space-xs) 0; word-break: break-all; }
    .date { font-size: var(--fs-xs); color: var(--text-hint); }
  }
  .like-box { display: flex; flex-direction: column; align-items: center; padding: var(--space-xs) 0 0 16rpx;
    .like-icon { width: 32rpx; height: 32rpx; color: var(--text-hint);
      &.liked { color: var(--color-primary); }
    }
    .like-count { font-size: var(--fs-xs); color: var(--text-hint); margin-top: 4rpx; }
  }
}
.load-more { text-align: center; color: var(--text-hint); font-size: var(--fs-sm); padding: var(--space-md) 0; }
.input-bar { display: flex; align-items: center; padding: var(--space-sm) var(--space-md); padding-bottom: calc(var(--space-sm) + env(safe-area-inset-bottom)); border-top: 1rpx solid var(--border-faint); background: var(--bg-card);
  input { flex: 1; height: 72rpx; background: var(--bg-sub); border-radius: 36rpx; padding: 0 28rpx; font-size: var(--fs-base); }
  .send-btn { margin-left: 20rpx; font-size: var(--fs-base); color: var(--text-hint); min-height: 88rpx; &.active { color: var(--color-primary); font-weight: 600; } }
}
.ph { color: var(--text-hint); }
</style>
