<template>
  <view class="detail-page">
    <!-- 返回按钮：放在三态分支之外始终渲染，加载失败也能返回。纯符号必须提供 aria-label，否则读屏只播符号名。
         无深色照片 hero 时（骨架/错误态/无照片）毛玻璃白按钮在浅底上对比度不足，切换为卡片表面样式（WCAG 1.4.3） -->
    <a11y-button
      class="back-btn"
      :class="photos.length > 0 ? 'glass' : 'surface-btn shadow-card'"
      :style="{ top: statusBarHeight + 8 + 'px' }"
      label="返回上一页"
      @tap="goBack"
    >
      <view
        class="i-ph-arrow-left"
        aria-hidden="true"
      />
    </a11y-button>

    <!-- 加载骨架：占位块只挂全局 .skeleton（App.vue 统一 shimmer 灰底，自动适配暗色），本页 class 只管几何布局；
         aria-hidden 避免读屏朗读无意义占位 -->
    <view
      v-if="loading"
      class="detail-skeleton"
      aria-hidden="true"
    >
      <view class="skeleton skeleton-hero" />
      <view class="info-card">
        <view class="skeleton-name-row">
          <view class="skeleton skeleton-name" />
          <view class="skeleton skeleton-age" />
        </view>
        <view class="skeleton skeleton-fate" />
        <view class="skeleton-meta">
          <view
            v-for="n in 3"
            :key="n"
            class="skeleton skeleton-meta-item"
          />
        </view>
      </view>
    </view>

    <!-- 加载失败：role="alert" 让读屏即时播报；与「资料确实为空」严格区分，slot 内放重试入口 -->
    <empty-state
      v-else-if="loadFailed"
      role="alert"
      icon="info"
      title="暂时没能获取 TA 的资料"
      desc="服务可能开小差了，检查网络后重试"
    >
      <a11y-button
        class="retry-btn"
        label="重新加载资料"
        @tap="retryLoad"
      >
        重新加载
      </a11y-button>
    </empty-state>

    <!-- 正常态 -->
    <template v-else>
      <!-- 照片轮播：photos 为空时不渲染，避免 750rpx 高空白块 -->
      <swiper
        v-if="photos.length > 0"
        class="photo-swiper"
        circular
        indicator-dots
        indicator-color="rgba(255,255,255,0.4)"
        indicator-active-color="#fff"
        @change="onSwiperChange"
      >
        <swiper-item
          v-for="(photo, i) in photos"
          :key="i"
        >
          <smart-image
            lazy-load
            class="photo"
            :src="photo"
            mode="aspectFill"
            :aria-label="`用户照片 ${i + 1}，共 ${photos.length} 张`"
            @click="previewPhoto(i)"
          />
        </swiper-item>
      </swiper>

      <!-- 照片计数 -->
      <view
        v-if="photos.length > 1"
        class="photo-count glass"
      >
        <text>{{ currentPhotoIndex + 1 }} / {{ photos.length }}</text>
      </view>

      <!-- 更多按钮：举报入口，纯符号需 aria-label；无照片时同样切换表面样式保证对比度 -->
      <a11y-button
        class="more-btn"
        :class="photos.length > 0 ? 'glass' : 'surface-btn shadow-card'"
        :style="{ top: statusBarHeight + 8 + 'px' }"
        label="更多操作，举报该用户"
        @tap="handleMore"
      >
        <text>⋯</text>
      </a11y-button>

      <!-- 资料卡：无照片时取消负边距上叠，改为正常流间距（swiper 已不渲染） -->
      <view
        class="info-card shadow-card"
        :class="{ 'info-card--no-photo': photos.length === 0 }"
      >
        <view class="name-row">
          <text class="nickname">
            {{ user?.nickname || '—' }}
          </text>
          <view class="age-badge">
            <text>{{ user?.age || '—' }}</text>
          </view>
        </view>

        <!-- 缘分值：进度条需暴露数值语义，否则读屏只见一个空条 -->
        <view class="fate-row">
          <text class="fate-label">
            缘分值
          </text>
          <view
            class="fate-bar"
            role="progressbar"
            aria-label="缘分值"
            :aria-valuenow="fateScore"
            :aria-valuemin="0"
            :aria-valuemax="FATE_SCORE_MAX"
            :aria-valuetext="`${fateScore} 分，满分 ${FATE_SCORE_MAX} 分`"
          >
            <view
              class="fate-fill"
              :style="{ width: fatePercent }"
            />
          </view>
          <text class="fate-score">
            {{ fateScore }}
          </text>
        </view>

        <view class="meta-grid">
          <view class="meta-item">
            <text class="label">
              城市
            </text><text class="value">
              {{ user?.city || '—' }}
            </text>
          </view>
          <view class="meta-item">
            <text class="label">
              学历
            </text><text class="value">
              {{ user?.edu || '—' }}
            </text>
          </view>
          <view class="meta-item">
            <text class="label">
              行业
            </text><text class="value">
              {{ user?.industry || '—' }}
            </text>
          </view>
        </view>

        <!-- 个人标签 -->
        <view
          v-if="user?.tags && user.tags.length > 0"
          class="tags-section"
        >
          <text class="section-title">
            个人标签
          </text>
          <view class="tags-wrap">
            <view
              v-for="(tag, i) in user.tags"
              :key="i"
              class="tag"
            >
              <text>{{ tag }}</text>
            </view>
          </view>
        </view>

        <!-- 陌生人问题 -->
        <view
          v-if="questions.length > 0"
          class="question-section"
        >
          <text class="section-title">
            破冰问题
          </text>
          <view
            v-for="(q, i) in questions"
            :key="i"
            class="question-item"
          >
            <view class="q-index">
              <text>{{ i + 1 }}</text>
            </view>
            <text class="question">
              {{ q }}
            </text>
          </view>
        </view>
      </view>

      <!-- TA的动态 -->
      <view
        v-if="movements.length > 0"
        class="movements-card shadow-card"
      >
        <text class="section-title">
          TA的动态
        </text>
        <!-- 动态项：改为可聚焦按钮；不设 aria-label 以保留内部正文朗读 -->
        <a11y-button
          v-for="m in movements"
          :key="m.id"
          class="movement-item"
          @tap="goMovementDetail(m.id)"
        >
          <smart-image
            v-if="m.imageContent && m.imageContent.length > 0"
            lazy-load
            class="m-cover"
            :src="m.imageContent[0]"
            mode="aspectFill"
            aria-label="动态封面图片"
          />
          <view class="m-body">
            <text class="m-text ellipsis-2">
              {{ m.textContent }}
            </text>
            <view class="m-stats">
              <text>
                <uni-icons
                  type="heart-filled"
                  size="14"
                  color="var(--color-primary-text)"
                /> {{ m.likeCount }}
              </text>
              <text>
                <uni-icons
                  type="chatbubble"
                  size="14"
                  color="var(--text-hint)"
                /> {{ m.commentCount }}
              </text>
            </view>
          </view>
        </a11y-button>
      </view>

      <!-- 底部操作栏：4 键统一「白底彩环」圆形按钮（与匹配页 action-btn 同一视觉语言） -->
      <view class="action-bar safe-area-bottom">
        <a11y-button
          class="action dislike"
          label="不喜欢"
          @tap="handleDislike"
        >
          <uni-icons
            type="close"
            size="22"
            color="var(--color-danger)"
          />
        </a11y-button>
        <a11y-button
          class="action chat"
          label="打招呼"
          @tap="handleChat"
        >
          <uni-icons
            type="chatbubble"
            size="22"
            color="var(--info-blue)"
          />
        </a11y-button>
        <a11y-button
          class="action super-like"
          label="超级喜欢"
          @tap="handleSuperLike"
        >
          <uni-icons
            type="star-filled"
            size="22"
            color="var(--gold)"
          />
        </a11y-button>
        <a11y-button
          class="action like"
          label="喜欢"
          @tap="handleLike"
        >
          <uni-icons
            type="heart-filled"
            size="22"
            color="var(--success)"
          />
        </a11y-button>
      </view>

      <!-- 匹配成功弹窗（与匹配页行为一致）：仅正常态挂载，无用户数据时弹窗无意义 -->
      <MatchPopup />
    </template>
  </view>
