<template>
  <view class="match-page">
    <!-- 顶部栏 -->
    <view class="top-bar" :style="{ paddingTop: statusBarHeight + 'px' }">
      <a11y-button class="city" label="选择城市" @tap="openCityPicker">
        <text class="city-name">{{ currentCity }}</text>
        <text class="arrow" aria-hidden="true">▾</text>
      </a11y-button>
      <a11y-button class="locate-btn" label="重新定位" @tap="relocate">
        <text class="locate-ico" aria-hidden="true">◎</text>
        <text class="locate-text">定位</text>
      </a11y-button>
      <a11y-button class="filter-btn" label="查看附近的人" @tap="goNearby">
        <text>附近</text>
      </a11y-button>
    </view>

    <!-- 卡片区域 -->
    <view class="card-area">
      <!-- 骨架卡片（首屏加载中）：与真实卡片同几何（全屏铺底单卡） -->
      <skeleton v-if="cardList.length === 0 && loading" variant="match" />

      <!-- 空状态：仅加载完成且无卡片时显示（否则会与骨架/卡片叠显） -->
      <empty-state
        v-if="cardList.length === 0 && !loading"
        :title="exhausted ? '推荐已看完' : '暂无推荐'"
        :desc="exhausted ? '今日候选都已滑过，点击重新加载看看有没有新面孔' : '稍后再来看看，缘分不期而至'"
      >
        <a11y-button
          v-if="exhausted"
          label="重新加载"
          variant="primary"
          @click="handleReload"
        />
      </empty-state>

      <!-- 卡片堆叠（从后往前渲染） -->
      <view
        v-for="(card, index) in visibleCards"
        :key="card.id"
        class="card-wrapper"
        :style="getCardStyle(index)"
        @touchstart="onTouchStart($event, index)"
        @touchmove="onTouchMove($event, index)"
        @touchend="onTouchEnd($event, index)"
        @tap="onCardTap(index)"
        :aria-label="`推荐卡片：${card.nickname}，${card.age}岁，轻点查看完整资料`"
      >
        <!-- 主图 -->
        <image lazy-load
          class="card-image"
          :src="card.avatar"
          mode="aspectFill"
          :aria-label="`${card.nickname} 的照片`"
        />
        <!-- 底部渐变遮罩（纯装饰） -->
        <view class="card-mask" aria-hidden="true" />
        <!-- 用户信息 -->
        <view class="card-info">
          <view class="name-row">
            <text class="nickname">{{ card.nickname }}</text>
            <text class="age">{{ card.age }}</text>
          </view>
          <!-- 缘分值进度条：告知读屏当前值而非仅视觉呈现 -->
          <view v-if="card.fateValue" class="fate-row">
            <text class="fate-label">缘分值</text>
            <view
              class="fate-bar"
              role="progressbar"
              aria-label="缘分值"
              :aria-valuenow="Math.min(card.fateValue, 100)"
              aria-valuemin="0"
              aria-valuemax="100"
            >
              <view
                class="fate-fill"
                :style="{ width: Math.min(card.fateValue, 100) + '%' }"
              />
            </view>
            <text class="fate-value">{{ card.fateValue }}</text>
          </view>
          <!-- 决策信息：城市 / 学历 / 行业（有则显示，避免用户在缺少判断依据时做决定） -->
          <view v-if="cardMeta(card)" class="meta-row">
            <text class="meta-text">{{ cardMeta(card) }}</text>
          </view>
          <!-- 标签 -->
          <view v-if="card.tags && card.tags.length" class="tags">
            <text v-for="tag in card.tags.slice(0, MAX_TAG_COUNT)" :key="tag" class="tag glass">{{ tag }}</text>
          </view>
          <!-- 照片数 + 查看资料入口（明确可点击，解决"卡片点不动"） -->
          <a11y-button
            class="detail-entry btn-feedback"
            :label="`查看 ${card.nickname} 的完整资料`"
            @tap.stop="onDetailEntryTap(card.id)"
          >
            <text class="detail-photo" aria-hidden="true">▣</text>
            <text class="detail-text">{{ photoCount(card) > 0 ? `${photoCount(card)} 张照片 · ` : "" }}查看完整资料</text>
            <text class="detail-arrow" aria-hidden="true">›</text>
          </a11y-button>
        </view>
        <!-- 滑动提示（喜欢 / 不喜欢）：随手势显示的视觉反馈，对读屏隐藏 -->
        <view v-if="index === 0" class="swipe-hint like" :style="{ opacity: likeOpacity }" aria-hidden="true">
          喜欢
        </view>
        <view v-if="index === 0" class="swipe-hint nope" :style="{ opacity: nopeOpacity }" aria-hidden="true">
          不喜欢
        </view>
      </view>
    </view>

    <!-- 底部操作栏（探探式五键：回看 / 不喜欢 / 超级喜欢 / 心动 / 曝光提升） -->
    <view class="action-bar safe-area-bottom">
      <a11y-button class="action-btn rewind btn-feedback" label="回看上一张" @tap="handleRewind">
        <text aria-hidden="true">↺</text>
      </a11y-button>
      <a11y-button class="action-btn dislike btn-feedback" label="不喜欢" @tap="handleDislike">
        <uni-icons type="close" size="24" color="#ff3b30"></uni-icons>
      </a11y-button>
      <a11y-button class="action-btn superlike btn-feedback" label="超级喜欢" @tap="handleSuperLike">
        <uni-icons type="star-filled" size="22" color="#4dabf7"></uni-icons>
      </a11y-button>
      <a11y-button class="action-btn like btn-feedback" label="心动" @tap="handleLike">
        <uni-icons type="heart-filled" size="24" color="#4cd964"></uni-icons>
      </a11y-button>
      <a11y-button class="action-btn boost btn-feedback" label="曝光提升" @tap="handleBoost">
        <text class="boost-ico" aria-hidden="true">⚡</text>
        <text class="boost-label">曝光</text>
      </a11y-button>
    </view>

    <!-- 匹配成功弹窗（全局组件，由 match store 驱动；同步匹配与 IM 匹配消息共用） -->
    <MatchPopup />

    <!-- 城市选择底部选择器 -->
    <a11y-picker
      :visible="cityPickerVisible"
      title="选择城市"
      :options="CITY_OPTIONS"
      :model-value="currentCity"
      :show-input="true"
      input-placeholder="请输入城市名称"
      @update:visible="cityPickerVisible = $event"
      @confirm="onCityConfirm"
    />
  </view>
