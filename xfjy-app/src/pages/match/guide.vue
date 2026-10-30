<template>
  <view class="guide-page">
    <!-- 进度指示 -->
    <view class="progress-bar">
      <view class="progress-fill" :style="{ width: completeness + '%' }" />
      <text class="progress-text">资料完整度 {{ completeness }}%</text>
    </view>

    <!-- 头像 -->
    <view class="section">
      <text class="section-title">头像</text>
      <view class="avatar-picker" @click="chooseAvatar">
        <image
          class="avatar"
          :src="form.avatar || '/static/logo/logo-icon-transparent.png'"
          mode="aspectFill"
        />
        <text class="avatar-hint">点击更换</text>
      </view>
    </view>

    <!-- 昵称 -->
    <view class="section">
      <text class="section-title">昵称</text>
      <input
        class="input"
        v-model="form.nickname"
        placeholder="请输入昵称"
        maxlength="20"
      />
    </view>

    <!-- 性别 -->
    <view class="section">
      <text class="section-title">性别</text>
      <view class="gender-row">
        <view
          class="gender-btn"
          :class="{ active: form.gender === 'man' }"
          @click="form.gender = 'man'"
        >
          <text>男</text>
        </view>
        <view
          class="gender-btn"
          :class="{ active: form.gender === 'woman' }"
          @click="form.gender = 'woman'"
        >
          <text>女</text>
        </view>
      </view>
    </view>

    <!-- 生日 -->
    <view class="section">
      <text class="section-title">生日</text>
      <picker mode="date" :value="form.birthday" @change="onBirthdayChange">
        <view class="picker-value">
          <text :class="{ placeholder: !form.birthday }">
            {{ form.birthday || '请选择生日' }}
          </text>
        </view>
      </picker>
    </view>

    <!-- 城市 -->
    <view class="section">
      <text class="section-title">城市</text>
      <input
        class="input"
        v-model="form.city"
        placeholder="如：北京"
        maxlength="20"
      />
    </view>

    <!-- 学历 -->
    <view class="section">
      <text class="section-title">学历</text>
      <picker :range="EDUCATION_OPTIONS" @change="onEducationChange">
        <view class="picker-value">
          <text :class="{ placeholder: !form.education }">
            {{ form.education || '请选择学历' }}
          </text>
        </view>
      </picker>
    </view>

    <!-- 提交 -->
    <button class="submit-btn" :disabled="submitting" @click="handleSubmit">
      {{ submitting ? '保存中...' : '完成' }}
    </button>
    <text class="skip-btn" @click="handleSkip">跳过，稍后完善</text>
  </view>
</template>

<script setup lang="ts">
import { ref, computed } from "vue";
import { updateUserProfile } from "@/api/mine";

/** 学历选项（与后端枚举一致） */
const EDUCATION_OPTIONS = ["本科", "硕士", "双硕", "博士", "双博"];

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

/** 选择头像 */
function chooseAvatar(): void {
  uni.chooseImage({
    count: 1,
    success: (res) => {
      form.value.avatar = res.tempFilePaths[0];
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

/** 提交资料 */
async function handleSubmit(): Promise<void> {
  if (!form.value.nickname) {
    uni.showToast({ title: "请输入昵称", icon: "none" });
    return;
  }
  if (!form.value.gender) {
    uni.showToast({ title: "请选择性别", icon: "none" });
    return;
  }

  submitting.value = true;
  try {
    await updateUserProfile({
      nickname: form.value.nickname,
      gender: form.value.gender,
      birthday: form.value.birthday || undefined,
      city: form.value.city || undefined,
      education: form.value.education || undefined,
    });
    uni.showToast({ title: "保存成功", icon: "success" });
    setTimeout(() => {
      uni.switchTab({ url: "/pages/match/index" });
    }, 1000);
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
  background: #f5f5f5;
  padding: 32rpx;
}

.progress-bar {
  height: 8rpx;
  background: #e0e0e0;
  border-radius: 4rpx;
  margin-bottom: 16rpx;
  position: relative;

  .progress-fill {
    height: 100%;
    background: linear-gradient(90deg, #b13bff, #ff5e9c);
    border-radius: 4rpx;
    transition: width 0.3s ease;
  }

  .progress-text {
    position: absolute;
    right: 0;
    top: 16rpx;
    font-size: 24rpx;
    color: #ff5e9c;
    white-space: nowrap;
  }
}

.section {
  background: #fff;
  border-radius: 24rpx;
  padding: 28rpx 32rpx;
  margin-bottom: 24rpx;
  box-shadow: 0 2rpx 12rpx rgba(0, 0, 0, 0.04);

  .section-title {
    font-size: 28rpx;
    color: #999;
    display: block;
    margin-bottom: 16rpx;
  }

  .input {
    font-size: 32rpx;
    color: #333;
  }

  .picker-value {
    font-size: 32rpx;
    color: #333;
    padding: 8rpx 0;

    .placeholder {
      color: #ccc;
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
    background: #f0f0f0;
  }

  .avatar-hint {
    font-size: 26rpx;
    color: #ff5e9c;
  }
}

.gender-row {
  display: flex;
  gap: 24rpx;

  .gender-btn {
    flex: 1;
    height: 80rpx;
    display: flex;
    align-items: center;
    justify-content: center;
    border-radius: 16rpx;
    background: #f5f5f5;
    font-size: 30rpx;
    color: #666;
    border: 2rpx solid transparent;
    transition: all 0.2s;

    &.active {
      background: linear-gradient(135deg, #b13bff, #ff5e9c);
      color: #fff;
      border-color: #ff5e9c;
      box-shadow: 0 4rpx 14rpx rgba(255, 94, 142, 0.3);
    }
  }
}

.submit-btn {
  margin-top: 48rpx;
  background: linear-gradient(90deg, #b13bff, #ff5e9c);
  color: #fff;
  font-size: 32rpx;
  font-weight: bold;
  height: 88rpx;
  line-height: 88rpx;
  border-radius: 44rpx;
  border: none;
  box-shadow: 0 8rpx 24rpx rgba(255, 94, 142, 0.35);

  &[disabled] {
    opacity: 0.6;
  }
}

.skip-btn {
  display: block;
  text-align: center;
  margin-top: 24rpx;
  font-size: 28rpx;
  color: #999;
}
</style>