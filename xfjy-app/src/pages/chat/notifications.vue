<template>
  <view class="notifications-page">
    <!-- VIP 权益锁定状态（心动/谁喜欢我） -->
    <view v-if="locked" class="locked-state">
      <view class="locked-icon"><uni-icons type="locked" size="28" color="#fff"></uni-icons></view>
      <text class="locked-title">VIP 专享权益</text>
      <text class="locked-desc">开通会员即可查看谁喜欢你</text>
      <button class="locked-btn btn-feedback" @click="goVip">开通 VIP</button>
    </view>

    <!-- 空状态 + 通知列表（非锁定态时二选一） -->
    <template v-else>
      <empty-state v-if="list.length === 0" icon="notification" title="暂无通知" desc="有人喜欢你时会在这里提醒你" />
      <a11y-button v-for="item in list" :key="item.id" class="notice-item shadow-card btn-feedback" :label="noticeLabel(item)" @tap="goDetail(item)">
        <view class="avatar-wrap">
          <image class="avatar" :src="resolveAvatar(item.avatar)" mode="aspectFill" lazy-load :aria-label="`${item.nickname} 的头像`" />
          <!-- 类型徽章为纯图标，语义已由 desc 文本（赞了你的动态等）承载，避免重复朗读 -->
          <view class="type-badge" :style="{ background: typeMeta.color }" aria-hidden="true"><uni-icons :type="typeMeta.uniIcon" size="18" color="#fff"></uni-icons></view>
        </view>
        <view class="info">
          <text class="nickname">{{ item.nickname }}</text>
          <text class="desc">{{ item.desc }}</text>
        </view>
        <text class="time">{{ item.time }}</text>
      </a11y-button>
    </template>
  </view>
</template>

<script setup lang="ts">
import { ref, computed } from "vue";
import EmptyState from "@/components/empty-state.vue";
import { onLoad, onShow, onPullDownRefresh } from "@dcloudio/uni-app";
import A11yButton from "@/components/a11y-button.vue";
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

/** 页面标题映射 */
const PAGE_TITLES: Record<string, string> = {
  like: "谁喜欢我", comment: "评论", love: "心动",
  announcement: "公告", visitors: "谁看过我", fans: "粉丝",
};

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

function goDetail(item: NoticeItem): void {
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
 * @returns 是否加载成功（失败时不清未读，避免"没看到却清零"） */
async function loadList(): Promise<boolean> {
  if (loading.value) return false;
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
          return;
        }
      } catch {
        // 权益校验失败不阻断（降级放行）
      }
    }
    locked.value = false;

    if (currentType === "like") {
      const res = await getLikeMessages(params);
      list.value = (res.items || []).map((m) => mapLikeItem(m, TYPE_DESC.like));
    } else if (currentType === "comment") {
      const res = await getCommentMessages(params);
      list.value = (res.items || []).map((m) => mapLikeItem(m, TYPE_DESC.comment));
    } else if (currentType === "love") {
      const res = await getLoveMessages(params);
      list.value = (res.items || []).map((m) => mapLikeItem(m, TYPE_DESC.love));
    } else if (currentType === "announcement") {
      const res = await getAnnouncementMessages(params);
      list.value = (res.items || []).map(mapAnnouncementItem);
    } else {
      // visitors / fans 后端暂无接口，显示空状态
      list.value = [];
    }
    return true;
  } catch {
    // 请求失败由 request 层统一 toast
    return false;
  } finally {
    loading.value = false;
  }
}

onLoad((options: any) => {
  type.value = options?.type || "like";
  uni.setNavigationBarTitle({ title: PAGE_TITLES[type.value] || "通知" });
});

onShow(async () => {
  const ok = await loadList();
  // 真正进入通知页并完成加载后才清零未读，避免入口误触或加载失败即丢计数
  if (ok) chatStore.markNoticeRead(type.value as NoticeType);
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

.notice-item { display: flex; align-items: center; padding: 24rpx; background: var(--bg-card); border-radius: 24rpx; margin-bottom: 16rpx;
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
  .locked-btn { margin-top: 40rpx; width: 320rpx; height: 80rpx; line-height: 80rpx; background: linear-gradient(90deg, var(--gold), var(--gold-soft)); color: var(--bg-deep); font-size: 28rpx; font-weight: bold; border-radius: 40rpx; border: none; }
}
</style>
