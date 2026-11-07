<template>
  <view class="guide-page">
    <!-- 进度指示（role=progressbar：读屏可获知完整度而非仅视觉宽度） -->
    <view
      class="progress-bar"
      role="progressbar"
      aria-label="资料完整度"
      :aria-valuenow="completeness"
      aria-valuemin="0"
      aria-valuemax="100"
    >
      <view
        class="progress-fill"
        :style="{ width: completeness + '%' }"
      />
      <text class="progress-text">
        资料完整度 {{ completeness }}%
      </text>
    </view>

    <!-- 头像 -->
    <view class="section">
      <text class="section-title">
        头像
      </text>
      <a11y-button
        class="avatar-picker"
        label="更换头像"
        @tap="chooseAvatar"
      >
        <smart-image
          lazy-load
          class="avatar"
          :src="form.avatar"
          mode="aspectFill"
          :aria-hidden="true"
        />
        <text class="avatar-hint">
          点击更换
        </text>
      </a11y-button>
    </view>

    <!-- 昵称 -->
    <view class="section">
      <text
        :id="nicknameLabelId"
        class="section-title"
      >
        昵称
      </text>
      <input
        v-model="form.nickname"
        class="input"
        placeholder="请输入昵称"
        aria-label="昵称"
        :aria-labelledby="nicknameLabelId"
        maxlength="20"
      >
    </view>

    <!-- 性别（语义单选组） -->
    <view class="section">
      <text class="section-title">
        性别
      </text>
      <a11y-radio-group
        class="gender-row"
        label="性别"
        :options="GENDER_OPTIONS"
        :model-value="form.gender"
        option-class="gender-btn"
        @update:model-value="form.gender = $event"
      >
        <template #option="{ option }">
          <text>{{ option.label }}</text>
        </template>
      </a11y-radio-group>
    </view>

    <!-- 生日 -->
    <view class="section">
      <text class="section-title">
        生日
      </text>
      <picker
        mode="date"
        :value="form.birthday"
        @change="onBirthdayChange"
      >
        <view class="picker-value">
          <text :class="{ placeholder: !form.birthday }">
            {{ form.birthday || '请选择生日' }}
          </text>
        </view>
      </picker>
    </view>

    <!-- 城市 -->
    <view class="section">
      <text
        :id="cityLabelId"
        class="section-title"
      >
        城市
      </text>
      <input
        v-model="form.city"
        class="input"
        placeholder="如：北京"
        aria-label="城市"
        :aria-labelledby="cityLabelId"
        maxlength="20"
      >
    </view>

    <!-- 学历 -->
    <view class="section">
      <text class="section-title">
        学历
      </text>
      <picker
        :range="EDUCATION_OPTIONS"
        @change="onEducationChange"
      >
        <view class="picker-value">
          <text :class="{ placeholder: !form.education }">
            {{ form.education || '请选择学历' }}
          </text>
        </view>
      </picker>
    </view>

    <!-- 提交 -->
    <a11y-button
      class="submit-btn"
      variant="primary"
      block
      :label="submitting ? '保存中' : '完成'"
      :disabled="submitting"
      @tap="handleSubmit"
    >
      {{ submitting ? '保存中…' : '完成' }}
    </a11y-button>
    <a11y-button
      class="skip-btn"
      label="跳过，稍后完善资料"
      @tap="handleSkip"
    >
      跳过，稍后完善
    </a11y-button>
  </view>
</template>

<script setup lang="ts">
import { ref, computed } from "vue";
import { onUnload, onHide } from "@dcloudio/uni-app";
import A11yButton from "@/components/a11y-button.vue";
import SmartImage from "@/components/smart-image.vue";
import A11yRadioGroup from "@/components/a11y-radio-group.vue";
import { updateUserProfile, uploadImage } from "@/api/mine";
import { success, info, fail, loading, hideLoading } from "@/utils/ux";

/** 表单字段唯一 id：用于 aria-labelledby 建立程序化标签关联（WCAG 1.3.1 / 3.3.2） */
const nicknameLabelId = "guide-nickname-label";
const cityLabelId = "guide-city-label";

/** 学历选项（与后端枚举一致） */
const EDUCATION_OPTIONS = ["本科", "硕士", "双硕", "博士", "双博"];

/** 性别选项（value 与后端一致；label 供读屏朗读） */
const GENDER_OPTIONS = [
  { value: "man", label: "男" },
  { value: "woman", label: "女" },
];

/** 表单数据 */
const form = ref({
  avatar: "",
  nickname: "",
  gender: "",
  birthday: "",
  city: "",
  education: "",
});

const submitting = ref(false);

/** 资料完整度：已填字段数 / 总字段数 */
const completeness = computed(() => {
  const fields = [form.value.avatar, form.value.nickname, form.value.gender, form.value.birthday, form.value.city, form.value.education];
  const filledCount = fields.filter((field) => field).length;
  return Math.round((filledCount / fields.length) * 100);
});

/** 选择头像：选图后立即上传 MinIO，成功存 MinIO URL（随提交一并保存后端）。
 *  原实现只存 tempFilePath 从不上传，payload 也不含 avatar，完整度虚标 100%（M-A4）。 */
