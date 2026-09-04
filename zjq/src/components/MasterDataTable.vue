<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { api } from '../api/index.js'

// module: 'warehouses' | 'suppliers' | 'customers'
const props = defineProps({
  module: { type: String, required: true }
})

const MODULE_CONFIG = {
  warehouses: {
    title: '仓库管理',
    eyebrow: 'MASTER DATA · WAREHOUSE',
    desc: '维护仓库主数据，供采购入库、销售出库和库存查询使用。',
    table: 'warehouses',
    codeLabel: '仓库编码',
    nameLabel: '仓库名称',
    columns: [
      { key: 'code', label: '编码', type: 'name' },
      { key: 'name', label: '名称', type: 'text' },
      { key: 'manager', label: '负责人', type: 'text' },
      { key: 'address', label: '地址', type: 'text' },
      { key: 'status', label: '状态', type: 'status' }
    ],
    formFields: [
      { key: 'code', label: '仓库编码', required: true, hint: '创建后不可修改，全局唯一' },
      { key: 'name', label: '仓库名称', required: true },
      { key: 'manager', label: '负责人', required: false },
      { key: 'address', label: '地址', required: false, full: true },
      { key: 'remark', label: '备注', required: false, full: true },
      { key: 'status', label: '状态', required: true, type: 'status' }
    ],
    filters: [
      { key: 'code', label: '编码' },
      { key: 'name', label: '名称' },
      { key: 'status', label: '状态', type: 'status' }
    ],
    disabled: '停用的仓库不允许在新单据中选择',
    addPerm: 'base:warehouse:add',
    editPerm: 'base:warehouse:edit',
    statusPerm: 'base:warehouse:status'
  },
  suppliers: {
    title: '供应商管理',
    eyebrow: 'MASTER DATA · SUPPLIER',
    desc: '维护供应商主数据，供采购订单选择使用。',
    table: 'suppliers',
    codeLabel: '供应商编码',
    nameLabel: '供应商名称',
    columns: [
      { key: 'code', label: '编码', type: 'name' },
      { key: 'name', label: '名称', type: 'text' },
      { key: 'contact', label: '联系人', type: 'text' },
      { key: 'phone', label: '电话', type: 'text' },
      { key: 'remark', label: '备注', type: 'text' },
      { key: 'status', label: '状态', type: 'status' }
    ],
    formFields: [
      { key: 'code', label: '供应商编码', required: true, hint: '创建后不可修改，全局唯一' },
      { key: 'name', label: '供应商名称', required: true },
      { key: 'contact', label: '联系人', required: false },
      { key: 'phone', label: '电话', required: false },
      { key: 'remark', label: '备注', required: false, full: true },
      { key: 'status', label: '状态', required: true, type: 'status' }
    ],
    filters: [
      { key: 'code', label: '编码' },
      { key: 'name', label: '名称' },
      { key: 'status', label: '状态', type: 'status' }
    ],
    disabled: '停用的供应商不允许在采购订单中选择',
    addPerm: 'base:supplier:add',
    editPerm: 'base:supplier:edit',
    statusPerm: 'base:supplier:status'
  },
  customers: {
    title: '客户管理',
    eyebrow: 'MASTER DATA · CUSTOMER',
    desc: '维护客户主数据，供销售订单选择使用。',
    table: 'customers',
    codeLabel: '客户编码',
    nameLabel: '客户名称',
    columns: [
      { key: 'code', label: '编码', type: 'name' },
      { key: 'name', label: '名称', type: 'text' },
      { key: 'contact', label: '联系人', type: 'text' },
      { key: 'phone', label: '电话', type: 'text' },
      { key: 'remark', label: '备注', type: 'text' },
      { key: 'status', label: '状态', type: 'status' }
    ],
    formFields: [
      { key: 'code', label: '客户编码', required: true, hint: '创建后不可修改，全局唯一' },
      { key: 'name', label: '客户名称', required: true },
      { key: 'contact', label: '联系人', required: false },
      { key: 'phone', label: '电话', required: false },
      { key: 'remark', label: '备注', required: false, full: true },
      { key: 'status', label: '状态', required: true, type: 'status' }
    ],
    filters: [
      { key: 'code', label: '编码' },
      { key: 'name', label: '名称' },
      { key: 'status', label: '状态', type: 'status' }
    ],
    disabled: '停用的客户不允许在销售订单中选择',
    addPerm: 'base:customer:add',
    editPerm: 'base:customer:edit',
    statusPerm: 'base:customer:status'
  }
}

