<template>
  <view class="video-page">
    <!-- 顶部 关注 / 推荐 Tab（role=tablist 语义 + 可键盘操作） -->
    <view class="top-tabs" role="tablist" aria-label="视频频道">
      <view class="tab-group">
        <a11y-button
          v-for="ch in CHANNELS"
          :key="ch.key"
          class="tab"
          role="tab"
          :checked="activeChannel === ch.key"
          :label="ch.label"
          @tap="switchChannel(ch.key)"
        >{{ ch.label }}</a11y-button>
      </view>
      <a11y-button class="search-btn" label="搜索" @tap="onSearch">
        <uni-icons type="search" size="28" color="#fff"></uni-icons>
      </a11y-button>
    </view>

    <swiper
      class="video-swiper"
      vertical
      :current="swiperIndex"
      @change="onSwiperChange"
    >
      <swiper-item v-for="(video, idx) in displayList" :key="video.id">
        <!-- is-active 仅当前条目：唱片旋转/跑马灯等无限动画只在此条目运行，避免多个动画并发渲染卡顿 -->
        <view class="video-item" :class="{ 'is-active': idx === swiperIndex }">
          <!-- 当前及相邻 item 常驻 video 节点（±1 窗口），滑入即有缓存节点，避免每次滑动销毁重建原生播放器；远处的 item 退回封面图控制内存 -->
          <template v-if="Math.abs(idx - swiperIndex) <= 1">
            <video
              :id="'vid-' + video.id"
              :key="video.id + '-' + (videoRetryTick[video.id] || 0)"
              class="video-player"
              :src="video.videoUrl"
              :autoplay="idx === swiperIndex"
              loop
              :muted="isMuted"
              :controls="false"
              object-fit="cover"
              @timeupdate="idx === swiperIndex ? onTimeUpdate($event) : null"
              @play="onVideoPlay(idx)"
              @pause="onVideoPause(idx)"
              @error="onVideoError(video)"
            />
            <!-- 余像：窗口内非当前 item 用封面盖住未起播的黑块 -->
            <image
              v-if="idx !== swiperIndex"
              class="cover-residue"
              :src="video.cover"
              mode="aspectFill"
              aria-hidden="true"
            />
          </template>
          <image v-else class="cover" :src="video.cover" mode="aspectFill" lazy-load :aria-label="`${video.nickname} 的视频封面`" />

          <!-- 透明点击层：单击暂停/播放，双击点赞（纯手势层，另有显式按钮） -->
          <view class="tap-layer" aria-hidden="true" @click="onVideoTap(video, $event)" />

          <!-- 中央播放/暂停指示（暂停时显示大播放键：CSS 三角，避免安卓把 ▶ 渲染成 emoji） -->
          <view v-if="currentId === video.id && !isPlaying && !erroredVideoIds.has(video.id)" class="center-state" aria-hidden="true">
            <view class="play-icon" />
          </view>

          <!-- 播放失败浮层：点击重试（重挂载 video 节点重新加载） -->
          <view
            v-if="video.id === currentId && erroredVideoIds.has(video.id)"
            class="video-error"
            role="alert"
            aria-label="视频加载失败，点击重试"
            @tap="retryVideo(video)"
          >
            <text class="video-error-title">视频加载失败</text>
            <view class="video-error-retry" aria-hidden="true">点此重试</view>
          </view>

          <!-- 双击飞出的爱心（纯装饰动画） -->
          <view
            v-for="h in floatHearts"
            :key="h.id"
            class="float-heart"
            :style="{ left: h.x + 'px', top: h.y + 'px' }"
            aria-hidden="true"
          ><uni-icons type="heart-filled" size="20" color="#ff5e9c"></uni-icons></view>

          <!-- 右侧操作栏（完全模仿抖音：头像+关注 / 点赞 / 评论 / 分享 / 静音 / 旋转唱片），底缘与左侧文字区对齐 -->
          <view class="side-bar">
            <view class="side-stack">
              <!-- 作者头像 + 关注（抖音置于栏顶） -->
              <a11y-button
                class="side-btn btn-feedback follow-btn"
                :label="video.hasFocus ? `取消关注 ${video.nickname}` : `关注 ${video.nickname}`"
                @tap="handleFocus(video)"
              >
                <image class="side-avatar" :src="video.avatar" mode="aspectFill" lazy-load aria-hidden="true" />
                <view v-if="!video.hasFocus" class="follow-plus" aria-hidden="true">+</view>
              </a11y-button>

              <a11y-button
                class="side-btn btn-feedback"
                :label="video.hasLiked === 1 ? `取消点赞，当前 ${video.likeCount} 赞` : `点赞，当前 ${video.likeCount} 赞`"
                @tap="handleLike(video)"
              >
                <view class="side-ico" :class="{ 'like-pop': video.hasLiked === 1 }">
                  <uni-icons :type="video.hasLiked === 1 ? 'heart-filled' : 'heart'" :color="video.hasLiked === 1 ? '#ff5e9c' : '#fff'" size="30"></uni-icons>
                </view>
                <text class="side-count">{{ formatCount(video.likeCount) }}</text>
              </a11y-button>

              <a11y-button class="side-btn btn-feedback" :label="`查看评论，共 ${video.commentCount} 条`" @tap="showComments(video)">
                <view class="side-ico">
                  <uni-icons type="chatbubble" color="#fff" size="30"></uni-icons>
                </view>
                <text class="side-count">{{ formatCount(video.commentCount) }}</text>
              </a11y-button>

              <a11y-button class="side-btn btn-feedback" :label="`分享 ${video.nickname} 的视频`" @tap="openShare(video)">
                <view class="side-ico">
                  <uni-icons type="redo" color="#fff" size="30"></uni-icons>
                </view>
              </a11y-button>
            </view>
          </view>

          <!-- 底部信息 + 滚动音乐条 -->
          <view class="video-info">
            <text class="author">@{{ video.nickname }}</text>
            <text class="desc ellipsis-2">{{ video.signature }}</text>
            <view class="music-row">
              <!-- 旋转音乐唱片（抖音位：左下角音乐条旁，替代静态音符图标） -->
              <view
                class="music-disc"
                :class="{ 'disc-paused': !isPlaying && currentId === video.id }"
                aria-hidden="true"
              >
                <image lazy-load class="disc-img" :src="video.cover" mode="aspectFill" />
              </view>
              <view class="music-track">
                <text class="music-text">{{ video.nickname }} 创作的原声 · {{ video.signature || '小视频' }}</text>
              </view>
            </view>
          </view>

          <!-- 进度条（role=progressbar：读屏可获知播放进度，而非仅视觉呈现） -->
          <view v-if="currentId === video.id" class="progress-bar" role="progressbar" aria-label="播放进度" :aria-valuenow="Math.round(progress)" aria-valuemin="0" aria-valuemax="100">
            <view class="progress-inner" :style="{ width: progress + '%' }" />
          </view>
        </view>
      </swiper-item>
    </swiper>

    <!-- 顶部/底部暗角渐变：保证白字在任意视频上都清晰（抖音式层次） -->
    <view class="top-scrim" aria-hidden="true"></view>
    <view class="bottom-scrim" aria-hidden="true"></view>

    <!-- 上滑提示（纯装饰，仅首条有效视频上短暂显示；弱化动效偏好下自动静止） -->
    <view v-if="showSwipeHint" class="swipe-hint" aria-hidden="true">
      <text class="swipe-arrow">︿</text>
      <text class="swipe-text">上滑看更多</text>
    </view>

    <!-- 骨架屏（首屏加载中）：占位元素，对读屏隐藏 -->
    <skeleton v-if="displayList.length === 0 && loading" variant="video" />

    <!-- 空状态 -->
    <empty-state
      v-if="displayList.length === 0 && !loading"
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
      @close="commentVisible = false"
      @commented="onVideoCommented"
    />

    <!-- 底部分享面板（对话框语义 + Esc 关闭 + 焦点管理） -->
    <a11y-dialog class="share-mask" :visible="showShare" title="分享给好友" @close="showShare = false">
      <view class="share-panel-inner">
        <text class="share-title">分享给好友</text>
        <view class="share-grid">
          <a11y-button class="share-item" label="分享到微信" @tap="doShare('wechat')">
            <view class="share-ico ico-wechat" aria-hidden="true">微</view>
            <text class="share-label">微信</text>
          </a11y-button>
          <a11y-button class="share-item" label="分享到朋友圈" @tap="doShare('moments')">
            <view class="share-ico ico-moments" aria-hidden="true">友</view>
            <text class="share-label">朋友圈</text>
          </a11y-button>
          <a11y-button class="share-item" label="复制链接" @tap="doShare('copy')">
            <view class="share-ico ico-copy" aria-hidden="true">链</view>
            <text class="share-label">复制链接</text>
          </a11y-button>
        </view>
        <a11y-button class="share-cancel" label="取消分享" @tap="showShare = false">取消</a11y-button>
      </view>
    </a11y-dialog>
    <!-- 匹配成功弹窗（全局，由 match store 驱动） -->
    <MatchPopup />
  </view>
