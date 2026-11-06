<template>
  <view class="notifications-page">
    <!-- VIP 权益锁定状态（心动/谁喜欢我） -->
    <view
      v-if="locked"
      class="locked-state"
    >
      <view class="locked-icon">
        <uni-icons
          type="locked"
          size="28"
          color="#fff"
        />
      </view>
      <text class="locked-title">
        VIP 专享权益
      </text>
      <text class="locked-desc">
        开通会员即可查看谁喜欢你
      </text>
      <a11y-button
        class="locked-btn"
        label="开通 VIP"
        @tap="goVip"
      >
        开通 VIP
      </a11y-button>
    </view>

    <!-- 空状态 + 通知列表（非锁定态时二选一） -->
    <template v-else>
      <empty-state
        v-if="list.length === 0"
        icon="notification"
        title="暂无通知"
        desc="有人喜欢你时会在这里提醒你"
      />
      <a11y-button
        v-for="item in list"
        :key="item.id"
        class="notice-item shadow-card"
        :label="noticeLabel(item)"
        @tap="goDetail(item)"
      >
        <view class="avatar-wrap">
          <image
            class="avatar"
            :src="resolveAvatar(item.avatar)"
            mode="aspectFill"
            lazy-load
            :aria-label="`${item.nickname} 的头像`"
          />
          <!-- 类型徽章为纯图标，语义已由 desc 文本（赞了你的动态等）承载，避免重复朗读 -->
          <view
            class="type-badge"
            :style="{ background: typeMeta.color }"
            aria-hidden="true"
          >
            <uni-icons
              :type="typeMeta.uniIcon"
              size="18"
              color="#fff"
            />
          </view>
        </view>
        <view class="info">
          <text class="nickname">
            {{ item.nickname }}
          </text>
          <text class="desc">
            {{ item.desc }}
          </text>
        </view>
        <text class="time">
          {{ item.time }}
        </text>
      </a11y-button>
    </template>

    <!-- 公告详情弹窗：公告无对应用户，点击通知项弹出正文，不跳用户主页 -->
    <AnnouncementDetailDialog
      :visible="!!announcementDetail"
      :notice="announcementDialogNotice"
      @close="announcementDetail = null"
    />
  </view>
</template>

<script setup lang="ts">
import { ref, computed } from "vue";
import EmptyState from "@/components/empty-state.vue";
import { onLoad, onShow, onPullDownRefresh } from "@dcloudio/uni-app";
import A11yButton from "@/components/a11y-button.vue";
import AnnouncementDetailDialog from "@/components/announcement-detail-dialog.vue";
import { useChatStore, type NoticeType } from "@/store/chat";
import {
  getLikeMessages,
  getCommentMessages,
  getLoveMessages,
  getAnnouncementMessages,
  type MessageLike,
  type MessageAnnouncement,
} from "@/api/chat";
import { checkBenefit } from "@/api/mine";
import { resolveAvatar } from "@/utils/avatar";

/** 页面展示模型（统一映射后端两种消息结构） */
interface NoticeItem {
  id: string;
  avatar?: string;
  nickname: string;
  desc: string;
  time: string;
  userId?: number;
}

const chatStore = useChatStore();

/** 通知类型元数据（图标+颜色，与会话列表 NOTICE_META 保持一致） */
const TYPE_META: Record<string, { uniIcon: string; color: string }> = {
  like: { uniIcon: "heart-filled", color: "var(--color-primary)" },
  comment: { uniIcon: "chatbubble", color: "var(--info-blue)" },
  love: { uniIcon: "star-filled", color: "var(--color-primary)" },
  announcement: { uniIcon: "notification", color: "var(--success)" },
  visitors: { uniIcon: "eye", color: "var(--color-primary-violet)" },
  fans: { uniIcon: "person", color: "var(--color-primary-soft)" },
};

/**
 * 页面标题映射。
 *
 * 历史错位（§11.7 #41）：`like` 原写「谁喜欢我」，与同文件 `TYPE_DESC.like`
 * 「赞了你的动态」语义冲突——标题说的是「谁喜欢我」（对应 love 的语义），
 * 内容却是「赞了你的动态」。已按 TYPE_DESC 对齐为「喜欢」。
 * 「谁喜欢我」是 love 的语义，已由 PAGE_TITLES.love = "心动" 承载。
 */
