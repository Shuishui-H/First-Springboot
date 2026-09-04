<script setup>
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { api } from '../api/index.js'
import { apiConfig } from '../api/config.js'

const route = useRoute()
const router = useRouter()
const user = ref(null)
const menuOpen = ref(false)
const roleName = ref('')

// 侧边栏与 erp-web 主界面保持一致：工作台 / 业务管理 / 分析与设置三组完整导航，
// 当前定位在「系统设置」模块，因此系统设置项高亮（current）。
const navGroups = [
  {
    label: '工作台',
    items: [{ key: 'overview', icon: '⌂', label: '经营概览', hint: '主工程业务模块' }]
  },
  {
    label: '业务管理',
    items: [
      { key: 'products', icon: '◇', label: '商品档案', hint: '主工程业务模块' },
      { key: 'purchases', icon: '▤', label: '采购管理', hint: '主工程业务模块' },
      { key: 'sales', icon: '▣', label: '销售管理', muted: true },
      { key: 'warehouse', icon: '▥', label: '仓储管理', muted: true }
    ]
  },
  {
    label: '分析与设置',
    items: [
      { key: 'reports', icon: '⌁', label: '业务报表', muted: true },
      { key: 'settings', icon: '⚙', label: '系统设置', current: true }
    ]
  }
]

const breadcrumbTitle = computed(() => {
  if (route.meta && route.meta.title) return route.meta.title
  if (route.path === '/setting') return '功能板块'
  return '系统设置'
})

const currentDate = new Intl.DateTimeFormat('zh-CN', {
  month: 'long',
  day: 'numeric',
  weekday: 'short'
}).format(new Date())

async function loadMe() {
  if (!localStorage.getItem(apiConfig.tokenKey)) {
    router.replace('/login')
    return
  }
  try {
    user.value = await api.me()
    roleName.value = user.value.roleName || ''
  } catch {
    localStorage.removeItem(apiConfig.tokenKey)
    localStorage.removeItem(apiConfig.userKey)
    router.replace('/login')
  }
}

async function logout() {
  try {
    await api.logout()
  } catch {
    // ignore
  }
  localStorage.removeItem(apiConfig.tokenKey)
  localStorage.removeItem(apiConfig.userKey)
  menuOpen.value = false
  router.replace('/login')
}

const avatarText = computed(() => {
  const name = user.value ? user.value.realName || user.value.username : '系'
  return name.slice(0, 1)
})

onMounted(loadMe)
</script>

<template>
  <div class="app-frame">
    <aside class="sidebar">
      <div class="brand">
        <span class="brand-mark">N</span>
        <div><strong>{{ apiConfig.appName }}</strong><small>企业资源管理平台</small></div>
      </div>

      <nav class="nav-menu">
        <template v-for="group in navGroups" :key="group.label">
          <p class="nav-label">{{ group.label }}</p>
          <template v-for="item in group.items" :key="item.key">
            <router-link v-if="item.current" to="/setting" class="nav-item active">
              <span>{{ item.icon }}</span>{{ item.label }}
            </router-link>
            <a
              v-else
              :href="item.path || '#'"
              :class="['nav-item', { muted: item.muted }]"
              :title="item.hint || (item.muted ? '待建设' : '')"
              @click.prevent
            >
              <span>{{ item.icon }}</span>{{ item.label }}<small v-if="item.hint || item.muted">{{ item.hint || '待建设' }}</small>
            </a>
          </template>
        </template>
      </nav>

      <div class="sidebar-status">
        <span class="status-dot"></span>
        <div><strong>系统设置模块</strong><small>SET-01 ~ SET-06</small></div>
      </div>
    </aside>

    <main class="workspace">
      <header class="topbar">
        <div class="breadcrumb"><span>进销存管理</span><b>/</b><strong>系统设置</strong><b>/</b><span>{{ breadcrumbTitle }}</span></div>
        <div class="topbar-right">
          <span class="date-label">{{ currentDate }}</span>
          <div class="user-card" @mouseenter="menuOpen = true" @mouseleave="menuOpen = false">
            <span>{{ avatarText }}</span>
            <div><strong>{{ user ? user.realName || user.username : '登录中' }}</strong><small>{{ roleName }}</small></div>
            <div v-if="menuOpen" class="user-menu">
              <button @click="logout">退出登录</button>
            </div>
          </div>
        </div>
      </header>

      <router-view />
    </main>
  </div>
</template>
