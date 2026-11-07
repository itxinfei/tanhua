<template>
  <view class="login-page">
    <!-- 漂浮装饰心形（增强品牌区氛围）：纯装饰，对读屏隐藏 -->
    <view
      class="deco-heart deco-1"
      aria-hidden="true"
    />
    <view
      class="deco-heart deco-2"
      aria-hidden="true"
    />
    <view
      class="deco-heart deco-3"
      aria-hidden="true"
    />
    <view
      class="deco-heart deco-4"
      aria-hidden="true"
    />

    <!-- 品牌区：padding-top 动态避让状态栏/刘海/挖孔（custom 导航页必须） -->
    <view
      class="brand anim-fade-down"
      :style="{ paddingTop: statusBarHeight + 90 + 'px' }"
    >
      <!-- logo 与紧随其后的 app-name 文字语义重复，标为装饰避免重复朗读 -->
      <image
        lazy-load
        class="logo"
        src="/static/logo/logo-icon-transparent.png"
        mode="aspectFit"
        aria-hidden="true"
      />
      <text class="app-name">
        心飞交友
      </text>
      <text class="slogan">
        心飞为你飞，遇见对的人
      </text>
    </view>

    <!-- 表单区 -->
    <view class="form anim-fade-up anim-delay-1">
      <view class="input-group">
        <text
          :id="PHONE_LABEL_ID"
          class="label"
        >
          手机号
        </text>
        <input
          v-model="phone"
          class="input"
          :class="{ 'input--error': errors.phone }"
          type="number"
          inputmode="numeric"
          maxlength="11"
          confirm-type="next"
          placeholder="请输入手机号"
          placeholder-class="placeholder"
          autocomplete="tel"
          :aria-labelledby="PHONE_LABEL_ID"
          :aria-invalid="errors.phone ? 'true' : 'false'"
          :aria-describedby="errors.phone ? PHONE_ERROR_ID : null"
          @input="sanitizePhone"
          @blur="validatePhone"
        >
        <text
          v-if="errors.phone"
          :id="PHONE_ERROR_ID"
          class="field-error"
          role="alert"
        >
          {{ errors.phone }}
        </text>
      </view>

      <view class="input-group">
        <text
          :id="CODE_LABEL_ID"
          class="label"
        >
          验证码
        </text>
        <view class="code-row">
          <input
            v-model="code"
            class="input code-input"
            :class="{ 'input--error': errors.code }"
            type="number"
            inputmode="numeric"
            maxlength="6"
            confirm-type="done"
            placeholder="请输入验证码"
            placeholder-class="placeholder"
            autocomplete="one-time-code"
            :aria-labelledby="CODE_LABEL_ID"
            :aria-invalid="errors.code ? 'true' : 'false'"
            :aria-describedby="errors.code ? CODE_ERROR_ID : null"
            @input="sanitizeCode"
            @blur="validateCode"
          >
          <a11y-button
            variant="ghost"
            shape="default"
            class="code-btn"
            :disabled="countdown > 0 || !phoneValid"
            @tap="handleSendCode"
          >
            {{ countdown > 0 ? `${countdown}s` : "获取验证码" }}
          </a11y-button>
        </view>
        <text
          v-if="errors.code"
          :id="CODE_ERROR_ID"
          class="field-error"
          role="alert"
        >
          {{ errors.code }}
        </text>
      </view>

      <!-- 用户协议：整行作为语义 checkbox，支持键盘 Space 切换（WCAG 2.1.1 / 4.1.2） -->
      <a11y-button
        class="agreement"
        role="checkbox"
        :checked="agree"
        label="我已阅读并同意《用户协议》和《隐私政策》"
        @tap="agree = !agree"
      >
        <view
          class="checkbox"
          :class="{ checked: agree }"
        >
          <text
            v-if="agree"
            class="check-icon"
          >
            ✓
          </text>
        </view>
        <text class="agreement-text">
          我已阅读并同意
          <text
            class="link"
            role="link"
            @click.stop="openAgreement('user')"
          >
            《用户协议》
          </text>
          和
          <text
            class="link"
            role="link"
            @click.stop="openAgreement('privacy')"
          >
            《隐私政策》
          </text>
        </text>
      </a11y-button>

      <!-- 登录按钮：统一主按钮（primary + block），loading/disabled 由组件接管 -->
      <a11y-button
        variant="primary"
        block
        :loading="logging"
        :disabled="!canLogin || logging"
        @tap="handleLogin"
      >
        {{ logging ? "登录中…" : "登录 / 注册" }}
      </a11y-button>
    </view>

    <!-- 协议半屏弹窗：登录前阅读（探探式），滚到底部后「同意并继续」点亮并自动勾选 -->
    <a11y-dialog
      :visible="sheetVisible"
      :title="sheetContent.title"
      :mask-closable="true"
      class="sheet anim-fade-in"
      @close="sheetVisible = false"
    >
      <view class="sheet-panel anim-slide-up">
        <!-- 抓手：底部弹层的可视可供性（affordance） -->
        <view
          class="sheet-grabber"
          aria-hidden="true"
        />
        <view class="sheet-header">
          <text class="sheet-title">
            {{ sheetContent.title }}
          </text>
          <text
            class="sheet-full"
            role="link"
            @click.stop="goFullAgreement"
          >
            查看全文 ›
          </text>
        </view>

        <!-- 阅读进度条 + 百分比反馈 -->
        <view
          class="sheet-progress-row"
          aria-hidden="true"
        >
          <view class="sheet-progress">
            <view
              class="sheet-progress-fill"
              :style="{ width: readProgress + '%' }"
            />
          </view>
          <text class="sheet-progress-text">
            {{ readComplete ? "已读完" : readProgress + "%" }}
          </text>
        </view>

        <scroll-view
          scroll-y
          class="sheet-scroll"
          :scroll-top="0"
          @scroll="onSheetScroll"
          @scrolltolower="onSheetScrollToLower"
        >
          <text
            v-for="(p, i) in sheetContent.paragraphs"
            :key="i"
            class="sheet-paragraph"
          >
            {{ p }}
          </text>
        </scroll-view>

        <!-- 主行动唯一：通栏主按钮承担视觉重量（探探/微信式）；
             「不同意」降级为文字链接，主次分明 -->
        <view class="sheet-footer">
          <a11y-button
            variant="primary"
            block
            :disabled="!readComplete"
            @tap="agreeFromSheet"
          >
            {{ readComplete ? "同意并继续" : "阅读全文后可同意" }}
          </a11y-button>
          <a11y-button
            variant="text"
            class="sheet-disagree"
            label="不同意协议，关闭弹窗"
            @tap="disagreeFromSheet"
          >
            不同意
          </a11y-button>
        </view>
      </view>
    </a11y-dialog>
  </view>