</template>

<script setup lang="ts">
import { ref, computed, watch, nextTick, getCurrentInstance } from "vue";
import { onShow, onHide, onShareAppMessage } from "@dcloudio/uni-app";
import MatchPopup from "@/components/match-popup.vue";
import {
  getVideoList,
  getFollowVideoList,
  getHotVideoList,
  getFriendVideoList,
  getLocationVideoList,
  likeVideo,
  dislikeVideo,
  focusVideoUser,
  unfocusVideoUser,
  type Video,
} from "@/api/video";
import VideoCommentDrawer from "@/components/video-comment-drawer.vue";
import A11yButton from "@/components/a11y-button.vue";
import EmptyState from "@/components/empty-state.vue";
import Skeleton from "@/components/skeleton.vue";
import A11yDialog from "@/components/a11y-dialog.vue";
import { formatCount } from "@/utils/format";

/** 分页大小 */
const PAGE_SIZE = 10;

/** 5 个频道各自独立的列表与分页 */
const channels = {
  recommend: { list: ref<Video[]>([]), page: ref(1), hasMore: ref(true) },
  follow: { list: ref<Video[]>([]), page: ref(1), hasMore: ref(true) },
  hot: { list: ref<Video[]>([]), page: ref(1), hasMore: ref(true) },
  friend: { list: ref<Video[]>([]), page: ref(1), hasMore: ref(true) },
  location: { list: ref<Video[]>([]), page: ref(1), hasMore: ref(true) },
} as const;

