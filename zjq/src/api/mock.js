// 内置 Mock 数据层：在无后端时预览完整界面（含登录态、权限、增删改）。
// 数据以 localStorage 持久化，重启页面不丢失；可通过 api/config.js 的 useMock 切换真实后端。
const LS_KEY = 'z_mock_db_v1'

const MENUS = [
  { id: 100, parentId: 0, menuName: '系统设置', menuType: 1, path: '/setting', perms: '', sort: 1, status: 1 },
  { id: 101, parentId: 100, menuName: '用户管理', menuType: 1, path: '/setting/users', perms: 'system:user:list', sort: 1, status: 1 },
  { id: 1011, parentId: 101, menuName: '新增用户', menuType: 2, path: '', perms: 'system:user:add', sort: 1, status: 1 },
  { id: 1012, parentId: 101, menuName: '编辑用户', menuType: 2, path: '', perms: 'system:user:edit', sort: 2, status: 1 },
  { id: 1013, parentId: 101, menuName: '启停用户', menuType: 2, path: '', perms: 'system:user:status', sort: 3, status: 1 },
  { id: 1014, parentId: 101, menuName: '重置密码', menuType: 2, path: '', perms: 'system:user:password', sort: 4, status: 1 },
  { id: 102, parentId: 100, menuName: '角色权限', menuType: 1, path: '/setting/roles', perms: 'system:role:list', sort: 2, status: 1 },
  { id: 1021, parentId: 102, menuName: '配置角色权限', menuType: 2, path: '', perms: 'system:role:config', sort: 1, status: 1 },
  { id: 1022, parentId: 102, menuName: '启停角色', menuType: 2, path: '', perms: 'system:role:status', sort: 2, status: 1 },
  { id: 103, parentId: 100, menuName: '仓库管理', menuType: 1, path: '/setting/warehouses', perms: 'base:warehouse:list', sort: 3, status: 1 },
  { id: 1031, parentId: 103, menuName: '新增仓库', menuType: 2, path: '', perms: 'base:warehouse:add', sort: 1, status: 1 },
  { id: 1032, parentId: 103, menuName: '编辑仓库', menuType: 2, path: '', perms: 'base:warehouse:edit', sort: 2, status: 1 },
  { id: 1033, parentId: 103, menuName: '启停仓库', menuType: 2, path: '', perms: 'base:warehouse:status', sort: 3, status: 1 },
  { id: 104, parentId: 100, menuName: '供应商管理', menuType: 1, path: '/setting/suppliers', perms: 'base:supplier:list', sort: 4, status: 1 },
  { id: 1041, parentId: 104, menuName: '新增供应商', menuType: 2, path: '', perms: 'base:supplier:add', sort: 1, status: 1 },
  { id: 1042, parentId: 104, menuName: '编辑供应商', menuType: 2, path: '', perms: 'base:supplier:edit', sort: 2, status: 1 },
  { id: 1043, parentId: 104, menuName: '启停供应商', menuType: 2, path: '', perms: 'base:supplier:status', sort: 3, status: 1 },
  { id: 105, parentId: 100, menuName: '客户管理', menuType: 1, path: '/setting/customers', perms: 'base:customer:list', sort: 5, status: 1 },
  { id: 1051, parentId: 105, menuName: '新增客户', menuType: 2, path: '', perms: 'base:customer:add', sort: 1, status: 1 },
  { id: 1052, parentId: 105, menuName: '编辑客户', menuType: 2, path: '', perms: 'base:customer:edit', sort: 2, status: 1 },
  { id: 1053, parentId: 105, menuName: '启停客户', menuType: 2, path: '', perms: 'base:customer:status', sort: 3, status: 1 }
]

const ROLES = [
  { roleCode: 'admin', roleName: '管理员', status: 1, remark: '系统内置，拥有全部权限，不可停用', menuIds: MENUS.map((m) => m.id) },
  { roleCode: 'purchaser', roleName: '采购员', status: 1, remark: '供应商查看、采购订单创建/提交', menuIds: [100, 104] },
  { roleCode: 'purchase_manager', roleName: '采购主管', status: 1, remark: '采购订单审核', menuIds: [100, 104] },
  { roleCode: 'seller', roleName: '销售员', status: 1, remark: '客户查看、销售订单创建/提交', menuIds: [100, 105] },
  { roleCode: 'sales_manager', roleName: '销售主管', status: 1, remark: '销售订单审核', menuIds: [100, 105] },
  { roleCode: 'warehouse', roleName: '仓库管理员', status: 1, remark: '出入库、库存查询', menuIds: [100, 103] },
  { roleCode: 'manager', roleName: '经营管理者', status: 1, remark: '报表只读', menuIds: [] }
]

