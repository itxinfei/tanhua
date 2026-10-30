import { createRouter, createWebHistory } from 'vue-router'
import type { RouteRecordRaw } from 'vue-router'

const Layout = () => import('@/layout/index.vue')

export const routes: RouteRecordRaw[] = [
  {
    path: '/login',
    name: 'Login',
    component: () => import('@/views/login/index.vue'),
    meta: { title: '登录', hidden: true },
  },
  {
    path: '/',
    component: Layout,
    redirect: '/dashboard',
    children: [
      {
        path: 'dashboard',
        name: 'Dashboard',
        component: () => import('@/views/dashboard/index.vue'),
        meta: { title: '数据看板', icon: 'DataBoard' },
      },
    ],
  },
  {
    path: '/user',
    component: Layout,
    redirect: '/user/list',
    meta: { title: '用户中心', icon: 'User' },
    children: [
      {
        path: 'list',
        name: 'UserList',
        component: () => import('@/views/user/index.vue'),
        meta: { title: '用户列表', icon: 'UserFilled', roles: ['SUPER_ADMIN', 'ADMIN'] },
      },
    ],
  },
  {
    path: '/content',
    component: Layout,
    redirect: '/content/movement',
    meta: { title: '内容管理', icon: 'Film' },
    children: [
      {
        path: 'movement',
        name: 'MovementList',
        component: () => import('@/views/content/movement.vue'),
        meta: { title: '动态管理', icon: 'PictureFilled', roles: ['SUPER_ADMIN', 'ADMIN'] },
      },
      {
        path: 'video',
        name: 'VideoList',
        component: () => import('@/views/content/video.vue'),
        meta: { title: '视频管理', icon: 'VideoCamera', roles: ['SUPER_ADMIN', 'ADMIN'] },
      },
      {
        path: 'comment',
        name: 'CommentList',
        component: () => import('@/views/content/comment.vue'),
        meta: { title: '评论管理', icon: 'ChatDotSquare', roles: ['SUPER_ADMIN', 'ADMIN'] },
      },
    ],
  },
  {
    path: '/audit',
    component: Layout,
    redirect: '/audit/review',
    meta: { title: '审核中心', icon: 'DocumentChecked' },
    children: [
      {
        path: 'review',
        name: 'ReviewWorkbench',
        component: () => import('@/views/review/index.vue'),
        meta: { title: '内容审核', icon: 'Finished', roles: ['SUPER_ADMIN', 'ADMIN', 'OPERATOR'] },
      },
      {
        path: 'report',
        name: 'ReportList',
        component: () => import('@/views/report/index.vue'),
        meta: { title: '举报处理', icon: 'WarningFilled', roles: ['SUPER_ADMIN', 'ADMIN', 'OPERATOR'] },
      },
    ],
  },
  {
    path: '/operation',
    component: Layout,
    redirect: '/operation/notice',
    meta: { title: '运营管理', icon: 'Operation' },
    children: [
      {
        path: 'notice',
        name: 'NoticeList',
        component: () => import('@/views/notice/index.vue'),
        meta: { title: '公告管理', icon: 'Bell', roles: ['SUPER_ADMIN', 'ADMIN'] },
      },
      {
        path: 'vip',
        name: 'VipPackages',
        component: () => import('@/views/vip/index.vue'),
        meta: { title: 'VIP套餐', icon: 'Medal', roles: ['SUPER_ADMIN', 'ADMIN'] },
      },
      {
        path: 'pay',
        name: 'PayOrders',
        component: () => import('@/views/pay/index.vue'),
        meta: { title: '支付订单', icon: 'Wallet', roles: ['SUPER_ADMIN', 'ADMIN'] },
      },
      {
        path: 'sensitive',
        name: 'SensitiveWords',
        component: () => import('@/views/sensitive/index.vue'),
        meta: { title: '敏感词库', icon: 'Filter', roles: ['SUPER_ADMIN', 'ADMIN'] },
      },
    ],
  },
  {
    path: '/service',
    component: Layout,
    redirect: '/service/workbench',
    meta: { title: '客服中心', icon: 'Service' },
    children: [
      {
        path: 'workbench',
        name: 'KefuWorkbench',
        component: () => import('@/views/kefu/workbench.vue'),
        meta: { title: '客服工作台', icon: 'ChatLineRound', roles: ['SUPER_ADMIN', 'ADMIN', 'OPERATOR'] },
      },
      {
        path: 'feedback',
        name: 'KefuFeedback',
        component: () => import('@/views/kefu/feedback.vue'),
        meta: { title: '用户反馈', icon: 'ChatDotRound', roles: ['SUPER_ADMIN', 'ADMIN', 'OPERATOR'] },
      },
      {
        path: 'faq',
        name: 'KefuFaq',
        component: () => import('@/views/kefu/faq.vue'),
        meta: { title: 'FAQ管理', icon: 'QuestionFilled', roles: ['SUPER_ADMIN', 'ADMIN', 'OPERATOR'] },
      },
    ],
  },
  {
    path: '/system',
    component: Layout,
    redirect: '/system/admin',
    meta: { title: '系统管理', icon: 'Setting' },
    children: [
      {
        path: 'admin',
        name: 'SystemAdmin',
        component: () => import('@/views/system/admin.vue'),
        meta: { title: '管理员管理', icon: 'Avatar', roles: ['SUPER_ADMIN'] },
      },
      {
        path: 'log',
        name: 'SystemLog',
        component: () => import('@/views/system/log.vue'),
        meta: { title: '操作日志', icon: 'Document', roles: ['SUPER_ADMIN', 'ADMIN', 'OPERATOR'] },
      },
    ],
  },
  {
    path: '/profile',
    component: Layout,
    meta: { hidden: true },
    children: [
      {
        path: '',
        name: 'Profile',
        component: () => import('@/views/profile/index.vue'),
        meta: { title: '个人中心', hidden: true },
      },
    ],
  },
  {
    path: '/:pathMatch(.*)*',
    name: 'NotFound',
    component: () => import('@/views/error/404.vue'),
    meta: { title: '404', hidden: true },
  },
]

const router = createRouter({
  history: createWebHistory(),
  routes,
})

// 直接从 localStorage 读取当前角色（守卫内不引入 store，避免 router→store→request→router 循环依赖）
function getCurrentRole(): string {
  try {
    const info = localStorage.getItem('xfjy_admin_info')
    return info ? (JSON.parse(info).role as string) || '' : ''
  } catch {
    return ''
  }
}

// 路由守卫
router.beforeEach((to, _from, next) => {
  // 双斜杠规范化：//dashboard → /dashboard，避免命中 404 兜底路由
  if (to.path.includes('//')) {
    const normalized = to.path.replace(/\/+/g, '/')
    next(normalized)
    return
  }
  const token = localStorage.getItem('xfjy_admin_token')
  if (to.path === '/login') {
    next()
    return
  }
  if (!token) {
    next('/login')
    return
  }
  // 角色权限校验：meta.roles 存在且当前角色不在允许列表内，回退到看板
  const roles = to.meta.roles as string[] | undefined
  if (roles && roles.length > 0 && !roles.includes(getCurrentRole())) {
    next('/dashboard')
    return
  }
  next()
})

router.afterEach((to) => {
  document.title = to.meta.title ? `${to.meta.title} - 心飞交友后台` : '心飞交友后台'
})

export default router