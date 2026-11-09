<template>
  <div class="login-page">
    <!-- 同色相极淡光晕：品牌浓度的唯一来源，与 dashboard 暖灰底同源 -->
    <div
      aria-hidden="true"
      class="login-page__glow login-page__glow--1"
    />
    <div
      aria-hidden="true"
      class="login-page__glow login-page__glow--2"
    />

    <!-- 左上角轻品牌标识 -->
    <div class="login-page__brand">
      <img
        class="login-page__brand-logo"
        src="/logo/logo-icon-transparent.png"
        alt="心飞交友"
      >
      <span class="login-page__brand-name">心飞交友</span>
    </div>

    <!-- 居中登录卡：白卡 + 细边框，与 dashboard 卡片语言一致 -->
    <div class="login-card">
      <div class="login-card__brand">
        <img
          class="login-card__logo"
          src="/logo/logo-icon-transparent.png"
          alt="心飞交友"
        >
        <h1 class="login-card__title">
          心飞交友运营后台
        </h1>
        <p class="login-card__slogan">
          让每一次相遇都心动的社交平台
        </p>
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

      <div class="login-card__version">
        心飞交友运营管理系统 v1.0
      </div>
    </div>

    <div class="login-page__copy">
      © 2026 心飞交友 · 运营管理系统
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
    } catch (err) {
      // 后端不可用时使用模拟登录（仅开发环境；生产环境禁止降级放行，避免任意密码绕过认证）
      // strict 模式下 catch 变量为 unknown，按 axios 错误结构（message/code）收窄后再读字段
      const error = err as { message?: string; code?: string }
      const isNetworkError = error.message?.includes('Network Error') || error.code === 'ERR_NETWORK'
      if (isNetworkError && import.meta.env.DEV) {
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
      // 网络异常已由 request 拦截器统一提示，此处无需重复
    } finally {
      loading.value = false
    }
  })
}
</script>

<style scoped>
.login-page {
  position: relative;
  min-height: 100vh;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: var(--xfjy-space-6);
  overflow: hidden;
  background: var(--xfjy-bg-page);
}

/* 光晕只切到圆边缘过渡带，中心实色落在视口外，无需 rgba 即可得到柔和晕染 */
.login-page__glow {
  position: absolute;
  border-radius: 50%;
  pointer-events: none;
}

.login-page__glow--1 {
  width: 560px;
  height: 560px;
  top: -220px;
  right: -160px;
  background: radial-gradient(circle, var(--xfjy-primary-200) 0%, transparent 68%);
}

.login-page__glow--2 {
  width: 480px;
  height: 480px;
  bottom: -200px;
  left: -140px;
  background: radial-gradient(circle, var(--xfjy-primary-100) 0%, transparent 68%);
}

/* 左上角品牌标识 */
.login-page__brand {
  position: absolute;
  top: var(--xfjy-space-6);
  left: var(--xfjy-space-8);
  z-index: 1;
  display: flex;
  align-items: center;
  gap: var(--xfjy-space-2);
}

.login-page__brand-logo {
  width: 30px;
  height: 30px;
  object-fit: contain;
}

.login-page__brand-name {
  font-size: var(--xfjy-font-size-md);
  font-weight: 700;
  color: var(--xfjy-text-primary);
}

/* 居中卡片 */
.login-card {
  position: relative;
  z-index: 1;
  width: 420px;
  max-width: 100%;
  padding: var(--xfjy-space-8) var(--xfjy-space-8) var(--xfjy-space-6);
  background: var(--xfjy-bg-card);
  border: 1px solid var(--xfjy-border);
  border-radius: var(--xfjy-radius-xl);
}

.login-card__brand {
  display: flex;
  flex-direction: column;
  align-items: center;
  text-align: center;
}

.login-card__logo {
  width: 56px;
  height: 56px;
  object-fit: contain;
}

.login-card__title {
  margin-top: var(--xfjy-space-4);
  font-size: var(--xfjy-font-size-lg);
  font-weight: 700;
  color: var(--xfjy-text-primary);
}

.login-card__slogan {
  margin-top: var(--xfjy-space-2);
  font-size: var(--xfjy-font-size-sm);
  color: var(--xfjy-text-secondary);
}

.login-card :deep(.el-form) {
  margin-top: var(--xfjy-space-8);
}

.login-btn {
  width: 100%;
  height: 44px;
  font-size: var(--xfjy-font-size-md);
  letter-spacing: 4px;
  /* 品牌主色 #fe3c72 白字仅 3.4:1，按钮降一档到 dark-2 系达 5.1:1 AA */
  --el-button-bg-color: var(--el-color-primary-dark-2);
  --el-button-border-color: var(--el-color-primary-dark-2);
  --el-button-hover-bg-color: var(--xfjy-primary-dark);
  --el-button-hover-border-color: var(--xfjy-primary-dark);
  --el-button-active-bg-color: var(--xfjy-primary-darker);
  --el-button-active-border-color: var(--xfjy-primary-darker);
}

.login-card__version {
  margin-top: var(--xfjy-space-6);
  text-align: center;
  font-size: var(--xfjy-font-size-xs);
  /* 版本号是真实信息，disabled 灰不可读，走 secondary（4.6:1 AA） */
  color: var(--xfjy-text-secondary);
}

.login-page__copy {
  position: absolute;
  bottom: var(--xfjy-space-6);
  left: 0;
  right: 0;
  z-index: 1;
  text-align: center;
  font-size: var(--xfjy-font-size-xs);
  color: var(--xfjy-text-disabled);
}

/* 窄屏：单卡方案只收卡宽与页边距，不存在整块品牌栏的显隐跳变 */
@media (max-width: 480px) {
  .login-page {
    padding: var(--xfjy-space-4);
  }

  .login-card {
    padding: var(--xfjy-space-6) var(--xfjy-space-5) var(--xfjy-space-5);
  }

  .login-page__brand {
    top: var(--xfjy-space-4);
    left: var(--xfjy-space-4);
  }
}
</style>
