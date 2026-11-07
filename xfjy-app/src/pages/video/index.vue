<template>
  <view class="video-page">
    <!-- 断网/恢复全局提示条（fixed 定位自管理显隐，S1） -->
    <network-status-bar />
    <!-- 顶部 Tab（role=tablist 语义 + 可键盘操作） -->
    <view
      class="top-tabs"
      role="tablist"
      aria-label="视频频道"
    >
      <view class="tab-group">
        <a11y-button
          v-for="ch in CHANNELS"
          :key="ch.key"
          class="tab"
          :class="{ active: activeChannel === ch.key }"
          role="tab"
          :checked="activeChannel === ch.key"
          :label="ch.label"
          @tap="switchChannel(ch.key)"
        >
          {{ ch.label }}
        </a11y-button>
      </view>
      <a11y-button
        class="side-btn"
        label="刷新视频列表"
        :loading="refreshing"
        @tap="onRefresh"
      >
        <view
          class="side-ico i-ph-arrows-clockwise-bold"
          :class="{ 'is-spinning': refreshing }"
          aria-hidden="true"
        />
      </a11y-button>
      <a11y-button
        class="side-btn search-btn"
        label="搜索"
        @tap="onSearch"
      >
        <view
          class="side-ico i-ph-magnifying-glass"
          aria-hidden="true"
        />
      </a11y-button>
    </view>

    <!-- S6：错误边界捕获视频流渲染异常，@retry 重新拉取视频列表 -->
    <error-boundary @retry="loadVideos">
    <swiper
      class="video-swiper"
      vertical
      :current="swiperIndex"
      @change="onSwiperChange"
    >
      <swiper-item
        v-for="(video, idx) in displayList"
        :key="video.id"
      >
        <view
          class="video-item"
          :class="{ 'is-active': idx === swiperIndex }"
        >
          <!-- 相邻三条挂载：上一条保留实例，回退时无需重建（重建会重新拉流、回退后要等几秒才出声）；
              但只有当前条 auto 预载，相邻条仅取元数据，避免三条同时下载视频体。 -->
          <template v-if="Math.abs(idx - swiperIndex) <= 1">
            <video
              :id="'vid-' + video.id"
              :key="video.id + '-' + (videoRetryTick[video.id] || 0)"
              class="video-player"
              :src="video.videoUrl"
              :poster="video.cover"
              :autoplay="idx === swiperIndex"
              loop
              :muted="isMuted"
              :preload="idx === swiperIndex ? 'auto' : 'metadata'"
              :controls="false"
              :aria-label="`视频：${video.nickname} 的作品`"
              :show-center-play-btn="false"
              :enable-progress-gesture="false"
              object-fit="cover"
              @timeupdate="idx === swiperIndex ? onTimeUpdate($event) : null"
              @play="onVideoPlay(idx)"
              @pause="onVideoPause(idx)"
              @error="onVideoError(video, $event)"
            />
            <smart-image
              v-if="idx !== swiperIndex"
              class="cover-residue"
              :src="video.cover"
              :fallback="''"
              mode="aspectFill"
              aria-hidden="true"
            />
          </template>
          <smart-image
            v-else
            class="cover"
            :src="video.cover"
            :fallback="''"
            mode="aspectFill"
            lazy-load
            :aria-label="`${video.nickname} 的视频封面`"
          />

          <!-- 透明点击层：单击暂停/播放，双击点赞 -->
          <view
            class="tap-layer"
            aria-hidden="true"
            @tap="onVideoTap(video, $event)"
          />

          <!-- 中央播放/暂停指示 -->
          <view
            v-if="currentId === video.id && !isPlaying && !erroredVideoIds.has(video.id)"
            class="center-state"
            aria-hidden="true"
          >
            <view class="play-icon i-ph-play-fill" />
          </view>

          <!-- 播放失败浮层 -->
          <a11y-button
            v-if="video.id === currentId && erroredVideoIds.has(video.id)"
            class="video-error"
            label="视频加载失败，点击重试"
            @tap="retryVideo(video)"
          >
            <text class="video-error-title">
              视频加载失败
            </text>
            <text
              class="video-error-retry"
              aria-hidden="true"
            >
              点此重试
            </text>
          </a11y-button>

          <!-- 双击飞出的爱心（抖音式大爱心跳弹） -->
          <view
            v-for="h in floatHearts"
            :key="h.id"
            class="float-heart"
            :style="{ left: h.x + 'px', top: h.y + 'px' }"
            aria-hidden="true"
          >
            <view class="float-heart__ico i-ph-heart-fill" />
          </view>

          <!-- 右侧操作栏（抖音原版布局） -->
          <view class="side-bar">
            <view class="side-stack">
              <!-- 作者头像 + 关注 -->
              <a11y-button
                class="side-btn follow-btn"
                :label="video.hasFocus ? `取消关注 ${video.nickname}` : `关注 ${video.nickname}`"
                @tap="handleFocus(video)"
              >
                <smart-image
                  class="side-avatar"
                  :src="video.avatar"
                  mode="aspectFill"
                  lazy-load
                  aria-hidden="true"
                />
                <view
                  v-if="!video.hasFocus"
                  class="follow-plus"
                  aria-hidden="true"
                >
                  <view class="follow-plus__ico i-ph-plus-bold" />
                </view>
              </a11y-button>

              <a11y-button
                class="side-btn"
                :label="video.hasLiked === 1 ? `取消点赞，当前 ${video.likeCount} 赞` : `点赞，当前 ${video.likeCount} 赞`"
                @tap="handleLike(video)"
              >
                <view
                  class="side-ico"
                  :class="{ 'like-pop': video.hasLiked === 1 }"
                >
                  <view
                    class="side-ico__ph"
                    :class="video.hasLiked === 1 ? 'i-ph-heart-fill side-ico__ph--liked' : 'i-ph-heart'"
                    aria-hidden="true"
                  />
                </view>
                <!-- 0 赞时给明确引导，避免"恒为 0、不知能否互动" -->
                <text class="side-count">
                  {{ video.likeCount > 0 ? formatCount(video.likeCount) : "抢首赞" }}
                </text>
              </a11y-button>

              <a11y-button
                class="side-btn"
                :label="`查看评论，共 ${video.commentCount} 条`"
                @tap="showComments(video)"
              >
                <view class="side-ico">
                  <view
                    class="side-ico__ph i-ph-chat-circle-dots-bold"
                    aria-hidden="true"
                  />
                </view>
                <text class="side-count">
                  {{ video.commentCount > 0 ? formatCount(video.commentCount) : "抢首评" }}
                </text>
              </a11y-button>

              <a11y-button
                class="side-btn"
                :label="`分享 ${video.nickname} 的视频`"
                @tap="openShare(video)"
              >
                <view class="side-ico">
                  <view
                    class="side-ico__ph i-ph-arrow-bend-up-right-bold"
                    aria-hidden="true"
                  />
                </view>
              </a11y-button>
            </view>
          </view>

          <!-- 底部信息 + 滚动音乐条 -->
          <view class="video-info">
            <text class="author">
              @{{ video.nickname }}
            </text>
            <!-- 位置：城市名可空（H5 不支持逆地理），空时用距离标签，绝不展示坐标串 -->
            <text
              v-if="activeChannel === 'location' && (video.locationName || video.distance)"
              class="location-label"
            >
              {{ video.locationName || formatDistance(video.distance) }}
            </text>
            <text class="desc ellipsis-2">
              {{ video.signature }}
            </text>
            <view class="music-row">
              <view
                class="music-disc"
                :class="{ 'disc-paused': !isPlaying && currentId === video.id }"
                aria-hidden="true"
              >
                <smart-image
                  lazy-load
                  class="disc-img"
                  :src="video.cover"
                  :fallback="''"
                  mode="aspectFill"
                />
              </view>
              <view class="music-track">
                <text
                  class="music-text"
                  :class="{ 'text-paused': !isPlaying && currentId === video.id }"
                >
                  {{ video.nickname }} 创作的原声 · {{ video.signature || '小视频' }}
                </text>
              </view>
            </view>
          </view>

          <!-- 进度条 + 拖动定位（抖音式）：88rpx 热区保证可触摸；拖动只更新预览，松手才 seek，
               避免拖动过程中连续 seek 造成卡顿。横拖被 .stop.prevent 吃掉，不会带动竖向 swiper。 -->
          <view
            v-if="currentId === video.id"
            class="progress-hit"
            :class="{ 'is-scrubbing': scrubbing }"
            @touchstart="onScrubStart"
            @touchmove.stop.prevent="onScrubMove"
            @touchend="onScrubEnd"
            @touchcancel="onScrubEnd"
          >
            <view
              class="progress-bar"
              role="progressbar"
              aria-label="播放进度"
              :aria-valuenow="Math.round(scrubbing ? scrubPercent : progress)"
              aria-valuemin="0"
              aria-valuemax="100"
            >
              <view
                class="progress-inner"
                :style="{ transform: 'scaleX(' + (scrubbing ? scrubPercent : progress) / 100 + ')' }"
              />
              <view
                v-if="scrubbing"
                class="progress-thumb"
                :style="{ left: scrubPercent + '%' }"
              />
            </view>
            <view
              v-if="scrubbing"
              class="scrub-time"
              aria-hidden="true"
            >
              {{ scrubLabel }}
            </view>
          </view>
        </view>
      </swiper-item>
    </swiper>
    </error-boundary>

    <!-- 顶部/底部暗角渐变 -->
    <view
      class="top-scrim"
      aria-hidden="true"
    />
    <view
      class="bottom-scrim"
      aria-hidden="true"
    />

    <!-- H5 静音提示：浏览器自动播放策略禁止无交互页面带声 autoplay，首屏只能 muted 起播。
         未解锁前明示「静音中」，避免用户误以为视频本身没声音；用户首次触碰屏幕任意位置
         （全局 pointerdown 监听）或点此按钮即解锁，解锁后 v-if 移除。小程序不受该策略约束，不渲染。 -->
    <a11y-button
      v-if="isH5 && isMuted"
      class="muted-hint"
      label="视频处于静音状态，轻触开启声音"
      @tap="unlockAudio"
    >
      <view
        class="muted-hint__ico i-ph-speaker-x"
        aria-hidden="true"
      />
      <text class="muted-hint__text">
        轻触开启声音
      </text>
    </a11y-button>

    <!-- 附近回退提示：未授权/半径内无数据时后端回退全站热门，必须明示而不是伪装成附近内容 -->
    <view
      v-if="activeChannel === 'location' && nearbyFallback && displayList.length > 0"
      class="nearby-tip"
      role="status"
    >
      <text class="nearby-tip__text">
        {{ locationDenied ? "未开启定位权限，正在展示全站热门" : "附近暂无内容，正在展示全站热门" }}
      </text>
      <a11y-button
        class="nearby-tip__btn"
        label="开启定位"
        @tap="openLocationSetting"
      >
        开启定位
      </a11y-button>
    </view>

    <!-- 上滑提示（抖音式 SVG 三角 + 呼吸动画） -->
    <view
      v-if="showSwipeHint"
      class="swipe-hint"
      aria-hidden="true"
    >
      <svg
        class="swipe-triangle"
        viewBox="0 0 24 12"
        aria-hidden="true"
      >
        <path
          d="M12 0L24 12H0z"
          fill="var(--text-inverse)"
          opacity="0.9"
        />
      </svg>
      <text class="swipe-text">
        上滑看更多
      </text>
    </view>

    <!-- 首屏加载态：后端跨服务组装 VideoVo 约需 4~5s，旧版暗色骨架叠在黑底上几乎不可见，
         用户会看到 4~7 秒纯黑并误以为页面卡死。改为有文案、有进度的可见加载层 + 失败重试。 -->
    <view
      v-if="displayList.length === 0 && (loading || loadFailed)"
      class="video-loading"
      role="status"
      aria-live="polite"
    >
      <view
        class="video-loading__glow"
        aria-hidden="true"
      />
      <view class="video-loading__content">
        <view
          v-if="!loadFailed"
          class="video-loading__spinner"
          aria-hidden="true"
        />
        <text class="video-loading__title">
          {{ loadFailed ? "视频没能加载出来" : "正在为你挑选精彩视频" }}
        </text>
        <text class="video-loading__desc">
          {{ loadFailed ? "网络似乎不太给力，点下方按钮重试" : loadingHint }}
        </text>
        <a11y-button
          v-if="loadFailed"
          class="video-loading__retry"
          label="重新加载视频"
          @tap="retryLoad"
        >
          重新加载
        </a11y-button>
      </view>
    </view>

    <!-- 空状态：附近未授权时给可点击的授权引导，不显示成「没有内容」的坏页 -->
    <view
      v-if="activeChannel === 'location' && locationDenied && displayList.length === 0 && !loading && !loadFailed"
      class="nearby-auth"
      role="status"
    >
      <text class="nearby-auth__title">
        需要开启定位权限
      </text>
      <text class="nearby-auth__desc">
        开启后可查看附近的视频；也可以先在推荐页逛逛
      </text>
      <a11y-button
        class="nearby-auth__btn"
        label="开启定位权限"
        @tap="openLocationSetting"
      >
        开启定位权限
      </a11y-button>
    </view>

    <empty-state
      v-else-if="displayList.length === 0 && !loading && !loadFailed"
      overlay
      dark
      icon="eye"
      :title="emptyStateTitle"
      :desc="emptyStateDesc"
    />

    <!-- 评论抽屉 -->
    <video-comment-drawer
      :visible="commentVisible"
      :video-id="activeCommentVideoId"
      @close="closeComments"
      @commented="onVideoCommented"
    />

    <!-- 底部分享面板 -->
    <a11y-dialog
      class="share-mask"
      :visible="showShare"
      title="分享给好友"
      @close="showShare = false"
    >
      <view class="share-panel-inner">
        <text class="share-title">
          分享给好友
        </text>
        <view class="share-grid">
          <a11y-button
            class="share-item"
            label="分享到微信"
            @tap="doShare('wechat')"
          >
            <view
              class="share-ico ico-wechat"
              aria-hidden="true"
            >
              微
            </view>
            <text class="share-label">
              微信
            </text>
          </a11y-button>
          <a11y-button
            class="share-item"
            label="分享到朋友圈"
            @tap="doShare('moments')"
          >
            <view
              class="share-ico ico-moments"
              aria-hidden="true"
            >
              友
            </view>
            <text class="share-label">
              朋友圈
            </text>
          </a11y-button>
          <a11y-button
            class="share-item"
            label="复制链接"
            @tap="doShare('copy')"
          >
            <view
              class="share-ico ico-copy"
              aria-hidden="true"
            >
              链
            </view>
            <text class="share-label">
              复制链接
            </text>
          </a11y-button>
        </view>
        <a11y-button
          class="share-cancel"
          label="取消分享"
          @tap="showShare = false"
        >
          取消
        </a11y-button>
      </view>
    </a11y-dialog>
    <!-- 匹配成功弹窗（全局，由 match store 驱动） -->
    <MatchPopup />
  </view>
