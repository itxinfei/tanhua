<template>
  <view v-if="visible" class="drawer-mask" @click="close">
    <view class="drawer-panel" @click.stop>
      <view class="drawer-header">
        <text class="drawer-title">评论{{ total > 0 ? ` ${total}` : '' }}</text>
        <text class="drawer-close" @click="close">×</text>
      </view>
      <scroll-view class="comment-list" scroll-y @scrolltolower="loadMore">
        <view v-if="loading && list.length === 0" class="empty">加载中...</view>
        <view v-else-if="list.length === 0" class="empty">暂无评论，快来抢沙发</view>
        <view v-for="c in list" :key="c.id" class="comment-item">
          <image v-if="c.avatar" class="avatar" :src="c.avatar" mode="aspectFill" />
          <view v-else class="avatar avatar-ph">{{ c.nickname ? c.nickname.charAt(0) : '?' }}</view>
          <view class="comment-body">
            <text class="nickname">{{ c.nickname }}</text>
            <text class="content">{{ c.content }}</text>
            <text class="date">{{ c.createDate }}</text>
          </view>
          <view class="like-box" @click="toggleLike(c)">
            <text class="like-icon" :class="{ liked: c.hasLiked === 1 }">{{ c.hasLiked === 1 ? '♥' : '♡' }}</text>
            <text class="like-count">{{ c.likeCount > 0 ? c.likeCount : '' }}</text>
          </view>
        </view>
        <view v-if="loading && list.length > 0" class="load-more">加载中...</view>
        <view v-else-if="!hasMore && list.length > 0" class="load-more">没有更多了</view>
      </scroll-view>
      <view class="input-bar">
        <input v-model="text" placeholder="说点什么..." placeholder-class="ph" confirm-type="send" @confirm="send" />
        <text class="send-btn" :class="{ active: text.trim() }" @click="send">发送</text>
      </view>
    </view>
  </view>
</template>

<script setup lang="ts">
import { ref, computed, watch } from "vue";
import { getVideoComments, publishVideoComment, likeVideoComment, dislikeVideoComment } from "@/api/video";
import type { Comment } from "@/api/circle";

const props = defineProps<{ visible: boolean; videoId: string }>();
const emit = defineEmits<{ (e: "close"): void }>();

const PAGE_SIZE = 10;
const list = ref<Comment[]>([]);
const page = ref(1);
const pages = ref(1);
const total = ref(0);
const loading = ref(false);
const text = ref("");

const hasMore = computed(() => page.value < pages.value);

watch(() => props.visible, (v) => { if (v && props.videoId) reload(); });

function close() { emit("close"); }

async function reload() {
  page.value = 1;
  list.value = [];
  await load();
}

async function load() {
  if (loading.value) return;
  loading.value = true;
  try {
    const res = await getVideoComments(props.videoId, { page: page.value, pagesize: PAGE_SIZE });
    list.value = page.value === 1 ? (res.items || []) : list.value.concat(res.items || []);
    pages.value = res.pages || 1;
    total.value = res.counts || 0;
  } catch {
    // request 层统一 toast
  } finally {
    loading.value = false;
  }
}

async function loadMore() {
  if (!hasMore.value || loading.value) return;
  page.value++;
  await load();
}

/** 点赞 / 取消点赞 */
async function toggleLike(c: Comment) {
  try {
    if (c.hasLiked === 1) {
      await dislikeVideoComment(c.id);
      c.hasLiked = 0;
      c.likeCount = Math.max(0, c.likeCount - 1);
    } else {
      await likeVideoComment(c.id);
      c.hasLiked = 1;
      c.likeCount += 1;
    }
  } catch {
    // request 层统一 toast
  }
}

/** 发送评论，成功后刷新列表 */
async function send() {
  const content = text.value.trim();
  if (!content || !props.videoId) return;
  try {
    await publishVideoComment(props.videoId, content);
    text.value = "";
    uni.showToast({ title: "评论成功", icon: "success" });
    await reload();
  } catch {
    // request 层统一 toast
  }
}
</script>

<style lang="scss" scoped>
.drawer-mask { position: fixed; left: 0; top: 0; right: 0; bottom: 0; background: rgba(0, 0, 0, 0.5); z-index: 999; display: flex; align-items: flex-end; }
.drawer-panel { width: 100%; height: 70vh; background: #fff; border-radius: 32rpx 32rpx 0 0; display: flex; flex-direction: column; animation: slideUp 0.25s ease; }
@keyframes slideUp { from { transform: translateY(100%); } to { transform: translateY(0); } }
.drawer-header { display: flex; align-items: center; justify-content: space-between; padding: 24rpx 32rpx; border-bottom: 1rpx solid #f5f5f5;
  .drawer-title { font-size: 30rpx; font-weight: 600; color: #333; }
  .drawer-close { font-size: 40rpx; color: #999; padding: 0 8rpx; line-height: 1; }
}
.comment-list { flex: 1; padding: 0 32rpx; }
.empty { text-align: center; color: #999; font-size: 26rpx; padding: 80rpx 0; }
.comment-item { display: flex; padding: 24rpx 0; border-bottom: 1rpx solid #f5f5f5;
  .avatar { width: 72rpx; height: 72rpx; border-radius: 50%; flex-shrink: 0; background: #eee; }
  .avatar-ph { display: flex; align-items: center; justify-content: center; font-size: 28rpx; color: #fff; background: #ff5e9c; }
  .comment-body { flex: 1; margin-left: 20rpx; display: flex; flex-direction: column;
    .nickname { font-size: 26rpx; color: #666; }
    .content { font-size: 28rpx; color: #333; margin: 8rpx 0; word-break: break-all; }
    .date { font-size: 22rpx; color: #bbb; }
  }
  .like-box { display: flex; flex-direction: column; align-items: center; padding: 8rpx 0 0 16rpx;
    .like-icon { font-size: 32rpx; color: #ccc; &.liked { color: #ff5e9c; } }
    .like-count { font-size: 22rpx; color: #999; margin-top: 4rpx; }
  }
}
.load-more { text-align: center; color: #bbb; font-size: 24rpx; padding: 24rpx 0; }
.input-bar { display: flex; align-items: center; padding: 16rpx 24rpx; padding-bottom: calc(16rpx + env(safe-area-inset-bottom)); border-top: 1rpx solid #f5f5f5; background: #fff;
  input { flex: 1; height: 72rpx; background: #f5f5f5; border-radius: 36rpx; padding: 0 28rpx; font-size: 28rpx; }
  .send-btn { margin-left: 20rpx; font-size: 28rpx; color: #ccc; &.active { color: #ff5e9c; } }
}
.ph { color: #bbb; }
</style>