function chooseAvatar(): void {
  uni.chooseImage({
    count: 1,
    sizeType: ["compressed"],
    success: async (res) => {
      const tempPath = res.tempFilePaths[0];
      if (!tempPath) return;
      loading("上传中…");
      try {
        const result = await uploadImage(tempPath);
        hideLoading();
        if (result.status === "done" && result.name) {
          form.value.avatar = result.name;
          success("头像已上传，完成后生效");
        } else {
          fail("头像上传失败，请重试");
        }
      } catch {
        hideLoading();
        fail("头像上传失败，请重试");
      }
    },
    fail: (err: any) => {
      // 区分用户取消（静默）与授权拒绝（提示 + MP 端引导打开设置页）
      const msg = String(err?.errMsg || "");
      if (msg.includes("cancel")) return;
      info("需要相册权限才能更换头像");
      const openSetting = (uni as any).openSetting;
      if (typeof openSetting === "function") {
        openSetting({});
      }
    },
  });
}

/** 生日选择 */
function onBirthdayChange(e: any): void {
  form.value.birthday = e.detail.value;
}

/** 学历选择 */
function onEducationChange(e: any): void {
  form.value.education = EDUCATION_OPTIONS[e.detail.value];
}

/** 延迟导航定时器：离页（卸载/压栈隐藏）即清理，防止保存成功后用户手动返回仍被自动 switchTab 劫持 */
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

/** 提交资料 */
async function handleSubmit(): Promise<void> {
  if (!form.value.nickname) {
    info("请输入昵称");
    return;
  }
  if (!form.value.gender) {
    info("请选择性别");
    return;
  }

  submitting.value = true;
  try {
    await updateUserProfile({
      avatar: form.value.avatar || undefined,
      nickname: form.value.nickname,
      gender: form.value.gender,
      birthday: form.value.birthday || undefined,
      city: form.value.city || undefined,
      education: form.value.education || undefined,
    });
    success("保存成功");
    scheduleNav(1000, () => {
      uni.switchTab({ url: "/pages/match/index" });
    });
  } catch {
    // 错误提示由 request 封装统一处理
  } finally {
    submitting.value = false;
  }
}

/** 跳过引导 */
function handleSkip(): void {
  uni.switchTab({ url: "/pages/match/index" });
}
</script>

<style lang="scss" scoped>
.guide-page {
  min-height: 100vh;
  background: var(--bg-sub);
  padding: 32rpx;
}

.progress-bar {
  height: 8rpx;
  background: var(--border);
  border-radius: 4rpx;
  margin-bottom: 16rpx;
  position: relative;

  .progress-fill {
    height: 100%;
    background: var(--gradient-brand-h);
    border-radius: 4rpx;
    transition: width 0.3s ease;
  }

  .progress-text {
    position: absolute;
    right: 0;
    top: 16rpx;
    font-size: 24rpx;
    color: var(--color-primary);
    white-space: nowrap;
  }
}

.section {
  background: var(--bg-card);
  border-radius: 24rpx;
  padding: 28rpx 32rpx;
  margin-bottom: 24rpx;
  box-shadow: 0 2rpx 12rpx rgba(0, 0, 0, 0.04);

  .section-title {
    font-size: 28rpx;
    /* #999 → #767676 */
    color: var(--text-hint);
    display: block;
    margin-bottom: 16rpx;
  }

  .input {
    /* 热区 ≥88rpx（44px）：原无高度声明，实测仅 37px 难以点中 */
    min-height: 88rpx;
    font-size: 32rpx;
    color: var(--text-primary);
  }

  .picker-value {
    font-size: 32rpx;
    color: var(--text-primary);
    padding: 8rpx 0;

    .placeholder {
      color: var(--text-hint);
    }
  }
}

.avatar-picker {
  display: flex;
  align-items: center;
  gap: 24rpx;

  .avatar {
    width: 120rpx;
    height: 120rpx;
    border-radius: 50%;
    background: var(--bg-sub);
  }

  .avatar-hint {
    font-size: 26rpx;
    color: var(--color-primary);
  }
}

.gender-row {
  display: flex;
  gap: 24rpx;

  /* 选项节点由 a11y-radio-group 内部渲染，页面 scoped 样式需 :deep 穿透才能命中 */
  :deep(.gender-btn) {
    flex: 1;
    /* 热区 ≥88rpx（44px）：原 80rpx 仅 40px，窄屏会等比缩到 33px */
    min-height: 88rpx;
    display: flex;
    align-items: center;
    justify-content: center;
    border-radius: 16rpx;
    background: var(--bg-sub);
    font-size: 30rpx;
    color: var(--text-secondary);
    border: 2rpx solid transparent;
    transition: background 0.2s, color 0.2s, border-color 0.2s, box-shadow 0.2s;

    &.active {
      background: var(--gradient-brand);
      color: var(--text-inverse);
      border-color: var(--color-primary);
      box-shadow: 0 4rpx 14rpx rgba(var(--color-primary-rgb), 0.3);
    }
  }
}

/* primary variant 提供底色/热区/按压反馈/字体 */
.submit-btn {
  margin-top: 48rpx;

  &[disabled] {
    opacity: 0.6;
  }
}

/* 跳过：#999 → #767676；热区 ≥88rpx（44px），原 min-height 48rpx 仅 24px */
.skip-btn {
  display: flex;
  align-items: center;
  justify-content: center;
  text-align: center;
  margin-top: 24rpx;
  font-size: 28rpx;
  color: var(--text-hint);
  min-height: 88rpx;
  padding: 0;
}
</style>
