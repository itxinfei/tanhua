<template>
  <view class="mine-page">
    <!-- 头部背景区 -->
    <view class="header" :style="{ paddingTop: statusBarHeight + HEADER_EXTRA_PADDING_PX + 'px' }">
      <!-- 漂浮装饰圆 -->
      <view class="deco-circle deco-1 float" />
      <view class="deco-circle deco-2 float" />
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

    <!-- 功能列表（表驱动渲染） -->
    <view
      v-for="(group, groupIndex) in menuGroups"
      :key="groupIndex"
      class="menu-group"
    >
      <view
        v-for="item in group"
        :key="item.label"
        class="menu-item btn-feedback"
        @click="item.action()"
      >
        <view class="menu-icon-bg" :style="{ background: item.color }">
          <text class="menu-icon">{{ item.icon }}</text>
        </view>
        <text class="menu-label">{{ item.label }}</text>
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

/** 状态栏高度兜底值（px） */
const DEFAULT_STATUS_BAR_HEIGHT_PX = 20;
/** 头部额外顶部留白（px，状态栏下方间距） */
const HEADER_EXTRA_PADDING_PX = 40;

/** 关系统计（与后端 CountsVo 对齐） */
const counts = ref<RelationCounts>({
  eachLoveCount: 0,
  loveCount: 0,
  fanCount: 0,
});

/** 菜单项配置（表驱动：图标/文案/色块颜色/点击行为） */
interface MenuItemConfig {
  icon: string;
  label: string;
  color: string;
  action: () => void;
}

/** 社交关系菜单组 */
const socialMenuItems: MenuItemConfig[] = [
  {
    icon: "✏️",
    label: "编辑资料",
    color: "#ff5e8e",
    action: () => goPage("/pages/mine/profile"),
  },
  {
    icon: "👥",
    label: "我的好友",
    color: "#4a90d9",
    action: () => goFriends(FRIEND_TYPE.MUTUAL),
  },
  {
    icon: "👁",
    label: "谁看过我",
    color: "#a78bfa",
    action: () => goFriends(FRIEND_TYPE.VISITOR),
  },
  {
    icon: "♥",
    label: "我喜欢",
    color: "#ff3b30",
    action: () => goFriends(FRIEND_TYPE.FOLLOW),
  },
  {
    icon: "🙋",
    label: "粉丝",
    color: "#4cd964",
    action: () => goFriends(FRIEND_TYPE.FANS),
  },
];

/** 系统设置菜单组 */
const settingsMenuItems: MenuItemConfig[] = [
  {
    icon: "🚫",
    label: "黑名单",
    color: "#999999",
    action: () => goPage("/pages/mine/blacklist"),
  },
  {
    icon: "🎧",
    label: "在线客服",
    color: "#ff5e8e",
    action: () => goPage("/pages/kefu/index"),
  },
  {
    icon: "⚙",
    label: "设置",
    color: "#666666",
    action: () => goPage("/pages/mine/settings"),
  },
];

/** 全部菜单组（渲染顺序：社交 → 设置） */
const menuGroups = [socialMenuItems, settingsMenuItems];

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
  statusBarHeight.value =
    sysInfo.statusBarHeight || DEFAULT_STATUS_BAR_HEIGHT_PX;
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
  position: relative;
  overflow: hidden;

  /* 漂浮装饰圆 */
  .deco-circle {
    position: absolute;
    border-radius: 50%;
    background: rgba(255, 255, 255, 0.1);
    pointer-events: none;

    &.deco-1 {
      width: 240rpx;
      height: 240rpx;
      top: 20rpx;
      right: -60rpx;
      animation-duration: 3.5s;
    }

    &.deco-2 {
      width: 160rpx;
      height: 160rpx;
      top: 120rpx;
      left: -40rpx;
      animation-duration: 4s;
      animation-delay: 0.8s;
    }
  }

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
  background: linear-gradient(135deg, #1a1a2e, #2c2c2c, #4a3b2a);
  border-radius: 24rpx;
  border: 2rpx solid rgba(255, 215, 0, 0.6);
  box-shadow: 0 8rpx 32rpx rgba(255, 215, 0, 0.15);

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

    .menu-icon-bg {
      width: 64rpx;
      height: 64rpx;
      border-radius: 16rpx;
      display: flex;
      align-items: center;
      justify-content: center;
      flex-shrink: 0;
    }

    .menu-icon {
      font-size: 32rpx;
      color: #fff;
      line-height: 1;
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