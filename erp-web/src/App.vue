<script setup>
import { computed, onMounted, ref } from 'vue'

const products = ref([])
const keyword = ref('')
const categoryFilter = ref('全部分类')
const statusFilter = ref('全部状态')
const sortBy = ref('default')
const loading = ref(false)
const submitting = ref(false)
const error = ref('')
const notice = ref('')

const showForm = ref(false)
const editingId = ref(null)
const showDetails = ref(false)
const selectedProduct = ref(null)
const showRestock = ref(false)
const restockProduct = ref(null)
const restockAmount = ref(10)

const emptyForm = () => ({
  sku: '',
  name: '',
  category: '办公耗材',
  unit: '个',
  price: 0,
  stock: 0,
  safetyStock: 0,
  status: '启用'
})

const form = ref(emptyForm())

const activeModule = ref('products')
const purchaseOrders = ref([])
const suppliers = ref([])
const purchaseReceipts = ref([])
const purchaseLoading = ref(false)
const purchaseSubmitting = ref(false)
const purchaseError = ref('')
const purchaseKeyword = ref('')
const purchaseStatusFilter = ref('全部状态')
const showPurchaseForm = ref(false)
const showReceiptForm = ref(false)
const purchaseDetails = ref(null)
const emptyPurchaseForm = () => ({
  supplierId: '',
  orderDate: new Date().toISOString().slice(0, 10),
  expectedArrivalDate: new Date(Date.now() + 7 * 86400000).toISOString().slice(0, 10),
  remark: '',
  items: [{ productId: '', orderedQuantity: 10, unitPrice: 0 }]
})
const purchaseForm = ref(emptyPurchaseForm())
const receiptForm = ref({
  purchaseOrderId: null,
  warehouseId: 1,
  warehouseName: '主仓',
  stockInDate: new Date().toISOString().slice(0, 10),
  remark: '',
  items: []
})

const currentDate = new Intl.DateTimeFormat('zh-CN', {
  month: 'long',
  day: 'numeric',
  weekday: 'short'
}).format(new Date())

const currency = new Intl.NumberFormat('zh-CN', {
  style: 'currency',
  currency: 'CNY',
  minimumFractionDigits: 2
})

const categories = computed(() => [...new Set(products.value.map((product) => product.category))].sort())
const lowStockCount = computed(() => products.value.filter((product) => product.stock < product.safetyStock).length)
const totalStock = computed(() => products.value.reduce((sum, product) => sum + product.stock, 0))
const activeCount = computed(() => products.value.filter((product) => product.status === '启用').length)
const inventoryValue = computed(() => products.value.reduce(
  (sum, product) => sum + Number(product.price) * product.stock,
  0
))

const visibleProducts = computed(() => {
  const query = keyword.value.trim().toLowerCase()
  const result = products.value.filter((product) => {
    const matchesKeyword = !query || [product.sku, product.name, product.category]
      .some((value) => String(value ?? '').toLowerCase().includes(query))
    const matchesCategory = categoryFilter.value === '全部分类' || product.category === categoryFilter.value
    const matchesStatus = statusFilter.value === '全部状态' || product.status === statusFilter.value
    return matchesKeyword && matchesCategory && matchesStatus
  })

  return [...result].sort((left, right) => {
    if (sortBy.value === 'stockAsc') return left.stock - right.stock
    if (sortBy.value === 'stockDesc') return right.stock - left.stock
    if (sortBy.value === 'priceDesc') return Number(right.price) - Number(left.price)
    if (sortBy.value === 'name') return left.name.localeCompare(right.name, 'zh-CN')
    const leftWarning = left.stock < left.safetyStock ? 0 : 1
    const rightWarning = right.stock < right.safetyStock ? 0 : 1
    return leftWarning - rightWarning || left.id - right.id
  })
})

const filteredPurchaseOrders = computed(() => {
  const query = purchaseKeyword.value.trim().toLowerCase()
  return purchaseOrders.value.filter((order) => {
    const matchesKeyword = !query || order.orderNo.toLowerCase().includes(query)
      || order.supplierName.toLowerCase().includes(query)
    const matchesStatus = purchaseStatusFilter.value === '全部状态'
      || order.status === purchaseStatusFilter.value
    return matchesKeyword && matchesStatus
  })
})

const purchasePendingCount = computed(() => purchaseOrders.value.filter((order) => order.status === '待审核').length)
const purchaseReceivingCount = computed(() => purchaseOrders.value.filter((order) => order.status === '已审核').length)
const purchaseTotalAmount = computed(() => purchaseOrders.value.reduce((sum, order) => sum + Number(order.totalAmount), 0))
const purchaseProductOptions = computed(() => products.value.filter((product) => product.status === '启用'))

function formatCurrency(value) {
  return currency.format(Number(value))
}

function stockState(product) {
  if (product.stock === 0) return { label: '已缺货', className: 'critical' }
  if (product.stock < product.safetyStock) return { label: '库存偏低', className: 'low' }
  return { label: '库存健康', className: 'healthy' }
}

function stockProgress(product) {
  if (product.safetyStock <= 0) return 100
  return Math.min(100, Math.round((product.stock / product.safetyStock) * 100))
}