</template>

<script setup lang="ts">
import { ref, reactive, computed, watch, nextTick, onUnmounted } from "vue";
import { onLoad } from "@dcloudio/uni-app";
import { useUserStore } from "@/store/user";
import { isTabPage } from "@/utils/request";
import A11yButton from "@/components/a11y-button.vue";
import A11yDialog from "@/components/a11y-dialog.vue";
import { AGREEMENT_CONTENT, type AgreementContent } from "@/config/agreement-content";
import { success, info, fail } from "@/utils/ux";

/** 表单标签 id：供 input 的 aria-labelledby 关联，保证读屏能读出字段名 */
const PHONE_LABEL_ID = "login-phone-label";
const CODE_LABEL_ID = "login-code-label";
/** 错误提示 id：供 input 的 aria-describedby 关联，读屏能读出错误内容（WCAG 3.3.1） */
const PHONE_ERROR_ID = "login-phone-error";
const CODE_ERROR_ID = "login-code-error";

const userStore = useUserStore();

/**
 * 登录过期回跳目标：由 request.handleUnauthorized / auth-guard 拦截时编码传入，
 * 登录成功后优先回跳原页面，避免 token 过期打断深层页/支付流程后上下文全丢（S7）。
 *
 * 安全：必须为站内绝对路径（以 / 开头），拒绝外部 URL 防止开放重定向钓鱼攻击。
 */
let redirectTarget = "";

onLoad((options) => {
  const raw = options?.redirect || "";
  let decoded = "";
  try {
    decoded = raw ? decodeURIComponent(raw) : "";
  } catch {
    decoded = raw;
  }
  // 开放重定向防护：仅接受站内绝对路径（/pages/...），拒绝 https://evil.com 等外部地址
  redirectTarget = decoded.startsWith("/") ? decoded : "";
});

