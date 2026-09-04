<script setup>
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import { api } from '../api/index.js'
import { apiConfig } from '../api/config.js'

const router = useRouter()
const username = ref('admin')
const password = ref('Admin@123')
const error = ref('')
const submitting = ref(false)

async function login() {
  error.value = ''
  if (!username.value || !password.value) {
    error.value = '请输入账号和密码'
    return
  }
  submitting.value = true
  try {
    const res = await api.login({ username: username.value.trim(), password: password.value })
    // 将权限码数组一并并入 z_user 持久化，供路由守卫与首页板块过滤使用
    const storedUser = { ...(res.user || {}), permissions: res.permissions || [] }
    localStorage.setItem(apiConfig.tokenKey, res.token)
    localStorage.setItem(apiConfig.userKey, JSON.stringify(storedUser))
    router.replace('/setting')
  } catch (exception) {
    error.value = exception.message || '登录失败'
  } finally {
    submitting.value = false
  }
}
</script>

<template>
  <div class="login-page">
    <aside class="login-brand">
      <div class="brand">
        <span class="brand-mark">N</span>
        <div><strong>{{ apiConfig.appName }}</strong><small>企业资源管理平台</small></div>
      </div>
      <div>
        <h1>进销存 · 系统设置</h1>
        <p>统一管理用户、角色权限与仓库/供应商/客户等基础主数据，<br />为采购、销售、库存等业务模块提供可信数据底座。</p>
        <ul class="login-feature">
          <li><span>✓</span> SET-01 账号登录 · 会话保持 · 退出入口</li>
          <li><span>✓</span> SET-02 用户管理 · SET-03 角色权限</li>
          <li><span>✓</span> SET-04/05/06 仓库 · 供应商 · 客户主数据</li>
        </ul>
      </div>
      <p class="login-footer">NOVA ERP · System Settings Module · SET-01 ~ SET-06</p>
    </aside>

    <section class="login-panel">
      <form class="login-card" @submit.prevent="login">
        <h2>登录系统</h2>
        <p>使用账号密码登录以进入系统设置</p>
        <label class="login-field">
          账号
          <input v-model.trim="username" required autocomplete="username" placeholder="请输入账号" />
        </label>
        <label class="login-field">
          密码
          <input v-model="password" required type="password" autocomplete="current-password" placeholder="请输入密码" />
        </label>
        <div v-if="error" class="message error-message compact"><span>!</span><p>{{ error }}</p></div>
        <button class="primary-button" type="submit" :disabled="submitting">{{ submitting ? '登录中…' : '登 录' }}</button>
        <p class="login-tip">
          当前为内置 Mock 预览模式（api/config.js 的 useMock=true）。<br />
          演示账号：<code>admin</code> / <code>Admin@123</code>；也可用 purchaser01、seller01、whkeeper01（密码 123456）。<br />
          职位可见板块：admin 全部；采购员→供应商管理；销售员→客户管理；仓管员→仓库管理。不同职位登录后可见板块不同。
        </p>
      </form>
    </section>
  </div>
</template>