const USERS = [
  { id: 1, username: 'admin', realName: '系统管理员', roleCode: 'admin', status: 1, createdAt: '2026-09-01 09:00:00' },
  { id: 2, username: 'purchaser01', realName: '采购员小采', roleCode: 'purchaser', status: 1, createdAt: '2026-09-01 09:05:00' },
  { id: 3, username: 'seller01', realName: '销售员小销', roleCode: 'seller', status: 1, createdAt: '2026-09-01 09:10:00' },
  { id: 4, username: 'whkeeper01', realName: '仓管员小仓', roleCode: 'warehouse', status: 1, createdAt: '2026-09-01 09:15:00' }
]

// 初始密码（仅 Mock 用，真实后端为 BCrypt 哈希且不回传）
const PASSWORDS = {
  admin: 'Admin@123',
  purchaser01: '123456',
  seller01: '123456',
  whkeeper01: '123456'
}

const WAREHOUSES = [
  { id: 1, code: 'WH001', name: '主仓', manager: '李仓管', address: '高新区一号库房', status: 1, remark: '系统预置主仓', createdAt: '2026-09-01 10:00:00' },
  { id: 2, code: 'WH002', name: '东区仓', manager: '王小东', address: '工业园东区 3 号', status: 1, remark: '', createdAt: '2026-09-02 10:00:00' }
]

const SUPPLIERS = [
  { id: 1, code: 'SUP001', name: '晨光办公用品有限公司', contact: '张晨', phone: '13800001111', status: 1, remark: '', createdAt: '2026-09-01 11:00:00' },
  { id: 2, code: 'SUP002', name: '华为授权经销商', contact: '刘华', phone: '13800002222', status: 1, remark: '电子产品', createdAt: '2026-09-01 11:05:00' }
]

const CUSTOMERS = [
  { id: 1, code: 'CUS001', name: '云帆科技公司', contact: '陈帆', phone: '13900001111', status: 1, remark: '重点客户', createdAt: '2026-09-01 12:00:00' },
  { id: 2, code: 'CUS002', name: '蓝海商贸中心', contact: '陈海', phone: '13900002222', status: 1, remark: '', createdAt: '2026-09-01 12:05:00' }
]

function seed() {
  return {
    menus: MENUS,
    roles: ROLES,
    users: USERS,
    passwords: PASSWORDS,
    warehouses: WAREHOUSES,
    suppliers: SUPPLIERS,
    customers: CUSTOMERS
  }
}

function loadDb() {
  const raw = localStorage.getItem(LS_KEY)
  if (raw) {
    try {
      return JSON.parse(raw)
    } catch {
      // fallthrough
    }
  }
  const db = seed()
  saveDb(db)
  return db
}

function saveDb(db) {
  localStorage.setItem(LS_KEY, JSON.stringify(db))
}

export function resetDb() {
  localStorage.removeItem(LS_KEY)
}

// ---- 模拟接口（按统一返回结构 { code, message, data, fieldErrors }） ----
const ok = (data) => ({ code: 200, message: 'OK', data })
const fail = (code, message, fieldErrors) => ({ code, message, fieldErrors: fieldErrors || null })

function sleep() {
  return new Promise((resolve) => setTimeout(resolve, 120))
}

function nextId(rows) {
  return rows.length ? Math.max(...rows.map((r) => r.id)) + 1 : 1
}

function now() {
  const d = new Date()
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')} ${String(d.getHours()).padStart(2, '0')}:${String(d.getMinutes()).padStart(2, '0')}:${String(d.getSeconds()).padStart(2, '0')}`
}

