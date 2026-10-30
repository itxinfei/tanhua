<template>
  <div class="login-container">
    <!-- 左侧品牌展示区 -->
    <div class="brand-panel">
      <!-- 动态极光光斑 -->
      <div class="brand-aurora aurora-1"></div>
      <div class="brand-aurora aurora-2"></div>
      <div class="brand-aurora aurora-3"></div>

      <!-- 漂浮爱心粒子 -->
      <span
        v-for="h in hearts"
        :key="h.id"
        class="float-heart"
        :style="h.style"
        >&#10084;</span
      >

      <div class="brand-content">
        <div class="brand-logo-wrap">
          <img class="brand-logo" src="/logo/logo-icon-transparent.png" alt="心飞交友" />
        </div>
        <h1 class="brand-title">心飞交友</h1>
        <p class="brand-slogan">让每一次相遇都心动的社交平台</p>

        <div class="brand-stats">
          <div class="stat-item">
            <div class="stat-num">128<small>万</small></div>
            <div class="stat-label">注册用户</div>
          </div>
          <div class="stat-divider"></div>
          <div class="stat-item">
            <div class="stat-num">23<small>万</small></div>
            <div class="stat-label">日均活跃</div>
          </div>
          <div class="stat-divider"></div>
          <div class="stat-item">
            <div class="stat-num">9.6<small>万</small></div>
            <div class="stat-label">今日匹配</div>
          </div>
        </div>

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

// 漂浮爱心粒子（随机位置/大小/时长，营造浪漫氛围）
const hearts = Array.from({ length: 26 }).map((_, i) => ({
  id: i,
  style: {
    left: (Math.random() * 96 + 2).toFixed(1) + '%',
    top: (Math.random() * 94 + 3).toFixed(1) + '%',
    fontSize: (Math.random() * 18 + 16).toFixed(1) + 'px',
    animationDelay: '-' + (Math.random() * 12).toFixed(1) + 's',
    animationDuration: (Math.random() * 6 + 9).toFixed(1) + 's',
  },
}))

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
      } else if (isNetworkError) {
        ElMessage.error('网络异常，无法连接后端服务，请稍后重试')
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
  overflow: hidden;
}

