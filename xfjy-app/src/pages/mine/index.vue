<template>
  <view class="mine-page">
    <!-- 头部背景区 -->
    <view class="header" :style="{ paddingTop: statusBarHeight + HEADER_EXTRA_PADDING_PX + 'px' }">
      <!-- 漂浮装饰圆（纯装饰，对读屏隐藏） -->
      <view class="deco-circle deco-1 float" aria-hidden="true" />
      <view class="deco-circle deco-2 float" aria-hidden="true" />
      <a11y-button class="user-row" label="编辑个人资料" @tap="goProfile">
        <image lazy-load class="avatar" :src="userInfo?.avatar || '/static/logo/logo-icon-transparent.png'" mode="aspectFill" aria-hidden="true" />
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
        <!-- 箭头为装饰符号，避免读屏逐字朗读 -->
        <text class="edit-arrow" aria-hidden="true">›</text>
      </a11y-button>

      <!-- 数据栏（与后端 CountsVo 对齐：eachLoveCount/loveCount/fanCount） -->
      <view class="stats-row">
        <a11y-button class="stat-item" :label="`互相喜欢 ${counts.eachLoveCount} 人，查看列表`" @tap="goFriends(FRIEND_TYPE.MUTUAL)">
          <text class="stat-num">{{ counts.eachLoveCount }}</text>
          <text class="stat-label">互相喜欢</text>
        </a11y-button>
        <a11y-button class="stat-item" :label="`我喜欢 ${counts.loveCount} 人，查看列表`" @tap="goFriends(FRIEND_TYPE.FOLLOW)">
          <text class="stat-num">{{ counts.loveCount }}</text>
          <text class="stat-label">我喜欢</text>
        </a11y-button>
        <a11y-button class="stat-item" :label="`粉丝 ${counts.fanCount} 人，查看列表`" @tap="goFriends(FRIEND_TYPE.FANS)">
          <text class="stat-num">{{ counts.fanCount }}</text>
          <text class="stat-label">粉丝</text>
        </a11y-button>
      </view>
    </view>

    <!-- VIP 入口 -->
    <a11y-button class="vip-card" :label="userInfo?.vip ? '会员中心' : '开通会员'" @tap="goVip">
      <view class="vip-left">
        <uni-icons class="vip-icon" type="vip" size="24"></uni-icons>
        <view>
          <text class="vip-title">{{ userInfo?.vip ? '会员中心' : '开通会员' }}</text>
          <text class="vip-desc">{{ userInfo?.vip ? '查看你的会员权益' : '无限滑动 · 查看谁喜欢我 · 位置穿越' }}</text>
        </view>
      </view>
      <text class="vip-arrow" aria-hidden="true">›</text>
    </a11y-button>

    <!-- 功能列表（表驱动渲染） -->
    <view
      v-for="(group, groupIndex) in menuGroups"
      :key="groupIndex"
      class="menu-group"
    >
      <a11y-button
        v-for="item in group"
        :key="item.label"
        class="menu-item btn-feedback"
        :label="item.label"
        @tap="item.action()"
      >
        <view class="menu-icon-bg" :style="{ background: item.color }">
          <uni-icons class="menu-icon" :type="item.uniIcon" size="24" color="#fff"></uni-icons>
        </view>
        <text class="menu-label">{{ item.label }}</text>
        <text class="menu-arrow" aria-hidden="true">›</text>
      </a11y-button>
    </view>
    <!-- 匹配成功弹窗（全局，由 match store 驱动） -->
    <MatchPopup />
  </view>
</template>

<script setup lang="ts">
import { ref, computed } from "vue";
import { onShow } from "@dcloudio/uni-app";
import MatchPopup from "@/components/match-popup.vue";
import A11yButton from "@/components/a11y-button.vue";
import { useUserStore } from "@/store/user";
import { getRelationCounts, FRIEND_TYPE, type RelationCounts } from "@/api/mine";