/** 按频道 key 获取对应的 API 请求函数 */
function getChannelApi(key: ChannelKey) {
  switch (key) {
    case "recommend": return getVideoList;
    case "follow": return getFollowVideoList;
    case "hot": return getHotVideoList;
    case "friend": return getFriendVideoList;
    case "location": return getLocationVideoList;
  }
}

const swiperIndex = ref(0);
type ChannelKey = "hot" | "friend" | "follow" | "location" | "recommend";
const CHANNELS = [
  { key: "hot", label: "热点" },
  { key: "friend", label: "朋友" },
  { key: "follow", label: "关注" },
  { key: "location", label: "定位" },
  { key: "recommend", label: "推荐" },
] as const;
const activeChannel = ref<ChannelKey>("recommend");

/** 当前 Tab 展示的列表 */
const displayList = computed<Video[]>(() => channels[activeChannel.value].list.value);

/** 评论抽屉状态 */
const commentVisible = ref(false);
const activeCommentVideoId = ref("");

/** 分页加载守卫：防止快速滑动触发重复请求；到底后不再重发 */
const loadingMore = ref(false);

/** 请求代际令牌：切频道/重载后，旧请求的响应直接丢弃 */
let loadToken = 0;

/** 当前播放视频：始终由 swiperIndex 单一推导，不再平行维护 currentId */
const currentVideo = computed<Video | undefined>(() => displayList.value[swiperIndex.value]);
const currentId = computed(() => currentVideo.value?.id ?? "");

const isPlaying = ref(true);
/** 是否静音：H5 自动播放策略要求无声才能 autoplay，小程序可有声 */
const isH5 = uni.getSystemInfoSync().uniPlatform === "web";
const isMuted = ref(isH5);
const progress = ref(0);
const loading = ref(false);

/** video 的 play/pause 事件会滞后于 index 切换到达，用此标记丢弃来自旧视频的滞后事件 */
let lastPlayedIndex = -1;

/** 单击/双击判定 */
let lastTapTime = 0;
let singleTapTimer: ReturnType<typeof setTimeout> | null = null;
/** 双击冷却截止时间：冷却期内忽略浏览器/触摸产生的残留点击事件 */
let doubleTapCooldownUntil = 0;

/** uni-app 的上下文仍用于 onShow/onHide 等显式恢复场景，暂停/切换交由 autoplay + paused 托管 */
const instance = getCurrentInstance();

function getCtx(index: number) {
  const video = displayList.value[index];
  if (!video) return null;
  return uni.createVideoContext("vid-" + video.id, instance?.proxy as any);
}

