<template>
  <view class="change-phone-page">
    <view class="tips">更换后需使用新手机号登录，请确保新号码可正常接收短信</view>
    <view class="form-card">
      <view class="form-item">
        <text class="form-label">新手机号</text>
        <input v-model="phone" type="number" maxlength="11" placeholder="请输入新手机号" placeholder-class="ph" />
      </view>
      <view class="form-item">
        <text class="form-label">验证码</text>
        <input v-model="code" type="number" maxlength="6" placeholder="6位验证码" placeholder-class="ph" />
        <text class="send-btn" :class="{ disabled: countdown > 0 }" @click="sendCode">{{ countdown > 0 ? `${countdown}s` : '获取验证码' }}</text>
      </view>
    </view>
    <button class="submit-btn" :disabled="submitting" @click="submit">确认更换</button>
  </view>
</template>

<script setup lang="ts">
import { ref, onUnmounted } from "vue";
import { sendSmsCode, changePhone } from "@/api/auth";

const phone = ref('');
const code = ref('');
const countdown = ref(0);
const submitting = ref(false);
let timer: ReturnType<typeof setInterval> | null = null;

onUnmounted(() => { if (timer) clearInterval(timer); });

/** 发送验证码到新手机号（60s 倒计时防重复） */
async function sendCode() {
  if (countdown.value > 0) return;
  if (!/^1[3-9]\d{9}$/.test(phone.value)) {
    uni.showToast({ title: '请输入正确的手机号', icon: 'none' });
    return;
  }
  try {
    await sendSmsCode(phone.value);
    uni.showToast({ title: '验证码已发送', icon: 'none' });
    countdown.value = 60;
    timer = setInterval(() => {
      countdown.value--;
      if (countdown.value <= 0 && timer) { clearInterval(timer); timer = null; }
    }, 1000);
  } catch {
    // 4xx/限流由 request 层统一 toast
  }
}

/** 提交换绑：200 成功 / 403 验证码错误 / 409 手机号已被占用 */
async function submit() {
  if (submitting.value) return;
  if (!/^1[3-9]\d{9}$/.test(phone.value)) {
    uni.showToast({ title: '请输入正确的手机号', icon: 'none' });
    return;
  }
  if (!/^\d{6}$/.test(code.value)) {
    uni.showToast({ title: '请输入6位验证码', icon: 'none' });
    return;
  }
  submitting.value = true;
  try {
    await changePhone(phone.value, code.value);
    uni.showToast({ title: '换绑成功', icon: 'success' });
    setTimeout(() => uni.navigateBack(), 800);
  } catch {
    // 403/409 由 request 层统一 toast
  } finally {
    submitting.value = false;
  }
}
</script>

<style lang="scss" scoped>
.change-phone-page { min-height: 100vh; background: #f5f5f5; padding: 24rpx; }
.tips { font-size: 24rpx; color: #999; line-height: 1.6; margin-bottom: 24rpx; }
.form-card { background: #fff; border-radius: 24rpx; padding: 8rpx 32rpx; box-shadow: 0 2rpx 12rpx rgba(0, 0, 0, 0.04);
  .form-item { display: flex; align-items: center; padding: 26rpx 0; border-bottom: 1rpx solid #f5f5f5;
    &:last-child { border-bottom: none; }
    .form-label { width: 160rpx; font-size: 28rpx; color: #333; flex-shrink: 0; }
    input { flex: 1; font-size: 28rpx; color: #333; }
  }
}
.send-btn { font-size: 26rpx; color: #ff5e9c; flex-shrink: 0; margin-left: 16rpx;
  &.disabled { color: #ccc; }
}
.ph { color: #ccc; }
.submit-btn { margin-top: 60rpx; background: #ff5e9c; color: #fff; font-size: 30rpx; border-radius: 48rpx; height: 88rpx; line-height: 88rpx;
  &[disabled] { opacity: 0.6; }
}
</style>