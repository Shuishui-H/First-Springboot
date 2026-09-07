<script setup>
import { computed, onMounted, ref } from 'vue'

const emit = defineEmits(['navigate'])
const range = ref('month')
const data = ref(null)
const loading = ref(false)
const error = ref('')

const currency = new Intl.NumberFormat('zh-CN', { style: 'currency', currency: 'CNY', minimumFractionDigits: 2 })
const rangeLabel = computed(() => ({ today: '今日', '7d': '近 7 日', month: '本月' }[range.value]))
function money(value) { return currency.format(Number(value || 0)) }
function quantity(value) { return Number(value || 0).toLocaleString() }
function time(value) { return value ? new Intl.DateTimeFormat('zh-CN', { month: '2-digit', day: '2-digit', hour: '2-digit', minute: '2-digit', hour12: false }).format(new Date(value)) : '—' }

async function loadOverview() {
  loading.value = true
  error.value = ''
  try {
    const response = await fetch(`/api/dashboard/overview?range=${range.value}`)
    if (!response.ok) {
      const body = await response.json().catch(() => ({}))
      throw new Error(body.detail || body.message || '暂时无法读取经营数据')
    }
    data.value = await response.json()
  } catch (exception) {
    error.value = exception.message || '暂时无法读取经营数据'
  } finally {
    loading.value = false
  }
}

function changeRange(value) { range.value = value; loadOverview() }
function navigate(payload) { emit('navigate', payload) }
function todoIcon(key) { return ({ purchaseApproval: '审', purchaseReceipt: '入', salesApproval: '审', salesStockOut: '出' }[key] || '待') }

onMounted(loadOverview)
</script>