function productTone(product) {
  return `tone-${(product.id ?? 0) % 4}`
}

function showNotice(message) {
  notice.value = message
  window.setTimeout(() => {
    if (notice.value === message) notice.value = ''
  }, 2800)
}

async function responseError(response, fallback) {
  const body = await response.json().catch(() => ({}))
  return body.detail || body.message || `${fallback}（HTTP ${response.status}）`
}

async function loadProducts() {
  loading.value = true
  error.value = ''
  try {
    const response = await fetch('/api/products')
    if (!response.ok) throw new Error(await responseError(response, '无法读取商品数据'))
    products.value = await response.json()
    if (selectedProduct.value) {
      selectedProduct.value = products.value.find((item) => item.id === selectedProduct.value.id) ?? null
      if (!selectedProduct.value) showDetails.value = false
    }
  } catch (exception) {
    error.value = exception.message || '后端服务未启动。请先运行 erp-server，再刷新页面。'
  } finally {
    loading.value = false
  }
}

async function loadProcurement() {
  purchaseLoading.value = true
  purchaseError.value = ''
  try {
    const [ordersResponse, suppliersResponse, receiptsResponse] = await Promise.all([
      fetch('/api/purchase-orders'),
      fetch('/api/suppliers'),
      fetch('/api/purchase-receipts')
    ])
    if (!ordersResponse.ok) throw new Error(await responseError(ordersResponse, '无法读取采购订单'))
    if (!suppliersResponse.ok) throw new Error(await responseError(suppliersResponse, '无法读取供应商'))
    if (!receiptsResponse.ok) throw new Error(await responseError(receiptsResponse, '无法读取采购入库'))
    purchaseOrders.value = await ordersResponse.json()
    suppliers.value = await suppliersResponse.json()
    purchaseReceipts.value = await receiptsResponse.json()
  } catch (exception) {
    purchaseError.value = exception.message || '采购服务未启动，请先运行后端。'
  } finally {
    purchaseLoading.value = false
  }
}

function switchModule(module) {
  activeModule.value = module
  if (module === 'purchases' && purchaseOrders.value.length === 0) loadProcurement()
}

function openPurchaseCreate() {
  purchaseForm.value = emptyPurchaseForm()
  purchaseError.value = ''
  showPurchaseForm.value = true
}

function addPurchaseItem() {
  purchaseForm.value.items.push({ productId: '', orderedQuantity: 10, unitPrice: 0 })
}

function removePurchaseItem(index) {
  if (purchaseForm.value.items.length > 1) purchaseForm.value.items.splice(index, 1)
}

function selectedPurchaseProduct(item) {
  return purchaseProductOptions.value.find((product) => product.id === Number(item.productId))
}

function fillPurchasePrice(item) {
  const product = selectedPurchaseProduct(item)
  if (product && (!item.unitPrice || Number(item.unitPrice) === 0)) item.unitPrice = Number(product.price)
}

function purchaseFormAmount(item) {
  return Number(item.orderedQuantity || 0) * Number(item.unitPrice || 0)
}

function purchaseFormTotal() {
  return purchaseForm.value.items.reduce((sum, item) => sum + purchaseFormAmount(item), 0)
}

function receiptTotal() {
  return receiptForm.value.items.reduce((sum, item) => sum + Number(item.receivedQuantity || 0), 0)
}

async function savePurchaseOrder() {
  purchaseSubmitting.value = true
  purchaseError.value = ''
  try {
    const response = await fetch('/api/purchase-orders', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({
        supplierId: Number(purchaseForm.value.supplierId),
        orderDate: purchaseForm.value.orderDate,
        expectedArrivalDate: purchaseForm.value.expectedArrivalDate || null,
        remark: purchaseForm.value.remark,
        items: purchaseForm.value.items.map((item) => ({
          productId: Number(item.productId),
          orderedQuantity: Number(item.orderedQuantity),
          unitPrice: Number(item.unitPrice)
        }))
      })
    })
    if (!response.ok) throw new Error(await responseError(response, '采购订单保存失败'))
    await loadProcurement()
    showPurchaseForm.value = false
    showNotice('采购订单草稿已创建')
  } catch (exception) {
    purchaseError.value = exception.message
  } finally {
    purchaseSubmitting.value = false
  }
}

async function purchaseAction(order, action, comment = null) {
  const messages = { submit: '提交审核', approve: '审核通过', reject: '驳回', void: '作废' }
  if (!window.confirm(`确定要${messages[action]}采购单 ${order.orderNo} 吗？`)) return
  purchaseError.value = ''
  try {
    const response = await fetch(`/api/purchase-orders/${order.id}/${action}`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: action === 'reject' || action === 'approve' || action === 'void'
        ? JSON.stringify({ comment: comment || (action === 'reject' ? window.prompt('请输入驳回原因') : '') })
        : undefined
    })
    if (!response.ok) throw new Error(await responseError(response, `${messages[action]}失败`))
    await loadProcurement()
    showNotice(`采购单已${messages[action]}`)
  } catch (exception) {
    purchaseError.value = exception.message
  }
}

