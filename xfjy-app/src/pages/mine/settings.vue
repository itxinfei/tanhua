<template>
  <view class="settings-page">
    <!-- 账号与安全 -->
    <view class="group">
      <text class="group-title">账号与安全</text>
      <view class="item" @click="goPhone">
        <text class="label">手机号</text>
        <text class="value">{{ phoneMasked || '未绑定' }}</text>
        <text class="arrow">›</text>
      </view>
      <view class="item" @click="goVerify">
        <text class="label">实名认证</text>
        <text class="value" :class="{ verified: isVerified }">{{ isVerified ? '已认证' : '未认证' }}</text>
        <text class="arrow">›</text>
      </view>
      <view class="item" @click="goBlacklist">
        <text class="label">黑名单</text>
        <text class="arrow">›</text>
      </view>
    </view>

    <!-- 通知设置 -->
    <view class="group">
      <text class="group-title">通知设置</text>
      <view class="item" v-for="s in notificationSettings" :key="s.key">
        <text class="label">{{ s.label }}</text>
        <switch :checked="s.value" color="#ff5e8e" @change="toggleNotification(s.key, $event)" />
      </view>
    </view>

    <!-- 隐私 -->
    <view class="group">
      <text class="group-title">隐私</text>
      <view class="item">
        <text class="label">显示距离</text>
        <switch :checked="showDistance" color="#ff5e8e" @change="showDistance = ($event as any).detail.value" />
      </view>
    </view>

    <!-- 通用 -->
    <view class="group">
      <text class="group-title">通用</text>
      <view class="item" @click="clearCache">
        <text class="label">清除缓存</text>
        <text class="value">{{ cacheSize }}</text>
      </view>
      <view class="item" @click="showAbout">
        <text class="label">关于心飞</text>
        <text class="value">v1.0.0</text>
      </view>
    </view>

    <!-- 退出登录 -->
    <button class="logout-btn" @click="handleLogout">退出登录</button>

    <!-- 注销账号 -->
    <view class="delete-account" @click="handleDeleteAccount">
      <text>注销账号</text>
    </view>
  </view>
</template>

<script setup lang="ts">
import { ref, computed } from "vue";
import { onShow } from "@dcloudio/uni-app";
import { useUserStore } from "@/store/user";
import { getUserSettings, deleteAccount, getVerifyStatus, updateNotificationSetting } from "@/api/mine";
import { sendSmsCode } from "@/api/auth";

const userStore = useUserStore();
const phoneMasked = ref('');
const isVerified = ref(false);
const showDistance = ref(true);
const cacheSize = ref('0MB');

const notificationSettings = ref([
  { key: 'match', label: '匹配成功通知', value: true },
  { key: 'like', label: '被喜欢通知', value: true },
  { key: 'comment', label: '评论通知', value: true },
  { key: 'announcement', label: '系统公告', value: false },
]);

async function toggleNotification(key: string, e: any) {
  const s = notificationSettings.value.find(n => n.key === key);
  if (s) s.value = e.detail.value;
  // 四项开关统一持久化到 /users/notifications/setting
  const find = (k: string) => notificationSettings.value.find(n => n.key === k)?.value ?? true;
  try {
    await updateNotificationSetting({
      likeNotification: find('like'),
      pinglunNotification: find('comment'),
      gonggaoNotification: find('announcement'),
      matchNotification: find('match'),
    });
  } catch {
    // 持久化失败由 request 层统一 toast，回滚本地开关
    if (s) s.value = !e.detail.value;
  }
}

function goPhone() { uni.navigateTo({ url: "/pages/mine/change-phone" }); }
function goVerify() { uni.navigateTo({ url: "/pages/mine/verify" }); }
function goBlacklist() { uni.navigateTo({ url: "/pages/mine/blacklist" }); }

/** 加载当前手机号（脱敏显示，换绑返回后 onShow 刷新） */
async function loadPhone() {
  try {
    const settings = await getUserSettings();
    const phone = settings?.phone || '';
    phoneMasked.value = phone ? phone.replace(/(\d{3})\d{4}(\d{4})/, '$1****$2') : '';
    // 回填通知开关初始值
    if (settings) {
      const setVal = (key: string, val?: boolean) => {
        const item = notificationSettings.value.find(n => n.key === key);
        if (item && typeof val === 'boolean') item.value = val;
      };
      setVal('like', settings.likeNotification);
      setVal('comment', settings.pinglunNotification);
      setVal('announcement', settings.gonggaoNotification);
      setVal('match', settings.matchNotification);
    }
  } catch {
    // 请求失败由 request 层统一 toast
  }
}

