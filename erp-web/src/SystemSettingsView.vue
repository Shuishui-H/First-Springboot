<script setup>
import { computed, onMounted, reactive, ref } from 'vue'

const props = defineProps({ currentUser: { type: Object, default: null } })

const LEGACY_STORAGE_KEY = 'nova_erp_system_settings_v1'
const settingView = ref('home')
const loading = ref(false)
const error = ref('')
const notice = ref('')
const showForm = ref(false)
const showPermission = ref(false)
const editingId = ref(null)
const permissionRole = ref(null)
const permissionIds = ref([])
const formError = ref('')

const menus = [
  { id: 100, name: '系统设置', type: 'menu' },
  { id: 101, name: '用户管理', type: 'menu', parentId: 100 },
  { id: 1011, name: '新增用户', type: 'button', parentId: 101, perm: 'system:user:add' },
  { id: 1012, name: '编辑用户', type: 'button', parentId: 101, perm: 'system:user:edit' },
  { id: 1013, name: '启停用户', type: 'button', parentId: 101, perm: 'system:user:status' },
  { id: 1014, name: '重置密码', type: 'button', parentId: 101, perm: 'system:user:password' },
  { id: 102, name: '角色权限', type: 'menu', parentId: 100 },
  { id: 1021, name: '配置角色权限', type: 'button', parentId: 102, perm: 'system:role:config' },
  { id: 1022, name: '启停角色', type: 'button', parentId: 102, perm: 'system:role:status' },
  { id: 103, name: '仓库管理', type: 'menu', parentId: 100 },
  { id: 104, name: '供应商管理', type: 'menu', parentId: 100 },
  { id: 105, name: '客户管理', type: 'menu', parentId: 100 }
]

const seed = () => ({
  users: [
    { id: 1, username: 'admin', realName: '系统管理员', roleCode: 'admin', status: 1, createdAt: '2026-09-01 09:00:00' },
    { id: 2, username: 'purchaser01', realName: '采购员小采', roleCode: 'purchaser', status: 1, createdAt: '2026-09-01 09:05:00' },
    { id: 3, username: 'seller01', realName: '销售员小销', roleCode: 'seller', status: 1, createdAt: '2026-09-01 09:10:00' },
    { id: 4, username: 'whkeeper01', realName: '仓管员小仓', roleCode: 'warehouse', status: 1, createdAt: '2026-09-01 09:15:00' }
  ],
  roles: [
    { id: 1, roleCode: 'admin', roleName: '管理员', status: 1, remark: '系统内置，拥有全部权限', menuIds: menus.map((item) => item.id) },
    { id: 2, roleCode: 'purchaser', roleName: '采购员', status: 1, remark: '供应商查看、采购订单创建/提交', menuIds: [100, 104] },
    { id: 3, roleCode: 'seller', roleName: '销售员', status: 1, remark: '客户查看、销售订单创建/提交', menuIds: [100, 105] },
    { id: 4, roleCode: 'warehouse', roleName: '仓库管理员', status: 1, remark: '出入库、库存查询', menuIds: [100, 103] },
    { id: 5, roleCode: 'manager', roleName: '经营管理者', status: 1, remark: '报表只读', menuIds: [100] }
  ],
  warehouses: [
    { id: 1, code: 'WH001', name: '主仓', manager: '李仓管', address: '高新区一号库房', status: 1, remark: '系统预置主仓' },
    { id: 2, code: 'WH002', name: '东区仓', manager: '王小东', address: '工业园东区 3 号', status: 1, remark: '' }
  ],
  suppliers: [
    { id: 1, code: 'SUP001', name: '晨光办公用品有限公司', contact: '张晨', phone: '13800001111', status: 1, remark: '' },
    { id: 2, code: 'SUP002', name: '华为授权经销商', contact: '刘华', phone: '13800002222', status: 1, remark: '电子产品' }
  ],
  customers: [
    { id: 1, code: 'CUS001', name: '云帆科技公司', contact: '陈帆', phone: '13900001111', status: 1, remark: '重点客户' },
    { id: 2, code: 'CUS002', name: '蓝海商贸中心', contact: '陈海', phone: '13900002222', status: 1, remark: '' }
  ]
})

