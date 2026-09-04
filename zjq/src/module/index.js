// ============================================================
// 系统设置模块 · 对外合并入口
// ------------------------------------------------------------
// 本文件是「合并进 erp-web 主工程」的标准入口，提供三类能力：
//   1. 根组件  : SystemSettingsLayout（深色侧栏 + 顶栏 + 内容区）
//   2. 路由配置: createSystemSettingsRoutes() 返回 /setting 下子路由
//   3. 配置能力: setApiBase / setApiConfig / setAppName，以及统一 api 对象
//
// 用法详见 README.md「二、合并进 erp-web 主工程」。
// ============================================================

import SystemSettingsLayout from '../layout/SystemLayout.vue'
import SystemSettingsHome from '../views/SystemSettingsHome.vue'
import UserManage from '../views/UserManage.vue'
import RolePermission from '../views/RolePermission.vue'
import WarehouseManage from '../views/WarehouseManage.vue'
import SupplierManage from '../views/SupplierManage.vue'
import CustomerManage from '../views/CustomerManage.vue'

import { api, ApiError } from '../api/index.js'
import { apiConfig, setApiBase, setApiConfig, setAppName, getApiBase } from '../api/config.js'
import { resetDb } from '../api/mock.js'

// 组件级导出：主工程其它页面可直接 import 使用（如 tab 内嵌）
export { UserManage, RolePermission, WarehouseManage, SupplierManage, CustomerManage }

// 主容器：一个自带侧栏/顶栏的系统设置布局，挂到 /setting 父路由即可
export { SystemSettingsLayout, SystemSettingsHome }

// 统一 API 对象与配置能力
export { api, ApiError, apiConfig, setApiBase, setApiConfig, setAppName, getApiBase, resetDb }

// 菜单/导航元信息：供主工程把「系统设置」合并进其主导航
export const systemSettingsNav = {
  label: '系统设置',
  icon: '⚙',
  path: '/setting',
  children: [
    { path: '/setting/users', label: '用户管理', perms: 'system:user:list' },
    { path: '/setting/roles', label: '角色权限', perms: 'system:role:list' },
    { path: '/setting/warehouses', label: '仓库管理', perms: 'base:warehouse:list' },
    { path: '/setting/suppliers', label: '供应商管理', perms: 'base:supplier:list' },
    { path: '/setting/customers', label: '客户管理', perms: 'base:customer:list' }
  ]
}

// 返回系统设置的所有子路由。
// 主工程用法：把返回值展开到父路由 { path: '/setting', component: SystemSettingsLayout } 的 children。
//
//   import { SystemSettingsLayout, createSystemSettingsRoutes } from '@/system-settings/src/module/index.js'
//   {
//     path: '/setting',
//     component: SystemSettingsLayout,
//     meta: { requiresAuth: true, title: '系统设置' },
//     children: createSystemSettingsRoutes()
//   }
//
// '' 路由为六板块首页（SET-01~06 一行一个入口），可与主工程菜单高亮逻辑协作。
export function createSystemSettingsRoutes() {
  return [
    { path: '', name: 'SystemSettingsHome', component: SystemSettingsHome, meta: { title: '功能板块', requiresAuth: true } },
    { path: 'users', name: 'SystemUserManage', component: UserManage, meta: { title: '用户管理', requiresAuth: true, perms: 'system:user:list' } },
    { path: 'roles', name: 'SystemRolePermission', component: RolePermission, meta: { title: '角色权限', requiresAuth: true, perms: 'system:role:list' } },
    { path: 'warehouses', name: 'SystemWarehouseManage', component: WarehouseManage, meta: { title: '仓库管理', requiresAuth: true, perms: 'base:warehouse:list' } },
    { path: 'suppliers', name: 'SystemSupplierManage', component: SupplierManage, meta: { title: '供应商管理', requiresAuth: true, perms: 'base:supplier:list' } },
    { path: 'customers', name: 'SystemCustomerManage', component: CustomerManage, meta: { title: '客户管理', requiresAuth: true, perms: 'base:customer:list' } }
  ]
}

// 独立运行的完整路由（main.js 使用）。合并时不要使用本函数，避免与主工程路由冲突。
export function createStandaloneRoutes() {
  return [
    { path: '/login', name: 'SystemLogin', component: () => import('../views/LoginView.vue') },
    { path: '/', redirect: '/setting' },
    {
      path: '/setting',
      name: 'SystemSettingsLayout',
      component: SystemSettingsLayout,
      meta: { requiresAuth: true, title: '系统设置' },
      children: createSystemSettingsRoutes()
    }
  ]
}
