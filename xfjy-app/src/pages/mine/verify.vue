<template>
  <view class="verify-page">
    <!-- 已认证 -->
    <view
      v-if="verified"
      class="verified-card"
    >
      <text
        class="verified-icon"
        aria-hidden="true"
      >
        ✓
      </text>
      <text class="verified-text">
        已完成实名认证
      </text>
    </view>
    <!-- 未认证 -->
    <template v-else>
      <view class="tips">
        实名信息仅用于身份核验，不会公开展示
      </view>
      <view class="form-card">
        <view class="form-item">
          <text
            :id="realNameLabelId"
            class="form-label"
          >
            真实姓名
          </text>
          <input
            v-model="realName"
            maxlength="20"
            placeholder="请输入真实姓名"
            placeholder-class="ph"
            aria-label="真实姓名"
            :aria-labelledby="realNameLabelId"
          >
        </view>
        <view class="form-item">
          <text
            :id="idCardLabelId"
            class="form-label"
          >
            身份证号
          </text>
          <input
            v-model="idCard"
            maxlength="18"
            placeholder="18位身份证号"
            placeholder-class="ph"
            aria-label="身份证号"
            :aria-labelledby="idCardLabelId"
          >
        </view>
      </view>
      <a11y-button
        class="submit-btn"
        variant="primary"
        block
        label="提交实名认证"
        :loading="submitting"
        :disabled="submitting"
        @tap="submit"
      >
        <text>提交认证</text>
      </a11y-button>
    </template>
  </view>
</template>

<script setup lang="ts">
import { ref } from "vue";
import { onShow } from "@dcloudio/uni-app";
import A11yButton from "@/components/a11y-button.vue";
import { submitVerify, getVerifyStatus } from "@/api/mine";
import { info, success } from "@/utils/ux";

/** 表单字段唯一 id：用于 aria-labelledby 建立程序化标签关联（WCAG 1.3.1 / 3.3.2） */
const realNameLabelId = "verify-realname-label";
const idCardLabelId = "verify-idcard-label";

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
    info('请输入真实姓名');
    return;
  }
  // 对齐后端 2-20 位限制，避免过前端后收到通用 400 文案
  if (name.length > 20) {
    info('姓名不超过 20 个字符');
    return;
  }
  if (!/^\d{17}[\dXx]$/.test(card)) {
    info('身份证号格式不正确');
    return;
  }
  submitting.value = true;
  try {
    await submitVerify(name, card);
    success('认证成功');
    verified.value = true;
  } catch {
    // request 层统一 toast
  } finally {
    submitting.value = false;
  }
}
</script>

<style lang="scss" scoped>
/* 原生导航页：min-height 需减去导航栏，否则内容不足时底部仍溢出 44px */
.verify-page { min-height: calc(100vh - var(--window-top, 0px)); background: var(--bg-sub); padding: 24rpx; }
.tips { font-size: 24rpx; color: var(--text-hint); line-height: 1.6; margin-bottom: 24rpx; }
.form-card { background: var(--bg-card); border-radius: 24rpx; padding: 8rpx 32rpx; box-shadow: 0 2rpx 12rpx rgba(0, 0, 0, 0.04);
  .form-item { display: flex; align-items: center; padding: 26rpx 0; border-bottom: 1rpx solid var(--border-faint);
    &:last-child { border-bottom: none; }
    .form-label { width: 160rpx; font-size: 28rpx; color: var(--text-primary); flex-shrink: 0; }
    /* 热区 ≥88rpx（44px）：原仅由 line-height 撑高约 38.8rpx，点中困难 */
    input { flex: 1; min-height: 88rpx; font-size: 28rpx; color: var(--text-primary); }
  }
}
.ph { color: var(--text-hint); }
/* 提交按钮：a11y-button primary 变体（品牌渐变），仅补外边距与宽度 */
.submit-btn { margin-top: 60rpx; }
.verified-card { background: var(--bg-card); border-radius: 24rpx; padding: 80rpx 32rpx; text-align: center; box-shadow: 0 2rpx 12rpx rgba(0, 0, 0, 0.04);
  .verified-icon { display: block; width: 96rpx; height: 96rpx; line-height: 96rpx; margin: 0 auto 24rpx; border-radius: 50%; background: var(--color-primary); color: var(--bg-card); font-size: 48rpx; }
  .verified-text { font-size: 30rpx; color: var(--text-primary); }
}
</style>