const db = ref(seed())
const currentUser = computed(() => props.currentUser || db.value.users.find((user) => user.username === 'admin') || db.value.users[0])
const currentRole = computed(() => db.value.roles.find((role) => role.roleCode === currentUser.value?.roleCode))
const roleOptions = computed(() => db.value.roles.filter((role) => role.status === 1))
const activeUserCount = computed(() => db.value.users.filter((user) => user.status === 1).length)
const activeRoleCount = computed(() => db.value.roles.filter((role) => role.status === 1).length)

const modules = [
  { key: 'users', no: 'SET-02', title: '用户管理', icon: '◈', perm: 101 },
  { key: 'roles', no: 'SET-03', title: '角色权限', icon: '⌑', perm: 102 },
  { key: 'warehouses', no: 'SET-04', title: '仓库管理', icon: '▤', perm: 103 },
  { key: 'suppliers', no: 'SET-05', title: '供应商管理', icon: '◉', perm: 104 },
  { key: 'customers', no: 'SET-06', title: '客户管理', icon: '◎', perm: 105 }
]
const visibleModules = computed(() => modules.filter((module) => currentRole.value?.roleCode === 'admin' || currentRole.value?.menuIds?.includes(module.perm)))

const masterConfig = {
  warehouses: { title: '仓库管理', eyebrow: 'MASTER DATA · WAREHOUSE', desc: '维护仓库主数据，供采购入库、销售出库和库存查询使用。', codeLabel: '仓库编码', nameLabel: '仓库名称', fields: [['code', '仓库编码', true], ['name', '仓库名称', true], ['manager', '负责人', false], ['address', '地址', false], ['remark', '备注', false]] },
  suppliers: { title: '供应商管理', eyebrow: 'MASTER DATA · SUPPLIER', desc: '维护供应商主数据，供采购订单选择使用。', codeLabel: '供应商编码', nameLabel: '供应商名称', fields: [['code', '供应商编码', true], ['name', '供应商名称', true], ['contact', '联系人', false], ['phone', '电话', false], ['remark', '备注', false]] },
  customers: { title: '客户管理', eyebrow: 'MASTER DATA · CUSTOMER', desc: '维护客户主数据，供销售订单选择使用。', codeLabel: '客户编码', nameLabel: '客户名称', fields: [['code', '客户编码', true], ['name', '客户名称', true], ['contact', '联系人', false], ['phone', '电话', false], ['remark', '备注', false]] }
}
const masterFilters = reactive({ code: '', name: '', status: '' })
const userFilters = reactive({ keyword: '', roleCode: '', status: '' })
const form = ref({})

const masterCurrent = computed(() => masterConfig[settingView.value])
const masterRows = computed(() => {
  if (!masterCurrent.value) return []
  return db.value[settingView.value].filter((row) => (!masterFilters.code || row.code.toLowerCase().includes(masterFilters.code.toLowerCase())) && (!masterFilters.name || row.name.toLowerCase().includes(masterFilters.name.toLowerCase())) && (masterFilters.status === '' || String(row.status) === masterFilters.status))
})
const userRows = computed(() => db.value.users.filter((row) => (!userFilters.keyword || `${row.username}${row.realName}`.toLowerCase().includes(userFilters.keyword.toLowerCase())) && (!userFilters.roleCode || row.roleCode === userFilters.roleCode) && (userFilters.status === '' || String(row.status) === userFilters.status)))

