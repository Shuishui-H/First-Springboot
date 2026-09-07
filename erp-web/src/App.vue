<script setup>
import { computed, onMounted, ref } from 'vue'
import OverviewView from './OverviewView.vue'
import WarehouseView from './WarehouseView.vue'
import SystemSettingsView from './SystemSettingsView.vue'
import LoginView from './LoginView.vue'

const products = ref([])
const keyword = ref('')
const categoryFilter = ref('全部分类')
const statusFilter = ref('全部状态')
const sortBy = ref('default')
const loading = ref(false)
const submitting = ref(false)
const error = ref('')
const notice = ref('')
const authChecked = ref(false)
const authEnabled = ref(false)
const currentUser = ref(null)
const showChangePassword = ref(false)
const changingPassword = ref(false)
const passwordError = ref('')
const passwordForm = ref({ currentPassword: '', newPassword: '', confirmPassword: '' })

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

const activeModule = ref('overview')
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

const salesOrders = ref([])
const customers = ref([])
const stockOutOrders = ref([])
const salesReturns = ref([])
const salesLoading = ref(false)
const salesSubmitting = ref(false)
const salesError = ref('')
const salesKeyword = ref('')
const salesStatusFilter = ref('全部状态')
const showSalesForm = ref(false)
const showStockOutForm = ref(false)
const showReturnForm = ref(false)
const showCustomerForm = ref(false)
const customerEditingId = ref(null)
const salesDetails = ref(null)
const emptyCustomerForm = () => ({ code: '', name: '', contact: '', phone: '', status: '启用' })
const customerForm = ref(emptyCustomerForm())
const emptySalesForm = () => ({
  customerId: '',
  warehouseId: 1,
  orderDate: new Date().toISOString().slice(0, 10),
  requiredShipDate: new Date(Date.now() + 3 * 86400000).toISOString().slice(0, 10),
  remark: '',
  items: [{ productId: '', orderedQuantity: 1, unitPrice: 0 }]
})
const salesForm = ref(emptySalesForm())
const stockOutForm = ref({ salesOrderId: null, stockOutDate: '', remark: '', items: [] })
const returnForm = ref({ sourceStockOutId: null, returnDate: '', reason: '', remark: '', items: [] })

const reportLoading = ref(false)
const reportError = ref('')
const reportTab = ref('dashboard')
const dashboardData = ref(null)
const purchaseAnalysisData = ref(null)
const inventoryBalance = ref([])
const lowStockWarnings = ref([])
const inventoryCategoryFilter = ref('全部分类')
const reportCategories = computed(() => [...new Set(inventoryBalance.value.map((item) => item.category))].sort())
const filteredInventoryBalance = computed(() => inventoryBalance.value.filter((item) =>
  inventoryCategoryFilter.value === '全部分类' || item.category === inventoryCategoryFilter.value
))
const reportLowStockCount = computed(() => lowStockWarnings.value.length)
const purchaseSummary = computed(() => {
  const suppliers = purchaseAnalysisData.value?.suppliers ?? []
  return {
    suppliers: suppliers.length,
    orders: suppliers.reduce((sum, item) => sum + Number(item.orderCount || 0), 0),
    amount: suppliers.reduce((sum, item) => sum + Number(item.orderAmount || 0), 0),
    received: suppliers.reduce((sum, item) => sum + Number(item.receivedAmount || 0), 0)
  }
})
const inventorySummary = computed(() => ({
  products: inventoryBalance.value.length,
  stock: inventoryBalance.value.reduce((sum, item) => sum + Number(item.currentStock || 0), 0),
  value: inventoryBalance.value.reduce((sum, item) => sum + Number(item.stockValue || 0), 0),
  warnings: inventoryBalance.value.filter((item) => item.currentStock < item.safetyStock).length
}))
const warningSummary = computed(() => ({
  total: lowStockWarnings.value.length,
  out: lowStockWarnings.value.filter((item) => item.warningType === '缺货').length,
  low: lowStockWarnings.value.filter((item) => item.warningType === '库存偏低').length,
  disabled: lowStockWarnings.value.filter((item) => item.warningType === '停用商品').length
}))

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
const filteredSalesOrders = computed(() => {
  const query = salesKeyword.value.trim().toLowerCase()
  return salesOrders.value.filter((order) => {
    const matchesKeyword = !query || order.orderNo.toLowerCase().includes(query) || order.customerName.toLowerCase().includes(query)
    const matchesStatus = salesStatusFilter.value === '全部状态' || order.status === salesStatusFilter.value
    return matchesKeyword && matchesStatus
  })
})
const salesPendingCount = computed(() => salesOrders.value.filter((order) => order.status === '待审核').length)
const salesShippingCount = computed(() => salesOrders.value.filter((order) => order.status === '已审核' && salesOrderPending(order) > 0).length)
const salesTotalAmount = computed(() => salesOrders.value.reduce((sum, order) => sum + Number(order.totalAmount), 0))
const salesProductOptions = computed(() => products.value.filter((product) => product.status === '启用'))
const activeModuleLabel = computed(() => ({ products: '商品档案', purchases: '采购管理', sales: '销售管理', warehouse: '仓储管理', reports: '业务报表', settings: '系统设置' }[activeModule.value] || '经营概览'))

function formatCurrency(value) {
  return currency.format(Number(value))
}

function salesItemPending(item) {
  return Number(item.pendingQuantity ?? (Number(item.orderedQuantity || 0) - Number(item.shippedQuantity || 0)))
}

function salesOrderPending(order) {
  return Number(order.pendingQuantity ?? order.items.reduce((sum, item) => sum + salesItemPending(item), 0))
}

function salesOrderProgress(order) {
  return Number(order.progress ?? (order.totalQuantity ? Math.round(Number(order.shippedQuantity || 0) * 100 / order.totalQuantity) : 0))
}

