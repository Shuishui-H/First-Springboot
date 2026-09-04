<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { api } from '../api/index.js'

const rows = ref([])
const total = ref(0)
const page = ref(1)
const size = ref(10)
const loading = ref(false)
const error = ref('')
const notice = ref('')

const filters = reactive({ keyword: '', roleCode: '', status: '' })
const roleOptions = ref([])
const current = ref(null)
const permissions = ref([])
const can = (perm) => permissions.value.includes(perm)

const showForm = ref(false)
const editingId = ref(null)
const form = ref({})
const submitting = ref(false)
const formError = ref('')

function emptyForm() {
  return { username: '', password: '', realName: '', roleCode: '' }
}

function showNotice(message) {
  notice.value = message
  window.setTimeout(() => {
    if (notice.value === message) notice.value = ''
  }, 2800)
}

async function load() {
  loading.value = true
  error.value = ''
  try {
    const res = await api.list('users', {
      page: page.value,
      size: size.value,
      filters: {
        keyword: filters.keyword,
        roleCode: filters.roleCode,
        status: filters.status === '' ? '' : Number(filters.status)
      }
    })
    rows.value = res.records
    total.value = res.total
  } catch (exception) {
    error.value = exception.message || '加载失败'
  } finally {
    loading.value = false
  }
}

async function loadRoles(enabledOnly = false) {
  try {
    const res = await api.list('roles', { page: 1, size: 100, filters: {} })
    roleOptions.value = enabledOnly ? res.records.filter((r) => r.status === 1) : res.records
  } catch {
    roleOptions.value = []
  }
}

function roleNameOf(code) {
  const hit = roleOptions.value.find((r) => r.roleCode === code)
  return hit ? hit.roleName : code
}

async function loadMe() {
  try {
    current.value = await api.me()
    permissions.value = current.value.permissions || []
  } catch {
    // 登录守卫会处理
  }
}

function resetFilters() {
  filters.keyword = ''
  filters.roleCode = ''
  filters.status = ''
  page.value = 1
  load()
}

function filterChanged() {
  page.value = 1
  load()
}

function changePage(target) {
  if (target < 1) return
  page.value = target
  load()
}

const totalPages = computed(() => Math.max(1, Math.ceil(total.value / size.value)))

function openCreate() {
  editingId.value = null
  form.value = emptyForm()
  formError.value = ''
  showForm.value = true
}

async function openEdit(row) {
  editingId.value = row.id
  form.value = { username: row.username, realName: row.realName, roleCode: row.roleCode }
  formError.value = ''
  showForm.value = true
}

async function save() {
  formError.value = ''
  if (!form.value.username || !form.value.roleCode || (!editingId.value && !form.value.password)) {
    formError.value = '请填写必填项：用户名、角色、初始密码'
    return
  }
  submitting.value = true
  try {
    if (editingId.value !== null) {
      await api.update('users', editingId.value, {
        realName: form.value.realName,
        roleCode: form.value.roleCode
      })
    } else {
      await api.create('users', {
        username: form.value.username.trim(),
        password: form.value.password,
        realName: form.value.realName,
        roleCode: form.value.roleCode,
        status: 1
      })
    }
    showForm.value = false
    await load()
    showNotice(editingId.value !== null ? '用户信息已更新' : '用户创建成功')
  } catch (exception) {
    formError.value = exception.message || '保存失败'
  } finally {
    submitting.value = false
  }
}

async function toggleStatus(row) {
  error.value = ''
  try {
    const next = row.status === 1 ? 0 : 1
    await api.toggleStatus('users', row.id, next)
    await load()
    showNotice(`${row.username} 已${next === 1 ? '启用' : '停用'}`)
  } catch (exception) {
    error.value = exception.message || '状态更新失败'
  }
}

async function resetPassword(row) {
  if (!window.confirm(`确定将用户「${row.realName || row.username}」的密码重置为初始密码 Admin@123 吗？`)) return
  error.value = ''
  try {
    const res = await api.resetPassword(row.id)
    showNotice(res.message || '密码已重置')
  } catch (exception) {
    error.value = exception.message || '重置失败'
  }
}

onMounted(() => {
  load()
  loadRoles()
  loadMe()
})
</script>