</template>

<script setup lang="ts">
import { ref, computed, onMounted, onUnmounted } from "vue";
import EmptyState from "@/components/empty-state.vue";
import Skeleton from "@/components/skeleton.vue";
import { onShow, onShareAppMessage } from "@dcloudio/uni-app";
import { useMatchStore } from "@/store/match";
import { useSystemStore } from "@/store/system";
import { getCards, type TodayBest } from "@/api/match";
import { checkBenefit, recordBenefitUsage } from "@/api/mine";
import { useUserStore } from "@/store/user";
import MatchPopup from "@/components/match-popup.vue";
import A11yButton from "@/components/a11y-button.vue";
import A11yPicker from "@/components/a11y-picker.vue";
import { info, fail } from "@/utils/ux";

const matchStore = useMatchStore();
const userStore = useUserStore();

/** ===== 常量定义（阿里规范：不允许任何魔法值直接出现在代码中） ===== */

/** 卡片堆叠可见数量 */
const VISIBLE_CARD_COUNT = 3;
/** 剩余卡片不足此数时触发预加载 */
const MIN_REMAINING_CARDS = 3;
/** 滑动出栈阈值（rpx），超过则判定为喜欢/不喜欢 */
const SWIPE_THRESHOLD_RPX = 120;
/** 卡片飞出屏幕的水平距离（rpx） */
const FLY_DISTANCE_X_RPX = 1000;
/** 超级喜欢飞出的垂直距离（rpx，向上） */
const FLY_DISTANCE_Y_SUPER_RPX = -800;
/** 喜欢/不喜欢飞出的垂直距离（rpx，微向下） */
const FLY_DISTANCE_Y_NORMAL_RPX = 50;
/** 卡片移除动画延迟（ms），需与 CSS 过渡时间一致 */
const CARD_REMOVE_DELAY_MS = 300;
/** 卡片过渡动画时长 */
const CARD_TRANSITION = "transform 0.3s ease-out";
/** 卡片堆叠基础层级 */
const CARD_BASE_Z_INDEX = 30;
/** 滑动旋转角度除数（dragX / 此值 = 旋转角度） */
const ROTATE_DIVISOR = 20;
/** 滑动提示透明度计算除数 */
const OPACITY_DIVISOR = 100;
/** 标签最大展示数量 */
const MAX_TAG_COUNT = 3;
/** 轻点判定阈值（rpx）：位移超过此值视为滑动，不再触发"轻点看资料" */
const TAP_SLOP_RPX = 16;
/** 详情跳转路径前缀 */
const DETAIL_PAGE_PATH = "/pages/match/detail?id=";
/** rpx 换算基准（750rpx = 屏幕宽度） */
const RPX_BASE_WIDTH = 750;
/** 默认屏幕宽度兜底值（px） */
const DEFAULT_WINDOW_WIDTH_PX = 375;
/** 默认状态栏高度兜底值（px） */
const DEFAULT_STATUS_BAR_HEIGHT_PX = 20;

// 状态栏高度（自定义导航栏适配）
const statusBarHeight = ref(0);
const systemStore = useSystemStore();
/** 顶部城市：读取 system store（本地持久化，默认北京）；
 *  坐标→城市名的逆地理编码需地图服务 key，接入后改为定位结果 */
const currentCity = computed(() => systemStore.currentCity);

/** 城市选项列表（支持手动输入） */
const CITY_OPTIONS = [
  { value: "北京", label: "北京" },
  { value: "上海", label: "上海" },
  { value: "广州", label: "广州" },
  { value: "深圳", label: "深圳" },
  { value: "杭州", label: "杭州" },
  { value: "成都", label: "成都" },
  { value: "武汉", label: "武汉" },
  { value: "西安", label: "西安" },
  { value: "南京", label: "南京" },
  { value: "重庆", label: "重庆" },
  { value: "苏州", label: "苏州" },
  { value: "天津", label: "天津" },
  { value: "长沙", label: "长沙" },
  { value: "郑州", label: "郑州" },
  { value: "青岛", label: "青岛" },
  { value: "大连", label: "大连" },
  { value: "厦门", label: "厦门" },
  { value: "合肥", label: "合肥" },
  { value: "昆明", label: "昆明" },
  { value: "珠海", label: "珠海" },
] as const;

