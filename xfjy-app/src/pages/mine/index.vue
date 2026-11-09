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
        <view class="avatar-wrap">
          <smart-image
            lazy-load
            class="avatar"
            :class="{ 'avatar--vip': userInfo?.vip }"
            :src="userInfo?.avatar"
            mode="aspectFill"
            :aria-hidden="true"
          />
        </view>
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
            <!-- VIP 身份徽章：统一走 components/vip-badge.vue，非 VIP 不渲染 -->
            <vip-badge
              :is-vip="userInfo?.vip"
              variant="pill"
            />
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
          :label="counts.eachLoveCount === null ? '互相喜欢，加载中' : `互相喜欢 ${counts.eachLoveCount} 人，查看列表`"
          @tap="goFriends(FRIEND_TYPE.MUTUAL)"
        >
          <text
            class="stat-num skeleton-text"
            :class="{ 'is-loading': counts.eachLoveCount === null }"
          >
            {{ counts.eachLoveCount === null ? '—' : counts.eachLoveCount }}
          </text>
          <text class="stat-label">
            互相喜欢
          </text>
        </a11y-button>
        <a11y-button
          class="stat-item"
          :label="counts.loveCount === null ? '我喜欢，加载中' : `我喜欢 ${counts.loveCount} 人，查看列表`"
          @tap="goFriends(FRIEND_TYPE.FOLLOW)"
        >
          <text
            class="stat-num skeleton-text"
            :class="{ 'is-loading': counts.loveCount === null }"
          >
            {{ counts.loveCount === null ? '—' : counts.loveCount }}
          </text>
          <text class="stat-label">
            我喜欢
          </text>
        </a11y-button>
        <a11y-button
          class="stat-item"
          :label="counts.fanCount === null ? '粉丝，加载中' : `粉丝 ${counts.fanCount} 人，查看列表`"
          @tap="goFriends(FRIEND_TYPE.FANS)"
        >
          <text
            class="stat-num skeleton-text"
            :class="{ 'is-loading': counts.fanCount === null }"
          >
            {{ counts.fanCount === null ? '—' : counts.fanCount }}
          </text>
          <text class="stat-label">
            粉丝
          </text>
        </a11y-button>
      </view>
    </view>

    <!-- VIP 入口：已开通显示尊贵标识 + 描边流光；未开通显示特权列表（呼吸光晕两态都在）。
         核心思路：让用户感受到「开通后有多尊贵」，从而感兴趣开通。 -->
    <view class="vip-card-wrap">
      <a11y-button
        class="vip-card anim-fade-up anim-delay-2"
        :class="{ 'is-vip': userInfo?.vip }"
        :label="userInfo?.vip ? '会员中心' : '开通会员'"
        @tap="goVip"
      >
        <!-- 呼吸光晕独立图层：只动 opacity，避免逐帧重绘 box-shadow -->
        <view
          class="vip-glow"
          aria-hidden="true"
        />
        <view class="vip-left">
          <!-- 金底方徽：统一走 components/vip-badge.vue（营销态，两态都显示，保持布局不变） -->
          <vip-badge
            promo
            variant="emblem"
          />
          <view class="vip-copy">
            <!-- 已开通：显示 VIP 标识 + 到期时间；未开通：显示「立即」标签 -->
            <view class="vip-title-row">
              <text class="vip-title">
                {{ userInfo?.vip ? 'VIP 会员' : '开通会员' }}
              </text>
              <view
                v-if="!userInfo?.vip"
                class="vip-cta-tag"
              >
                立即开通
              </view>
            </view>
            <!-- 未开通：特权列表（图标 + 文字）；已开通：已享特权 + 引导 -->
            <view
              v-if="!userInfo?.vip"
              class="vip-perks"
            >
              <view class="vip-perk">
                <view class="vip-perk-icon i-ph-infinity" />
                <text class="vip-perk-text">
                  无限滑动
                </text>
              </view>
              <view class="vip-perk">
                <view class="vip-perk-icon i-ph-eye" />
                <text class="vip-perk-text">
                  查看谁喜欢我
                </text>
              </view>
              <view class="vip-perk">
                <view class="vip-perk-icon i-ph-crown-simple" />
                <text class="vip-perk-text">
                  专属标识
                </text>
              </view>
              <view class="vip-perk">
                <view class="vip-perk-icon i-ph-star" />
                <text class="vip-perk-text">
                  超级喜欢×5
                </text>
              </view>
            </view>
            <view
              v-else
              class="vip-perks vip-perks--active"
            >
              <view class="vip-perk">
                <view class="vip-perk-icon i-ph-check-circle" />
                <text class="vip-perk-text">
                  无限滑动已解锁
                </text>
              </view>
              <view class="vip-perk">
                <view class="vip-perk-icon i-ph-check-circle" />
                <text class="vip-perk-text">
                  查看谁喜欢我
                </text>
              </view>
            </view>
          </view>
        </view>
        <!-- 箭头用 Phosphor 图标 -->
        <view
          class="vip-chevron i-ph-caret-right"
          aria-hidden="true"
        />
      </a11y-button>
    </view>

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
            color="var(--text-inverse)"
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
import { ref, computed, onMounted, onUnmounted, watch } from "vue";
import { onShow } from "@dcloudio/uni-app";
import MatchPopup from "@/components/match-popup.vue";
import SmartImage from "@/components/smart-image.vue";
import NetworkStatusBar from "@/components/network-status-bar.vue";
import A11yButton from "@/components/a11y-button.vue";
import VipBadge from "@/components/vip-badge.vue";
import { useUserStore } from "@/store/user";
import { getRelationCounts, FRIEND_TYPE, type RelationCounts } from "@/api/mine";

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

