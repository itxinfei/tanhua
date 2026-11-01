<template>
  <view class="login-page">
    <!-- 漂浮装饰心形（增强品牌区氛围）：纯装饰，对读屏隐藏 -->
    <view class="deco-heart deco-1 float" aria-hidden="true" />
    <view class="deco-heart deco-2 float" aria-hidden="true" />
    <view class="deco-heart deco-3 float" aria-hidden="true" />

    <!-- 品牌区 -->
    <view class="brand">
      <!-- logo 与紧随其后的 app-name 文字语义重复，标为装饰避免重复朗读 -->
      <image lazy-load class="logo" src="/static/logo/logo-icon-transparent.png" mode="aspectFit" aria-hidden="true" />
      <text class="app-name">心飞交友</text>
      <text class="slogan">心飞为你飞，遇见对的人</text>
    </view>

    <!-- 表单区 -->
    <view class="form">
      <view class="input-group">
        <text :id="PHONE_LABEL_ID" class="label">手机号</text>
        <input
          v-model="phone"
          class="input"
          type="number"
          maxlength="11"
          placeholder="请输入手机号"
          placeholder-class="placeholder"
          :aria-labelledby="PHONE_LABEL_ID"
        />
      </view>

      <view class="input-group">
        <text :id="CODE_LABEL_ID" class="label">验证码</text>
        <view class="code-row">
          <input
            v-model="code"
            class="input code-input"
            type="number"
            maxlength="6"
            placeholder="请输入验证码"
            placeholder-class="placeholder"
            :aria-labelledby="CODE_LABEL_ID"
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

      <!-- 用户协议：整行作为语义 checkbox，支持键盘 Space 切换（WCAG 2.1.1 / 4.1.2） -->
      <a11y-button
        class="agreement"
        role="checkbox"
        :checked="agree"
        label="我已阅读并同意《用户协议》和《隐私政策》"
        @tap="agree = !agree"
      >
        <view class="checkbox" :class="{ checked: agree }">
          <text v-if="agree" class="check-icon">✓</text>
        </view>
        <text class="agreement-text">
          我已阅读并同意
          <text class="link" role="link" @click.stop="openAgreement('user')">《用户协议》</text>
          和
          <text class="link" role="link" @click.stop="openAgreement('privacy')">《隐私政策》</text>
        </text>
      </a11y-button>

      <!-- 登录按钮 -->
      <button
        class="login-btn btn-feedback"
        :disabled="!canLogin || logging"
      :loading="logging"
        :class="{ disabled: !canLogin || logging }"
        @click="handleLogin"
      >
        {{ logging ? '登录中...' : '登录 / 注册' }}
      </button>
    </view>
  </view>
</template>

<script setup lang="ts">
import { ref, computed, onUnmounted } from "vue";
import { useUserStore } from "@/store/user";
import A11yButton from "@/components/a11y-button.vue";
import { success, info } from "@/utils/ux";

/** 表单标签 id：供 input 的 aria-labelledby 关联，保证读屏能读出字段名 */
const PHONE_LABEL_ID = "login-phone-label";
const CODE_LABEL_ID = "login-code-label";

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
    info("请输入正确的手机号");
    return;
  }
  try {
    await userStore.sendCode(phone.value);
    success("验证码已发送");
    startCountdown();
  } catch {
    // 错误已在 request 层提示
  }
}

/** 倒计时定时器句柄：页面卸载时清理，避免离页后 setInterval 继续跑 */
let countdownTimer: ReturnType<typeof setInterval> | null = null;

/** 启动验证码倒计时 */
function startCountdown() {
  if (countdownTimer) clearInterval(countdownTimer);
  countdown.value = COUNTDOWN_SECONDS;
  countdownTimer = setInterval(() => {
    countdown.value--;
    if (countdown.value <= 0 && countdownTimer) {
      clearInterval(countdownTimer);
      countdownTimer = null;
    }
  }, COUNTDOWN_INTERVAL_MS);
}

onUnmounted(() => {
  if (countdownTimer) clearInterval(countdownTimer);
});

// 跳转用户协议 / 隐私政策落地页
function openAgreement(type: 'user' | 'privacy') {
  uni.navigateTo({ url: `/pages/mine/agreement?type=${type}` })
}

// 登录
async function handleLogin() {
  if (!canLogin.value) return;
  logging.value = true;
  try {
    await userStore.doLogin(phone.value, code.value);
    success("登录成功");
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
  background: linear-gradient(180deg, var(--color-primary-deep) 0%, var(--color-primary) 30%, #f5f5f5 60%);
  padding: 0 60rpx;
  display: flex;
  flex-direction: column;
  position: relative;
  overflow: hidden;
}

/* 漂浮装饰心形 */
.deco-heart {
  position: absolute;
  color: rgba(255, 255, 255, 0.14);
  line-height: 1;
  pointer-events: none;
  display: flex;
  align-items: center;
  justify-content: center;

  &::after {
    content: '\2665';
  }

  &.deco-1 {
    font-size: 320rpx;
    top: 40rpx;
    right: -100rpx;
    animation-duration: 3.5s;
  }

  &.deco-2 {
    font-size: 200rpx;
    top: 280rpx;
    left: -60rpx;
    animation-duration: 4s;
    animation-delay: 0.5s;
  }

  &.deco-3 {
    font-size: 120rpx;
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
  background: var(--bg-card);
  border-radius: 32rpx;
  padding: 48rpx 40rpx;
  box-shadow: 0 8rpx 40rpx rgba(0, 0, 0, 0.06);
}

.input-group {
  margin-bottom: 36rpx;

  .label {
    font-size: 28rpx;
    color: var(--text-secondary);
    margin-bottom: 12rpx;
    display: block;
  }

  .input {
    height: 88rpx;
    border: 2rpx solid var(--border-light);
    border-radius: 16rpx;
    padding: 0 24rpx;
    font-size: 32rpx;
    background: var(--bg-sub);
    transition: border-color 0.2s ease, box-shadow 0.2s ease;

    &:focus {
      border-color: var(--color-primary);
      box-shadow: 0 0 0 4rpx rgba(255, 94, 156, 0.12);
      background: var(--bg-card);
    }
  }

  .placeholder {
    color: var(--text-hint);
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
    color: var(--color-primary);
    border: 2rpx solid var(--color-primary);
    border-radius: 16rpx;
    background: var(--bg-card);
    padding: 0;

    &[disabled] {
      color: var(--text-hint);
      border-color: var(--border-light);
      background: var(--bg-sub);
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
    border: 2rpx solid var(--border);
    border-radius: 50%;
    margin-right: 12rpx;
    display: flex;
    align-items: center;
    justify-content: center;

    &.checked {
      background: var(--color-primary);
      border-color: var(--color-primary);
    }

    .check-icon {
      color: #fff;
      font-size: 24rpx;
    }
  }

  .agreement-text {
    font-size: 24rpx;
    color: var(--text-hint);

    .link {
      color: var(--color-primary-text);
    }
  }
}

.login-btn {
  width: 100%;
  height: 96rpx;
  line-height: 96rpx;
  text-align: center;
  background: var(--gradient-brand-h);
  color: #fff;
  font-size: 34rpx;
  font-weight: bold;
  border-radius: 48rpx;
  border: none;
  box-shadow: 0 12rpx 32rpx rgba(255, 94, 156, 0.35);

  &.disabled {
    opacity: 0.5;
    box-shadow: none;
  }
}
</style>