</template>

<script setup lang="ts">
import { ref, computed, watch, nextTick, getCurrentInstance } from "vue";
import { onLoad, onShow, onHide, onShareAppMessage } from "@dcloudio/uni-app";
import MatchPopup from "@/components/match-popup.vue";
import NetworkStatusBar from "@/components/network-status-bar.vue";
import {
  getVideoList,
  getFollowVideoList,
  getHotVideoList,
  getLocationVideoList,
  reportVideoPlay,
  likeVideo,
  dislikeVideo,
  focusVideoUser,
  unfocusVideoUser,
  type Video,
} from "@/api/video";
import VideoCommentDrawer from "@/components/video-comment-drawer.vue";
import A11yButton from "@/components/a11y-button.vue";
import SmartImage from "@/components/smart-image.vue";
import ErrorBoundary from "@/components/error-boundary.vue";
import EmptyState from "@/components/empty-state.vue";
import {
  readVideoCache,
  writeVideoCache,
  getPrefetchPromise,
} from "@/utils/video-cache";
import A11yDialog from "@/components/a11y-dialog.vue";
import { formatCount } from "@/utils/format";
import { info } from "@/utils/ux";
import { warn } from "@/utils/logger";
import { useSystemStore } from "@/store/system";
import type { VideoSearchItem } from "@/api/video-search";

const systemStore = useSystemStore();

/**
 * 分页大小。
 * 取 30 而非 10：视频源按发布时间聚集，最新十几条常常全部来自同一作者，
 * 若每页只取 10 条会出现"连续刷到的视频都是同一个人"（用户误以为是自己的账号）。
 * 一次多取一批后由 interleaveByAuthor 按作者轮转打散，保证首屏即作者多样。
 * swiper 仅渲染当前 ±1 项，30 条不会带来渲染压力。
 */
const PAGE_SIZE = 30;

/**
 * 已加载条数上限：上滑久了 list 只增不减，DOM/封面图会常驻内存（30 条/页 × 4 页就很可观）。
 * 超过上限先删头部再同步 swiper 索引，当前正在看的那条始终保留。
 */
const MAX_LOADED_ITEMS = 120;

/** 4 个频道各自独立的列表与分页（「朋友」与「关注」同源重复，已从前后端移除） */
const channels = {
  recommend: { list: ref<Video[]>([]), page: ref(1), hasMore: ref(true) },
  follow: { list: ref<Video[]>([]), page: ref(1), hasMore: ref(true) },
  hot: { list: ref<Video[]>([]), page: ref(1), hasMore: ref(true) },
  location: { list: ref<Video[]>([]), page: ref(1), hasMore: ref(true) },
} as const;

/** 按频道 key 获取对应的 API 请求函数 */
function getChannelApi(key: ChannelKey) {
  switch (key) {
    case "recommend": return getVideoList;
    case "follow": return getFollowVideoList;
    case "hot": return getHotVideoList;
    case "location": return getLocationVideoList;
  }
}

const swiperIndex = ref(0);
type ChannelKey = "hot" | "follow" | "location" | "recommend";
/**
 * 频道精简为 4 个（原 5 个中「朋友」与「关注」后端同源、内容完全重复，
 * 「定位」已补全为真实地理数据）。
 * 各 tab 语义：热点=高赞优先 / 关注=已关注作者 / 附近=按距离排序 / 推荐=热度分排序。
 */
const CHANNELS = [
  { key: "hot", label: "热点" },
  { key: "follow", label: "关注" },
  { key: "location", label: "附近" },
  { key: "recommend", label: "推荐" },
] as const;
const activeChannel = ref<ChannelKey>("recommend");

const displayList = computed<Video[]>(() => channels[activeChannel.value].list.value);
/** 「没有更多了」提示防重复：每频道只提示一次，切频道/重载重置（P2-4） */
const noMoreNotified = ref(false);

const commentVisible = ref(false);
const activeCommentVideoId = ref("");
const loadingMore = ref(false);
let loadToken = 0;

/**
 * 手动刷新（顶部刷新按钮 / 再次点击当前频道 tab）。
 *
 * 为什么需要显式入口：视频页用 swiper 竖向滑动，swiper 不支持 scroll-view 的
 * refresher-* 下拉刷新能力；而「上滑看更多」与「下拉刷新」在同一个 swiper 上
 * 手势冲突，业务层拦截成本高且 H5/小程序行为不一致。抖音类竖流产品同样不做下拉，
 * 主流做法是提供可见的刷新入口。
 */
const refreshing = ref(false);

const currentVideo = computed<Video | undefined>(() => displayList.value[swiperIndex.value]);
const currentId = computed(() => currentVideo.value?.id ?? "");

const isPlaying = ref(true);
// getSystemInfoSync 为废弃 API，uniPlatform 改从 getAppBaseInfo 取（旧运行时无此 API 时按非 H5 处理）。
// 当前 @dcloudio 类型声明的 GetAppBaseInfoResult 漏报 uniPlatform（H5 运行时实测返回 "web"），用结构类型断言补齐
const isH5 =
  (uni.getAppBaseInfo?.() as { uniPlatform?: string } | undefined)?.uniPlatform === "web";
const isMuted = ref(isH5);
const progress = ref(0);
/** 视频总时长（秒）：由 timeupdate 回填，供进度条拖动换算 */
const duration = ref(0);
/** 是否正在拖进度条，以及拖动中的预览百分比（松手才真正 seek） */
const scrubbing = ref(false);
const scrubPercent = ref(0);
let scrubRectLeft = 0;
let scrubRectWidth = 0;
const loading = ref(false);
/** 首屏加载失败（且无任何缓存可展示）时显示重试入口，避免死在纯黑页 */
const loadFailed = ref(false);
/** 超过 2.5s 仍未出内容时切换安抚文案，降低用户误判为卡死的概率 */
const slowLoading = ref(false);
let slowLoadingTimer: ReturnType<typeof setTimeout> | null = null;
const loadingHint = computed(() =>
  slowLoading.value
    ? "内容较多，正在努力加载，请稍等片刻…"
    : "马上就好，先逛逛其他页面也不会打断加载",
);
function retryLoad() {
  loadFailed.value = false;
  loadVideos();
}