/**
 * 关系统计展示值：null = 加载中（骨架占位），避免先闪 0 再跳数字。
 * 与后端 RelationCounts（全 number）分离，接口层不改。
 */
const counts = ref<{
  eachLoveCount: number | null;
  loveCount: number | null;
  fanCount: number | null;
}>({
  eachLoveCount: null,
  loveCount: null,
  fanCount: null,
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
  // getSystemInfoSync 为废弃 API，改用 getWindowInfo（旧运行时无此 API 时取兜底常量）
  const win = uni.getWindowInfo?.();
  statusBarHeight.value =
    win?.statusBarHeight || DEFAULT_STATUS_BAR_HEIGHT_PX;
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
  /* 用 --gradient-brand（同源主色渐变）替代已废弃的 --color-primary-deep 历史紫 */
  background: linear-gradient(180deg, var(--color-primary) 0%, var(--color-primary-text) 100%);
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
    /* circleFloat* 为常驻 infinite 动画，合成层由浏览器自动提升，无需 will-change 常驻占位 */

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

    .avatar-wrap {
      position: relative;
      flex-shrink: 0;
    }

    .avatar {
      width: 128rpx;
      height: 128rpx;
      border-radius: 50%;
      border: 4rpx solid rgba(255, 255, 255, 0.6);
    }

    /* VIP 土豪金边框：粗实色金边 + 金色光晕 */
    .avatar--vip {
      border: 8rpx solid var(--gold);
      box-shadow: 0 0 24rpx rgba(var(--gold-rgb), 0.6), 0 0 48rpx rgba(var(--gold-rgb), 0.3);
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
        color: var(--text-inverse);
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
      /* 热区 ≥44px：文案仅 2 字时宽度不足（「粉丝」实测 30px），补足可点击宽度；
         min-width:0 防止长数字（如 9999）撑破 flex 容器致右侧留白丢失 */
      min-width: 88rpx;
      flex: 1;

      .stat-num {
        font-size: 36rpx;
        font-weight: bold;
        color: var(--text-inverse);

        /* 加载占位：与文字同色的柔光条，避免 0 → 数字的跳变感 */
        &.is-loading {
          min-width: 48rpx;
          border-radius: 8rpx;
          color: transparent;
          background: rgba(255, 255, 255, 0.35);
          animation: skeletonPulse 1.2s ease-in-out infinite;
        }
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

/* VIP 卡片：已开通显示尊贵标识 + 流光边框；未开通显示特权列表 + 呼吸光晕。
   核心思路：让用户感受到「开通后有多尊贵」，从而感兴趣开通。 */
.vip-card-wrap {
  position: relative;
  padding: 16rpx 32rpx;
}

.vip-card {
  display: flex;
  align-items: center;
  justify-content: space-between;
  width: 100%;
  box-sizing: border-box;
  margin: 0;
  padding: 28rpx 32rpx;
  flex-shrink: 0;
  /* 描边用 border-box 背景层实现，边框本身透明：
     前两层是卡内金色渐变（clip: padding-box），盖住环带内部；
     第 3 层是流光、第 4 层是描边底色，只露在 2rpx 环带上。
     分工：.vip-glow 负责「面」的内发光，本组背景层只负责「边」，两者不重叠。 */
  background-image:
    radial-gradient(120% 150% at 0% 0%, rgba(var(--gold-rgb), 0.25) 0%, transparent 58%),
    linear-gradient(135deg, var(--vip-card-grad-1) 0%, var(--vip-card-grad-2) 100%),
    linear-gradient(90deg, transparent 0%, var(--vip-ring-shine) 50%, transparent 100%),
    linear-gradient(var(--vip-ring-base), var(--vip-ring-base));
  background-origin: padding-box, padding-box, border-box, border-box;
  background-clip: padding-box, padding-box, border-box, border-box;
  background-size: 100% 100%, 100% 100%, 34% 100%, 100% 100%;
  background-position: 0 0, 0 0, -40% 0, 0 0;
  background-repeat: no-repeat;
  border-radius: var(--radius-lg);
  border: 2rpx solid transparent;
  box-shadow:
    inset 0 1rpx 0 rgba(255, 255, 255, 0.15),
    inset 0 0 0 1rpx rgba(var(--gold-rgb), 0.12);
  position: relative;
  z-index: 2;
  --vip-glow-color: rgba(var(--gold-rgb), 0.16);
  /* 未开通：描边为静态金色，流光层全透明（不出光） */
  --vip-ring-base: rgba(var(--gold-rgb), 0.8);
  --vip-ring-shine: transparent;
}

/* 内发光层：inset 阴影锁在卡内，不会被 header 的 overflow:hidden 裁出硬边 */
.vip-glow {
  position: absolute;
  top: 0;
  right: 0;
  bottom: 0;
  left: 0;
  z-index: 0;
  border-radius: var(--radius-lg);
  pointer-events: none;
  box-shadow: inset 0 0 28rpx var(--vip-glow-color);
  transition: box-shadow 0.3s ease;
  animation: vipBreathe 3s ease-in-out infinite;
}

/* 已开通：描边更亮、流光启用、光晕更足（呼吸继续，不再完全静止） */
.vip-card.is-vip {
  --vip-ring-base: var(--gold-light);
  --vip-ring-shine: rgba(255, 255, 255, 0.7);
  --vip-glow-color: rgba(var(--gold-rgb), 0.3);
  /* 5s 一圈、匀速线性，慢速流转不闪烁；只改 background-position */
  animation: vipRingSweep 5s linear infinite;
}

/* 描边流光：光带沿 2rpx 环带从左向右匀速流过，5s 一轮。
   只动 background-position（仅环形 2rpx 区域重绘），不碰 box-shadow /
   border-color / text-shadow，也不进入 padding-box 内的卡片内容。 */
@keyframes vipRingSweep {
  from {
    background-position: 0 0, 0 0, -40% 0, 0 0;
  }
  to {
    background-position: 0 0, 0 0, 140% 0, 0 0;
  }
}

/* 呼吸：只动 opacity，走合成器 */
@keyframes vipBreathe {
  0%, 100% {
    opacity: 0.45;
  }
  50% {
    opacity: 1;
  }
}

/* 减弱动效偏好：停掉呼吸与描边流光 */
@media (prefers-reduced-motion: reduce) {
  .vip-glow {
    animation: none;
  }

  .vip-card.is-vip {
    animation: none;
  }
}

.vip-left {
    display: flex;
    align-items: center;
    gap: 20rpx;
    position: relative;
    z-index: 2;
    /* flex 子项显式 min-width:0（AGENTS §7.1）：否则长文案会把徽章挤扁 */
    min-width: 0;
    flex: 1;

    .vip-copy {
      min-width: 0;
      flex: 1;

      .vip-title-row {
        display: flex;
        align-items: center;
        gap: var(--space-sm);

        .vip-title {
          font-size: var(--fs-lg);
          font-weight: 700;
          color: var(--gold-light);
          display: block;
          line-height: 1.25;
        }

        /* 「立即」标签：增强行动感 */
        .vip-cta-tag {
          font-size: var(--fs-xs);
          font-weight: 600;
          color: var(--vip-card-grad-2);
          background: var(--gold-light);
          padding: 0 var(--space-sm);
          border-radius: var(--radius-sm);
          flex-shrink: 0;
          line-height: 1.4;
        }
      }

      /* 特权列表：图标 + 文字，让用户一眼看到价值 */
      .vip-perks {
        display: flex;
        flex-wrap: wrap;
        gap: var(--space-sm);
        margin-top: var(--space-sm);

        .vip-perk {
          display: flex;
          align-items: center;
          gap: var(--space-xs);
          background: rgba(var(--gold-rgb), 0.1);
          padding: 4rpx 12rpx;
          border-radius: var(--radius-sm);

          .vip-perk-icon {
            font-size: var(--fs-sm);
            color: var(--gold-light);
            line-height: 1;
          }

          .vip-perk-text {
            font-size: var(--fs-sm);
            color: var(--gold-light);
            line-height: 1.3;
            white-space: nowrap;
          }
        }

      }

    }
  }

.vip-chevron {
  font-size: var(--fs-base);
  color: rgba(var(--gold-rgb), 0.8);
  position: relative;
  z-index: 2;
  flex-shrink: 0;
  margin-left: var(--space-xs);
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

@keyframes skeletonPulse {
  0%,
  100% {
    opacity: 0.5;
  }
  50% {
    opacity: 0.9;
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
  .deco-circle,
  .stat-num.is-loading {
    animation: none !important;
  }
}
</style>
