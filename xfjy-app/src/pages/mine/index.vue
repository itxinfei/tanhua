<template>
  <view class="mine-page">
    <!-- 头部背景区 -->
    <view class="header" :style="{ paddingTop: statusBarHeight + 40 + 'px' }">
      <view class="user-row" @click="goProfile">
        <image class="avatar" :src="userInfo?.avatar || '/static/logo/logo-icon-transparent.png'" mode="aspectFill" />
        <view class="user-info">
          <view class="name-row">
            <text class="nickname">{{ userInfo?.nickname || '未设置昵称' }}</text>
            <view v-if="userInfo?.verified" class="verified">已认证</view>
            <view v-if="userInfo?.vip" class="vip-badge">VIP</view>
          </view>
          <text class="uid">ID: {{ userInfo?.id || '—' }}</text>
          <text class="meta">{{ userInfo?.city || '未知城市' }} · {{ userInfo?.age || '—' }}岁</text>
          <text v-if="userInfo?.signature" class="signature ellipsis">{{ userInfo.signature }}</text>
        </view>
        <text class="edit-arrow">›</text>
      </view>

      <!-- 数据栏（与后端 CountsVo 对齐：eachLoveCount/loveCount/fanCount） -->
      <view class="stats-row">
        <view class="stat-item" @click="goFriends(FRIEND_TYPE.MUTUAL)">
          <text class="stat-num">{{ counts.eachLoveCount }}</text>
          <text class="stat-label">互相喜欢</text>
        </view>
        <view class="stat-item" @click="goFriends(FRIEND_TYPE.FOLLOW)">
          <text class="stat-num">{{ counts.loveCount }}</text>
          <text class="stat-label">我喜欢</text>
        </view>
        <view class="stat-item" @click="goFriends(FRIEND_TYPE.FANS)">
          <text class="stat-num">{{ counts.fanCount }}</text>
          <text class="stat-label">粉丝</text>
        </view>
      </view>
    </view>

    <!-- VIP 入口 -->
    <view class="vip-card" @click="goVip">
      <view class="vip-left">
        <text class="vip-icon">👑</text>
        <view>
          <text class="vip-title">{{ userInfo?.vip ? '会员中心' : '开通会员' }}</text>
          <text class="vip-desc">{{ userInfo?.vip ? '查看你的会员权益' : '无限滑动 · 查看谁喜欢我 · 位置穿越' }}</text>
        </view>
      </view>
      <text class="vip-arrow">›</text>
    </view>

    <!-- 功能列表 -->
    <view class="menu-group">
      <view class="menu-item" @click="goPage('/pages/mine/profile')">
        <text class="menu-icon">✏️</text>
        <text class="menu-label">编辑资料</text>
        <text class="menu-arrow">›</text>
      </view>
      <view class="menu-item" @click="goFriends(FRIEND_TYPE.MUTUAL)">
        <text class="menu-icon">👥</text>
        <text class="menu-label">我的好友</text>
        <text class="menu-arrow">›</text>
      </view>
      <view class="menu-item" @click="goFriends(FRIEND_TYPE.VISITOR)">
        <text class="menu-icon">👁</text>
        <text class="menu-label">谁看过我</text>
        <text class="menu-arrow">›</text>
      </view>
      <view class="menu-item" @click="goFriends(FRIEND_TYPE.FOLLOW)">
        <text class="menu-icon">♥</text>
        <text class="menu-label">我喜欢</text>
        <text class="menu-arrow">›</text>
      </view>
      <view class="menu-item" @click="goFriends(FRIEND_TYPE.FANS)">
        <text class="menu-icon">🙋</text>
        <text class="menu-label">粉丝</text>
        <text class="menu-arrow">›</text>
      </view>
    </view>

    <view class="menu-group">
      <view class="menu-item" @click="goPage('/pages/mine/blacklist')">
        <text class="menu-icon">🚫</text>
        <text class="menu-label">黑名单</text>
        <text class="menu-arrow">›</text>
      </view>
      <view class="menu-item" @click="goPage('/pages/mine/settings')">
        <text class="menu-icon">⚙</text>
        <text class="menu-label">设置</text>
        <text class="menu-arrow">›</text>
      </view>
    </view>
  </view>
</template>

<script setup lang="ts">
import { ref, computed } from "vue";
import { onShow } from "@dcloudio/uni-app";
import { useUserStore } from "@/store/user";
import { getRelationCounts, FRIEND_TYPE, type RelationCounts } from "@/api/mine";

const userStore = useUserStore();
const userInfo = computed(() => userStore.userInfo);
const statusBarHeight = ref(0);

/** 关系统计（与后端 CountsVo 对齐） */
const counts = ref<RelationCounts>({
  eachLoveCount: 0,
  loveCount: 0,
  fanCount: 0,
});

