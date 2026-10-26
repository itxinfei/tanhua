<template>
  <view class="profile-page">
    <!-- 头像 -->
    <view class="avatar-row" @click="changeAvatar">
      <text class="label">头像</text>
      <view class="avatar-wrap">
        <image class="avatar" :src="form.avatar || '/static/logo/logo-icon-transparent.png'" mode="aspectFill" />
        <view class="avatar-mask"><text>更换</text></view>
      </view>
      <text class="arrow">›</text>
    </view>

    <!-- 照片墙 -->
    <view class="section">
      <text class="section-title">照片墙 ({{ form.photoAlbum.length }}/{{ MAX_ALBUM_COUNT }})</text>
      <view class="album-grid">
        <view v-for="(img, i) in form.photoAlbum" :key="i" class="album-item">
          <image class="album-img" :src="img" mode="aspectFill" />
          <view class="remove btn-feedback" @click="removePhoto(i)"><text>✕</text></view>
        </view>
        <view v-if="form.photoAlbum.length < MAX_ALBUM_COUNT" class="add-photo btn-feedback" @click="addPhoto">
          <text class="plus">+</text>
          <text class="add-label">{{ form.photoAlbum.length }}/{{ MAX_ALBUM_COUNT }}</text>
        </view>
      </view>
    </view>

    <!-- 基本资料 -->
    <view class="section">
      <view class="form-item" @click="editField('nickname', '昵称')">
        <text class="label">昵称</text><text class="value" :class="{ unset: !form.nickname }">{{ form.nickname || '未设置' }}</text><text class="arrow">›</text>
      </view>
      <view class="form-item" @click="editField('signature', '个性签名')">
        <text class="label">签名</text><text class="value ellipsis" :class="{ unset: !form.signature }">{{ form.signature || '未设置' }}</text><text class="arrow">›</text>
      </view>
      <!-- 性别：自定义色块选择器（替代原生 radio） -->
      <view class="form-item">
        <text class="label">性别</text>
        <view class="gender-picker">
          <view v-for="opt in GENDER_OPTIONS" :key="opt.value" class="gender-opt btn-feedback" :class="{ active: form.gender === opt.value, [opt.cls]: true }" @click="form.gender = opt.value">
            <text>{{ opt.icon }}</text>
            <text class="gender-label">{{ opt.label }}</text>
          </view>
        </view>
      </view>
      <view class="form-item" @click="editField('city', '城市')">
        <text class="label">城市</text><text class="value" :class="{ unset: !form.city }">{{ form.city || '未设置' }}</text><text class="arrow">›</text>
      </view>
      <view class="form-item" @click="editField('height', '身高(cm)')">
        <text class="label">身高</text><text class="value" :class="{ unset: !form.height }">{{ form.height || '未设置' }}</text><text class="arrow">›</text>
      </view>
      <view class="form-item" @click="editField('education', '学历')">
        <text class="label">学历</text><text class="value" :class="{ unset: !form.education }">{{ form.education || '未设置' }}</text><text class="arrow">›</text>
      </view>
      <view class="form-item" @click="editField('industry', '行业')">
        <text class="label">行业</text><text class="value" :class="{ unset: !form.industry }">{{ form.industry || '未设置' }}</text><text class="arrow">›</text>
      </view>
    </view>

    <!-- 保存 -->
    <button class="save-btn btn-gradient safe-area-bottom" :disabled="submitting" @click="handleSave">保存</button>
  </view>
</template>

<script setup lang="ts">
import { ref } from "vue";
import { onShow } from "@dcloudio/uni-app";
import { useUserStore } from "@/store/user";
import { getUserProfile, updateUserProfile, uploadImage } from "@/api/mine";

/** 照片墙最大数量 */
const MAX_ALBUM_COUNT = 6;

/** 性别选项配置（表驱动，替代原生 radio） */
const GENDER_OPTIONS = [
  { value: "男", label: "男生", icon: "👨", cls: "male" },
  { value: "女", label: "女生", icon: "👩", cls: "female" },
] as const;

const userStore = useUserStore();
const form = ref({
  avatar: "", nickname: "", signature: "", gender: "男",
  city: "", height: "", education: "", industry: "",
  photoAlbum: [] as string[],
});
const submitting = ref(false);

/** 更换头像：选图后上传 OSS，成功写入 OSS URL（随保存提交后端） */
function changeAvatar(): void {
  uni.chooseImage({ count: 1, sizeType: ["compressed"], success: async (res) => {
    const tempPath = res.tempFilePaths[0];
    uni.showLoading({ title: "上传中...", mask: true });
    try {
      const result = await uploadImage(tempPath);
      uni.hideLoading();
      if (result.status === "done" && result.name) {
        form.value.avatar = result.name;
        uni.showToast({ title: "头像已更新", icon: "success" });
      }
    } catch (e) {
      uni.hideLoading();
      // upload 工具已统一 toast 错误提示
    }
  }});
}

/** 添加照片墙图片 */
function addPhoto(): void {
  uni.chooseImage({ count: MAX_ALBUM_COUNT - form.value.photoAlbum.length, sizeType: ["compressed"], success: (res) => {
    form.value.photoAlbum.push(...res.tempFilePaths);
  }});
}

/** 删除照片墙图片 */
function removePhoto(i: number): void {
  form.value.photoAlbum.splice(i, 1);
}