export const mockApi = {
  async login({ username, password }) {
    await sleep()
    const db = loadDb()
    const user = db.users.find((u) => u.username === username)
    if (!user || user.status !== 1 || db.passwords[user.username] !== password) {
      return fail(401, '用户名或密码错误')
    }
    const role = db.roles.find((r) => r.roleCode === user.roleCode)
    const menuIds = (role && role.menuIds) || []
    const permissions = db.menus
      .filter((m) => menuIds.includes(m.id) && m.perms)
      .map((m) => m.perms)
    const token = `mock-token-${user.id}-${Date.now()}`
    return ok({ token, user: { ...user, roleName: role ? role.roleName : '' }, permissions })
  },

  async me(token) {
    await sleep()
    const db = loadDb()
    const match = token && token.match(/mock-token-(\d+)-/)
    if (!match) return fail(401, '未登录或登录已过期')
    const user = db.users.find((u) => u.id === Number(match[1]))
    if (!user || user.status !== 1) return fail(401, '账号已停用')
    const role = db.roles.find((r) => r.roleCode === user.roleCode)
    const menuIds = (role && role.menuIds) || []
    const permissions = db.menus.filter((m) => menuIds.includes(m.id) && m.perms).map((m) => m.perms)
    return ok({ ...user, roleName: role ? role.roleName : '', permissions })
  },

  // 通用分页+筛选
  async list(table, { page = 1, size = 10, filters = {} }) {
    await sleep()
    const db = loadDb()
    let rows = db[table].map((r) => ({ ...r }))
    Object.entries(filters).forEach(([key, value]) => {
      if (value === undefined || value === null || value === '') return
      if (typeof value === 'string') {
        rows = rows.filter((r) => String(r[key] || '').toLowerCase().includes(String(value).toLowerCase()))
      } else {
        rows = rows.filter((r) => r[key] === value)
      }
    })
    const total = rows.length
    const start = (page - 1) * size
    return ok({ total, records: rows.slice(start, start + size) })
  },

  async listAll(table) {
    await sleep()
    const db = loadDb()
    return ok(db[table].map((r) => ({ ...r })))
  },

  async create(table, payload, uniqueKey) {
    await sleep()
    const db = loadDb()
    if (uniqueKey && db[table].some((r) => String(r[uniqueKey]) === String(payload[uniqueKey]))) {
      return fail(409, `${uniqueKey === 'username' ? '用户名' : (uniqueKey === 'code' ? '编码' : uniqueKey)}已存在`, [
        { field: uniqueKey, message: '唯一性冲突，禁止重复' }
      ])
    }
    const row = { ...payload, id: nextId(db[table]), status: payload.status === undefined ? 1 : payload.status, createdAt: now() }
    db[table].push(row)
    saveDb(db)
    return ok(row)
  },

  async update(table, id, payload) {
    await sleep()
    const db = loadDb()
    const idx = db[table].findIndex((r) => r.id === Number(id))
    if (idx < 0) return fail(404, '记录不存在')
    db[table][idx] = { ...db[table][idx], ...payload, id: Number(id) }
    saveDb(db)
    return ok(db[table][idx])
  },

  async toggleStatus(table, id, status) {
    await sleep()
    const db = loadDb()
    const idx = db[table].findIndex((r) => r.id === Number(id))
    if (idx < 0) return fail(404, '记录不存在')
    db[table][idx].status = status
    saveDb(db)
    return ok(db[table][idx])
  },

  async resetPassword(userId) {
    await sleep()
    const db = loadDb()
    const user = db.users.find((u) => u.id === Number(userId))
    if (!user) return fail(404, '用户不存在')
    db.passwords[user.username] = 'Admin@123'
    saveDb(db)
    return ok({ message: '密码已重置为 Admin@123（Mock 模式）' })
  },

  async saveRoleMenus(roleCode, menuIds) {
    await sleep()
    const db = loadDb()
    const role = db.roles.find((r) => r.roleCode === roleCode)
    if (!role) return fail(404, '角色不存在')
    role.menuIds = menuIds
    saveDb(db)
    return ok({ message: '角色权限已保存' })
  },

  async getRoleMenus(roleCode) {
    await sleep()
    const db = loadDb()
    const role = db.roles.find((r) => r.roleCode === roleCode)
    if (!role) return fail(404, '角色不存在')
    return ok(role.menuIds || [])
  }
}
