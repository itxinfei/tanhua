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
      <a11y-button
        class="item"
        v-for="s in notificationSettings"
        :key="s.key"
        :label="`${s.label}，当前${s.value ? '开启' : '关闭'}，点击切换`"
        @tap="onNotificationTap(s)"
      >
        <text class="label">{{ s.label }}</text>
        <!-- 服务端配置加载完成前禁用，避免拨动默认值覆盖服务端 -->
        <switch :checked="s.value" :disabled="!settingsLoaded" color="var(--color-primary)" @change="toggleNotification(s.key, $event)" @click.stop />
      </a11y-button>
    </view>

    <!-- 隐私 -->
    <view class="group">
      <text class="group-title">隐私</text>
      <a11y-button class="item" :label="`显示距离，当前${showDistance ? '开启' : '关闭'}，点击切换`" @tap="onDistanceChange({ detail: { value: !showDistance } })">
        <text class="label">显示距离</text>
        <switch :checked="showDistance" color="var(--color-primary)" @change="onDistanceChange" @click.stop />
      </a11y-button>
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

    <!-- 退出登录（a11y-button 全站按钮单一真源，loading 防重复点击） -->
    <a11y-button class="logout-btn" label="退出登录" :loading="logoutLoading" @tap="handleLogout">
      <text>退出登录</text>
    </a11y-button>

    <!-- 注销账号（破坏性操作：需可聚焦 + 语义化 + 足够对比度） -->
    <a11y-button class="delete-account" label="注销账号" @tap="handleDeleteAccount">
      <text>注销账号</text>
    </a11y-button>
  </view>
</template>

<script setup lang="ts">
import { ref } from "vue";
import { onShow, onUnload } from "@dcloudio/uni-app";
import { useUserStore } from "@/store/user";
import A11yButton from "@/components/a11y-button.vue";
import { getUserSettings, deleteAccount, getVerifyStatus, updateNotificationSetting, type NotificationSettingParam } from "@/api/mine";
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

/**
 * 通知开关切换（带防抖）
 *
 * 防抖理由：switch 原生控件在快速连点时会连续触发 @change，若无节流会产生竞态——
 * 后发请求先返回，导致本地状态与服务端不一致（BUG-11 收口）。
 * 策略：第一次点击立即生效并乐观更新 UI；在 DEBOUNCE_MS 内的重复点击只改 UI 不提交；
 * 最后一次点击后再等 DEBOUNCE_MS 才真正提交（trailing 生效），保证最终态被持久化。
 */
const NOTIFICATION_DEBOUNCE_MS = 300;
let notificationTimer: ReturnType<typeof setTimeout> | null = null;
/** 待提交的最终状态：每次点击覆盖，保证 trailing 提交的是最后一次 */
let pendingNotificationPayload: NotificationSettingParam | null = null;

/**
 * 整行点击切换通知开关（热区 ≥88rpx，原 switch 仅 32px 高难以点中）
 * switch 自身已有 @change，此处 @click.stop 阻止冒泡避免重复触发
 */
function onNotificationTap(s: { key: string; value: boolean }): void {
  toggleNotification(s.key, { detail: { value: !s.value } });
}

async function toggleNotification(key: string, e: any) {
  const s = notificationSettings.value.find(n => n.key === key);
  if (!settingsLoaded.value) {
    // 配置尚未从服务端加载，提交会把其余未加载项的默认值一并覆盖——拒绝并回弹
    if (s) s.value = !e.detail.value;
    fail("配置加载中，请稍候");
    return;
  }
  // 乐观更新：UI 立即跟随，不等服务端
  if (s) s.value = e.detail.value;

  // 记录本次目标状态，覆盖前一次未提交的
  const find = (k: string) => notificationSettings.value.find(n => n.key === k)?.value ?? true;
  pendingNotificationPayload = {
    likeNotification: find('like'),
    pinglunNotification: find('comment'),
    gonggaoNotification: find('announcement'),
    matchNotification: find('match'),
  };

  if (notificationTimer) clearTimeout(notificationTimer);
  notificationTimer = setTimeout(() => {
    notificationTimer = null;
    const payload = pendingNotificationPayload;
    pendingNotificationPayload = null;
    if (!payload) return;
    updateNotificationSetting(payload).catch(() => {
      // 持久化失败由 request 层统一 toast；回滚四项到提交前状态
      // （回滚为乐观更新的逆操作：把当前值取反）
      notificationSettings.value.forEach((n) => { n.value = !n.value; });
      fail("设置保存失败，已还原");
    });
  }, NOTIFICATION_DEBOUNCE_MS);
}

/** 页面卸载时清理防抖定时器，避免离页后回调触发请求 */
function clearNotificationDebounce(): void {
  if (notificationTimer) {
    clearTimeout(notificationTimer);
    notificationTimer = null;
  }
}

onUnload(clearNotificationDebounce);

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

/** 退出登录中状态（signOutAll 含 IM 登出，可能耗时，期间阻断重复点击） */
const logoutLoading = ref(false);

function handleLogout() {
  uni.showModal({
    title: '退出登录',
    content: '确定要退出登录吗？',
    success: async (res) => {
      if (!res.confirm) return;
      logoutLoading.value = true;
      try {
        // 全量清理：业务 store + IM（WuKongIM） + token，避免换账号登录后残留上一账号状态
        await userStore.signOutAll();
      } finally {
        logoutLoading.value = false;
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
/* 底部留出「固定 TabBar 高度 + 安全区 + 呼吸余量」，否则「退出登录 / 注销账号」
   会被导航栏遮住，滚到底也点不到（S1）。--tab-bar-height 由 App.vue 全局注入。 */
.settings-page {
  min-height: 100vh;
  background: var(--bg-sub);
  padding: 20rpx 24rpx calc(var(--tab-bar-height, 50px) + env(safe-area-inset-bottom, 0px) + 80rpx);
}

.group { background: var(--bg-card); margin: 0 0 24rpx; padding: 8rpx 32rpx; border-radius: 24rpx; box-shadow: 0 2rpx 12rpx rgba(0, 0, 0, 0.04);
  /* 分组标题原 #999（≈2.85:1）→ #767676 */
  .group-title { font-size: 24rpx; color: var(--text-hint); padding: 16rpx 0 8rpx; display: block; }
  .item { display: flex; align-items: center; min-height: var(--row-h-touch-md); border-bottom: 1rpx solid var(--border-faint); min-width: 0;
    &:last-child { border-bottom: none; }
    .label { flex: 1; font-size: 28rpx; color: var(--text-primary); }
    /* 长手机号/状态文本截断，避免挤占右侧箭头 */
    .value { font-size: 26rpx; color: var(--text-hint); margin-right: 8rpx; min-width: 0; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
    .value.verified { color: var(--color-primary-text); font-weight: 500; }
    /* 箭头 #ccc → #767676 */
    .arrow { font-size: 32rpx; color: var(--text-hint); }
  }
}

.logout-btn {
  margin: 16rpx 0 0; min-height: var(--row-h-touch-md);
  display: flex; align-items: center; justify-content: center;
  background: var(--bg-card); color: var(--color-danger); font-size: 30rpx; font-weight: 500;
  border-radius: 44rpx;
  box-shadow: 0 2rpx 12rpx rgba(0, 0, 0, 0.04);
}

/* 破坏性操作：保证触摸目标 ≥88rpx（54px），原 48rpx 偏低 */
.delete-account { text-align: center; padding: 32rpx; min-height: 88rpx; box-sizing: border-box;
  text { font-size: 26rpx; color: var(--color-danger); }
}
</style>
