<template>
  <view class="post-detail">
    <!-- 动态内容 -->
    <view class="post-card">
      <view class="post-header">
        <image class="avatar" :src="post.avatar" mode="aspectFill" />
        <view class="user-info">
          <text class="nickname">{{ post.nickname }}</text>
          <text class="time">{{ post.time }}</text>
        </view>
      </view>
      <view class="post-content">{{ post.content }}</view>
      <view v-if="post.images?.length" class="image-grid">
        <image v-for="(img, i) in post.images" :key="i" class="grid-img" :src="img" mode="aspectFill" @click="previewImg(i)" />
      </view>
    </view>

    <!-- 评论区 -->
    <view class="comment-section">
      <text class="section-title">评论 ({{ comments.length }})</text>
      <view v-for="c in comments" :key="c.id" class="comment-item">
        <image class="avatar" :src="c.avatar" mode="aspectFill" />
        <view class="comment-info">
          <text class="nickname">{{ c.nickname }}</text>
          <text class="content">{{ c.content }}</text>
          <text class="time">{{ c.time }}</text>
        </view>
      </view>
    </view>

    <!-- 评论输入 -->
    <view class="comment-bar safe-area-bottom">
      <input v-model="commentText" class="comment-input" placeholder="写评论..." confirm-type="send" @confirm="sendComment" />
    </view>
  </view>
</template>

<script setup lang="ts">
import { ref } from "vue";
import { onLoad } from "@dcloudio/uni-app";

const postId = ref("");
const commentText = ref("");
const post = ref<any>({ avatar: "", nickname: "", time: "", content: "", images: [] });
const comments = ref<any[]>([]);

function previewImg(index: number | string) {
  const i = Number(index);
  uni.previewImage({ urls: post.value.images, current: post.value.images[i] });
}

async function sendComment() {
  if (!commentText.value.trim()) return;
  // TODO: 对接 POST /comments
  uni.showToast({ title: "评论成功", icon: "success" });
  commentText.value = "";
}

onLoad((options: any) => {
  postId.value = options?.id || "";
  // TODO: 对接 GET /movements/{id} 和 GET /comments?movementId=
});
</script>

<style lang="scss" scoped>
.post-detail { min-height: 100vh; background: #f5f5f5; padding: 20rpx 24rpx 120rpx; }
.post-card { background: #fff; padding: 28rpx; margin-bottom: 16rpx; border-radius: 24rpx; box-shadow: 0 2rpx 12rpx rgba(0, 0, 0, 0.04);
  .post-header { display: flex; align-items: center;
    .avatar { width: 76rpx; height: 76rpx; border-radius: 50%; border: 3rpx solid #fff0f5; }
    .user-info { margin-left: 16rpx;
      .nickname { font-size: 28rpx; font-weight: bold; display: block; color: #333; }
      .time { font-size: 22rpx; color: #ccc; margin-top: 4rpx; display: block; }
    }
  }
  .post-content { font-size: 28rpx; line-height: 1.7; margin: 20rpx 0; color: #333; }
  .image-grid { display: flex; flex-wrap: wrap; gap: 8rpx;
    .grid-img { width: 210rpx; height: 210rpx; border-radius: 12rpx; }
  }
}
.comment-section { background: #fff; padding: 28rpx; border-radius: 24rpx; box-shadow: 0 2rpx 12rpx rgba(0, 0, 0, 0.04);
  .section-title { font-size: 28rpx; font-weight: bold; display: block; margin-bottom: 16rpx; color: #333; }
  .comment-item { display: flex; padding: 18rpx 0; border-bottom: 1rpx solid #f5f5f5;
    &:last-child { border-bottom: none; }
    .avatar { width: 64rpx; height: 64rpx; border-radius: 50%; background: #f0f0f0; }
    .comment-info { margin-left: 16rpx; flex: 1;
      .nickname { font-size: 24rpx; color: #ff5e8e; display: block; }
      .content { font-size: 28rpx; color: #333; margin-top: 6rpx; display: block; }
      .time { font-size: 20rpx; color: #ccc; margin-top: 6rpx; display: block; }
    }
  }
}
.comment-bar { position: fixed; left: 0; right: 0; bottom: 0; padding: 16rpx 24rpx; background: #fff; box-shadow: 0 -2rpx 12rpx rgba(0, 0, 0, 0.05);
  .comment-input { height: 72rpx; background: #f5f5f5; border-radius: 36rpx; padding: 0 28rpx; font-size: 28rpx; }
}
</style>