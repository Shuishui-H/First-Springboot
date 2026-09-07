<script setup>
import { computed, onMounted, ref } from 'vue'
import InventoryOperationsView from './InventoryOperationsView.vue'

const props = defineProps({ currentUser: { type: Object, default: null } })

const activeTab = ref('warehouses')
const warehouses = ref([])
const balances = ref([])
const flows = ref([])
const products = ref([])
const loading = ref(false)
const error = ref('')
const notice = ref('')
function can(permission) { return !props.currentUser || props.currentUser.permissions?.includes(permission) }

const warehouseKeyword = ref('')
const warehouseStatusFilter = ref('全部状态')
const inventoryKeyword = ref('')
const inventoryWarehouseFilter = ref('全部仓库')
const flowKeyword = ref('')
const flowTypeFilter = ref('全部类型')

const showWarehouseForm = ref(false)
const editingWarehouseId = ref(null)
const warehouseForm = ref(emptyWarehouseForm())
const warehouseError = ref('')
const warehouseSubmitting = ref(false)
const showMovementForm = ref(false)
const movementForm = ref(emptyMovementForm())
const movementError = ref('')
const movementSubmitting = ref(false)

function emptyWarehouseForm() { return { code: '', name: '', manager: '', status: '启用' } }
function emptyMovementForm() { return { warehouseId: '', productId: '', businessType: '手工入库', quantity: 1, remark: '' } }

const filteredWarehouses = computed(() => {
  const keyword = warehouseKeyword.value.trim().toLowerCase()
  return warehouses.value.filter((warehouse) => {
    const matchKeyword = !keyword || warehouse.code.toLowerCase().includes(keyword) || warehouse.name.toLowerCase().includes(keyword) || (warehouse.manager || '').toLowerCase().includes(keyword)
    const matchStatus = warehouseStatusFilter.value === '全部状态' || warehouse.status === warehouseStatusFilter.value
    return matchKeyword && matchStatus
  })
})
const activeWarehouses = computed(() => warehouses.value.filter((warehouse) => warehouse.status === '启用'))
const inventoryWarehouseOptions = computed(() => ['全部仓库', ...warehouses.value.map((warehouse) => warehouse.name)])
const enabledProducts = computed(() => products.value.filter((product) => product.status === '启用'))
const totalQuantity = computed(() => balances.value.reduce((sum, balance) => sum + balance.quantity, 0))
const lowStockBalances = computed(() => balances.value.filter((balance) => balance.quantity < balance.safetyStock))
const todayFlowCount = computed(() => {
  const now = new Date()
  const today = `${now.getFullYear()}-${String(now.getMonth() + 1).padStart(2, '0')}-${String(now.getDate()).padStart(2, '0')}`
  return flows.value.filter((flow) => flow.time && flow.time.slice(0, 10) === today).length
})

async function loadAll() {
  loading.value = true
  error.value = ''
  try { await Promise.all([loadWarehouses(), loadBalances(), loadFlows(), loadProducts()]) }
  catch (exception) { error.value = exception.message || '加载仓储数据失败' }
  finally { loading.value = false }
}

async function loadWarehouses() {
  const params = new URLSearchParams()
  if (warehouseKeyword.value) params.set('keyword', warehouseKeyword.value)
  if (warehouseStatusFilter.value !== '全部状态') params.set('status', warehouseStatusFilter.value)
  warehouses.value = await fetchJson(`/api/warehouses${queryString(params)}`)
}

async function loadBalances() {
  const params = new URLSearchParams()
  if (inventoryWarehouseFilter.value !== '全部仓库') {
    const warehouse = warehouses.value.find((item) => item.name === inventoryWarehouseFilter.value)
    if (warehouse) params.set('warehouseId', warehouse.id)
  }
  if (inventoryKeyword.value) params.set('keyword', inventoryKeyword.value)
  balances.value = await fetchJson(`/api/inventory${queryString(params)}`)
}