function openPurchaseDetails(order) {
  purchaseDetails.value = order
}

function openReceipt(order) {
  receiptForm.value = {
    purchaseOrderId: order.id,
    warehouseId: 1,
    warehouseName: '主仓',
    stockInDate: new Date().toISOString().slice(0, 10),
    remark: '',
    items: order.items.filter((item) => item.pendingQuantity > 0).map((item) => ({
      purchaseOrderItemId: item.id,
      productId: item.productId,
      productName: item.productName,
      pendingQuantity: item.pendingQuantity,
      receivedQuantity: item.pendingQuantity
    }))
  }
  purchaseDetails.value = null
  showReceiptForm.value = true
}

async function saveReceipt() {
  purchaseSubmitting.value = true
  purchaseError.value = ''
  try {
    const response = await fetch('/api/purchase-receipts', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({
        purchaseOrderId: receiptForm.value.purchaseOrderId,
        warehouseId: Number(receiptForm.value.warehouseId),
        warehouseName: receiptForm.value.warehouseName,
        stockInDate: receiptForm.value.stockInDate,
        remark: receiptForm.value.remark,
        items: receiptForm.value.items.map((item) => ({
          purchaseOrderItemId: item.purchaseOrderItemId,
          receivedQuantity: Number(item.receivedQuantity)
        }))
      })
    })
    if (!response.ok) throw new Error(await responseError(response, '入库单创建失败'))
    const draft = await response.json()
    const confirmResponse = await fetch(`/api/purchase-receipts/${draft.id}/confirm`, { method: 'POST' })
    if (!confirmResponse.ok) throw new Error(await responseError(confirmResponse, '入库确认失败'))
    await loadProcurement()
    await loadProducts()
    showReceiptForm.value = false
    showNotice('采购入库已确认，库存已更新')
  } catch (exception) {
    purchaseError.value = exception.message
  } finally {
    purchaseSubmitting.value = false
  }
}

function formatDate(value) {
  return value ? String(value).replaceAll('-', '/') : '-'
}

function openCreate() {
  editingId.value = null
  form.value = emptyForm()
  error.value = ''
  showForm.value = true
}

function openEdit(product) {
  editingId.value = product.id
  form.value = { ...product }
  error.value = ''
  showDetails.value = false
  showForm.value = true
}

function closeForm() {
  showForm.value = false
  error.value = ''
}

function productPayload(source) {
  return {
    sku: source.sku,
    name: source.name,
    category: source.category,
    unit: source.unit,
    price: Number(source.price),
    stock: Number(source.stock),
    safetyStock: Number(source.safetyStock),
    status: source.status
  }
}

async function saveProduct() {
  error.value = ''
  submitting.value = true
  const isEditing = editingId.value !== null
  try {
    const response = await fetch(isEditing ? `/api/products/${editingId.value}` : '/api/products', {
      method: isEditing ? 'PUT' : 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(productPayload(form.value))
    })
    if (!response.ok) throw new Error(await responseError(response, '保存失败，请检查输入内容'))
    await loadProducts()
    showForm.value = false
    showNotice(isEditing ? '商品信息已更新' : '商品已成功创建')
  } catch (exception) {
    error.value = exception.message
  } finally {
    submitting.value = false
  }
}

function openProductDetails(product) {
  selectedProduct.value = product
  showDetails.value = true
}

function openRestockDialog(product) {
  restockProduct.value = product
  restockAmount.value = Math.max(10, product.safetyStock - product.stock)
  showRestock.value = true
}

async function submitRestock() {
  if (!restockProduct.value || restockAmount.value <= 0) return
  submitting.value = true
  error.value = ''
  try {
    const updated = {
      ...restockProduct.value,
      stock: restockProduct.value.stock + Number(restockAmount.value)
    }
    const response = await fetch(`/api/products/${restockProduct.value.id}`, {
      method: 'PUT',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(productPayload(updated))
    })
    if (!response.ok) throw new Error(await responseError(response, '补货失败'))
    await loadProducts()
    showRestock.value = false
    showNotice(`已为 ${restockProduct.value.name} 入库 ${restockAmount.value} ${restockProduct.value.unit}`)
  } catch (exception) {
    error.value = exception.message
  } finally {
    submitting.value = false
  }
}

async function toggleStatus(product) {
  error.value = ''
  const updated = { ...product, status: product.status === '启用' ? '停用' : '启用' }
  try {
    const response = await fetch(`/api/products/${product.id}`, {
      method: 'PUT',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(productPayload(updated))
    })
    if (!response.ok) throw new Error(await responseError(response, '状态更新失败'))
    await loadProducts()
    showNotice(`${product.name} 已${updated.status}`)
  } catch (exception) {
    error.value = exception.message
  }
}

async function duplicateProduct(product) {
  error.value = ''
  const suffix = Date.now().toString().slice(-4)
  const copy = {
    ...product,
    sku: `${product.sku}-C${suffix}`.slice(0, 32),
    name: `${product.name}（副本）`
  }
  try {
    const response = await fetch('/api/products', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(productPayload(copy))
    })
    if (!response.ok) throw new Error(await responseError(response, '复制商品失败'))
    await loadProducts()
    showNotice('商品副本已创建')
  } catch (exception) {
    error.value = exception.message
  }
}