<template>
  <div class="page-content overview-page">
    <section class="page-heading overview-heading">
      <div><p class="eyebrow">WORKSPACE · BUSINESS OVERVIEW</p><h1>经营概览</h1><p>掌握经营状态、库存风险与待处理业务，快速进入下一步操作。</p></div>
      <div class="overview-actions"><span v-if="data?.meta" class="overview-updated">数据更新于 {{ time(data.meta.generatedAt) }}</span><button class="secondary-button" :disabled="loading" @click="loadOverview">↻ {{ loading ? '刷新中…' : '刷新数据' }}</button></div>
    </section>

    <div class="overview-range" role="group" aria-label="统计范围"><button v-for="item in [{ key: 'today', label: '今日' }, { key: '7d', label: '近 7 日' }, { key: 'month', label: '本月' }]" :key="item.key" :class="{ active: range === item.key }" @click="changeRange(item.key)">{{ item.label }}</button></div>

    <section v-if="loading && !data" class="overview-loading"><span class="spinner"></span><strong>正在汇总经营数据</strong></section>
    <div v-else-if="error && !data" class="message error-message overview-error"><span>!</span><p>{{ error }}</p><button @click="loadOverview">重试</button></div>

    <template v-if="data">
      <div v-if="error" class="message error-message overview-error"><span>!</span><p>{{ error }}</p><button @click="loadOverview">重试</button></div>
      <section class="overview-metrics">
        <button class="overview-metric blue" @click="navigate({ module: 'reports', tab: 'dashboard' })"><span class="metric-symbol">销</span><div><small>{{ rangeLabel }}销售金额</small><strong>{{ money(data.metrics.salesAmount) }}</strong><em>{{ quantity(data.metrics.stockOutQuantity) }} 件已确认出库</em></div><b>→</b></button>
        <button class="overview-metric violet" @click="navigate({ module: 'reports', tab: 'purchase' })"><span class="metric-symbol">采</span><div><small>{{ rangeLabel }}采购金额</small><strong>{{ money(data.metrics.purchaseAmount) }}</strong><em>按确认采购入库统计</em></div><b>→</b></button>
        <button class="overview-metric green" @click="navigate({ module: 'reports', tab: 'inventory' })"><span class="metric-symbol">库</span><div><small>库存货值</small><strong>{{ money(data.metrics.inventoryValue) }}</strong><em>按售价参考估值</em></div><b>→</b></button>
        <button class="overview-metric orange" @click="navigate({ module: 'reports', tab: 'warning' })"><span class="metric-symbol">!</span><div><small>主仓库存预警</small><strong>{{ quantity(data.metrics.lowStockCount) }}</strong><em>{{ data.metrics.lowStockCount ? '需调拨或补货' : '主仓库存状态正常' }}</em></div><b>→</b></button>
      </section>

      <section class="overview-section overview-todos"><div class="overview-section-head"><div><p class="eyebrow">ACTION CENTER</p><h2>业务待办</h2><span>优先处理影响履约和库存的业务单据</span></div><i>{{ data.todos.reduce((sum, item) => sum + Number(item.count || 0), 0) }} 项待处理</i></div><div class="todo-grid"><button v-for="todo in data.todos" :key="todo.key" class="todo-card" @click="navigate(todo)"><span class="todo-icon">{{ todoIcon(todo.key) }}</span><div><small>{{ todo.title }}</small><strong>{{ todo.count }}</strong><em>{{ todo.count ? '点击立即处理' : '暂无待处理事项' }}</em></div><b>→</b></button></div></section>

      <section class="overview-columns">
        <article class="overview-section risk-section"><div class="overview-section-head"><div><p class="eyebrow">INVENTORY ALERT</p><h2>主仓库存风险</h2><span>主仓可用库存低于安全库存时，需调拨或补货</span></div><button class="text-button" @click="navigate({ module: 'reports', tab: 'warning' })">查看全部 →</button></div><div v-if="!data.risks.length" class="overview-empty"><span>✓</span><strong>暂无主仓库存风险</strong><small>主仓商品均处于安全范围</small></div><div v-else class="risk-list"><div v-for="risk in data.risks" :key="risk.productId" class="risk-row"><span :class="['risk-dot', risk.warningType === '缺货' ? 'critical' : 'low']"></span><div class="risk-main"><strong>{{ risk.name }}</strong><small>{{ risk.sku }} · {{ risk.warehouseName }}可用 {{ risk.availableQuantity }}</small></div><div class="risk-stock"><small>总库存 / 安全</small><strong>{{ risk.totalStock }} / {{ risk.safetyStock }}</strong></div><div class="risk-gap"><small>主仓缺口 {{ risk.gap }}</small><button @click="navigate({ module: 'purchases', productId: risk.productId })">去补货</button></div></div></div></article>
        <article class="overview-section trend-section"><div class="overview-section-head"><div><p class="eyebrow">BUSINESS TREND</p><h2>经营趋势</h2><span>{{ rangeLabel }}已确认业务汇总</span></div><button class="text-button" @click="navigate({ module: 'reports', tab: 'dashboard' })">查看报表 →</button></div><div class="trend-list"><div v-for="point in data.trend" :key="point.date" class="trend-row"><time>{{ String(point.date).slice(5) }}</time><div><small>采购</small><strong>{{ money(point.purchaseAmount) }}</strong></div><div><small>销售</small><strong>{{ money(point.salesAmount) }}</strong></div><div class="trend-volume"><span>入 {{ point.stockInQuantity }}</span><span>出 {{ point.stockOutQuantity }}</span></div></div></div></article>
      </section>

      <section class="overview-section activity-section"><div class="overview-section-head"><div><p class="eyebrow">RECENT ACTIVITY</p><h2>近期业务动态</h2><span>最近 {{ data.activities.length }} 条库存变化记录</span></div><button class="text-button" @click="navigate({ module: 'warehouse' })">查看库存流水 →</button></div><div v-if="!data.activities.length" class="overview-empty"><span>⌁</span><strong>暂无库存动态</strong><small>采购入库、销售出库或手工登记后会在这里显示</small></div><div v-else class="activity-list"><div v-for="activity in data.activities" :key="activity.flowNo" class="activity-row"><time>{{ time(activity.time) }}</time><span :class="['activity-type', activity.changeQuantity > 0 ? 'in' : 'out']">{{ activity.businessType }}</span><div><strong>{{ activity.productName }}</strong><small>{{ activity.warehouseName }} · {{ activity.sourceNo || '手工登记' }}</small></div><b :class="activity.changeQuantity > 0 ? 'positive' : 'negative'">{{ activity.changeQuantity > 0 ? '+' : '' }}{{ activity.changeQuantity }}</b></div></div></section>
    </template>
  </div>