function stockOutReturnable(item) {
  return Number(item.returnableQuantity ?? (Number(item.shippedQuantity || 0) - Number(item.returnedQuantity || 0)))
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

function can(permission) {
  return !authEnabled.value || currentUser.value?.permissions?.includes(permission)
}

async function responseError(response, fallback) {
  if (response.status === 401 && authEnabled.value) {
    currentUser.value = null
    showChangePassword.value = false
  }
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

async function loadSales() {
  salesLoading.value = true
  salesError.value = ''
  try {
    const [ordersResponse, customersResponse, stockOutResponse, returnsResponse] = await Promise.all([
      fetch('/api/sales-orders'), fetch('/api/customers'), fetch('/api/stock-out-orders'), fetch('/api/sales-returns')
    ])
    if (!ordersResponse.ok) throw new Error(await responseError(ordersResponse, '无法读取销售订单'))
    if (!customersResponse.ok) throw new Error(await responseError(customersResponse, '无法读取客户'))
    if (!stockOutResponse.ok) throw new Error(await responseError(stockOutResponse, '无法读取销售出库'))
    if (!returnsResponse.ok) throw new Error(await responseError(returnsResponse, '无法读取销售退货'))
    salesOrders.value = await ordersResponse.json()
    customers.value = await customersResponse.json()
    stockOutOrders.value = await stockOutResponse.json()
    salesReturns.value = await returnsResponse.json()
  } catch (exception) {
    salesError.value = exception.message || '销售服务未启动，请先运行后端。'
  } finally {
    salesLoading.value = false
  }
}

function openCustomerCreate() {
  customerEditingId.value = null
  customerForm.value = emptyCustomerForm()
  salesError.value = ''
  showCustomerForm.value = true
}

function openCustomerEdit(customer) {
  customerEditingId.value = customer.id
  customerForm.value = { ...customer }
  salesError.value = ''
  showCustomerForm.value = true
}

async function saveCustomer() {
  salesSubmitting.value = true
  salesError.value = ''
  try {
    const editing = customerEditingId.value !== null
    const response = await fetch(editing ? `/api/customers/${customerEditingId.value}` : '/api/customers', {
      method: editing ? 'PUT' : 'POST', headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(customerForm.value)
    })
    if (!response.ok) throw new Error(await responseError(response, '客户保存失败'))
    await loadSales()
    showCustomerForm.value = false
    showNotice(editing ? '客户档案已更新' : '客户档案已创建')
  } catch (exception) { salesError.value = exception.message }
  finally { salesSubmitting.value = false }
}

function switchModule(module) {
  activeModule.value = module
  if (module === 'purchases' && purchaseOrders.value.length === 0) loadProcurement()
  if (module === 'sales' && salesOrders.value.length === 0) loadSales()
  if (module === 'reports' && !dashboardData.value) loadReport()
}

function handleOverviewNavigate(target) {
  if (target.module === 'purchases') {
    purchaseStatusFilter.value = target.status || '全部状态'
    activeModule.value = 'purchases'
    loadProcurement()
    return
  }
  if (target.module === 'sales') {
    salesStatusFilter.value = target.status || '全部状态'
    activeModule.value = 'sales'
    loadSales()
    return
  }
  if (target.module === 'reports') {
    reportTab.value = target.tab || 'dashboard'
    activeModule.value = 'reports'
    loadReport()
    return
  }
  activeModule.value = target.module || 'overview'
}

function openSalesCreate() {
  salesForm.value = emptySalesForm()
  salesError.value = ''
  showSalesForm.value = true
}

function addSalesItem() { salesForm.value.items.push({ productId: '', orderedQuantity: 1, unitPrice: 0 }) }
function removeSalesItem(index) { if (salesForm.value.items.length > 1) salesForm.value.items.splice(index, 1) }
function selectedSalesProduct(item) { return salesProductOptions.value.find((product) => product.id === Number(item.productId)) }
function fillSalesPrice(item) {
  const product = selectedSalesProduct(item)
  if (product && (!item.unitPrice || Number(item.unitPrice) === 0)) item.unitPrice = Number(product.price)
}
function salesFormAmount(item) { return Number(item.orderedQuantity || 0) * Number(item.unitPrice || 0) }
function salesFormTotal() { return salesForm.value.items.reduce((sum, item) => sum + salesFormAmount(item), 0) }

async function saveSalesOrder() {
  salesSubmitting.value = true
  salesError.value = ''
  try {
    const response = await fetch('/api/sales-orders', {
      method: 'POST', headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({
        customerId: Number(salesForm.value.customerId), warehouseId: 1, orderDate: salesForm.value.orderDate,
        requiredShipDate: salesForm.value.requiredShipDate || null, remark: salesForm.value.remark,
        items: salesForm.value.items.map((item) => ({ productId: Number(item.productId), orderedQuantity: Number(item.orderedQuantity), unitPrice: Number(item.unitPrice) }))
      })
    })
    if (!response.ok) throw new Error(await responseError(response, '销售订单保存失败'))
    await loadSales()
    showSalesForm.value = false
    showNotice('销售订单草稿已创建')
  } catch (exception) { salesError.value = exception.message }
  finally { salesSubmitting.value = false }
}

async function salesAction(order, action) {
  const messages = { submit: '提交审核', approve: '审核通过', reject: '驳回', void: '作废' }
  if (!window.confirm(`确定要${messages[action]}销售单 ${order.orderNo} 吗？`)) return
  let comment = ''
  if (action === 'reject') comment = window.prompt('请输入驳回原因') || ''
  salesError.value = ''
  try {
    const response = await fetch(`/api/sales-orders/${order.id}/${action}`, {
      method: 'POST', headers: { 'Content-Type': 'application/json' },
      body: ['approve', 'reject', 'void'].includes(action) ? JSON.stringify({ comment }) : undefined
    })
    if (!response.ok) throw new Error(await responseError(response, `${messages[action]}失败`))
    await loadSales()
    showNotice(`销售单已${messages[action]}`)
  } catch (exception) { salesError.value = exception.message }
}

function openSalesDetails(order) { salesDetails.value = order }

function openStockOut(order) {
  stockOutForm.value = {
    salesOrderId: order.id, stockOutDate: new Date().toISOString().slice(0, 10), remark: '',
    items: order.items.filter((item) => salesItemPending(item) > 0).map((item) => ({
      salesOrderItemId: item.id, productId: item.productId, productName: item.productName, unit: item.unit,
      pendingQuantity: salesItemPending(item), availableStock: products.value.find((product) => product.id === item.productId)?.stock ?? 0,
      shippedQuantity: Math.min(salesItemPending(item), products.value.find((product) => product.id === item.productId)?.stock ?? 0)
    }))
  }
  salesDetails.value = null
  showStockOutForm.value = true
}

function stockOutTotal() { return stockOutForm.value.items.reduce((sum, item) => sum + Number(item.shippedQuantity || 0), 0) }

async function saveStockOut() {
  salesSubmitting.value = true
  salesError.value = ''
  try {
    const response = await fetch('/api/stock-out-orders', {
      method: 'POST', headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ salesOrderId: stockOutForm.value.salesOrderId, stockOutDate: stockOutForm.value.stockOutDate, remark: stockOutForm.value.remark,
        items: stockOutForm.value.items.filter((item) => Number(item.shippedQuantity) > 0).map((item) => ({ salesOrderItemId: item.salesOrderItemId, shippedQuantity: Number(item.shippedQuantity) })) })
    })
    if (!response.ok) throw new Error(await responseError(response, '出库单创建失败'))
    const draft = await response.json()
    const confirmResponse = await fetch(`/api/stock-out-orders/${draft.id}/confirm`, { method: 'POST' })
    if (!confirmResponse.ok) throw new Error(await responseError(confirmResponse, '销售出库确认失败'))
    await loadSales(); await loadProducts()
    showStockOutForm.value = false
    showNotice('销售出库已确认，库存已扣减')
  } catch (exception) { salesError.value = exception.message }
  finally { salesSubmitting.value = false }
}

function openReturn(stockOut) {
  returnForm.value = {
    sourceStockOutId: stockOut.id, returnDate: new Date().toISOString().slice(0, 10), reason: '', remark: '',
    items: stockOut.items.filter((item) => stockOutReturnable(item) > 0).map((item) => ({
      sourceStockOutItemId: item.id, productName: item.productName, returnableQuantity: stockOutReturnable(item), returnedQuantity: stockOutReturnable(item)
    }))
  }
  showReturnForm.value = true
}

