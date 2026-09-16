import { createRouter, createWebHistory } from 'vue-router'

const routes = [
  {
    path: '/',
    redirect: '/home'
  },
  {
    path: '/home',
    name: 'Home',
    component: () => import('@/views/home/index.vue'),
    meta: { title: '首页' }
  },
  {
    path: '/login',
    name: 'Login',
    component: () => import('@/views/auth/login.vue'),
    meta: { title: '登录' }
  },
  {
    path: '/register',
    name: 'Register',
    component: () => import('@/views/auth/register.vue'),
    meta: { title: '注册' }
  },
  {
    path: '/post/lost',
    name: 'PostLost',
    component: () => import('@/views/post/lost.vue'),
    meta: { title: '发布失物启事', requiresAuth: true }
  },
  {
    path: '/post/found',
    name: 'PostFound',
    component: () => import('@/views/post/found.vue'),
    meta: { title: '发布招领信息', requiresAuth: true }
  },
  {
    path: '/post/mine',
    name: 'MyPosts',
    component: () => import('@/views/post/mine.vue'),
    meta: { title: '我的发布', requiresAuth: true }
  },
  {
    path: '/detail/:id',
    name: 'Detail',
    component: () => import('@/views/detail/index.vue'),
    meta: { title: '物品详情' }
  },
  {
    path: '/claim/:id',
    name: 'Claim',
    component: () => import('@/views/claim/apply.vue'),
    meta: { title: '认领申请', requiresAuth: true }
  },
  {
    path: '/claim/progress',
    name: 'ClaimProgress',
    component: () => import('@/views/claim/progress.vue'),
    meta: { title: '认领进度', requiresAuth: true }
  },
  {
    path: '/messages',
    name: 'Messages',
    component: () => import('@/views/message/index.vue'),
    meta: { title: '消息中心', requiresAuth: true }
  },
  {
    path: '/profile',
    name: 'Profile',
    component: () => import('@/views/profile/index.vue'),
    meta: { title: '个人中心', requiresAuth: true }
  },
  {
    path: '/admin',
    name: 'Admin',
    component: () => import('@/views/admin/layout.vue'),
    meta: { title: '管理后台', requiresAuth: true, requiresAdmin: true },
    redirect: '/admin/dashboard',
    children: [
      {
        path: 'dashboard',
        name: 'AdminDashboard',
        component: () => import('@/views/admin/dashboard.vue'),
        meta: { title: '数据看板' }
      },
      {
        path: 'audit',
        name: 'AdminAudit',
        component: () => import('@/views/admin/audit.vue'),
        meta: { title: '内容审核' }
      },
      {
        path: 'users',
        name: 'AdminUsers',
        component: () => import('@/views/admin/users.vue'),
        meta: { title: '用户管理' }
      },
      {
        path: 'settings',
        name: 'AdminSettings',
        component: () => import('@/views/admin/settings.vue'),
        meta: { title: '系统设置' }
      }
    ]
  }
]

const router = createRouter({
  history: createWebHistory(),
  routes
})

// 路由守卫
router.beforeEach((to, from, next) => {
  document.title = to.meta.title ? `${to.meta.title} - 校园失物招领` : '校园失物招领'

  const token = localStorage.getItem('token')

  if (to.meta.requiresAuth && !token) {
    next('/login')
  } else {
    next()
  }
})

export default router
