<template>
  <view class="mine-page">
    <!-- 断网/恢复全局提示条（fixed 定位自管理显隐，S1） -->
    <network-status-bar />
    <!-- 头部背景区 -->
    <view
      class="header"
      :style="{ paddingTop: statusBarHeight + HEADER_EXTRA_PADDING_PX + 'px' }"
    >
      <!-- 漂浮装饰圆（纯装饰，对读屏隐藏） -->
      <view
        class="deco-circle deco-1"
        aria-hidden="true"
      />
      <view
        class="deco-circle deco-2"
        aria-hidden="true"
      />
      <a11y-button
        class="user-row anim-fade-up"
        label="编辑个人资料"
        @tap="goProfile"
      >
        <smart-image
          lazy-load
          class="avatar"
          :src="userInfo?.avatar"
          mode="aspectFill"
          :aria-hidden="true"
        />
        <view class="user-info">
          <view class="name-row">
            <text class="nickname">
              {{ userInfo?.nickname || '点击完善资料' }}
            </text>
            <view
              v-if="userInfo?.verified"
              class="verified"
            >
              已认证
            </view>
            <view
              v-if="userInfo?.vip"
              class="vip-badge"
            >
              VIP
            </view>
          </view>
          <text class="uid">
            ID: {{ userInfo?.id || '—' }}
          </text>
          <text class="meta">
            {{ profileMeta }}
          </text>
          <text
            v-if="userInfo?.signature"
            class="signature ellipsis"
          >
            {{ userInfo.signature }}
          </text>
          <!-- 资料未完善：给明确引导而非显示空值"破相" -->
          <text
            v-else
            class="profile-guide"
          >
            完善资料，让更多人遇见你 ›
          </text>
        </view>
        <!-- 箭头为装饰符号，避免读屏逐字朗读 -->
        <text
          class="edit-arrow"
          aria-hidden="true"
        >
          ›
        </text>
      </a11y-button>

      <!-- 数据栏（与后端 CountsVo 对齐：eachLoveCount/loveCount/fanCount） -->
      <view class="stats-row anim-fade-up anim-delay-1">
        <a11y-button
          class="stat-item"
          :label="`互相喜欢 ${counts.eachLoveCount} 人，查看列表`"
          @tap="goFriends(FRIEND_TYPE.MUTUAL)"
        >
          <text class="stat-num">
            {{ counts.eachLoveCount }}
          </text>
          <text class="stat-label">
            互相喜欢
          </text>
        </a11y-button>
        <a11y-button
          class="stat-item"
          :label="`我喜欢 ${counts.loveCount} 人，查看列表`"
          @tap="goFriends(FRIEND_TYPE.FOLLOW)"
        >
          <text class="stat-num">
            {{ counts.loveCount }}
          </text>
          <text class="stat-label">
            我喜欢
          </text>
        </a11y-button>
        <a11y-button
          class="stat-item"
          :label="`粉丝 ${counts.fanCount} 人，查看列表`"
          @tap="goFriends(FRIEND_TYPE.FANS)"
        >
          <text class="stat-num">
            {{ counts.fanCount }}
          </text>
          <text class="stat-label">
            粉丝
          </text>
        </a11y-button>
      </view>
    </view>

    <!-- VIP 入口 -->
    <a11y-button
      class="vip-card anim-fade-up anim-delay-2"
      :label="userInfo?.vip ? '会员中心' : '开通会员'"
      @tap="goVip"
    >
      <view class="vip-left">
        <uni-icons
          class="vip-icon"
          type="vip"
          size="24"
        />
        <view>
          <text class="vip-title">
            {{ userInfo?.vip ? '会员中心' : '开通会员' }}
          </text>
          <text class="vip-desc">
            {{ userInfo?.vip ? '查看你的会员权益' : '无限滑动 · 查看谁喜欢我 · 专属标识' }}
          </text>
        </view>
      </view>
      <text
        class="vip-arrow"
        aria-hidden="true"
      >
        ›
      </text>
    </a11y-button>

    <!-- 功能列表（表驱动渲染）：行高固定，不随组内项数与视口宽度变化 -->
    <view
      v-for="(group, groupIndex) in menuGroups"
      :key="groupIndex"
      class="menu-group anim-fade-up"
      :class="groupIndex ? 'anim-delay-4' : 'anim-delay-3'"
    >
      <a11y-button
        v-for="item in group"
        :key="item.label"
        class="menu-item"
        :label="item.label"
        @tap="item.action()"
      >
        <view
          class="menu-icon-bg"
          :style="{ background: item.color }"
        >
          <uni-icons
            class="menu-icon"
            :type="item.uniIcon"
            size="24"
            color="#fff"
          />
        </view>
        <text class="menu-label">
          {{ item.label }}
        </text>
        <text
          class="menu-arrow"
          aria-hidden="true"
        >
          ›
        </text>
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
import SmartImage from "@/components/smart-image.vue";
import NetworkStatusBar from "@/components/network-status-bar.vue";
import A11yButton from "@/components/a11y-button.vue";
import { useUserStore } from "@/store/user";
import { getRelationCounts, FRIEND_TYPE, type RelationCounts } from "@/api/mine";
import { resolveAvatar } from "@/utils/avatar";