function returnTotal() { return returnForm.value.items.reduce((sum, item) => sum + Number(item.returnedQuantity || 0), 0) }

async function saveReturn() {
  salesSubmitting.value = true
  salesError.value = ''
  try {
    const response = await fetch('/api/sales-returns', {
      method: 'POST', headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ sourceStockOutId: returnForm.value.sourceStockOutId, returnDate: returnForm.value.returnDate, reason: returnForm.value.reason, remark: returnForm.value.remark,
        items: returnForm.value.items.filter((item) => Number(item.returnedQuantity) > 0).map((item) => ({ sourceStockOutItemId: item.sourceStockOutItemId, returnedQuantity: Number(item.returnedQuantity) })) })
    })
    if (!response.ok) throw new Error(await responseError(response, '退货单创建失败'))
    const draft = await response.json()
    const submitResponse = await fetch(`/api/sales-returns/${draft.id}/submit`, { method: 'POST' })
    if (!submitResponse.ok) throw new Error(await responseError(submitResponse, '退货单提交失败'))
    const approveResponse = await fetch(`/api/sales-returns/${draft.id}/approve`, { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ comment: '销售退货审核通过' }) })
    if (!approveResponse.ok) throw new Error(await responseError(approveResponse, '退货单审核失败'))
    const confirmResponse = await fetch(`/api/sales-returns/${draft.id}/confirm`, { method: 'POST' })
    if (!confirmResponse.ok) throw new Error(await responseError(confirmResponse, '退货确认失败'))
    await loadSales(); await loadProducts()
    showReturnForm.value = false
    showNotice('销售退货已确认，库存已回增')
  } catch (exception) { salesError.value = exception.message }
  finally { salesSubmitting.value = false }
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

async function loadReport() {
  reportLoading.value = true
  reportError.value = ''
  try {
    const [dashboardResponse, purchaseResponse, inventoryResponse, warningResponse] = await Promise.all([
      fetch('/api/reports/dashboard'),
      fetch('/api/reports/purchase-analysis'),
      fetch('/api/reports/inventory-balance'),
      fetch('/api/reports/low-stock')
    ])
    if (!dashboardResponse.ok) throw new Error(await responseError(dashboardResponse, '无法读取经营看板'))
    if (!purchaseResponse.ok) throw new Error(await responseError(purchaseResponse, '无法读取采购分析'))
    if (!inventoryResponse.ok) throw new Error(await responseError(inventoryResponse, '无法读取库存余额'))
    if (!warningResponse.ok) throw new Error(await responseError(warningResponse, '无法读取库存预警'))
    dashboardData.value = await dashboardResponse.json()
    purchaseAnalysisData.value = await purchaseResponse.json()
    inventoryBalance.value = await inventoryResponse.json()
    lowStockWarnings.value = await warningResponse.json()
  } catch (exception) {
    reportError.value = exception.message || '报表服务未启动，请先运行后端。'
  } finally {
    reportLoading.value = false
  }
}

function selectReportTab(tab) {
  reportTab.value = tab
}

async function exportReportCsv(type, filename) {
  try {
    const response = await fetch(`/api/reports/export?type=${type}`)
    if (!response.ok) throw new Error(await responseError(response, '报表导出失败'))
    const url = URL.createObjectURL(await response.blob())
    const link = document.createElement('a')
    link.href = url
    link.download = `${filename}_${new Date().toISOString().slice(0, 10)}.csv`
    link.click()
    URL.revokeObjectURL(url)
    showNotice(`${filename}已导出`)
  } catch (exception) {
    reportError.value = exception.message || '报表导出失败'
  }
}

async function initializeApplication() {
  try {
    const statusResponse = await fetch('/api/auth/status')
    if (statusResponse.ok) {
      const status = await statusResponse.json()
      authEnabled.value = Boolean(status.enabled)
    }
    if (authEnabled.value) {
      const meResponse = await fetch('/api/auth/me')
      if (meResponse.ok) currentUser.value = await meResponse.json()
    }
    if (!authEnabled.value || currentUser.value) await loadProducts()
  } catch (_) {
    authEnabled.value = false
    await loadProducts()
  } finally {
    authChecked.value = true
  }
}

async function handleAuthenticated(user) {
  currentUser.value = user
  if (user.mustChangePassword) showChangePassword.value = true
  await loadProducts()
}

async function logout() {
  try { await fetch('/api/auth/logout', { method: 'POST' }) } finally { currentUser.value = null }
}

function openChangePassword() {
  passwordForm.value = { currentPassword: '', newPassword: '', confirmPassword: '' }
  passwordError.value = ''
  showChangePassword.value = true
}

async function changePassword() {
  passwordError.value = ''
  if (passwordForm.value.newPassword !== passwordForm.value.confirmPassword) {
    passwordError.value = '两次输入的新密码不一致'
    return
  }
  changingPassword.value = true
  try {
    const response = await fetch('/api/auth/change-password', {
      method: 'POST', headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ currentPassword: passwordForm.value.currentPassword, newPassword: passwordForm.value.newPassword })
    })
    if (!response.ok) throw new Error(await responseError(response, '密码修改失败'))
    showChangePassword.value = false
    if (currentUser.value) currentUser.value = { ...currentUser.value, mustChangePassword: false }
    showNotice('密码已修改，请使用新密码登录')
  } catch (exception) { passwordError.value = exception.message || '密码修改失败' }
  finally { changingPassword.value = false }
}

onMounted(initializeApplication)
</script>

