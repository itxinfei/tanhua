<template>
  <view class="feedback-page">
    <scroll-view class="fb-scroll" scroll-y>
      <!-- 反馈表单 -->
      <view class="form-card shadow-card">
        <text class="form-title">意见反馈</text>
        <view class="form-content">
          <textarea
            class="content-input"
            v-model="form.content"
            placeholder="请描述您遇到的问题或建议（必填）"
            :maxlength="500"
            auto-height
          />
          <view class="char-count">
            <text>{{ form.content.length }}/500</text>
          </view>
        </view>
        <input
          class="contact-input"
          v-model="form.contact"
          placeholder="联系方式（选填，方便我们回复您）"
          :maxlength="100"
        />
        <view class="submit-btn btn-feedback" :class="{ disabled: submitting }" @click="handleSubmit">
          <text>{{ submitting ? '提交中...' : '提交反馈' }}</text>
        </view>
      </view>

      <!-- 我的反馈 -->
      <view class="history-section">
        <text class="section-title">我的反馈</text>
        <view v-if="myList.length === 0 && !listLoading" class="empty-tip">
          <text>暂无反馈记录</text>
        </view>
        <view v-for="item in myList" :key="item.id" class="history-item shadow-card">
          <view class="item-header">
            <text class="item-status" :class="statusClass(item.status)">{{ statusText(item.status) }}</text>
            <text class="item-date">{{ item.createDate }}</text>
          </view>
          <text class="item-content">{{ item.content }}</text>
          <view v-if="item.reply" class="item-reply">
            <text class="reply-label">客服回复：</text>
            <text class="reply-text">{{ item.reply }}</text>
          </view>
        </view>
      </view>
    </scroll-view>
  </view>
</template>

<script setup lang="ts">
import { ref, reactive } from "vue";
import { onShow } from "@dcloudio/uni-app";
import { submitFeedbackApi, getMyFeedbackApi, type FeedbackItem } from "@/api/kefu";

const form = reactive({
  content: "",
  contact: "",
});
const submitting = ref(false);
const myList = ref<FeedbackItem[]>([]);
const listLoading = ref(false);

/** onShow：每次进入页面刷新我的反馈（提交后返回能看到最新状态） */
onShow(() => {
  loadMyList();
});

async function loadMyList(): Promise<void> {
  listLoading.value = true;
  try {
    myList.value = await getMyFeedbackApi();
  } catch {
    // 错误提示由 request 封装统一处理
  } finally {
    listLoading.value = false;
  }
}

async function handleSubmit(): Promise<void> {
  if (!form.content.trim()) {
    uni.showToast({ title: "请填写反馈内容", icon: "none" });
    return;
  }
  if (submitting.value) return;
  submitting.value = true;
  try {
    await submitFeedbackApi(form.content.trim(), form.contact.trim() || undefined);
    uni.showToast({ title: "提交成功", icon: "success" });
    form.content = "";
    form.contact = "";
    await loadMyList();
  } catch {
    // 错误提示由 request 封装统一处理
  } finally {
    submitting.value = false;
  }
}

function statusText(status: number): string {
  return ["待处理", "已回复", "已关闭"][status] || "未知";
}

function statusClass(status: number): string {
  return ["st-pending", "st-replied", "st-closed"][status] || "";
}
</script>

<style lang="scss" scoped>
.feedback-page {
  height: 100vh;
  background: #f5f5f5;
}

.fb-scroll {
  height: 100%;
  padding: 20rpx 24rpx;
}

.form-card {
  background: #fff;
  border-radius: 20rpx;
  padding: 32rpx 24rpx;

  .form-title {
    font-size: 32rpx;
    color: #333;
    font-weight: bold;
    display: block;
  }

  .form-content {
    margin-top: 24rpx;
    background: #f9f9f9;
    border-radius: 16rpx;
    padding: 20rpx;

    .content-input {
      width: 100%;
      min-height: 200rpx;
      font-size: 28rpx;
      color: #333;
      line-height: 1.6;
    }

    .char-count {
      text-align: right;
      margin-top: 8rpx;

      text {
        font-size: 22rpx;
        color: #ccc;
      }
    }
  }

  .contact-input {
    margin-top: 20rpx;
    background: #f9f9f9;
    border-radius: 16rpx;
    padding: 20rpx;
    font-size: 28rpx;
    color: #333;
  }

  .submit-btn {
    margin-top: 32rpx;
    background: #ff5e8e;
    border-radius: 44rpx;
    text-align: center;
    padding: 22rpx 0;

    text {
      color: #fff;
      font-size: 30rpx;
      font-weight: 500;
    }

    &.disabled {
      opacity: 0.6;
    }
  }
}

.history-section {
  margin-top: 32rpx;
  padding-bottom: 40rpx;

  .section-title {
    font-size: 30rpx;
    color: #333;
    font-weight: bold;
    display: block;
    padding: 8rpx;
  }

  .empty-tip {
    text-align: center;
    padding: 60rpx 0;

    text {
      font-size: 26rpx;
      color: #ccc;
    }
  }
}

.history-item {
  background: #fff;
  border-radius: 20rpx;
  padding: 24rpx;
  margin-top: 16rpx;

  .item-header {
    display: flex;
    align-items: center;
    justify-content: space-between;

    .item-status {
      font-size: 24rpx;
      padding: 4rpx 16rpx;
      border-radius: 20rpx;

      &.st-pending {
        color: #ff9500;
        background: #fff7e6;
      }

      &.st-replied {
        color: #4cd964;
        background: #f0fff4;
      }

      &.st-closed {
        color: #999;
        background: #f5f5f5;
      }
    }

    .item-date {
      font-size: 22rpx;
      color: #ccc;
    }
  }

  .item-content {
    font-size: 28rpx;
    color: #333;
    line-height: 1.6;
    margin-top: 16rpx;
    display: block;
  }

  .item-reply {
    margin-top: 16rpx;
    padding: 16rpx 20rpx;
    background: #fff0f5;
    border-radius: 12rpx;

    .reply-label {
      font-size: 24rpx;
      color: #ff5e8e;
      font-weight: 500;
    }

    .reply-text {
      font-size: 26rpx;
      color: #666;
      line-height: 1.6;
      margin-top: 8rpx;
      display: block;
    }
  }
}
</style>