const userStore = useUserStore();
const userInfo = computed(() => userStore.userInfo);
const statusBarHeight = ref(0);

/**
 * 资料摘要：资料完整时显示"城市 · 年龄岁"；缺字段时不再显示
 * "未知城市 · —岁"这类破相文案，改为引导去完善资料。
 */
const profileMeta = computed(() => {
  const u = userInfo.value;
  const city = u?.city;
  const age = u?.age;
  if (city && age) return `${city} · ${age}岁`;
  if (city) return `${city} · 点击补充年龄`;
  if (age) return `${age}岁 · 点击补充城市`;
  return "点击完善城市与年龄，提升匹配质量";
});

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

  /* 漂浮装饰圆：独立轨迹（浮动 + 缩放 + 呼吸），节奏错开 */
  .deco-circle {
    position: absolute;
    border-radius: 50%;
    background: rgba(255, 255, 255, 0.1);
    pointer-events: none;
    will-change: transform, opacity;

    &.deco-1 {
      width: 240rpx;
      height: 240rpx;
      top: 20rpx;
      right: -60rpx;
      animation: circleFloatA 2.6s ease-in-out infinite;
    }

    &.deco-2 {
      width: 160rpx;
      height: 160rpx;
      top: 120rpx;
      left: -40rpx;
      animation: circleFloatB 3s ease-in-out infinite;
      animation-delay: 0.6s;
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

      /* 资料未完善时的引导（替代空值破相） */
      .profile-guide {
        font-size: 22rpx;
        color: #fff;
        margin-top: 10rpx;
        display: inline-block;
        padding: 6rpx 18rpx;
        border-radius: 20rpx;
        background: rgba(255, 255, 255, 0.22);
        border: 1rpx solid rgba(255, 255, 255, 0.45);
        font-weight: 600;
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
      /* 热区 ≥44px：文案仅 2 字时宽度不足（「粉丝」实测 30px），补足可点击宽度 */
      min-width: 88rpx;

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

/* 菜单组：卡片容器，纵向排布，高度由行数 × 行高决定。
   不做剩余空间分配：390px 视口下头部+VIP 卡已占 513px，留给菜单的 281px
   不够 8 × 48px 的紧凑行高（需 384px）——「视觉紧凑」与「占满屏幕」在小屏
   是硬冲突，此处优先紧凑（信息密度），剩余空间保留为底部留白。 */
.menu-group {
  margin: 0 32rpx 16rpx;
  background: var(--bg-card);
  border-radius: 24rpx;
  /* 不用 overflow:hidden：它会配合 min-height:0 把低于 min-height 的菜单项裁掉。
     首尾圆角改由 menu-item 自身处理 */
  display: flex;
  flex-direction: column;

  .menu-item {
    display: flex;
    align-items: center;
    /* 视觉紧凑度 = 内容高度（图标 64rpx + 文字行高），约 80rpx。
       热区由 min-height 兜住（≥48px 物理下限，WCAG 2.5.5）。
       二者解耦后「看得见的小、摸得着的大」：撑出来的空白不承载视觉元素。 */
    min-height: var(--row-h-touch-md);
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

@keyframes circleFloatA {
  0%,
  100% {
    transform: translate(0, 0) scale(1);
    opacity: 1;
  }
  50% {
    transform: translate(-14rpx, 24rpx) scale(1.08);
    opacity: 0.7;
  }
}

@keyframes circleFloatB {
  0%,
  100% {
    transform: translate(0, 0) scale(1);
    opacity: 1;
  }
  50% {
    transform: translate(16rpx, -20rpx) scale(1.12);
    opacity: 0.65;
  }
}

/* 减弱动效偏好下停用页面独有的装饰动画（入场动画由 App.vue 全局收口） */
@media (prefers-reduced-motion: reduce) {
  .deco-circle {
    animation: none !important;
  }
}
</style>
