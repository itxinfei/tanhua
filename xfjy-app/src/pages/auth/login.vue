<template>
  <view class="login-page">
    <!-- 漂浮装饰圆（增强品牌区氛围） -->
    <view class="deco-circle deco-1 float" />
    <view class="deco-circle deco-2 float" />
    <view class="deco-circle deco-3 float" />

    <!-- 品牌区 -->
    <view class="brand">
      <image class="logo" src="/static/logo/logo-icon-transparent.png" mode="aspectFit" />
      <text class="app-name">心飞交友</text>
      <text class="slogan">心飞为你飞，遇见对的人</text>
    </view>

    <!-- 表单区 -->
    <view class="form">
      <view class="input-group">
        <text class="label">手机号</text>
        <input
          v-model="phone"
          class="input"
          type="number"
          maxlength="11"
          placeholder="请输入手机号"
          placeholder-class="placeholder"
        />
      </view>

      <view class="input-group">
        <text class="label">验证码</text>
        <view class="code-row">
          <input
            v-model="code"
            class="input code-input"
            type="number"
            maxlength="6"
            placeholder="请输入验证码"
            placeholder-class="placeholder"
          />
          <button
            class="code-btn"
            :disabled="countdown > 0 || !phoneValid"
            @click="handleSendCode"
          >
            {{ countdown > 0 ? `${countdown}s` : "获取验证码" }}
          </button>
        </view>
      </view>

      <!-- 用户协议 -->
      <view class="agreement" @click="agree = !agree">
        <view class="checkbox" :class="{ checked: agree }">
          <text v-if="agree" class="check-icon">✓</text>
        </view>
        <text class="agreement-text">
          我已阅读并同意
          <text class="link">《用户协议》</text>
          和
          <text class="link">《隐私政策》</text>
        </text>
      </view>

      <!-- 登录按钮 -->
      <button
        class="login-btn btn-feedback"
        :disabled="!canLogin"
        :class="{ disabled: !canLogin }"
        @click="handleLogin"
      >
        登录 / 注册
      </button>
    </view>
  </view>
</template>

<script setup lang="ts">
import { ref, computed } from "vue";
import { useUserStore } from "@/store/user";

const userStore = useUserStore();

/** 验证码倒计时总时长（秒） */
const COUNTDOWN_SECONDS = 60;
/** 倒计时步进间隔（ms） */
const COUNTDOWN_INTERVAL_MS = 1000;
/** 验证码最短长度 */
const MIN_CODE_LENGTH = 4;
/** 手机号正则（中国大陆 1[3-9] 开头 11 位） */
const PHONE_REGEX = /^1[3-9]\d{9}$/;

// 表单状态
const phone = ref("");
const code = ref("");
const agree = ref(false);
const countdown = ref(0);
const logging = ref(false);

// 计算属性
const phoneValid = computed(() => PHONE_REGEX.test(phone.value));
const canLogin = computed(
  () =>
    phoneValid.value &&
    code.value.length >= MIN_CODE_LENGTH &&
    agree.value &&
    !logging.value
);

// 发送验证码
async function handleSendCode() {
  if (!phoneValid.value) {
    uni.showToast({ title: "请输入正确的手机号", icon: "none" });
    return;
  }
  try {
    await userStore.sendCode(phone.value);
    uni.showToast({ title: "验证码已发送", icon: "success" });
    startCountdown();
  } catch {
    // 错误已在 request 层提示
  }
}

/** 启动验证码倒计时 */
function startCountdown() {
  countdown.value = COUNTDOWN_SECONDS;
  const timer = setInterval(() => {
    countdown.value--;
    if (countdown.value <= 0) {
      clearInterval(timer);
    }
  }, COUNTDOWN_INTERVAL_MS);
}

// 登录
async function handleLogin() {
  if (!canLogin.value) return;
  logging.value = true;
  try {
    await userStore.doLogin(phone.value, code.value);
    uni.showToast({ title: "登录成功", icon: "success" });
    // 跳转主页
    uni.switchTab({ url: "/pages/match/index" });
  } catch {
    // 错误已在 request 层提示
  } finally {
    logging.value = false;
  }
}
</script>

