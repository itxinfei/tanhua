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
.post-detail { min-height: 100vh; background: #f5f5f5; padding-bottom: 120rpx; }
.post-card { background: #fff; padding: 24rpx; margin-bottom: 16rpx;
  .post-header { display: flex; align-items: center;
    .avatar { width: 72rpx; height: 72rpx; border-radius: 50%; }
    .user-info { margin-left: 16rpx;
      .nickname { font-size: 28rpx; font-weight: 500; display: block; }
      .time { font-size: 22rpx; color: #ccc; margin-top: 4rpx; display: block; }
    }
  }
  .post-content { font-size: 28rpx; line-height: 1.6; margin: 16rpx 0; }
  .image-grid { display: flex; flex-wrap: wrap; gap: 8rpx;
    .grid-img { width: 220rpx; height: 220rpx; border-radius: 8rpx; }
  }
}
.comment-section { background: #fff; padding: 24rpx;
  .section-title { font-size: 28rpx; font-weight: bold; display: block; margin-bottom: 16rpx; }
  .comment-item { display: flex; padding: 16rpx 0; border-bottom: 1rpx solid #f5f5f5;
    .avatar { width: 64rpx; height: 64rpx; border-radius: 50%; }
    .comment-info { margin-left: 16rpx; flex: 1;
      .nickname { font-size: 24rpx; color: #666; display: block; }
      .content { font-size: 28rpx; color: #333; margin-top: 4rpx; display: block; }
      .time { font-size: 20rpx; color: #ccc; margin-top: 4rpx; display: block; }
    }
  }
}
.comment-bar { position: fixed; left: 0; right: 0; bottom: 0; padding: 16rpx 24rpx; background: #fff;
  .comment-input { height: 72rpx; background: #f5f5f5; border-radius: 36rpx; padding: 0 28rpx; font-size: 28rpx; }
}
</style>