/** 「附近」：当前用户坐标；未授权/取不到为 null（不等于请求失败，需展示授权引导） */
const userLocation = ref<{ lat: number; lng: number } | null>(null);
const locationDenied = ref(false);
/** 后端在无坐标/半径无结果时回退全站热门，此标记用于文案提示，避免用户误以为是附近内容 */
const nearbyFallback = ref(false);

/**
 * 取一次定位坐标。H5 不支持逆地理（geocode），拿不到城市名属正常路径，
 * 这里只要经纬度；失败不抛错、不阻塞列表请求。
 */
function locateOnce(): Promise<{ lat: number; lng: number } | null> {
  if (userLocation.value) return Promise.resolve(userLocation.value);
  return new Promise((resolve) => {
    uni.getLocation({
      type: "gcj02",
      success: (res: any) => {
        const lat = Number(res.latitude);
        const lng = Number(res.longitude);
        if (Number.isFinite(lat) && Number.isFinite(lng)) {
          locationDenied.value = false;
          userLocation.value = { lat, lng };
          resolve(userLocation.value);
          return;
        }
        resolve(null);
      },
      fail: () => {
        // 未开权限/系统拒绝：标记后由页面给出可点击的授权引导
        locationDenied.value = true;
        resolve(null);
      },
    });
  });
}

/** 未授权时的授权引导：小程序走设置页，其它端直接重试定位 */
function openLocationSetting(): void {
  const openSetting = (uni as any).openSetting;
  if (typeof openSetting === "function") {
    openSetting({
      success: () => {
        locationDenied.value = false;
        userLocation.value = null;
        channels.location.list.value = [];
        loadVideos();
      },
      fail: () => {
        locationDenied.value = true;
      },
    });
    return;
  }
  locationDenied.value = false;
  userLocation.value = null;
  channels.location.list.value = [];
  loadVideos();
}

/** 距离标签：绝不展示 "lng,lat" 坐标串（locationName 为空时的展示兜底） */
function formatDistance(distance?: number): string {
  if (distance === undefined || distance === null || !Number.isFinite(distance)) return "";
  if (distance < 500) return "500m 内";
  if (distance < 1000) return `${Math.round(distance / 100) * 100}m`;
  return `${(distance / 1000).toFixed(1)}km`;
}

let lastPlayedIndex = -1;

let lastTapTime = 0;
let singleTapTimer: ReturnType<typeof setTimeout> | null = null;
let doubleTapCooldownUntil = 0;

const instance = getCurrentInstance();

function getCtx(index: number) {
  const video = displayList.value[index];
  if (!video) return null;
  return uni.createVideoContext("vid-" + video.id, instance?.proxy as any);
}

/**
 * 按「渲染索引」取真实的原生 video 元素。
 *
 * 不能靠 createVideoContext("vid-"+id)：H5 编译后内层原生 <video> 的 id 是空串
 * （实测 id 未从模板透传），按 id 查会拿不到目标实例，甚至作用到相邻实例上，
 * 表现为「滑回上一条时它被误暂停」。.video-item 与 displayList 顺序一致，按索引取最可靠。
 */
function videoEl(index: number): HTMLVideoElement | null {
  if (!isH5 || typeof document === "undefined") return null;
  const item = document.querySelectorAll(".video-item")[index] as any;
  return (item?.querySelector?.("video") || null) as HTMLVideoElement | null;
}

function safePlay(index: number) {
  const video = displayList.value[index];
  if (!video) return;
  if (isH5 && typeof document !== "undefined") {
    let el = videoEl(index);
    if (!el) {
      // 该条刚被 v-if 挂载、DOM 还没就绪时按 src 反查兜底
      el = Array.from(document.querySelectorAll("video")).find(
        (v) => (v.getAttribute("src") || "") === video.videoUrl,
      ) || null;
    }
    if (el) {
      try {
        const p = el.play();
        if (p && typeof p.catch === "function") {
          p.catch(() => {
            /* 非手势下的非静音自动播放被浏览器拒绝：保持静音等待下一次手势，不抛到全局 */
          });
        }
        // 刚挂载/刚换源的实例 play 可能被挂起（media 未 ready），短暂后仍是暂停态就补一次。
        // 必须再判 isPlaying：用户在这 320ms 内点了暂停，这里不能再把视频拉起来。
        setTimeout(() => {
          if (!el || el !== videoEl(index) || !el.paused) return;
          if (!isPlaying.value) return;
          el.play().catch(() => {});
        }, 320);
      } catch {
        /* 个别 WebView 的 play 同步抛错 */
      }
      return;
    }
  }
  getCtx(index)?.play();
}

function playAt(index: number) {
  const video = displayList.value[index];
  if (!video) return;
  lastPlayedIndex = index;
  safePlay(index);
}

function pauseAt(index: number) {
  if (!displayList.value[index]) return;
  if (isH5 && typeof document !== "undefined") {
    // H5 拿不到实例就跳过：createVideoContext 的 id 不可靠，宁可少暂停也不能误伤别的实例
    const el = videoEl(index);
    if (el) el.pause();
    return;
  }
  getCtx(index)?.pause();
}

/**
 * 解除 H5 静音。浏览器自动播放策略：无用户交互的页面只允许 muted autoplay，
 * 故首屏/每个频道的第一个视频必然静音起播；必须在用户手势回调里 unmute 才被允许。
 * 入口有四类（均为用户手势）：文档级 pointerdown 全局监听（覆盖点 Tab/封面等任意触碰）、
 * swiper 切换、视频区单击、静音提示按钮。isMuted 守卫保证只生效一次，重复调用 no-op。
 */
function unlockAudio() {
  if (isH5 && isMuted.value) {
    isMuted.value = false;
    // 命令式同步 unmute 兜底；uni-h5 内部 watch(muted) 也会随 prop 变化同步 video.muted。
    // 限定在当前页面容器内，避免误解除其它页面/浮层的视频实例。
    const scope: any = document.querySelector(".video-page") || document;
    scope.querySelectorAll("video").forEach((v: HTMLVideoElement) => (v.muted = false));
  }
}

watch(swiperIndex, () => {
  lastTapTime = 0;
  if (singleTapTimer) {
    clearTimeout(singleTapTimer);
    singleTapTimer = null;
  }
});

function onSwiperChange(e: any) {
  const index = e.detail.current;
  const old = swiperIndex.value;
  if (old !== index) pauseAt(old);
  swiperIndex.value = index;
  isPlaying.value = true;
  progress.value = 0;
  // 切条后旧时长立即作废：新条 timeupdate 之前若拖进度，用旧 duration 换算会跳到错误位置
  duration.value = 0;
  unlockAudio();
  // 回退到「上一条」时该条 video 是 v-if 重新挂载的，此刻 DOM 里还没有它；
  // 必须等渲染完成再 play，否则 safePlay 取不到元素、getCtx 也指向未挂载实例，画面会停在 0 秒。
  nextTick(() => {
    if (commentVisible.value || index !== swiperIndex.value) return;
    playAt(index);
  });
  if (index >= displayList.value.length - 2) {
    loadMoreVideos();
    // P2-4：滑到底且无更多时提示一次，避免用户反复上滑以为卡了
    if (
      index === displayList.value.length - 1 &&
      !channels[activeChannel.value].hasMore.value &&
      !noMoreNotified.value
    ) {
      noMoreNotified.value = true;
      info("没有更多了");
    }
  }
}

function onVideoPlay(idx: number) {
  if (idx !== swiperIndex.value) return;
  // 首屏由 template 的 autoplay 直接起播，不经过 playAt()，必须在这里补写 lastPlayedIndex，
  // 否则 onVideoPause 的 `idx !== lastPlayedIndex` 会吞掉首条的 pause 事件，isPlaying 永远为 true。
  lastPlayedIndex = idx;
  isPlaying.value = true;
  // 播放上报：仅在 play 事件触发时上报一次/视频（不随 timeupdate 上报），
  // 后端另有 Redis「用户×视频」去重兜底，双层保证防刷。
  const video = displayList.value[idx];
  if (video && !reportedPlayIds.has(video.id)) {
    reportedPlayIds.add(video.id);
    reportVideoPlay(video.id).catch(() => {
      // 上报失败不影响播放
    });
  }
}

function onVideoPause(idx: number) {
  if (idx !== swiperIndex.value || idx !== lastPlayedIndex) return;
  isPlaying.value = false;
}

const erroredVideoIds = ref(new Set<string>());
const videoRetryTick = ref<Record<string, number>>({});
/** 本次会话已上报播放的视频，避免同一视频反复播放时重复请求 */
const reportedPlayIds = new Set<string>();

function onVideoError(video: Video, e?: any) {
  const detail = e?.detail || {};
  warn("[video] 加载失败", video.id, detail.errMsg || detail.message || video.videoUrl);
  erroredVideoIds.value = new Set(erroredVideoIds.value).add(video.id);
}

function retryVideo(video: Video) {
  const next = new Set(erroredVideoIds.value);
  next.delete(video.id);
  erroredVideoIds.value = next;
  videoRetryTick.value[video.id] = (videoRetryTick.value[video.id] || 0) + 1;
  nextTick(() => playAt(swiperIndex.value));
}

const emptyStateTitle = computed(() => {
  switch (activeChannel.value) {
    case "follow": return "还没有关注的作者";
    case "hot": return "暂无热门视频";
    case "location": return "暂无附近的视频";
    default: return "暂无视频";
  }
});
const emptyStateDesc = computed(() => {
  switch (activeChannel.value) {
    case "follow": return "去推荐页关注喜欢的人吧";
    case "hot": return "精彩内容正在路上";
    case "location": return "开启定位后看看附近的人吧";
    default: return "精彩内容即将上线";
  }
});

const floatHearts = ref<{ id: number; x: number; y: number }[]>([]);

const showShare = ref(false);
const shareVideo = ref<Video | null>(null);

function onSearch(): void {
  uni.navigateTo({ url: "/pages/video/search" });
}

