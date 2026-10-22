<template>
  <view class="profile-page">
    <!-- 头像 -->
    <view class="avatar-row" @click="changeAvatar">
      <text class="label">头像</text>
      <image class="avatar" :src="form.avatar || '/static/logo/logo-icon-transparent.png'" mode="aspectFill" />
      <text class="arrow">›</text>
    </view>

    <!-- 照片墙 -->
    <view class="section">
      <text class="section-title">照片墙 ({{ form.photoAlbum.length }}/6)</text>
      <view class="album-grid">
        <view v-for="(img, i) in form.photoAlbum" :key="i" class="album-item">
          <image class="album-img" :src="img" mode="aspectFill" />
          <view class="remove" @click="removePhoto(i)">✕</view>
        </view>
        <view v-if="form.photoAlbum.length < 6" class="add-photo" @click="addPhoto"><text>+</text></view>
      </view>
    </view>

    <!-- 基本资料 -->
    <view class="section">
      <view class="form-item" @click="editField('nickname', '昵称')">
        <text class="label">昵称</text><text class="value">{{ form.nickname || '未设置' }}</text><text class="arrow">›</text>
      </view>
      <view class="form-item" @click="editField('signature', '个性签名')">
        <text class="label">签名</text><text class="value ellipsis">{{ form.signature || '未设置' }}</text><text class="arrow">›</text>
      </view>
      <view class="form-item">
        <text class="label">性别</text>
        <radio-group @change="form.gender = $event.detail.value" class="radio-group">
          <label><radio value="男" :checked="form.gender === '男'" />男</label>
          <label><radio value="女" :checked="form.gender === '女'" />女</label>
        </radio-group>
      </view>
      <view class="form-item" @click="editField('city', '城市')">
        <text class="label">城市</text><text class="value">{{ form.city || '未设置' }}</text><text class="arrow">›</text>
      </view>
      <view class="form-item" @click="editField('height', '身高(cm)')">
        <text class="label">身高</text><text class="value">{{ form.height || '未设置' }}</text><text class="arrow">›</text>
      </view>
      <view class="form-item" @click="editField('education', '学历')">
        <text class="label">学历</text><text class="value">{{ form.education || '未设置' }}</text><text class="arrow">›</text>
      </view>
      <view class="form-item" @click="editField('industry', '行业')">
        <text class="label">行业</text><text class="value">{{ form.industry || '未设置' }}</text><text class="arrow">›</text>
      </view>
    </view>

    <!-- 保存 -->
    <button class="save-btn" @click="handleSave">保存</button>
  </view>
</template>

<script setup lang="ts">
import { ref } from "vue";
import { onShow } from "@dcloudio/uni-app";
import { useUserStore } from "@/store/user";

const userStore = useUserStore();
const form = ref({
  avatar: "", nickname: "", signature: "", gender: "男",
  city: "", height: "", education: "", industry: "",
  photoAlbum: [] as string[],
});

function changeAvatar() {
  uni.chooseImage({ count: 1, sizeType: ["compressed"], success: (res) => {
    // TODO: 上传 OSS
    form.value.avatar = res.tempFilePaths[0];
  }});
}

function addPhoto() {
  uni.chooseImage({ count: 6 - form.value.photoAlbum.length, sizeType: ["compressed"], success: (res) => {
    form.value.photoAlbum.push(...res.tempFilePaths);
  }});
}

function removePhoto(i: number) { form.value.photoAlbum.splice(i, 1); }

function editField(field: string, label: string) {
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

async function handleSave() {
  // TODO: 对接 PUT /users 更新资料
  userStore.updateUserInfo(form.value);
  uni.showToast({ title: "保存成功", icon: "success" });
  setTimeout(() => uni.navigateBack(), 800);
}

onShow(() => {
  if (userStore.userInfo) Object.assign(form.value, userStore.userInfo);
});
</script>

<style lang="scss" scoped>
.profile-page { min-height: 100vh; background: #f5f5f5; padding-bottom: 160rpx; }

.avatar-row { display: flex; align-items: center; padding: 24rpx 32rpx; background: #fff;
  .label { flex: 1; font-size: 28rpx; }
  .avatar { width: 120rpx; height: 120rpx; border-radius: 50%; }
  .arrow { font-size: 32rpx; color: #ccc; margin-left: 16rpx; }
}

.section { background: #fff; margin-top: 16rpx; padding: 24rpx 32rpx;
  .section-title { font-size: 26rpx; color: #999; display: block; margin-bottom: 16rpx; }
}

.album-grid { display: flex; flex-wrap: wrap; gap: 12rpx;
  .album-item { position: relative; width: 180rpx; height: 180rpx;
    .album-img { width: 100%; height: 100%; border-radius: 12rpx; }
    .remove { position: absolute; top: -8rpx; right: -8rpx; width: 36rpx; height: 36rpx; background: rgba(0,0,0,0.5); color: #fff; border-radius: 50%; display: flex; align-items: center; justify-content: center; font-size: 20rpx; }
  }
  .add-photo { width: 180rpx; height: 180rpx; background: #f5f5f5; border-radius: 12rpx; display: flex; align-items: center; justify-content: center; font-size: 56rpx; color: #ccc; }
}

.form-item { display: flex; align-items: center; padding: 20rpx 0; border-bottom: 1rpx solid #f5f5f5;
  &:last-child { border-bottom: none; }
  .label { width: 140rpx; font-size: 28rpx; color: #666; }
  .value { flex: 1; font-size: 28rpx; color: #333; text-align: right; }
  .arrow { font-size: 28rpx; color: #ccc; margin-left: 8rpx; }
  .radio-group { flex: 1; display: flex; gap: 32rpx; justify-content: flex-end; font-size: 28rpx; }
}

.save-btn {
  position: fixed; left: 32rpx; right: 32rpx; bottom: 40rpx; height: 88rpx; line-height: 88rpx;
  background: linear-gradient(90deg, #ff5e8e, #ff8fab); color: #fff;
  font-size: 32rpx; font-weight: bold; border-radius: 44rpx; border: none;
}
</style>