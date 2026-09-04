// 统一请求封装：
// - useMock=true 时走内置 Mock（src/api/mock.js），便于无后端预览；
// - useMock=false 时走 axios 风格真实 HTTP（fetch 实现），兼容统一返回结构 { code, message, data, fieldErrors }。
import { apiConfig } from './config.js'
import { mockApi } from './mock.js'

async function http(method, url, body, query = {}) {
  const base = apiConfig.baseURL.replace(/\/$/, '')
  const path = url.startsWith('/') ? url : `/${url}`
  const qs = Object.entries(query)
    .filter(([, v]) => v !== undefined && v !== null && v !== '')
    .map(([k, v]) => `${encodeURIComponent(k)}=${encodeURIComponent(v)}`)
    .join('&')
  const full = `${base}${path}${qs ? `?${qs}` : ''}`
  const token = localStorage.getItem(apiConfig.tokenKey)
  const headers = { 'Content-Type': 'application/json' }
  if (token) headers.Authorization = `Bearer ${token}`

  const resp = await fetch(full, {
    method,
    headers,
    body: body === undefined ? undefined : JSON.stringify(body)
  })
  let payload = null
  try {
    payload = await resp.json()
  } catch {
    payload = null
  }
  if (!resp.ok) {
    throw new ApiError(payload?.message || `请求失败（HTTP ${resp.status}）`, payload)
  }
  return payload?.data
}

// 解包统一返回结构 { code, message, data, fieldErrors }：成功返回 .data，
// 失败抛 ApiError —— 与 http 分支（非 2xx 抛 ApiError）行为对齐，视图层统一 catch。
function unwrap(envelope) {
  if (envelope && envelope.code === 200) {
    return envelope.data
  }
  throw new ApiError(envelope?.message || '请求失败', envelope || null)
}

export class ApiError extends Error {
  constructor(message, payload) {
    super(message)
    this.code = payload?.code
    this.fieldErrors = payload?.fieldErrors
    this.payload = payload
  }
}

function tablePath(table) {
  const map = {
    users: '/system/users',
    roles: '/system/roles',
    warehouses: '/base/warehouses',
    suppliers: '/base/suppliers',
    customers: '/base/customers'
  }
  return map[table] || `/${table}`
}

export const api = {
  useMock: () => apiConfig.useMock,

  async login(payload) {
    if (apiConfig.useMock) return unwrap(await mockApi.login(payload))
    return http('POST', '/auth/login', payload)
  },
  async me() {
    if (apiConfig.useMock) return unwrap(await mockApi.me(localStorage.getItem(apiConfig.tokenKey)))
    return http('GET', '/auth/me')
  },
  async logout() {
    if (apiConfig.useMock) return { message: '退出成功' }
    return http('POST', '/auth/logout')
  },

  // 通用主数据 CRUD
  async list(table, params) {
    if (apiConfig.useMock) return unwrap(await mockApi.list(table, params))
    const { page, size, filters = {} } = params
    const query = { page, size, ...filters }
    return http('GET', tablePath(table), undefined, query)
  },
  async listAll(table) {
    if (apiConfig.useMock) return unwrap(await mockApi.listAll(table))
    return http('GET', `${tablePath(table)}/enabled`)
  },
  async create(table, payload) {
    if (apiConfig.useMock) return unwrap(await mockApi.create(table, payload, table === 'users' ? 'username' : 'code'))
    return http('POST', tablePath(table), payload)
  },
  async update(table, id, payload) {
    if (apiConfig.useMock) return unwrap(await mockApi.update(table, id, payload))
    return http('PUT', `${tablePath(table)}/${id}`, payload)
  },
  async toggleStatus(table, id, status) {
    if (apiConfig.useMock) return unwrap(await mockApi.toggleStatus(table, id, status))
    return http('PUT', `${tablePath(table)}/${id}/status`, { status })
  },
  async resetPassword(id) {
    if (apiConfig.useMock) return unwrap(await mockApi.resetPassword(id))
    return http('PUT', `/system/users/${id}/password`, { newPassword: 'Admin@123' })
  },

  // 角色权限
  async saveRoleMenus(roleCode, menuIds) {
    if (apiConfig.useMock) return unwrap(await mockApi.saveRoleMenus(roleCode, menuIds))
    return http('PUT', `/system/roles/${roleCode}/menus`, { menuIds })
  },
  async getRoleMenus(roleCode) {
    if (apiConfig.useMock) return unwrap(await mockApi.getRoleMenus(roleCode))
    return http('GET', `/system/roles/${roleCode}/menus`)
  },
  async getMenus() {
    if (apiConfig.useMock) return unwrap(await mockApi.listAll('menus'))
    return http('GET', '/system/menus')
  }
}