async function loadFlows() {
  const params = new URLSearchParams()
  if (flowTypeFilter.value !== '全部类型') params.set('businessType', flowTypeFilter.value)
  if (flowKeyword.value) params.set('keyword', flowKeyword.value)
  flows.value = await fetchJson(`/api/stock-flows${queryString(params)}`)
}

async function loadProducts() { products.value = await fetchJson('/api/products') }
function queryString(params) { return params.toString() ? `?${params}` : '' }

async function fetchJson(url, options) {
  const response = await fetch(url, options)
  if (!response.ok) {
    let message = `请求失败（${response.status}）`
    try { const body = await response.json(); message = body.detail || body.message || message } catch (_) { /* ignore */ }
    throw new Error(message)
  }
  return response.status === 204 ? null : response.json()
}

function showNotice(text) {
  notice.value = text
  window.setTimeout(() => { if (notice.value === text) notice.value = '' }, 2600)
}
function parseError(exception) { return exception.message || '操作失败，请稍后重试' }

function openCreateWarehouse() {
  editingWarehouseId.value = null
  warehouseForm.value = emptyWarehouseForm()
  warehouseError.value = ''
  showWarehouseForm.value = true
}
function openEditWarehouse(warehouse) {
  editingWarehouseId.value = warehouse.id
  warehouseForm.value = { code: warehouse.code, name: warehouse.name, manager: warehouse.manager || '', status: warehouse.status }
  warehouseError.value = ''
  showWarehouseForm.value = true
}
async function saveWarehouse() {
  warehouseError.value = ''
  warehouseSubmitting.value = true
  try {
    const editing = editingWarehouseId.value !== null
    await fetchJson(editing ? `/api/warehouses/${editingWarehouseId.value}` : '/api/warehouses', {
      method: editing ? 'PUT' : 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(warehouseForm.value)
    })
    showWarehouseForm.value = false
    showNotice(editing ? '仓库档案已更新' : '仓库档案已创建')
    await loadAll()
  } catch (exception) { warehouseError.value = parseError(exception) }
  finally { warehouseSubmitting.value = false }
}
async function toggleWarehouseStatus(warehouse) {
  const nextStatus = warehouse.status === '启用' ? '停用' : '启用'
  try {
    await fetchJson(`/api/warehouses/${warehouse.id}`, { method: 'PUT', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ ...warehouse, status: nextStatus }) })
    showNotice(nextStatus === '启用' ? '仓库已启用' : '仓库已停用')
    await loadAll()
  } catch (exception) { error.value = parseError(exception) }
}
async function removeWarehouse(warehouse) {
  if (!window.confirm(`确定删除仓库「${warehouse.name}」吗？删除后不可恢复。`)) return
  try { await fetchJson(`/api/warehouses/${warehouse.id}`, { method: 'DELETE' }); showNotice('仓库已删除'); await loadAll() }
  catch (exception) { error.value = parseError(exception) }
}

function openMovementForm() {
  movementForm.value = emptyMovementForm()
  movementError.value = ''
  showMovementForm.value = true
}
async function submitMovement() {
  movementError.value = ''
  movementSubmitting.value = true
  try {
    await fetchJson('/api/stock-flows', { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ ...movementForm.value, warehouseId: Number(movementForm.value.warehouseId), productId: Number(movementForm.value.productId), quantity: Number(movementForm.value.quantity) }) })
    showMovementForm.value = false
    showNotice('出入库登记成功，库存已更新')
    await loadAll()
  } catch (exception) { movementError.value = parseError(exception) }
  finally { movementSubmitting.value = false }
}

function formatCurrency(value) { return `¥ ${Number(value || 0).toLocaleString('zh-CN', { minimumFractionDigits: 2, maximumFractionDigits: 2 })}` }
function formatDateTime(value) { return value ? value.replace('T', ' ').slice(0, 19) : '—' }
function flowClass(flow) { return flow.changeQuantity > 0 ? 'in' : 'out' }

