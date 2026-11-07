<template>
  <div class="login-container">
    <!-- 左侧品牌展示区 -->
    <div class="brand-panel">
      <div class="brand-content">
        <div class="brand-logo-wrap">
          <img class="brand-logo" src="/logo/logo-icon-transparent.png" alt="心飞交友" />
        </div>
        <h1 class="brand-title">心飞交友</h1>
        <p class="brand-slogan">让每一次相遇都心动的社交平台</p>

        <ul class="feature-list">
          <li v-for="f in features" :key="f.title">
            <span class="feature-icon">
              <el-icon :size="18"><component :is="f.icon" /></el-icon>
            </span>
            <div>
              <div class="feature-title">{{ f.title }}</div>
              <div class="feature-desc">{{ f.desc }}</div>
            </div>
          </li>
        </ul>
      </div>

      <div class="brand-footer">
        <span>© 2026 心飞交友 · 运营管理系统</span>
      </div>
    </div>

    <!-- 右侧登录表单 -->
    <div class="form-panel">
      <div class="login-card">
        <div class="login-header">
          <h2>欢迎回来</h2>
          <p>请登录运营管理后台</p>
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
          <span>心飞交友运营管理系统 v1.0</span>
        </div>
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

// 品牌特性列表
const features = [
  { icon: 'DataAnalysis', title: '实时数据看板', desc: 'DAU、留存、转化率一目了然' },
  { icon: 'CircleCheck', title: '智能内容审核', desc: 'AI 辅助 + 人工复核双重保障' },
  { icon: 'Service', title: '一站式客服工作台', desc: 'IM 会话、反馈、FAQ 统一管理' },
  { icon: 'Lock', title: '权限与安全管控', desc: '角色隔离、操作日志全程可溯' },
]

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
      // 后端不可用时使用模拟登录（仅开发环境；生产环境禁止降级放行，避免任意密码绕过认证）
      const isNetworkError = err?.message?.includes('Network Error') || err?.code === 'ERR_NETWORK'
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
.login-container {
  height: 100vh;
  display: flex;
  overflow: hidden;
}

/* ==================== 左侧品牌区 ==================== */
.brand-panel {
  /* 品牌区叠加层：深色渐变背景上，卡片用深色玻璃底保证内文 4.5:1+，边框/图标用白色保持可见 */
  /* stylelint-disable function-disallowed-list */
  --brand-overlay-strong: rgba(255, 255, 255, 0.18);
  --brand-overlay: rgba(255, 255, 255, 0.3);
  /* feature 卡底：深色玻璃（白字 5.4:1+ AA；原 0.08 白底仅 2.1:1） */
  --brand-overlay-soft: rgba(0, 0, 0, 0.4);
  --brand-overlay-faint: rgba(255, 255, 255, 0.2);
  --brand-overlay-hover: rgba(0, 0, 0, 0.5);
  --brand-overlay-icon: rgba(255, 255, 255, 0.2);
  --brand-shadow: 0 8px 24px rgba(0, 0, 0, 0.2);
  /* stylelint-enable function-disallowed-list */
  flex: 1.5;
  position: relative;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  overflow: hidden;
  padding: 60px;
  background:
    radial-gradient(120% 120% at 0% 0%, var(--xfjy-primary-soft) 0%, transparent 55%),
    radial-gradient(120% 120% at 100% 0%, var(--xfjy-primary-deep) 0%, transparent 50%),
    radial-gradient(140% 140% at 50% 120%, var(--xfjy-primary-soft) 0%, transparent 55%),
    linear-gradient(135deg, var(--xfjy-primary-deep) 0%, var(--xfjy-primary) 100%);
  isolation: isolate;
}

/* 内容直接落在渐变背景上 */
.brand-content {
  position: relative;
  z-index: 2;
  width: 100%;
  max-width: 460px;
  margin: 0 auto;
  color: var(--xfjy-text-inverse);
}

.brand-logo-wrap {
  width: 96px;
  height: 96px;
  margin: 0 auto;
  border-radius: var(--xfjy-radius-3xl);
  display: flex;
  align-items: center;
  justify-content: center;
  background: var(--brand-overlay-strong);
  border: 1px solid var(--brand-overlay);
  box-shadow: var(--brand-shadow);
}

