<template>
  <div class="header-wrapper">
    <!-- 左侧：折叠按钮 + 面包屑 -->
    <div class="header-left">
      <el-icon class="collapse-btn" :size="20" @click="appStore.toggleSidebar()">
        <Fold v-if="!appStore.sidebarCollapsed" />
        <Expand v-else />
      </el-icon>
      <Breadcrumb />
    </div>

    <!-- 右侧：环境标签 + 待办铃铛 + 用户信息 -->
    <div class="header-right">
      <!-- DEV 环境标识（生产构建自动隐藏，避免误操作线上） -->
      <el-tag v-if="isDev" size="small" type="warning" effect="dark" class="env-tag">DEV</el-tag>
      <el-tooltip content="待审核内容" placement="bottom">
        <el-badge :value="pendingCount" :hidden="pendingCount === 0" :max="99" class="todo-badge">
          <el-icon class="header-icon" :size="18" @click="goReview"><Bell /></el-icon>
        </el-badge>
      </el-tooltip>
      <el-tooltip content="刷新" placement="bottom">
        <el-icon class="header-icon" :size="18" @click="refreshPage"><Refresh /></el-icon>
      </el-tooltip>
      <el-tooltip content="全屏" placement="bottom">
        <el-icon class="header-icon" :size="18" @click="toggleFullscreen"><FullScreen /></el-icon>
      </el-tooltip>

      <el-dropdown trigger="click" @command="handleCommand">
        <div class="user-info">
          <el-avatar :size="32" :src="authStore.adminInfo?.avatar">
            <el-icon><UserFilled /></el-icon>
          </el-avatar>
          <span class="username">{{ authStore.adminInfo?.nickname || '管理员' }}</span>
          <el-icon :size="12"><ArrowDown /></el-icon>
        </div>
        <template #dropdown>
          <el-dropdown-menu>
            <el-dropdown-item command="profile">
              <el-icon><User /></el-icon>个人中心
            </el-dropdown-item>
            <el-dropdown-item command="password">
              <el-icon><Lock /></el-icon>修改密码
            </el-dropdown-item>
            <el-dropdown-item divided command="logout">
              <el-icon><SwitchButton /></el-icon>退出登录
            </el-dropdown-item>
          </el-dropdown-menu>
        </template>
      </el-dropdown>
    </div>

    <!-- 修改密码对话框 -->
    <el-dialog v-model="passwordVisible" title="修改密码" width="420px" :close-on-click-modal="false">
      <el-form ref="passwordFormRef" :model="passwordForm" :rules="passwordRules" label-width="90px">
        <el-form-item label="旧密码" prop="oldPassword">
          <el-input v-model="passwordForm.oldPassword" type="password" show-password placeholder="请输入旧密码" />
        </el-form-item>
        <el-form-item label="新密码" prop="newPassword">
          <el-input v-model="passwordForm.newPassword" type="password" show-password placeholder="请输入新密码（至少6位）" />
        </el-form-item>
        <el-form-item label="确认密码" prop="confirmPassword">
          <el-input v-model="passwordForm.confirmPassword" type="password" show-password placeholder="请再次输入新密码" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="passwordVisible = false">取消</el-button>
        <el-button type="primary" :loading="passwordLoading" @click="submitPassword">确定</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessageBox, ElMessage } from 'element-plus'
import type { FormInstance, FormRules } from 'element-plus'
import Breadcrumb from './Breadcrumb.vue'
import { useAppStore } from '@/stores/app'
import { useAuthStore } from '@/stores/auth'
import { changePasswordApi } from '@/api/auth'
import { getStatOverviewApi } from '@/api/dashboard'

const router = useRouter()
const appStore = useAppStore()
const authStore = useAuthStore()

/** 是否开发环境（生产构建自动隐藏 DEV 标签） */
const isDev = import.meta.env.DEV
/** 待审核内容数（铃铛角标，复用看板概览接口） */
const pendingCount = ref(0)