/** 城市选择器状态 */
const cityPickerVisible = ref(false);

function openCityPicker(): void {
  cityPickerVisible.value = true;
}

/** 重新定位：清除手动选择标记，触发自动定位覆盖当前城市 */
function relocate(): void {
  uni.removeStorageSync(CITY_MANUAL_KEY);
  syncLocation();
  info("正在重新定位...");
}

/** 城市选择确认：更新 system store（持久化）+ 标记为手动选择，后续定位不再自动覆盖 */
function onCityConfirm(city: string): void {
  systemStore.setCity(city);
  uni.setStorageSync(CITY_MANUAL_KEY, true);
  info(`已切换到 ${city}`);
}

/**
 * 主要城市坐标表（WGS84 近似经纬度），用于根据定位坐标自动匹配最近城市。
 * 覆盖一二线及强三线，非列表内城市保持用户已选值或默认北京。
 */
const CITY_COORDS: readonly { name: string; lng: number; lat: number }[] = [
  { name: "北京", lng: 116.407, lat: 39.904 },
  { name: "上海", lng: 121.473, lat: 31.230 },
  { name: "广州", lng: 113.264, lat: 23.129 },
  { name: "深圳", lng: 114.057, lat: 22.543 },
  { name: "杭州", lng: 120.155, lat: 30.274 },
  { name: "成都", lng: 104.066, lat: 30.572 },
  { name: "武汉", lng: 114.305, lat: 30.593 },
  { name: "西安", lng: 108.948, lat: 34.263 },
  { name: "南京", lng: 118.796, lat: 32.060 },
  { name: "重庆", lng: 106.551, lat: 29.563 },
  { name: "苏州", lng: 120.585, lat: 31.299 },
  { name: "天津", lng: 117.201, lat: 39.084 },
  { name: "长沙", lng: 112.938, lat: 28.228 },
  { name: "郑州", lng: 113.625, lat: 34.747 },
  { name: "青岛", lng: 120.382, lat: 36.067 },
  { name: "大连", lng: 121.614, lat: 38.914 },
  { name: "厦门", lng: 118.089, lat: 24.479 },
  { name: "合肥", lng: 117.227, lat: 31.820 },
  { name: "昆明", lng: 102.833, lat: 24.880 },
  { name: "珠海", lng: 113.576, lat: 22.271 },
  { name: "福州", lng: 119.296, lat: 26.074 },
  { name: "济南", lng: 117.120, lat: 36.651 },
  { name: "哈尔滨", lng: 126.534, lat: 45.803 },
  { name: "沈阳", lng: 123.431, lat: 41.805 },
  { name: "长春", lng: 125.324, lat: 43.886 },
  { name: "石家庄", lng: 114.514, lat: 38.042 },
  { name: "太原", lng: 112.549, lat: 37.857 },
  { name: "南昌", lng: 115.858, lat: 28.683 },
  { name: "贵阳", lng: 106.630, lat: 26.647 },
  { name: "南宁", lng: 108.366, lat: 22.817 },
  { name: "兰州", lng: 103.834, lat: 36.061 },
  { name: "海口", lng: 110.199, lat: 20.044 },
  { name: "乌鲁木齐", lng: 87.617, lat: 43.792 },
  { name: "呼和浩特", lng: 111.751, lat: 40.841 },
  { name: "银川", lng: 106.230, lat: 38.487 },
  { name: "西宁", lng: 101.778, lat: 36.617 },
  { name: "拉萨", lng: 91.132, lat: 29.660 },
  { name: "无锡", lng: 120.311, lat: 31.491 },
  { name: "佛山", lng: 113.122, lat: 23.029 },
  { name: "东莞", lng: 113.751, lat: 23.021 },
  { name: "宁波", lng: 121.550, lat: 29.874 },
];

/** 城市匹配半径（km）：在此半径内认为定位到该城市，超出则不自动切换 */
const CITY_MATCH_RADIUS_KM = 80;

/** 本地存储键：是否已手动选择城市 */
const CITY_MANUAL_KEY = "xfjy_city_manual";

/** 计算两个坐标点之间的球面距离（Haversine 公式，单位 km） */
function haversineKm(lng1: number, lat1: number, lng2: number, lat2: number): number {
  const R = 6371;
  const dLat = ((lat2 - lat1) * Math.PI) / 180;
  const dLng = ((lng2 - lng1) * Math.PI) / 180;
  const a =
    Math.sin(dLat / 2) * Math.sin(dLat / 2) +
    Math.cos((lat1 * Math.PI) / 180) *
      Math.cos((lat2 * Math.PI) / 180) *
      Math.sin(dLng / 2) * Math.sin(dLng / 2);
  return R * 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
}

/** 根据坐标匹配最近的城市名；不在任何城市半径内返回 null */
function matchNearestCity(lng: number, lat: number): string | null {
  let nearest: { name: string; dist: number } | null = null;
  for (const city of CITY_COORDS) {
    const dist = haversineKm(lng, lat, city.lng, city.lat);
    if (!nearest || dist < nearest.dist) {
      nearest = { name: city.name, dist };
    }
  }
  return nearest && nearest.dist <= CITY_MATCH_RADIUS_KM ? nearest.name : null;
}

