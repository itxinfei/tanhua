<template>
  <view class="settings-page">
    <!-- 账号与安全 -->
    <view class="group">
      <text class="group-title">账号与安全</text>
      <a11y-button class="item" :label="`手机号，当前 ${phoneMasked || '未绑定'}`" @tap="goPhone">
        <text class="label">手机号</text>
        <text class="value">{{ phoneMasked || '未绑定' }}</text>
        <text class="arrow" aria-hidden="true">›</text>
      </a11y-button>
      <a11y-button class="item" :label="`实名认证，当前 ${isVerified ? '已认证' : '未认证'}`" @tap="goVerify">
        <text class="label">实名认证</text>
        <text class="value" :class="{ verified: isVerified }">{{ isVerified ? '已认证' : '未认证' }}</text>
        <text class="arrow" aria-hidden="true">›</text>
      </a11y-button>
      <a11y-button class="item" label="黑名单" @tap="goBlacklist">
        <text class="label">黑名单</text>
        <text class="arrow" aria-hidden="true">›</text>
      </a11y-button>
    </view>

    <!-- 通知设置 -->
    <view class="group">
      <text class="group-title">通知设置</text>
      <view class="item" v-for="s in notificationSettings" :key="s.key">
        <text class="label">{{ s.label }}</text>
        <switch :checked="s.value" :aria-label="s.label" color="var(--color-primary)" @change="toggleNotification(s.key, $event)" />
      </view>
    </view>

    <!-- 隐私 -->
    <view class="group">
      <text class="group-title">隐私</text>
      <view class="item">
        <text class="label">显示距离</text>
        <switch :checked="showDistance" aria-label="显示距离" color="var(--color-primary)" @change="onDistanceChange" />
      </view>
    </view>

    <!-- 通用 -->
    <view class="group">
      <text class="group-title">通用</text>
      <a11y-button class="item" :label="`清除缓存，当前 ${cacheSize}`" @tap="clearCache">
        <text class="label">清除缓存</text>
        <text class="value">{{ cacheSize }}</text>
      </a11y-button>
      <a11y-button class="item" label="关于心飞，版本 v1.0.0" @tap="showAbout">
        <text class="label">关于心飞</text>
        <text class="value">v1.0.0</text>
      </a11y-button>
    </view>

    <!-- 退出登录 -->
    <button class="logout-btn" @click="handleLogout">退出登录</button>

    <!-- 注销账号（破坏性操作：需可聚焦 + 语义化 + 足够对比度） -->
    <a11y-button class="delete-account" label="注销账号" @tap="handleDeleteAccount">
      <text>注销账号</text>
    </a11y-button>
  </view>
</template>

<script setup lang="ts">
import { ref } from "vue";
import { onShow } from "@dcloudio/uni-app";
import { useUserStore } from "@/store/user";
import A11yButton from "@/components/a11y-button.vue";
import { getUserSettings, deleteAccount, getVerifyStatus, updateNotificationSetting } from "@/api/mine";
import { sendSmsCode } from "@/api/auth";
import { success, fail, info, loading, hideLoading } from "@/utils/ux";

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

/** 服务端通知配置是否已加载（未加载前开关只改本地、不提交，避免默认值覆盖服务端） */
const settingsLoaded = ref(false);