</template>

<script setup lang="ts">
import { ref, computed } from "vue";
import { onLoad, onUnload, onHide } from "@dcloudio/uni-app";
import A11yButton from "@/components/a11y-button.vue";
import SmartImage from "@/components/smart-image.vue";
import EmptyState from "@/components/empty-state.vue";
import MatchPopup from "@/components/match-popup.vue";
import { useMatchStore } from "@/store/match";
import { submitReport, REPORT_TARGET_TYPE, checkBenefit, recordBenefitUsage } from "@/api/mine";
import { getPersonalInfo, getStrangerQuestion, type TodayBest } from "@/api/match";
import { getUserMovements, type Movement } from "@/api/circle";
import { safeGoBack } from "@/utils/nav";
// ux 的 loading 遮罩函数与本页三态 loading ref 同名冲突（TS2440），import 别名消歧；
// 仅本地绑定名变化，调用的仍是同一函数，举报提交的遮罩行为零改动；hideLoading 无同名冲突保留原名
import { success, info, fail, loading as showLoading, hideLoading } from "@/utils/ux";
import { warn } from "@/utils/logger";

/** 缘分值满分 */
const FATE_SCORE_MAX = 100;
/** 默认状态栏高度（px） */
const DEFAULT_STATUS_BAR_HEIGHT = 20;
/** 举报原因选项 */
const REPORT_REASONS = ["骚扰", "欺诈", "色情信息", "冒充他人"];
/** 动态加载页大小 */
const MOVEMENT_PAGE_SIZE = 10;