/** 弹窗编辑文本字段 */
function editField(field: string, label: string): void {
  uni.showModal({
    title: `修改${label}`,
    editable: true,
    placeholderText: `请输入${label}`,
    content: (form.value as any)[field] || "",
    success: (res) => {
      if (res.confirm && res.content) (form.value as any)[field] = res.content;
    },
  });
}

/** 保存资料：可映射字段提交后端，signature/height/照片墙暂仅本地（后端无对应字段） */
async function handleSave(): Promise<void> {
  if (submitting.value) return;
  submitting.value = true;
  try {
    await updateUserProfile({
      avatar: form.value.avatar,
      nickname: form.value.nickname,
      gender: form.value.gender,
      city: form.value.city,
      education: form.value.education,
      profession: form.value.industry,
    });
    userStore.updateUserInfo(form.value);
    uni.showToast({ title: "保存成功", icon: "success" });
    setTimeout(() => uni.navigateBack(), 800);
  } catch {
    // request 层统一 toast
  } finally {
    submitting.value = false;
  }
}

onShow(async () => {
  if (userStore.userInfo) {
    Object.assign(form.value, userStore.userInfo);
    return;
  }
  // store 为空时从后端拉取
  try {
    const profile = await getUserProfile();
    if (profile) {
      form.value.avatar = profile.avatar || form.value.avatar;
      form.value.nickname = profile.nickname || form.value.nickname;
      form.value.gender = profile.gender || form.value.gender;
      form.value.city = profile.city || form.value.city;
      form.value.education = profile.education || form.value.education;
      form.value.industry = profile.profession || form.value.industry;
    }
  } catch {
    // request 层统一 toast
  }
});
</script>

<style lang="scss" scoped>
.profile-page { min-height: 100vh; background: #f5f5f5; padding-bottom: 180rpx; }

/* ===== 头像行 ===== */
.avatar-row { display: flex; align-items: center; padding: 32rpx; background: #fff;
  .label { flex: 1; font-size: 28rpx; color: #333; font-weight: 500; }
  .avatar-wrap { position: relative; width: 120rpx; height: 120rpx;
    .avatar { width: 100%; height: 100%; border-radius: 50%; border: 4rpx solid #fff0f5; }
    .avatar-mask { position: absolute; bottom: 0; left: 0; right: 0; height: 40rpx; background: rgba(0, 0, 0, 0.45); border-radius: 0 0 60rpx 60rpx; display: flex; align-items: center; justify-content: center;
      text { font-size: 18rpx; color: #fff; }
    }
  }
  .arrow { font-size: 36rpx; color: #ccc; margin-left: 16rpx; }
}

/* ===== 分组 ===== */
.section { background: #fff; margin-top: 16rpx; padding: 28rpx 32rpx;
  .section-title { font-size: 26rpx; color: #999; display: block; margin-bottom: 20rpx; }
}

/* ===== 照片墙 ===== */
.album-grid { display: flex; flex-wrap: wrap; gap: 16rpx;
  .album-item { position: relative; width: 180rpx; height: 180rpx; border-radius: 16rpx; overflow: visible;
    .album-img { width: 100%; height: 100%; border-radius: 16rpx; border: 2rpx solid #f0f0f0; }
    .remove { position: absolute; top: -10rpx; right: -10rpx; width: 40rpx; height: 40rpx; background: rgba(0, 0, 0, 0.55); color: #fff; border-radius: 50%; display: flex; align-items: center; justify-content: center; font-size: 22rpx; }
  }
  .add-photo { width: 180rpx; height: 180rpx; background: #fafafa; border-radius: 16rpx; display: flex; flex-direction: column; align-items: center; justify-content: center; gap: 8rpx; border: 2rpx dashed #ddd;
    .plus { font-size: 56rpx; color: #ccc; line-height: 1; }
    .add-label { font-size: 22rpx; color: #ccc; }
  }
}

/* ===== 表单项 ===== */
.form-item { display: flex; align-items: center; padding: 24rpx 0; border-bottom: 1rpx solid #f5f5f5;
  &:last-child { border-bottom: none; }
  .label { width: 140rpx; font-size: 28rpx; color: #666; }
  .value { flex: 1; font-size: 28rpx; color: #333; text-align: right;
    &.unset { color: #ccc; }
  }
  .arrow { font-size: 32rpx; color: #ccc; margin-left: 8rpx; }
}

/* 性别色块选择器 */
.gender-picker { flex: 1; display: flex; gap: 20rpx; justify-content: flex-end; }
.gender-opt { display: flex; align-items: center; gap: 8rpx; padding: 12rpx 28rpx; border-radius: 32rpx; font-size: 26rpx; border: 2rpx solid #eee; transition: all 0.2s;
  .gender-label { color: #666; }
  &.male.active { background: linear-gradient(135deg, #4a90d9, #6bb0f5); border-color: #4a90d9; box-shadow: 0 4rpx 14rpx rgba(74, 144, 217, 0.3);
    .gender-label { color: #fff; }
  }
  &.female.active { background: linear-gradient(135deg, #ff5e8e, #ff8fab); border-color: #ff5e8e; box-shadow: 0 4rpx 14rpx rgba(255, 94, 142, 0.3);
    .gender-label { color: #fff; }
  }
}

/* ===== 保存按钮 ===== */
.save-btn { position: fixed; left: 32rpx; right: 32rpx; bottom: 40rpx; height: 88rpx; line-height: 88rpx; color: #fff; font-size: 32rpx; font-weight: bold; border-radius: 44rpx; border: none; }
</style>