/**
 * 定位同步：获取当前坐标 → 自动匹配最近城市（仅未手动选择时）→ 上报后端（写入 ES）。
 * 首次进入自动定位城市；用户手动选择城市后不再自动覆盖。
 */
function syncLocation(): void {
  uni.getLocation({
    type: "wgs84",
    success: (res) => {
      // 自动定位城市（仅未手动选择过时覆盖）
      const isManual = uni.getStorageSync(CITY_MANUAL_KEY);
      if (!isManual) {
        const matched = matchNearestCity(res.longitude, res.latitude);
        if (matched && matched !== systemStore.currentCity) {
          systemStore.setCity(matched);
        }
      }

      systemStore
        .updateLocation({
          longitude: res.longitude,
          latitude: res.latitude,
          addrStr: `${res.longitude.toFixed(6)},${res.latitude.toFixed(6)}`,
        })
        .catch(() => {});
    },
  });
}

// 卡片数据
const cardList = ref<TodayBest[]>([]);
const loading = ref(false);
/** 服务端候选已耗尽（拉取返回空）：为 true 时展示重试入口，避免滑到底后静默卡死 */
const exhausted = ref(false);

// 滑动手势状态
const dragX = ref(0);
const dragY = ref(0);
const isDragging = ref(false);
const touchStartX = ref(0);
const touchStartY = ref(0);
const currentCardIndex = ref(0); // 当前顶部卡片在 cardList 中的索引


/** 可见卡片（最多显示 VISIBLE_CARD_COUNT 张，从后往前堆叠渲染） */
const visibleCards = computed(() => {
  return cardList.value.slice(
    currentCardIndex.value,
    currentCardIndex.value + VISIBLE_CARD_COUNT
  );
});

/** 滑动提示透明度（跟随水平拖拽距离渐显） */
const likeOpacity = computed(() =>
  Math.min(Math.max(dragX.value / OPACITY_DIVISOR, 0), 1)
);
const nopeOpacity = computed(() =>
  Math.min(Math.max(-dragX.value / OPACITY_DIVISOR, 0), 1)
);

/** 计算卡片堆叠样式：顶部卡片跟随手势；后置卡片全屏垫底，滑走即直接露出（无缩放动画） */
function getCardStyle(index: number) {
  if (index === 0) {
    // 顶部卡片跟随手势位移并微旋转
    return {
      transform: `translate(${dragX.value}rpx, ${dragY.value}rpx) rotate(${dragX.value / ROTATE_DIVISOR}deg)`,
      zIndex: CARD_BASE_Z_INDEX,
      transition: isDragging.value ? "none" : CARD_TRANSITION,
    };
  }
  return {
    zIndex: CARD_BASE_Z_INDEX - index,
  };
}

// ===== 手势处理 =====
/**
 * touchmove 合帧节流：拖拽位移先攒进 pending，requestAnimationFrame 每帧只提交一次，
 * 避免高频事件下 Vue 逐事件重排渲染造成的滑动卡顿；
 * 部分小程序运行时无 requestAnimationFrame，降级为直接更新
 */
const supportsRaf = typeof requestAnimationFrame === "function";
let dragRafId = 0;
let pendingDragX = 0;
let pendingDragY = 0;

/** 本次手势是否发生了明显拖动（用于区分"轻点看资料"与"滑动"） */
let gestureMoved = false;
/** 滑动出栈后抑制紧随其后的 tap，避免误跳详情 */
let suppressCardTap = false;
/** 上次跳转详情的时间戳：防止 tap 与 click 叠加导致重复入栈 */
let lastDetailNavAt = 0;

/** 城市 / 学历 / 行业：有则拼成一行决策信息，无则返回空 */
function cardMeta(card: TodayBest): string {
  return [card.city, card.edu, card.industry].filter(Boolean).join(" · ");
}

/** 照片墙数量（photoAlbum 为 JSON 数组字符串，解析失败按 0 处理） */
function photoCount(card: TodayBest): number {
  if (!card.photoAlbum) return 0;
  try {
    const list = JSON.parse(card.photoAlbum);
    return Array.isArray(list) ? list.length : 0;
  } catch {
    return 0;
  }
}

/** 进入用户资料详情页（页面已存在，此前未从卡片可达） */
function openDetail(id: number | string) {
  const now = Date.now();
  if (now - lastDetailNavAt < 400) return;
  lastDetailNavAt = now;
  uni.navigateTo({ url: DETAIL_PAGE_PATH + id });
}

/** 轻点卡片看完整资料：滑动手势 / 出栈动画期间不触发 */
function onCardTap(index: number) {
  if (suppressCardTap) {
    suppressCardTap = false;
    return;
  }
  if (index !== 0 || gestureMoved || swiping.value) return;
  const card = visibleCards.value[0];
  if (card) openDetail(card.id);
}

/** 卡片底部「查看完整资料」入口（阻止冒泡，避免与卡片轻点重复入栈） */
function onDetailEntryTap(id: number) {
  if (gestureMoved || swiping.value) return;
  openDetail(id);
}