const matchStore = useMatchStore();

const statusBarHeight = ref(DEFAULT_STATUS_BAR_HEIGHT);
const userId = ref("");
const user = ref<TodayBest | null>(null);
const photos = ref<string[]>([]);
const questions = ref<string[]>([]);
const movements = ref<Movement[]>([]);
const currentPhotoIndex = ref(0);
const fateScore = ref(0);
/** 整页加载中（骨架态），onLoad 即发起首屏请求，初值 true */
const loading = ref(true);
/** 核心资料加载失败标记：与「资料字段为空」严格区分，失败展示 role=alert 错误态 + 重试 */
const loadFailed = ref(false);

/** 缘分值百分比（用于进度条宽度） */
const fatePercent = computed(() => `${Math.min(fateScore.value / FATE_SCORE_MAX, 1) * 100}%`);

/** 返回上一页（无历史栈时兜底回匹配 tab） */
function goBack(): void {
  safeGoBack("/pages/match/index");
}

/**
 * 待执行的延时导航句柄表。
 * 裸 setTimeout 的问题：用户在延时内手动返回/跳走后，回调仍会触发——
 * 典型如无权益时 600ms 后强跳 VIP 页，用户已返回却被重新压栈劫持。
 * onUnload（页面销毁）/ onHide（navigateTo 压新页、tab 切走）时统一清理，
 * 用户主动导航优先于自动导航。
 */
const pendingNavTimers: ReturnType<typeof setTimeout>[] = [];

/** 注册延时导航，回调触发后自动从句柄表移除 */
function scheduleNav(delay: number, nav: () => void): void {
  const timer = setTimeout(() => {
    const idx = pendingNavTimers.indexOf(timer);
    if (idx >= 0) pendingNavTimers.splice(idx, 1);
    nav();
  }, delay);
  pendingNavTimers.push(timer);
}

/** 清空全部尚未触发的延时导航 */
function clearPendingNav(): void {
  pendingNavTimers.forEach((t) => clearTimeout(t));
  pendingNavTimers.length = 0;
}

onUnload(clearPendingNav);
onHide(clearPendingNav);

/** 轮播切换回调，更新照片计数 */
function onSwiperChange(e: any): void {
  currentPhotoIndex.value = e.detail.current;
}

/** 预览照片 */
function previewPhoto(index: number): void {
  uni.previewImage({ urls: photos.value, current: photos.value[index] });
}

/** photoAlbum 兼容 string(JSON)/string[]/undefined，统一归一为 string[] */
function normalizeAlbum(raw: string | string[] | undefined): string[] {
  if (!raw) return [];
  if (Array.isArray(raw)) return raw.filter(Boolean);
  try {
    const arr = JSON.parse(raw);
    return Array.isArray(arr) ? arr.filter(Boolean) : [];
  } catch {
    return [];
  }
}