onMounted(() => {
  authStore.restoreAdminInfo()
  loadPendingCount()
})

/** 加载待审核数 */
async function loadPendingCount() {
  try {
    const stat = await getStatOverviewApi()
    pendingCount.value = stat?.pendingReviews || 0
  } catch {
    // 接口不可用时铃铛静默隐藏角标
  }
}

/** 跳转审核工作台 */
function goReview() {
  router.push('/audit/review')
}

function refreshPage() {
  window.location.reload()
}

function toggleFullscreen() {
  if (!document.fullscreenElement) {
    document.documentElement.requestFullscreen()
  } else {
    document.exitFullscreen()
  }
}

// ==================== 修改密码 ====================
const passwordVisible = ref(false)
const passwordLoading = ref(false)
const passwordFormRef = ref<FormInstance>()
const passwordForm = reactive({
  oldPassword: '',
  newPassword: '',
  confirmPassword: '',
})

const passwordRules: FormRules = {
  oldPassword: [{ required: true, message: '请输入旧密码', trigger: 'blur' }],
  newPassword: [
    { required: true, message: '请输入新密码', trigger: 'blur' },
    { min: 6, message: '密码长度不能少于6位', trigger: 'blur' },
  ],
  confirmPassword: [
    { required: true, message: '请再次输入新密码', trigger: 'blur' },
    {
      validator: (_rule, value, callback) => {
        if (value !== passwordForm.newPassword) {
          callback(new Error('两次输入的密码不一致'))
        } else {
          callback()
        }
      },
      trigger: 'blur',
    },
  ],
}

function openPasswordDialog() {
  passwordForm.oldPassword = ''
  passwordForm.newPassword = ''
  passwordForm.confirmPassword = ''
  passwordVisible.value = true
}

async function submitPassword() {
  if (!passwordFormRef.value) return
  await passwordFormRef.value.validate(async (valid) => {
    if (!valid) return
    passwordLoading.value = true
    try {
      await changePasswordApi(passwordForm.oldPassword, passwordForm.newPassword)
      ElMessage.success('密码修改成功，请重新登录')
      passwordVisible.value = false
      authStore.logout()
      router.push('/login')
    } catch {
      // 拦截器已提示错误
    } finally {
      passwordLoading.value = false
    }
  })
}

async function handleCommand(command: string) {
  if (command === 'logout') {
    try {
      await ElMessageBox.confirm('确定要退出登录吗？', '提示', {
        confirmButtonText: '确定',
        cancelButtonText: '取消',
        type: 'warning',
      })
      authStore.logout()
      ElMessage.success('已退出登录')
      router.push('/login')
    } catch {
      // 取消
    }
  } else if (command === 'password') {
    openPasswordDialog()
  } else if (command === 'profile') {
    router.push('/profile')
  }
}
</script>

<style scoped>
.header-wrapper {
  width: 100%;
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.header-left {
  display: flex;
  align-items: center;
  gap: 12px;
}

.collapse-btn {
  cursor: pointer;
  color: #494a4d;
  padding: 6px;
  border-radius: 4px;
  transition: background 0.2s, color 0.2s;
}

.collapse-btn:hover {
  color: #409eff;
  background: rgba(64, 158, 255, 0.1);
}

.header-right {
  display: flex;
  align-items: center;
  gap: 16px;
}

.header-icon {
  cursor: pointer;
  color: #606266;
}

.header-icon:hover {
  color: #409eff;
}

.user-info {
  display: flex;
  align-items: center;
  gap: 8px;
  cursor: pointer;
  padding: 4px 8px;
  border-radius: 4px;
}

.user-info:hover {
  background: #f5f7fa;
}

.username {
  font-size: 14px;
  color: #303133;
}

/* DEV 环境标识 */
.env-tag {
  font-weight: 600;
  letter-spacing: 1px;
}

/* 待办铃铛角标 */
.todo-badge {
  display: flex;
  align-items: center;
}
</style>