/* ==================== 左侧品牌区 ==================== */
.brand-panel {
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

/* 动态极光光斑 */
.brand-aurora {
  position: absolute;
  border-radius: 50%;
  filter: blur(80px);
  opacity: 0.5;
  z-index: 0;
  animation: drift 16s ease-in-out infinite;
  will-change: transform;
}
.aurora-1 {
  width: 480px;
  height: 480px;
  top: -160px;
  left: -120px;
  background: radial-gradient(circle, var(--xfjy-primary), transparent 70%);
}
.aurora-2 {
  width: 420px;
  height: 420px;
  bottom: -180px;
  right: -100px;
  background: radial-gradient(circle, var(--xfjy-primary-deep), transparent 70%);
  animation-delay: -5s;
}
.aurora-3 {
  width: 320px;
  height: 320px;
  top: 38%;
  left: 52%;
  background: radial-gradient(circle, var(--xfjy-primary-soft), transparent 70%);
  opacity: 0.4;
  animation-delay: -9s;
}

/* 漂浮爱心粒子 */
.float-heart {
  position: absolute;
  color: #fff;
  z-index: 1;
  opacity: 0.7;
  pointer-events: none;
  animation-name: floatHeart;
  animation-timing-function: linear;
  animation-iteration-count: infinite;
  will-change: transform, opacity;
}

/* 内容直接落在渐变背景上（取消玻璃卡片） */
.brand-content {
  position: relative;
  z-index: 2;
  width: 100%;
  max-width: 460px;
  margin: 0 auto;
  color: #fff;
}

/* 内容区柔光遮罩：压暗四周、提亮中央，保证文字在爱心背景上的可读性 */
.brand-content::before {
  content: '';
  position: absolute;
  inset: -48px -36px;
  z-index: -1;
  border-radius: 36px;
  background: radial-gradient(ellipse at center, rgba(70, 8, 84, 0.42), transparent 72%);
  filter: blur(18px);
}

.brand-logo-wrap {
  width: 96px;
  height: 96px;
  margin: 0 auto;
  border-radius: 28px;
  display: flex;
  align-items: center;
  justify-content: center;
  background: rgba(255, 255, 255, 0.18);
  border: 1px solid rgba(255, 255, 255, 0.3);
  box-shadow: 0 14px 40px rgba(0, 0, 0, 0.25), 0 0 0 8px rgba(255, 255, 255, 0.06);
  backdrop-filter: blur(10px);
}

.brand-logo {
  width: 64px;
  height: 64px;
  object-fit: contain;
}

.brand-title {
  margin-top: 22px;
  text-align: center;
  font-size: 40px;
  font-weight: 800;
  letter-spacing: 4px;
  background: linear-gradient(90deg, #ffffff, #ffe3f1);
  -webkit-background-clip: text;
  background-clip: text;
  color: transparent;
  text-shadow: 0 2px 14px rgba(0, 0, 0, 0.18);
}

.brand-slogan {
  margin-top: 10px;
  text-align: center;
  font-size: 15px;
  opacity: 0.9;
  letter-spacing: 1px;
}

/* 数据指标条 */
.brand-stats {
  margin: 32px auto 0;
  display: flex;
  width: fit-content;
  align-items: center;
  padding: 14px 10px;
  border-radius: 18px;
  background: rgba(255, 255, 255, 0.1);
  border: 1px solid rgba(255, 255, 255, 0.16);
  backdrop-filter: blur(10px);
  box-shadow: 0 8px 30px rgba(0, 0, 0, 0.15);
}
.stat-item {
  padding: 0 20px;
  text-align: center;
}
.stat-num {
  font-size: 25px;
  font-weight: 700;
  line-height: 1.1;
}
.stat-num small {
  font-size: 13px;
  font-weight: 500;
  margin-left: 2px;
  opacity: 0.85;
}
.stat-label {
  margin-top: 4px;
  font-size: 12px;
  color: rgba(255, 255, 255, 0.72);
}
.stat-divider {
  width: 1px;
  height: 34px;
  background: rgba(255, 255, 255, 0.2);
}

/* 特性列表（玻璃卡片） */
.feature-list {
  list-style: none;
  margin-top: 32px;
  display: flex;
  flex-direction: column;
  gap: 12px;
}
.feature-list li {
  display: flex;
  align-items: center;
  gap: 14px;
  padding: 12px 14px;
  border-radius: 14px;
  background: rgba(255, 255, 255, 0.08);
  border: 1px solid rgba(255, 255, 255, 0.12);
  backdrop-filter: blur(8px);
  transition: transform 0.25s ease, background 0.25s ease, border-color 0.25s ease;
}
.feature-list li:hover {
  transform: translateX(6px);
  background: rgba(255, 255, 255, 0.16);
  border-color: rgba(255, 255, 255, 0.3);
}
.feature-icon {
  width: 42px;
  height: 42px;
  border-radius: 12px;
  background: linear-gradient(135deg, rgba(255, 255, 255, 0.28), rgba(255, 255, 255, 0.12));
  backdrop-filter: blur(4px);
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
  font-size: 13px;
  opacity: 0.75;
}
.brand-footer {
  position: absolute;
  bottom: 24px;
  left: 0;
  right: 0;
  text-align: center;
  font-size: 12px;
  color: rgba(255, 255, 255, 0.5);
  z-index: 2;
}

@keyframes drift {
  0%, 100% { transform: translate(0, 0) scale(1); }
  50% { transform: translate(30px, -24px) scale(1.08); }
}
@keyframes floatHeart {
  0% { transform: translateY(0) scale(1); opacity: 0; }
  12% { opacity: 0.85; }
  88% { opacity: 0.85; }
  100% { transform: translateY(-130px) scale(1.1); opacity: 0; }
}

/* ==================== 右侧表单区 ==================== */
.form-panel {
  width: 520px;
  display: flex;
  align-items: center;
  justify-content: center;
  background: #fff;
  flex-shrink: 0;
}

.login-card {
  width: 360px;
  padding: 20px;
}

.login-header {
  margin-bottom: 36px;
}

.login-header h2 {
  font-size: 26px;
  font-weight: 700;
  color: #303133;
}

.login-header p {
  margin-top: 6px;
  font-size: 14px;
  color: #909399;
}

.login-btn {
  width: 100%;
  height: 44px;
  font-size: 16px;
  letter-spacing: 4px;
}

.login-footer {
  text-align: center;
  margin-top: 28px;
  font-size: 12px;
  color: #c0c4cc;
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