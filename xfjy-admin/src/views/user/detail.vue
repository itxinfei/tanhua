<template>
  <div class="page-container">
    <div class="detail-header">
      <el-button :icon="ArrowLeft" @click="router.back()">返回</el-button>
      <span class="detail-title">用户详情</span>
    </div>

    <div v-loading="loading">
      <!-- 基本信息 -->
      <div class="detail-card">
        <div class="card-title">基本信息</div>
        <div class="user-profile">
          <el-avatar :size="80" :src="detail.avatar">
            <el-icon :size="40"><UserFilled /></el-icon>
          </el-avatar>
          <div class="profile-info">
            <div class="profile-name">
              {{ detail.nickname }}
              <el-tag :type="detail.gender === '女' ? 'danger' : 'primary'" size="small">{{ detail.gender }}</el-tag>
              <el-tag v-if="detail.vipLevel > 0" type="warning" size="small">VIP{{ detail.vipLevel }}</el-tag>
              <el-tag :type="detail.status === 'NORMAL' ? 'success' : 'danger'" size="small">
                {{ detail.status === 'NORMAL' ? '正常' : '已封禁' }}
              </el-tag>
            </div>
            <div class="profile-meta">
              <span>ID: {{ detail.id }}</span>
              <span>手机: {{ detail.mobile }}</span>
              <span>年龄: {{ detail.age }}</span>
              <span>城市: {{ detail.city }}</span>
            </div>
            <div class="profile-meta">
              <span>注册: {{ detail.createdAt }}</span>
              <span>最后活跃: {{ detail.lastActiveAt }}</span>
            </div>
            <div v-if="detail.signature" class="profile-signature">签名: {{ detail.signature }}</div>
            <div v-if="detail.freezeReason" class="freeze-reason">封禁原因: {{ detail.freezeReason }}</div>
          </div>
        </div>
      </div>

      <!-- 数据统计 -->
      <div class="detail-card">
        <div class="card-title">数据统计</div>
        <el-row :gutter="16">
          <el-col :span="4" v-for="stat in stats" :key="stat.label">
            <div class="stat-box">
              <div class="stat-num">{{ stat.value }}</div>
              <div class="stat-text">{{ stat.label }}</div>
            </div>
          </el-col>
        </el-row>
      </div>

      <!-- 照片墙 -->
      <div class="detail-card">
        <div class="card-title">照片墙</div>
        <div class="photo-wall">
          <div v-for="(photo, i) in detail.photos" :key="i" class="photo-item">
            <el-image :src="photo" fit="cover" style="width: 120px; height: 120px; border-radius: 8px" />
          </div>
          <div v-if="!detail.photos?.length" class="no-data">暂无照片</div>
        </div>
      </div>

      <!-- 标签 -->
      <div class="detail-card">
        <div class="card-title">个人标签</div>
        <div class="tag-list">
          <el-tag v-for="tag in detail.tags" :key="tag" effect="plain" style="margin: 4px">{{ tag }}</el-tag>
          <span v-if="!detail.tags?.length" class="no-data">暂无标签</span>
        </div>
      </div>

      <!-- 操作 -->
      <div class="detail-card">
        <div class="card-title">账号操作</div>
        <el-space>
          <el-button v-if="detail.status === 'NORMAL'" type="danger" @click="handleFreeze">封禁账号</el-button>
          <el-button v-else type="success" @click="handleUnfreeze">解封账号</el-button>
          <el-button type="warning" @click="handleResetPwd">重置密码</el-button>
        </el-space>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { ArrowLeft } from '@element-plus/icons-vue'
import { getUserDetailApi, freezeUserApi, unfreezeUserApi, resetUserPasswordApi } from '@/api/user'
import type { UserDetail } from '@/api/user'

const route = useRoute()
const router = useRouter()
const loading = ref(false)

const detail = ref<UserDetail>({
  id: 0,
  mobile: '',
  nickname: '',
  avatar: '',
  gender: '',
  age: 0,
  city: '',
  status: 'NORMAL',
  vipLevel: 0,
  createdAt: '',
  lastActiveAt: '',
  photos: [],
  signature: '',
  tags: [],
  postCount: 0,
  followerCount: 0,
  followingCount: 0,
  matchCount: 0,
  reportCount: 0,
})