/** 命令式播放：uni-h5 不支持 paused 属性绑定，必须通过 ctx 显式控制 */
function playAt(index: number) {
  const video = displayList.value[index];
  if (!video) return;
  lastPlayedIndex = index;
  getCtx(index)?.play();
}

/** 命令式暂停 */
function pauseAt(index: number) {
  getCtx(index)?.pause();
}

/** H5 首次用户手势后解除静音（浏览器自动播放策略要求首次必须 muted 起播） */
function unlockAudio() {
  if (isH5 && isMuted.value) {
    isMuted.value = false;
    // uni-h5 的 video 组件 :muted 动态绑定不生效，直接改 DOM 保证取消静音
    document.querySelectorAll("video").forEach((v) => (v.muted = false));
  }
}

// 位置变化时退出残留的点击判定与待触发的单击计时（滑切自带"播放新视频"语义）
watch(swiperIndex, () => {
  lastTapTime = 0;
  if (singleTapTimer) {
    clearTimeout(singleTapTimer);
    singleTapTimer = null;
  }
});

/** 滑动切换：video 节点常驻，无需重建，只同步播放态与预加载 */
/** 滑动切换：显式暂停旧视频、播放新视频，避免多视频同时发声或状态错乱 */
function onSwiperChange(e: any) {
  const index = e.detail.current;
  const old = swiperIndex.value;
  if (old !== index) pauseAt(old);
  swiperIndex.value = index;
  isPlaying.value = true;
  progress.value = 0;
  unlockAudio();
  playAt(index);
  if (index >= displayList.value.length - 2) {
    loadMoreVideos();
  }
}

function onVideoPlay(idx: number) {
  if (idx !== swiperIndex.value) return;
  isPlaying.value = true;
}

function onVideoPause(idx: number) {
  // 切走的旧 video 卸载/切换时会补发滞后 pause 事件，丢弃，否则会误重置新视频状态
  if (idx !== swiperIndex.value || idx !== lastPlayedIndex) return;
  isPlaying.value = false;
}

/** 播放失败的视频 id 集合（仅当前条目展示重试浮层） */
const erroredVideoIds = ref(new Set<string>());
/** 重试计数：变化即重挂载 video 节点，触发重新加载 */
const videoRetryTick = ref<Record<string, number>>({});

function onVideoError(video: Video) {
  // 记录所有失败视频（含相邻预加载条目）：滑到该条时 error 不会重发，
  // 若此处过滤会导致预加载失败的视频滑入后永久黑屏；浮层仅对当前条目展示（见模板条件）
  erroredVideoIds.value = new Set(erroredVideoIds.value).add(video.id);
}

/** 点击重试：清除错误态 → bump key 重挂载 video → 重新起播 */
function retryVideo(video: Video) {
  const next = new Set(erroredVideoIds.value);
  next.delete(video.id);
  erroredVideoIds.value = next;
  videoRetryTick.value[video.id] = (videoRetryTick.value[video.id] || 0) + 1;
  nextTick(() => playAt(swiperIndex.value));
}

/** 空状态文案随频道变化 */
const emptyStateTitle = computed(() => {
  switch (activeChannel.value) {
    case "follow": return "还没有关注的作者";
    case "friend": return "还没有好友";
    case "hot": return "暂无热门视频";
    case "location": return "暂无附近的视频";
    default: return "暂无视频";
  }
});
const emptyStateDesc = computed(() => {
  switch (activeChannel.value) {
    case "follow": return "去推荐页关注喜欢的人吧";
    case "friend": return "去推荐页关注喜欢的人吧";
    case "hot": return "精彩内容正在路上";
    case "location": return "开启定位后看看附近的人吧";
    default: return "精彩内容即将上线";
  }
});

/** 双击飞心 */
const floatHearts = ref<{ id: number; x: number; y: number }[]>([]);

/** 分享面板 */
const showShare = ref(false);
const shareVideo = ref<Video | null>(null);

/** 搜索（暂未上线） */
function onSearch(): void {
  uni.showToast({ title: "搜索功能即将上线", icon: "none" });
}

/**
 * 切换顶部 Tab，重置到首条（currentId 由 swiperIndex 推导，无需手工维护）
 */
async function switchChannel(ch: ChannelKey) {
  if (activeChannel.value === ch) return;
  activeChannel.value = ch;
  swiperIndex.value = 0;
  isPlaying.value = true;
  progress.value = 0;
  const chState = channels[ch];
  // 切频道统一清空错误态，避免命中缓存数据时残留过期错误浮层
  erroredVideoIds.value = new Set();
  if (chState.list.value.length === 0) {
    await loadVideos();
  } else {
    // 已有数据的频道：autoplay 属性变化在 H5 不会触发播放，需命令式起播首条
    playAt(0);
  }
}