/**
 * 手动刷新当前频道：回到第一条并重新拉取第 1 页。
 * 与 loadVideos() 的差别：强制走「有内容也显示加载层」路径并上抛 refreshing 态，
 * 让用户明确感知刷新动作（loadVideos 在已有内容时是静默后台刷新）。
 */
async function onRefresh() {
  if (refreshing.value) return;
  refreshing.value = true;
  noMoreNotified.value = false;
  const ch = activeChannel.value;
  const chState = channels[ch];
  const token = ++loadToken;
  try {
    // 迁移期双传：新契约读 pageSize，存量网关只认 pagesize；后端 xfjy-server 上线后删除 pagesize
    const query: Record<string, any> = { page: 1, pageSize: PAGE_SIZE, pagesize: PAGE_SIZE };
    if (ch === "location") {
      nearbyFallback.value = false;
      const loc = await locateOnce();
      if (loc) {
        query.lat = loc.lat;
        query.lng = loc.lng;
      }
    }
    const res = await (getChannelApi(ch) as any)(query);
    if (token !== loadToken || activeChannel.value !== ch) return;
    nearbyFallback.value = Boolean((res as any).fallback);
    const items = res.items || [];
    if (items.length) {
      chState.list.value = arrangeForChannel(ch, items);
      writeVideoCache(ch, items);
      chState.page.value = 1;
      chState.hasMore.value = true;
      swiperIndex.value = 0;
      erroredVideoIds.value = new Set();
      isPlaying.value = true;
      progress.value = 0;
      nextTick(() => playAt(0));
    } else {
      // 空结果保留原列表：刷新拿到空不该把用户正在看的内容清掉
      info("暂无新视频");
    }
  } catch {
    // request 层统一 toast
  } finally {
    if (token === loadToken) refreshing.value = false;
  }
}

async function switchChannel(ch: ChannelKey) {
  // 再次点击当前频道 = 刷新（可发现性低于顶部按钮，作为快捷操作保留）
  if (activeChannel.value === ch) {
    await onRefresh();
    return;
  }
  activeChannel.value = ch;
  swiperIndex.value = 0;
  isPlaying.value = true;
  progress.value = 0;
  noMoreNotified.value = false;
  const chState = channels[ch];
  erroredVideoIds.value = new Set();
  // 切频道前先 hydration 该频道缓存，避免每次切回都重新黑屏 4~5s
  if (chState.list.value.length === 0) {
    const cached = readVideoCache(ch);
    if (cached.length) chState.list.value = arrangeForChannel(ch, cached);
  }
  if (chState.list.value.length === 0) {
    await loadVideos();
  } else {
    // 已有数据先立即起播，再后台静默刷新第一页：旧实现只 playAt(0)，
    // 切回「热点」看到的可能是几分钟前的旧流，且没有任何加载提示
    playAt(0);
    void loadVideos();
  }
}

/**
 * 按作者打散：避免视频流顶部连续多条都来自同一作者（如最新 12 条全是某用户），
 * 导致用户误以为"所有视频都是自己"。轮转排列，保证相邻视频作者不同（作者数≥2 时）。
 */
function interleaveByAuthor<T extends { userId: string | number }>(list: T[]): T[] {
  if (!list || list.length <= 2) return list;
  const byAuthor = new Map<string | number, T[]>();
  for (const v of list) {
    const k = v.userId;
    if (!byAuthor.has(k)) byAuthor.set(k, []);
    byAuthor.get(k)!.push(v);
  }
  const queues = Array.from(byAuthor.values()).sort((a, b) => b.length - a.length);
  const result: T[] = [];
  const maxLen = Math.max(...queues.map((q) => q.length));
  for (let i = 0; i < maxLen; i++) {
    for (const q of queues) {
      if (i < q.length) result.push(q[i]);
    }
  }
  return result;
}

/** 合并下一页时已打散列表，并消除跨页边界处的同作者相邻 */
function mergeInterleaved<T extends { id: string | number; userId: string | number }>(current: T[], next: T[]): T[] {
  if (!next.length) return current;
  const interleaved = interleaveByAuthor(next);
  const last = current[current.length - 1];
  if (last && interleaved.length && interleaved[0].userId === last.userId) {
    interleaved.push(interleaved.shift() as T);
  }
  return appendUnique(current, interleaved);
}

/** 推荐/附近必须保持后端顺序（热度分/距离），二次打散会破坏排序与稳定分页 */
const KEEP_BACKEND_ORDER: ChannelKey[] = ["recommend", "location"];

function arrangeForChannel(key: ChannelKey, items: Video[]): Video[] {
  return KEEP_BACKEND_ORDER.indexOf(key) >= 0 ? items : interleaveByAuthor(items);
}

function mergeForChannel(key: ChannelKey, current: Video[], next: Video[]): Video[] {
  return KEEP_BACKEND_ORDER.indexOf(key) >= 0 ? appendUnique(current, next) : mergeInterleaved(current, next);
}

const TAP_GAP = 280;

function onVideoTap(video: Video, e: any) {
  unlockAudio();
  const now = Date.now();
  if (now < doubleTapCooldownUntil) return;
  if (now - lastTapTime < TAP_GAP) {
    if (singleTapTimer) {
      clearTimeout(singleTapTimer);
      singleTapTimer = null;
    }
    lastTapTime = 0;
    doubleTapCooldownUntil = now + TAP_GAP;
    doubleLike(video, e);
  } else {
    lastTapTime = now;
    singleTapTimer = setTimeout(() => {
      singleTapTimer = null;
      togglePlay();
    }, TAP_GAP);
  }
}

function togglePlay() {
  const idx = swiperIndex.value;
  if (isPlaying.value) {
    isPlaying.value = false;
    pauseAt(idx);
  } else {
    isPlaying.value = true;
    unlockAudio();
    playAt(idx);
  }
}

function doubleLike(video: Video, e: any) {
  spawnHeart(e);
  if (video.hasLiked !== 1) {
    handleLike(video);
  }
}

function spawnHeart(e: any) {
  let x: number;
  let y: number;
  if (e && e.detail && typeof e.detail.x === "number") {
    x = e.detail.x;
    y = e.detail.y;
  } else if (e && typeof e.clientX === "number") {
    x = e.clientX;
    y = e.clientY;
  } else {
    x = uni.upx2px(375);
    y = uni.upx2px(600);
  }
  const id = Date.now() + Math.floor(Math.random() * 1000);
  floatHearts.value.push({ id, x: x - 24, y: y - 24 });
  setTimeout(() => {
    floatHearts.value = floatHearts.value.filter((h) => h.id !== id);
  }, 800);
}

function onTimeUpdate(e: any) {
  const cur = e?.detail?.currentTime || 0;
  const dur = e?.detail?.duration || 0;
  if (dur > 0 && dur !== duration.value) duration.value = dur;
  const next = dur > 0 ? Math.min(100, Math.round((cur / dur) * 100)) : 0;
  if (next !== Math.round(progress.value)) {
    progress.value = next;
  }
}

/**
 * H5 上按 DOM 实测进度条热区的几何位置；其它端拿不到 document 时返回 false，
 * 拖动逻辑直接不生效（短视频 App/小程序仍可按原生的进度手势，不受影响）。
 */
function measureScrubBar(): boolean {
  if (!isH5 || typeof document === "undefined") return false;
  const box = document.querySelector(".progress-hit") as HTMLElement | null;
  if (!box) return false;
  const rect = box.getBoundingClientRect();
  scrubRectLeft = rect.left;
  scrubRectWidth = rect.width;
  return scrubRectWidth > 0;
}

function firstTouchX(e: any): number {
  const list = e?.touches || (e?.detail && e.detail.touches) || e?.changedTouches || [];
  return list[0] && typeof list[0].clientX === "number" ? list[0].clientX : 0;
}

function percentFromX(x: number): number {
  if (!scrubRectWidth) return scrubPercent.value;
  return Math.max(0, Math.min(100, ((x - scrubRectLeft) / scrubRectWidth) * 100));
}

function onScrubStart(e: any) {
  if (!measureScrubBar()) return;
  scrubbing.value = true;
  scrubPercent.value = percentFromX(firstTouchX(e));
}

function onScrubMove(e: any) {
  if (!scrubbing.value) return;
  scrubPercent.value = percentFromX(firstTouchX(e));
}

function onScrubEnd() {
  if (!scrubbing.value) return;
  scrubbing.value = false;
  seekTo(scrubPercent.value);
}

/** 按百分比定位播放位置；时长未知（还未 loadedmetadata）时什么都不做，避免把视频跳到 0 */
function seekTo(percent: number) {
  const el = videoEl(swiperIndex.value);
  const dur = duration.value || (el ? el.duration : 0);
  if (!el || !dur || !Number.isFinite(dur)) return;
  el.currentTime = (percent / 100) * dur;
  progress.value = Math.round(percent);
}

const scrubLabel = computed(() => {
  const total = duration.value || 0;
  const sec = Math.round((scrubPercent.value / 100) * total);
  const m = Math.floor(sec / 60);
  const s = sec % 60;
  return `${String(m).padStart(2, "0")}:${String(s).padStart(2, "0")}`;
});

async function handleLike(video: Video) {
  const wasLiked = video.hasLiked === 1;
  const prevCount = video.likeCount;
  try {
    if (wasLiked) {
      video.hasLiked = 0;
      video.likeCount = Math.max(0, video.likeCount - 1);
      const count = await dislikeVideo(video.id);
      if (typeof count === "number") video.likeCount = count;
    } else {
      video.hasLiked = 1;
      video.likeCount += 1;
      const count = await likeVideo(video.id);
      if (typeof count === "number") video.likeCount = count;
    }
  } catch {
    // 请求失败：回滚到请求前的精确值（不用 += 反推，避免请求期间计数被改导致回滚错误）
    video.hasLiked = wasLiked ? 1 : 0;
    video.likeCount = prevCount;
  }
}

