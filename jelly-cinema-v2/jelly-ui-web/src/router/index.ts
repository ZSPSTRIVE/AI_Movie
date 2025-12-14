import { createRouter, createWebHistory, RouteRecordRaw } from 'vue-router'
import NProgress from 'nprogress'
import 'nprogress/nprogress.css'
import { useUserStore } from '@/stores/user'

NProgress.configure({ showSpinner: false })

const routes: RouteRecordRaw[] = [
  {
    path: '/',
    component: () => import('@/layouts/MainLayout.vue'),
    children: [
      {
        path: '',
        name: 'Home',
        component: () => import('@/views/home/index.vue'),
        meta: { title: '首页' }
      },
      {
        path: 'film',
        name: 'FilmList',
        component: () => import('@/views/film/index.vue'),
        meta: { title: '电影' }
      },
      {
        path: 'film/:id',
        name: 'FilmDetail',
        component: () => import('@/views/film/detail.vue'),
        meta: { title: '电影详情' }
      },
      {
        path: 'community',
        name: 'Community',
        component: () => import('@/views/community/index.vue'),
        meta: { title: '社区' }
      },
      {
        path: 'community/post/:id',
        name: 'PostDetail',
        component: () => import('@/views/community/detail.vue'),
        meta: { title: '帖子详情' }
      },
      {
        path: 'user',
        name: 'UserProfile',
        component: () => import('@/views/user/index.vue'),
        meta: { title: '个人中心', requireAuth: true }
      },
      {
        path: 'ai-lab',
        name: 'AILab',
        component: () => import('@/views/ai/index.vue'),
        meta: { title: 'AI 实验室', requireAuth: true }
      },
      {
        path: 'search',
        name: 'Search',
        component: () => import('@/views/search/index.vue'),
        meta: { title: '搜索结果' }
      },
      {
        path: 'about',
        name: 'About',
        component: () => import('@/views/about/index.vue'),
        meta: { title: '关于我们' }
      },
      {
        path: 'help',
        name: 'Help',
        component: () => import('@/views/help/index.vue'),
        meta: { title: '帮助与反馈' }
      },
      {
        path: 'growth',
        name: 'Growth',
        component: () => import('@/views/growth/index.vue'),
        meta: { title: '积分商城', requireAuth: true }
      }
    ]
  },
  {
    path: '/chat',
    name: 'Chat',
    component: () => import('@/views/chat/index.vue'),
    meta: { title: '消息中心', requireAuth: true }
  },
  // 管理后台
  {
    path: '/admin',
    component: () => import('@/layouts/AdminLayout.vue'),
    meta: { requireAuth: true, requireAdmin: true },
    children: [
      {
        path: '',
        name: 'AdminDashboard',
        component: () => import('@/views/admin/Dashboard.vue'),
        meta: { title: '管理后台' }
      },
      {
        path: 'users',
        name: 'AdminUsers',
        component: () => import('@/views/admin/UserManage.vue'),
        meta: { title: '用户管理' }
      },
      {
        path: 'sensitive',
        name: 'AdminSensitive',
        component: () => import('@/views/admin/SensitiveWord.vue'),
        meta: { title: '敏感词管理' }
      },
      {
        path: 'reports',
        name: 'AdminReports',
        component: () => import('@/views/admin/ReportHandle.vue'),
        meta: { title: '举报处理' }
      },
      {
        path: 'groups',
        name: 'AdminGroups',
        component: () => import('@/views/admin/GroupAudit.vue'),
        meta: { title: '群组审计' }
      },
      {
        path: 'films',
        name: 'AdminFilms',
        component: () => import('@/views/admin/FilmManage.vue'),
        meta: { title: '影片管理' }
      }
    ]
  },
  {
    path: '/login',
    name: 'Login',
    component: () => import('@/views/login/index.vue'),
    meta: { title: '登录' }
  },
  {
    path: '/register',
    name: 'Register',
    component: () => import('@/views/login/register.vue'),
    meta: { title: '注册' }
  },
  {
    path: '/:pathMatch(.*)*',
    name: 'NotFound',
    component: () => import('@/views/error/404.vue'),
    meta: { title: '页面不存在' }
  }
]

const router = createRouter({
  history: createWebHistory(),
  routes,
  scrollBehavior(_to, _from, savedPosition) {
    if (savedPosition) {
      return savedPosition
    } else {
      return { top: 0 }
    }
  }
})

// 路由守卫
router.beforeEach((to, _from, next) => {
  NProgress.start()
  
  // 设置页面标题
  document.title = to.meta.title ? `${to.meta.title} - 果冻影院` : '果冻影院'
  
  // 权限检查
  if (to.meta.requireAuth) {
    const userStore = useUserStore()
    if (!userStore.isLogin) {
      next({ name: 'Login', query: { redirect: to.fullPath } })
      return
    }
  }
  
  next()
})

router.afterEach(() => {
  NProgress.done()
})

export default router