/** 加载用户资料 */
async function loadUserInfo(): Promise<void> {
  if (!userId.value) return;
  try {
    const info = await getPersonalInfo(userId.value);
    user.value = info;
    fateScore.value = info?.fateValue || 0;
    // 照片轮播：头像 + 照片墙（photo_album）+ 动态图片，去重
    const avatar = info?.avatar || "";
    const albumPhotos = normalizeAlbum(info?.photoAlbum);
    const mediaPhotos = movements.value
      .flatMap((m) => m.imageContent || [])
      .filter((url) => /\.(jpe?g|png|webp|gif)$/i.test(url));
    const all = [...(avatar ? [avatar] : []), ...albumPhotos, ...mediaPhotos];
    photos.value = [...new Set(all)];
  } catch (e) {
    // 核心资料失败 → 整页错误态；request 层已统一 toast，这里不重复弹。
    // 不 rethrow：避免中断同批并发的 loadQuestion；日志带 userId 与原始错误，禁止空吞。
    loadFailed.value = true;
    warn("[match-detail] getPersonalInfo 失败，userId=%s", userId.value, e);
  }
}

/** 加载破冰问题 */
async function loadQuestion(): Promise<void> {
  if (!userId.value) return;
  try {
    const q = await getStrangerQuestion(userId.value);
    questions.value = q ? [q] : [];
  } catch {
    questions.value = [];
  }
}

/** 加载TA的动态 */
async function loadMovements(): Promise<void> {
  if (!userId.value) return;
  try {
    const res = await getUserMovements(userId.value, { page: 1, pagesize: MOVEMENT_PAGE_SIZE });
    movements.value = res.items || [];
  } catch {
    movements.value = [];
  }
}

/** 操作进行中标记（防连点：喜欢/超级喜欢/不喜欢在途时忽略后续点击） */
const acting = ref(false);

/**
 * 喜欢：统一走 matchStore.doSwipe，计入每日滑动额度（与匹配页口径一致）。
 * doSwipe 内部先上报、成功后才计数，失败重试不白扣额度。
 */
async function handleLike(): Promise<void> {
  if (!userId.value || acting.value) return;
  acting.value = true;
  try {
    const matched = await matchStore.doSwipe(userId.value, "like");
    if (matched) {
      matchStore.showMatchPopup({
        userId: userId.value,
        nickname: user.value?.nickname || "",
        avatar: user.value?.avatar || "",
      });
    }
    success(matched ? "互相喜欢，已匹配！" : "已喜欢");
    // 匹配成功时留在本页展示匹配弹窗，由弹窗承接后续；未匹配才自动返回
    if (!matched) scheduleNav(800, goBack);
  } catch {
    // 请求失败由 request 层统一 toast
  } finally {
    acting.value = false;
  }
}

/** 超级喜欢（VIP 权益：super_like，VIP1 每日 5 次） */
async function handleSuperLike(): Promise<void> {
  if (!userId.value || acting.value) return;
  acting.value = true;
  try {
    const benefit = await checkBenefit("super_like");
    if (!benefit.hasBenefit) {
      info("超级喜欢需开通VIP");
      scheduleNav(600, () => uni.navigateTo({ url: "/pages/mine/vip" }));
      return;
    }
    if (benefit.remainingToday === 0) {
      info("今日超级喜欢次数已用完");
      return;
    }
    // 先上报喜欢（走 store.doSwipe，计入额度），成功后再扣权益：
    // 后端无权益回滚接口，先扣后爱会导致请求失败白扣一次
    const matched = await matchStore.doSwipe(userId.value, "superlike");
    const ok = await recordBenefitUsage("super_like");
    if (!ok) {
      // 并发下名额可能已被用尽：喜欢已生效，仅提示
      info("今日超级喜欢次数已用完");
    }
    if (matched) {
      matchStore.showMatchPopup({
        userId: userId.value,
        nickname: user.value?.nickname || "",
        avatar: user.value?.avatar || "",
      });
    }
    success(matched ? "超级喜欢，已匹配！" : "已超级喜欢");
    if (!matched) scheduleNav(800, goBack);
  } catch {
    // 请求失败由 request 层统一 toast
  } finally {
    acting.value = false;
  }
}