/** 单击/双击判定间隔与单击延迟（对齐抖音：单击延迟执行，给双击留出判定窗口） */
const TAP_GAP = 280;

/**
 * 透明层点击：第一次点击延迟 280ms 再切换播放/暂停；
 * 280ms 内第二次点击视为双击点赞，并撤销待执行的单击
 */
function onVideoTap(video: Video, e: any) {
  // 任何点击都是有效的用户手势：立即尝试解锁声音（H5 自动播放策略允许在手势中 unmute）
  unlockAudio();
  const now = Date.now();
  // 双击冷却期内的残留事件直接忽略，避免其误触发暂停
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

/** 单击到点：命令式切换播放/暂停 */
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

/**
 * 双击点赞 + 飞心动画
 */
function doubleLike(video: Video, e: any) {
  spawnHeart(e);
  if (video.hasLiked !== 1) {
    handleLike(video);
  }
}

/**
 * 在点击位置生成飞出的爱心
 */
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
  }, 700);
}

/**
 * 进度条更新：timeupdate 触发频率远高于视觉需要（每秒可达数十次），
 * 仅在整数百分比变化时才写 ref，避免高频触发页面重渲染
 */
function onTimeUpdate(e: any) {
  const cur = e?.detail?.currentTime || 0;
  const dur = e?.detail?.duration || 0;
  const next = dur > 0 ? Math.min(100, Math.round((cur / dur) * 100)) : 0;
  if (next !== Math.round(progress.value)) {
    progress.value = next;
  }
}

/**
 * 点赞/取消点赞（乐观更新，失败回滚）
 */
async function handleLike(video: Video) {
  const wasLiked = video.hasLiked === 1;
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
    video.hasLiked = wasLiked ? 1 : 0;
    video.likeCount += wasLiked ? 1 : -1;
  }
}

/**
 * 关注/取消关注作者（乐观更新，失败回滚）
 */
async function handleFocus(video: Video) {
  const wasFocused = video.hasFocus === 1;
  try {
    if (wasFocused) {
      video.hasFocus = 0;
      await unfocusVideoUser(video.userId);
      // 关注流中取关：移除该作者的全部视频（同作者可能有多条），并修正播放位置
      if (activeChannel.value === "follow") {
        const chState = channels["follow"];
        chState.list.value = chState.list.value.filter((v) => v.userId !== video.userId);
        const safeIdx = Math.min(swiperIndex.value, Math.max(0, chState.list.value.length - 1));
        // 先归 -1 再赋值，保证即使索引数值不变 watch 也能感知"切换到了新的第 safeIdx 条"
        swiperIndex.value = -1;
        swiperIndex.value = safeIdx;
        if (chState.list.value.length > 0) playAt(safeIdx);
      }
    } else {
      video.hasFocus = 1;
      await focusVideoUser(video.userId);
    }
    // 同步所有频道中同一作者的其他视频的关注态（每条 video 是独立对象，不会自动联动）
    const focused = wasFocused ? 0 : 1;
    for (const ch of Object.values(channels)) {
      for (const v of ch.list.value) {
        if (v.userId === video.userId && v !== video) v.hasFocus = focused;
      }
    }
  } catch {
    video.hasFocus = wasFocused ? 1 : 0;
  }
}

function showComments(video: Video) {
  activeCommentVideoId.value = video.id;
  commentVisible.value = true;
}

/** 抽屉内发表评论成功：同步刷新列表中该视频的评论数 */
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
    uni.setClipboardData({
      data: shareVideo.value?.videoUrl || "",
      success: () => uni.showToast({ title: "链接已复制", icon: "none" }),
    });
  } else {
    // 未接入真实微信分享，不再谎报"已分享"
    uni.showToast({ title: "该分享方式即将上线", icon: "none" });
  }
}


/**
 * 加载视频列表首页（重载即重置滑动位置，并作废在途请求）
 */