/** 状态栏高度（px）：custom 导航页需动态避让刘海/挖孔，兜底 44（iOS 刘海屏典型值） */
const statusBarHeight = uni.getWindowInfo?.().statusBarHeight || 44;

/** 验证码倒计时总时长（秒） */
const COUNTDOWN_SECONDS = 60;
/** 倒计时步进间隔（ms） */
const COUNTDOWN_INTERVAL_MS = 1000;
/** 验证码最短长度 */
const MIN_CODE_LENGTH = 4;
/** 手机号正则（中国大陆 1[3-9] 开头 11 位） */
const PHONE_REGEX = /^1[3-9]\d{9}$/;

/**
 * 输入清洗：部分安卓机型 type="number" 仍允许输入 +、-、.、空格等字符，
 * 只靠 maxlength 无法拦住，导致正则永远不匹配（BUG-03 收口）。
 *
 * 注意：uni-app H5 的 uni-input 内部对 input 事件做了 100ms 节流
 * （uni-h5.es.js 中 triggerInputFn = throttle(..., 100)），快速连输时
 * 中间态写回会被丢弃，仅在 @input 里同步清洗不可靠。因此采用
 * 「防抖清洗 + 提交前兜底」：
 *  1. 每次输入启动 150ms 定时器，停手后才写回（避开节流窗口）；
 *  2. handleSendCode / handleLogin 提交前再强制清洗一次，确保到后端的值一定是纯数字。
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

// 表单状态
const phone = ref("");
const code = ref("");
const agree = ref(false);
const countdown = ref(0);
const logging = ref(false);

/**
 * 字段级内联错误：blur / 提交时写入，输入内容变化时清空。
 * 用 toast 做全局反馈的同时保留字段定位，让读屏与键盘用户能准确找到问题字段
 * （WCAG 3.3.1 Error Identification + 3.3.3 Error Suggestion）。
 */
const errors = reactive<{ phone: string; code: string }>({ phone: "", code: "" });

/** 手机号校验：写入 errors.phone */
function validatePhone(): void {
  if (!phone.value) {
    errors.phone = "请输入手机号";
  } else if (!PHONE_REGEX.test(phone.value)) {
    errors.phone = "请输入正确的 11 位手机号";
  } else {
    errors.phone = "";
  }
}

/** 验证码校验：写入 errors.code */
function validateCode(): void {
  if (!code.value) {
    errors.code = "请输入验证码";
  } else if (code.value.length < MIN_CODE_LENGTH) {
    errors.code = "请输入完整的验证码";
  } else {
    errors.code = "";
  }
}

/** 输入变化时清掉该字段的错误（避免错误信息残留到已修正的字段） */
watch(phone, () => {
  if (errors.phone) errors.phone = "";
});

// 计算属性
const phoneValid = computed(() => PHONE_REGEX.test(phone.value));
/**
 * 可提交基础表单校验（不含协议勾选）。
 * 协议未勾选时不再让按钮整体禁用——那样用户点了没有任何反馈，
 * 改为允许点击，在 handleLogin 里给出明确的「请先同意协议」提示。
 */
const canLogin = computed(
  () =>
    phoneValid.value &&
    code.value.length >= MIN_CODE_LENGTH &&
    !logging.value
);

/** 验证码输满自动提交（探探式体验）：仅在手机号有效、协议已勾选、非登录中时触发，不打扰用户 */
const CODE_AUTO_SUBMIT_LENGTH = 6;
watch(code, (val) => {
  // 输入变化即清掉该字段错误
  if (errors.code) errors.code = "";
  // 输满自动提交
  if (val.length >= CODE_AUTO_SUBMIT_LENGTH && phoneValid.value && agree.value && !logging.value) {
    handleLogin();
  }
});

// 发送验证码
async function handleSendCode() {
  forceSanitize();
  validatePhone();
  if (!phoneValid.value) {
    info(errors.phone || "请输入正确的手机号");
    return;
  }
  try {
    await userStore.sendCode(phone.value);
    success("验证码已发送，请查收短信");
    startCountdown();
  } catch {
    // request 层通常已 toast；此处兜底保证「发送失败」一定有显式反馈
    // （ux.fail 自带去重，不会与 request 层重复弹两次）
    fail("验证码发送失败，请稍后重试");
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
  // 防抖清洗定时器同样要清，避免页面销毁后回调写已卸载组件的状态
  if (phoneSanitizeTimer) clearTimeout(phoneSanitizeTimer);
  if (codeSanitizeTimer) clearTimeout(codeSanitizeTimer);
});

