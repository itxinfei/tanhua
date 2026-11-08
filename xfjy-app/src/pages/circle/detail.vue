<template>
  <view class="post-detail">
    <!-- S6：错误边界捕获详情渲染异常，@retry 重新拉取动态详情 -->
    <error-boundary @retry="loadDetail">
    <scroll-view
      scroll-y
      class="detail-scroll"
    >
      <!-- 动态内容 -->
      <!-- 加载中骨架 -->
      <skeleton
        v-if="postLoading && !post"
        variant="feed"
      />
      <!-- 加载失败：与「动态不存在」区分，给明确原因 + 可重试入口。
         接口失败时旧版 post=null 直接留白，用户只看到评论区无从恢复。 -->
      <empty-state
        v-if="!post && !postLoading && postLoadFailed"
        role="alert"
        icon="info"
        title="暂时没能获取动态详情"
        desc="服务可能开小差了，检查网络后重试"
      >
        <a11y-button
          class="detail-retry"
          label="重新加载动态详情"
          @tap="loadDetail()"
        >
          重新加载
        </a11y-button>
      </empty-state>
      <!-- 动态主体：接口失败 post=null 时不渲染，避免读 null 属性崩溃（评论区仍可用） -->
      <view
        v-if="post"
        class="post-card"
      >
        <view class="post-header">
          <smart-image
            lazy-load
            class="avatar"
            :src="post.avatar"
            mode="aspectFill"
            :aria-label="`${post.nickname} 的头像`"
          />
          <view class="user-info">
            <text class="nickname">
              {{ post.nickname }}
            </text>
            <text class="time">
              {{ post.createDate }}
            </text>
          </view>
        </view>
        <view class="post-content">
          {{ post.textContent }}
        </view>
        <!-- 话题标签（点击进入话题筛选） -->
        <view
          v-if="post?.tags && post.tags.length"
          class="tag-row"
        >
          <a11y-button
            v-for="(tag, i) in post.tags"
            :key="i"
            class="tag-chip"
            :label="`查看话题 ${tag}`"
            @tap="goTag(tag)"
          >
            <text># {{ tag }}</text>
          </a11y-button>
        </view>
        <!-- filter(Boolean)：后端可能返回含空字符串的 imageContent，空串点击预览会报错 -->
        <view
          v-if="post.imageContent?.filter(Boolean).length"
          class="image-grid"
        >
          <a11y-button
            v-for="(img, i) in post.imageContent.filter(Boolean)"
            :key="i"
            class="grid-img"
            :label="`查看第 ${i + 1} 张配图`"
            @tap="previewImg(i)"
          >
            <smart-image
              lazy-load
              class="grid-img-inner"
            :src="img"
            :fallback="''"
            mode="aspectFill"
            :aria-hidden="true"
            />
          </a11y-button>
        </view>
      </view>

      <!-- 评论区 -->
      <view class="comment-section">
        <text class="section-title">
          评论 ({{ comments.length }})
        </text>
        <view
          v-for="c in comments"
          :key="c.id"
          class="comment-item"
        >
          <smart-image
            lazy-load
            class="avatar"
            :src="c.avatar"
            mode="aspectFill"
            :aria-label="`${c.nickname} 的头像`"
          />
          <view class="comment-info">
            <text class="nickname">
              {{ c.nickname }}
            </text>
            <text class="content">
              {{ c.content }}
            </text>
            <text class="time">
              {{ c.createDate }}
            </text>
          </view>
          <a11y-button
            class="comment-like"
            :class="{ liked: c.hasLiked }"
            :label="c.hasLiked ? '取消点赞，当前 ' + (c.likeCount || 0) + ' 赞' : '点赞，当前 ' + (c.likeCount || 0) + ' 赞'"
            @tap="toggleCommentLike(c)"
          >
            <view
              class="comment-like-icon"
              :class="{ 'i-ph-heart-fill': c.hasLiked, 'i-ph-heart': !c.hasLiked }"
              aria-hidden="true"
            />
            <text class="comment-like-count">
              {{ c.likeCount > 0 ? c.likeCount : '' }}
            </text>
          </a11y-button>
        </view>
        <!-- 评论首屏失败：显式错误态 + 重试，与「暂无评论」真空态区分 -->
        <empty-state
          v-if="commentLoadFailed && comments.length === 0"
          role="alert"
          icon="info"
          title="评论加载失败"
          desc="服务可能开小差了，检查网络后重试"
        >
          <a11y-button
            class="detail-retry"
            label="重新加载评论"
            @tap="loadComments"
          >
            重新加载
          </a11y-button>
        </empty-state>
        <view
          v-else-if="comments.length === 0"
          class="comment-empty"
        >
          暂无评论，快来抢沙发
        </view>
        <a11y-button
          v-if="commentHasMore"
          class="load-more"
          label="加载更多评论"
          @tap="loadMoreComments"
        >
          <text>加载更多评论</text>
        </a11y-button>
      </view>
    </scroll-view>
    </error-boundary>

    <!-- 评论输入：发送按钮 + 回车双通道（H5 浏览器/部分输入法 confirm 不可靠，仅回车会让用户以为"评论发不出去"） -->
    <view class="comment-bar safe-area-bottom">
      <input
        v-model="commentText"
        class="comment-input"
        placeholder="写评论…"
        maxlength="500"
        aria-label="写评论"
        confirm-type="send"
        @confirm="sendComment"
      >
      <a11y-button
        class="send-btn"
        variant="primary"
        label="发送评论"
        @tap="sendComment"
      >
        <text>发送</text>
      </a11y-button>
    </view>
  </view>