<style lang="scss" scoped>
.login-page {
  min-height: 100vh;
  background: linear-gradient(180deg, #ff5e8e 0%, #ff8fab 30%, #f5f5f5 60%);
  padding: 0 60rpx;
  display: flex;
  flex-direction: column;
  position: relative;
  overflow: hidden;
}

/* 漂浮装饰圆 */
.deco-circle {
  position: absolute;
  border-radius: 50%;
  background: rgba(255, 255, 255, 0.12);
  pointer-events: none;

  &.deco-1 {
    width: 300rpx;
    height: 300rpx;
    top: 60rpx;
    right: -80rpx;
    animation-duration: 3.5s;
  }

  &.deco-2 {
    width: 200rpx;
    height: 200rpx;
    top: 280rpx;
    left: -60rpx;
    animation-duration: 4s;
    animation-delay: 0.5s;
  }

  &.deco-3 {
    width: 120rpx;
    height: 120rpx;
    top: 140rpx;
    left: 180rpx;
    animation-duration: 3s;
    animation-delay: 1s;
  }
}

.brand {
  display: flex;
  flex-direction: column;
  align-items: center;
  padding-top: 180rpx;
  padding-bottom: 80rpx;

  .logo {
    width: 160rpx;
    height: 160rpx;
    border-radius: 32rpx;
  }

  .app-name {
    font-size: 48rpx;
    font-weight: bold;
    color: #fff;
    margin-top: 24rpx;
  }

  .slogan {
    font-size: 26rpx;
    color: rgba(255, 255, 255, 0.85);
    margin-top: 12rpx;
  }
}

.form {
  background: #fff;
  border-radius: 32rpx;
  padding: 48rpx 40rpx;
  box-shadow: 0 8rpx 40rpx rgba(0, 0, 0, 0.06);
}

.input-group {
  margin-bottom: 36rpx;

  .label {
    font-size: 28rpx;
    color: #666;
    margin-bottom: 12rpx;
    display: block;
  }

  .input {
    height: 88rpx;
    border: 2rpx solid #eee;
    border-radius: 16rpx;
    padding: 0 24rpx;
    font-size: 32rpx;
    background: #fafafa;
    transition: border-color 0.2s ease, box-shadow 0.2s ease;

    &:focus {
      border-color: #ff5e8e;
      box-shadow: 0 0 0 4rpx rgba(255, 94, 142, 0.12);
      background: #fff;
    }
  }

  .placeholder {
    color: #ccc;
  }
}

.code-row {
  display: flex;
  align-items: center;
  gap: 20rpx;

  .code-input {
    flex: 1;
  }

  .code-btn {
    width: 220rpx;
    height: 88rpx;
    line-height: 88rpx;
    text-align: center;
    font-size: 26rpx;
    color: #ff5e8e;
    border: 2rpx solid #ff5e8e;
    border-radius: 16rpx;
    background: #fff;
    padding: 0;

    &[disabled] {
      color: #ccc;
      border-color: #eee;
      background: #fafafa;
    }
  }
}

.agreement {
  display: flex;
  align-items: center;
  margin: 32rpx 0;

  .checkbox {
    width: 36rpx;
    height: 36rpx;
    border: 2rpx solid #ddd;
    border-radius: 50%;
    margin-right: 12rpx;
    display: flex;
    align-items: center;
    justify-content: center;

    &.checked {
      background: #ff5e8e;
      border-color: #ff5e8e;
    }

    .check-icon {
      color: #fff;
      font-size: 24rpx;
    }
  }

  .agreement-text {
    font-size: 24rpx;
    color: #999;

    .link {
      color: #ff5e8e;
    }
  }
}

.login-btn {
  width: 100%;
  height: 96rpx;
  line-height: 96rpx;
  text-align: center;
  background: linear-gradient(90deg, #ff5e8e, #ff8fab);
  color: #fff;
  font-size: 34rpx;
  font-weight: bold;
  border-radius: 48rpx;
  border: none;
  box-shadow: 0 12rpx 32rpx rgba(255, 94, 142, 0.35);

  &.disabled {
    opacity: 0.5;
    box-shadow: none;
  }
}
</style>