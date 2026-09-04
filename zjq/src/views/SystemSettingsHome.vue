<script setup>
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { api } from '../api/index.js'
import { apiConfig } from '../api/config.js'

const router = useRouter()
const user = ref(null)

// 系统设置六个功能板块（对齐《系统设置开发文档》SET-01 ~ SET-06），一行一个入口
// perms 为对应功能页面的权限码；SET-01 登录与当前用户始终显示，不设 perms
const modules = [
  { no: 'SET-01', title: '登录与当前用户', icon: '⌂' },
  { no: 'SET-02', title: '用户管理', icon: '◈', path: '/setting/users', perms: 'system:user:list' },
  { no: 'SET-03', title: '角色权限', icon: '⌑', path: '/setting/roles', perms: 'system:role:list' },
  { no: 'SET-04', title: '仓库管理', icon: '▤', path: '/setting/warehouses', perms: 'base:warehouse:list' },
  { no: 'SET-05', title: '供应商管理', icon: '◉', path: '/setting/suppliers', perms: 'base:supplier:list' },
  { no: 'SET-06', title: '客户管理', icon: '◎', path: '/setting/customers', perms: 'base:customer:list' }
]

// 当前用户权限码：登录时已并入 z_user 持久化，与路由守卫读取同一来源
function readPermissions() {
  try {
    const raw = localStorage.getItem(apiConfig.userKey)
    if (!raw) return []
    const parsed = JSON.parse(raw)
    return Array.isArray(parsed.permissions) ? parsed.permissions : []
  } catch {
    return []
  }
}

const permissions = ref(readPermissions())
// 按权限过滤可见板块：无 perms 的板块（SET-01）始终显示
const visibleModules = computed(() => modules.filter((m) => !m.perms || permissions.value.includes(m.perms)))

onMounted(async () => {
  permissions.value = readPermissions()
  try {
    user.value = await api.me()
  } catch {
    router.replace('/login')
  }
})

async function logout() {
  try {
    await api.logout()
  } catch {
    // ignore
  }
  localStorage.removeItem(apiConfig.tokenKey)
  localStorage.removeItem(apiConfig.userKey)
  router.replace('/login')
}
</script>

<template>
  <div class="page-content">
    <section class="page-heading">
      <div>
        <p class="eyebrow">SYSTEM · SETTINGS</p>
        <h1>系统设置</h1>
      </div>
      <div v-if="user" class="home-user">
        <span class="home-user-avatar">{{ String(user.realName || user.username || '?').charAt(0) }}</span>
        <div>
          <strong>{{ user.roleName || user.roleCode || '未分配职位' }} · {{ user.realName || user.username }}</strong>
          <small>当前职位可见板块已按权限过滤</small>
        </div>
      </div>
    </section>

    <section class="content-card settings-home">
      <div class="list-heading">
        <div><h2>功能板块</h2></div>
      </div>

      <div class="settings-modules">
        <!-- SET-01 登录与当前用户：内嵌展示当前用户信息（始终显示） -->
        <div class="settings-module">
          <span class="module-icon">⌂</span>
          <div class="module-main">
            <strong>登录与当前用户</strong>
            <small v-if="user">{{ user.roleName || user.roleCode || '未分配职位' }} · {{ user.realName || user.username }}</small>
          </div>
          <button class="module-exit" @click="logout">退出登录</button>
        </div>

        <!-- SET-02 ~ SET-06：按权限过滤后渲染，点击进入对应功能 -->
        <router-link v-for="m in visibleModules.filter((x) => x.path)" :key="m.no" :to="m.path" class="settings-module">
          <span class="module-icon">{{ m.icon }}</span>
          <div class="module-main">
            <strong>{{ m.title }}</strong>
          </div>
          <span class="module-enter">进入 →</span>
        </router-link>
      </div>
    </section>
  </div>
</template>
