<script setup>
import { computed, onMounted, ref } from 'vue'

const props = defineProps({ currentUser: { type: Object, default: null } })

const tab = ref('transfer')
const warehouses = ref([])
const products = ref([])
const balances = ref([])
const transfers = ref([])
const stocktakes = ref([])
const loading = ref(false)
const error = ref('')
const notice = ref('')
function can(permission) { return !props.currentUser || props.currentUser.permissions?.includes(permission) }
const showTransferForm = ref(false)
const showStocktakeForm = ref(false)
const submitting = ref(false)
const formError = ref('')

const dateToday = () => new Date().toISOString().slice(0, 10)
const emptyTransfer = () => ({ fromWarehouseId: '', toWarehouseId: '', transferDate: dateToday(), remark: '', items: [{ productId: '', quantity: 1, remark: '' }] })
const emptyStocktake = () => ({ warehouseId: '', stocktakeDate: dateToday(), remark: '', items: [{ productId: '', countedQuantity: 0, reason: '' }] })
const transferForm = ref(emptyTransfer())
const stocktakeForm = ref(emptyStocktake())

const enabledWarehouses = computed(() => warehouses.value.filter((item) => item.status === '启用'))
const enabledProducts = computed(() => products.value.filter((item) => item.status === '启用'))
const pendingTransfers = computed(() => transfers.value.filter((item) => item.status === '待确认').length)
const pendingStocktakes = computed(() => stocktakes.value.filter((item) => item.status === '待确认').length)

async function fetchJson(url, options) {
  const response = await fetch(url, options)
  if (!response.ok) {
    let message = `请求失败（${response.status}）`
    try { const body = await response.json(); message = body.detail || body.message || message } catch (_) { /* ignore */ }
    throw new Error(message)
  }
  return response.status === 204 ? null : response.json()
}

async function loadAll() {
  loading.value = true
  error.value = ''
  try {
    const result = await Promise.all([
      fetchJson('/api/warehouses'), fetchJson('/api/products'), fetchJson('/api/inventory'),
      fetchJson('/api/inventory/transfers'), fetchJson('/api/inventory/stocktakes')
    ])
    ;[warehouses.value, products.value, balances.value, transfers.value, stocktakes.value] = result
  } catch (exception) { error.value = exception.message || '无法读取调拨与盘点数据' }
  finally { loading.value = false }
}

function showNotice(text) {
  notice.value = text
  window.setTimeout(() => { if (notice.value === text) notice.value = '' }, 2600)
}
function productById(id) { return products.value.find((item) => item.id === Number(id)) }
function balanceFor(warehouseId, productId) { return balances.value.find((item) => item.warehouseId === Number(warehouseId) && item.productId === Number(productId)) }
function availableFor(warehouseId, productId) { return balanceFor(warehouseId, productId)?.availableQuantity ?? 0 }

function openTransfer() { transferForm.value = emptyTransfer(); formError.value = ''; showTransferForm.value = true }
function addTransferLine() { if (transferForm.value.items.length < 50) transferForm.value.items.push({ productId: '', quantity: 1, remark: '' }) }
function removeTransferLine(index) { if (transferForm.value.items.length > 1) transferForm.value.items.splice(index, 1) }
async function createTransfer() {
  formError.value = ''; submitting.value = true
  try {
    const payload = { ...transferForm.value, fromWarehouseId: Number(transferForm.value.fromWarehouseId), toWarehouseId: Number(transferForm.value.toWarehouseId), items: transferForm.value.items.map((item) => ({ ...item, productId: Number(item.productId), quantity: Number(item.quantity) })) }
    await fetchJson('/api/inventory/transfers', { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(payload) })
    showTransferForm.value = false; showNotice('调拨单已创建，等待确认'); await loadAll()
  } catch (exception) { formError.value = exception.message || '创建调拨单失败' }
  finally { submitting.value = false }
}
async function confirmTransfer(order) {
  if (!window.confirm(`确认将 ${order.items.length} 种商品从「${order.fromWarehouseName}」调拨至「${order.toWarehouseName}」吗？确认后库存会立即变化。`)) return
  try { await fetchJson(`/api/inventory/transfers/${order.id}/confirm`, { method: 'POST' }); showNotice('调拨已确认，两个仓库库存已同步'); await loadAll() }
  catch (exception) { error.value = exception.message || '确认调拨失败' }
}
async function cancelTransfer(order) {
  if (!window.confirm(`确定取消调拨单「${order.transferNo}」吗？`)) return
  try { await fetchJson(`/api/inventory/transfers/${order.id}/cancel`, { method: 'POST' }); showNotice('调拨单已取消'); await loadAll() }
  catch (exception) { error.value = exception.message || '取消调拨失败' }
}