<template>
  <LoginView v-if="authChecked && authEnabled && !currentUser" @authenticated="handleAuthenticated" />
  <div v-else-if="authChecked" class="app-frame">
    <aside class="sidebar">
      <div class="brand">
        <span class="brand-mark">N</span>
        <div><strong>NOVA ERP</strong><small>企业资源管理平台</small></div>
      </div>

      <nav class="nav-menu">
        <p class="nav-label">工作台</p>
        <a v-if="can('report:view')" href="#" :class="['nav-item', { active: activeModule === 'overview' }]" @click.prevent="switchModule('overview')"><span>⌂</span>经营概览</a>
        <p class="nav-label">业务管理</p>
        <a v-if="can('base:product:list')" href="#" :class="['nav-item', { active: activeModule === 'products' }]" @click.prevent="switchModule('products')"><span>◇</span>商品档案<em>{{ products.length }}</em></a>
        <a v-if="can('purchase:order:list')" href="#" :class="['nav-item', { active: activeModule === 'purchases' }]" @click.prevent="switchModule('purchases')"><span>▤</span>采购管理<em>{{ purchaseOrders.length }}</em></a>
        <a v-if="can('sales:order:list')" href="#" :class="['nav-item', { active: activeModule === 'sales' }]" @click.prevent="switchModule('sales')"><span>▣</span>销售管理<em>{{ salesOrders.length }}</em></a>
        <a v-if="can('inventory:balance:view')" href="#" :class="['nav-item', { active: activeModule === 'warehouse' }]" @click.prevent="switchModule('warehouse')"><span>▥</span>仓储管理<em>V3</em></a>
        <p class="nav-label">分析与设置</p>
        <a v-if="can('report:view')" href="#" :class="['nav-item', { active: activeModule === 'reports' }]" @click.prevent="switchModule('reports')"><span>⌁</span>业务报表<em v-if="reportLowStockCount">{{ reportLowStockCount }}</em></a>
        <a v-if="can('system:user:list')" href="#" :class="['nav-item', { active: activeModule === 'settings' }]" @click.prevent="switchModule('settings')"><span>⚙</span>系统设置</a>
      </nav>

      <div class="sidebar-status">
        <span class="status-dot"></span>
        <div><strong>开发环境</strong><small>Spring Boot 服务在线</small></div>
      </div>
    </aside>

    <main class="workspace">
      <header class="topbar">
        <div class="breadcrumb"><span>进销存管理</span><b>/</b><strong>{{ activeModuleLabel }}</strong></div>
        <div class="topbar-right">
          <span class="date-label">{{ currentDate }}</span>
          <button class="round-button" title="通知">◔<i v-if="lowStockCount"></i></button>
          <div class="user-card"><span>{{ (currentUser?.realName || '管').slice(0, 1) }}</span><div><strong>{{ currentUser?.realName || '系统管理员' }}</strong><small>{{ currentUser?.roleName || '演示账户' }}</small></div></div>
          <button v-if="authEnabled" class="logout-button" @click="openChangePassword">改密</button>
          <button v-if="authEnabled" class="logout-button" @click="logout">退出</button>
        </div>
      </header>

      <OverviewView v-if="activeModule === 'overview'" @navigate="handleOverviewNavigate" />

      <div v-else-if="activeModule === 'products'" class="page-content">
        <section class="page-heading">
          <div>
            <p class="eyebrow">INVENTORY · PRODUCT MASTER</p>
            <h1>商品档案</h1>
            <p>统一管理商品基础信息、销售价格与安全库存规则。</p>
          </div>
          <div class="heading-actions">
            <button class="secondary-button" @click="exportCsv"><span>⇩</span> 导出 CSV</button>
            <button v-if="can('base:product:manage')" class="primary-button" @click="openCreate"><span>＋</span> 新增商品</button>
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
                  <td><button v-if="can('base:product:manage')" :class="['status-switch', { off: product.status !== '启用' }]" @click="toggleStatus(product)"><i></i>{{ product.status }}</button><span v-else>{{ product.status }}</span></td>
                  <td class="row-actions">
                    <button @click="openProductDetails(product)">详情</button>
                    <button v-if="can('base:product:manage')" @click="openEdit(product)">编辑</button>
                    <button v-if="can('base:product:manage')" class="restock-button" @click="openRestockDialog(product)">补货</button>
                    <details v-if="can('base:product:manage')" class="row-menu"><summary>•••</summary><div><button @click="duplicateProduct(product)">复制商品</button><button class="danger" @click="removeProduct(product)">删除商品</button></div></details>
                  </td>
                </tr>
              </tbody>
            </table>
          </div>

          <footer class="list-footer"><span>显示 {{ visibleProducts.length }} / {{ products.length }} 条记录</span><span>数据源：Spring Boot 商品与库存服务</span></footer>
        </section>
      </div>

      <div v-else-if="activeModule === 'purchases'" class="page-content purchase-page">
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

      <div v-else-if="activeModule === 'sales'" class="page-content sales-page">
        <section class="page-heading">
          <div><p class="eyebrow">SALES · ORDER TO CASH</p><h1>销售管理</h1><p>从客户下单、审核，到销售出库与退货回库，完整串起销售履约流程。</p></div>
          <div class="heading-actions"><button class="secondary-button" @click="loadSales" :disabled="salesLoading"><span>↻</span> 刷新数据</button><button class="primary-button" @click="openSalesCreate"><span>＋</span> 新建销售单</button></div>
        </section>

        <section class="metrics">
          <article class="metric-card blue"><div class="metric-icon">销</div><div><span>销售订单</span><strong>{{ salesOrders.length }}</strong><small>当前可见订单</small></div><b>V1</b></article>
          <article class="metric-card orange"><div class="metric-icon">审</div><div><span>待审核</span><strong>{{ salesPendingCount }}</strong><small>等待主管处理</small></div><b :class="{ alert: salesPendingCount }">待办</b></article>
          <article class="metric-card violet"><div class="metric-icon">发</div><div><span>待出库</span><strong>{{ salesShippingCount }}</strong><small>审核通过待履约</small></div><b>跟进</b></article>
          <article class="metric-card green"><div class="metric-icon">¥</div><div><span>销售总额</span><strong>{{ formatCurrency(salesTotalAmount) }}</strong><small>当前列表订单合计</small></div><b>估算</b></article>
        </section>

        <section class="content-card">
          <div class="list-heading"><div><h2>销售订单</h2><p>订单审核通过后才能出库；出库确认会自动扣减库存。</p></div><span class="module-badge">{{ customers.length }} 个客户</span></div>
          <div class="filter-bar purchase-filter"><label class="search-box"><span>⌕</span><input v-model="salesKeyword" placeholder="搜索销售单号或客户名称" /></label><select v-model="salesStatusFilter"><option>全部状态</option><option>草稿</option><option>待审核</option><option>已审核</option><option>已完成</option><option>已驳回</option><option>已作废</option></select><button class="text-button" @click="salesKeyword = ''; salesStatusFilter = '全部状态'">重置</button></div>
          <div v-if="salesError" class="message error-message"><span>!</span><p>{{ salesError }}</p><button @click="salesError = ''">×</button></div>
          <div class="table-wrap"><table class="purchase-table"><thead><tr><th>销售单号</th><th>客户</th><th>订单日期</th><th>销售金额</th><th>履约进度</th><th>状态</th><th class="action-column">操作</th></tr></thead><tbody>
            <tr v-if="salesLoading"><td colspan="7" class="empty-state"><span class="spinner"></span><strong>正在读取销售数据</strong><small>请稍候…</small></td></tr>
            <tr v-else-if="filteredSalesOrders.length === 0"><td colspan="7" class="empty-state"><span class="empty-icon">⌕</span><strong>暂无匹配销售订单</strong><small>可以新建一张销售订单开始流程</small></td></tr>
            <tr v-for="order in filteredSalesOrders" v-else :key="order.id"><td><div class="purchase-no"><strong>{{ order.orderNo }}</strong><small>{{ order.totalQuantity }} 件商品</small></div></td><td><strong>{{ order.customerName }}</strong></td><td>{{ formatDate(order.orderDate) }}</td><td><strong class="price">{{ formatCurrency(order.totalAmount) }}</strong></td><td><div class="purchase-progress"><div><span>{{ order.shippedQuantity }} / {{ order.totalQuantity }}</span><b>{{ salesOrderProgress(order) }}%</b></div><i><em :style="{ width: `${salesOrderProgress(order)}%` }"></em></i></div></td><td><span :class="['order-status', `status-${order.status}`]">{{ order.status }}</span></td><td class="row-actions purchase-actions"><button @click="openSalesDetails(order)">详情</button><button v-if="order.status === '草稿' || order.status === '已驳回'" @click="salesAction(order, 'submit')">提交</button><button v-if="order.status === '待审核'" @click="salesAction(order, 'approve')">审核</button><button v-if="order.status === '待审核'" class="danger-text" @click="salesAction(order, 'reject')">驳回</button><button v-if="order.status === '已审核' && salesOrderPending(order) > 0" class="restock-button" @click="openStockOut(order)">出库</button></td></tr>
          </tbody></table></div>
          <footer class="list-footer"><span>显示 {{ filteredSalesOrders.length }} / {{ salesOrders.length }} 条记录</span><span>库存变化由确认销售出库驱动 · 当前为可运行演示版</span></footer>
        </section>

        <section class="content-card sales-record-card"><div class="list-heading"><div><h2>出库与退货记录</h2><p>退货必须引用已确认的销售出库单，确认后商品回到原仓库。</p></div><span class="module-badge">{{ salesReturns.length }} 张退货单</span></div>
          <div class="table-wrap"><table class="purchase-table"><thead><tr><th>业务单号</th><th>客户 / 关联销售单</th><th>日期</th><th>数量</th><th>状态</th><th class="action-column">操作</th></tr></thead><tbody>
            <tr v-for="stockOut in stockOutOrders" :key="`out-${stockOut.id}`"><td><div class="purchase-no"><strong>{{ stockOut.stockOutNo }}</strong><small>销售出库</small></div></td><td><strong>{{ stockOut.customerName }}</strong><small class="table-subline">{{ stockOut.salesOrderNo }}</small></td><td>{{ formatDate(stockOut.stockOutDate) }}</td><td>{{ stockOut.totalQuantity }} 件</td><td><span :class="['order-status', `status-${stockOut.status}`]">{{ stockOut.status }}</span></td><td class="row-actions purchase-actions"><button v-if="stockOut.status === '已确认' && stockOut.items.some(item => stockOutReturnable(item) > 0)" class="restock-button" @click="openReturn(stockOut)">销售退货</button></td></tr>
            <tr v-for="item in salesReturns" :key="`return-${item.id}`"><td><div class="purchase-no"><strong>{{ item.returnNo }}</strong><small>销售退货</small></div></td><td><strong>{{ item.customerName }}</strong><small class="table-subline">{{ item.salesOrderNo }}</small></td><td>{{ formatDate(item.returnDate) }}</td><td>{{ item.totalQuantity }} 件</td><td><span :class="['order-status', `status-${item.status}`]">{{ item.status }}</span></td><td class="row-actions purchase-actions"><span class="muted-action">已回库</span></td></tr>
            <tr v-if="!stockOutOrders.length && !salesReturns.length"><td colspan="6" class="empty-state"><span class="empty-icon">↗</span><strong>暂无出库或退货记录</strong><small>审核销售订单后即可发起出库</small></td></tr>
          </tbody></table></div>
        </section>

        <section class="content-card customer-card"><div class="list-heading"><div><h2>客户档案</h2><p>销售订单只能选择启用状态的客户，客户编码必须保持唯一。</p></div><button class="secondary-button" @click="openCustomerCreate"><span>＋</span> 新增客户</button></div>
          <div class="table-wrap"><table class="purchase-table"><thead><tr><th>客户编码</th><th>客户名称</th><th>联系人</th><th>联系电话</th><th>状态</th><th class="action-column">操作</th></tr></thead><tbody>
            <tr v-for="customer in customers" :key="customer.id"><td><code>{{ customer.code }}</code></td><td><strong>{{ customer.name }}</strong></td><td>{{ customer.contact || '—' }}</td><td>{{ customer.phone || '—' }}</td><td><span :class="['order-status', customer.status === '启用' ? 'status-已审核' : 'status-已驳回']">{{ customer.status }}</span></td><td class="row-actions purchase-actions"><button @click="openCustomerEdit(customer)">编辑</button></td></tr>
          </tbody></table></div>
        </section>
      </div>

      <div v-else-if="activeModule === 'reports'" class="page-content report-page">
        <section class="page-heading">
          <div><p class="eyebrow">ANALYTICS · BUSINESS REPORTS</p><h1>业务报表</h1><p>实时聚合采购、销售、仓储与商品数据，支持经营分析和 CSV 导出。</p></div>
          <div class="heading-actions"><button class="secondary-button" @click="loadReport" :disabled="reportLoading"><span>↻</span> 刷新报表</button><button class="secondary-button" @click="exportReportCsv('inventory', '库存余额报表')"><span>⇩</span> 库存导出</button><button class="secondary-button" @click="exportReportCsv('warning', '库存预警报表')"><span>⇩</span> 预警导出</button><button class="primary-button" @click="exportReportCsv('purchase', '采购分析报表')"><span>⇩</span> 采购导出</button></div>
        </section>

        <div class="report-tabs"><button :class="['report-tab', { active: reportTab === 'dashboard' }]" @click="selectReportTab('dashboard')">经营看板</button><button :class="['report-tab', { active: reportTab === 'purchase' }]" @click="selectReportTab('purchase')">采购分析</button><button :class="['report-tab', { active: reportTab === 'inventory' }]" @click="selectReportTab('inventory')">库存余额</button><button :class="['report-tab', { active: reportTab === 'warning' }]" @click="selectReportTab('warning')">库存预警</button></div>
        <div v-if="reportError" class="message error-message"><span>!</span><p>{{ reportError }}</p><button @click="reportError = ''">×</button></div>

        <template v-if="reportTab === 'dashboard'">
          <section v-if="reportLoading" class="report-loading"><span class="spinner"></span><strong>正在生成经营看板</strong></section>
          <template v-else-if="dashboardData">
            <section class="metrics">
              <article class="metric-card green"><div class="metric-icon">¥</div><div><span>采购金额</span><strong>{{ formatCurrency(dashboardData.purchaseAmount) }}</strong><small>已确认入库单口径</small></div><b>采购</b></article>
              <article class="metric-card blue"><div class="metric-icon">销</div><div><span>销售金额</span><strong>{{ formatCurrency(dashboardData.saleAmount) }}</strong><small>{{ Number(dashboardData.saleQuantity || 0).toLocaleString() }} 件已出库</small></div><b>销售</b></article>
              <article class="metric-card violet"><div class="metric-icon">库</div><div><span>库存货值</span><strong>{{ formatCurrency(dashboardData.inventoryValue) }}</strong><small>{{ Number(dashboardData.stockTotal || 0).toLocaleString() }} 件在库</small></div><b>估值</b></article>
              <article class="metric-card orange"><div class="metric-icon">!</div><div><span>库存预警</span><strong>{{ dashboardData.lowStockCount }}</strong><small>商品总数 {{ dashboardData.productCount }} · 待审 {{ dashboardData.purchasePendingCount }}</small></div><b :class="{ alert: dashboardData.lowStockCount }">{{ dashboardData.lowStockCount ? '待处理' : '正常' }}</b></article>
            </section>
            <section class="content-card">
              <div class="list-heading"><div><h2>近 7 日经营趋势</h2><p>按已确认采购入库与销售出库日期聚合，数据来自当前业务单据。</p></div><span class="module-badge">RPT-01</span></div>
              <div class="table-wrap"><table><thead><tr><th>日期</th><th>入库数量</th><th>采购金额</th><th>出库数量</th><th>销售金额</th></tr></thead><tbody><tr v-for="point in dashboardData.trend" :key="point.date"><td><strong>{{ point.date }}</strong></td><td>{{ point.stockIn }}</td><td>{{ formatCurrency(point.purchaseAmount) }}</td><td>{{ point.sale }}</td><td>{{ formatCurrency(point.saleAmount) }}</td></tr></tbody></table></div>
              <footer class="list-footer"><span>数据源：Spring Boot 业务报表服务</span><span>销售数据按已确认销售出库单统计</span></footer>
            </section>
          </template>
        </template>

        <template v-else-if="reportTab === 'purchase'">
          <section v-if="reportLoading" class="report-loading"><span class="spinner"></span><strong>正在生成采购分析</strong></section>
          <template v-else-if="purchaseAnalysisData">
            <section class="metrics"><article class="metric-card green"><div class="metric-icon">单</div><div><span>采购订单数</span><strong>{{ purchaseSummary.orders }}</strong><small>按供应商汇总</small></div><b>采购</b></article><article class="metric-card blue"><div class="metric-icon">供</div><div><span>供应商数</span><strong>{{ purchaseSummary.suppliers }}</strong><small>当前订单涉及供应商</small></div><b>渠道</b></article><article class="metric-card violet"><div class="metric-icon">¥</div><div><span>订单总额</span><strong>{{ formatCurrency(purchaseSummary.amount) }}</strong><small>含各状态采购订单</small></div><b>汇总</b></article><article class="metric-card orange"><div class="metric-icon">入</div><div><span>已入库金额</span><strong>{{ formatCurrency(purchaseSummary.received) }}</strong><small>仅已确认入库单</small></div><b>已确认</b></article></section>
            <section class="content-card"><div class="list-heading"><div><h2>按供应商统计</h2><p>订单金额与已确认入库金额对比。</p></div><span class="module-badge">RPT-03</span></div><div class="table-wrap"><table><thead><tr><th>供应商</th><th>订单数</th><th>采购数量</th><th>订单金额</th><th>已入库数量</th><th>已入库金额</th></tr></thead><tbody><tr v-for="line in purchaseAnalysisData.suppliers" :key="line.id"><td><strong>{{ line.name }}</strong></td><td>{{ line.orderCount }}</td><td>{{ line.orderQuantity }}</td><td>{{ formatCurrency(line.orderAmount) }}</td><td>{{ line.receivedQuantity }}</td><td>{{ formatCurrency(line.receivedAmount) }}</td></tr><tr v-if="!purchaseAnalysisData.suppliers.length"><td colspan="6" class="empty-state"><strong>暂无采购分析数据</strong></td></tr></tbody></table></div></section>
            <section class="content-card report-card-gap"><div class="list-heading"><div><h2>订单状态分布</h2><p>按采购订单当前状态汇总金额。</p></div></div><div class="table-wrap"><table><thead><tr><th>订单状态</th><th>订单数</th><th>订单金额</th></tr></thead><tbody><tr v-for="line in purchaseAnalysisData.statuses" :key="line.status"><td><span :class="['order-status', `status-${line.status}`]">{{ line.status }}</span></td><td>{{ line.orderCount }}</td><td>{{ formatCurrency(line.orderAmount) }}</td></tr></tbody></table></div></section>
            <section class="content-card report-card-gap"><div class="list-heading"><div><h2>近 12 个月采购趋势</h2><p>订单金额与入库金额的月度对比。</p></div></div><div class="table-wrap"><table><thead><tr><th>月份</th><th>订单金额</th><th>入库金额</th></tr></thead><tbody><tr v-for="point in purchaseAnalysisData.months" :key="point.month"><td><strong>{{ point.month }}</strong></td><td>{{ formatCurrency(point.orderAmount) }}</td><td>{{ formatCurrency(point.receivedAmount) }}</td></tr></tbody></table></div></section>
          </template>
        </template>

        <template v-else-if="reportTab === 'inventory'">
          <section v-if="reportLoading" class="report-loading"><span class="spinner"></span><strong>正在生成库存余额报表</strong></section>
          <template v-else><section class="metrics"><article class="metric-card green"><div class="metric-icon">品</div><div><span>在库商品</span><strong>{{ inventorySummary.products }}</strong><small>SKU 数量</small></div><b>SKU</b></article><article class="metric-card blue"><div class="metric-icon">库</div><div><span>库存总量</span><strong>{{ inventorySummary.stock.toLocaleString() }}</strong><small>当前在库件数</small></div><b>在库</b></article><article class="metric-card violet"><div class="metric-icon">¥</div><div><span>库存货值</span><strong>{{ formatCurrency(inventorySummary.value) }}</strong><small>按当前销售价估值</small></div><b>估值</b></article><article class="metric-card orange"><div class="metric-icon">!</div><div><span>低库存商品</span><strong>{{ inventorySummary.warnings }}</strong><small>当前库存低于安全库存</small></div><b :class="{ alert: inventorySummary.warnings }">{{ inventorySummary.warnings ? '待处理' : '正常' }}</b></article></section>
            <section class="content-card"><div class="list-heading"><div><h2>库存余额</h2><p>期初/入库/出库对账视图；当前内存版暂无历史快照，期初暂为 0。</p></div><span class="module-badge">RPT-04</span></div><div class="filter-bar report-filter"><select v-model="inventoryCategoryFilter"><option>全部分类</option><option v-for="category in reportCategories" :key="category">{{ category }}</option></select></div><div class="table-wrap"><table><thead><tr><th>商品</th><th>分类</th><th>期初</th><th>累计入库</th><th>累计出库</th><th>当前库存</th><th>可用库存</th><th>安全库存</th><th>货值</th><th>状态</th></tr></thead><tbody><tr v-if="!filteredInventoryBalance.length"><td colspan="10" class="empty-state"><span class="empty-icon">⌕</span><strong>暂无库存数据</strong><small>请先维护商品档案</small></td></tr><tr v-for="row in filteredInventoryBalance" :key="row.productId"><td><div class="product-cell"><div><strong>{{ row.name }}</strong><code>{{ row.sku }} · {{ row.unit }}</code></div></div></td><td><span class="category-chip">{{ row.category }}</span></td><td>{{ row.openingStock }}</td><td>{{ row.stockIn }}</td><td>{{ row.stockOut }}</td><td><strong>{{ row.currentStock }}</strong></td><td>{{ row.availableStock }}</td><td>{{ row.safetyStock }}</td><td>{{ formatCurrency(row.stockValue) }}</td><td><span class="status-switch" :class="{ off: row.status !== '启用' }">{{ row.status }}</span></td></tr></tbody></table></div><footer class="list-footer"><span>显示 {{ filteredInventoryBalance.length }} / {{ inventoryBalance.length }} 条库存记录</span><span>累计入库/出库来自仓储库存流水</span></footer></section></template>
        </template>

        <template v-else-if="reportTab === 'warning'">
          <section v-if="reportLoading" class="report-loading"><span class="spinner"></span><strong>正在生成库存预警</strong></section>
          <template v-else><section class="metrics"><article class="metric-card orange"><div class="metric-icon">!</div><div><span>预警总数</span><strong>{{ warningSummary.total }}</strong><small>需要关注的记录</small></div><b :class="{ alert: warningSummary.total }">{{ warningSummary.total ? '待处理' : '正常' }}</b></article><article class="metric-card green"><div class="metric-icon">缺</div><div><span>缺货</span><strong>{{ warningSummary.out }}</strong><small>已无可用库存</small></div><b>缺货</b></article><article class="metric-card blue"><div class="metric-icon">低</div><div><span>库存偏低</span><strong>{{ warningSummary.low }}</strong><small>低于安全库存</small></div><b>偏低</b></article><article class="metric-card violet"><div class="metric-icon">停</div><div><span>停用商品</span><strong>{{ warningSummary.disabled }}</strong><small>停用但仍有库存记录</small></div><b>留存</b></article></section><section class="content-card"><div class="list-heading"><div><h2>库存预警</h2><p>缺货、低于安全库存与停用商品清单。</p></div><span class="module-badge">RPT-06</span></div><div class="table-wrap"><table><thead><tr><th>商品</th><th>分类</th><th>预警类型</th><th>当前库存</th><th>安全库存</th><th>补货缺口</th><th>商品状态</th></tr></thead><tbody><tr v-if="!lowStockWarnings.length"><td colspan="7" class="empty-state"><span class="empty-icon">✓</span><strong>库存状态全部正常</strong><small>没有需要处理的预警商品</small></td></tr><tr v-for="row in lowStockWarnings" :key="row.productId"><td><div class="product-cell"><div><strong>{{ row.name }}</strong><code>{{ row.sku }} · {{ row.unit }}</code></div></div></td><td><span class="category-chip">{{ row.category }}</span></td><td><span :class="['stock-label', row.warningType === '缺货' ? 'critical' : 'low']">{{ row.warningType }}</span></td><td><strong>{{ row.currentStock }}</strong></td><td>{{ row.safetyStock }}</td><td>{{ row.gap }}</td><td><span class="status-switch" :class="{ off: row.status !== '启用' }">{{ row.status }}</span></td></tr></tbody></table></div><footer class="list-footer"><span>预警类型：缺货 / 库存偏低 / 停用商品</span><span>可前往商品档案执行补货</span></footer></section></template>
        </template>
      </div>

      <SystemSettingsView v-else-if="activeModule === 'settings'" :current-user="currentUser" />
      <WarehouseView v-else-if="activeModule === 'warehouse'" :current-user="currentUser" />
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
        <div class="drawer-note"><strong>数据说明</strong><p>当前页面通过 Spring Boot API 读取业务数据；MySQL profile 下由版本化迁移和持久化服务管理。</p></div>
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

    <div v-if="showSalesForm" class="modal-backdrop" @click.self="showSalesForm = false">
      <form class="modal purchase-modal sales-modal" @submit.prevent="saveSalesOrder">
        <div class="modal-title"><div><p class="eyebrow">SALES · ORDER</p><h2>新建销售订单</h2><p>选择客户与商品，保存后提交销售主管审核。</p></div><button type="button" class="icon-button" @click="showSalesForm = false">×</button></div>
        <div class="form-grid"><label>客户 <em>*</em><select v-model="salesForm.customerId" required><option value="" disabled>请选择客户</option><option v-for="customer in customers" :key="customer.id" :value="customer.id">{{ customer.name }} · {{ customer.code }}</option></select></label><label>订单日期 <em>*</em><input v-model="salesForm.orderDate" type="date" required /></label><label>要求发货日期<input v-model="salesForm.requiredShipDate" type="date" /></label><label>备注<input v-model.trim="salesForm.remark" maxlength="500" placeholder="例如：请分批发货" /></label></div>
        <div class="purchase-lines-heading"><strong>销售明细</strong><button type="button" class="text-button" @click="addSalesItem">＋ 添加商品</button></div>
        <div class="purchase-line-list"><div v-for="(item, index) in salesForm.items" :key="index" class="purchase-line"><select v-model="item.productId" required @change="fillSalesPrice(item)"><option value="" disabled>选择商品</option><option v-for="product in salesProductOptions" :key="product.id" :value="product.id">{{ product.name }} · {{ product.sku }}</option></select><input v-model.number="item.orderedQuantity" type="number" min="1" required placeholder="数量" /><div class="input-prefix"><span>¥</span><input v-model.number="item.unitPrice" type="number" min="0" step="0.01" required /></div><strong>{{ formatCurrency(salesFormAmount(item)) }}</strong><button type="button" class="line-remove" @click="removeSalesItem(index)" :disabled="salesForm.items.length === 1">×</button></div></div>
        <div class="purchase-total"><span>订单预计金额</span><strong>{{ formatCurrency(salesFormTotal()) }}</strong></div><div v-if="salesError" class="message error-message compact"><span>!</span><p>{{ salesError }}</p></div><div class="modal-actions"><button type="button" class="secondary-button" @click="showSalesForm = false">取消</button><button type="submit" class="primary-button" :disabled="salesSubmitting">{{ salesSubmitting ? '保存中…' : '保存销售草稿' }}</button></div>
      </form>
    </div>

    <div v-if="showStockOutForm" class="modal-backdrop" @click.self="showStockOutForm = false">
      <form class="modal receipt-modal" @submit.prevent="saveStockOut">
        <div class="modal-title"><div><p class="eyebrow">WAREHOUSE · STOCK OUT</p><h2>销售出库</h2><p>确认后库存将扣减，并生成销售出库流水。</p></div><button type="button" class="icon-button" @click="showStockOutForm = false">×</button></div>
        <div class="form-grid"><label>出库仓库<select><option>主仓</option></select></label><label>出库日期<input v-model="stockOutForm.stockOutDate" type="date" required /></label></div>
        <div class="purchase-lines-heading"><strong>本次出库明细</strong><span class="module-badge">最多 {{ stockOutForm.items.reduce((sum, item) => sum + item.pendingQuantity, 0) }} 件</span></div>
        <div class="receipt-line-list"><div v-for="item in stockOutForm.items" :key="item.salesOrderItemId" class="receipt-line"><div><strong>{{ item.productName }}</strong><small>待出库 {{ item.pendingQuantity }} · 可用库存 {{ item.availableStock }}</small></div><input v-model.number="item.shippedQuantity" type="number" min="0" :max="Math.min(item.pendingQuantity, item.availableStock)" required /><span>{{ item.unit }}</span></div></div>
        <div class="purchase-total"><span>本次出库数量</span><strong>{{ stockOutTotal() }} 件</strong></div><div v-if="salesError" class="message error-message compact"><span>!</span><p>{{ salesError }}</p></div><div class="modal-actions"><button type="button" class="secondary-button" @click="showStockOutForm = false">取消</button><button type="submit" class="primary-button" :disabled="salesSubmitting">{{ salesSubmitting ? '确认中…' : '确认出库' }}</button></div>
      </form>
    </div>

    <div v-if="showReturnForm" class="modal-backdrop" @click.self="showReturnForm = false">
      <form class="modal receipt-modal" @submit.prevent="saveReturn">
        <div class="modal-title"><div><p class="eyebrow">SALES · RETURN</p><h2>销售退货</h2><p>退货审核通过后，商品将回到原销售出库仓库。</p></div><button type="button" class="icon-button" @click="showReturnForm = false">×</button></div>
        <div class="form-grid"><label>退货日期<input v-model="returnForm.returnDate" type="date" required /></label><label>退货原因 <em>*</em><input v-model.trim="returnForm.reason" required maxlength="200" placeholder="例如：客户包装破损退回" /></label></div>
        <div class="purchase-lines-heading"><strong>退货明细</strong><span class="module-badge">需引用原出库单</span></div>
        <div class="receipt-line-list"><div v-for="item in returnForm.items" :key="item.sourceStockOutItemId" class="receipt-line"><div><strong>{{ item.productName }}</strong><small>可退数量 {{ item.returnableQuantity }}</small></div><input v-model.number="item.returnedQuantity" type="number" min="0" :max="item.returnableQuantity" required /><span>件</span></div></div>
        <div class="purchase-total"><span>本次退货数量</span><strong>{{ returnTotal() }} 件</strong></div><div v-if="salesError" class="message error-message compact"><span>!</span><p>{{ salesError }}</p></div><div class="modal-actions"><button type="button" class="secondary-button" @click="showReturnForm = false">取消</button><button type="submit" class="primary-button" :disabled="salesSubmitting">{{ salesSubmitting ? '确认中…' : '提交并确认退货' }}</button></div>
      </form>
    </div>

    <div v-if="salesDetails" class="drawer-backdrop" @click.self="salesDetails = null"><aside class="detail-drawer purchase-drawer">
      <div class="drawer-header"><div><p class="eyebrow">SALES ORDER DETAILS</p><h2>销售单详情</h2></div><button class="icon-button" @click="salesDetails = null">×</button></div>
      <div class="purchase-detail-title"><strong>{{ salesDetails.orderNo }}</strong><span :class="['order-status', `status-${salesDetails.status}`]">{{ salesDetails.status }}</span></div>
      <dl class="detail-list"><div><dt>客户</dt><dd>{{ salesDetails.customerName }}</dd></div><div><dt>订单日期</dt><dd>{{ formatDate(salesDetails.orderDate) }}</dd></div><div><dt>要求发货</dt><dd>{{ formatDate(salesDetails.requiredShipDate) }}</dd></div><div><dt>销售金额</dt><dd>{{ formatCurrency(salesDetails.totalAmount) }}</dd></div><div><dt>已出库</dt><dd>{{ salesDetails.shippedQuantity }} / {{ salesDetails.totalQuantity }} 件</dd></div><div><dt>备注</dt><dd>{{ salesDetails.remark || '—' }}</dd></div></dl>
      <div class="detail-items"><strong>商品明细</strong><div v-for="item in salesDetails.items" :key="item.id"><span>{{ item.productName }}<small>{{ item.sku }} · {{ item.unit }}</small></span><b>{{ item.shippedQuantity }} / {{ item.orderedQuantity }}<small v-if="item.returnedQuantity">已退 {{ item.returnedQuantity }}</small></b></div></div>
      <div class="drawer-actions"><button v-if="salesDetails.status === '已审核' && salesOrderPending(salesDetails) > 0" class="primary-button" @click="openStockOut(salesDetails)">创建出库单</button><button v-if="salesDetails.status === '待审核'" class="secondary-button" @click="salesAction(salesDetails, 'approve'); salesDetails = null">审核通过</button></div>
    </aside></div>

    <div v-if="showCustomerForm" class="modal-backdrop" @click.self="showCustomerForm = false"><form class="modal customer-modal" @submit.prevent="saveCustomer">
      <div class="modal-title"><div><p class="eyebrow">SALES · CUSTOMER</p><h2>{{ customerEditingId ? '编辑客户档案' : '新增客户档案' }}</h2><p>维护销售业务使用的客户基础信息。</p></div><button type="button" class="icon-button" @click="showCustomerForm = false">×</button></div>
      <div class="form-grid"><label>客户编码 <em>*</em><input v-model.trim="customerForm.code" required maxlength="32" placeholder="例如 CUS-1003" /></label><label>客户名称 <em>*</em><input v-model.trim="customerForm.name" required maxlength="80" placeholder="请输入客户名称" /></label><label>联系人<input v-model.trim="customerForm.contact" maxlength="40" placeholder="请输入联系人" /></label><label>联系电话<input v-model.trim="customerForm.phone" maxlength="30" placeholder="请输入联系电话" /></label><label>启用状态<select v-model="customerForm.status"><option>启用</option><option>停用</option></select></label></div>
      <div v-if="salesError" class="message error-message compact"><span>!</span><p>{{ salesError }}</p></div><div class="modal-actions"><button type="button" class="secondary-button" @click="showCustomerForm = false">取消</button><button type="submit" class="primary-button" :disabled="salesSubmitting">{{ salesSubmitting ? '保存中…' : '保存客户' }}</button></div>
    </form></div>

    <div v-if="showChangePassword" class="modal-backdrop" @click.self="!currentUser?.mustChangePassword && (showChangePassword = false)">
      <form class="modal restock-modal" @submit.prevent="changePassword">
        <div class="modal-title"><div><p class="eyebrow">ACCOUNT · SECURITY</p><h2>修改登录密码</h2><p>新密码至少 8 位，修改后仅保存安全哈希。</p></div><button v-if="!currentUser?.mustChangePassword" type="button" class="icon-button" @click="showChangePassword = false">×</button></div>
        <div class="form-grid"><label class="full">当前密码 <em>*</em><input v-model="passwordForm.currentPassword" type="password" autocomplete="current-password" required /></label><label class="full">新密码 <em>*</em><input v-model="passwordForm.newPassword" type="password" autocomplete="new-password" minlength="8" required /></label><label class="full">确认新密码 <em>*</em><input v-model="passwordForm.confirmPassword" type="password" autocomplete="new-password" minlength="8" required /></label></div>
        <div v-if="passwordError" class="message error-message compact"><span>!</span><p>{{ passwordError }}</p></div>
        <div class="modal-actions"><button v-if="!currentUser?.mustChangePassword" type="button" class="secondary-button" @click="showChangePassword = false">取消</button><button type="submit" class="primary-button" :disabled="changingPassword">{{ changingPassword ? '修改中…' : '确认修改' }}</button></div>
      </form>
    </div>

    <transition name="toast"><div v-if="notice" class="toast-message"><span>✓</span>{{ notice }}</div></transition>
  </div>
  <div v-else class="auth-loading">正在初始化 NOVA ERP…</div>
</template>