async function handleFocus(video: Video) {
  const wasFocused = video.hasFocus === 1;
  // 取消关注时若处于关注频道会把视频从列表过滤；失败时需恢复列表，
  // 否则出现「hasFocus 已回滚但视频从列表消失」的界面不一致
  const followListBefore = wasFocused && activeChannel.value === "follow"
    ? channels["follow"].list.value
    : null;
  try {
    if (wasFocused) {
      video.hasFocus = 0;
      await unfocusVideoUser(video.userId);
      if (activeChannel.value === "follow") {
        const chState = channels["follow"];
        chState.list.value = chState.list.value.filter((v) => v.userId !== video.userId);
        const safeIdx = Math.min(swiperIndex.value, Math.max(0, chState.list.value.length - 1));
        swiperIndex.value = -1;
        swiperIndex.value = safeIdx;
        if (chState.list.value.length > 0) playAt(safeIdx);
      }
    } else {
      video.hasFocus = 1;
      await focusVideoUser(video.userId);
    }
    const focused = wasFocused ? 0 : 1;
    for (const ch of Object.values(channels)) {
      for (const v of ch.list.value) {
        if (v.userId === video.userId && v !== video) v.hasFocus = focused;
      }
    }
  } catch {
    video.hasFocus = wasFocused ? 1 : 0;
    // 恢复被过滤的关注频道列表
    if (followListBefore) channels["follow"].list.value = followListBefore;
  }
}

/** 打开评论抽屉时记录视频是否在播；抽屉内的语音评论与视频声音叠加，必须先暂停 */
let resumeAfterComment = false;

function showComments(video: Video) {
  activeCommentVideoId.value = video.id;
  commentVisible.value = true;
  resumeAfterComment = isPlaying.value;
  if (isPlaying.value) {
    isPlaying.value = false;
    pauseAt(swiperIndex.value);
  }
}

function closeComments() {
  commentVisible.value = false;
  if (resumeAfterComment) {
    resumeAfterComment = false;
    isPlaying.value = true;
    playAt(swiperIndex.value);
  }
}

function onVideoCommented() {
  const target = displayList.value.find((v) => v.id === activeCommentVideoId.value);
  if (target) target.commentCount += 1;
}

function openShare(video: Video) {
  shareVideo.value = video;
  showShare.value = true;
}

function doShare(channel: "wechat" | "moments" | "copy") {
  showShare.value = false;
  if (channel === "copy") {
    // 不能复制 MinIO 直链：那是内网 http 地址，分享出去对方打不开，还泄露内网 IP。
    // H5 复制当前页面链接；其它端暂用「页面 + 视频 ID」文案，等后端短链上线再替换。
    const id = shareVideo.value?.id || "";
    let data = shareVideo.value?.videoUrl || "";
    if (isH5 && typeof window !== "undefined") {
      data = `${window.location.origin}${window.location.pathname}#/pages/video/index?id=${id}`;
    } else {
      data = `我在心飞交友看到一个视频，视频 ID：${id}`;
    }
    uni.setClipboardData({
      data,
      success: () => info("链接已复制"),
    });
  } else {
    info("该分享方式即将上线");
  }
}

async function loadVideos() {
  const token = ++loadToken;
  const ch = activeChannel.value;
  const chState = channels[ch];
  const apiFn = getChannelApi(ch);

  swiperIndex.value = 0;
  // 1. 先用首屏缓存渲染（启动预取命中 / 二次进入 / 切回频道时 0 秒出画面）
  if (chState.list.value.length === 0) {
    const cached = readVideoCache(ch);
    if (cached.length) {
      chState.list.value = interleaveByAuthor(cached);
      isPlaying.value = true;
      nextTick(() => playAt(0));
    }
  }
  // 2. 有内容就后台刷新、不遮挡画面；完全无内容才显示可见加载层
  const blocking = displayList.value.length === 0;
  loading.value = blocking;
  loadFailed.value = false;
  slowLoading.value = false;
  if (slowLoadingTimer) clearTimeout(slowLoadingTimer);
  if (blocking) {
    slowLoadingTimer = setTimeout(() => {
      if (token === loadToken) slowLoading.value = true;
    }, 2500);
  }
  erroredVideoIds.value = new Set();

  try {
    chState.page.value = 1;
    chState.hasMore.value = true;
    // 迁移期双传：新契约读 pageSize，存量网关只认 pagesize；后端 xfjy-server 上线后删除 pagesize
    const query: Record<string, any> = { page: 1, pageSize: PAGE_SIZE, pagesize: PAGE_SIZE };
    if (ch === "location") {
      // 附近：先取坐标再请求；未授权时也继续请求，由后端回退全站热门（绝不静默空列表）
      nearbyFallback.value = false;
      const loc = await locateOnce();
      if (loc) {
        query.lat = loc.lat;
        query.lng = loc.lng;
      }
    }
    // 3. 复用 App 冷启动时的在途预取，避免同一份数据重复等 4~5s
    const inflight = ch === "recommend" ? getPrefetchPromise() : null;
    const res = inflight
      ? { items: await inflight }
      : await (apiFn as any)(query);
    if (token !== loadToken || activeChannel.value !== ch) return;
    nearbyFallback.value = Boolean((res as any).fallback);
    const items = res.items || [];
    if (items.length) {
      chState.list.value = arrangeForChannel(ch, items);
      writeVideoCache(ch, items);
      trimLoadedList(chState);
      isPlaying.value = true;
      // 分享链接带 id 时直接定位到目标视频；找不到才回到第一条
      if (!locateTargetVideo()) playAt(0);
    } else if (displayList.value.length === 0) {
      // 空结果由 empty-state 承担，不误报为网络失败
      chState.list.value = [];
    }
  } catch {
    // request 层统一 toast；缓存也拿不到时才给可重试的加载层
    if (token === loadToken && displayList.value.length === 0) {
      loadFailed.value = true;
    }
  } finally {
    if (token === loadToken) {
      loading.value = false;
      slowLoading.value = false;
      if (slowLoadingTimer) {
        clearTimeout(slowLoadingTimer);
        slowLoadingTimer = null;
      }
    }
  }
}

function appendUnique<T extends { id: string | number }>(target: T[], items: T[]): T[] {
  const seen = new Set(target.map((v) => v.id));
  return target.concat(items.filter((v) => !seen.has(v.id)));
}

/**
 * 裁掉超出上限的头部项，并同步 swiper 索引与 lastPlayedIndex。
 * 只删用户已经滑过去的部分，当前正在看的那条一定保留；
 * 索引变化后重播同一视频，避免 swiper current 变化造成画面错位。
 */
function trimLoadedList(chState: { list: { value: Video[] } }) {
  const overflow = chState.list.value.length - MAX_LOADED_ITEMS;
  if (overflow <= 0) return;
  const oldIndex = swiperIndex.value;
  chState.list.value = chState.list.value.slice(overflow);
  const newIndex = Math.max(0, oldIndex - overflow);
  swiperIndex.value = newIndex;
  lastPlayedIndex = lastPlayedIndex >= overflow ? lastPlayedIndex - overflow : -1;
  // 裁掉头部会让 swiper-item 以 :key 重建，video 元素随之重新挂载，必须重新起播
  nextTick(() => playAt(newIndex));
}

async function loadMoreVideos() {
  if (loadingMore.value) return;
  const channel = activeChannel.value;
  const chState = channels[channel];
  if (!chState.hasMore.value) return;
  const token = loadToken;
  const apiFn = getChannelApi(channel);

  loadingMore.value = true;
  try {
    const nextPage = chState.page.value + 1;
    // 迁移期双传：新契约读 pageSize，存量网关只认 pagesize；后端上线后删除 pagesize
    const query: Record<string, any> = { page: nextPage, pageSize: PAGE_SIZE, pagesize: PAGE_SIZE };
    if (channel === "location" && userLocation.value) {
      query.lat = userLocation.value.lat;
      query.lng = userLocation.value.lng;
    }
    const res = await (apiFn as any)(query);
    if (token !== loadToken || activeChannel.value !== channel) return;
    if (res.items && res.items.length > 0) {
      const before = chState.list.value.length;
      chState.list.value = mergeForChannel(channel, chState.list.value, res.items);
      trimLoadedList(chState);
      // 本页全为重复视频时同样推进页码，避免反复请求同一页
      chState.page.value = nextPage;
      if (chState.list.value.length === before) {
        chState.hasMore.value = false;
      }
    } else {
      chState.hasMore.value = false;
    }
  } catch {
    // request 层统一 toast
  } finally {
    loadingMore.value = false;
  }
}

const showSwipeHint = ref(false);
let swipeHintTimer: ReturnType<typeof setTimeout> | null = null;
watch(
  () => [currentVideo.value?.id, swiperIndex.value] as const,
  ([id, idx]) => {
    // 快速切换时旧 timer 会提前把新提示关掉，必须先清掉
    if (swipeHintTimer) {
      clearTimeout(swipeHintTimer);
      swipeHintTimer = null;
    }
    if (id && idx === 0) {
      showSwipeHint.value = true;
      swipeHintTimer = setTimeout(() => {
        showSwipeHint.value = false;
        swipeHintTimer = null;
      }, 4000);
    } else {
      showSwipeHint.value = false;
    }
  },
);

/** 分享链接携带的目标视频 ID（`#/pages/video/index?id=xxx`）；首屏数据到达后定位到它 */
const targetVideoId = ref("");

/**
 * 当前 targetVideoId 的来源，决定「目标不在当前频道列表」时的分流处理：
 * - share：分享链接冷启动，未找到给一次提示后放弃（既有行为，禁止改动）；
 * - search：搜索结果深链，目标是后端确认的有效公开视频，未找到则映射后插到当前频道头部再定位，
 *   不能像分享入口那样给「未找到」死路。
 */
const targetVideoSource = ref<"share" | "search">("share");

/** 搜索深链携带的原始搜索条目，仅 source=search 且需要插入头部时用于字段映射 */
const pendingSearchItem = ref<VideoSearchItem | null>(null);

onLoad((query: any) => {
  const id = query?.id;
  if (typeof id === "string" && id) {
    targetVideoId.value = id;
  }
});

// H5 兜底：实测（2026-11-06）H5 冷启动直接打开 `#/pages/video/index?id=xxx` 时 onLoad 拿不到 query，
// 这里直接解析 hash，保证分享链接在任何情况下都能定位到目标视频。
if (isH5 && typeof window !== "undefined") {
  const qs = window.location.hash.split("?")[1] || "";
  const id = new URLSearchParams(qs).get("id");
  if (id) {
    targetVideoId.value = id;
  }
}

/**
 * H5 无手势起播兜底：非静音 autoplay 会被浏览器拦截导致画面停在第一帧，
 * 退回「静音起播」（muted autoplay 是策略唯一允许的自动播放），用户任意触碰屏幕即由 unlockAudio 解锁。
 * 分享冷启动与搜索深链插入头部两条路径共用，避免平行维护两套起播逻辑。
 */
