<template>
  <view class="post-detail">
    <!-- 动态内容 -->
    <view class="post-card">
      <view class="post-header">
        <image lazy-load class="avatar" :src="post.avatar" mode="aspectFill" :aria-label="`${post.nickname} 的头像`" />
        <view class="user-info">
          <text class="nickname">{{ post.nickname }}</text>
          <text class="time">{{ post.createDate }}</text>
        </view>
      </view>
      <view class="post-content">{{ post.textContent }}</view>
      <!-- 话题标签（点击进入话题筛选） -->
      <view v-if="post?.tags && post.tags.length" class="tag-row">
        <view v-for="(tag, i) in post.tags" :key="i" class="tag-chip" @tap="goTag(tag)"># {{ tag }}</view>
      </view>
      <view v-if="post.imageContent?.length" class="image-grid">
        <a11y-button v-for="(img, i) in post.imageContent" :key="i" class="grid-img" :label="`查看第 ${i + 1} 张配图`" @tap="previewImg(i)">
          <image lazy-load class="grid-img-inner" :src="img" mode="aspectFill" aria-hidden="true" />
        </a11y-button>
      </view>
    </view>

    <!-- 评论区 -->
    <view class="comment-section">
      <text class="section-title">评论 ({{ comments.length }})</text>
      <view v-for="c in comments" :key="c.id" class="comment-item">
        <image lazy-load class="avatar" :src="c.avatar" mode="aspectFill" :aria-label="`${c.nickname} 的头像`" />
        <view class="comment-info">
          <text class="nickname">{{ c.nickname }}</text>
          <text class="content">{{ c.content }}</text>
          <text class="time">{{ c.createDate }}</text>
        </view>
      </view>
    </view>

    <!-- 评论输入 -->
    <view class="comment-bar safe-area-bottom">
      <input v-model="commentText" class="comment-input" placeholder="写评论..." aria-label="写评论" confirm-type="send" @confirm="sendComment" />
    </view>
  </view>
</template>

<script setup lang="ts">
import { ref } from "vue";
import { onLoad } from "@dcloudio/uni-app";
import A11yButton from "@/components/a11y-button.vue";
import { getMovementById, getComments, publishComment } from "@/api/circle";
import type { Movement, Comment } from "@/api/circle";

const PAGE_SIZE = 20;
const postId = ref("");
const commentText = ref("");
const post = ref<Movement | null>(null);
const comments = ref<Comment[]>([]);
const sending = ref(false);

function previewImg(index: number | string) {
  const i = Number(index);
  const images = post.value?.imageContent || [];
  uni.previewImage({ urls: images, current: images[i] });
}

/** 加载动态详情 */
async function loadDetail() {
  try {
    post.value = await getMovementById(postId.value);
  } catch {
    // request 层统一 toast
  }
}

/** 加载评论列表 */
async function loadComments() {
  try {
    const res = await getComments(postId.value, { page: 1, pagesize: PAGE_SIZE });
    comments.value = res.items || [];
  } catch {
    // request 层统一 toast
  }
}

/** 发表评论，成功后刷新列表 */
async function sendComment() {
  const text = commentText.value.trim();
  if (!text || sending.value) return;
  sending.value = true;
  try {
    await publishComment(postId.value, text);
    commentText.value = "";
    uni.showToast({ title: "评论成功", icon: "success" });
    await loadComments();
  } catch {
    // request 层统一 toast
  } finally {
    sending.value = false;
  }
}

/** 点击话题标签：跳转话题筛选页 */
function goTag(tag: string) {
  uni.navigateTo({ url: "/pages/circle/tag?tag=" + encodeURIComponent(tag) });
}

onLoad((options: any) => {
  postId.value = options?.id || "";
  loadDetail();
  loadComments();
});
</script>


.post-detail { min-height: 100vh; background: var(--bg-sub); padding: 20rpx 24rpx 120rpx; }
.post-card { background: var(--bg-card); padding: 28rpx; margin-bottom: 16rpx; border-radius: 24rpx; box-shadow: 0 2rpx 12rpx rgba(0, 0, 0, 0.04);
  .post-header { display: flex; align-items: center;
    .avatar { width: 76rpx; height: 76rpx; border-radius: 50%; border: 3rpx solid var(--bg-icon-soft); }
    .user-info { margin-left: 16rpx;
      .nickname { font-size: 28rpx; font-weight: bold; display: block; color: var(--text-primary); }
      /* #ccc ≈1.6:1 → #767676 */
      .time { font-size: 22rpx; color: var(--text-hint); margin-top: 4rpx; display: block; }
    }
  }
  .post-content { font-size: 28rpx; line-height: 1.7; margin: 20rpx 0; color: var(--text-primary); }

  /* 话题标签（发布页勾选，落库 Movement.tags） */
  .tag-row { display: flex; flex-wrap: wrap; gap: 12rpx; margin: 0 0 20rpx; }
  .tag-chip { font-size: 22rpx; color: var(--color-primary-text); background: var(--bg-icon-soft); padding: 6rpx 18rpx; border-radius: 24rpx; }
  .image-grid { display: flex; flex-wrap: wrap; gap: 8rpx;
    /* 九宫格每格作为可聚焦按钮（原 image 直接 click 无键盘入口） */
    .grid-img { width: 210rpx; height: 210rpx; border-radius: 12rpx; overflow: hidden; }
    .grid-img-inner { width: 100%; height: 100%; display: block; }
  }
}
.comment-section { background: var(--bg-card); padding: 28rpx; border-radius: 24rpx; box-shadow: 0 2rpx 12rpx rgba(0, 0, 0, 0.04);
  .section-title { font-size: 28rpx; font-weight: bold; display: block; margin-bottom: 16rpx; color: var(--text-primary); }
  .comment-item { display: flex; padding: 18rpx 0; border-bottom: 1rpx solid var(--border-faint);
    &:last-child { border-bottom: none; }
    .avatar { width: 64rpx; height: 64rpx; border-radius: 50%; background: var(--bg-sub); }
    .comment-info { margin-left: 16rpx; flex: 1;
      .nickname { font-size: 24rpx; color: var(--color-primary-text); display: block; }
      .content { font-size: 28rpx; color: var(--text-primary); margin-top: 6rpx; display: block; }
      /* 20rpx 极小字号 + #ccc → 提字号并提深 */
      .time { font-size: 22rpx; color: var(--text-hint); margin-top: 6rpx; display: block; }
    }
  }
}
.comment-bar { position: fixed; left: 0; right: 0; bottom: 0; padding: 16rpx 24rpx; background: var(--bg-card); box-shadow: 0 -2rpx 12rpx rgba(0, 0, 0, 0.05);
  .comment-input { height: 72rpx; background: var(--bg-sub); border-radius: 36rpx; padding: 0 28rpx; font-size: 28rpx; }
}