const cfg = computed(() => MODULE_CONFIG[props.module])

const rows = ref([])
const total = ref(0)
const loading = ref(false)
const error = ref('')
const notice = ref('')

const filters = reactive({})
function initFilters() {
  cfg.value.filters.forEach((f) => {
    filters[f.key] = f.type === 'status' ? '' : ''
  })
  filters.page = 1
  filters.size = 10
}
initFilters()
const page = ref(1)
const size = ref(10)

const showForm = ref(false)
const editingId = ref(null)
const form = ref({})
const submitting = ref(false)
const formError = ref('')

function emptyForm() {
  const obj = {}
  cfg.value.formFields.forEach((f) => {
    obj[f.key] = f.type === 'status' ? 1 : ''
  })
  return obj
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
    const queryFilters = {}
    cfg.value.filters.forEach((f) => {
      if (f.type === 'status' && filters[f.key] === '') queryFilters[f.key] = ''
      else queryFilters[f.key] = filters[f.key]
    })
    const res = await api.list(cfg.value.table, {
      page: page.value,
      size: size.value,
      filters: queryFilters
    })
    rows.value = res.records
    total.value = res.total
  } catch (exception) {
    error.value = exception.message || '加载失败'
  } finally {
    loading.value = false
  }
}

function resetFilters() {
  initFilters()
  page.value = 1
  load()
}

function filterChanged() {
  page.value = 1
  load()
}

function openCreate() {
  editingId.value = null
  form.value = emptyForm()
  formError.value = ''
  showForm.value = true
}

function openEdit(row) {
  editingId.value = row.id
  form.value = { ...row }
  formError.value = ''
  showForm.value = true
}

async function save() {
  formError.value = ''
  const required = cfg.value.formFields.filter((f) => f.required)
  for (const f of required) {
    if (form.value[f.key] === '' || form.value[f.key] === undefined || form.value[f.key] === null) {
      formError.value = `请填写必填项：${f.label}`
      return
    }
  }
  submitting.value = true
  try {
    const payload = {}
    cfg.value.formFields.forEach((f) => {
      payload[f.key] = form.value[f.key]
    })
    if (editingId.value !== null) {
      delete payload.code
      await api.update(cfg.value.table, editingId.value, payload)
    } else {
      payload.status = payload.status === undefined ? 1 : payload.status
      await api.create(cfg.value.table, payload)
    }
    showForm.value = false
    await load()
    showNotice(editingId.value !== null ? '信息已更新' : '创建成功')
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
    await api.toggleStatus(cfg.value.table, row.id, next)
    await load()
    showNotice(`${row.name} 已${next === 1 ? '启用' : '停用'}`)
  } catch (exception) {
    error.value = exception.message || '状态更新失败'
  }
}

function statusLabel(row) {
  return row.status === 1 ? '启用' : '停用'
}

onMounted(load)
</script>