function h5MutedAutoplayFallback(index: number): void {
  if (!isH5) return;
  setTimeout(() => {
    const el = videoEl(index);
    if (!el || !el.paused) return;
    isMuted.value = true;
    el.muted = true;
    const p = el.play();
    if (p && typeof p.catch === "function") p.catch(() => {});
  }, 360);
}

/**
 * 把搜索条目显式映射为视频页 Video。
 * 两个类型字段不同源（VideoSearchVo 仅搜索瀑布流所需字段，VideoVo 是播放页完整字段），
 * 逐字段映射、缺项给安全默认值，禁止 as any 强转。
 *
 * 播放地址由 TASK-20261106-04 全链路透传（搜索 DTO/VO 补 videoUrl）；
 * 保留空值兜底防御脏数据，为空时视频走既有「加载失败点此重试」浮层。
 */
function mapSearchItemToVideo(item: VideoSearchItem): Video {
  return {
    id: item.id,
    // Video.userId 为 string，搜索 authorId 为 number|null；无作者时给空串，侧栏关注类操作不会误调接口
    userId: item.authorId === null || item.authorId === undefined ? "" : String(item.authorId),
    nickname: item.authorName || "用户",
    // 搜索 VO 无头像字段，给空串由 image 组件走自身空态，不编造地址
    avatar: "",
    signature: item.title || "小视频",
    cover: item.cover || "",
    // 播放地址来自搜索接口透传（TASK-20261106-04）；|| "" 兜底防御脏数据
    videoUrl: item.videoUrl || "",
    hasFocus: 0,
    hasLiked: 0,
    likeCount: item.likeCount,
    commentCount: 0,
  };
}

/**
 * 搜索深链目标不在当前频道列表时：映射后插到当前频道列表头部并定位到 0。
 * 搜索结果是后端按公开+未删除条件过滤后的有效视频，不能给用户「未找到」死路。
 */
function insertSearchVideoToHead(item: VideoSearchItem): void {
  const chState = channels[activeChannel.value];
  // 防御性去重：正常走到这里说明 findIndex 未命中，这里再挡一次并发/重复消费
  const existIdx = chState.list.value.findIndex((v) => v.id === item.id);
  if (existIdx >= 0) {
    swiperIndex.value = existIdx;
    nextTick(() => {
      playAt(existIdx);
      h5MutedAutoplayFallback(existIdx);
    });
    return;
  }
  chState.list.value = [mapSearchItemToVideo(item), ...chState.list.value];
  swiperIndex.value = 0;
  isPlaying.value = true;
  progress.value = 0;
  erroredVideoIds.value = new Set();
  nextTick(() => {
    playAt(0);
    h5MutedAutoplayFallback(0);
  });
}

/**
 * 首屏数据就绪后定位到目标视频（分享链接与搜索深链共用入口）。
 * 只在当前已加载列表里找：
 * - 分享来源：目标可能已下架或不在推荐池，未找到给一次提示后放弃，避免每次进页面反复提示；
 * - 搜索来源：目标是有效公开视频，未找到则映射插到当前频道头部再定位（不给死路）。
 * 无论命中与否，targetVideoId/source/pendingSearchItem 都一次性消费并复位，防止下次 onShow 重复定位。
 */
function locateTargetVideo(): boolean {
  if (!targetVideoId.value) return false;
  const idx = displayList.value.findIndex((v) => v.id === targetVideoId.value);
  const source = targetVideoSource.value;
  targetVideoId.value = "";
  targetVideoSource.value = "share";

  if (idx < 0) {
    if (source === "search") {
      const item = pendingSearchItem.value;
      pendingSearchItem.value = null;
      if (item) {
        insertSearchVideoToHead(item);
        return true;
      }
    }
    // 分享入口既有行为：提示后放弃（搜索分支因数据缺失拿不到 item 时也安全降级到这里）
    info("分享的视频未找到，可能已下架");
    return false;
  }

  pendingSearchItem.value = null;
  swiperIndex.value = idx;
  nextTick(() => {
    playAt(idx);
    h5MutedAutoplayFallback(idx);
  });
  return true;
}

/**
 * 消费 system store 里的搜索深链（onShow 最先执行）。
 * tab 页 onLoad 全生命周期只触发一次、switchTab 又不支持 URL 参数，
 * 搜索入口只能经 store 中转：取走后立刻清 store，保证本次 onShow 之后切走再切回不会重复强制定位。
 */
function consumePendingDeepLink(): void {
  const pending = systemStore.pendingVideoDeepLink;
  if (!pending) return;
  targetVideoId.value = pending.videoId;
  targetVideoSource.value = pending.source;
  pendingSearchItem.value = pending.sourceItem;
  systemStore.clearPendingVideoDeepLink();
}

onShow(() => {
  // 先消费搜索深链：把 store 里的待定视频转成 targetVideoId/source，随后本函数两条分支
  // （空列表走 loadVideos 内定位 / 有列表走下方 nextTick 定位）都会经过 locateTargetVideo 统一处理。
  consumePendingDeepLink();
  // H5：文档级首次手势监听（capture 阶段，先于业务 click/tap；不写 preventDefault，不拦截事件）。
  // 用户触碰屏幕任意处即解锁音频——覆盖点频道 Tab（切频道后的第一个视频也要带声）、点封面、
  // 滑动等所有入口，根治「每个频道第一个视频全程静音且无提示」。配对注销见 onHide。
  if (isH5) {
    document.addEventListener("pointerdown", unlockAudio, true);
  }
  // 冷启动 / 缓存命中：先渲染本地首屏，再决定是否后台刷新
  if (displayList.value.length === 0) {
    const cached = readVideoCache(activeChannel.value);
    if (cached.length) {
      channels[activeChannel.value].list.value = arrangeForChannel(activeChannel.value, cached);
    }
  }
  if (displayList.value.length === 0) {
    loadVideos();
  } else {
    // 回到页面/首次显示：无条件恢复当前视频。
    // 旧实现只在 wasPlayingBeforeHide 为 true 时恢复，而 :autoplay 对已挂载元素不会二次触发，
    // 实测从「我的」切回视频页会停在 paused+muted，必须在这里显式 play。
    nextTick(() => {
      if (commentVisible.value) return;
      isPlaying.value = true;
      // 缓存命中时 loadVideos 不会跑（列表已有内容），分享定位必须在这里补一次，
      // 否则带 ?id= 打开永远停在第一条。
      if (!locateTargetVideo()) playAt(swiperIndex.value);
    });
  }
});

onHide(() => {
  // 与 onShow 配对：离开视频页即注销文档级监听，避免跨页面残留
  if (isH5) {
    document.removeEventListener("pointerdown", unlockAudio, true);
  }
  if (isPlaying.value) {
    isPlaying.value = false;
    pauseAt(swiperIndex.value);
  }
});

onShareAppMessage(() => ({
  title: "心飞交友 · 遇见心动的人",
  path: "/pages/match/index",
}));
</script>

<style lang="scss" scoped>
/* ============================================================
   视觉基线冻结表（2026-11-06 用户裁决，优先级最高）
   ------------------------------------------------------------
   本页对标抖音沉浸播放页，色值只允许以下三个 token：
     --video-accent  #fe2c55  抖音红：点赞红心/关注+/双击爱心/加载重试等一切强调色
     --video-bg      #000000  纯黑底：页面/加载态/音乐唱片底色
     --text-inverse  #ffffff  纯白：图标/文字/频道选中条/进度条
   黑白透明叠层 rgba(0,0,0,x) / rgba(255,255,255,x) 是抖音同款遮罩语言，允许使用。
   第三方品牌色（微信 #07c160 / 朋友圈 #ff8200）仅分享面板保留。
   基线出处：git show 6d4497b:xfjy-app/src/pages/video/index.vue（948 行抖音对标版）。
   收敛说明：原版双击爱心 #ff2d55 与点赞/关注 #fe2c55 视觉等价，统一为 --video-accent；
             原版音乐唱片 #111 收敛为 --video-bg，靠白色描边勾勒轮廓。
   ------------------------------------------------------------
   【冻结声明】本文件样式只接受 bug 修复；任何视觉/色值/尺寸/布局改动必须经用户单独裁决，
   UX 审查批次不得顺手修改本文件样式。模板结构与 script 逻辑改动不受此限但须最小化。
   ============================================================ */
.video-page {
  height: calc(100vh - var(--tab-bar-height, 0px));
  height: calc(100dvh - var(--tab-bar-height, 0px));
  /* 冻结表：播放页永恒纯黑，不随系统暗色模式翻转 */
  background: var(--video-bg);
  position: relative;
}

/* ===== 首屏加载态 =====
   视频页是纯黑底，旧的暗色骨架几乎与背景同色，4~5s 内用户只看到纯黑。
   这里用抖音红光晕 + 旋转指示 + 明确文案，让等待「看得见、有解释、可重试」。 */
.video-loading {
  position: absolute;
  inset: 0;
  z-index: 25;
  display: flex;
  align-items: center;
  justify-content: center;
  background: var(--video-bg);
  overflow: hidden;
}

.video-loading__glow {
  position: absolute;
  width: 620rpx;
  height: 620rpx;
  border-radius: 50%;
  /* 冻结表：抖音红同系光晕（废弃粉紫 #ff4d8d/#8a5cf6 已清退），rgb 分量走 --video-accent-rgb */
  background: radial-gradient(circle, rgba(var(--video-accent-rgb), 0.28) 0%, rgba(var(--video-accent-rgb), 0.12) 38%, transparent 72%);
  animation: loadingGlow 3.2s ease-in-out infinite;
}

.video-loading__content {
  position: relative;
  display: flex;
  flex-direction: column;
  align-items: center;
  padding: 0 72rpx;
  text-align: center;
}

.video-loading__spinner {
  width: 76rpx;
  height: 76rpx;
  margin-bottom: 40rpx;
  border-radius: 50%;
  border: 5rpx solid rgba(255, 255, 255, 0.16);
  /* 冻结表：抖音式单色 spinner——顶环抖音红，其余环与基础环同为白色半透明 */
  border-top-color: var(--video-accent);
  border-right-color: rgba(255, 255, 255, 0.16);
  animation: loadingSpin 0.9s linear infinite;
}