// 跳转用户协议 / 隐私政策落地页
function openAgreement(type: 'user' | 'privacy') {
  sheetType.value = type;
  readProgress.value = 0;
  readComplete.value = false;
  sheetVisible.value = true;
  measureSheetScroll();
}

// ===== 协议半屏弹窗（登录前阅读；探探式） =====
const sheetVisible = ref(false);
const sheetType = ref<"user" | "privacy">("user");
const sheetContent = computed<AgreementContent>(
  () => AGREEMENT_CONTENT[sheetType.value] || AGREEMENT_CONTENT.user
);
/** 阅读进度（0-100） */
const readProgress = ref(0);
/** 是否已滚动到底部（决定「同意并继续」是否点亮） */
const readComplete = ref(false);
/** scroll-view 视口高度（px）：滚动进度分母 = scrollHeight - clientHeight */
let sheetClientHeight = 0;

function measureSheetScroll(): void {
  nextTick(() => {
    try {
      uni
        .createSelectorQuery()
        .select(".sheet-scroll")
        .boundingClientRect((rect: any) => {
          sheetClientHeight = rect?.height || 0;
        })
        .exec();
    } catch {
      // 非 H5 端取不到视口高度时，进度条仍可用 scrolltolower 点亮同意
    }
  });
}

function onSheetScroll(e: any): void {
  const detail = e?.detail || {};
  const maxScroll = Math.max(1, (detail.scrollHeight || 0) - sheetClientHeight);
  readProgress.value = Math.min(100, Math.round(((detail.scrollTop || 0) / maxScroll) * 100));
}

function onSheetScrollToLower(): void {
  readComplete.value = true;
  readProgress.value = 100;
}

/** 弹窗内「同意并继续」：自动勾选协议复选框并关闭（闭环） */
function agreeFromSheet(): void {
  agree.value = true;
  sheetVisible.value = false;
  success("已同意协议");
}

/** 弹窗内「不同意」：不勾选，仅关闭 */
function disagreeFromSheet(): void {
  sheetVisible.value = false;
}

/** 查看全文：跳转整页协议（auth-guard 白名单已放行） */
function goFullAgreement(): void {
  uni.navigateTo({ url: `/pages/mine/agreement?type=${sheetType.value}` });
}