async function toggleNotification(key: string, e: any) {
  const s = notificationSettings.value.find(n => n.key === key);
  if (!settingsLoaded.value) {
    // 配置尚未从服务端加载，提交会把其余未加载项的默认值一并覆盖——拒绝并回弹
    if (s) s.value = !e.detail.value;
    fail("配置加载中，请稍候");
    return;
  }
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
      settingsLoaded.value = true;
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

/** 计算本地缓存实际占用（currentSize 单位 KB） */
function loadCacheSize(): void {
  try {
    const info = uni.getStorageInfoSync();
    cacheSize.value = `${(info.currentSize / 1024).toFixed(1)}MB`;
  } catch {
    cacheSize.value = '0MB';
  }
}

/** 读取本地持久化的「显示距离」偏好（后端无对应字段，存本地存储） */
function loadDistance(): void {
  try {
    const saved = uni.getStorageSync('xfjy_show_distance');
    if (typeof saved === 'boolean') showDistance.value = saved;
  } catch {
    // 读取失败静默降级，沿用默认值
  }
}

/** 切换「显示距离」并持久化到本地存储，避免返回即丢失 */
function onDistanceChange(e: any): void {
  showDistance.value = !!e?.detail?.value;
  try {
    uni.setStorageSync('xfjy_show_distance', showDistance.value);
  } catch {
    // 持久化失败静默降级
  }
}

onShow(() => {
  loadPhone();
  loadVerifyStatus();
  loadCacheSize();
  loadDistance();
});

function clearCache() {
  uni.showModal({
    title: '清除缓存',
    content: `将清除 ${cacheSize.value} 缓存数据`,
    success: (res) => {
      if (res.confirm) {
        // 保留登录 token 与用户偏好（城市/距离开关/通知已读标记），其余缓存清除
        const keep: Record<string, unknown> = {};
        for (const key of ["xfjy_token", "xfjy_city", "xfjy_show_distance"]) {
          const v = uni.getStorageSync(key);
          if (v !== "" && v !== null && v !== undefined) keep[key] = v;
        }
        // 通知已读标记前缀 xfjy_notice_seen_*
        try {
          for (const k of uni.getStorageInfoSync().keys) {
            if (k.startsWith("xfjy_notice_seen_")) keep[k] = uni.getStorageSync(k);
          }
        } catch {
          // 枚举失败则不保留该项
        }
        uni.clearStorageSync();
        for (const [k, v] of Object.entries(keep)) uni.setStorageSync(k, v);
        cacheSize.value = '0MB';
        success('清除成功');
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
      if (res.confirm) {
        // 全量清理：业务 store + IM（WuKongIM） + token，避免换账号登录后残留上一账号状态
        userStore.signOutAll();
      }
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
  loading('发送验证码...');
  try {
    const settings = await getUserSettings();
    if (!settings?.phone) {
      fail('未绑定手机号，无法注销');
      return;
    }
    await sendSmsCode(settings.phone);
    hideLoading();
    info('验证码已发送');
    inputDeleteCode();
  } catch (e) {
    // 失败提示由 request 层统一 toast，此处仅关 loading，避免双 toast 叠显
    hideLoading();
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
        info('请输入6位数字验证码');
        return;
      }
      loading('注销中...');
      try {
        await deleteAccount(code);
        hideLoading();
        success('账号已注销');
        userStore.signOutAll();
      } catch (e) {
        // 失败提示由 request 层统一 toast（验证码错误也是 400，文案由请求层给出）
        hideLoading();
      }
    },
  });
}
</script>

<style lang="scss" scoped>
.settings-page { min-height: 100vh; background: var(--bg-sub); padding: 20rpx 24rpx 80rpx; }

.group { background: var(--bg-card); margin: 0 0 24rpx; padding: 8rpx 32rpx; border-radius: 24rpx; box-shadow: 0 2rpx 12rpx rgba(0, 0, 0, 0.04);
  /* 分组标题原 #999（≈2.85:1）→ #767676 */
  .group-title { font-size: 24rpx; color: var(--text-hint); padding: 16rpx 0 8rpx; display: block; }
  .item { display: flex; align-items: center; padding: 26rpx 0; border-bottom: 1rpx solid var(--border-faint);
    &:last-child { border-bottom: none; }
    .label { flex: 1; font-size: 28rpx; color: var(--text-primary); }
    /* 副文本 #999 → #767676 */
    .value { font-size: 26rpx; color: var(--text-hint); margin-right: 8rpx; }
    .value.verified { color: var(--color-primary-text); font-weight: 500; }
    /* 箭头 #ccc → #767676 */
    .arrow { font-size: 32rpx; color: var(--text-hint); }
  }
}

.logout-btn {
  margin: 16rpx 0 0; height: 88rpx; line-height: 88rpx;
  background: var(--bg-card); color: #ff3b30; font-size: 30rpx; font-weight: 500;
  border-radius: 44rpx; border: none;
  box-shadow: 0 2rpx 12rpx rgba(0, 0, 0, 0.04);
}

/* 破坏性操作：#ccc（≈1.6:1）改为深红并保证触摸目标 ≥24px */
.delete-account { text-align: center; padding: 32rpx; min-height: 48rpx;
  text { font-size: 26rpx; color: #c62828; }
}
</style>