async function removeProduct(product) {
  if (!window.confirm(`确定删除“${product.name}”吗？此操作无法撤销。`)) return
  error.value = ''
  try {
    const response = await fetch(`/api/products/${product.id}`, { method: 'DELETE' })
    if (!response.ok) throw new Error(await responseError(response, '删除失败'))
    await loadProducts()
    showNotice('商品已删除')
  } catch (exception) {
    error.value = exception.message
  }
}

function resetFilters() {
  keyword.value = ''
  categoryFilter.value = '全部分类'
  statusFilter.value = '全部状态'
  sortBy.value = 'default'
}

function exportCsv() {
  const escapeCell = (value) => `"${String(value).replaceAll('"', '""')}"`
  const header = ['商品编码', '商品名称', '分类', '单位', '销售单价', '当前库存', '安全库存', '状态']
  const rows = visibleProducts.value.map((product) => [
    product.sku,
    product.name,
    product.category,
    product.unit,
    Number(product.price).toFixed(2),
    product.stock,
    product.safetyStock,
    product.status
  ])
  const csv = `\uFEFF${[header, ...rows].map((row) => row.map(escapeCell).join(',')).join('\r\n')}`
  const url = URL.createObjectURL(new Blob([csv], { type: 'text/csv;charset=utf-8' }))
  const link = document.createElement('a')
  link.href = url
  link.download = `商品档案_${new Date().toISOString().slice(0, 10)}.csv`
  link.click()
  URL.revokeObjectURL(url)
  showNotice(`已导出 ${rows.length} 条商品数据`)
}

onMounted(loadProducts)
</script>