onMounted(loadAll)
</script>

<template>
  <div class="page-content">
    <section class="page-heading"><div><p class="eyebrow">WAREHOUSE · INVENTORY</p><h1>仓储管理</h1><p>统一管理仓库档案、商品库存余额与出入库流水。</p></div><div class="heading-actions"><button class="secondary-button" @click="loadAll" :disabled="loading"><span>↻</span> 刷新数据</button><button v-if="can('inventory:movement:manage')" class="primary-button" @click="openMovementForm"><span>⇅</span> 手工出入库</button><button v-if="can('base:warehouse:manage')" class="primary-button" @click="openCreateWarehouse"><span>＋</span> 新增仓库</button></div></section>
    <section class="metrics"><article class="metric-card blue"><div class="metric-icon">仓</div><div><span>仓库数量</span><strong>{{ warehouses.length }}</strong><small>{{ activeWarehouses.length }} 个正在启用</small></div><b>V3</b></article><article class="metric-card violet"><div class="metric-icon">库</div><div><span>库存总量</span><strong>{{ totalQuantity.toLocaleString() }}</strong><small>全部仓库现存合计</small></div><b>实时</b></article><article class="metric-card orange"><div class="metric-icon">!</div><div><span>库存预警</span><strong>{{ lowStockBalances.length }}</strong><small>{{ lowStockBalances.length ? '低于安全库存，需及时补货' : '库存状态全部正常' }}</small></div><b :class="{ alert: lowStockBalances.length }">{{ lowStockBalances.length ? '待处理' : '正常' }}</b></article><article class="metric-card green"><div class="metric-icon">流</div><div><span>今日流水</span><strong>{{ todayFlowCount }}</strong><small>今日出入库登记笔数</small></div><b>动态</b></article></section>
    <div class="wh-tabs"><button :class="['wh-tab', { active: activeTab === 'warehouses' }]" @click="activeTab = 'warehouses'"><span>▤</span>仓库档案<em>{{ warehouses.length }}</em></button><button :class="['wh-tab', { active: activeTab === 'inventory' }]" @click="activeTab = 'inventory'"><span>库</span>库存余额<em>{{ balances.length }}</em></button><button :class="['wh-tab', { active: activeTab === 'flows' }]" @click="activeTab = 'flows'"><span>流</span>库存流水<em>{{ flows.length }}</em></button><button v-if="can('inventory:transfer:manage') || can('inventory:stocktake:manage')" :class="['wh-tab', { active: activeTab === 'operations' }]" @click="activeTab = 'operations'"><span>⇄</span>调拨与盘点<em>V3</em></button></div>
    <div v-if="error" class="message error-message"><span>!</span><p>{{ error }}</p><button @click="error = ''">×</button></div>

    <section v-if="activeTab === 'warehouses'" class="content-card"><div class="list-heading"><div><h2>仓库列表</h2><p>共 {{ warehouses.length }} 个仓库，当前筛选出 {{ filteredWarehouses.length }} 条</p></div><button class="refresh-button" :disabled="loading" @click="loadWarehouses">↻ <span>{{ loading ? '刷新中' : '刷新数据' }}</span></button></div><div class="filter-bar wh-filter"><label class="search-box"><span>⌕</span><input v-model="warehouseKeyword" placeholder="搜索仓库编码、名称或负责人" /></label><select v-model="warehouseStatusFilter"><option>全部状态</option><option>启用</option><option>停用</option></select><button class="text-button" @click="warehouseKeyword = ''; warehouseStatusFilter = '全部状态'">重置</button></div><div class="table-wrap"><table><thead><tr><th>仓库编码</th><th>仓库名称</th><th>负责人</th><th>启用状态</th><th class="action-column">操作</th></tr></thead><tbody><tr v-if="loading"><td colspan="5" class="empty-state"><span class="spinner"></span><strong>正在读取仓库数据</strong><small>请稍候…</small></td></tr><tr v-else-if="filteredWarehouses.length === 0"><td colspan="5" class="empty-state"><span class="empty-icon">⌕</span><strong>暂无匹配仓库</strong><small>可以新建一个仓库开始管理</small></td></tr><tr v-for="warehouse in filteredWarehouses" v-else :key="warehouse.id"><td><code>{{ warehouse.code }}</code></td><td><strong>{{ warehouse.name }}</strong></td><td>{{ warehouse.manager || '—' }}</td><td><button :class="['status-switch', { off: warehouse.status !== '启用' }]" @click="toggleWarehouseStatus(warehouse)"><i></i>{{ warehouse.status }}</button></td><td class="row-actions"><button @click="openEditWarehouse(warehouse)">编辑</button><button class="danger" @click="removeWarehouse(warehouse)">删除</button></td></tr></tbody></table></div><footer class="list-footer"><span>显示 {{ filteredWarehouses.length }} / {{ warehouses.length }} 条记录</span><span>数据源：Spring Boot 服务 · 持久化仓库主数据</span></footer></section>

    <section v-else-if="activeTab === 'inventory'" class="content-card"><div class="list-heading"><div><h2>库存余额</h2><p>按「商品 + 仓库」维度展示现存 / 锁定 / 可用数量</p></div><button class="refresh-button" :disabled="loading" @click="loadBalances">↻ <span>{{ loading ? '刷新中' : '刷新数据' }}</span></button></div><div class="filter-bar wh-filter inventory-filter"><label class="search-box"><span>⌕</span><input v-model="inventoryKeyword" placeholder="搜索商品名称、编码或仓库" /></label><select v-model="inventoryWarehouseFilter"><option v-for="name in inventoryWarehouseOptions" :key="name">{{ name }}</option></select><button class="text-button" @click="inventoryKeyword = ''; inventoryWarehouseFilter = '全部仓库'">重置</button></div><div class="table-wrap"><table><thead><tr><th>仓库</th><th>商品信息</th><th>现存数量</th><th>锁定 / 可用</th><th>安全库存</th><th>库存状态</th></tr></thead><tbody><tr v-if="loading"><td colspan="6" class="empty-state"><span class="spinner"></span><strong>正在读取库存数据</strong><small>请稍候…</small></td></tr><tr v-else-if="balances.length === 0"><td colspan="6" class="empty-state"><span class="empty-icon">⌕</span><strong>暂无库存数据</strong><small>可通过手工出入库登记生成库存</small></td></tr><tr v-for="balance in balances" v-else :key="balance.id"><td><span class="category-chip">{{ balance.warehouseName }}</span></td><td><div class="product-cell"><span :class="['product-avatar', balance.productId % 2 ? 'tone-1' : 'tone-0']">{{ balance.productName.slice(0, 1) }}</span><div><strong>{{ balance.productName }}</strong><code>{{ balance.productSku }} · {{ balance.unit }}</code></div></div></td><td><strong>{{ balance.quantity.toLocaleString() }} {{ balance.unit }}</strong></td><td><span class="balance-split"><b>{{ balance.lockedQuantity }}</b> / <b>{{ balance.availableQuantity }}</b></span></td><td>{{ balance.safetyStock }} {{ balance.unit }}</td><td><span :class="['stock-label', balance.quantity >= balance.safetyStock ? 'healthy' : 'low']">{{ balance.quantity >= balance.safetyStock ? '库存正常' : '库存偏低' }}</span></td></tr></tbody></table></div><footer class="list-footer"><span>显示 {{ balances.length }} 条库存记录</span><span>初始库存由商品档案同步至主仓 · 手工出入库同步更新商品总库存</span></footer></section>

    <section v-else-if="activeTab === 'flows'" class="content-card"><div class="list-heading"><div><h2>库存流水</h2><p>记录每次出入库变动，支持按类型与关键字检索</p></div><button class="refresh-button" :disabled="loading" @click="loadFlows">↻ <span>{{ loading ? '刷新中' : '刷新数据' }}</span></button></div><div class="filter-bar wh-filter flow-filter"><label class="search-box"><span>⌕</span><input v-model="flowKeyword" placeholder="搜索流水号、商品名称或编码" /></label><select v-model="flowTypeFilter"><option>全部类型</option><option>采购入库</option><option>销售出库</option><option>销售退货</option><option>手工入库</option><option>手工出库</option><option>仓库调入</option><option>仓库调出</option><option>盘盈调整</option><option>盘亏调整</option></select><button class="text-button" @click="flowKeyword = ''; flowTypeFilter = '全部类型'">重置</button></div><div class="table-wrap"><table><thead><tr><th>流水号</th><th>业务类型</th><th>商品信息</th><th>仓库</th><th>变动数量</th><th>来源 / 备注</th><th>操作人</th><th>时间</th></tr></thead><tbody><tr v-if="loading"><td colspan="8" class="empty-state"><span class="spinner"></span><strong>正在读取流水数据</strong><small>请稍候…</small></td></tr><tr v-else-if="flows.length === 0"><td colspan="8" class="empty-state"><span class="empty-icon">⌕</span><strong>暂无库存流水</strong><small>出入库登记后将在此生成流水</small></td></tr><tr v-for="flow in flows" v-else :key="flow.id"><td><code>{{ flow.flowNo }}</code></td><td><span :class="['order-status', `status-${flowClass(flow)}`]">{{ flow.businessType }}</span></td><td><div class="product-cell"><span :class="['product-avatar', flow.productId % 2 ? 'tone-1' : 'tone-0']">{{ flow.productName.slice(0, 1) }}</span><div><strong>{{ flow.productName }}</strong><code>{{ flow.productSku }}</code></div></div></td><td><span class="category-chip">{{ flow.warehouseName }}</span></td><td><strong :class="['flow-qty', flowClass(flow)]">{{ flow.changeQuantity > 0 ? '+' : '' }}{{ flow.changeQuantity }}</strong></td><td class="flow-source">{{ flow.sourceNo || '—' }}</td><td>{{ flow.operator }}</td><td>{{ formatDateTime(flow.time) }}</td></tr></tbody></table></div><footer class="list-footer"><span>显示 {{ flows.length }} 条流水记录</span><span>采购、销售、调拨、盘点与手工出入库都会生成流水</span></footer></section>
    <InventoryOperationsView v-else :current-user="currentUser" />

    <div v-if="showWarehouseForm" class="modal-backdrop" @click.self="showWarehouseForm = false"><form class="modal warehouse-modal" @submit.prevent="saveWarehouse"><div class="modal-title"><div><p class="eyebrow">WAREHOUSE MASTER</p><h2>{{ editingWarehouseId ? '编辑仓库档案' : '新建仓库档案' }}</h2><p>完善仓库基础信息。</p></div><button type="button" class="icon-button" @click="showWarehouseForm = false">×</button></div><div class="form-grid"><label>仓库编码 <em>*</em><input v-model.trim="warehouseForm.code" required maxlength="32" placeholder="例如 WH-3001" /></label><label>仓库名称 <em>*</em><input v-model.trim="warehouseForm.name" required maxlength="80" placeholder="请输入仓库名称" /></label><label>负责人<input v-model.trim="warehouseForm.manager" maxlength="40" placeholder="请输入负责人姓名" /></label><label>启用状态 <em>*</em><select v-model="warehouseForm.status"><option>启用</option><option>停用</option></select></label></div><div v-if="warehouseError" class="message error-message compact"><span>!</span><p>{{ warehouseError }}</p></div><div class="modal-actions"><button type="button" class="secondary-button" @click="showWarehouseForm = false">取消</button><button type="submit" class="primary-button" :disabled="warehouseSubmitting">{{ warehouseSubmitting ? '保存中…' : '保存仓库' }}</button></div></form></div>
    <div v-if="showMovementForm" class="modal-backdrop" @click.self="showMovementForm = false"><form class="modal movement-modal" @submit.prevent="submitMovement"><div class="modal-title"><div><p class="eyebrow">STOCK MOVEMENT</p><h2>手工出入库登记</h2><p>登记后库存余额即时更新并生成对应流水。</p></div><button type="button" class="icon-button" @click="showMovementForm = false">×</button></div><div class="form-grid"><label>业务类型 <em>*</em><select v-model="movementForm.businessType"><option>手工入库</option><option>手工出库</option></select></label><label>所属仓库 <em>*</em><select v-model="movementForm.warehouseId" required><option value="" disabled>请选择仓库</option><option v-for="warehouse in activeWarehouses" :key="warehouse.id" :value="warehouse.id">{{ warehouse.name }} · {{ warehouse.code }}</option></select></label><label>商品 <em>*</em><select v-model="movementForm.productId" required><option value="" disabled>请选择商品</option><option v-for="product in enabledProducts" :key="product.id" :value="product.id">{{ product.name }} · {{ product.sku }}</option></select></label><label>变动数量 <em>*</em><input v-model.number="movementForm.quantity" type="number" min="1" required /></label><label class="span-2">来源 / 备注<input v-model.trim="movementForm.remark" maxlength="200" placeholder="例如：手工盘点调整、退换货入库" /></label></div><div v-if="movementError" class="message error-message compact"><span>!</span><p>{{ movementError }}</p></div><div class="modal-actions"><button type="button" class="secondary-button" @click="showMovementForm = false">取消</button><button type="submit" class="primary-button" :disabled="movementSubmitting">{{ movementSubmitting ? '提交中…' : '确认登记' }}</button></div></form></div>
    <transition name="toast"><div v-if="notice" class="toast-message"><span>✓</span>{{ notice }}</div></transition>
  </div>