const stats = computed(() => [
  { label: '动态数', value: detail.value.postCount },
  { label: '粉丝数', value: detail.value.followerCount },
  { label: '关注数', value: detail.value.followingCount },
  { label: '匹配数', value: detail.value.matchCount },
  { label: '被举报数', value: detail.value.reportCount },
  { label: 'VIP等级', value: detail.value.vipLevel },
])

async function loadDetail() {
  const id = Number(route.params.id)
  loading.value = true
  try {
    const res = await getUserDetailApi(id)
    detail.value = res
  } catch {
    // Mock 兜底
    detail.value = {
      id,
      mobile: '13812345678',
      nickname: '星辰大海',
      avatar: '',
      gender: '女',
      age: 24,
      city: '上海',
      status: 'NORMAL',
      vipLevel: 2,
      createdAt: '2026-06-15 10:30:00',
      lastActiveAt: '2026-10-22 15:30:00',
      photos: [],
      signature: '愿所有的美好都如约而至',
      tags: ['旅行', '美食', '摄影', '猫控'],
      postCount: 56,
      followerCount: 1280,
      followingCount: 320,
      matchCount: 89,
      reportCount: 0,
    }
  } finally {
    loading.value = false
  }
}

async function handleFreeze() {
  try {
    const { value: reason } = await ElMessageBox.prompt('请输入封禁原因', '封禁账号', {
      confirmButtonText: '确认封禁',
      cancelButtonText: '取消',
      inputPattern: /\S+/,
      inputErrorMessage: '封禁原因不能为空',
    })
    await freezeUserApi(detail.value.id, reason)
    ElMessage.success('已封禁')
    detail.value.status = 'FROZEN'
    detail.value.freezeReason = reason
  } catch {
    // 取消
  }
}

async function handleUnfreeze() {
  try {
    await ElMessageBox.confirm('确定解封该账号吗？', '提示', { type: 'warning' })
    await unfreezeUserApi(detail.value.id)
    ElMessage.success('已解封')
    detail.value.status = 'NORMAL'
    detail.value.freezeReason = undefined
  } catch {
    // 取消
  }
}

async function handleResetPwd() {
  try {
    await ElMessageBox.confirm('确定重置该用户密码吗？', '提示', { type: 'warning' })
    await resetUserPasswordApi(detail.value.id)
    ElMessage.success('密码已重置')
  } catch {
    // 取消
  }
}

onMounted(() => {
  loadDetail()
})
</script>

<style scoped>
.detail-header {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 16px;
}

.detail-title {
  font-size: 18px;
  font-weight: 600;
}

.detail-card {
  background: #fff;
  border-radius: 8px;
  padding: 20px;
  margin-bottom: 16px;
}

.card-title {
  font-size: 15px;
  font-weight: 600;
  margin-bottom: 16px;
  padding-left: 10px;
  border-left: 3px solid #409eff;
}

.user-profile {
  display: flex;
  gap: 20px;
}

.profile-name {
  font-size: 18px;
  font-weight: 600;
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 8px;
}

.profile-meta {
  display: flex;
  gap: 24px;
  color: #606266;
  font-size: 13px;
  margin-bottom: 4px;
}

.profile-signature {
  color: #909399;
  font-size: 13px;
  margin-top: 8px;
}

.freeze-reason {
  color: #f56c6c;
  font-size: 13px;
  margin-top: 4px;
}

.stat-box {
  text-align: center;
  padding: 16px;
  background: #f5f7fa;
  border-radius: 8px;
}

.stat-num {
  font-size: 22px;
  font-weight: 600;
  color: #303133;
}

.stat-text {
  font-size: 12px;
  color: #909399;
  margin-top: 4px;
}

.photo-wall {
  display: flex;
  flex-wrap: wrap;
  gap: 12px;
}

.tag-list {
  display: flex;
  flex-wrap: wrap;
}

.no-data {
  color: #c0c4cc;
  font-size: 13px;
}
</style>