<template>
  <div class="app-frame">
    <aside class="sidebar">
      <div class="brand">
        <span class="brand-mark">N</span>
        <div><strong>NOVA ERP</strong><small>企业资源管理平台</small></div>
      </div>

      <nav class="nav-menu">
        <p class="nav-label">工作台</p>
        <a href="#" class="nav-item"><span>⌂</span>经营概览</a>
        <p class="nav-label">业务管理</p>
        <a href="#" :class="['nav-item', { active: activeModule === 'products' }]" @click.prevent="switchModule('products')"><span>◇</span>商品档案<em>{{ products.length }}</em></a>
        <a href="#" :class="['nav-item', { active: activeModule === 'purchases' }]" @click.prevent="switchModule('purchases')"><span>▤</span>采购管理<em>{{ purchaseOrders.length }}</em></a>
        <a href="#" class="nav-item muted"><span>▣</span>销售管理<small>待建设</small></a>
        <a href="#" class="nav-item muted"><span>▥</span>仓储管理<small>待建设</small></a>
        <p class="nav-label">分析与设置</p>
        <a href="#" class="nav-item muted"><span>⌁</span>业务报表<small>待建设</small></a>
        <a href="#" class="nav-item muted"><span>⚙</span>系统设置<small>待建设</small></a>
      </nav>

      <div class="sidebar-status">
        <span class="status-dot"></span>
        <div><strong>开发环境</strong><small>Spring Boot 服务在线</small></div>
      </div>
    </aside>

    <main class="workspace">
      <header class="topbar">
        <div class="breadcrumb"><span>进销存管理</span><b>/</b><strong>商品档案</strong></div>
        <div class="topbar-right">
          <span class="date-label">{{ currentDate }}</span>
          <button class="round-button" title="通知">◔<i v-if="lowStockCount"></i></button>
          <div class="user-card"><span>管</span><div><strong>系统管理员</strong><small>演示账户</small></div></div>
        </div>
      </header>

      <div v-if="activeModule === 'products'" class="page-content">
        <section class="page-heading">
          <div>
            <p class="eyebrow">INVENTORY · PRODUCT MASTER</p>
            <h1>商品档案</h1>
            <p>统一管理商品基础信息、销售价格与安全库存规则。</p>
          </div>
          <div class="heading-actions">
            <button class="secondary-button" @click="exportCsv"><span>⇩</span> 导出 CSV</button>
            <button class="primary-button" @click="openCreate"><span>＋</span> 新增商品</button>
          </div>
        </section>

        <section class="metrics">
          <article class="metric-card blue">
            <div class="metric-icon">品</div>
            <div><span>商品总数</span><strong>{{ products.length }}</strong><small>{{ activeCount }} 个正在启用</small></div>
            <b>+{{ products.length }}</b>
          </article>
          <article class="metric-card violet">
            <div class="metric-icon">库</div>
            <div><span>库存总量</span><strong>{{ totalStock.toLocaleString() }}</strong><small>全部计量单位合计</small></div>
            <b>实时</b>
          </article>
          <article class="metric-card green">
            <div class="metric-icon">¥</div>
            <div><span>库存货值</span><strong>{{ formatCurrency(inventoryValue) }}</strong><small>按当前销售价估算</small></div>
            <b>估值</b>
          </article>
          <article class="metric-card orange">
            <div class="metric-icon">!</div>
            <div><span>库存预警</span><strong>{{ lowStockCount }}</strong><small>{{ lowStockCount ? '需要及时安排补货' : '库存状态全部正常' }}</small></div>
            <b :class="{ alert: lowStockCount }">{{ lowStockCount ? '待处理' : '正常' }}</b>
          </article>
        </section>

        <section class="content-card">
          <div class="list-heading">
            <div><h2>商品列表</h2><p>共 {{ products.length }} 个商品，当前筛选出 {{ visibleProducts.length }} 条</p></div>
            <button class="refresh-button" :disabled="loading" @click="loadProducts">↻ <span>{{ loading ? '刷新中' : '刷新数据' }}</span></button>
          </div>

          <div class="filter-bar">
            <label class="search-box"><span>⌕</span><input v-model="keyword" placeholder="搜索商品名称、编码或分类" /></label>
            <select v-model="categoryFilter"><option>全部分类</option><option v-for="category in categories" :key="category">{{ category }}</option></select>
            <select v-model="statusFilter"><option>全部状态</option><option>启用</option><option>停用</option></select>
            <select v-model="sortBy"><option value="default">预警优先</option><option value="name">按名称排序</option><option value="stockAsc">库存从低到高</option><option value="stockDesc">库存从高到低</option><option value="priceDesc">价格从高到低</option></select>
            <button class="text-button" @click="resetFilters">重置</button>
          </div>

          <div v-if="error" class="message error-message"><span>!</span><p>{{ error }}</p><button @click="error = ''">×</button></div>

          <div class="table-wrap">
            <table>
              <thead><tr><th>商品信息</th><th>分类</th><th>销售单价</th><th>库存状态</th><th>启用状态</th><th class="action-column">操作</th></tr></thead>
              <tbody>
                <tr v-if="loading"><td colspan="6" class="empty-state"><span class="spinner"></span><strong>正在读取商品数据</strong><small>请稍候…</small></td></tr>
                <tr v-else-if="visibleProducts.length === 0"><td colspan="6" class="empty-state"><span class="empty-icon">⌕</span><strong>没有找到匹配的商品</strong><small>请调整搜索条件或重置筛选</small></td></tr>
                <tr v-for="product in visibleProducts" v-else :key="product.id">
                  <td><div class="product-cell"><span :class="['product-avatar', productTone(product)]">{{ product.name.slice(0, 1) }}</span><div><strong>{{ product.name }}</strong><code>{{ product.sku }} · {{ product.unit }}</code></div></div></td>
                  <td><span class="category-chip">{{ product.category }}</span></td>
                  <td><strong class="price">{{ formatCurrency(product.price) }}</strong></td>
                  <td>
                    <div class="stock-cell">
                      <div><strong>{{ product.stock }} {{ product.unit }}</strong><span :class="['stock-label', stockState(product).className]">{{ stockState(product).label }}</span></div>
                      <div class="stock-track"><i :class="stockState(product).className" :style="{ width: `${stockProgress(product)}%` }"></i></div>
                      <small>安全库存 {{ product.safetyStock }} {{ product.unit }}</small>
                    </div>
                  </td>
                  <td><button :class="['status-switch', { off: product.status !== '启用' }]" @click="toggleStatus(product)"><i></i>{{ product.status }}</button></td>
                  <td class="row-actions">
                    <button @click="openProductDetails(product)">详情</button>
                    <button @click="openEdit(product)">编辑</button>
                    <button class="restock-button" @click="openRestockDialog(product)">补货</button>
                    <details class="row-menu"><summary>•••</summary><div><button @click="duplicateProduct(product)">复制商品</button><button class="danger" @click="removeProduct(product)">删除商品</button></div></details>
                  </td>
                </tr>
              </tbody>
            </table>
          </div>

          <footer class="list-footer"><span>显示 {{ visibleProducts.length }} / {{ products.length }} 条记录</span><span>数据源：Spring Boot 内存服务 · 重启后恢复初始数据</span></footer>
        </section>
      </div>

      <div v-else class="page-content purchase-page">
        <section class="page-heading">
          <div>
            <p class="eyebrow">PROCUREMENT · PURCHASE ORDER</p>
            <h1>采购管理</h1>
            <p>从供应商下单到到货入库，全程跟踪采购进度与库存补给。</p>
          </div>
          <div class="heading-actions">
            <button class="secondary-button" @click="loadProcurement" :disabled="purchaseLoading"><span>↻</span> 刷新数据</button>
            <button class="primary-button" @click="openPurchaseCreate"><span>＋</span> 新建采购单</button>
          </div>
        </section>

        <section class="metrics">
          <article class="metric-card blue"><div class="metric-icon">单</div><div><span>采购订单</span><strong>{{ purchaseOrders.length }}</strong><small>当前可见订单</small></div><b>V1</b></article>
          <article class="metric-card orange"><div class="metric-icon">审</div><div><span>待审核</span><strong>{{ purchasePendingCount }}</strong><small>等待主管处理</small></div><b :class="{ alert: purchasePendingCount }">待办</b></article>
          <article class="metric-card violet"><div class="metric-icon">入</div><div><span>待入库</span><strong>{{ purchaseReceivingCount }}</strong><small>已有审核结果</small></div><b>跟进</b></article>
          <article class="metric-card green"><div class="metric-icon">¥</div><div><span>采购总额</span><strong>{{ formatCurrency(purchaseTotalAmount) }}</strong><small>当前列表订单合计</small></div><b>估算</b></article>
        </section>

        <section class="content-card">
          <div class="list-heading"><div><h2>采购订单</h2><p>订单审核通过后，仓库即可按实际到货分批入库。</p></div><span class="module-badge">{{ purchaseReceipts.length }} 张入库单</span></div>
          <div class="filter-bar purchase-filter"><label class="search-box"><span>⌕</span><input v-model="purchaseKeyword" placeholder="搜索采购单号或供应商" /></label><select v-model="purchaseStatusFilter"><option>全部状态</option><option>草稿</option><option>待审核</option><option>已审核</option><option>已完成</option><option>已驳回</option><option>已作废</option></select><button class="text-button" @click="purchaseKeyword = ''; purchaseStatusFilter = '全部状态'">重置</button></div>
          <div v-if="purchaseError" class="message error-message"><span>!</span><p>{{ purchaseError }}</p><button @click="purchaseError = ''">×</button></div>
          <div class="table-wrap">
            <table class="purchase-table">
              <thead><tr><th>采购单号</th><th>供应商</th><th>订单日期</th><th>采购金额</th><th>到货进度</th><th>状态</th><th class="action-column">操作</th></tr></thead>
              <tbody>
                <tr v-if="purchaseLoading"><td colspan="7" class="empty-state"><span class="spinner"></span><strong>正在读取采购数据</strong><small>请稍候…</small></td></tr>
                <tr v-else-if="filteredPurchaseOrders.length === 0"><td colspan="7" class="empty-state"><span class="empty-icon">⌕</span><strong>暂无匹配采购订单</strong><small>可以新建一张采购订单开始流程</small></td></tr>
                <tr v-for="order in filteredPurchaseOrders" v-else :key="order.id">
                  <td><div class="purchase-no"><strong>{{ order.orderNo }}</strong><small>{{ order.totalQuantity }} 件商品</small></div></td>
                  <td><strong>{{ order.supplierName }}</strong></td>
                  <td>{{ formatDate(order.orderDate) }}</td>
                  <td><strong class="price">{{ formatCurrency(order.totalAmount) }}</strong></td>
                  <td><div class="purchase-progress"><div><span>{{ order.receivedQuantity }} / {{ order.totalQuantity }}</span><b>{{ order.progress }}%</b></div><i><em :style="{ width: `${order.progress}%` }"></em></i></div></td>
                  <td><span :class="['order-status', `status-${order.status}`]">{{ order.status }}</span></td>
                  <td class="row-actions purchase-actions"><button @click="openPurchaseDetails(order)">详情</button><button v-if="order.status === '草稿' || order.status === '已驳回'" @click="purchaseAction(order, 'submit')">提交</button><button v-if="order.status === '待审核'" @click="purchaseAction(order, 'approve')">审核</button><button v-if="order.status === '待审核'" class="danger-text" @click="purchaseAction(order, 'reject')">驳回</button><button v-if="order.status === '已审核' && order.pendingQuantity > 0" class="restock-button" @click="openReceipt(order)">入库</button></td>
                </tr>
              </tbody>
            </table>
          </div>
          <footer class="list-footer"><span>显示 {{ filteredPurchaseOrders.length }} / {{ purchaseOrders.length }} 条记录</span><span>库存变化由确认入库单驱动 · 当前为可运行演示版</span></footer>
        </section>
      </div>
    </main>

    <div v-if="showForm" class="modal-backdrop" @click.self="closeForm">
      <form class="modal product-modal" @submit.prevent="saveProduct">
        <div class="modal-title"><div><p class="eyebrow">PRODUCT MASTER</p><h2>{{ editingId ? '编辑商品档案' : '新建商品档案' }}</h2><p>完善商品基础信息和库存规则。</p></div><button type="button" class="icon-button" @click="closeForm">×</button></div>
        <div class="form-grid">
          <label>商品编码 <em>*</em><input v-model.trim="form.sku" required maxlength="32" placeholder="例如 SP-3001" /></label>
          <label>商品名称 <em>*</em><input v-model.trim="form.name" required maxlength="80" placeholder="请输入商品名称" /></label>
          <label>商品分类 <em>*</em><input v-model.trim="form.category" required maxlength="40" list="category-options" /><datalist id="category-options"><option v-for="category in categories" :key="category" :value="category" /></datalist></label>
          <label>计量单位 <em>*</em><select v-model="form.unit" required><option>个</option><option>件</option><option>包</option><option>箱</option><option>台</option><option>套</option><option>支</option></select></label>
          <label>销售单价 <em>*</em><div class="input-prefix"><span>¥</span><input v-model.number="form.price" min="0" step="0.01" type="number" required /></div></label>
          <label>当前库存 <em>*</em><input v-model.number="form.stock" min="0" max="999999" type="number" required /></label>
          <label>安全库存 <em>*</em><input v-model.number="form.safetyStock" min="0" type="number" required /></label>
          <label>启用状态 <em>*</em><select v-model="form.status"><option>启用</option><option>停用</option></select></label>
        </div>
        <div v-if="error" class="message error-message compact"><span>!</span><p>{{ error }}</p></div>
        <div class="modal-actions"><button type="button" class="secondary-button" @click="closeForm">取消</button><button type="submit" class="primary-button" :disabled="submitting">{{ submitting ? '保存中…' : '保存商品' }}</button></div>
      </form>
    </div>

    <div v-if="showRestock" class="modal-backdrop" @click.self="showRestock = false">
      <form class="modal restock-modal" @submit.prevent="submitRestock">
        <div class="modal-title"><div><p class="eyebrow">QUICK STOCK-IN</p><h2>快速补货</h2></div><button type="button" class="icon-button" @click="showRestock = false">×</button></div>
        <div class="restock-product"><span :class="['product-avatar large', productTone(restockProduct)]">{{ restockProduct.name.slice(0, 1) }}</span><div><strong>{{ restockProduct.name }}</strong><small>{{ restockProduct.sku }} · 当前库存 {{ restockProduct.stock }} {{ restockProduct.unit }}</small></div></div>
        <label class="restock-input">本次入库数量<div><input v-model.number="restockAmount" type="number" min="1" required /><span>{{ restockProduct.unit }}</span></div></label>
        <div class="restock-result"><span>入库后预计库存</span><strong>{{ restockProduct.stock + Number(restockAmount || 0) }} {{ restockProduct.unit }}</strong></div>
        <div class="modal-actions"><button type="button" class="secondary-button" @click="showRestock = false">取消</button><button type="submit" class="primary-button" :disabled="submitting">确认入库</button></div>
      </form>
    </div>

    <div v-if="showDetails && selectedProduct" class="drawer-backdrop" @click.self="showDetails = false">
      <aside class="detail-drawer">
        <div class="drawer-header"><div><p class="eyebrow">PRODUCT DETAILS</p><h2>商品详情</h2></div><button class="icon-button" @click="showDetails = false">×</button></div>
        <div class="detail-identity"><span :class="['product-avatar hero-avatar', productTone(selectedProduct)]">{{ selectedProduct.name.slice(0, 1) }}</span><div><h3>{{ selectedProduct.name }}</h3><code>{{ selectedProduct.sku }}</code><span :class="['detail-status', { off: selectedProduct.status !== '启用' }]">{{ selectedProduct.status }}</span></div></div>
        <div class="detail-summary"><article><span>当前库存</span><strong>{{ selectedProduct.stock }} {{ selectedProduct.unit }}</strong></article><article><span>库存货值</span><strong>{{ formatCurrency(Number(selectedProduct.price) * selectedProduct.stock) }}</strong></article></div>
        <dl class="detail-list"><div><dt>商品分类</dt><dd>{{ selectedProduct.category }}</dd></div><div><dt>计量单位</dt><dd>{{ selectedProduct.unit }}</dd></div><div><dt>销售单价</dt><dd>{{ formatCurrency(selectedProduct.price) }}</dd></div><div><dt>安全库存</dt><dd>{{ selectedProduct.safetyStock }} {{ selectedProduct.unit }}</dd></div><div><dt>库存状态</dt><dd><span :class="['stock-label', stockState(selectedProduct).className]">{{ stockState(selectedProduct).label }}</span></dd></div></dl>
        <div class="drawer-note"><strong>数据说明</strong><p>当前演示版本使用内存数据，后端重启后会恢复初始商品。下一迭代将接入 MySQL。</p></div>
        <div class="drawer-actions"><button class="secondary-button" @click="openRestockDialog(selectedProduct); showDetails = false">快速补货</button><button class="primary-button" @click="openEdit(selectedProduct)">编辑档案</button></div>
      </aside>
    </div>

    <div v-if="showPurchaseForm" class="modal-backdrop" @click.self="showPurchaseForm = false">
      <form class="modal purchase-modal" @submit.prevent="savePurchaseOrder">
        <div class="modal-title"><div><p class="eyebrow">PROCUREMENT · ORDER</p><h2>新建采购订单</h2><p>选择供应商和商品，保存后可提交主管审核。</p></div><button type="button" class="icon-button" @click="showPurchaseForm = false">×</button></div>
        <div class="form-grid">
          <label>供应商 <em>*</em><select v-model="purchaseForm.supplierId" required><option value="" disabled>请选择供应商</option><option v-for="supplier in suppliers" :key="supplier.id" :value="supplier.id">{{ supplier.name }}</option></select></label>
          <label>订单日期 <em>*</em><input v-model="purchaseForm.orderDate" type="date" required /></label>
          <label>预计到货日期<input v-model="purchaseForm.expectedArrivalDate" type="date" /></label>
          <label>备注<input v-model.trim="purchaseForm.remark" maxlength="500" placeholder="例如：安全库存补货" /></label>
        </div>
        <div class="purchase-lines-heading"><strong>采购明细</strong><button type="button" class="text-button" @click="addPurchaseItem">＋ 添加商品</button></div>
        <div class="purchase-line-list">
          <div v-for="(item, index) in purchaseForm.items" :key="index" class="purchase-line">
            <select v-model="item.productId" required @change="fillPurchasePrice(item)"><option value="" disabled>选择商品</option><option v-for="product in purchaseProductOptions" :key="product.id" :value="product.id">{{ product.name }} · {{ product.sku }}</option></select>
            <input v-model.number="item.orderedQuantity" type="number" min="1" required placeholder="数量" />
            <div class="input-prefix"><span>¥</span><input v-model.number="item.unitPrice" type="number" min="0" step="0.01" required /></div>
            <strong>{{ formatCurrency(purchaseFormAmount(item)) }}</strong>
            <button type="button" class="line-remove" @click="removePurchaseItem(index)" :disabled="purchaseForm.items.length === 1">×</button>
          </div>
        </div>
        <div class="purchase-total"><span>订单预计金额</span><strong>{{ formatCurrency(purchaseFormTotal()) }}</strong></div>
        <div v-if="purchaseError" class="message error-message compact"><span>!</span><p>{{ purchaseError }}</p></div>
        <div class="modal-actions"><button type="button" class="secondary-button" @click="showPurchaseForm = false">取消</button><button type="submit" class="primary-button" :disabled="purchaseSubmitting">{{ purchaseSubmitting ? '保存中…' : '保存采购草稿' }}</button></div>
      </form>
    </div>

    <div v-if="showReceiptForm" class="modal-backdrop" @click.self="showReceiptForm = false">
      <form class="modal receipt-modal" @submit.prevent="saveReceipt">
        <div class="modal-title"><div><p class="eyebrow">WAREHOUSE · STOCK IN</p><h2>采购入库</h2><p>确认后库存将增加，并生成采购入库流水。</p></div><button type="button" class="icon-button" @click="showReceiptForm = false">×</button></div>
        <div class="form-grid"><label>入库仓库<select v-model="receiptForm.warehouseName"><option>主仓</option></select></label><label>入库日期<input v-model="receiptForm.stockInDate" type="date" required /></label></div>
        <div class="purchase-lines-heading"><strong>本次实收明细</strong><span class="module-badge">最多 {{ receiptForm.items.reduce((sum, item) => sum + item.pendingQuantity, 0) }} 件</span></div>
        <div class="receipt-line-list"><div v-for="item in receiptForm.items" :key="item.purchaseOrderItemId" class="receipt-line"><div><strong>{{ item.productName }}</strong><small>剩余未入库 {{ item.pendingQuantity }}</small></div><input v-model.number="item.receivedQuantity" type="number" min="1" :max="item.pendingQuantity" required /><span>件</span></div></div>
        <div class="purchase-total"><span>本次入库数量</span><strong>{{ receiptTotal() }} 件</strong></div>
        <div v-if="purchaseError" class="message error-message compact"><span>!</span><p>{{ purchaseError }}</p></div>
        <div class="modal-actions"><button type="button" class="secondary-button" @click="showReceiptForm = false">取消</button><button type="submit" class="primary-button" :disabled="purchaseSubmitting">{{ purchaseSubmitting ? '确认中…' : '确认入库' }}</button></div>
      </form>
    </div>

    <div v-if="purchaseDetails" class="drawer-backdrop" @click.self="purchaseDetails = null">
      <aside class="detail-drawer purchase-drawer">
        <div class="drawer-header"><div><p class="eyebrow">PURCHASE ORDER DETAILS</p><h2>采购单详情</h2></div><button class="icon-button" @click="purchaseDetails = null">×</button></div>
        <div class="purchase-detail-title"><strong>{{ purchaseDetails.orderNo }}</strong><span :class="['order-status', `status-${purchaseDetails.status}`]">{{ purchaseDetails.status }}</span></div>
        <dl class="detail-list"><div><dt>供应商</dt><dd>{{ purchaseDetails.supplierName }}</dd></div><div><dt>订单日期</dt><dd>{{ formatDate(purchaseDetails.orderDate) }}</dd></div><div><dt>预计到货</dt><dd>{{ formatDate(purchaseDetails.expectedArrivalDate) }}</dd></div><div><dt>采购金额</dt><dd>{{ formatCurrency(purchaseDetails.totalAmount) }}</dd></div><div><dt>备注</dt><dd>{{ purchaseDetails.remark || '—' }}</dd></div></dl>
        <div class="detail-items"><strong>商品明细</strong><div v-for="item in purchaseDetails.items" :key="item.id"><span>{{ item.productName }}<small>{{ item.sku }} · {{ item.unit }}</small></span><b>{{ item.receivedQuantity }} / {{ item.orderedQuantity }}</b></div></div>
        <div class="drawer-actions"><button v-if="purchaseDetails.status === '已审核' && purchaseDetails.pendingQuantity > 0" class="primary-button" @click="openReceipt(purchaseDetails)">创建入库单</button><button v-if="purchaseDetails.status === '待审核'" class="secondary-button" @click="purchaseAction(purchaseDetails, 'approve'); purchaseDetails = null">审核通过</button></div>
      </aside>
    </div>

    <transition name="toast"><div v-if="notice" class="toast-message"><span>✓</span>{{ notice }}</div></transition>
  </div>
</template>