async function loadVideos() {
  const token = ++loadToken;
  const ch = activeChannel.value;
  const chState = channels[ch];
  const apiFn = getChannelApi(ch);

  swiperIndex.value = 0;
  loading.value = true;
  erroredVideoIds.value = new Set();
  try {
    chState.page.value = 1;
    chState.hasMore.value = true;
    const res = await apiFn({ page: 1, pagesize: PAGE_SIZE });
    if (token !== loadToken || activeChannel.value !== ch) return;
    chState.list.value = res.items || [];
    if (displayList.value.length > 0) {
      isPlaying.value = true;
      playAt(0);
    }
  } catch {
    // 请求失败由 request 层统一 toast
  } finally {
    if (token === loadToken) loading.value = false;
  }
}

/** 按 id 去重后追加，防止后端翻页数据变动导致 swiper 重复 key */
function appendUnique(target: Video[], items: Video[]): Video[] {
  const seen = new Set(target.map((v) => v.id));
  return target.concat(items.filter((v) => !seen.has(v.id)));
}

/**
 * 加载更多视频（上拉预加载）
 */
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
    const res = await apiFn({ page: nextPage, pagesize: PAGE_SIZE });
    if (token !== loadToken || activeChannel.value !== channel) return;
    if (res.items && res.items.length > 0) {
      chState.list.value = appendUnique(chState.list.value, res.items);
      chState.page.value = nextPage;
    } else {
      chState.hasMore.value = false;
    }
  } catch {
    // 请求失败由 request 层统一 toast
  } finally {
    loadingMore.value = false;
  }
}

/** 上滑提示：仅首屏有效视频显示，4 秒后自动退场，避免与底部文字区长期重叠 */
const showSwipeHint = ref(false);
watch(
  () => [currentVideo.value?.id, swiperIndex.value] as const,
  ([id, idx]) => {
    if (id && idx === 0) {
      showSwipeHint.value = true;
      setTimeout(() => {
        showSwipeHint.value = false;
      }, 4000);
    } else {
      showSwipeHint.value = false;
    }
  },
);

/** 记录离开页面前的播放状态，返回时恢复 */
let wasPlayingBeforeHide = false;

onShow(() => {
  if (displayList.value.length === 0) {
    loadVideos();
  } else if (wasPlayingBeforeHide) {
    wasPlayingBeforeHide = false;
    isPlaying.value = true;
    playAt(swiperIndex.value);
  }
});

onHide(() => {
  // 离开页面（切后台/跳转其他 tab）时暂停，避免声音继续播放；
  // paused 属性托管的是"用户单击暂停"，tab 切走时 isPlaying 变 false 也会置 paused，双保险
  wasPlayingBeforeHide = isPlaying.value;
  if (isPlaying.value) {
    isPlaying.value = false;
    const video = currentVideo.value;
    if (video) {
      const ctx = uni.createVideoContext("vid-" + video.id, instance?.proxy as any);
      ctx?.pause();
    }
  }
});

/** 小程序端分享配置（H5 端静默忽略） */
onShareAppMessage(() => ({
  title: "心飞交友 · 遇见心动的人",
  path: "/pages/match/index",
}));
</script>

<style lang="scss" scoped>
.video-page {
  /* tab 页可用高度 = 视口 - 原生 tabbar；禁用 var(--window-bottom)（页面 body 上为 0px，
     会让页面铺满整屏、底部信息区被 tabbar 遮挡，见需求文档附录 B2.2） */
  height: calc(100vh - var(--tab-bar-height, 0px));
  background: #000;
  position: relative;
}

/* 顶部 Tab */
.top-tabs {
  position: absolute;
  top: calc(env(safe-area-inset-top, 0px) + 16rpx);
  left: 0;
  right: 0;
  display: flex;
  align-items: center;
  padding: 0 24rpx;
  z-index: 30;

  .tab-group {
    flex: 1;
    display: flex;
    justify-content: center;
    align-items: center;
    gap: 36rpx;
  }

  .tab {
    position: relative;
    font-size: 30rpx;
    color: rgba(255, 255, 255, 0.6);
    padding: 6rpx 6rpx;
    transition: color 0.2s ease, font-size 0.2s ease;

    &.active {
      color: #fff;
      font-weight: 700;
      font-size: 36rpx;

      &::after {
        content: "";
        position: absolute;
        left: 50%;
        bottom: -6rpx;
        transform: translateX(-50%);
        width: 44rpx;
        height: 6rpx;
        border-radius: 3rpx;
        background: var(--bg-card);
      }
    }
  }
}

.search-btn {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 56rpx;
  height: 56rpx;
  flex-shrink: 0;
}

