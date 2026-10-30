<template>
  <div class="page-container">
    <el-row :gutter="16">
      <!-- 左侧：基本信息卡片 -->
      <el-col :span="8">
        <div class="profile-card">
          <div class="avatar-area">
            <el-avatar :size="80" :src="authStore.adminInfo?.avatar">
              <el-icon :size="40"><UserFilled /></el-icon>
            </el-avatar>
            <h3 class="nickname">{{ authStore.adminInfo?.nickname || '管理员' }}</h3>
            <el-tag :type="roleTagType" size="small">{{ roleLabel }}</el-tag>
          </div>
          <el-divider />
          <el-descriptions :column="1" size="small">
            <el-descriptions-item label="登录账号">
              <el-icon><User /></el-icon>
              {{ authStore.adminInfo?.username }}
            </el-descriptions-item>
            <el-descriptions-item label="角色">
              {{ roleLabel }}
            </el-descriptions-item>
            <el-descriptions-item label="账号状态">
              <el-tag type="success" size="small">正常</el-tag>
            </el-descriptions-item>
          </el-descriptions>
        </div>
      </el-col>

      <!-- 右侧：编辑资料 -->
      <el-col :span="16">
        <div class="profile-card">
          <div class="card-title">编辑资料</div>
          <el-form
            ref="profileFormRef"
            :model="profileForm"
            :rules="profileRules"
            label-width="80px"
            style="max-width: 420px"
          >
            <el-form-item label="昵称" prop="nickname">
              <el-input v-model="profileForm.nickname" placeholder="请输入昵称" maxlength="20" show-word-limit />
            </el-form-item>
            <el-form-item label="头像地址" prop="avatar">
              <el-input v-model="profileForm.avatar" placeholder="请输入头像图片URL" clearable />
            </el-form-item>
            <el-form-item>
              <el-button type="primary" :loading="saving" @click="handleSave">保存修改</el-button>
              <el-button @click="handleReset">重置</el-button>
            </el-form-item>
          </el-form>
        </div>

        <div class="profile-card" style="margin-top: 16px">
          <div class="card-title">安全设置</div>
          <div class="security-item">
            <div>
              <div class="security-label">登录密码</div>
              <div class="security-desc">定期修改密码可以保护账号安全</div>
            </div>
            <el-button type="primary" plain @click="passwordVisible = true">修改密码</el-button>
          </div>
        </div>
      </el-col>
    </el-row>

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
import { computed, onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import type { FormInstance, FormRules } from 'element-plus'
import { useAuthStore } from '@/stores/auth'
import { changePasswordApi, updateProfileApi } from '@/api/auth'

const router = useRouter()
const authStore = useAuthStore()

const roleLabel = computed(() => {
  const roleMap: Record<string, string> = {
    SUPER_ADMIN: '超级管理员',
    ADMIN: '管理员',
    OPERATOR: '运营',
  }
  return roleMap[authStore.adminInfo?.role || ''] || '管理员'
})

const roleTagType = computed(() => {
  return authStore.adminInfo?.role === 'SUPER_ADMIN' ? 'danger' : 'primary'
})

// ==================== 编辑资料 ====================
const profileFormRef = ref<FormInstance>()
const saving = ref(false)
const profileForm = reactive({
  nickname: '',
  avatar: '',
})

const profileRules: FormRules = {
  nickname: [{ required: true, message: '请输入昵称', trigger: 'blur' }],
}

function syncFormFromStore() {
  profileForm.nickname = authStore.adminInfo?.nickname || ''
  profileForm.avatar = authStore.adminInfo?.avatar || ''
}

onMounted(() => {
  authStore.restoreAdminInfo()
  syncFormFromStore()
})

async function handleSave() {
  if (!profileFormRef.value) return
  await profileFormRef.value.validate(async (valid) => {
    if (!valid) return
    saving.value = true
    try {
      await updateProfileApi(profileForm.nickname, profileForm.avatar)
      // 同步更新本地存储
      if (authStore.adminInfo) {
        authStore.adminInfo.nickname = profileForm.nickname
        authStore.adminInfo.avatar = profileForm.avatar
        localStorage.setItem('xfjy_admin_info', JSON.stringify(authStore.adminInfo))
      }
      ElMessage.success('资料修改成功')
    } catch {
      // 拦截器已提示
    } finally {
      saving.value = false
    }
  })
}

function handleReset() {
  syncFormFromStore()
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
      // 拦截器已提示
    } finally {
      passwordLoading.value = false
    }
  })
}
</script>

<style scoped>
.profile-card {
  background: #fff;
  border-radius: 8px;
  padding: 24px;
}

.avatar-area {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 12px;
  padding: 8px 0;
}

.nickname {
  margin: 0;
  font-size: 18px;
  color: #303133;
}

.card-title {
  font-size: 15px;
  font-weight: 600;
  color: #303133;
  margin-bottom: 20px;
  padding-bottom: 12px;
  border-bottom: 1px solid #ebeef5;
}

.security-item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 16px 0;
}

.security-label {
  font-size: 14px;
  color: #303133;
  font-weight: 500;
}

.security-desc {
  font-size: 12px;
  color: #909399;
  margin-top: 4px;
}
</style>