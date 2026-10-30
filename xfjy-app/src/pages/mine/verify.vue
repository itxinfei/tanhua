<template>
  <view class="verify-page">
    <!-- 已认证 -->
    <view v-if="verified" class="verified-card">
      <text class="verified-icon">✓</text>
      <text class="verified-text">已完成实名认证</text>
    </view>
    <!-- 未认证 -->
    <template v-else>
      <view class="tips">实名信息仅用于身份核验，不会公开展示</view>
      <view class="form-card">
        <view class="form-item">
          <text class="form-label">真实姓名</text>
          <input v-model="realName" placeholder="请输入真实姓名" placeholder-class="ph" />
        </view>
        <view class="form-item">
          <text class="form-label">身份证号</text>
          <input v-model="idCard" maxlength="18" placeholder="18位身份证号" placeholder-class="ph" />
        </view>
      </view>
      <button class="submit-btn" :disabled="submitting" @click="submit">提交认证</button>
    </template>
  </view>
</template>

<script setup lang="ts">
import { ref } from "vue";
import { onShow } from "@dcloudio/uni-app";
import { submitVerify, getVerifyStatus } from "@/api/mine";

const verified = ref(false);
const realName = ref('');
const idCard = ref('');
const submitting = ref(false);

onShow(loadStatus);

/** 加载实名认证状态 */
async function loadStatus() {
  try {
    const res = await getVerifyStatus();
    verified.value = !!res?.verified;
  } catch {
    // request 层统一 toast
  }
}

/** 提交实名认证：姓名 + 身份证二要素（人脸核验 key 到位后扩展） */
async function submit() {
  if (submitting.value) return;
  const name = realName.value.trim();
  const card = idCard.value.trim();
  if (name.length < 2) {
    uni.showToast({ title: '请输入真实姓名', icon: 'none' });
    return;
  }
  if (!/^\d{17}[\dXx]$/.test(card)) {
    uni.showToast({ title: '身份证号格式不正确', icon: 'none' });
    return;
  }
  submitting.value = true;
  try {
    await submitVerify(name, card);
    uni.showToast({ title: '认证成功', icon: 'success' });
    verified.value = true;
  } catch {
    // request 层统一 toast
  } finally {
    submitting.value = false;
  }
}
</script>

<style lang="scss" scoped>
.verify-page { min-height: 100vh; background: #f5f5f5; padding: 24rpx; }
.tips { font-size: 24rpx; color: #999; line-height: 1.6; margin-bottom: 24rpx; }
.form-card { background: #fff; border-radius: 24rpx; padding: 8rpx 32rpx; box-shadow: 0 2rpx 12rpx rgba(0, 0, 0, 0.04);
  .form-item { display: flex; align-items: center; padding: 26rpx 0; border-bottom: 1rpx solid #f5f5f5;
    &:last-child { border-bottom: none; }
    .form-label { width: 160rpx; font-size: 28rpx; color: #333; flex-shrink: 0; }
    input { flex: 1; font-size: 28rpx; color: #333; }
  }
}
.ph { color: #ccc; }
.submit-btn { margin-top: 60rpx; background: #ff5e9c; color: #fff; font-size: 30rpx; border-radius: 48rpx; height: 88rpx; line-height: 88rpx;
  &[disabled] { opacity: 0.6; }
}
.verified-card { background: #fff; border-radius: 24rpx; padding: 80rpx 32rpx; text-align: center; box-shadow: 0 2rpx 12rpx rgba(0, 0, 0, 0.04);
  .verified-icon { display: block; width: 96rpx; height: 96rpx; line-height: 96rpx; margin: 0 auto 24rpx; border-radius: 50%; background: #ff5e9c; color: #fff; font-size: 48rpx; }
  .verified-text { font-size: 30rpx; color: #333; }
}
</style>