async function requestJson(url, options) {
  const response = await fetch(url, options)
  if (!response.ok) {
    const body = await response.json().catch(() => ({}))
    throw new Error(body.detail || body.message || `请求失败（HTTP ${response.status}）`)
  }
  return response.status === 204 ? null : response.json()
}
async function persist() { /* V3 使用关系表；保留空函数兼容旧演示数据结构。 */ }
async function reloadRemoteSettings() {
  const [users, roles, permissions, warehouses, suppliers, customers] = await Promise.all([
    requestJson('/api/system/users'), requestJson('/api/system/roles'), requestJson('/api/system/permissions'),
    requestJson('/api/warehouses'), requestJson('/api/suppliers'), requestJson('/api/customers')
  ])
  const permissionMap = new Map(permissions.map((item) => [item.id, item]))
  db.value = {
    users: users.map((item) => ({ ...item, status: Number(item.status), createdAt: item.createdAt?.replace('T', ' ') })),
    roles: roles.map((item) => ({ ...item, roleCode: item.roleCode, status: Number(item.status), menuIds: item.permissionIds || [] })),
    warehouses: warehouses.map((item) => ({ ...item, status: item.status === '启用' ? 1 : 0 })),
    suppliers: suppliers.map((item) => ({ ...item, status: item.status === '启用' ? 1 : 0 })),
    customers: customers.map((item) => ({ ...item, status: item.status === '启用' ? 1 : 0 }))
  }
  // Keep the permission catalogue available to the existing tree rendering.
  menus.splice(0, menus.length, ...permissionMap.values().map((item) => ({ id: item.id, name: item.name, type: item.permissionType === 'MENU' ? 'menu' : 'button', parentId: item.parentId, perm: item.permissionCode })))
}
function showNotice(message) { notice.value = message; window.setTimeout(() => { if (notice.value === message) notice.value = '' }, 2600) }
function selectView(view) { settingView.value = view; error.value = ''; showForm.value = false }
function backHome() { selectView('home') }
function resetMasterFilters() { masterFilters.code = ''; masterFilters.name = ''; masterFilters.status = '' }
function resetUserFilters() { userFilters.keyword = ''; userFilters.roleCode = ''; userFilters.status = '' }
function newMasterForm() { const value = {}; masterCurrent.value.fields.forEach(([key]) => { value[key] = '' }); value.status = 1; return value }
function openMasterCreate() { editingId.value = null; form.value = newMasterForm(); formError.value = ''; showForm.value = true }
function openMasterEdit(row) { editingId.value = row.id; form.value = { ...row }; formError.value = ''; showForm.value = true }
async function saveMaster() {
  formError.value = ''
  const config = masterCurrent.value
  const required = config.fields.find(([key, , isRequired]) => isRequired && !String(form.value[key] ?? '').trim())
  if (required) { formError.value = `请填写必填项：${required[1]}`; return }
  const endpoints = { warehouses: '/api/warehouses', suppliers: '/api/suppliers', customers: '/api/customers' }
  const payload = settingView.value === 'warehouses'
    ? { code: form.value.code, name: form.value.name, manager: form.value.manager, status: Number(form.value.status) === 1 ? '启用' : '停用' }
    : { code: form.value.code, name: form.value.name, contact: form.value.contact, phone: form.value.phone, status: Number(form.value.status) === 1 ? '启用' : '停用' }
  try {
    await requestJson(editingId.value === null ? endpoints[settingView.value] : `${endpoints[settingView.value]}/${editingId.value}`, {
      method: editingId.value === null ? 'POST' : 'PUT', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(payload)
    })
    await reloadRemoteSettings(); showForm.value = false; showNotice(editingId.value === null ? '创建成功' : '信息已更新')
  } catch (exception) { formError.value = exception.message }
}
async function toggleMaster(row) {
  const nextStatus = row.status === 1 ? '停用' : '启用'
  const endpoints = { warehouses: '/api/warehouses', suppliers: '/api/suppliers', customers: '/api/customers' }
  try { await requestJson(`${endpoints[settingView.value]}/${row.id}`, { method: 'PUT', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ ...row, status: nextStatus }) }); await reloadRemoteSettings(); showNotice(`${row.name} 已${nextStatus}`) }
  catch (exception) { error.value = exception.message }
}
function emptyUserForm() { return { username: '', password: '', realName: '', roleCode: 'purchaser' } }
function openUserCreate() { editingId.value = null; form.value = emptyUserForm(); formError.value = ''; showForm.value = true }
function openUserEdit(row) { editingId.value = row.id; form.value = { ...row }; formError.value = ''; showForm.value = true }
async function saveUser() {
  formError.value = ''
  if (!form.value.username || !form.value.roleCode || (editingId.value === null && !form.value.password)) { formError.value = '请填写用户名、角色和初始密码'; return }
  try {
    await requestJson(editingId.value === null ? '/api/system/users' : `/api/system/users/${editingId.value}`, { method: editingId.value === null ? 'POST' : 'PUT', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(form.value) })
    await reloadRemoteSettings(); showForm.value = false; showNotice(editingId.value === null ? '用户创建成功' : '用户信息已更新')
  } catch (exception) { formError.value = exception.message }
}
async function toggleUser(row) {
  const nextStatus = row.status === 1 ? 0 : 1
  try { await requestJson(`/api/system/users/${row.id}/status`, { method: 'PUT', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ status: nextStatus }) }); await reloadRemoteSettings(); showNotice(`${row.username} 已${nextStatus ? '启用' : '停用'}`) }
  catch (exception) { error.value = exception.message }
}
async function resetPassword(row) {
  if (!window.confirm(`确定重置用户“${row.realName || row.username}”的密码吗？`)) return
  const password = window.prompt('请输入新的临时密码（至少 8 位）')
  if (!password) return
  try { await requestJson(`/api/system/users/${row.id}/reset-password`, { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ password }) }); showNotice('密码已重置；用户下次登录必须修改密码') }
  catch (exception) { error.value = exception.message }
}
async function toggleRole(role) {
  if (role.roleCode === 'admin') { showNotice('管理员角色不能停用'); return }
  const nextStatus = role.status === 1 ? 0 : 1
  try { await requestJson(`/api/system/roles/${role.id}/status`, { method: 'PUT', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ status: nextStatus }) }); await reloadRemoteSettings(); showNotice(`${role.roleName} 已${nextStatus ? '启用' : '停用'}`) }
  catch (exception) { error.value = exception.message }
}
function openPermission(row) { permissionRole.value = row; permissionIds.value = [...(row.menuIds || [])]; showPermission.value = true }
function togglePermission(id) { permissionIds.value = permissionIds.value.includes(id) ? permissionIds.value.filter((item) => item !== id) : [...permissionIds.value, id] }
async function savePermission() {
  try { await requestJson(`/api/system/roles/${permissionRole.value.id}/permissions`, { method: 'PUT', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ permissionIds: permissionIds.value }) }); await reloadRemoteSettings(); showPermission.value = false; showNotice(`已保存“${permissionRole.value.roleName}”的权限配置`) }
  catch (exception) { error.value = exception.message }
}
function roleName(code) { return db.value.roles.find((row) => row.roleCode === code)?.roleName || code }
function formatStatus(status) { return status === 1 ? '启用' : '停用' }

