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
    meta: { title: '用户管理', icon: 'User' },
    children: [
      {
        path: 'list',
        name: 'UserList',
        component: () => import('@/views/user/index.vue'),
        meta: { title: '用户列表', icon: 'UserFilled' },
      },
      {
        path: 'detail/:id',
        name: 'UserDetail',
        component: () => import('@/views/user/detail.vue'),
        meta: { title: '用户详情', hidden: true, activeMenu: '/user/list' },
      },
    ],
  },
  {
    path: '/review',
    component: Layout,
    redirect: '/review/workbench',
    meta: { title: '内容审核', icon: 'DocumentChecked' },
    children: [
      {
        path: 'workbench',
        name: 'ReviewWorkbench',
        component: () => import('@/views/review/index.vue'),
        meta: { title: '审核工作台', icon: 'Finished' },
      },
    ],
  },
  {
    path: '/report',
    component: Layout,
    redirect: '/report/list',
    meta: { title: '举报处理', icon: 'Warning' },
    children: [
      {
        path: 'list',
        name: 'ReportList',
        component: () => import('@/views/report/index.vue'),
        meta: { title: '举报列表', icon: 'WarningFilled' },
      },
    ],
  },
  {
    path: '/notice',
    component: Layout,
    redirect: '/notice/list',
    meta: { title: '公告管理', icon: 'Bell' },
    children: [
      {
        path: 'list',
        name: 'NoticeList',
        component: () => import('@/views/notice/index.vue'),
        meta: { title: '公告列表', icon: 'BellFilled' },
      },
    ],
  },
  {
    path: '/operation',
    component: Layout,
    redirect: '/operation/pay',
    meta: { title: '运营管理', icon: 'Operation' },
    children: [
      {
        path: 'pay',
        name: 'PayOrders',
        component: () => import('@/views/pay/index.vue'),
        meta: { title: '支付订单', icon: 'Wallet' },
      },
      {
        path: 'sensitive',
        name: 'SensitiveWords',
        component: () => import('@/views/sensitive/index.vue'),
        meta: { title: '敏感词库', icon: 'Filter' },
      },
      {
        path: 'vip',
        name: 'VipPackages',
        component: () => import('@/views/vip/index.vue'),
        meta: { title: 'VIP套餐', icon: 'Medal' },
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

// 路由守卫
router.beforeEach((to, _from, next) => {
  const token = localStorage.getItem('xfjy_admin_token')
  if (to.path === '/login') {
    next()
  } else if (!token) {
    next('/login')
  } else {
    next()
  }
})

router.afterEach((to) => {
  document.title = to.meta.title ? `${to.meta.title} - 心飞交友后台` : '心飞交友后台'
})

export default router