.video-swiper {
  height: calc(100vh - var(--tab-bar-height, 0px));
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

  /* 窗口内非当前 item 的封面余像（盖住未起播的黑块，pointer-events 交给上层 tap-layer） */
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

  /* CSS 三角：border-left 实色 + 上下透明 = 向右播放键；跨端兼容（小程序不支持 clip-path） */
  .play-icon {
    width: 0;
    height: 0;
    border-left: 48rpx solid rgba(255, 255, 255, 0.85);
    border-top: 34rpx solid transparent;
    border-bottom: 34rpx solid transparent;
    /* 三角几何中心偏左，右移修正视觉居中 */
    margin-left: 14rpx;
    filter: drop-shadow(0 4rpx 16rpx rgba(0, 0, 0, 0.4));
  }
}

/* 播放失败浮层 */
.video-error {
  position: absolute;
  inset: 0;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 24rpx;
  z-index: 7;
  background: rgba(0, 0, 0, 0.6);

  .video-error-title {
    font-size: 30rpx;
    color: #fff;
    text-shadow: 0 2rpx 8rpx rgba(0, 0, 0, 0.5);
  }

  .video-error-retry {
    padding: 16rpx 48rpx;
    border: 2rpx solid rgba(255, 255, 255, 0.9);
    border-radius: 999rpx;
    font-size: 28rpx;
    color: #fff;
    background: rgba(255, 255, 255, 0.12);
  }
}

/* 双击飞心 */
.float-heart {
  position: absolute;
  font-size: 96rpx;
  color: var(--color-primary);
  z-index: 20;
  pointer-events: none;
  transform: translate(-50%, -50%);
  animation: floatUp 0.7s ease-out forwards;
}

@keyframes floatUp {
  0% {
    transform: translate(-50%, -50%) scale(0.4);
    opacity: 1;
  }
  60% {
    opacity: 1;
  }
  100% {
    transform: translate(-50%, -90%) scale(1.6);
    opacity: 0;
  }
}

.side-bar {
  position: absolute;
  right: 24rpx;
  bottom: calc(env(safe-area-inset-bottom, 0px) + 36rpx);
  z-index: 10;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 40rpx;

  .side-stack {
    display: flex;
    flex-direction: column;
    align-items: center;
    /* 探探/抖音按钮排：紧凑间距（40rpx 偏松） */
    gap: 24rpx;
  }

  .side-btn {
    position: relative;
    display: flex;
    flex-direction: column;
    align-items: center;
    gap: 8rpx;

    .side-ico {
      width: 80rpx;
      height: 80rpx;
      display: flex;
      align-items: center;
      justify-content: center;
    }

    /* 点赞弹动（抖音式）：点赞瞬间放大再回弹 */
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
      font-size: 24rpx;
      color: #fff;
      text-shadow: 0 2rpx 8rpx rgba(0, 0, 0, 0.5);
    }

    .side-avatar {
      width: 84rpx;
      height: 84rpx;
      border-radius: 50%;
      border: 4rpx solid #fff;
    }

    .follow-plus {
      position: absolute;
      bottom: -8rpx;
      left: 50%;
      transform: translateX(-50%);
      width: 32rpx;
      height: 32rpx;
      line-height: 28rpx;
      text-align: center;
      background: var(--color-primary);
      color: #fff;
      border-radius: 50%;
      font-size: 24rpx;
      box-shadow: 0 4rpx 12rpx rgba(255, 94, 156, 0.5);
    }
  }
}

@keyframes spin {
  to {
    transform: rotate(360deg);
  }
}

.video-info {
  position: absolute;
  left: 24rpx;
  right: 150rpx;
  bottom: calc(env(safe-area-inset-bottom, 0px) + 40rpx);
  z-index: 10;

  .author {
    font-size: 32rpx;
    font-weight: bold;
    color: #fff;
    display: block;
    margin-bottom: 12rpx;
    text-shadow: 0 2rpx 8rpx rgba(0,0,0,0.5);
  }

  .desc {
    font-size: 26rpx;
    color: rgba(255, 255, 255, 0.9);
    line-height: 1.5;
    display: block;
    text-shadow: 0 2rpx 8rpx rgba(0,0,0,0.5);
  }

  .music-row {
    display: flex;
    align-items: center;
    margin-top: 14rpx;

    /* 旋转音乐唱片（抖音位：左下角音乐条旁） */
    .music-disc {
      flex-shrink: 0;
      width: 56rpx;
      height: 56rpx;
      border-radius: 50%;
      background: #111;
      border: 4rpx solid rgba(255, 255, 255, 0.9);
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
      width: 360rpx;

      .music-text {
        display: inline-block;
        white-space: nowrap;
        font-size: 24rpx;
        color: rgba(255, 255, 255, 0.9);
        padding-left: 100%;
      }
    }
  }
}