onMounted(async () => {
  loading.value = true
  try {
    await reloadRemoteSettings()
  } catch (exception) { error.value = `系统设置数据库读取失败：${exception.message}` }
  finally { loading.value = false }
})
</script>

<template>
  <div class="settings-workspace">
      <section v-if="settingView === 'home'" class="page-content">
      <section class="page-heading"><div><p class="eyebrow">SYSTEM · SETTINGS</p><h1>系统设置</h1><p>统一管理用户、角色权限与仓库、供应商、客户等基础主数据。</p></div><div class="home-user"><span class="home-user-avatar">{{ currentUser?.realName?.slice(0, 1) || '管' }}</span><div><strong>{{ currentRole?.roleName || '系统管理员' }} · {{ currentUser?.realName || '系统管理员' }}</strong><small>设置数据来自数据库关系表</small></div></div></section>
      <section class="metrics"><article class="metric-card blue"><div class="metric-icon">员</div><div><span>用户总数</span><strong>{{ db.users.length }}</strong><small>{{ activeUserCount }} 个已启用账号</small></div><b>SET-02</b></article><article class="metric-card violet"><div class="metric-icon">角</div><div><span>角色数量</span><strong>{{ db.roles.length }}</strong><small>{{ activeRoleCount }} 个可分配角色</small></div><b>RBAC</b></article><article class="metric-card green"><div class="metric-icon">基</div><div><span>基础主数据</span><strong>{{ db.warehouses.length + db.suppliers.length + db.customers.length }}</strong><small>仓库 / 供应商 / 客户</small></div><b>SET-04~06</b></article><article class="metric-card orange"><div class="metric-icon">权</div><div><span>权限点</span><strong>{{ menus.filter((item) => item.type === 'button').length }}</strong><small>按钮级权限标识</small></div><b>perms</b></article></section>
      <section class="content-card settings-home"><div class="list-heading"><div><h2>功能板块</h2><p>点击进入对应设置页面，保存后立即写入对应关系表。</p></div><span class="module-badge">SET-01 ~ SET-06</span></div><div class="settings-modules"><div class="settings-module"><span class="module-icon">⌂</span><div class="module-main"><strong>登录与当前用户</strong><small>{{ currentRole?.roleName || '管理员' }} · {{ currentUser?.realName || '系统管理员' }}</small></div><span class="module-exit">已登录</span></div><button v-for="module in visibleModules" :key="module.key" class="settings-module settings-module-button" @click="selectView(module.key)"><span class="module-icon">{{ module.icon }}</span><div class="module-main"><strong>{{ module.title }}</strong><small>{{ module.key === 'users' ? '创建、启停和重置账号密码' : module.key === 'roles' ? '配置角色可见菜单与按钮权限' : '维护业务单据使用的基础资料' }}</small></div><span class="module-enter">进入 →</span></button></div></section>
    </section>

      <section v-else-if="settingView === 'users'" class="page-content">
      <section class="page-heading"><div><p class="eyebrow">ACCESS · USER MANAGEMENT</p><h1>用户管理</h1><p>管理员可创建、启停和重置用户；用户名全局唯一，创建后不可修改。</p></div><div class="heading-actions"><button class="secondary-button" @click="showNotice('用户数据已是最新')">↻ 刷新数据</button><button class="primary-button" @click="openUserCreate">＋ 新增用户</button></div></section>
      <section class="content-card"><div class="list-heading"><div><h2>用户列表</h2><p>密码仅以安全哈希保存；重置后用户下次登录必须修改密码。</p></div><button class="refresh-button" @click="backHome">← 返回系统设置</button></div><div class="filter-bar filter-3"><label class="search-box"><span>⌕</span><input v-model="userFilters.keyword" placeholder="搜索用户名 / 姓名" /></label><select v-model="userFilters.roleCode"><option value="">全部角色</option><option v-for="role in db.roles" :key="role.roleCode" :value="role.roleCode">{{ role.roleName }}</option></select><select v-model="userFilters.status"><option value="">全部状态</option><option value="1">启用</option><option value="0">停用</option></select><button class="text-button" @click="resetUserFilters">重置</button></div><div class="table-wrap"><table><thead><tr><th>用户</th><th>账号</th><th>角色</th><th>创建时间</th><th>状态</th><th class="action-column">操作</th></tr></thead><tbody><tr v-if="!userRows.length"><td colspan="6" class="empty-state"><strong>没有找到匹配的用户</strong></td></tr><tr v-for="row in userRows" :key="row.id"><td><div class="cell-main"><strong>{{ row.realName || '—' }}</strong><code>ID {{ row.id }}</code></div></td><td><span class="code-chip">{{ row.username }}</span></td><td>{{ roleName(row.roleCode) }}</td><td>{{ row.createdAt || '—' }}</td><td><button :class="['status-switch', { off: row.status !== 1 }]" @click="toggleUser(row)"><i></i>{{ formatStatus(row.status) }}</button></td><td class="row-actions"><button @click="openUserEdit(row)">编辑</button><button @click="toggleUser(row)">{{ row.status === 1 ? '停用' : '启用' }}</button><button class="restock-button" @click="resetPassword(row)">重置密码</button></td></tr></tbody></table></div><footer class="list-footer"><span>显示 {{ userRows.length }} / {{ db.users.length }} 条记录</span><span>SET-02 · 关系化用户表</span></footer></section>
    </section>

    <section v-else-if="settingView === 'roles'" class="page-content"><section class="page-heading"><div><p class="eyebrow">ACCESS · ROLE & PERMISSION</p><h1>角色权限</h1><p>按角色配置菜单与按钮权限；停用角色后，对应用户的权限立即失效。</p></div><button class="secondary-button" @click="backHome">← 返回系统设置</button></section><section class="content-card"><div class="list-heading"><div><h2>角色列表</h2><p>管理员角色不可停用，其他角色可按需冻结。</p></div><span class="module-badge">RBAC</span></div><div class="table-wrap"><table><thead><tr><th>角色编码</th><th>角色名称</th><th>状态</th><th>备注</th><th class="action-column">操作</th></tr></thead><tbody><tr v-for="role in db.roles" :key="role.id"><td><span class="code-chip">{{ role.roleCode }}</span></td><td><strong>{{ role.roleName }}</strong></td><td><button :class="['status-switch', { off: role.status !== 1 }]" @click="toggleRole(role)"><i></i>{{ formatStatus(role.status) }}</button></td><td>{{ role.remark || '—' }}</td><td class="row-actions"><button class="restock-button" @click="openPermission(role)">配置权限</button></td></tr></tbody></table></div><footer class="list-footer"><span>显示 {{ db.roles.length }} 条角色记录</span><span>前端菜单控制 + 后端鉴权是生产环境最终边界</span></footer></section></section>

    <section v-else class="page-content"><section class="page-heading"><div><p class="eyebrow">{{ masterCurrent.eyebrow }}</p><h1>{{ masterCurrent.title }}</h1><p>{{ masterCurrent.desc }}</p></div><div class="heading-actions"><button class="secondary-button" @click="backHome">← 返回系统设置</button><button class="primary-button" @click="openMasterCreate">＋ 新增{{ masterCurrent.nameLabel }}</button></div></section><section class="content-card"><div class="list-heading"><div><h2>{{ masterCurrent.title }}</h2><p>停用的资料不应在新的采购、销售或仓储单据中继续选择。</p></div><span class="module-badge">{{ settingView === 'warehouses' ? 'SET-04' : settingView === 'suppliers' ? 'SET-05' : 'SET-06' }}</span></div><div class="filter-bar filter-3"><label class="search-box"><span>⌕</span><input v-model="masterFilters.code" :placeholder="`搜索${masterCurrent.codeLabel}`" /></label><label class="search-box"><span>⌕</span><input v-model="masterFilters.name" :placeholder="`搜索${masterCurrent.nameLabel}`" /></label><select v-model="masterFilters.status"><option value="">全部状态</option><option value="1">启用</option><option value="0">停用</option></select><button class="text-button" @click="resetMasterFilters">重置</button></div><div class="table-wrap"><table><thead><tr><th>编码</th><th>名称</th><th>联系人/负责人</th><th>电话/地址</th><th>备注</th><th>状态</th><th class="action-column">操作</th></tr></thead><tbody><tr v-if="!masterRows.length"><td colspan="7" class="empty-state"><strong>没有找到匹配的记录</strong></td></tr><tr v-for="row in masterRows" :key="row.id"><td><span class="code-chip">{{ row.code }}</span></td><td><strong>{{ row.name }}</strong></td><td>{{ row.manager || row.contact || '—' }}</td><td>{{ row.phone || row.address || '—' }}</td><td>{{ row.remark || '—' }}</td><td><button :class="['status-switch', { off: row.status !== 1 }]" @click="toggleMaster(row)"><i></i>{{ formatStatus(row.status) }}</button></td><td class="row-actions"><button @click="openMasterEdit(row)">编辑</button><button @click="toggleMaster(row)">{{ row.status === 1 ? '停用' : '启用' }}</button></td></tr></tbody></table></div><footer class="list-footer"><span>显示 {{ masterRows.length }} / {{ db[settingView].length }} 条记录</span><span>设置数据保存在当前浏览器</span></footer></section></section>

    <div v-if="showForm" class="modal-backdrop" @click.self="showForm = false"><form class="modal" @submit.prevent="settingView === 'users' ? saveUser() : saveMaster()"><div class="modal-title"><div><p class="eyebrow">SYSTEM SETTINGS</p><h2>{{ settingView === 'users' ? (editingId === null ? '新增用户' : '编辑用户') : (editingId === null ? `新增${masterCurrent.nameLabel}` : `编辑${masterCurrent.nameLabel}`) }}</h2><p>请填写必填信息后保存。</p></div><button type="button" class="icon-button" @click="showForm = false">×</button></div><div v-if="settingView === 'users'" class="form-grid"><label>用户名 <em>*</em><input v-model.trim="form.username" :disabled="editingId !== null" required /></label><label v-if="editingId === null">初始密码 <em>*</em><input v-model="form.password" type="password" required /></label><label>真实姓名<input v-model.trim="form.realName" /></label><label>角色 <em>*</em><select v-model="form.roleCode" required><option v-for="role in roleOptions" :key="role.roleCode" :value="role.roleCode">{{ role.roleName }}</option></select></label></div><div v-else class="form-grid"><label v-for="[key, label, required] in masterCurrent.fields" :key="key" :class="{ full: key === 'remark' || key === 'address' }">{{ label }} <em v-if="required">*</em><textarea v-if="key === 'remark' || key === 'address'" v-model.trim="form[key]" rows="2"></textarea><input v-else v-model.trim="form[key]" :disabled="editingId !== null && key === 'code'" :required="required" /></label><label>状态<select v-model.number="form.status"><option :value="1">启用</option><option :value="0">停用</option></select></label></div><div v-if="formError" class="message error-message compact"><span>!</span><p>{{ formError }}</p></div><div class="modal-actions"><button type="button" class="secondary-button" @click="showForm = false">取消</button><button type="submit" class="primary-button">保存</button></div></form></div>
    <div v-if="showPermission" class="modal-backdrop" @click.self="showPermission = false"><div class="modal permission-modal"><div class="modal-title"><div><p class="eyebrow">ROLE PERMISSION</p><h2>配置权限 · {{ permissionRole?.roleName }}</h2><p>勾选该角色可见的菜单与按钮权限点。</p></div><button class="icon-button" @click="showPermission = false">×</button></div><div class="permission-tree"><label v-for="menu in menus" :key="menu.id" :class="['permission-row', `permission-${menu.type}`]"><input type="checkbox" :checked="permissionIds.includes(menu.id)" @change="togglePermission(menu.id)" />{{ menu.name }}<code v-if="menu.perm">{{ menu.perm }}</code></label></div><div class="modal-actions"><button class="secondary-button" @click="showPermission = false">取消</button><button class="primary-button" @click="savePermission">保存配置</button></div></div></div>
    <transition name="toast"><div v-if="notice" class="toast-message"><span>✓</span>{{ notice }}</div></transition>
  </div>
</template>