/** 打招呼，跳转聊天页 */
function handleChat(): void {
  const name = user.value?.nickname || "";
  uni.navigateTo({ url: `/pages/chat/chat?userId=${userId.value}&nickname=${encodeURIComponent(name)}` });
}

/**
 * 不喜欢：统一走 matchStore.doSwipe（dislike 不计次、无后端状态变更，store 内部直接返回）。
 * 保持与匹配页同一入口，口径一致。
 */
async function handleDislike(): Promise<void> {
  if (!userId.value) {
    scheduleNav(300, goBack);
    return;
  }
  if (acting.value) return;
  acting.value = true;
  try {
    await matchStore.doSwipe(userId.value, "dislike");
  } finally {
    acting.value = false;
    scheduleNav(300, goBack);
  }
}

/** 跳转动态详情 */
function goMovementDetail(id: string): void {
  uni.navigateTo({ url: `/pages/circle/detail?id=${id}` });
}

/** 更多操作：举报入口 */
function handleMore(): void {
  uni.showActionSheet({
    itemList: ["举报该用户"],
    success: (res) => {
      if (res.tapIndex === 0) {
        chooseReportReason();
      }
    },
  });
}

/** 选择举报原因并提交 */
function chooseReportReason(): void {
  uni.showActionSheet({
    itemList: REPORT_REASONS,
    success: async (res) => {
      const reason = REPORT_REASONS[res.tapIndex];
      if (!reason || !userId.value) return;
      showLoading("提交中…");
      try {
        await submitReport({
          targetUserId: userId.value,
          targetType: REPORT_TARGET_TYPE.USER,
          reason,
        });
        hideLoading();
        success("举报已提交");
      } catch (e) {
        hideLoading();
        fail("提交失败，请重试");
      }
    },
  });
}

/** 重试前复位全部数据 refs，避免上一轮残留与新骨架/错误态混显 */
function resetPageState(): void {
  user.value = null;
  photos.value = [];
  questions.value = [];
  movements.value = [];
  currentPhotoIndex.value = 0;
  fateScore.value = 0;
}

/**
 * 首屏加载序列（onLoad 与错误态重试共用）：
 * 动态先行（资料照片轮播依赖动态图片），资料与破冰问题并发。
 * 动态是次要区块，loadMovements 内部 catch 已兜底不 rethrow，失败不拖垮整页；
 * loadUserInfo 失败只置 loadFailed 不抛出，故 finally 一定能关闭骨架。
 */
async function loadAll(): Promise<void> {
  loading.value = true;
  loadFailed.value = false;
  try {
    await loadMovements();
    await Promise.all([loadUserInfo(), loadQuestion()]);
  } finally {
    loading.value = false;
  }
}

/** 错误态重试：复位后重发完整加载序列（动态/资料/问题三个请求） */
async function retryLoad(): Promise<void> {
  resetPageState();
  await loadAll();
}

onLoad(async (options: any) => {
  userId.value = options?.id || "";
  // getSystemInfoSync 为废弃 API，改用 getWindowInfo（旧运行时无此 API 时取兜底常量）
  const win = uni.getWindowInfo?.();
  statusBarHeight.value = win?.statusBarHeight || DEFAULT_STATUS_BAR_HEIGHT;
  await loadAll();
});
</script>

<style lang="scss" scoped>
.detail-page { min-height: 100vh; background: var(--bg-sub); padding-bottom: 160rpx; }

/* ===== 照片轮播 ===== */
.photo-swiper { height: 750rpx;
  .photo { width: 100%; height: 100%; }
}

/* 悬浮在照片上的圆形按钮：88rpx 是相对单位，窄屏（320px）会等比缩到 37.5px，
   低于 WCAG 2.5.5 的 44px 物理下限；用 max() 保底，保证任何视口都不缩。
   a11y-button 默认 variant=default（无样式），尺寸在此自理。 */