</template>

<script setup lang="ts">
import { ref } from "vue";
import { onLoad } from "@dcloudio/uni-app";
import A11yButton from "@/components/a11y-button.vue";
import SmartImage from "@/components/smart-image.vue";
import ErrorBoundary from "@/components/error-boundary.vue";
import EmptyState from "@/components/empty-state.vue";
import Skeleton from "@/components/skeleton.vue";
import { getMovementById, getComments, publishComment, likeComment, dislikeComment } from "@/api/circle";
import type { Movement, Comment } from "@/api/circle";
import { success } from "@/utils/ux";

const PAGE_SIZE = 20;
const postId = ref("");
const commentText = ref("");
const post = ref<Movement | null>(null);
const postLoading = ref(false);
const postLoadFailed = ref(false);
const comments = ref<Comment[]>([]);
const sending = ref(false);
const commentPage = ref(1);
const commentHasMore = ref(false);
const commentLoading = ref(false);
/** 评论首屏加载失败：与「暂无评论」真空态区分，显式错误态 + 重试（request 层仍兜底 toast） */
const commentLoadFailed = ref(false);

function previewImg(index: number | string) {
  const i = Number(index);
  const images = post.value?.imageContent?.filter(Boolean) || [];
  uni.previewImage({ urls: images, current: images[i] });
}

/** 加载动态详情 */
async function loadDetail() {
  postLoading.value = true;
  postLoadFailed.value = false;
  try {
    post.value = await getMovementById(postId.value);
  } catch {
    // 首屏失败标记：页面据此显示错误态+重试，而非只剩空荡荡的评论区
    postLoadFailed.value = true;
    // request 层统一 toast
  } finally {
    postLoading.value = false;
  }
}

/** 加载评论列表（首页/刷新） */
async function loadComments() {
  try {
    const res = await getComments(postId.value, { page: 1, pagesize: PAGE_SIZE });
    comments.value = res.items || [];
    commentPage.value = 1;
    commentHasMore.value = (res.pages || 1) > 1;
    commentLoadFailed.value = false;
  } catch {
    // 首屏评论失败：置错误态由模板显示重试入口，避免评论区只剩「评论 (0)」空白（request 层仍兜底 toast）
    commentLoadFailed.value = true;
  }
}