// 登录
async function handleLogin() {
  forceSanitize();
  validatePhone();
  validateCode();
  if (!phoneValid.value) {
    info(errors.phone || "请输入正确的手机号");
    return;
  }
  if (code.value.length < MIN_CODE_LENGTH) {
    info(errors.code || "请输入验证码");
    return;
  }
  // 协议未勾选：明确告知而非静默无响应
  if (!agree.value) {
    info("请先阅读并同意《用户协议》和《隐私政策》");
    return;
  }
  logging.value = true;
  try {
    await userStore.doLogin(phone.value, code.value);
    success("登录成功");
    // 优先回跳登录前页面（S7）：tab 页 switchTab（不能带查询参数），非 tab 页 reLaunch 保留参数
    if (redirectTarget) {
      if (isTabPage(redirectTarget)) {
        uni.switchTab({ url: redirectTarget.split("?")[0] });
      } else {
        uni.reLaunch({ url: redirectTarget });
      }
    } else {
      uni.switchTab({ url: "/pages/match/index" });
    }
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
  /* 渐变只用 --color-primary（真源主色）：--color-primary-deep 属历史紫，
     §7.1 已禁止新代码使用，登录页大渐变背景是最高频命中点 */
  background: linear-gradient(180deg, var(--color-primary) 0%, var(--color-primary) 30%, var(--bg-page) 60%);
  padding: 0 60rpx;
  display: flex;
  flex-direction: column;
  position: relative;
  overflow: hidden;
}

/* 漂浮装饰心形：每颗独立轨迹（浮动 + 摆动 + 缩放 + 呼吸），节奏错开避免同步 */
.deco-heart {
  position: absolute;
  color: rgba(255, 255, 255, 0.14);
  line-height: 1;
  pointer-events: none;
  display: flex;
  align-items: center;
  justify-content: center;
  will-change: transform, opacity;

  &::after {
    content: '\2665';
  }

  &.deco-1 {
    font-size: 320rpx;
    top: 40rpx;
    right: -100rpx;
    filter: blur(2rpx);
    animation: heartFloatA 2.2s ease-in-out infinite;
  }

  &.deco-2 {
    font-size: 200rpx;
    top: 280rpx;
    left: -60rpx;
    animation: heartFloatB 2.6s ease-in-out infinite;
    animation-delay: 0.4s;
  }

  &.deco-3 {
    font-size: 120rpx;
    top: 140rpx;
    left: 180rpx;
    animation: heartFloatC 1.8s ease-in-out infinite;
    animation-delay: 0.8s;
  }

  &.deco-4 {
    font-size: 72rpx;
    top: 560rpx;
    right: 90rpx;
    opacity: 0.6;
    animation: heartFloatC 2s ease-in-out infinite;
    animation-delay: 1.2s;
  }
}

@keyframes heartFloatA {
  0%,
  100% {
    transform: translate(0, 0) rotate(0deg) scale(1);
    opacity: 0.9;
  }
  50% {
    transform: translate(20rpx, -40rpx) rotate(10deg) scale(1.1);
    opacity: 0.5;
  }
}

@keyframes heartFloatB {
  0%,
  100% {
    transform: translate(0, 0) rotate(0deg) scale(1);
    opacity: 0.85;
  }
  50% {
    transform: translate(-22rpx, -32rpx) rotate(-9deg) scale(1.14);
    opacity: 0.45;
  }
}

@keyframes heartFloatC {
  0%,
  100% {
    transform: translate(0, 0) rotate(0deg) scale(1);
    opacity: 0.9;
  }
  50% {
    transform: translate(14rpx, -26rpx) rotate(12deg) scale(1.08);
    opacity: 0.55;
  }
}

/* 减弱动效偏好下停用所有装饰动画（无障碍基线） */
@media (prefers-reduced-motion: reduce) {
  .deco-heart,
  .brand,
  .form,
  .logo {
    animation: none !important;
  }
}

.brand {
  display: flex;
  flex-direction: column;
  align-items: center;
  /* padding-top 由 :style 动态设置（statusBarHeight + 90px），避让刘海/挖孔 */
  padding-bottom: 80rpx;

  .logo {
    width: 160rpx;
    height: 160rpx;
    border-radius: 32rpx;
    animation: logoBeat 2.4s ease-in-out 1s infinite;
  }

  .app-name {
    font-size: 48rpx;
    font-weight: bold;
    color: var(--text-inverse);
    margin-top: 24rpx;
  }

  .slogan {
    font-size: 26rpx;
    /* 走 --text-inverse：写死 rgba(255,255,255,.85) 在暗色模式下与深底对比度不足 */
    color: var(--text-inverse);
    opacity: 0.85;
    margin-top: 12rpx;
  }
}

.form {
  background: var(--bg-card);
  border-radius: 32rpx;
  padding: 48rpx 40rpx;
  box-shadow: 0 8rpx 40rpx rgba(0, 0, 0, 0.06);
}

@keyframes logoBeat {
  0%,
  100% {
    transform: scale(1);
  }
  12% {
    transform: scale(1.08);
  }
  24% {
    transform: scale(1);
  }
  36% {
    transform: scale(1.06);
  }
  48% {
    transform: scale(1);
  }
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
      box-shadow: 0 0 0 4rpx rgba(var(--color-primary-rgb), 0.12);
      background: var(--bg-card);
    }

    /* 错误态：危险色描边 + 浅红底（token 化，适配暗色） */
    &.input--error {
      border-color: var(--color-danger);
      background: var(--color-danger-tint, var(--bg-sub));

      &:focus {
        border-color: var(--color-danger);
        box-shadow: 0 0 0 4rpx rgba(var(--color-danger-rgb, 229, 72, 77), 0.12);
      }
    }
  }

  .placeholder {
    color: var(--text-hint);
  }

  /* 字段级内联错误：位置紧邻输入框，色走 --color-danger（token 化） */
  .field-error {
    display: block;
    margin-top: 8rpx;
    font-size: 22rpx;
    line-height: 1.4;
    color: var(--color-danger);
  }
}