/** 触摸开始：记录起始坐标，仅响应顶部卡片 */
function onTouchStart(e: TouchEvent, index: number) {
  if (index !== 0) return;
  isDragging.value = true;
  gestureMoved = false;
  touchStartX.value = e.touches[0].clientX;
  touchStartY.value = e.touches[0].clientY;
  pendingDragX = 0;
  pendingDragY = 0;
}

/** 触摸移动：换算 px → rpx 后攒入 pending，帧末统一驱动卡片位移 */
function onTouchMove(e: TouchEvent, index: number) {
  if (index !== 0 || !isDragging.value) return;
  // rpx 换算比例已在 onMounted 缓存（750rpx / 屏幕宽度px），避免高频同步取系统信息
  pendingDragX = (e.touches[0].clientX - touchStartX.value) * rpxRatio.value;
  pendingDragY = (e.touches[0].clientY - touchStartY.value) * rpxRatio.value;
  if (Math.abs(pendingDragX) > TAP_SLOP_RPX || Math.abs(pendingDragY) > TAP_SLOP_RPX) {
    gestureMoved = true;
  }
  if (!supportsRaf) {
    dragX.value = pendingDragX;
    dragY.value = pendingDragY;
    return;
  }
  if (dragRafId) return;
  dragRafId = requestAnimationFrame(() => {
    dragRafId = 0;
    dragX.value = pendingDragX;
    dragY.value = pendingDragY;
  });
}

/** 触摸结束：超过阈值判定滑出，否则回弹复位 */
function onTouchEnd(_e: TouchEvent, index: number) {
  if (index !== 0) return;
  isDragging.value = false;
  // 提交最后一帧的 pending 位移，保证阈值判定用的是松手时刻的真实位置
  if (dragRafId) {
    cancelAnimationFrame(dragRafId);
    dragRafId = 0;
  }
  dragX.value = pendingDragX;
  dragY.value = pendingDragY;
  if (dragX.value > SWIPE_THRESHOLD_RPX) {
    suppressCardTap = true;
    swipeOut("like");
  } else if (dragX.value < -SWIPE_THRESHOLD_RPX) {
    suppressCardTap = true;
    swipeOut("dislike");
  } else {
    // 未达阈值，回弹复位
    dragX.value = 0;
    dragY.value = 0;
  }
}

// ===== 滑动出栈 =====
/** 滑出动画/上报进行中（防连点重复上报同一张卡片） */
const swiping = ref(false);
/** 待执行的卡片移除定时器（页面卸载时清理） */
let cardRemoveTimer: ReturnType<typeof setTimeout> | null = null;
/** 上一张已完成上报的卡片（回看撤销用） */
const lastSwipe = ref<{ id: number; action: "like" | "dislike" | "superlike" } | null>(null);

/** 卡片滑出并上报操作，互相喜欢时弹出匹配成功弹窗 */
async function swipeOut(action: "like" | "dislike" | "superlike") {
  const card = visibleCards.value[0];
  if (!card) return;
  if (swiping.value) return;
  swiping.value = true;

  // 检查每日免费滑动次数：探探口径下左滑（跳过）不计次，不应被额度拦截；
  // 只有右滑（like/superlike）才消耗额度
  if (action !== "dislike" && !matchStore.canSwipe()) {
    info("今日免费次数已用完，开通VIP无限滑");
    dragX.value = 0;
    dragY.value = 0;
    swiping.value = false;
    return;
  }

  // 驱动飞出动画：不喜欢向左，其余向右；超级喜欢向上飞
  dragX.value =
    action === "dislike" ? -FLY_DISTANCE_X_RPX : FLY_DISTANCE_X_RPX;
  dragY.value =
    action === "superlike"
      ? FLY_DISTANCE_Y_SUPER_RPX
      : FLY_DISTANCE_Y_NORMAL_RPX;

  // 上报滑动，互相喜欢时弹出匹配成功窗
  if (action === "dislike") {
    // 左滑「跳过」不消耗额度、不改变推荐结果，只是异步清理可能存在的喜欢关系，
    // 属于可补偿操作：乐观出栈 + 后台上报。若像 like 一样 await 后再决定，
    // 后端超时/500 会把卡片弹回，用户会反复卡在同一张卡上（实测过的高频痛点）。
    void matchStore.doSwipe(card.id, "dislike").catch(() => {
      // 静默降级：跳过纯本地行为，服务端关系未清理不影响当前体验
    });
  } else {
    try {
      const matched = await matchStore.doSwipe(card.id, action);
      if (matched) {
        matchStore.showMatchPopup({
          userId: card.id,
          nickname: card.nickname,
          avatar: card.avatar,
        });
      }
      // 超级喜欢为 VIP 权益：先上报成功再扣次数（与 detail 页一致，避免失败白扣）
      if (action === "superlike") {
        const ok = await recordBenefitUsage("super_like");
        if (!ok) {
          info("今日超级喜欢次数已用完");
        }
      }
    } catch {
      // like/superlike 涉及真实关系与额度，上报失败必须回弹，避免后端未记录却把推荐划走
      dragX.value = 0;
      dragY.value = 0;
      swiping.value = false;
      fail("操作失败，请重试");
      return;
    }
  }

  // 延迟移除卡片（等待飞出动画完成）
  cardRemoveTimer = setTimeout(() => {
    cardRemoveTimer = null;
    // 记录刚出栈的卡片供「回看」撤销
    lastSwipe.value = { id: card.id, action };
    currentCardIndex.value++;
    dragX.value = 0;
    dragY.value = 0;
    swiping.value = false;
    // 剩余卡片不足时预加载更多
    if (cardList.value.length - currentCardIndex.value < MIN_REMAINING_CARDS) {
      loadMoreCards();
    }
  }, CARD_REMOVE_DELAY_MS);
}