const userStore = useUserStore();
const userInfo = computed(() => userStore.userInfo);
const statusBarHeight = ref(0);

/** 状态栏高度兜底值（px） */
const DEFAULT_STATUS_BAR_HEIGHT_PX = 20;
/** 头部额外顶部留白（px，状态栏下方间距） */
const HEADER_EXTRA_PADDING_PX = 24;

/** 关系统计（与后端 CountsVo 对齐） */
const counts = ref<RelationCounts>({
  eachLoveCount: 0,
  loveCount: 0,
  fanCount: 0,
});

/** 菜单项配置（表驱动：图标/文案/色块颜色/点击行为） */
interface MenuItemConfig {
  uniIcon: string;
  label: string;
  color: string;
  action: () => void;
}

/** 社交关系菜单组 */
const socialMenuItems: MenuItemConfig[] = [
  {
    uniIcon: "compose",
    label: "编辑资料",
    color: "#ff5e9c",
    action: () => goPage("/pages/mine/profile"),
  },
  {
    uniIcon: "contact",
    label: "我的好友",
    color: "#4a90d9",
    action: () => goFriends(FRIEND_TYPE.MUTUAL),
  },
  {
    uniIcon: "eye",
    label: "谁看过我",
    color: "#a78bfa",
    action: () => goFriends(FRIEND_TYPE.VISITOR),
  },
  {
    uniIcon: "heart-filled",
    label: "我喜欢",
    color: "#ff5e9c",
    action: () => goFriends(FRIEND_TYPE.FOLLOW),
  },
  {
    uniIcon: "person",
    label: "粉丝",
    color: "#4cd964",
    action: () => goFriends(FRIEND_TYPE.FANS),
  },
];

/** 系统设置菜单组 */
const settingsMenuItems: MenuItemConfig[] = [
  {
    uniIcon: "close",
    label: "黑名单",
    color: "#999999",
    action: () => goPage("/pages/mine/blacklist"),
  },
  {
    uniIcon: "headphones",
    label: "在线客服",
    color: "#ff5e9c",
    action: () => goPage("/pages/kefu/index"),
  },
  {
    uniIcon: "gear",
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
  // 同步最新用户资料（头像/昵称等），避免登录态恢复或编辑资料后头部显示陈旧默认值
  userStore.fetchUserInfo().catch(() => {});
  loadCounts();
});
</script>

<style lang="scss" scoped>
.mine-page {
  min-height: 100vh;
  background: #f5f5f5;
  padding-bottom: 40rpx;
}

.header {
  background: linear-gradient(180deg, var(--color-primary-deep) 0%, var(--color-primary) 100%);
  padding: 0 32rpx 24rpx;
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
      width: 128rpx;
      height: 128rpx;
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

      /* 渐变底上的小字：透明度提至接近不透明，保证 4.5:1 */
      .uid {
        font-size: 24rpx;
        color: rgba(255, 255, 255, 0.95);
        margin-top: 6rpx;
        display: block;
      }

      .meta {
        font-size: 24rpx;
        color: rgba(255, 255, 255, 0.95);
        margin-top: 6rpx;
        display: block;
      }

      .signature {
        font-size: 24rpx;
        color: rgba(255, 255, 255, 0.95);
        margin-top: 8rpx;
        display: block;
      }
    }

    .edit-arrow {
      font-size: 40rpx;
      color: rgba(255, 255, 255, 0.85);
    }
  }

  .stats-row {
    display: flex;
    justify-content: space-around;
    margin-top: 24rpx;
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

      /* 22rpx 小字 + 半透明不达标 → 提至 0.95 */
      .stat-label {
        font-size: 24rpx;
        color: rgba(255, 255, 255, 0.95);
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
      color: rgba(255, 215, 0, 0.95);
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

    /* #ccc on #fff ≈1.6:1，提至 #767676（≈4.54:1） */
    .menu-arrow {
      font-size: 32rpx;
      color: #767676;
    }
  }
}
</style>