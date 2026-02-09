/**
 * Mobile端路由配置
 * 采用底部Tab导航 + 页面路由的混合模式
 */

import { createRouter, createWebHistory } from 'vue-router'

const router = createRouter({
  history: createWebHistory(import.meta.env.BASE_URL),
  routes: [
    {
      path: '/login',
      name: 'Login',
      component: () => import('../views/Login.vue'),
      meta: { requiresAuth: false },
    },
    {
      path: '/',
      component: () => import('../layouts/MainLayout.vue'),
      redirect: '/dashboard',
      meta: { requiresAuth: true },
      children: [
        {
          path: 'dashboard',
          name: 'Dashboard',
          component: () => import('../views/Dashboard.vue'),
          meta: { 
            title: '看板',
            tabBar: true,
            icon: 'home-o'
          },
        },
        {
          path: 'quick-entry',
          name: 'QuickEntry',
          component: () => import('../views/QuickEntry.vue'),
          meta: { 
            title: '快速录入',
            tabBar: true,
            icon: 'plus'
          },
        },
        {
          path: 'settlements',
          name: 'Settlements',
          component: () => import('../views/Settlements.vue'),
          meta: { 
            title: '待结算',
            tabBar: true,
            icon: 'clock-o',
            badge: true // 显示待结算数量徽章
          },
        },
        {
          path: 'holdings',
          name: 'Holdings',
          component: () => import('../views/Holdings.vue'),
          meta: { 
            title: '持仓',
            tabBar: true,
            icon: 'chart-trending-o'
          },
        },
        {
          path: 'settings',
          name: 'Settings',
          component: () => import('../views/Settings.vue'),
          meta: { 
            title: '我的',
            tabBar: true,
            icon: 'user-o'
          },
        },
        // 设置页面内的子功能页面
        {
          path: 'accounts',
          name: 'Accounts',
          component: () => import('../views/SubPage.vue'),
          meta: { title: '账户管理' },
        },
        {
          path: 'products',
          name: 'Products',
          component: () => import('../views/SubPage.vue'),
          meta: { title: '产品管理' },
        },
        {
          path: 'ledger',
          name: 'Ledger',
          component: () => import('../views/SubPage.vue'),
          meta: { title: '流水查询' },
        },
        {
          path: 'orders',
          name: 'Orders',
          component: () => import('../views/SubPage.vue'),
          meta: { title: '订单管理' },
        },
      ],
    },
  ],
})

// 路由守卫：检查认证（只依赖本地 token，避免在 Pinia 激活前调用 useUserStore）
router.beforeEach((to, from, next) => {
  const token = localStorage.getItem('token')

  if (to.meta.requiresAuth && !token) {
    next({ name: 'Login', query: { redirect: to.fullPath } })
  } else if (to.name === 'Login' && token) {
    next({ name: 'Dashboard' })
  } else {
    next()
  }
})

export default router
