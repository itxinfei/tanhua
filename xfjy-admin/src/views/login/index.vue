<template>
  <div class="login-container">
    <div class="login-card">
      <div class="login-header">
        <img class="login-logo" src="/logo/logo-icon-transparent.png" alt="心飞交友" />
        <h2>心飞交友运营后台</h2>
        <p>XinFei JiaoYou Admin System</p>
      </div>

      <el-form
        ref="loginFormRef"
        :model="loginForm"
        :rules="loginRules"
        size="large"
        @keyup.enter="handleLogin"
      >
        <el-form-item prop="username">
          <el-input
            v-model="loginForm.username"
            placeholder="请输入管理员账号"
            :prefix-icon="User"
            clearable
          />
        </el-form-item>

        <el-form-item prop="password">
          <el-input
            v-model="loginForm.password"
            type="password"
            placeholder="请输入密码"
            :prefix-icon="Lock"
            show-password
            clearable
          />
        </el-form-item>

        <el-form-item>
          <el-button
            type="primary"
            class="login-btn"
            :loading="loading"
            @click="handleLogin"
          >
            {{ loading ? '登录中...' : '登 录' }}
          </el-button>
        </el-form-item>
      </el-form>

      <div class="login-footer">
        <span>© 2026 心飞交友 · 运营管理系统</span>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import type { FormInstance, FormRules } from 'element-plus'
import { User, Lock } from '@element-plus/icons-vue'
import { useAuthStore } from '@/stores/auth'

const router = useRouter()
const authStore = useAuthStore()

const loginFormRef = ref<FormInstance>()
const loading = ref(false)

const loginForm = reactive({
  username: '',
  password: '',
})

const loginRules: FormRules = {
  username: [{ required: true, message: '请输入管理员账号', trigger: 'blur' }],
  password: [
    { required: true, message: '请输入密码', trigger: 'blur' },
    { min: 6, message: '密码长度不能少于6位', trigger: 'blur' },
  ],
}

async function handleLogin() {
  if (!loginFormRef.value) return
  await loginFormRef.value.validate(async (valid) => {
    if (!valid) return
    loading.value = true
    try {
      await authStore.login(loginForm.username, loginForm.password)
      ElMessage.success('登录成功')
      router.push('/dashboard')
    } catch (err: any) {
      // 后端不可用时使用模拟登录（开发阶段）
      if (err?.message?.includes('Network Error') || err?.code === 'ERR_NETWORK') {
        const mockToken = 'mock-admin-token-' + Date.now()
        authStore.token = mockToken
        authStore.adminInfo = {
          id: 1,
          username: loginForm.username,
          nickname: '超级管理员',
          avatar: '',
          role: 'SUPER_ADMIN',
        }
        localStorage.setItem('xfjy_admin_token', mockToken)
        localStorage.setItem(
          'xfjy_admin_info',
          JSON.stringify(authStore.adminInfo)
        )
        ElMessage.success('登录成功（开发模式）')
        router.push('/dashboard')
      }
    } finally {
      loading.value = false
    }
  })
}
</script>

<style scoped>
.login-container {
  height: 100vh;
  display: flex;
  align-items: center;
  justify-content: center;
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
}

.login-card {
  width: 420px;
  padding: 40px;
  background: #fff;
  border-radius: 12px;
  box-shadow: 0 20px 60px rgba(0, 0, 0, 0.15);
}

.login-header {
  text-align: center;
  margin-bottom: 32px;
}

.login-logo {
  width: 64px;
  height: 64px;
  object-fit: contain;
  border-radius: 14px;
}

.login-header h2 {
  margin-top: 12px;
  font-size: 22px;
  color: #303133;
}

.login-header p {
  margin-top: 4px;
  font-size: 12px;
  color: #909399;
  letter-spacing: 1px;
}

.login-btn {
  width: 100%;
  height: 44px;
  font-size: 16px;
  letter-spacing: 4px;
}

.login-footer {
  text-align: center;
  margin-top: 24px;
  font-size: 12px;
  color: #c0c4cc;
}
</style>