const PAGE_TITLES: Record<string, string> = {
  like: "喜欢", comment: "评论", love: "心动",
  announcement: "公告", visitors: "谁看过我", fans: "粉丝",
};

/**
 * 有效（后端有接口）的通知类型白名单。
 *
 * `visitors` / `fans` 仅作未来扩展占位：不在 chatStore.NoticeType 联合类型内、
 * 后端无对应接口、chatStore 无未读位，当前不参与任何逻辑。
 * 入口只对有效类型开放，避免用户点进一个必然空白的页面。
 */
const SUPPORTED_TYPES = ["like", "comment", "love", "announcement"] as const;
type SupportedType = (typeof SUPPORTED_TYPES)[number];

/** 各类型的描述文案 */
const TYPE_DESC: Record<string, string> = {
  like: "赞了你的动态",
  comment: "评论了你",
  love: "喜欢了你",
};

/** 默认分页大小 */
const PAGE_SIZE = 20;

const type = ref("like");
const list = ref<NoticeItem[]>([]);
const loading = ref(false);
/** VIP 权益锁定状态（心动/谁喜欢我：see_who_likes 权益） */
const locked = ref(false);

/** 跳转 VIP 开通页 */
function goVip(): void {
  uni.navigateTo({ url: "/pages/mine/vip" });
}

/** 当前类型元数据（默认喜欢） */
const typeMeta = computed(() => TYPE_META[type.value] || TYPE_META.like);

/** 跳转用户详情 */
/**
 * 通知项无障碍标签：合并昵称/行为/时间，读屏一次朗读完整信息
 * @param item 通知项
 */
function noticeLabel(item: NoticeItem): string {
  return `${item.nickname}，${item.desc}，${item.time}`;
}

/** 当前查看的公告项（null 表示弹窗关闭） */
const announcementDetail = ref<NoticeItem | null>(null);

/** 公告弹窗展示模型：NoticeItem 的 nickname/desc 即公告标题/正文 */
const announcementDialogNotice = computed(() =>
  announcementDetail.value
    ? {
        title: announcementDetail.value.nickname,
        content: announcementDetail.value.desc,
        time: announcementDetail.value.time,
      }
    : null
);

function goDetail(item: NoticeItem): void {
  // 公告无对应用户，点击弹出正文详情，不跳用户主页
  if (type.value === "announcement") {
    announcementDetail.value = item;
    return;
  }
  if (!item.userId) return;
  uni.navigateTo({ url: `/pages/match/detail?id=${item.userId}` });
}

/** 将 MessageLike 映射为页面展示模型 */
function mapLikeItem(msg: MessageLike, desc: string): NoticeItem {
  return {
    id: msg.id,
    avatar: msg.avatar,
    nickname: msg.nickname || "未知用户",
    desc,
    time: msg.createDate,
    userId: msg.userId,
  };
}

/** 将 MessageAnnouncement 映射为页面展示模型 */
function mapAnnouncementItem(msg: MessageAnnouncement): NoticeItem {
  return {
    id: msg.id,
    nickname: msg.title,
    desc: msg.description,
    time: msg.createDate,
  };
}

/** 加载当前类型的通知列表
 * @returns 当前总条数（失败返回 null，不清未读，避免"没看到却清零"） */