</template>

<style scoped>
.overview-page { max-width: 1480px; }.overview-heading { margin-bottom: 12px; }.overview-actions { display: flex; align-items: center; gap: 10px; }.overview-updated { color: #8b98aa; font-size: 10px; }.overview-range { display: inline-flex; gap: 3px; margin-bottom: 17px; padding: 4px; border: 1px solid #e0e7f1; border-radius: 11px; background: #fff; }.overview-range button { border: 0; border-radius: 7px; padding: 7px 12px; color: #718097; background: transparent; font-size: 10px; font-weight: 700; }.overview-range button.active { color: #315fd6; background: #edf2ff; }.overview-loading { display: flex; align-items: center; justify-content: center; gap: 12px; min-height: 320px; color: #77869e; font-size: 12px; }.overview-error { margin: 0 0 16px; }.overview-error button { padding: 0; font-size: 10px; font-weight: 700; }.overview-metrics { display: grid; grid-template-columns: repeat(4, minmax(0, 1fr)); gap: 14px; margin-bottom: 16px; }.overview-metric { display: flex; align-items: flex-start; gap: 11px; min-height: 130px; border: 1px solid #e5eaf3; border-radius: 14px; padding: 17px; background: #fff; box-shadow: 0 8px 22px #13254508; text-align: left; transition: .16s ease; }.overview-metric:hover, .todo-card:hover { transform: translateY(-2px); border-color: #cbd8f4; box-shadow: 0 12px 28px #13254514; }.metric-symbol, .todo-icon { display: grid; flex: 0 0 auto; place-items: center; border-radius: 10px; font-size: 13px; font-weight: 800; }.metric-symbol { width: 35px; height: 35px; }.overview-metric > div { min-width: 0; display: grid; gap: 5px; }.overview-metric small, .overview-metric em { color: #8593a8; font-size: 9px; font-style: normal; }.overview-metric strong { overflow: hidden; color: #25354f; font-size: 18px; text-overflow: ellipsis; white-space: nowrap; }.overview-metric > b, .todo-card > b { margin-left: auto; color: #9aa7ba; font-size: 14px; }.overview-metric.blue .metric-symbol { color: #2f61cf; background: #e9f0ff; }.overview-metric.violet .metric-symbol { color: #7357be; background: #f0ebfb; }.overview-metric.green .metric-symbol { color: #258262; background: #e6f6ef; }.overview-metric.orange .metric-symbol { color: #b96a27; background: #fff0e4; }.overview-section { border: 1px solid #e5eaf3; border-radius: 15px; background: #fff; box-shadow: 0 8px 22px #13254508; }.overview-section-head { display: flex; align-items: flex-start; justify-content: space-between; gap: 14px; padding: 18px 20px; border-bottom: 1px solid #edf0f5; }.overview-section-head .eyebrow { margin-bottom: 5px; font-size: 8px; }.overview-section-head h2 { margin: 0; color: #263650; font-size: 15px; }.overview-section-head span { display: block; margin-top: 4px; color: #8b98aa; font-size: 9px; }.overview-section-head i { margin-top: 7px; border-radius: 99px; padding: 5px 8px; color: #4f679b; background: #f0f4fb; font-size: 9px; font-style: normal; white-space: nowrap; }.overview-todos { margin-bottom: 16px; }.todo-grid { display: grid; grid-template-columns: repeat(4, 1fr); }.todo-card { display: flex; align-items: center; gap: 10px; min-height: 102px; border: 0; border-right: 1px solid #edf0f5; padding: 17px 18px; background: #fff; text-align: left; transition: .16s ease; }.todo-card:last-child { border-right: 0; }.todo-icon { width: 31px; height: 31px; color: #315fd6; background: #edf2ff; font-size: 11px; }.todo-card > div { display: grid; gap: 3px; }.todo-card small, .todo-card em { color: #8a97aa; font-size: 9px; font-style: normal; }.todo-card strong { color: #263750; font-size: 21px; }.overview-columns { display: grid; grid-template-columns: 1.08fr .92fr; gap: 16px; margin-bottom: 16px; }.risk-list, .trend-list, .activity-list { display: grid; }.risk-row { display: grid; grid-template-columns: 8px minmax(120px, 1fr) 90px auto; align-items: center; gap: 10px; padding: 13px 18px; border-bottom: 1px solid #f0f2f6; }.risk-row:last-child, .trend-row:last-child, .activity-row:last-child { border-bottom: 0; }.risk-dot { width: 7px; height: 7px; border-radius: 50%; }.risk-dot.critical { background: #d95e5e; box-shadow: 0 0 0 4px #fff0f0; }.risk-dot.low { background: #e29342; box-shadow: 0 0 0 4px #fff6e9; }.risk-main, .activity-row > div { min-width: 0; display: grid; gap: 4px; }.risk-main strong, .activity-row strong { overflow: hidden; color: #31415d; font-size: 10px; text-overflow: ellipsis; white-space: nowrap; }.risk-main small, .risk-stock small, .risk-gap small, .activity-row small { color: #95a1b3; font-size: 8px; }.risk-stock { display: grid; gap: 4px; text-align: right; }.risk-stock strong { color: #40516d; font-size: 10px; }.risk-gap { display: grid; justify-items: end; gap: 4px; }.risk-gap button { border: 0; color: #315fd6; background: transparent; font-size: 9px; font-weight: 700; }.trend-row { display: grid; grid-template-columns: 45px 1fr 1fr auto; align-items: center; gap: 9px; padding: 12px 18px; border-bottom: 1px solid #f0f2f6; }.trend-row time { color: #8d9aac; font-size: 9px; }.trend-row > div { display: grid; gap: 4px; }.trend-row small, .trend-volume { color: #92a0b2; font-size: 8px; }.trend-row strong { color: #3a4b67; font-size: 10px; }.trend-volume { display: grid !important; justify-items: end; gap: 3px !important; white-space: nowrap; }.overview-empty { display: grid; justify-items: center; gap: 7px; min-height: 210px; padding: 40px; color: #8190a5; text-align: center; }.overview-empty > span { display: grid; width: 34px; height: 34px; place-items: center; border-radius: 10px; color: #7590c6; background: #edf3fc; font-size: 16px; }.overview-empty strong { color: #52637d; font-size: 11px; }.overview-empty small { color: #98a5b5; font-size: 9px; }.activity-row { display: grid; grid-template-columns: 73px 76px minmax(160px, 1fr) 55px; align-items: center; gap: 11px; padding: 13px 20px; border-bottom: 1px solid #f0f2f6; }.activity-row time { color: #8c99ab; font-size: 9px; }.activity-type { width: fit-content; border-radius: 99px; padding: 4px 7px; font-size: 8px; font-weight: 700; white-space: nowrap; }.activity-type.in { color: #267d5e; background: #e7f5ee; }.activity-type.out { color: #315fae; background: #e9f0ff; }.activity-row b { text-align: right; font-size: 12px; }.activity-row b.positive { color: #2a906b; }.activity-row b.negative { color: #3469c5; }
@media (max-width: 1080px) { .overview-metrics { grid-template-columns: repeat(2, 1fr); }.todo-grid { grid-template-columns: repeat(2, 1fr); }.todo-card:nth-child(2) { border-right: 0; }.todo-card:nth-child(-n+2) { border-bottom: 1px solid #edf0f5; }.overview-columns { grid-template-columns: 1fr; } }
@media (max-width: 650px) { .overview-actions { align-items: flex-end; flex-direction: column; }.overview-metrics, .todo-grid { grid-template-columns: 1fr; }.todo-card, .todo-card:nth-child(2) { border-right: 0; border-bottom: 1px solid #edf0f5; }.todo-card:last-child { border-bottom: 0; }.risk-row { grid-template-columns: 8px minmax(90px, 1fr) auto; }.risk-stock { display: none; }.trend-row { grid-template-columns: 42px 1fr 1fr; }.trend-volume { display: none !important; }.activity-row { grid-template-columns: 58px 62px minmax(90px, 1fr) 40px; gap: 7px; padding: 12px 14px; }.activity-row small { overflow: hidden; text-overflow: ellipsis; white-space: nowrap; } }
</style>
