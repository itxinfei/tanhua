<template>
  <view class="mine-page">
    <!-- 头部背景区 -->
    <view class="header" :style="{ paddingTop: statusBarHeight + HEADER_EXTRA_PADDING_PX + 'px' }">
      <!-- 漂浮装饰圆（纯装饰，对读屏隐藏） -->
      <view class="deco-circle deco-1 float" aria-hidden="true" />
      <view class="deco-circle deco-2 float" aria-hidden="true" />
      <a11y-button class="user-row" label="编辑个人资料" @tap="goProfile">
        <image lazy-load class="avatar" :src="resolveAvatar(userInfo?.avatar)" mode="aspectFill" aria-hidden="true" />
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

    <!-- 功能列表（表驱动渲染）。末组加 menu-group--tail 吸收剩余高度，占满到 tabbar -->
    <view
      v-for="(group, groupIndex) in menuGroups"
      :key="groupIndex"
      class="menu-group"
      :class="{ 'menu-group--tail': groupIndex === menuGroups.length - 1 }"
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
import { resolveAvatar } from "@/utils/avatar";

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
  visitorCount: 0,
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
    color: "var(--color-primary)",
    action: () => goPage("/pages/mine/profile"),
  },
  {
    uniIcon: "contact",
    label: "我的好友",
    color: "var(--info-blue)",
    action: () => goFriends(FRIEND_TYPE.MUTUAL),
  },
  {
    uniIcon: "eye",
    label: "谁看过我",
    color: "var(--color-primary-violet)",
    action: () => goFriends(FRIEND_TYPE.VISITOR),
  },
  {
    uniIcon: "heart-filled",
    label: "我喜欢",
    color: "var(--color-primary)",
    action: () => goFriends(FRIEND_TYPE.FOLLOW),
  },
  {
    uniIcon: "person",
    label: "粉丝",
    color: "var(--success)",
    action: () => goFriends(FRIEND_TYPE.FANS),
  },
];

/** 系统设置菜单组 */
const settingsMenuItems: MenuItemConfig[] = [
  {
    uniIcon: "close",
    label: "黑名单",
    color: "var(--text-hint-weak)",
    action: () => goPage("/pages/mine/blacklist"),
  },
  {
    uniIcon: "headphones",
    label: "在线客服",
    color: "var(--color-primary)",
    action: () => goPage("/pages/kefu/index"),
  },
  {
    uniIcon: "gear",
    label: "设置",
    color: "var(--text-secondary)",
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
/* 纵向 flex：头部与 VIP 卡固定，菜单组吸收剩余高度，保证内容较少时也占满屏幕 */
.mine-page {
  display: flex;
  flex-direction: column;
  min-height: calc(100vh - var(--window-bottom, 0px));
  background: var(--bg-sub);
  padding-bottom: calc(var(--window-bottom, 0px) + 24rpx);
  box-sizing: border-box;
}

.header {
  background: linear-gradient(180deg, var(--color-primary-deep) 0%, var(--color-primary) 100%);
  padding: 0 32rpx 16rpx;
  border-radius: 0 0 40rpx 40rpx;
  position: relative;
  overflow: hidden;
  flex-shrink: 0;

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
          color: var(--text-inverse);
        }

        .verified {
          font-size: 20rpx;
          color: var(--text-inverse);
          background: rgba(255, 255, 255, 0.3);
          padding: 2rpx 12rpx;
          border-radius: 16rpx;
        }

        .vip-badge {
          font-size: 20rpx;
          color: var(--gold);
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
    margin-top: 16rpx;
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
        color: var(--text-inverse);
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
  margin: 16rpx 32rpx;
  padding: 20rpx 32rpx;
  flex-shrink: 0;
  background: linear-gradient(135deg, var(--bg-deep), #2c2c2c, #4a3b2a);
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
      color: var(--gold);
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
    color: var(--gold);
  }
}

/* 菜单组：占满剩余高度并均分给组内项。
   用 flex: 1 1 auto 让最后一组吸收剩余空间；group 内菜单项再按 flex:1 1 0 均分，
   使 5 项与 3 项两组的高度都被撑满到屏幕底部（而非堆在顶部留大片空白）。 */
.menu-group {
  margin: 0 32rpx 16rpx;
  background: var(--bg-card);
  border-radius: 24rpx;
  /* 不用 overflow:hidden：它会配合 min-height:0 把低于 min-height 的菜单项裁掉
     （320px 实测项高仅 38px）。首尾圆角改由 menu-item 自身处理 */
  display: flex;
  flex-direction: column;
  /* 各组等比吸收剩余高度：grow 1 + basis 0，总高度恒等于父级可用高度，
     不会超出视口（auto 基准会按内容高度累加，iPad 实测溢出 259px）。
     内容本身有 min-height 保底，窄屏时自然撑开并允许页面滚动。 */
  flex: 1 1 0;
  min-height: 0;

  .menu-item {
    display: flex;
    align-items: center;
    /* 高度由 flex 均分决定：垂直不再用 padding 撑高，仅保留左右内边距 */
    flex: 1 1 0;
    /* 保底热区：88rpx 是相对单位，窄屏会等比缩小（320px 视口下仅 37.5px），
       低于 WCAG 2.5.5 的 44px 物理下限。用 max() 取「88rpx 与 44px 的较大值」，
       宽屏维持 rpx 比例、窄屏兜住物理热区，菜单也因此能占满屏幕。 */
    min-height: max(88rpx, 44px);
    padding: 0 32rpx;
    box-sizing: border-box;
    border-bottom: 1rpx solid var(--border-faint);

    &:last-child {
      border-bottom: none;
    }

    /* 组容器不再 overflow:hidden，圆角改由首尾项承接 */
    &:first-child {
      border-radius: 24rpx 24rpx 0 0;
    }

    &:last-child {
      border-radius: 0 0 24rpx 24rpx;
    }

    &:only-child {
      border-radius: 24rpx;
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
      color: var(--text-inverse);
      line-height: 1;
    }

    .menu-label {
      flex: 1;
      font-size: 28rpx;
      color: var(--text-primary);
      margin-left: 16rpx;
    }

    /* #ccc on #fff ≈1.6:1，提至 #767676（≈4.54:1） */
    .menu-arrow {
      font-size: 32rpx;
      color: var(--text-hint);
    }
  }
}
</style>