/** 加载实名认证状态（认证返回后 onShow 刷新） */
async function loadVerifyStatus() {
  try {
    const res = await getVerifyStatus();
    isVerified.value = res?.verified || false;
  } catch {
    // 请求失败由 request 层统一 toast
  }
}

onShow(() => {
  loadPhone();
  loadVerifyStatus();
});

function clearCache() {
  uni.showModal({
    title: '清除缓存',
    content: `将清除 ${cacheSize.value} 缓存数据`,
    success: (res) => {
      if (res.confirm) {
        uni.clearStorageSync();
        cacheSize.value = '0MB';
        uni.showToast({ title: '清除成功', icon: 'success' });
      }
    },
  });
}

function showAbout() {
  uni.showModal({ title: '心飞交友', content: '版本 v1.0.0\n心飞为你飞，遇见对的人', showCancel: false });
}

function handleLogout() {
  uni.showModal({
    title: '退出登录',
    content: '确定要退出登录吗？',
    success: (res) => {
      if (res.confirm) userStore.logout();
    },
  });
}

/** 注销账号：二次确认 → 发验证码 → 输入验证码 → 调接口注销 */
function handleDeleteAccount() {
  uni.showModal({
    title: '注销账号',
    content: '注销后账号将立即无法登录，数据保留30天后永久删除。确定要注销吗？',
    confirmText: '继续',
    confirmColor: '#ff3b30',
    success: (res) => {
      if (res.confirm) {
        sendDeleteCode();
      }
    },
  });
}

/** 向当前账号手机号发送验证码 */
async function sendDeleteCode() {
  uni.showLoading({ title: '发送验证码...' });
  try {
    const settings = await getUserSettings();
    if (!settings?.phone) {
      uni.showToast({ title: '未绑定手机号，无法注销', icon: 'none' });
      return;
    }
    await sendSmsCode(settings.phone);
    uni.hideLoading();
    uni.showToast({ title: '验证码已发送', icon: 'none' });
    inputDeleteCode();
  } catch (e) {
    uni.hideLoading();
    uni.showToast({ title: '验证码发送失败', icon: 'none' });
  }
}

/** 弹出验证码输入框，提交注销 */
function inputDeleteCode() {
  uni.showModal({
    title: '输入验证码',
    content: '请输入手机收到的6位验证码',
    editable: true,
    placeholderText: '6位验证码',
    confirmText: '确定注销',
    confirmColor: '#ff3b30',
    success: async (res) => {
      if (!res.confirm) return;
      const code = (res.content || '').trim();
      if (!/^\d{6}$/.test(code)) {
        uni.showToast({ title: '请输入6位数字验证码', icon: 'none' });
        return;
      }
      uni.showLoading({ title: '注销中...' });
      try {
        await deleteAccount(code);
        uni.hideLoading();
        uni.showToast({ title: '账号已注销', icon: 'success' });
        userStore.logout();
      } catch (e) {
        uni.hideLoading();
        uni.showToast({ title: '验证码错误，注销失败', icon: 'none' });
      }
    },
  });
}
</script>

<style lang="scss" scoped>
.settings-page { min-height: 100vh; background: #f5f5f5; padding: 20rpx 24rpx 80rpx; }

.group { background: #fff; margin: 0 0 24rpx; padding: 8rpx 32rpx; border-radius: 24rpx; box-shadow: 0 2rpx 12rpx rgba(0, 0, 0, 0.04);
  .group-title { font-size: 24rpx; color: #999; padding: 16rpx 0 8rpx; display: block; }
  .item { display: flex; align-items: center; padding: 26rpx 0; border-bottom: 1rpx solid #f5f5f5;
    &:last-child { border-bottom: none; }
    .label { flex: 1; font-size: 28rpx; color: #333; }
    .value { font-size: 26rpx; color: #999; margin-right: 8rpx; }
    .value.verified { color: #ff5e8e; font-weight: 500; }
    .arrow { font-size: 32rpx; color: #ccc; }
  }
}

.logout-btn {
  margin: 16rpx 0 0; height: 88rpx; line-height: 88rpx;
  background: #fff; color: #ff3b30; font-size: 30rpx; font-weight: 500;
  border-radius: 44rpx; border: none;
  box-shadow: 0 2rpx 12rpx rgba(0, 0, 0, 0.04);
}

.delete-account { text-align: center; padding: 32rpx;
  text { font-size: 24rpx; color: #ccc; }
}
</style>