/* 唱片旋转/跑马灯仅在当前播放条目运行（顶层选择器：.is-active 挂在 .video-item 上），
   非当前条目静止，避免多个无限动画并发渲染导致卡顿 */
.video-item.is-active .disc-img {
  animation: spin 5s linear infinite;
}

.video-item.is-active .music-text {
  animation: marquee 8s linear infinite;
}

@keyframes marquee {
  0% {
    transform: translateX(0);
  }
  100% {
    transform: translateX(-100%);
  }
}

/* 进度条 */
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
    height: 100%;
    background: var(--bg-card);
    transition: width 0.2s linear;
  }
}

/* 顶部/底部暗角渐变（提升白字可读性，纯视觉、不拦截点击） */
.top-scrim {
  position: absolute;
  top: 0;
  left: 0;
  right: 0;
  height: 200rpx;
  background: linear-gradient(to bottom, rgba(0, 0, 0, 0.5), rgba(0, 0, 0, 0));
  z-index: 8;
  pointer-events: none;
}

.bottom-scrim {
  position: absolute;
  left: 0;
  right: 0;
  bottom: 0;
  height: 440rpx;
  background: linear-gradient(to top, rgba(0, 0, 0, 0.62), rgba(0, 0, 0, 0));
  z-index: 8;
  pointer-events: none;
}

/* 上滑提示（抖音式微动效；bottom 抬高避开左侧文字区与右侧唱片） */
.swipe-hint {
  position: absolute;
  left: 50%;
  transform: translateX(-50%);
  bottom: calc(env(safe-area-inset-bottom, 0px) + 280rpx);
  z-index: 11;
  display: flex;
  flex-direction: column;
  align-items: center;
  color: rgba(255, 255, 255, 0.9);
  pointer-events: none;
  animation: hintBounce 1.6s ease-in-out infinite;

  .swipe-arrow {
    font-size: 30rpx;
    line-height: 1;
    text-shadow: 0 2rpx 6rpx rgba(0, 0, 0, 0.5);
  }

  .swipe-text {
    font-size: 22rpx;
    margin-top: 2rpx;
    text-shadow: 0 2rpx 6rpx rgba(0, 0, 0, 0.5);
  }
}

@keyframes hintBounce {
  0%, 100% { transform: translateX(-50%) translateY(0); }
  50% { transform: translateX(-50%) translateY(-10rpx); }
}

/* 尊重系统「减弱动效」偏好 */
@media (prefers-reduced-motion: reduce) {
  .swipe-hint { animation: none; }
  .music-text { animation: none; }
}

/* 底部分享面板 */
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
    border-radius: 32rpx 32rpx 0 0;
    padding: 32rpx 32rpx calc(32rpx + env(safe-area-inset-bottom));
    animation: slideUp 0.25s ease;

    .share-title {
      text-align: center;
      font-size: 30rpx;
      font-weight: 600;
      color: var(--text-primary);
      margin-bottom: 32rpx;
    }

    .share-grid {
      display: flex;
      justify-content: space-around;
      margin-bottom: 24rpx;

      .share-item {
        display: flex;
        flex-direction: column;
        align-items: center;
        gap: 12rpx;
        /* 触摸目标 ≥24px */
        min-height: 48rpx;

        .share-ico {
          width: 96rpx;
          height: 96rpx;
          border-radius: 50%;
          display: flex;
          align-items: center;
          justify-content: center;
          color: #fff;
          font-size: 36rpx;

          &.ico-wechat { background: #07c160; }
          &.ico-moments { background: #ff8200; }
          &.ico-copy { background: var(--color-primary); }
        }

        .share-label {
          font-size: 24rpx;
          color: var(--text-secondary);
        }
      }
    }

    /* 触摸目标加高至 88rpx（≈44px，满足 WCAG 2.5.5 目标尺寸） */
    .share-cancel {
      display: flex;
      align-items: center;
      justify-content: center;
      font-size: 30rpx;
      color: var(--text-hint);
      padding: 24rpx 0;
      min-height: 88rpx;
      border-top: 1rpx solid var(--border-faint);
      margin-top: 8rpx;
    }
  }
}

@keyframes slideUp {
  from { transform: translateY(100%); }
  to { transform: translateY(0); }
}
</style>