.brand-logo {
  width: 64px;
  height: 64px;
  object-fit: contain;
}

.brand-title {
  margin-top: var(--xfjy-space-6);
  text-align: center;
  font-size: 40px;
  font-weight: 800;
  letter-spacing: 4px;
  color: var(--xfjy-text-inverse);
}

.brand-slogan {
  margin-top: var(--xfjy-space-2);
  text-align: center;
  font-size: 15px;
  opacity: 1;
  letter-spacing: 1px;
  /* 渐变浅区辅助，保证 15px 白字全区域可读 */
  text-shadow: 0 1px 2px rgba(0, 0, 0, 0.25);
}

/* 特性列表（半透明卡片） */
.feature-list {
  list-style: none;
  margin-top: var(--xfjy-space-8);
  display: flex;
  flex-direction: column;
  gap: var(--xfjy-space-3);
}
.feature-list li {
  display: flex;
  align-items: center;
  gap: var(--xfjy-space-3);
  padding: var(--xfjy-space-3) var(--xfjy-space-4);
  border-radius: var(--xfjy-radius-xl);
  background: var(--brand-overlay-soft);
  border: 1px solid var(--brand-overlay-faint);
  transition: background 0.25s ease, border-color 0.25s ease;
}
.feature-list li:hover {
  background: var(--brand-overlay-hover);
  border-color: var(--brand-overlay);
}
.feature-icon {
  width: 42px;
  height: 42px;
  border-radius: var(--xfjy-radius-lg);
  background: var(--brand-overlay-icon);
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}
.feature-title {
  font-size: 15px;
  font-weight: 600;
}
.feature-desc {
  margin-top: 2px;
  font-size: var(--xfjy-font-size-sm);
  /* 深色玻璃卡上 0.92 白 ≈ 4.6:1 AA 达标（原 0.75 仅 2.1:1） */
  opacity: 0.92;
}
.brand-footer {
  position: absolute;
  bottom: var(--xfjy-space-6);
  left: 0;
  right: 0;
  text-align: center;
  font-size: var(--xfjy-font-size-xs);
  color: var(--xfjy-text-inverse);
  z-index: 2;
  /* 深色底条：12px 小字在渐变最浅区也达 4.8:1 AA（原 0.5 白直落背景仅 1.6:1） */
  background: rgba(0, 0, 0, 0.35);
  padding: var(--xfjy-space-2) var(--xfjy-space-3);
}

/* ==================== 右侧表单区 ==================== */
.form-panel {
  width: 520px;
  display: flex;
  align-items: center;
  justify-content: center;
  background: var(--xfjy-text-inverse);
  flex-shrink: 0;
}

.login-card {
  width: 360px;
  padding: var(--xfjy-space-5);
}

.login-header {
  margin-bottom: 36px;
}

.login-header h2 {
  font-size: 26px;
  font-weight: 700;
  color: var(--xfjy-text-primary);
}

.login-header p {
  /* stylelint-disable-next-line declaration-property-value-disallowed-list -- 贴合标题基线的副文案间距 6px，见 AGENTS.md §11 存量豁免 */
  margin-top: 6px;
  font-size: var(--xfjy-font-size-base);
  color: var(--xfjy-text-secondary);
}

.login-btn {
  width: 100%;
  height: 44px;
  font-size: var(--xfjy-font-size-md);
  letter-spacing: 4px;
  /* 品牌主色 #fe3c72 白字仅 3.4:1，按钮降一档到 dark-2 系达 5.1:1 AA */
  --el-button-bg-color: var(--el-color-primary-dark-2);
  --el-button-border-color: var(--el-color-primary-dark-2);
  --el-button-hover-bg-color: #a8284d;
  --el-button-hover-border-color: #a8284d;
  --el-button-active-bg-color: #8f2241;
  --el-button-active-border-color: #8f2241;
}

.login-footer {
  text-align: center;
  margin-top: 28px;
  font-size: var(--xfjy-font-size-xs);
  color: var(--xfjy-text-disabled);
}

/* ==================== 响应式 ==================== */
@media (max-width: 900px) {
  .brand-panel {
    display: none;
  }

  .form-panel {
    width: 100%;
  }
}
</style>