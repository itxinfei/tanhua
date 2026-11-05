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
        <!-- 主提交动作：由非语义 view 改为可聚焦按钮，支持键盘 Enter/Space -->
        <a11y-button class="submit-btn" :class="{ disabled: submitting }" :disabled="submitting" @tap="handleSubmit">
          <text>{{ submitting ? '提交中...' : '提交反馈' }}</text>
        </a11y-button>
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
import A11yButton from "@/components/a11y-button.vue";
import { submitFeedbackApi, getMyFeedbackApi, type FeedbackItem } from "@/api/kefu";
import { info, success } from "@/utils/ux";

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
    info("请填写反馈内容");
    return;
  }
  if (submitting.value) return;
  submitting.value = true;
  try {
    await submitFeedbackApi(form.content.trim(), form.contact.trim() || undefined);
    success("提交成功");
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
  background: var(--bg-sub);
}

.fb-scroll {
  height: 100%;
  padding: 20rpx 24rpx;
}

.form-card {
  background: var(--bg-card);
  border-radius: 24rpx;
  padding: 32rpx 24rpx;
  box-shadow: 0 2rpx 12rpx rgba(0, 0, 0, 0.04);

  .form-title {
    font-size: 32rpx;
    color: var(--text-primary);
    font-weight: bold;
    display: block;
    padding-left: 16rpx;
    border-left: 6rpx solid var(--color-primary);
  }

  .form-content {
    margin-top: 24rpx;
    background: var(--bg-sub);
    border-radius: 16rpx;
    padding: 20rpx;

    .content-input {
      width: 100%;
      min-height: 200rpx;
      font-size: 28rpx;
      color: var(--text-primary);
      line-height: 1.6;
    }

    .char-count {
      text-align: right;
      margin-top: 8rpx;

      text {
        /* 字数统计：原 #ccc 对比不足 */
        font-size: 22rpx;
        color: var(--text-hint);
      }
    }
  }

  .contact-input {
    margin-top: 20rpx;
    background: var(--bg-sub);
    border-radius: 16rpx;
    padding: 20rpx;
    font-size: 28rpx;
    color: var(--text-primary);
  }

  .submit-btn {
    margin-top: 32rpx;
    background: var(--color-primary);
    border-radius: 44rpx;
    text-align: center;
    padding: 22rpx 0;
    box-shadow: 0 8rpx 24rpx rgba(255, 94, 156, 0.35);

    text {
      color: var(--text-inverse);
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
    color: var(--text-primary);
    font-weight: bold;
    display: block;
    padding: 8rpx 8rpx 8rpx 16rpx;
    border-left: 6rpx solid var(--color-primary);
  }

  .empty-tip {
    text-align: center;
    padding: 60rpx 0;

    text {
      font-size: 26rpx;
      color: var(--text-hint);
    }
  }
}

.history-item {
  background: var(--bg-card);
  border-radius: 24rpx;
  padding: 24rpx;
  margin-top: 16rpx;
  box-shadow: 0 2rpx 12rpx rgba(0, 0, 0, 0.04);

  .item-header {
    display: flex;
    align-items: center;
    justify-content: space-between;

    .item-status {
      font-size: 24rpx;
      padding: 4rpx 16rpx;
      border-radius: 20rpx;

      /* 状态徽章：原 #ff9500(≈2.2:1)、#4cd964(≈1.9:1) 远低于 4.5:1，提深至达标 */
      &.st-pending {
        color: #b35c00;
        background: #fff7e6;
      }

      &.st-replied {
        color: #1a7f37;
        background: #f0fff4;
      }

      &.st-closed {
        color: var(--text-hint-strong);
        background: var(--bg-sub);
      }
    }

    .item-date {
      font-size: 22rpx;
      color: var(--text-hint);
    }
  }

  .item-content {
    font-size: 28rpx;
    color: var(--text-primary);
    line-height: 1.6;
    margin-top: 16rpx;
    display: block;
  }

  .item-reply {
    margin-top: 16rpx;
    padding: 16rpx 20rpx;
    background: var(--bg-icon-soft);
    border-radius: 12rpx;

    /* 主色 #fe3c72 在 #ffeff5 底上仅约 3.1:1，提深至 ≥4.5:1 */
    .reply-label {
      font-size: 24rpx;
      color: var(--color-primary-text);
      font-weight: 500;
    }

    .reply-text {
      font-size: 26rpx;
      color: var(--text-secondary);
      line-height: 1.6;
      margin-top: 8rpx;
      display: block;
    }
  }
}
</style>