function goProfile() {
  uni.navigateTo({ url: "/pages/mine/profile" });
}

function goVip() {
  uni.navigateTo({ url: "/pages/mine/vip" });
}

function goPage(url: string) {
  uni.navigateTo({ url });
}

/**
 * 跳转好友关系页
 * @param type 关系类型：1互相关注 2我关注 3粉丝 4谁看过我
 */
function goFriends(type: string) {
  uni.navigateTo({ url: `/pages/mine/friends?type=${type}` });
}

/**
 * 加载关系统计
 */
async function loadCounts() {
  try {
    counts.value = await getRelationCounts();
  } catch {
    // 请求失败由 request 层统一 toast
  }
}

onShow(() => {
  const sysInfo = uni.getSystemInfoSync();
  statusBarHeight.value = sysInfo.statusBarHeight || 20;
  loadCounts();
});
</script>

<style lang="scss" scoped>
.mine-page {
  min-height: calc(100vh - var(--window-bottom, 0px));
  background: #f5f5f5;
  padding-bottom: calc(var(--window-bottom, 0px) + 40rpx);
}

.header {
  background: linear-gradient(180deg, #ff5e8e 0%, #ff8fab 100%);
  padding: 0 32rpx 32rpx;
  border-radius: 0 0 40rpx 40rpx;

  .user-row {
    display: flex;
    align-items: center;

    .avatar {
      width: 140rpx;
      height: 140rpx;
      border-radius: 50%;
      border: 4rpx solid rgba(255, 255, 255, 0.6);
    }

    .user-info {
      flex: 1;
      margin-left: 24rpx;

      .name-row {
        display: flex;
        align-items: center;
        gap: 12rpx;

        .nickname {
          font-size: 36rpx;
          font-weight: bold;
          color: #fff;
        }

        .verified {
          font-size: 20rpx;
          color: #fff;
          background: rgba(255, 255, 255, 0.3);
          padding: 2rpx 12rpx;
          border-radius: 16rpx;
        }

        .vip-badge {
          font-size: 20rpx;
          color: #ffd700;
          background: rgba(0, 0, 0, 0.25);
          padding: 2rpx 12rpx;
          border-radius: 16rpx;
        }
      }

      .uid {
        font-size: 22rpx;
        color: rgba(255, 255, 255, 0.7);
        margin-top: 6rpx;
        display: block;
      }

      .meta {
        font-size: 24rpx;
        color: rgba(255, 255, 255, 0.85);
        margin-top: 6rpx;
        display: block;
      }

      .signature {
        font-size: 24rpx;
        color: rgba(255, 255, 255, 0.8);
        margin-top: 8rpx;
        display: block;
      }
    }

    .edit-arrow {
      font-size: 40rpx;
      color: rgba(255, 255, 255, 0.6);
    }
  }

  .stats-row {
    display: flex;
    justify-content: space-around;
    margin-top: 32rpx;
    background: rgba(255, 255, 255, 0.15);
    border-radius: 20rpx;
    padding: 20rpx 0;

    .stat-item {
      display: flex;
      flex-direction: column;
      align-items: center;

      .stat-num {
        font-size: 36rpx;
        font-weight: bold;
        color: #fff;
      }

      .stat-label {
        font-size: 22rpx;
        color: rgba(255, 255, 255, 0.7);
        margin-top: 4rpx;
      }
    }
  }
}

.vip-card {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin: 24rpx 32rpx;
  padding: 28rpx 32rpx;
  background: linear-gradient(135deg, #2c2c2c, #4a3b2a);
  border-radius: 24rpx;
  border: 2rpx solid #ffd700;

  .vip-left {
    display: flex;
    align-items: center;
    gap: 20rpx;

    .vip-icon {
      font-size: 48rpx;
    }

    .vip-title {
      font-size: 30rpx;
      font-weight: bold;
      color: #ffd700;
      display: block;
    }

    .vip-desc {
      font-size: 22rpx;
      color: rgba(255, 215, 0, 0.7);
      margin-top: 4rpx;
      display: block;
    }
  }

  .vip-arrow {
    font-size: 36rpx;
    color: #ffd700;
  }
}

.menu-group {
  margin: 0 32rpx 24rpx;
  background: #fff;
  border-radius: 24rpx;
  overflow: hidden;

  .menu-item {
    display: flex;
    align-items: center;
    padding: 28rpx 32rpx;
    border-bottom: 1rpx solid #f5f5f5;

    &:last-child {
      border-bottom: none;
    }

    .menu-icon {
      font-size: 36rpx;
      width: 56rpx;
    }

    .menu-label {
      flex: 1;
      font-size: 28rpx;
      color: #333;
      margin-left: 16rpx;
    }

    .menu-arrow {
      font-size: 32rpx;
      color: #ccc;
    }
  }
}
</style>