.video-loading__title {
  font-size: var(--fs-lg, 32rpx);
  font-weight: 600;
  color: var(--text-inverse);
  letter-spacing: 1rpx;
}

.video-loading__desc {
  margin-top: 16rpx;
  font-size: var(--fs-sm, 24rpx);
  line-height: 1.6;
  color: rgba(255, 255, 255, 0.62);
}

.video-loading__retry {
  margin-top: 44rpx;
  padding: 16rpx 56rpx;
  border-radius: 999rpx;
  font-size: var(--fs-md, 28rpx);
  color: var(--text-inverse);
  /* 冻结表：抖音按钮范式为红底白字实色，不用品牌粉橙渐变 */
  background: var(--video-accent);
}

@keyframes loadingSpin {
  to { transform: rotate(360deg); }
}

@keyframes loadingGlow {
  0%, 100% { transform: scale(0.92); opacity: 0.75; }
  50% { transform: scale(1.08); opacity: 1; }
}

@media (prefers-reduced-motion: reduce) {
  .video-loading__glow,
  .video-loading__spinner { animation: none !important; }
}

/* ===== 顶部 Tab ===== */
.top-tabs {
  position: absolute;
  top: calc(env(safe-area-inset-top, 0px) + 16rpx);
  left: 0;
  right: 0;
  display: flex;
  align-items: stretch;
  /* 右侧刷新/搜索两个图标按钮的间距 */
  gap: var(--space-xs);
  height: var(--category-bar-h);
  padding: 0 var(--space-md);
  z-index: 30;

  .tab-group {
    flex: 1;
    display: flex;
    justify-content: center;
    align-items: stretch;
    gap: var(--space-xl);
  }

  .tab {
    position: relative;
    display: flex;
    align-items: center;
    /* 非选中：高亮白降至 60%（叠在深色画面上呈中性灰），字重 400 作次级层级 */
    font-size: var(--fs-lg);
    font-weight: 400;
    color: var(--text-inverse);
    opacity: 0.6;
    padding: var(--space-xs) var(--space-xs);
    /* 冻结表：抖音选中只变字重/透明度、不变字号，transition 不含 font-size */
    transition: opacity 0.2s ease;

    /* 选中：三通道区分——字重 700 + 纯白（opacity 1）+ 底部白色短条（对标抖音 40×6rpx） */
    &.active {
      opacity: 1;
      font-weight: 700;

      &::after {
        content: "";
        position: absolute;
        left: 50%;
        bottom: 0;
        transform: translateX(-50%);
        width: 40rpx;
        height: 6rpx;
        border-radius: 3rpx;
        /* 冻结表：频道选中条纯白，不用品牌渐变 */
        background: var(--text-inverse);
      }
    }
  }
}

/* 顶部右侧图标按钮（刷新 / 搜索）：88rpx 热区满足 §7.1 独立功能按钮要求。
   用 padding 撑热区而非写死 width/height——写死尺寸会把热区限死在图标大小，
   而 a11y-button 的 default 变体是零样式壳，不自带 min-height。
   视觉上仍是 48rpx 图标，热区由 padding 外扩到 88rpx。 */
.side-btn {
  display: flex;
  align-items: center;
  justify-content: center;
  /* 88rpx 热区 = 48rpx 图标 + 上下各 20rpx padding */
  min-width: 88rpx;
  min-height: 88rpx;
  padding: 20rpx;
  box-sizing: border-box;
  flex-shrink: 0;

  .side-ico {
    width: 48rpx;
    height: 48rpx;
    color: var(--text-inverse);
  }

  /* 刷新中的图标旋转：与 a11y-button 的 loading spinner 并存，
     图标本身转圈比spinner更贴合「刷新」语义 */
  .is-spinning {
    animation: refreshSpin 0.8s linear infinite;
  }
}

@keyframes refreshSpin {
  to { transform: rotate(360deg); }
}

@media (prefers-reduced-motion: reduce) {
  .is-spinning { animation: none; }
}

/* H5 静音提示：右上角浮层，仅 muted 起播期间显示，解锁后随 v-if 移除。
   视频画面明暗不定，浮层沿用本页 nearby-tip/video-error 的黑色半透明压暗范式；
   图标色走 --text-inverse 以适配暗色视频底。88rpx 热区满足 §7.1 独立功能按钮要求。
   必须带「轻触开启声音」文案：纯图标会被用户理解成「已静音」而不是「可开启」。 */
.muted-hint {
  position: absolute;
  top: calc(env(safe-area-inset-top, 0px) + 16rpx + var(--category-bar-h) + 16rpx);
  right: var(--space-md);
  z-index: 30;
  box-sizing: border-box;
  display: flex;
  align-items: center;
  justify-content: center;
  width: auto;
  min-width: 88rpx;
  height: 88rpx;
  padding: 0 var(--space-md);
  gap: var(--space-sm);
  border-radius: var(--radius-pill);
  background: rgba(0, 0, 0, 0.55);

  .muted-hint__ico {
    width: 40rpx;
    height: 40rpx;
    color: var(--text-inverse);
  }

  .muted-hint__text {
    font-size: var(--fs-sm);
    line-height: 1;
    color: var(--text-inverse);
    white-space: nowrap;
  }
}

.video-swiper {
  height: calc(100vh - var(--tab-bar-height, 0px));
  height: calc(100dvh - var(--tab-bar-height, 0px));
}

.video-item {
  position: relative;
  height: 100%;

  .video-player,
  .cover {
    position: absolute;
    inset: 0;
    width: 100%;
    height: 100%;
  }

  .cover-residue {
    position: absolute;
    inset: 0;
    width: 100%;
    height: 100%;
    z-index: 1;
  }
}

/* 透明点击层 */
.tap-layer {
  position: absolute;
  inset: 0;
  z-index: 5;
}

/* 中央播放指示 */
.center-state {
  position: absolute;
  inset: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  z-index: 6;
  pointer-events: none;

  .play-icon {
    width: 96rpx;
    height: 96rpx;
    color: var(--text-inverse);
    opacity: 0.85;
    filter: drop-shadow(0 4rpx 16rpx rgba(0, 0, 0, 0.4));
  }
}

/* 播放失败浮层（a11y-button：可点击重试，满足 §7.1 不新增裸 view 按钮） */
.video-error {
  position: absolute;
  inset: 0;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: var(--space-md);
  z-index: 7;
  background: rgba(0, 0, 0, 0.6);

  /* 全屏错误层不能跟随普通按钮缩放，否则整块画面抖动 */
  &:active {
    transform: none;
  }

  .video-error-title {
    font-size: var(--fs-md);
    color: var(--text-inverse);
    text-shadow: 0 2rpx 8rpx rgba(0, 0, 0, 0.5);
  }

  .video-error-retry {
    display: inline-block;
    padding: var(--space-md) 48rpx;
    border: 2rpx solid var(--text-inverse);
    border-radius: var(--radius-pill);
    font-size: var(--fs-base);
    color: var(--text-inverse);
    background: rgba(255, 255, 255, 0.12);
    opacity: 0.9;
  }
}

/* 双击飞心（抖音式大爱心跳弹） */
.float-heart {
  position: absolute;
  z-index: 20;
  pointer-events: none;
  transform: translate(-50%, -50%);
  animation: floatUp 0.8s cubic-bezier(0.22, 1, 0.36, 1) forwards;

  .float-heart__ico {
    width: 96rpx;
    height: 96rpx;
    /* 冻结表：双击爱心抖音红（原版 #ff2d55，统一收敛为 --video-accent） */
    color: var(--video-accent);
    filter: drop-shadow(0 4rpx 16rpx rgba(var(--video-accent-rgb), 0.4));
  }
}

@keyframes floatUp {
  0% {
    transform: translate(-50%, -50%) scale(0) rotate(-15deg);
    opacity: 1;
  }
  30% {
    transform: translate(-50%, -70%) scale(1.4) rotate(5deg);
    opacity: 1;
  }
  60% {
    transform: translate(-50%, -100%) scale(1.1) rotate(-3deg);
    opacity: 0.9;
  }
  100% {
    transform: translate(-50%, -160%) scale(0.8) rotate(0deg);
    opacity: 0;
  }
}

/* ===== 右侧操作栏 ===== */
.side-bar {
  position: absolute;
  right: var(--space-md);
  bottom: calc(env(safe-area-inset-bottom, 0px) + 36rpx);
  z-index: 10;
  display: flex;
  flex-direction: column;
  align-items: center;

  .side-stack {
    display: flex;
    flex-direction: column;
    align-items: center;
    gap: 36rpx;
  }

  .side-btn {
    position: relative;
    display: flex;
    flex-direction: column;
    align-items: center;
    gap: var(--space-xs);

    .side-ico {
      width: 80rpx;
      height: 80rpx;
      display: flex;
      align-items: center;
      justify-content: center;
    }

    .side-ico__ph {
      width: 64rpx;
      height: 64rpx;
      color: var(--text-inverse);
    }

    .side-ico__ph--liked {
      /* 冻结表：点赞激活红心抖音红（原版 #fe2c55） */
      color: var(--video-accent);
    }

    .like-pop {
      animation: likePop 0.35s ease;
    }

    @keyframes likePop {
      0% { transform: scale(1); }
      35% { transform: scale(1.35); }
      70% { transform: scale(0.9); }
      100% { transform: scale(1); }
    }

    .side-count {
      font-size: var(--fs-sm);
      color: var(--text-inverse);
      text-shadow: 0 2rpx 8rpx rgba(0, 0, 0, 0.5);
      font-weight: 500;
    }

    .side-avatar {
      width: 84rpx;
      height: 84rpx;
      border-radius: 50%;
      border: 4rpx solid var(--text-inverse);
    }

    .follow-plus {
      position: absolute;
      bottom: -8rpx;
      left: 50%;
      transform: translateX(-50%);
      width: 32rpx;
      height: 32rpx;
      /* 冻结表：关注加号抖音红实色 + 同色投影（对标原版 0 4rpx 12rpx rgba(254,44,85,.5)） */
      background: var(--video-accent);
      border-radius: 50%;
      display: flex;
      align-items: center;
      justify-content: center;
      box-shadow: 0 4rpx 12rpx rgba(var(--video-accent-rgb), 0.5);

      .follow-plus__ico {
        width: 20rpx;
        height: 20rpx;
        color: var(--text-inverse);
      }
    }
  }
}

