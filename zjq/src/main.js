import { createApp } from 'vue'
import { createRouter, createWebHashHistory } from 'vue-router'
import App from './App.vue'
import { setAppName } from './api/config.js'
import './style.css'

// 独立运行入口：默认项目名。合并进主工程时请改用 src/module/index.js 的挂载方式。
setAppName('NOVA ERP')

const router = createRouter({
  // 使用 hash 模式，便于文件方式打开或与主工程并存
  history: createWebHashHistory(),
  routes: [
    { path: '/login', name: 'Login', component: () => import('./views/LoginView.vue') },
    { path: '/', redirect: '/setting' },
    {
      path: '/setting',
      name: 'SystemSettingsLayout',
      component: () => import('./layout/SystemLayout.vue'),
      meta: { requiresAuth: true, title: '系统设置' },
      children: [
        { path: '', name: 'SystemSettingsHome', component: () => import('./views/SystemSettingsHome.vue'), meta: { title: '功能板块', requiresAuth: true } },
        { path: 'users', name: 'UserManage', component: () => import('./views/UserManage.vue'), meta: { title: '用户管理', perms: 'system:user:list' } },
        { path: 'roles', name: 'RolePermission', component: () => import('./views/RolePermission.vue'), meta: { title: '角色权限', perms: 'system:role:list' } },
        { path: 'warehouses', name: 'WarehouseManage', component: () => import('./views/WarehouseManage.vue'), meta: { title: '仓库管理', perms: 'base:warehouse:list' } },
        { path: 'suppliers', name: 'SupplierManage', component: () => import('./views/SupplierManage.vue'), meta: { title: '供应商管理', perms: 'base:supplier:list' } },
        { path: 'customers', name: 'CustomerManage', component: () => import('./views/CustomerManage.vue'), meta: { title: '客户管理', perms: 'base:customer:list' } }
      ]
    }
  ]
})

// 登录守卫 + 权限守卫（合并进主工程时，主工程路由守卫会统一处理登录态）
router.beforeEach((to) => {
  const token = localStorage.getItem('z_token')
  // 需要登录的路由：无 token 一律回登录页
  if (to.meta.requiresAuth && !token) {
    return { path: '/login' }
  }
  // 带权限码的路由：从 z_user 读取 permissions 校验，无权限重定向到 /setting 首页
  if (token && to.meta.perms) {
    let user = null
    try {
      user = JSON.parse(localStorage.getItem('z_user') || 'null')
    } catch (_) {
      user = null
    }
    const perms = (user && Array.isArray(user.permissions)) ? user.permissions : []
    if (!perms.includes(to.meta.perms)) {
      return { path: '/setting' }
    }
  }
  return true
})

createApp(App).use(router).mount('#app')