.back-btn { position: absolute; left: 24rpx; width: max(88rpx, 44px); height: max(88rpx, 44px); border-radius: 50%; display: flex; align-items: center; justify-content: center; color: var(--text-inverse); font-size: 44rpx; z-index: 10; box-sizing: border-box; }

.more-btn { position: absolute; right: 24rpx; width: max(88rpx, 44px); height: max(88rpx, 44px); border-radius: 50%; display: flex; align-items: center; justify-content: center; color: var(--text-inverse); font-size: 40rpx; z-index: 10; box-sizing: border-box; }

.photo-count { position: absolute; right: 24rpx; top: 780rpx; padding: 6rpx 20rpx; border-radius: 20rpx; font-size: 22rpx; color: var(--text-inverse); z-index: 10; }

/* ===== 资料卡 ===== */
.info-card { margin: -40rpx 24rpx 0; background: var(--bg-card); border-radius: 32rpx; padding: 36rpx 32rpx; position: relative; }

.name-row { display: flex; align-items: center; gap: 16rpx;
  .nickname { font-size: 42rpx; font-weight: bold; color: var(--text-primary); }
  .age-badge { background: var(--gradient-brand); color: var(--bg-card); font-size: 24rpx; padding: 4rpx 16rpx; border-radius: 16rpx; }
}

/* 缘分值进度条 */
.fate-row { display: flex; align-items: center; gap: 16rpx; margin-top: 24rpx;
  .fate-label { font-size: 24rpx; color: var(--text-hint); flex-shrink: 0; }
  .fate-bar { flex: 1; height: 16rpx; background: var(--bg-sub); border-radius: 8rpx; overflow: hidden; }
  .fate-fill { height: 100%; background: var(--gradient-brand-h); border-radius: 8rpx; transition: width 0.6s ease-out; }
  .fate-score { font-size: 30rpx; font-weight: bold; color: var(--color-primary); flex-shrink: 0; min-width: 48rpx; text-align: right; }
}

.meta-grid { display: flex; flex-wrap: wrap; gap: 20rpx; margin-top: 28rpx;
  .meta-item { flex: 1; min-width: calc(33.33% - 14rpx); background: var(--bg-sub); border-radius: 16rpx; padding: 16rpx 20rpx;
    .label { font-size: 22rpx; color: var(--text-hint); display: block; }
    .value { font-size: 28rpx; color: var(--text-primary); margin-top: 6rpx; display: block; font-weight: 500; }
  }
}

/* 个人标签 */
.tags-section { margin-top: 28rpx;
  .tags-wrap { display: flex; flex-wrap: wrap; gap: 12rpx; margin-top: 16rpx; }
  .tag { background: var(--bg-icon-soft); color: var(--color-primary); font-size: 24rpx; padding: 8rpx 20rpx; border-radius: 20rpx; border: 1rpx solid var(--color-primary-mist); }
}

.question-section { margin-top: 32rpx;
  .question-item { display: flex; align-items: flex-start; gap: 16rpx; padding: 18rpx 0; border-bottom: 1rpx solid var(--border-faint);
    &:last-child { border-bottom: none; }
    .q-index { width: 36rpx; height: 36rpx; border-radius: 50%; background: var(--gradient-brand); color: var(--bg-card); font-size: 22rpx; display: flex; align-items: center; justify-content: center; flex-shrink: 0; margin-top: 2rpx; }
    .question { font-size: 26rpx; color: var(--text-hint-strong); line-height: 1.5; }
  }
}

.section-title { font-size: 30rpx; font-weight: bold; display: block; margin-bottom: 12rpx; color: var(--text-primary); }

/* ===== TA的动态 ===== */
.movements-card { margin: 24rpx 24rpx 0; background: var(--bg-card); border-radius: 32rpx; padding: 32rpx; }

.movement-item { display: flex; gap: 20rpx; padding: 20rpx 0; border-bottom: 1rpx solid var(--border-faint);
  &:last-child { border-bottom: none; }
  .m-cover { width: 160rpx; height: 160rpx; border-radius: 16rpx; background: var(--bg-sub); flex-shrink: 0; }
  .m-body { flex: 1; display: flex; flex-direction: column; justify-content: space-between; min-width: 0; }
  .m-text { font-size: 26rpx; color: var(--text-primary); line-height: 1.5; }
  .m-stats { display: flex; gap: 24rpx;
    text { font-size: 22rpx; color: var(--text-hint); }
  }
}