/* ===== 底部信息区 ===== */
.video-info {
  position: absolute;
  left: var(--space-md);
  /* 右侧操作栏固定占位：不用固定 150rpx，改用 min() 兼顾宽屏
     （窄屏下 150rpx 会吃掉约 47% 宽度，信息区被压得只剩几十字） */
  right: min(150rpx, 20vw);
  /* 让长昵称/文案能正常折行而不溢出 */
  min-width: 0;
  bottom: calc(env(safe-area-inset-bottom, 0px) + 40rpx);
  z-index: 10;

  .author {
    font-size: var(--fs-xl);
    font-weight: 700;
    color: var(--text-inverse);
    display: block;
    margin-bottom: var(--space-sm);
    text-shadow: 0 2rpx 8rpx rgba(0, 0, 0, 0.5);
  }

  .desc {
    font-size: var(--fs-base);
    color: var(--text-inverse);
    opacity: 0.9;
    line-height: 1.5;
    display: block;
    text-shadow: 0 2rpx 8rpx rgba(0, 0, 0, 0.5);
  }

  .location-label {
    display: block;
    margin-bottom: var(--space-xs);
    font-size: var(--fs-sm);
    color: var(--text-inverse);
    opacity: 0.85;
    text-shadow: 0 2rpx 8rpx rgba(0, 0, 0, 0.5);
  }

  .music-row {
    display: flex;
    align-items: center;
    margin-top: var(--space-sm);

    .music-disc {
      flex-shrink: 0;
      width: 56rpx;
      height: 56rpx;
      border-radius: 50%;
      /* 冻结表：唱片底色收敛到播放页纯黑（原版 #111），靠白色描边勾勒轮廓 */
      background: var(--video-bg);
      border: 4rpx solid var(--text-inverse);
      display: flex;
      align-items: center;
      justify-content: center;
      overflow: hidden;
      margin-right: 12rpx;

      .disc-img {
        width: 36rpx;
        height: 36rpx;
        border-radius: 50%;
      }

      &.disc-paused .disc-img {
        animation-play-state: paused;
      }
    }

    .music-track {
      flex: 1;
      overflow: hidden;
      /* 音轨宽度跟随父容器自适应；min-width:0 允许在 flex 中收缩，
         否则长音频名会把信息区撑破（BUG-09 收口） */
      min-width: 0;
      max-width: 360rpx;

      .music-text {
        display: inline-block;
        white-space: nowrap;
        font-size: var(--fs-sm);
        color: var(--text-inverse);
        opacity: 0.9;
        padding-left: 100%;
      }
    }
  }
}

.video-item.is-active .disc-img {
  animation: spin 5s linear infinite;
}

.video-item.is-active .music-text {
  animation: marquee 8s linear infinite;
}

/* 暂停时唱片与歌词必须同时停：原规则只有 .disc-paused 一个类，
   优先级低于上面的 .video-item.is-active .disc-img，实际暂停后唱片仍在转。 */
.video-item.is-active .music-disc.disc-paused .disc-img,
.video-item.is-active .music-text.text-paused {
  animation-play-state: paused;
}

@keyframes spin {
  to { transform: rotate(360deg); }
}

@keyframes marquee {
  0% { transform: translateX(0); }
  100% { transform: translateX(-100%); }
}

/* ===== 进度条 ===== */
/* 拖动热区：88rpx 盖住 4rpx 细条，手指才点得准；层级高于透明点击层(5)、低于右侧操作栏(10)，
   所以底部这一条只用于拖进度，不会误触单击暂停，也不会挡住侧栏按钮 */
.progress-hit {
  position: absolute;
  left: 0;
  right: 0;
  bottom: 0;
  height: 88rpx;
  z-index: 8;

  /* 拖动中实时跟手，去掉 0.2s 平滑（否则手感发飘） */
  &.is-scrubbing .progress-inner {
    transition: none;
  }

  .scrub-time {
    position: absolute;
    bottom: 44rpx;
    left: 50%;
    transform: translateX(-50%);
    padding: 4rpx 16rpx;
    border-radius: var(--radius-pill);
    background: rgba(0, 0, 0, 0.6);
    color: var(--text-inverse);
    font-size: 24rpx;
    font-variant-numeric: tabular-nums;
  }
}

.progress-bar {
  position: absolute;
  left: 0;
  right: 0;
  bottom: 0;
  height: 4rpx;
  background: rgba(255, 255, 255, 0.2);
  z-index: 6;
  pointer-events: none;

  .progress-inner {
    /* 满宽 + scaleX 模拟进度：timeupdate 高频触发，transform 只走合成层不触发布局；
       视觉与 width 百分比等价，冻结表约束（纯白细条）不变 */
    width: 100%;
    height: 100%;
    /* 冻结表：播放进度条纯白细条（抖音范式），不用品牌渐变 */
    background: var(--text-inverse);
    transform-origin: left center;
    transition: transform 0.2s linear;
  }

  /* 拖动把手：跨在细条上的圆点，只在拖动时出现 */
  .progress-thumb {
    position: absolute;
    top: 50%;
    width: 20rpx;
    height: 20rpx;
    margin-left: -10rpx;
    border-radius: 50%;
    /* 冻结表：拖动把手与进度条同为纯白 */
    background: var(--text-inverse);
    transform: translateY(-50%);
  }
}

/* ===== 顶部/底部暗角 ===== */
.top-scrim {
  position: absolute;
  top: 0;
  left: 0;
  right: 0;
  height: 200rpx;
  background: linear-gradient(to bottom, rgba(0, 0, 0, 0.5), transparent);
  z-index: 8;
  pointer-events: none;
}

.bottom-scrim {
  position: absolute;
  left: 0;
  right: 0;
  bottom: 0;
  height: 440rpx;
  background: linear-gradient(to top, rgba(0, 0, 0, 0.62), transparent);
  z-index: 8;
  pointer-events: none;
}

/* ===== 上滑提示 ===== */
.swipe-hint {
  position: absolute;
  left: 50%;
  transform: translateX(-50%);
  bottom: calc(env(safe-area-inset-bottom, 0px) + 280rpx);
  z-index: 11;
  display: flex;
  flex-direction: column;
  align-items: center;
  pointer-events: none;
  /* ease-out-quart 曲线替代 ease-in-out 回弹感，仅上下轻提示，不抢视频内容 */
  animation: hintFloat 1.6s cubic-bezier(0.25, 1, 0.5, 1) infinite;

  .swipe-triangle {
    width: 36rpx;
    height: 18rpx;
    margin-bottom: 2rpx;
  }

  .swipe-text {
    font-size: var(--fs-sm);
    color: var(--text-inverse);
    opacity: 0.9;
    text-shadow: 0 2rpx 6rpx rgba(0, 0, 0, 0.5);
  }
}

@keyframes hintFloat {
  0%, 100% { transform: translateX(-50%) translateY(0); }
  50% { transform: translateX(-50%) translateY(-10rpx); }
}

@media (prefers-reduced-motion: reduce) {
  .swipe-hint { animation: none; }
  .music-text { animation: none; }
}

/* ===== 附近：回退提示与授权引导 ===== */
.nearby-tip {
  position: absolute;
  top: calc(env(safe-area-inset-top, 0px) + 100rpx);
  left: var(--space-md);
  right: var(--space-md);
  z-index: 20;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--space-sm);
  padding: 16rpx 20rpx;
  border-radius: 16rpx;
  background: rgba(0, 0, 0, 0.55);

  &__text {
    flex: 1;
    min-width: 0;
    font-size: var(--fs-sm);
    color: var(--text-inverse);
    opacity: 0.9;
  }

  &__btn {
    flex-shrink: 0;
    padding: 8rpx 20rpx;
    border-radius: 999rpx;
    font-size: var(--fs-sm);
    color: var(--text-inverse);
    background: var(--brand-primary);
  }
}

.nearby-auth {
  position: absolute;
  inset: 0;
  z-index: 15;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: var(--space-sm);
  padding: 0 48rpx;
  text-align: center;

  &__title {
    font-size: var(--fs-xl);
    font-weight: 700;
    color: var(--text-inverse);
  }

  &__desc {
    font-size: var(--fs-base);
    color: var(--text-inverse);
    opacity: 0.8;
  }

  &__btn {
    margin-top: var(--space-sm);
    padding: 16rpx 40rpx;
    border-radius: 999rpx;
    font-size: var(--fs-base);
    color: var(--text-inverse);
    background: var(--brand-primary);
  }
}

/* ===== 底部分享面板 ===== */
.share-mask {
  position: fixed;
  inset: 0;
  background: rgba(0, 0, 0, 0.5);
  z-index: 999;
  display: flex;
  align-items: flex-end;

  .share-panel-inner {
    width: 100%;
    background: var(--bg-card);
    border-radius: var(--radius-xl) var(--radius-xl) 0 0;
    padding: var(--space-lg) var(--space-lg) calc(var(--space-lg) + env(safe-area-inset-bottom));
    animation: slideUp 0.25s ease;

    .share-title {
      text-align: center;
      font-size: var(--fs-md);
      font-weight: 600;
      color: var(--text-primary);
      margin-bottom: var(--space-lg);
    }

    .share-grid {
      display: flex;
      justify-content: space-around;
      margin-bottom: var(--space-md);

      .share-item {
        display: flex;
        flex-direction: column;
        align-items: center;
        gap: 12rpx;
        min-height: 48rpx;

        .share-ico {
          width: 96rpx;
          height: 96rpx;
          border-radius: 50%;
          display: flex;
          align-items: center;
          justify-content: center;
          color: var(--text-inverse);
          font-size: 36rpx;

          &.ico-wechat { background: #07c160; }
          &.ico-moments { background: #ff8200; }
          /* 冻结表：复制链接中性灰（原版废弃冷粉 #ff5e9c 清退）；微信/朋友圈为第三方品牌色保留 */
          &.ico-copy { background: var(--text-hint); }
        }

        .share-label {
          font-size: var(--fs-sm);
          color: var(--text-secondary);
        }
      }
    }

    .share-cancel {
      display: flex;
      align-items: center;
      justify-content: center;
      font-size: var(--fs-md);
      color: var(--text-hint);
      padding: var(--space-md) 0;
      min-height: 88rpx;
      border-top: 1rpx solid var(--border-faint);
      margin-top: var(--space-xs);
    }
  }
}

@keyframes slideUp {
  from { transform: translateY(100%); }
  to { transform: translateY(0); }
}
</style>