/** 加载更多评论 */
async function loadMoreComments() {
  if (commentLoading.value || !commentHasMore.value) return;
  commentLoading.value = true;
  try {
    const nextPage = commentPage.value + 1;
    const res = await getComments(postId.value, { page: nextPage, pagesize: PAGE_SIZE });
    comments.value = comments.value.concat(res.items || []);
    commentPage.value = nextPage;
    commentHasMore.value = (res.pages || 1) > nextPage;
  } catch {
    // request 层统一 toast
  } finally {
    commentLoading.value = false;
  }
}

/** 切换评论点赞状态 */
async function toggleCommentLike(comment: Comment) {
  const wasLiked = comment.hasLiked;
  const prevCount = comment.likeCount;
  try {
    if (wasLiked) {
      comment.hasLiked = 0;
      comment.likeCount = Math.max(0, comment.likeCount - 1);
      const latest = await dislikeComment(comment.id);
      if (typeof latest === "number") comment.likeCount = latest;
    } else {
      comment.hasLiked = 1;
      comment.likeCount += 1;
      const latest = await likeComment(comment.id);
      if (typeof latest === "number") comment.likeCount = latest;
    }
  } catch {
    // 请求失败：回滚本地状态
    comment.hasLiked = wasLiked;
    comment.likeCount = prevCount;
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
    success("评论成功");
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

<style lang="scss" scoped>
/* border-box：padding 计入高度，避免超出视口 */
.post-detail { box-sizing: border-box; height: calc(100vh - var(--window-top, 0px) - var(--tab-bar-height, 0px)); background: var(--bg-sub); display: flex; flex-direction: column; overflow: hidden; }
/* 底部内边距必须 >= 固定输入栏高度（108rpx），否则最后一条评论永远滚不到输入栏上方 */
.detail-scroll { flex: 1; min-height: 0; padding: 20rpx 24rpx calc(108rpx + var(--tab-bar-height, 0px) + env(safe-area-inset-bottom, 0px) + 24rpx); }

/* 加载失败态的重试按钮（置于 empty-state 的 slot 内） */
.detail-retry {
  margin-top: 32rpx;
  padding: 16rpx 56rpx;
  border-radius: var(--radius-pill);
  font-size: 28rpx;
  font-weight: 600;
  color: var(--text-inverse);
  background: var(--gradient-brand);
}
.post-card { background: var(--bg-card); padding: 28rpx; margin-bottom: 16rpx; border-radius: var(--radius-lg); box-shadow: 0 2rpx 12rpx rgba(0, 0, 0, 0.04);
  .post-header { display: flex; align-items: center; min-width: 0;
    .avatar { width: 76rpx; height: 76rpx; border-radius: 50%; border: 3rpx solid var(--bg-icon-soft); flex-shrink: 0; }
    /* min-width:0：长昵称才能被约束在剩余空间内，不撑破 flex 行 */
    .user-info { margin-left: 16rpx; flex: 1; min-width: 0;
      .nickname { font-size: 28rpx; font-weight: bold; display: block; color: var(--text-primary); overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
      /* #ccc ≈1.6:1 → #767676 */
      .time { font-size: 22rpx; color: var(--text-hint); margin-top: 4rpx; display: block; }
    }
  }
  /* overflow-wrap：长文本/URL/连续英文才能断行，避免撑破卡片横向溢出 */
  .post-content { font-size: 28rpx; line-height: 1.7; margin: 20rpx 0; color: var(--text-primary); overflow-wrap: anywhere; }

  /* 话题标签（发布页勾选，落库 Movement.tags） */
  .tag-row { display: flex; flex-wrap: wrap; gap: 12rpx; margin: 0 0 20rpx; }
  /* 与列表页统一：纯展示标签，不需要 88rpx 大热区（详情页标签不可点） */
  .tag-chip { display: inline-block; font-size: 22rpx; color: var(--color-primary-text); background: var(--bg-icon-soft); padding: 8rpx 18rpx; line-height: 1.4; border-radius: var(--radius-lg); }
  /* 九宫格：按容器宽度等分而非固定 rpx，避免窄屏（320px）三格 + 间距
     超出卡片内宽导致横向溢出；aspect-ratio 保证正方形 */
  .image-grid { display: flex; flex-wrap: wrap; gap: 8rpx;
    /* 九宫格每格作为可聚焦按钮（原 image 直接 click 无键盘入口） */
    .grid-img {
      flex: 1 1 calc(33.333% - 8rpx);
      max-width: calc(33.333% - 8rpx);
      aspect-ratio: 1 / 1;
      border-radius: 12rpx;
      overflow: hidden;
    }
    .grid-img-inner { width: 100%; height: 100%; display: block; }
  }
}
.comment-section { background: var(--bg-card); padding: 28rpx; border-radius: var(--radius-lg); box-shadow: 0 2rpx 12rpx rgba(0, 0, 0, 0.04);
  .section-title { font-size: 28rpx; font-weight: bold; display: block; margin-bottom: 16rpx; color: var(--text-primary); }
  .comment-item { display: flex; align-items: flex-start; padding: 18rpx 0; border-bottom: 1rpx solid var(--border-faint);
    &:last-child { border-bottom: none; }
    .avatar { width: 64rpx; height: 64rpx; border-radius: 50%; background: var(--bg-sub); flex-shrink: 0; }
    .comment-info { margin-left: 16rpx; flex: 1; min-width: 0;
      .nickname { font-size: 24rpx; color: var(--color-primary-text); display: block; }
      .content { font-size: 28rpx; color: var(--text-primary); margin-top: 6rpx; display: block; }
      /* 20rpx 极小字号 + #ccc → 提字号并提深 */
      .time { font-size: 22rpx; color: var(--text-hint); margin-top: 6rpx; display: block; }
    }
    .comment-like { display: flex; flex-direction: column; align-items: center; gap: 4rpx; flex-shrink: 0; padding: 0 8rpx;
      .comment-like-icon { font-size: 32rpx; color: var(--text-hint); line-height: 1; }
      .comment-like-count { font-size: 22rpx; color: var(--text-hint); }
      &.liked .comment-like-icon { color: var(--color-primary); }
      &.liked .comment-like-count { color: var(--color-primary); }
    }
  }
  /* 热区：88rpx 基线；原 view 直接 @tap 无键盘/读屏入口 */
  .load-more { text-align: center; padding: 24rpx; min-height: 88rpx; box-sizing: border-box; color: var(--color-primary); font-size: 26rpx; }
  .comment-empty { text-align: center; color: var(--text-hint); font-size: var(--fs-base); padding: 48rpx 0; }
}
  /* background 走 --bg-card：写死 rgba(255,255,255,.92) 在暗色模式下会变成刺眼白条 */
  .comment-bar { position: fixed; left: 0; right: 0; bottom: var(--tab-bar-height, 0px); display: flex; align-items: center; gap: 16rpx; padding: 16rpx 24rpx calc(16rpx + env(safe-area-inset-bottom, 0px)); background: var(--bg-card); backdrop-filter: blur(20px); box-shadow: 0 -8rpx 32rpx rgba(0, 0, 0, 0.08);
  .comment-input { flex: 1; height: 76rpx; background: var(--bg-sub); border-radius: 38rpx; padding: 0 28rpx; font-size: 28rpx; border: 2rpx solid transparent; transition: border-color 0.2s ease;
    &:focus { border-color: var(--color-primary); background: var(--bg-card); }
  }
  .send-btn { flex-shrink: 0; height: 76rpx; padding: 0 40rpx; border-radius: 38rpx;
    text { font-size: 28rpx; font-weight: 600; }
  }
}
</style>
