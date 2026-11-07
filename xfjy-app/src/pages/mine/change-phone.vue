<template>
  <view class="change-phone-page">
    <view class="tips">
      更换后需使用新手机号登录，请确保新号码可正常接收短信
    </view>
    <view class="form-card">
      <view class="form-item">
        <text
          :id="phoneLabelId"
          class="form-label"
        >
          新手机号
        </text>
        <input
          v-model="phone"
          type="number"
          maxlength="11"
          placeholder="请输入新手机号"
          placeholder-class="ph"
          aria-label="新手机号"
          :aria-labelledby="phoneLabelId"
          @input="sanitizePhone"
        >
      </view>
      <view class="form-item">
        <text
          :id="codeLabelId"
          class="form-label"
        >
          验证码
        </text>
        <input
          v-model="code"
          type="number"
          maxlength="6"
          placeholder="6位验证码"
          placeholder-class="ph"
          aria-label="验证码"
          :aria-labelledby="codeLabelId"
          @input="sanitizeCode"
        >
        <a11y-button
          class="send-btn"
          :class="{ disabled: countdown > 0 }"
          :disabled="countdown > 0"
          :label="countdown > 0 ? `重新获取验证码，请等待 ${countdown} 秒` : '获取验证码'"
          @tap="sendCode"
        >
          {{ countdown > 0 ? `${countdown}s` : '获取验证码' }}
        </a11y-button>
      </view>
    </view>
    <a11y-button
      class="submit-btn"
      variant="primary"
      block
      label="确认更换手机号"
      :loading="submitting"
      :disabled="submitting"
      @tap="submit"
    >
      <text>确认更换</text>
    </a11y-button>
  </view>
</template>

<script setup lang="ts">
import { ref, onUnmounted } from "vue";
import { onUnload, onHide } from "@dcloudio/uni-app";
import A11yButton from "@/components/a11y-button.vue";

/** 表单字段的唯一 id：用于 aria-labelledby 建立 input 与可见标签的程序化关联（WCAG 1.3.1 / 3.3.2） */
const phoneLabelId = "change-phone-label";
const codeLabelId = "change-code-label";
import { sendSmsCode, changePhone } from "@/api/auth";
import { safeGoBack } from "@/utils/nav";
import { info, success } from "@/utils/ux";

const phone = ref('');
const code = ref('');
const countdown = ref(0);
const submitting = ref(false);
let timer: ReturnType<typeof setInterval> | null = null;

/**
 * 输入清洗（M-A6，模式对齐 login.vue）：部分安卓机型 type="number" 仍可输入 +、-、.、空格，
 * 残留到提交才报正则不匹配。采用「防抖清洗 + 提交前兜底」：
 * uni-h5 input 事件有 100ms 节流，@input 里同步写回不可靠，停手后再清。
 */
const SANITIZE_DEBOUNCE_MS = 150;
let phoneSanitizeTimer: ReturnType<typeof setTimeout> | null = null;
let codeSanitizeTimer: ReturnType<typeof setTimeout> | null = null;

function stripNonDigits(value: string): string {
  return value.replace(/\D/g, "");
}

function sanitizePhone(): void {
  if (phoneSanitizeTimer) clearTimeout(phoneSanitizeTimer);
  phoneSanitizeTimer = setTimeout(() => {
    phoneSanitizeTimer = null;
    const cleaned = stripNonDigits(phone.value);
    if (cleaned !== phone.value) phone.value = cleaned;
  }, SANITIZE_DEBOUNCE_MS);
}

function sanitizeCode(): void {
  if (codeSanitizeTimer) clearTimeout(codeSanitizeTimer);
  codeSanitizeTimer = setTimeout(() => {
    codeSanitizeTimer = null;
    const cleaned = stripNonDigits(code.value);
    if (cleaned !== code.value) code.value = cleaned;
  }, SANITIZE_DEBOUNCE_MS);
}

/** 提交前兜底清洗（同步，不可绕过的最后一道） */
function forceSanitize(): void {
  phone.value = stripNonDigits(phone.value);
  code.value = stripNonDigits(code.value);
}

onUnmounted(() => {
  if (timer) clearInterval(timer);
  if (phoneSanitizeTimer) clearTimeout(phoneSanitizeTimer);
  if (codeSanitizeTimer) clearTimeout(codeSanitizeTimer);
});

/** 延迟导航定时器：离页（卸载/压栈隐藏）即清理，防止换绑成功后用户手动返回仍被自动回退劫持 */
let navTimer: ReturnType<typeof setTimeout> | null = null;
function scheduleNav(delay: number, nav: () => void): void {
  if (navTimer) clearTimeout(navTimer);
  navTimer = setTimeout(() => {
    navTimer = null;
    nav();
  }, delay);
}
function clearPendingNav(): void {
  if (navTimer) {
    clearTimeout(navTimer);
    navTimer = null;
  }
}
onUnload(clearPendingNav);
onHide(clearPendingNav);

/** 发送验证码到新手机号（60s 倒计时防重复） */
async function sendCode() {
  if (countdown.value > 0) return;
  forceSanitize();
  if (!/^1[3-9]\d{9}$/.test(phone.value)) {
    info('请输入正确的手机号');
    return;
  }
  try {
    await sendSmsCode(phone.value);
    info('验证码已发送');
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
  forceSanitize();
  if (!/^1[3-9]\d{9}$/.test(phone.value)) {
    info('请输入正确的手机号');
    return;
  }
  if (!/^\d{6}$/.test(code.value)) {
    info('请输入6位验证码');
    return;
  }
  submitting.value = true;
  try {
    await changePhone(phone.value, code.value);
    success('换绑成功');
    scheduleNav(800, () => safeGoBack("/pages/mine/index"));
  } catch {
    // 403/409 由 request 层统一 toast
  } finally {
    submitting.value = false;
  }
}
</script>

<style lang="scss" scoped>
.change-phone-page { min-height: 100vh; background: var(--bg-sub); padding: 24rpx; }
/* 提示文案 #999（≈2.85:1）→ #767676 */
.tips { font-size: 24rpx; color: var(--text-hint); line-height: 1.6; margin-bottom: 24rpx; }
.form-card { background: var(--bg-card); border-radius: 24rpx; padding: 8rpx 32rpx; box-shadow: 0 2rpx 12rpx rgba(0, 0, 0, 0.04);
  .form-item { display: flex; align-items: center; padding: 26rpx 0; border-bottom: 1rpx solid var(--border-faint);
    &:last-child { border-bottom: none; }
    .form-label { width: 160rpx; font-size: 28rpx; color: var(--text-primary); flex-shrink: 0; }
    /* 热区 ≥88rpx（44px）：原仅由 line-height 撑高约 38.8rpx，点中困难 */
    input { flex: 1; min-height: 88rpx; font-size: 28rpx; color: var(--text-primary); }
  }
}
/* 发送验证码按钮：#fe3c72 on #fff ≈3.4:1 → #c2185b；禁用态 #ccc → #767676。
   触摸热区由 a11y-button 组件内置 min-height:88rpx 兜底（页面级 (0,2,0) 无法覆盖组件 (0,3,0)），
   此处不再写会失效的 min-height，避免误导维护者 */
.send-btn { font-size: 26rpx; color: var(--color-primary-text); flex-shrink: 0; margin-left: 16rpx; padding: 12rpx 8rpx;
  &.disabled { color: var(--text-hint); }
}
/* 占位符 #ccc → #767676 */
.ph { color: var(--text-hint); }
/* 提交按钮：a11y-button primary 变体（品牌渐变），仅补外边距与宽度 */
.submit-btn { margin-top: 60rpx; }
</style>