// ===== 按钮操作 =====
function handleLike() {
  dragX.value = 0;
  swipeOut("like");
}
function handleDislike() {
  dragX.value = 0;
  swipeOut("dislike");
}
/**
 * 回看（探探式 ↺）：撤销上一张的上报（store.undoSwipe 回滚后端并返还次数），
 * 再把卡片放回队首；无历史记录时提示。
 */
async function handleRewind(): Promise<void> {
  const last = lastSwipe.value;
  if (swiping.value || !last) {
    info("没有可回看的卡片");
    return;
  }
  // 每日免费回看次数上限（store 有 rewindCount，之前未接入导致限制形同虚设）
  if (!matchStore.canRewind()) {
    info("今日回看次数已用完");
    return;
  }
  try {
    // 先回滚后端再动队列：失败时保持现状，避免前后端状态不一致
    await matchStore.undoSwipe(last.id, last.action);
    if (cardRemoveTimer) {
      clearTimeout(cardRemoveTimer);
      cardRemoveTimer = null;
    }
    swiping.value = false;
    currentCardIndex.value = Math.max(0, currentCardIndex.value - 1);
    dragX.value = 0;
    dragY.value = 0;
    lastSwipe.value = null;
    // 计入今日回看次数（仅回滚成功后累加，失败重试不消耗）
    matchStore.useRewind();
    info("已回看上一张");
  } catch {
    fail("回看失败，请重试");
  }
}
/**
 * 超级喜欢（探探式 ★，VIP1 每日 5 次）：权益校验通过后向上飞出上报，
 * 次数扣减在 swipeOut 上报成功后执行（先上报后扣，避免失败白扣）。
 */
async function handleSuperLike(): Promise<void> {
  if (swiping.value) return;
  try {
    const benefit = await checkBenefit("super_like");
    if (!benefit.hasBenefit) {
      info("超级喜欢需开通VIP");
      setTimeout(() => uni.navigateTo({ url: "/pages/mine/vip" }), 600);
      return;
    }
    if (benefit.remainingToday === 0) {
      info("今日超级喜欢次数已用完");
      return;
    }
    dragX.value = 0;
    swipeOut("superlike");
  } catch {
    // 请求失败由 request 层统一 toast
  }
}
function handleBoost() {
  info("曝光提升（VIP专属）");
}

// ===== 跳转 =====
function goNearby() {
  uni.navigateTo({ url: "/pages/match/nearby" });
}

// ===== 数据加载 =====
async function loadMoreCards() {
  if (loading.value) return false;
  loading.value = true;
  try {
    const newCards = await getCards();
    if (newCards && newCards.length > 0) {
      // 按 id 去重后追加：分页间隙服务端可能重复下发同一用户
      const knownIds = new Set(cardList.value.map((card) => card.id));
      cardList.value.push(...newCards.filter((card) => !knownIds.has(card.id)));
      exhausted.value = false;
      return true;
    }
    // 服务端已无新候选：标记耗尽，避免静默卡死
    exhausted.value = true;
    return false;
  } catch {
    // 请求失败由 request 层统一 toast；保留既有 exhausted 态，避免重试入口被吞掉
    return false;
  } finally {
    loading.value = false;
  }
}

/** 候选耗尽后手动重新加载：清空已消费记录，重新从头展示 */
async function handleReload() {
  loading.value = true;
  try {
    const newCards = await getCards();
    if (newCards && newCards.length > 0) {
      // 重新加载不复用旧栈：旧栈已消费完，保留只会让 visibleCards 为空
      cardList.value = [...newCards];
      currentCardIndex.value = 0;
      exhausted.value = false;
      dragX.value = 0;
      dragY.value = 0;
    } else {
      exhausted.value = true;
    }
  } catch {
    // 请求失败由 request 层统一 toast
  } finally {
    loading.value = false;
  }
}

// ===== 生命周期 =====
/** px → rpx 换算比例（onMounted 按实际屏宽缓存，触摸移动高频路径不再取系统信息） */
const rpxRatio = ref(RPX_BASE_WIDTH / DEFAULT_WINDOW_WIDTH_PX);

onMounted(() => {
  const sysInfo = uni.getSystemInfoSync();
  statusBarHeight.value =
    sysInfo.statusBarHeight || DEFAULT_STATUS_BAR_HEIGHT_PX;
  rpxRatio.value = RPX_BASE_WIDTH / (sysInfo.windowWidth || DEFAULT_WINDOW_WIDTH_PX);
  // 检查登录状态
  if (!userStore.isLogin) {
    uni.reLaunch({ url: "/pages/auth/login" });
    return;
  }
  loadMoreCards();
});

onUnmounted(() => {
  if (cardRemoveTimer) {
    clearTimeout(cardRemoveTimer);
    cardRemoveTimer = null;
  }
  if (dragRafId) {
    cancelAnimationFrame(dragRafId);
    dragRafId = 0;
  }
});

