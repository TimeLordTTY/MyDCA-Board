/**
 * 路由配置
 */

import { createRouter, createWebHistory } from 'vue-router'

const router = createRouter({
  // 使用 Vite 的 BASE_URL 作为 history 的 base，保证部署在 /wealth-hub/ 下时路由正常工作
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
        },
        {
          path: 'products',
          name: 'Products',
          component: () => import('../views/Products.vue'),
        },
        {
          path: 'accounts',
          name: 'Accounts',
          component: () => import('../views/Accounts.vue'),
        },
        {
          path: 'ledger',
          name: 'Ledger',
          component: () => import('../views/Ledger.vue'),
        },
        {
          path: 'orders',
          name: 'Orders',
          component: () => import('../views/Orders.vue'),
        },
        {
          path: 'settlements',
          name: 'Settlements',
          component: () => import('../views/Settlements.vue'),
        },
        {
          path: 'holdings',
          name: 'Holdings',
          component: () => import('../views/Holdings.vue'),
        },
        {
          path: 'settings',
          name: 'Settings',
          component: () => import('../views/Settings.vue'),
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