/* ===== 底部操作栏 ===== */
/* background 走 --bg-card：写死 rgba(255,255,255,.92) 在暗色模式下会变成刺眼白条 */
.action-bar { position: fixed; left: 0; right: 0; bottom: var(--tab-bar-height, 0px); display: flex; justify-content: center; align-items: center; gap: 32rpx; padding: 24rpx 0; background: var(--bg-card); backdrop-filter: blur(20px); box-shadow: 0 -8rpx 32rpx rgba(0, 0, 0, 0.08); }

/* 与匹配页 action-btn 统一的「白底彩环」视觉语言：
   四键同尺寸 96rpx，4rpx 色环 + 同色系柔光阴影，功能区分交给色环 */
.action { width: 96rpx; height: 96rpx; border-radius: 50%; display: flex; align-items: center; justify-content: center; box-sizing: border-box; background: var(--bg-card); border: 4rpx solid transparent; transition: transform 0.2s cubic-bezier(0.4, 0, 0.2, 1), box-shadow 0.2s ease;
  &:active { transform: scale(0.92); }

  &.dislike { border-color: var(--color-danger); box-shadow: 0 6rpx 20rpx rgba(255, 59, 48, 0.25); }
  &.chat { border-color: var(--info-blue); box-shadow: 0 6rpx 20rpx rgba(74, 144, 217, 0.25); }
  &.super-like { border-color: var(--gold); box-shadow: 0 6rpx 20rpx rgba(255, 179, 71, 0.3); }
  &.like { border-color: var(--success); box-shadow: 0 6rpx 20rpx rgba(76, 217, 100, 0.3); }
}

/* ===== 加载骨架（三态之一）=====
   占位块颜色/闪光全部继承全局 .skeleton（App.vue），此处只编排几何布局，不写任何颜色字面量 */
.detail-skeleton { padding-bottom: 40rpx; }

/* 轮播位骨架：与 .photo-swiper 同高；真实照片是顶边方图，覆盖全局骨架的默认圆角 */
.skeleton-hero { height: 750rpx; width: 100%; border-radius: 0; }

.skeleton-name-row { display: flex; align-items: center; gap: 16rpx; }
.skeleton-name { height: 44rpx; width: 240rpx; }
.skeleton-age { height: 36rpx; width: 72rpx; }
/* 与 .fate-row 同节奏：上间距 24rpx、条高 16rpx */
.skeleton-fate { height: 16rpx; width: 100%; margin-top: 24rpx; }
/* 与 .meta-grid 同布局：gap 20rpx、上间距 28rpx；min-width:0 防 flex 子项被撑破（AGENTS §7.1） */
.skeleton-meta { display: flex; gap: 20rpx; margin-top: 28rpx; }
.skeleton-meta-item { flex: 1; min-width: 0; height: 96rpx; }

/* ===== 无照片正常态 =====
   swiper 不渲染后取消 info-card 的负边距上叠，否则卡片会顶出视口 40rpx */
.info-card--no-photo { margin-top: 24rpx; }

/* ===== 无深色 hero 时悬浮按钮的表面态（骨架/错误/无照片三场景共用）=====
   毛玻璃 .glass 是为深色照片设计的半透明白底，浅底上白色符号对比度不足（WCAG 1.4.3）；
   切换为卡片底色 + 主文字色 + 全局卡片阴影，颜色全走 token，暗色模式自动适配 */
.surface-btn { background: var(--bg-card); color: var(--text-primary); }

/* ===== 错误态重试按钮（置于 empty-state 默认 slot）=====
   几何对齐 nearby 页重试按钮，渐变/阴影只用品牌 token，禁止 hex 字面量 */
.retry-btn {
  margin-top: 32rpx;
  padding: 16rpx 56rpx;
  border-radius: var(--radius-pill);
  font-size: 28rpx;
  font-weight: 600;
  color: var(--text-inverse);
  background: var(--gradient-brand);
  box-shadow: var(--shadow-primary);
}
</style>