onShow(() => {
  // 卡片全滑完（或本就为空）时补拉：原先只在 cardList.length===0 才加载，
  // 而滑完时 cardList 仍含历史记录，导致用户卡在「暂无推荐」无法再加载。
  if (userStore.isLogin && (cardList.value.length === 0 || currentCardIndex.value >= cardList.value.length)) {
    loadMoreCards();
  }
  // 刷新无限滑动权益状态（VIP unlimited_swipe，每次进入匹配页刷新一次）
  if (userStore.isLogin) {
    matchStore.loadSwipeBenefit();
  }
  // 每次进入同步一次定位：后端 ES 有坐标，附近的人才能搜到本用户
  syncLocation();
});

/** 小程序端分享配置（H5 端静默忽略） */
onShareAppMessage(() => ({
  title: "心飞交友 · 遇见心动的人",
  path: "/pages/match/index",
}));
</script>

<style lang="scss" scoped>
.match-page {
  position: relative;
  /* tab 页可用高度 = 视口 - 原生 tabbar（50px）。
     不能用 var(--window-bottom)：uni-h5 把真实值设在 :root，页面 body 上是 0px，
     用它会铺满整屏，底部操作栏/照片被 tabbar 遮挡一半 */
  height: calc(100vh - var(--tab-bar-height, 0px));
  background: var(--bg-page);
  overflow: hidden;
}