<template>
  <div class="page-content">
    <section class="page-heading">
      <div>
        <p class="eyebrow">{{ cfg.eyebrow }}</p>
        <h1>{{ cfg.title }}</h1>
        <p>{{ cfg.desc }} <small class="form-hint">（{{ cfg.disabled }}）</small></p>
      </div>
      <div class="heading-actions">
        <button class="secondary-button" @click="load"><span>↻</span> 刷新数据</button>
        <button class="primary-button" @click="openCreate"><span>＋</span> 新增{{ cfg.nameLabel }}</button>
      </div>
    </section>

    <section class="content-card">
      <div class="list-heading">
        <div><h2>{{ cfg.title }}</h2><p>共 {{ total }} 条记录，{{ cfg.disabled }}</p></div>
        <button class="refresh-button" :disabled="loading" @click="load">↻ <span>{{ loading ? '刷新中' : '刷新数据' }}</span></button>
      </div>

      <div class="filter-bar filter-3">
        <label class="search-box">
          <span>⌕</span>
          <input v-model="filters.code" :placeholder="`搜索${cfg.codeLabel}`" @input="filterChanged" />
        </label>
        <label class="search-box">
          <span>⌕</span>
          <input v-model="filters.name" :placeholder="`搜索${cfg.nameLabel}`" @input="filterChanged" />
        </label>
        <select v-model="filters.status" @change="filterChanged">
          <option value="">全部状态</option>
          <option value="1">启用</option>
          <option value="0">停用</option>
        </select>
        <button class="text-button" @click="resetFilters">重置</button>
      </div>

      <div v-if="error" class="message error-message"><span>!</span><p>{{ error }}</p><button @click="error = ''">×</button></div>

      <div class="table-wrap">
        <table>
          <thead>
            <tr>
              <th v-for="col in cfg.columns" :key="col.key">{{ col.label }}</th>
              <th class="action-column">操作</th>
            </tr>
          </thead>
          <tbody>
            <tr v-if="loading"><td :colspan="cfg.columns.length + 1" class="empty-state"><span class="spinner"></span><strong>正在读取数据</strong><small>请稍候…</small></td></tr>
            <tr v-else-if="rows.length === 0"><td :colspan="cfg.columns.length + 1" class="empty-state"><span class="empty-icon">⌕</span><strong>没有找到匹配的记录</strong><small>请调整筛选条件或新增一条</small></td></tr>
            <tr v-for="row in rows" v-else :key="row.id">
              <template v-for="col in cfg.columns" :key="col.key">
                <td v-if="col.type === 'name'">
                  <div class="cell-main"><strong>{{ row[col.key] }}</strong><code>{{ cfg.codeLabel }}</code></div>
                </td>
                <td v-else-if="col.type === 'status'">
                  <button :class="['status-switch', { off: row.status !== 1 }]" @click="toggleStatus(row)"><i></i>{{ statusLabel(row) }}</button>
                </td>
                <td v-else><span :class="{ 'cell-muted': !row[col.key] }">{{ row[col.key] || '—' }}</span></td>
              </template>
              <td class="row-actions">
                <button @click="openEdit(row)">编辑</button>
                <button @click="toggleStatus(row)">{{ row.status === 1 ? '停用' : '启用' }}</button>
              </td>
            </tr>
          </tbody>
        </table>
      </div>

      <footer class="list-footer">
        <span>显示 {{ rows.length }} / {{ total }} 条记录</span>
        <span>数据源：SET-{{ cfg.table === 'warehouses' ? '04' : '05' }}</span>
      </footer>
    </section>

    <div v-if="showForm" class="modal-backdrop" @click.self="showForm = false">
      <form class="modal" @submit.prevent="save">
        <div class="modal-title">
          <div>
            <p class="eyebrow">{{ cfg.eyebrow }}</p>
            <h2>{{ editingId !== null ? `编辑${cfg.nameLabel}` : `新增${cfg.nameLabel}` }}</h2>
            <p>{{ cfg.disabled }}</p>
          </div>
          <button type="button" class="icon-button" @click="showForm = false">×</button>
        </div>
        <div class="form-grid">
          <template v-for="f in cfg.formFields" :key="f.key">
            <label :class="{ full: f.full }">
              {{ f.label }} <em v-if="f.required">*</em>
              <select v-if="f.type === 'status'" v-model.number="form.status">
                <option :value="1">启用</option>
                <option :value="0">停用</option>
              </select>
              <input
                v-else
                v-model.trim="form[f.key]"
                :disabled="editingId !== null && f.key === 'code'"
                :required="f.required"
                maxlength="50"
                :placeholder="`请输入${f.label}`"
              />
              <span v-if="f.hint" class="form-hint">{{ f.hint }}</span>
            </label>
          </template>
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
.cell-muted { color: #a6b0c0; }
</style>