function openStocktake() { stocktakeForm.value = emptyStocktake(); formError.value = ''; showStocktakeForm.value = true }
function addStocktakeLine() { if (stocktakeForm.value.items.length < 50) stocktakeForm.value.items.push({ productId: '', countedQuantity: 0, reason: '' }) }
function removeStocktakeLine(index) { if (stocktakeForm.value.items.length > 1) stocktakeForm.value.items.splice(index, 1) }
function useBookQuantity(line) { line.countedQuantity = balanceFor(stocktakeForm.value.warehouseId, line.productId)?.quantity ?? 0 }
async function createStocktake() {
  formError.value = ''; submitting.value = true
  try {
    const payload = { ...stocktakeForm.value, warehouseId: Number(stocktakeForm.value.warehouseId), items: stocktakeForm.value.items.map((item) => ({ ...item, productId: Number(item.productId), countedQuantity: Number(item.countedQuantity) })) }
    await fetchJson('/api/inventory/stocktakes', { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(payload) })
    showStocktakeForm.value = false; showNotice('盘点单已创建，等待确认'); await loadAll()
  } catch (exception) { formError.value = exception.message || '创建盘点单失败' }
  finally { submitting.value = false }
}
async function confirmStocktake(order) {
  if (!window.confirm(`确认盘点单「${order.stocktakeNo}」吗？确认后将按实盘数量调整库存。`)) return
  try { await fetchJson(`/api/inventory/stocktakes/${order.id}/confirm`, { method: 'POST' }); showNotice('盘点已确认，库存余额和流水已更新'); await loadAll() }
  catch (exception) { error.value = exception.message || '确认盘点失败' }
}
function formatDate(value) { return value || '—' }
function formatDateTime(value) { return value ? value.replace('T', ' ').slice(0, 16) : '—' }
function orderTone(status) { return status === '已确认' ? 'done' : status === '已取消' ? 'cancelled' : 'pending' }

onMounted(loadAll)
</script>