<template>
  <div class="page-content">
    <section class="page-heading">
      <div>
        <p class="eyebrow">ACCESS · USER MANAGEMENT</p>
        <h1>用户管理</h1>
        <p>管理员可创建、启停、重置密码用户；用户名全局唯一，创建后不可修改。</p>
      </div>
      <div class="heading-actions">
        <button class="secondary-button" @click="load"><span>↻</span> 刷新数据</button>
        <button class="primary-button" @click="openCreate"><span>＋</span> 新增用户</button>
      </div>
    </section>

    <section class="metrics">
      <article class="metric-card blue"><div class="metric-icon">员</div><div><span>用户总数</span><strong>{{ total }}</strong><small>当前账号体系</small></div><b>SET-02</b></article>
      <article class="metric-card green"><div class="metric-icon">启</div><div><span>已启用</span><strong>{{ rows.filter((r) => r.status === 1).length }}</strong><small>本页已启用数</small></div><b>正常</b></article>
      <article class="metric-card orange"><div class="metric-icon">停</div><div><span>已停用</span><strong>{{ rows.filter((r) => r.status === 0).length }}</strong><small>停用后不可登录</small></div><b>冻结</b></article>
      <article class="metric-card violet"><div class="metric-icon">角</div><div><span>角色数量</span><strong>{{ roleOptions.length }}</strong><small>预置 7 类角色</small></div><b>RBAC</b></article>
    </section>

    <section class="content-card">
      <div class="list-heading">
        <div><h2>用户列表</h2><p>密码使用强哈希存储，任何接口均不返回密码字段。</p></div>
        <button class="refresh-button" :disabled="loading" @click="load">↻ <span>{{ loading ? '刷新中' : '刷新数据' }}</span></button>
      </div>

      <div class="filter-bar filter-3">
        <label class="search-box"><span>⌕</span><input v-model="filters.keyword" placeholder="搜索用户名 / 姓名" @input="filterChanged" /></label>
        <select v-model="filters.roleCode" @change="filterChanged"><option value="">全部角色</option><option v-for="r in roleOptions" :key="r.roleCode" :value="r.roleCode">{{ r.roleName }}</option></select>
        <select v-model="filters.status" @change="filterChanged"><option value="">全部状态</option><option value="1">启用</option><option value="0">停用</option></select>
        <button class="text-button" @click="resetFilters">重置</button>
      </div>

      <div v-if="error" class="message error-message"><span>!</span><p>{{ error }}</p><button @click="error = ''">×</button></div>

      <div class="table-wrap">
        <table>
          <thead><tr><th>用户</th><th>账号</th><th>角色</th><th>创建时间</th><th>状态</th><th class="action-column">操作</th></tr></thead>
          <tbody>
            <tr v-if="loading"><td colspan="6" class="empty-state"><span class="spinner"></span><strong>正在读取用户数据</strong><small>请稍候…</small></td></tr>
            <tr v-else-if="rows.length === 0"><td colspan="6" class="empty-state"><span class="empty-icon">⌕</span><strong>没有找到匹配的用户</strong><small>请调整筛选条件或新增用户</small></td></tr>
            <tr v-for="row in rows" v-else :key="row.id">
              <td><div class="cell-main"><strong>{{ row.realName || '—' }}</strong><code>ID {{ row.id }}</code></div></td>
              <td><span class="code-chip">{{ row.username }}</span></td>
              <td>
                <span v-if="row.roleCode === 'admin'" class="module-badge" style="color:#315fd6;border-color:#d6e0f5;background:#f3f7ff;">{{ roleNameOf(row.roleCode) }}</span>
                <span v-else class="cell-muted-inline">{{ roleNameOf(row.roleCode) }}</span>
              </td>
              <td>{{ row.createdAt || '—' }}</td>
              <td><button :class="['status-switch', { off: row.status !== 1 }]" @click="toggleStatus(row)"><i></i>{{ row.status === 1 ? '启用' : '停用' }}</button></td>
              <td class="row-actions">
                <button @click="openEdit(row)">编辑</button>
                <button @click="toggleStatus(row)">{{ row.status === 1 ? '停用' : '启用' }}</button>
                <button v-if="can('system:user:password')" class="restock-button" @click="resetPassword(row)">重置密码</button>
              </td>
            </tr>
          </tbody>
        </table>
      </div>

      <div class="pagination">
        <span class="page-info">共 {{ total }} 条记录</span>
        <button :disabled="page <= 1" @click="changePage(page - 1)">‹</button>
        <button v-for="p in totalPages" :key="p" :class="{ active: p === page }" @click="changePage(p)">{{ p }}</button>
        <button :disabled="page >= totalPages" @click="changePage(page + 1)">›</button>
      </div>
    </section>

    <div v-if="showForm" class="modal-backdrop" @click.self="showForm = false">
      <form class="modal" @submit.prevent="save">
        <div class="modal-title">
          <div>
            <p class="eyebrow">ACCESS · USER</p>
            <h2>{{ editingId !== null ? '编辑用户' : '新增用户' }}</h2>
            <p>{{ editingId !== null ? '可修改姓名与角色，用户名创建后不可修改。' : '创建用户并分配角色，初始密码可自定义。' }}</p>
          </div>
          <button type="button" class="icon-button" @click="showForm = false">×</button>
        </div>
        <div class="form-grid">
          <label>用户名 <em>*</em><input v-model.trim="form.username" :disabled="editingId !== null" required maxlength="50" placeholder="登录账号，全局唯一" /></label>
          <label v-if="editingId === null">初始密码 <em>*</em><input v-model="form.password" required type="password" maxlength="50" placeholder="至少 6 位" /></label>
          <label>真实姓名<input v-model.trim="form.realName" maxlength="50" placeholder="可选" /></label>
          <label>角色 <em>*</em><select v-model="form.roleCode" required><option value="" disabled>请选择角色</option><option v-for="r in roleOptions" :key="r.roleCode" :value="r.roleCode">{{ r.roleName }}（{{ r.roleCode }}）</option></select></label>
        </div>
        <div v-if="formError" class="message error-message compact"><span>!</span><p>{{ formError }}</p></div>
        <div class="modal-actions">
          <button type="button" class="secondary-button" @click="showForm = false">取消</button>
          <button type="submit" class="primary-button" :disabled="submitting">{{ submitting ? '保存中…' : '保存' }}</button>
        </div>
      </form>
    </div>

    <transition name="toast"><div v-if="notice" class="toast-message"><span>✓</span>{{ notice }}</div></transition>
  </div>
</template>

<style scoped>
.cell-muted-inline { color: #5f6e87; }
</style>