async function loadList(): Promise<number | null> {
  if (loading.value) return null;
  loading.value = true;
  try {
    const params = { page: 1, pagesize: PAGE_SIZE };
    const currentType = type.value;

    // P2 权益拦截：心动（谁喜欢我）需 see_who_likes 权益
    if (currentType === "love") {
      try {
        const benefit = await checkBenefit("see_who_likes");
        if (!benefit?.hasBenefit) {
          locked.value = true;
          list.value = [];
          return null;
        }
      } catch {
        // 权益校验失败不阻断（降级放行）
      }
    }
    locked.value = false;

    if (currentType === "like") {
      const res = await getLikeMessages(params);
      list.value = (res.items || []).map((m) => mapLikeItem(m, TYPE_DESC.like));
      return res.counts || 0;
    } else if (currentType === "comment") {
      const res = await getCommentMessages(params);
      list.value = (res.items || []).map((m) => mapLikeItem(m, TYPE_DESC.comment));
      return res.counts || 0;
    } else if (currentType === "love") {
      const res = await getLoveMessages(params);
      list.value = (res.items || []).map((m) => mapLikeItem(m, TYPE_DESC.love));
      return res.counts || 0;
    } else if (currentType === "announcement") {
      const res = await getAnnouncementMessages(params);
      list.value = (res.items || []).map(mapAnnouncementItem);
      return res.counts || 0;
    } else {
      // visitors / fans 后端暂无接口，显示空状态
      list.value = [];
      return null;
    }
  } catch {
    // 请求失败由 request 层统一 toast
    return null;
  } finally {
    loading.value = false;
  }
}

onLoad((options: any) => {
  const requested = options?.type || "like";
  // 兜底：visitors/fans 后端无接口，手工拼 URL 进入只会看到空白页；回落到 like
  type.value = (SUPPORTED_TYPES as readonly string[]).includes(requested)
    ? requested
    : "like";
  uni.setNavigationBarTitle({ title: PAGE_TITLES[type.value] || "通知" });
});

onShow(async () => {
  const total = await loadList();
  // 真正进入通知页并完成加载后才清零未读，避免入口误触或加载失败即丢计数
  if (total !== null) chatStore.markNoticeRead(type.value as NoticeType, total);
});

/** 下拉刷新：重新拉取当前类型通知列表 */
onPullDownRefresh(async () => {
  try {
    await loadList();
  } finally {
    uni.stopPullDownRefresh();
  }
});
</script>

<style lang="scss" scoped>
.notifications-page { min-height: 100vh; background: var(--bg-sub); padding: 20rpx 24rpx; }

.notice-item { display: flex; align-items: center; padding: 24rpx; min-height: var(--row-h-touch-md); box-sizing: border-box; background: var(--bg-card); border-radius: 24rpx; margin-bottom: 16rpx;
  .avatar-wrap { position: relative; flex-shrink: 0;
    .avatar { width: 88rpx; height: 88rpx; border-radius: 50%; background: var(--bg-sub); border: 3rpx solid var(--bg-icon-soft); }
    .type-badge { position: absolute; bottom: -4rpx; right: -4rpx; width: 36rpx; height: 36rpx; border-radius: 50%; border: 3rpx solid var(--text-inverse); display: flex; align-items: center; justify-content: center; font-size: 18rpx; color: var(--text-inverse); }
  }
  .info { flex: 1; margin-left: 20rpx;
    .nickname { font-size: 28rpx; font-weight: bold; color: var(--text-primary); display: block; }
    /* #999 → #767676 */
    .desc { font-size: 24rpx; color: var(--text-hint); margin-top: 6rpx; display: block; }
  }
  /* #ccc → #767676 */
  .time { font-size: 22rpx; color: var(--text-hint); flex-shrink: 0; margin-left: 16rpx; }
}

/* VIP 权益锁定状态 */
.locked-state { display: flex; flex-direction: column; align-items: center; padding: 160rpx 48rpx 80rpx;
  .locked-icon { width: 120rpx; height: 120rpx; border-radius: 50%; background: linear-gradient(135deg, var(--gold), var(--gold-soft)); display: flex; align-items: center; justify-content: center; font-size: 56rpx; margin-bottom: 24rpx; box-shadow: 0 8rpx 24rpx rgba(255, 179, 71, 0.35); }
  .locked-title { font-size: 32rpx; color: var(--text-primary); font-weight: bold; }
  .locked-desc { font-size: 24rpx; color: var(--text-hint); margin-top: 12rpx; text-align: center; }
  .locked-btn { margin-top: 40rpx; width: 320rpx; min-height: 88rpx; padding: 0 48rpx; display: flex; align-items: center; justify-content: center; background: linear-gradient(90deg, var(--gold), var(--gold-soft)); color: var(--bg-deep); font-size: 28rpx; font-weight: bold; border-radius: 44rpx; box-sizing: border-box; }
}
</style>