<template>
  <section class="operations-card">
    <div class="operations-head"><div><p class="eyebrow">V3 · INVENTORY CONTROL</p><h2>调拨与盘点</h2><p>通过正式业务单据调整分仓库存，每次确认都会留下库存流水。</p></div><div class="operations-actions"><button class="secondary-button" :disabled="loading" @click="loadAll">↻ 刷新数据</button><button v-if="can('inventory:stocktake:manage')" class="secondary-button" @click="openStocktake">盘点库存</button><button v-if="can('inventory:transfer:manage')" class="primary-button" @click="openTransfer">⇄ 新建调拨</button></div></div>
    <div class="operation-metrics"><div><span>待确认调拨</span><strong>{{ pendingTransfers }}</strong><small>确认后两仓库存同步变化</small></div><div><span>待确认盘点</span><strong>{{ pendingStocktakes }}</strong><small>确认后按实盘差异调整</small></div><div><span>当前可用仓库</span><strong>{{ enabledWarehouses.length }}</strong><small>停用仓库不可创建新单据</small></div></div>
    <div class="operation-tabs"><button v-if="can('inventory:transfer:manage')" :class="{ active: tab === 'transfer' }" @click="tab = 'transfer'">仓库调拨 <em>{{ transfers.length }}</em></button><button v-if="can('inventory:stocktake:manage')" :class="{ active: tab === 'stocktake' }" @click="tab = 'stocktake'">库存盘点 <em>{{ stocktakes.length }}</em></button></div>
    <div v-if="error" class="operation-error"><span>!</span>{{ error }}<button @click="error = ''">×</button></div>

    <div v-if="tab === 'transfer'" class="operation-table"><div class="operation-caption"><div><h3>调拨单列表</h3><p>从有库存的仓库调拨至缺货或低库存仓库；调拨不会改变商品全仓总库存。</p></div></div><table><thead><tr><th>调拨单号</th><th>调拨方向</th><th>商品与数量</th><th>状态</th><th>创建时间</th><th>操作</th></tr></thead><tbody><tr v-if="loading"><td colspan="6" class="empty">加载中…</td></tr><tr v-else-if="!transfers.length"><td colspan="6" class="empty">暂无调拨单。主仓库存不足时，可从其他仓库发起调拨。</td></tr><tr v-for="order in transfers" :key="order.id"><td><code>{{ order.transferNo }}</code><small>{{ formatDate(order.transferDate) }}</small></td><td><strong>{{ order.fromWarehouseName }}</strong><span class="direction">→</span><strong>{{ order.toWarehouseName }}</strong></td><td><div v-for="item in order.items" :key="item.productId" class="line-item">{{ item.productName }} <b>{{ item.quantity }} {{ item.unit }}</b></div></td><td><span :class="['operation-status', orderTone(order.status)]">{{ order.status }}</span></td><td>{{ formatDateTime(order.createdAt) }}</td><td class="table-actions"><button v-if="can('inventory:transfer:manage') && order.status === '待确认'" class="confirm" @click="confirmTransfer(order)">确认调拨</button><button v-if="can('inventory:transfer:manage') && order.status === '待确认'" @click="cancelTransfer(order)">取消</button><span v-else>—</span></td></tr></tbody></table></div>

    <div v-else class="operation-table"><div class="operation-caption"><div><h3>盘点单列表</h3><p>实盘数量与账面数量有差异时，确认后自动生成盘盈或盘亏流水。</p></div></div><table><thead><tr><th>盘点单号</th><th>盘点仓库</th><th>商品差异</th><th>状态</th><th>创建时间</th><th>操作</th></tr></thead><tbody><tr v-if="loading"><td colspan="6" class="empty">加载中…</td></tr><tr v-else-if="!stocktakes.length"><td colspan="6" class="empty">暂无盘点单。可创建盘点单记录实盘差异。</td></tr><tr v-for="order in stocktakes" :key="order.id"><td><code>{{ order.stocktakeNo }}</code><small>{{ formatDate(order.stocktakeDate) }}</small></td><td><strong>{{ order.warehouseName }}</strong></td><td><div v-for="item in order.items" :key="item.productId" class="line-item">{{ item.productName }} <b :class="{ positive: item.differenceQuantity > 0, negative: item.differenceQuantity < 0 }">{{ item.differenceQuantity > 0 ? '+' : '' }}{{ item.differenceQuantity }}</b></div></td><td><span :class="['operation-status', orderTone(order.status)]">{{ order.status }}</span></td><td>{{ formatDateTime(order.createdAt) }}</td><td class="table-actions"><button v-if="can('inventory:stocktake:manage') && order.status === '待确认'" class="confirm" @click="confirmStocktake(order)">确认盘点</button><span v-else>—</span></td></tr></tbody></table></div>

    <div v-if="showTransferForm" class="modal-backdrop" @click.self="showTransferForm = false"><form class="modal operation-modal" @submit.prevent="createTransfer"><div class="modal-title"><div><p class="eyebrow">WAREHOUSE TRANSFER</p><h2>新建仓库调拨</h2><p>确认后从调出仓扣减，并同步增加调入仓库存。</p></div><button type="button" class="icon-button" @click="showTransferForm = false">×</button></div><div class="form-grid"><label>调出仓 <em>*</em><select v-model="transferForm.fromWarehouseId" required><option value="" disabled>请选择调出仓</option><option v-for="warehouse in enabledWarehouses" :key="warehouse.id" :value="warehouse.id">{{ warehouse.name }}</option></select></label><label>调入仓 <em>*</em><select v-model="transferForm.toWarehouseId" required><option value="" disabled>请选择调入仓</option><option v-for="warehouse in enabledWarehouses" :key="warehouse.id" :value="warehouse.id" :disabled="Number(transferForm.fromWarehouseId) === warehouse.id">{{ warehouse.name }}</option></select></label><label>调拨日期 <em>*</em><input v-model="transferForm.transferDate" type="date" required /></label><label>备注<input v-model.trim="transferForm.remark" maxlength="255" placeholder="可选" /></label></div><div class="form-lines"><div class="form-lines-title"><strong>调拨明细</strong><button type="button" class="text-button" @click="addTransferLine">＋ 添加商品</button></div><div v-for="(line, index) in transferForm.items" :key="index" class="line-form"><select v-model="line.productId" required><option value="" disabled>选择商品</option><option v-for="product in enabledProducts" :key="product.id" :value="product.id">{{ product.name }} · {{ product.sku }}</option></select><input v-model.number="line.quantity" type="number" min="1" required /><span class="available">可用 {{ availableFor(transferForm.fromWarehouseId, line.productId) }}</span><button type="button" class="remove-line" :disabled="transferForm.items.length === 1" @click="removeTransferLine(index)">×</button></div></div><div v-if="formError" class="operation-error compact"><span>!</span>{{ formError }}</div><div class="modal-actions"><button type="button" class="secondary-button" @click="showTransferForm = false">取消</button><button type="submit" class="primary-button" :disabled="submitting">{{ submitting ? '提交中…' : '创建调拨单' }}</button></div></form></div>

    <div v-if="showStocktakeForm" class="modal-backdrop" @click.self="showStocktakeForm = false"><form class="modal operation-modal" @submit.prevent="createStocktake"><div class="modal-title"><div><p class="eyebrow">STOCKTAKE</p><h2>新建库存盘点</h2><p>系统会保存当前账面数量，确认时校验库存是否已发生变化。</p></div><button type="button" class="icon-button" @click="showStocktakeForm = false">×</button></div><div class="form-grid"><label>盘点仓库 <em>*</em><select v-model="stocktakeForm.warehouseId" required><option value="" disabled>请选择盘点仓库</option><option v-for="warehouse in enabledWarehouses" :key="warehouse.id" :value="warehouse.id">{{ warehouse.name }}</option></select></label><label>盘点日期 <em>*</em><input v-model="stocktakeForm.stocktakeDate" type="date" required /></label><label class="span-2">备注<input v-model.trim="stocktakeForm.remark" maxlength="255" placeholder="可选" /></label></div><div class="form-lines"><div class="form-lines-title"><strong>盘点明细</strong><button type="button" class="text-button" @click="addStocktakeLine">＋ 添加商品</button></div><div v-for="(line, index) in stocktakeForm.items" :key="index" class="line-form stocktake-line"><select v-model="line.productId" required><option value="" disabled>选择商品</option><option v-for="product in enabledProducts" :key="product.id" :value="product.id">{{ product.name }} · {{ product.sku }}</option></select><span class="available">账面 {{ balanceFor(stocktakeForm.warehouseId, line.productId)?.quantity ?? 0 }}</span><input v-model.number="line.countedQuantity" type="number" min="0" required placeholder="实盘" /><input v-model.trim="line.reason" required maxlength="255" placeholder="盘点原因" /><button type="button" class="use-book" @click="useBookQuantity(line)">账实一致</button><button type="button" class="remove-line" :disabled="stocktakeForm.items.length === 1" @click="removeStocktakeLine(index)">×</button></div></div><div v-if="formError" class="operation-error compact"><span>!</span>{{ formError }}</div><div class="modal-actions"><button type="button" class="secondary-button" @click="showStocktakeForm = false">取消</button><button type="submit" class="primary-button" :disabled="submitting">{{ submitting ? '提交中…' : '创建盘点单' }}</button></div></form></div>
    <transition name="toast"><div v-if="notice" class="toast-message"><span>✓</span>{{ notice }}</div></transition>
  </section>