</template>

<style scoped>
.wh-tabs { display: flex; gap: 8px; margin-bottom: 18px; flex-wrap: wrap; }
.wh-tab { display: inline-flex; align-items: center; gap: 8px; padding: 9px 18px; border: 1px solid rgba(13, 29, 58, 0.1); border-radius: 10px; background: #fff; color: var(--navy); font-size: 14px; font-weight: 600; cursor: pointer; transition: all .18s ease; }
.wh-tab span { opacity: .65; }.wh-tab em { font-style: normal; font-size: 12px; font-weight: 700; background: rgba(13, 29, 58, .06); color: var(--navy); border-radius: 20px; padding: 1px 9px; }.wh-tab:hover { border-color: var(--primary); color: var(--primary); }.wh-tab.active { background: var(--primary); border-color: var(--primary); color: #fff; box-shadow: 0 6px 14px rgba(49, 95, 214, .25); }.wh-tab.active em { background: rgba(255, 255, 255, .22); color: #fff; }
.wh-filter, .inventory-filter, .flow-filter { grid-template-columns: minmax(260px, 1fr) 170px auto; }.balance-split b { font-weight: 600; }.balance-split b:first-child { color: var(--primary); }.balance-split b:last-child { color: #22a06b; }.flow-qty { font-variant-numeric: tabular-nums; }.flow-qty.in { color: #22a06b; }.flow-qty.out { color: #e5484d; }.flow-source { color: #6b7280; font-size: 13px; }.span-2 { grid-column: span 2; }.row-actions .danger { color: #bd4d4d; }.row-actions .danger:hover { color: #b02f2f; }.status-in { color: #258363; background: #e9f8f1; }.status-out { color: #b36324; background: #fff0e1; }
@media (max-width: 680px) { .wh-tabs { flex-wrap: nowrap; overflow-x: auto; }.wh-tab { flex: 1; justify-content: center; padding: 9px 10px; }.span-2 { grid-column: span 1; } }
</style>