/* 顶栏悬浮在卡片图片上方：透明渐变底保证照片上文字可读 */
.top-bar {
  position: absolute;
  top: 0;
  left: 0;
  right: 0;
  z-index: 50;
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 0 32rpx;
  height: 88rpx;
  background: linear-gradient(to bottom, rgba(0, 0, 0, 0.38), transparent);

  .city {
    display: flex;
    align-items: center;
    gap: 8rpx;

    .city-name {
      font-size: 32rpx;
      font-weight: bold;
      color: var(--text-inverse);
    }

    .arrow {
      font-size: 24rpx;
      color: rgba(255, 255, 255, 0.85);
    }
  }

  /* 重新定位：原先只有一个 ◎ 字符与背景融合，补文字 + 胶囊描边提高辨识度 */
  .locate-btn {
    display: flex;
    align-items: center;
    gap: 4rpx;
    padding: 8rpx 16rpx;
    border-radius: 24rpx;
    background: rgba(0, 0, 0, 0.28);
    border: 1rpx solid rgba(255, 255, 255, 0.45);

    .locate-ico { font-size: 28rpx; color: #fff; font-weight: 700; }
    .locate-text { font-size: 24rpx; color: #fff; font-weight: 600; }
  }

  .filter-btn {
    position: relative;
    display: flex;
    align-items: center;
    justify-content: center;
    /* 与 locate-btn 保持同一胶囊高度：44rpx 视觉 + 88rpx 热区 */
    height: 44rpx;
    min-height: 44rpx;
    box-sizing: border-box;
    padding: 0 16rpx;
    font-size: 24rpx;
    font-weight: 600;
    color: var(--text-inverse);
    background: rgba(255, 255, 255, 0.18);
    border: 1rpx solid rgba(255, 255, 255, 0.35);
    border-radius: 24rpx;

    /* 热区扩展：44rpx 视觉高度不满足 88rpx 最小热区，用伪元素补足。
       伪元素覆盖上下各 22rpx，透明但可点击 */
    &::before {
      content: "";
      position: absolute;
      left: 0;
      right: 0;
      top: -22rpx;
      bottom: -22rpx;
    }
  }
}

/* 卡片区全屏铺底：图片最大化，操作栏/顶栏悬浮其上 */
.card-area {
  position: absolute;
  inset: 0;
  overflow: hidden;
}

.card-wrapper {
  position: absolute;
  inset: 0;
  overflow: hidden;
  background: var(--bg-card);
  /* 拖拽期间浏览器需逐帧合成 transform，提升为独立图层避免整卡重绘 */
  will-change: transform;

  .card-image {
    position: absolute;
    inset: 0;
    width: 100%;
    height: 100%;
  }

  /* 加强遮罩：浅色照片（如白裙）上白字仍可读；文字区再加半透明暗底兜底 */
  .card-mask {
    position: absolute;
    left: 0;
    right: 0;
    bottom: 0;
    height: 52%;
    background: linear-gradient(
      to top,
      rgba(0, 0, 0, 0.88) 0%,
      rgba(0, 0, 0, 0.55) 42%,
      transparent 100%
    );
  }

  .card-info {
    position: absolute;
    left: 0;
    right: 0;
    /* 抬高到悬浮操作栏之上（操作栏高约 148rpx + 安全区） */
    bottom: calc(160rpx + env(safe-area-inset-bottom, 0px));
    padding: 0 32rpx;

    .name-row {
      display: flex;
      align-items: center;
      gap: 12rpx;

      .nickname {
        font-size: 40rpx;
        font-weight: bold;
        color: var(--text-inverse);
      }

      .age {
        font-size: 24rpx;
        font-weight: bold;
        color: var(--text-inverse);
        background: rgba(255, 94, 142, 0.85);
        padding: 2rpx 16rpx;
        border-radius: 16rpx;
      }
    }

    .fate-row {
      display: flex;
      align-items: center;
      gap: 12rpx;
      margin-top: 12rpx;

      .fate-label {
        font-size: 22rpx;
        color: rgba(255, 255, 255, 0.85);
      }

      .fate-bar {
        flex: 1;
        height: 12rpx;
        background: rgba(255, 255, 255, 0.25);
        border-radius: 6rpx;
        overflow: hidden;
      }

      .fate-fill {
        height: 100%;
        background: var(--gradient-brand-h);
        border-radius: 6rpx;
        transition: width 0.4s ease-out;
      }

      .fate-value {
        font-size: 22rpx;
        font-weight: bold;
        color: var(--color-primary);
        min-width: 36rpx;
        text-align: right;
      }
    }

    /* 城市 / 学历 / 行业：决策信息行 */
    .meta-row {
      margin-top: 12rpx;
      .meta-text {
        font-size: 24rpx;
        color: rgba(255, 255, 255, 0.92);
        text-shadow: 0 2rpx 8rpx rgba(0, 0, 0, 0.6);
      }
    }

    .tags {
      display: flex;
      gap: 12rpx;
      margin-top: 16rpx;

      .tag {
        font-size: 22rpx;
        color: var(--text-inverse);
        /* 卡片拖拽期间 backdrop-filter 会逐帧重算模糊导致卡顿，改用半透底近似毛玻璃观感 */
        background: rgba(255, 255, 255, 0.24);
        border: 1rpx solid rgba(255, 255, 255, 0.35);
        padding: 4rpx 16rpx;
        border-radius: 20rpx;
      }
    }

    /* 查看完整资料入口：半透明玻璃条，明确可点 */
    .detail-entry {
      display: flex;
      align-items: center;
      gap: 8rpx;
      margin-top: 20rpx;
      padding: 12rpx 24rpx;
      min-height: 64rpx;
      width: fit-content;
      background: rgba(255, 255, 255, 0.16);
      border: 1rpx solid rgba(255, 255, 255, 0.42);
      border-radius: 32rpx;
      backdrop-filter: blur(6rpx);

      .detail-photo { font-size: 26rpx; color: #fff; }
      .detail-text {
        font-size: 24rpx;
        color: #fff;
        font-weight: 600;
        text-shadow: 0 2rpx 8rpx rgba(0, 0, 0, 0.5);
      }
      .detail-arrow { font-size: 30rpx; color: #fff; margin-left: 4rpx; }
    }
  }

  .swipe-hint {
    position: absolute;
    /* 卡片全屏后下移，避开悬浮顶栏区域 */
    top: 180rpx;
    font-size: 44rpx;
    font-weight: 900;
    letter-spacing: 4rpx;
    padding: 8rpx 32rpx;
    border-radius: 12rpx;
    border: 6rpx solid;

    &.like {
      right: 40rpx;
      color: var(--success);
      border-color: var(--success);
      transform: rotate(15deg);
    }

    &.nope {
      left: 40rpx;
      color: var(--color-danger);
      border-color: var(--color-danger);
      transform: rotate(-15deg);
    }
  }
}

/* 悬浮在卡片图片之上：透明底 + 底部渐变压暗，按钮自身白底圆徽保证对比度 */
.action-bar {
  position: absolute;
  left: 0;
  right: 0;
  bottom: 0;
  z-index: 50;
  display: flex;
  justify-content: center;
  align-items: center;
  gap: 32rpx;
  padding: 24rpx 0;
  background: linear-gradient(to top, rgba(0, 0, 0, 0.3), transparent);

  .action-btn {
    display: flex;
    align-items: center;
    justify-content: center;
    border-radius: 50%;
    font-size: 44rpx;

    /* 探探式尺寸梯度：✕/心动大、超级喜欢中、回看/曝光小 */
    &.rewind {
      width: 88rpx;
      height: 88rpx;
      background: var(--bg-card);
      color: var(--gold-soft);
      border: 4rpx solid var(--gold-soft);
      box-shadow: 0 6rpx 20rpx rgba(246, 185, 59, 0.25);
    }

    &.dislike {
      width: 100rpx;
      height: 100rpx;
      background: var(--bg-card);
      color: var(--color-danger);
      border: 4rpx solid var(--color-danger);
      box-shadow: 0 6rpx 20rpx rgba(255, 59, 48, 0.25);
    }

    &.superlike {
      width: 88rpx;
      height: 88rpx;
      background: var(--bg-card);
      color: var(--info-blue-soft);
      border: 4rpx solid var(--info-blue-soft);
      box-shadow: 0 6rpx 20rpx rgba(77, 171, 247, 0.25);
    }

    &.like {
      width: 100rpx;
      height: 100rpx;
      background: var(--bg-card);
      color: var(--success);
      border: 4rpx solid var(--success);
      box-shadow: 0 6rpx 20rpx rgba(76, 217, 100, 0.3);
    }

    &.boost {
      width: 88rpx;
      height: 88rpx;
      flex-direction: column;
      gap: 0;
      background: linear-gradient(135deg, var(--color-primary-violet), var(--color-primary-deep));
      color: var(--text-inverse);
      box-shadow: 0 6rpx 20rpx rgba(138, 92, 246, 0.4);

      .boost-ico { font-size: 36rpx; line-height: 1; }
      .boost-label {
        font-size: 18rpx;
        font-weight: 600;
        line-height: 1.1;
        color: #fff;
      }
    }
  }
}
</style>