.code-row {
  display: flex;
  align-items: center;
  gap: 20rpx;

  .code-input {
    flex: 1;
  }

  /* 视觉样式（描边/字号/禁用态）由 a11y-button variant=ghost 统一提供，此处只管布局宽度 */
  .code-btn {
    width: 220rpx;
    flex-shrink: 0;
  }
}

.agreement {
  display: flex;
  align-items: center;
  /* 可点击区域比视觉区域大：min-height 88rpx 保证最小热区，padding 扩展点击范围 */
  min-height: 88rpx;
  padding: 16rpx 0;
  margin: 32rpx 0;
  box-sizing: content-box;

  .checkbox {
    width: 36rpx;
    height: 36rpx;
    border: 2rpx solid var(--border);
    border-radius: 50%;
    margin-right: 12rpx;
    display: flex;
    align-items: center;
    justify-content: center;
    flex-shrink: 0;

    &.checked {
      background: var(--color-primary);
      border-color: var(--color-primary);
    }

    .check-icon {
      color: var(--text-inverse);
      font-size: 24rpx;
    }
  }

  .agreement-text {
    font-size: 24rpx;
    color: var(--text-hint);
    line-height: 1.5;
    flex: 1;
    min-width: 0;

    .link {
      color: var(--color-primary-text);
    }
  }
}

/* ===== 协议半屏弹窗（a11y-dialog 壳 + 探探式阅读流） ===== */
.sheet {
  position: fixed;
  inset: 0;
  z-index: 1000;
  display: flex;
  align-items: flex-end;
  background: rgba(0, 0, 0, 0.5);
}

.sheet-panel {
  display: flex;
  flex-direction: column;
  width: 100%;
  /* 定高而非 max-height：正文区 flex 伸缩吸收空间，footer 永远贴底可见
     （max-height + 固定高 scroll-view 会把 footer 挤出面板外） */
  height: 72vh;
  background: var(--bg-card);
  border-radius: 32rpx 32rpx 0 0;
  padding: 32rpx 32rpx calc(env(safe-area-inset-bottom, 0px) + 24rpx);
  box-sizing: border-box;
}

/* 弹窗抓手：bottom sheet 的标准可供性暗示 */
.sheet-grabber {
  width: 72rpx;
  height: 8rpx;
  border-radius: var(--radius-pill);
  background: var(--border);
  margin: 0 auto 20rpx;
}

.sheet-header {
  display: flex;
  align-items: center;
  justify-content: space-between;

  .sheet-title {
    font-size: 32rpx;
    font-weight: bold;
    color: var(--text-primary);
  }

  .sheet-full {
    font-size: 26rpx;
    color: var(--color-primary-text);
    min-height: 88rpx;
    display: flex;
    align-items: center;
  }
}

/* 进度行：进度条 + 右侧百分比反馈，让「读到哪了」一目了然 */
.sheet-progress-row {
  display: flex;
  align-items: center;
  gap: 16rpx;
  margin-top: 8rpx;
}

.sheet-progress {
  flex: 1;
  height: 6rpx;
  border-radius: var(--radius-pill);
  background: var(--border-light);
  overflow: hidden;

  .sheet-progress-fill {
    height: 100%;
    border-radius: var(--radius-pill);
    background: var(--color-primary);
    transition: width 0.15s ease-out;
  }
}

.sheet-progress-text {
  flex-shrink: 0;
  font-size: 22rpx;
  color: var(--text-hint);
  font-variant-numeric: tabular-nums;
  min-width: 88rpx;
  text-align: right;
}

.sheet-scroll {
  flex: 1;
  min-height: 0;
  margin-top: 20rpx;

  .sheet-paragraph {
    display: block;
    font-size: 26rpx;
    line-height: 1.8;
    color: var(--text-secondary);
    margin-bottom: 16rpx;
    text-align: justify;
  }
}

.sheet-footer {
  margin-top: 24rpx;

  /* 「不同意」降级为居中细文字链接：主行动只有一个，不再与主按钮等权并排 */
  .sheet-disagree {
    display: flex;
    justify-content: center;
    margin-top: 8rpx;
    min-height: 88rpx;
    box-sizing: border-box;
    font-size: 24rpx;
  }
}

</style>