</template>

<style scoped>
.operations-card { background: #fff; border: 1px solid rgba(13,29,58,.08); border-radius: 18px; box-shadow: 0 12px 30px rgba(23,49,89,.05); overflow: hidden; }.operations-head { padding: 27px 30px 20px; display:flex; justify-content:space-between; gap:18px; align-items:flex-start; }.operations-head h2 { margin:3px 0 7px; color:#10254a; font-size:24px; }.operations-head p { margin:0; color:#7890b2; }.operations-actions { display:flex; gap:9px; flex-wrap:wrap; }.operation-metrics { display:grid; grid-template-columns:repeat(3,1fr); background:#f7faff; border-top:1px solid #edf2fb; border-bottom:1px solid #edf2fb; }.operation-metrics div { padding:16px 30px; border-right:1px solid #e8eef8; }.operation-metrics div:last-child { border:0; }.operation-metrics span,.operation-metrics small { display:block; color:#7890b2; font-size:12px; }.operation-metrics strong { display:block; color:#173d78; font-size:25px; margin:3px 0; }.operation-tabs { padding:18px 30px 0; display:flex; gap:8px; }.operation-tabs button { border:0; border-bottom:2px solid transparent; background:transparent; color:#6f83a3; font-weight:700; padding:10px 13px; cursor:pointer; }.operation-tabs button.active { color:#2e64d6; border-color:#2e64d6; }.operation-tabs em { font-style:normal; font-size:11px; margin-left:5px; padding:1px 7px; border-radius:10px; background:#edf3ff; }.operation-error { margin:12px 30px; padding:11px 14px; border-radius:9px; background:#fff0f0; color:#bf4545; display:flex; gap:9px; align-items:center; }.operation-error button { margin-left:auto; border:0; background:transparent; color:inherit; cursor:pointer; font-size:18px; }.operation-error.compact { margin:14px 0 0; }.operation-table { padding:0 30px 27px; }.operation-caption { padding:14px 0; }.operation-caption h3 { margin:0 0 4px; color:#18335e; }.operation-caption p { margin:0; font-size:13px; color:#8596af; }.operation-table table { width:100%; border-collapse:collapse; font-size:13px; }.operation-table th { text-align:left; color:#8292aa; font-weight:600; padding:11px 9px; border-bottom:1px solid #e8eef7; }.operation-table td { padding:13px 9px; color:#415571; border-bottom:1px solid #eff3f8; vertical-align:top; }.operation-table code { color:#3867c6; font-weight:700; }.operation-table td small { display:block; color:#91a0b7; margin-top:4px; }.direction { margin:0 6px; color:#4d79d7; }.line-item { margin-bottom:4px; }.line-item b { color:#193660; margin-left:6px; }.operation-status { display:inline-block; padding:4px 9px; border-radius:10px; font-size:12px; font-weight:700; }.operation-status.pending { color:#9b651b; background:#fff3e0; }.operation-status.done { color:#258363; background:#e9f8f1; }.operation-status.cancelled { color:#7f8794; background:#f0f2f5; }.table-actions { white-space:nowrap; }.table-actions button { border:0; background:transparent; color:#5c7191; cursor:pointer; margin-right:8px; }.table-actions .confirm { color:#2860d1; font-weight:700; }.positive { color:#208360; }.negative { color:#c34b4b; }.empty { text-align:center; color:#91a0b7; padding:36px !important; }.form-lines { margin-top:19px; border-top:1px solid #edf1f7; padding-top:15px; }.form-lines-title { display:flex; justify-content:space-between; margin-bottom:10px; color:#294563; }.line-form { display:grid; grid-template-columns:minmax(180px,1fr) 100px 90px 34px; gap:8px; align-items:center; margin-bottom:8px; }.line-form select,.line-form input { min-width:0; }.available { color:#6f86a5; font-size:12px; }.remove-line,.use-book { border:0; background:#eef3fb; color:#557096; border-radius:7px; cursor:pointer; padding:7px; }.remove-line:disabled { opacity:.35; cursor:not-allowed; }.stocktake-line { grid-template-columns:minmax(150px,1fr) 72px 90px minmax(130px,1fr) auto 34px; }.span-2 { grid-column:span 2; }.operation-modal { width:min(760px,calc(100vw - 30px)); }.toast-message { position:fixed; right:26px; bottom:26px; z-index:30; padding:12px 17px; background:#173c78; color:white; border-radius:10px; box-shadow:0 10px 24px rgba(16,39,79,.28); }.toast-message span { margin-right:7px; color:#86e5b8; } @media(max-width:780px) { .operations-head { padding:20px; flex-direction:column; }.operation-metrics { grid-template-columns:1fr; }.operation-metrics div { border-right:0; border-bottom:1px solid #e8eef8; }.operation-tabs,.operation-table { padding-left:16px; padding-right:16px; }.operation-table { overflow-x:auto; }.operation-table table { min-width:700px; }.line-form,.stocktake-line { grid-template-columns:1fr 90px; }.available { grid-column:span 2; }.span-2 { grid-column:span 1; } }
</style>
