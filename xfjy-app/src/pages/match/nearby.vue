<template>
  <view class="nearby-page">
    <!-- 筛选栏 -->
    <!-- 筛选栏（单选语义 + 可键盘操作） -->
    <view class="filter-bar" role="radiogroup" aria-label="筛选条件">
      <a11y-button class="filter-item btn-feedback" v-for="d in distances" :key="d" role="radio" :checked="distance === d" :label="d === 0 ? '距离不限' : `距离 ${d} 公里以内`" @tap="distance = d">
        <text>{{ d === 0 ? '全部' : d + 'km' }}</text>
      </a11y-button>
      <a11y-button class="filter-item gender btn-feedback" role="radio" :checked="gender === '女'" :label="`性别筛选：${gender}`" @tap="gender = gender === '女' ? '全部' : '女'">
        <text>{{ gender }}</text>
      </a11y-button>
    </view>

    <!-- 用户列表 -->
    <empty-state icon="location" title="附近暂无用户" desc="扩大搜索范围或稍后再来看看" />
  </view>
</template>

<script setup lang="ts">
import { ref, watch } from "vue";
import EmptyState from "@/components/empty-state.vue";
import { onShow, onPullDownRefresh } from "@dcloudio/uni-app";
import A11yButton from "@/components/a11y-button.vue";
import { getNearby, loveUser, type NearUser } from "@/api/match";

/** 距离筛选选项（km，0 表示全部） */
const DISTANCE_OPTIONS = [0, 1, 3, 5, 10];
/** "全部"距离对应的搜索半径（米），取 50km 覆盖全城 */
const ALL_DISTANCE_METER = 50000;

const distances = DISTANCE_OPTIONS;
const distance = ref(0);
const gender = ref("全部");
const userList = ref<NearUser[]>([]);
const loading = ref(false);

/** 跳转用户详情 */
function goDetail(id: number): void {
  uni.navigateTo({ url: `/pages/match/detail?id=${id}` });
}

/** 快速喜欢 */
async function handleQuickLike(id: number): Promise<void> {
  try {
    const matched = await loveUser(id);
    uni.showToast({ title: matched ? "互相喜欢，匹配成功！" : "已喜欢", icon: "success" });
  } catch {
    // 请求失败由 request 层统一 toast
  }
}

/** 请求序号：筛选连续切换时，仅最后一次请求的响应允许写入列表（防竞态） */
let loadSeq = 0;

/** 加载附近用户（允许重入，过期响应丢弃） */
async function loadNearby(): Promise<void> {
  const seq = ++loadSeq;
  loading.value = true;
  try {
    const genderParam = gender.value === "女" ? "woman" : undefined;
    const distanceMeter = distance.value === 0 ? ALL_DISTANCE_METER : distance.value * 1000;
    const list = await getNearby(genderParam, distanceMeter);
    // 响应返回时筛选已变（或有更新的请求），丢弃本次结果
    if (seq !== loadSeq) return;
    userList.value = list || [];
  } catch {
    // 请求失败由 request 层统一 toast
  } finally {
    if (seq === loadSeq) loading.value = false;
  }
}

/** 筛选条件变化时重新加载 */
watch([distance, gender], () => loadNearby());

onShow(() => loadNearby());

/** 下拉刷新：重新加载附近用户 */
onPullDownRefresh(async () => {
  try {
    await loadNearby();
  } finally {
    uni.stopPullDownRefresh();
  }
});
</script>

<style lang="scss" scoped>
.nearby-page { min-height: 100vh; background: #f5f5f5; }

/* ===== 筛选栏 ===== */
.filter-bar { display: flex; gap: 12rpx; padding: 20rpx 24rpx; background: #fff; position: sticky; top: 0; z-index: 10; box-shadow: 0 2rpx 8rpx rgba(0, 0, 0, 0.03);
  /* 触摸目标 ≥24px：10rpx 26rpx padding + 24rpx 字号偏小 */
  .filter-item { padding: 16rpx 26rpx; min-height: 48rpx; border-radius: 28rpx; font-size: 24rpx; color: #595959; background: #f5f5f5; transition: all 0.2s;
    &.active { background: linear-gradient(135deg, var(--color-primary-deep), var(--color-primary)); color: #fff; box-shadow: 0 4rpx 12rpx rgba(255, 94, 142, 0.3); }
    &.gender.active { background: linear-gradient(135deg, var(--color-primary-deep), var(--color-primary)); }
  }
}

/* ===== 用户列表 ===== */
.user-list { padding: 20rpx 24rpx; }

/* 行容器：卡片主体 + 独立喜欢按钮 */
.user-row { display: flex; align-items: center; gap: 16rpx; margin-bottom: 16rpx; }

.user-item { flex: 1; display: flex; align-items: center; padding: 24rpx; background: #fff; border-radius: 24rpx;
  .avatar-wrap { position: relative; flex-shrink: 0;
    .avatar { width: 100rpx; height: 100rpx; border-radius: 50%; background: #f0f0f0; border: 3rpx solid #ffeff5; }
  }
  .info { flex: 1; margin-left: 24rpx;
    .name-row { display: flex; align-items: center; gap: 12rpx;
      .nickname { font-size: 30rpx; font-weight: bold; color: #333; }
      .age-badge { background: linear-gradient(135deg, var(--color-primary-deep), var(--color-primary)); color: #fff; font-size: 20rpx; padding: 2rpx 12rpx; border-radius: 12rpx; }
    }
    .distance-tag { margin-top: 10rpx; display: inline-flex; background: #ffeff5; border-radius: 16rpx; padding: 4rpx 16rpx;
      /* #ff5e9c on #ffeff5 ≈2.6:1 不达标 → #c2185b */
      text { font-size: 22rpx; color: #c2185b; }
    }
  }
}
/* #ff5e9c on #ffeff5 → #c2185b */
.like-btn { width: 72rpx; height: 72rpx; border-radius: 50%; background: #ffeff5; display: flex; align-items: center; justify-content: center; font-size: 36rpx; color: #c2185b; flex-shrink: 0; transition: all 0.2s;
  &:active { background: #c2185b; color: #fff; }
}

.loading-tip { display: flex; justify-content: center; padding: 32rpx 0;
  /* #ccc → #767676 */
  text { font-size: 24rpx; color: #767676; }
}
</style>