<script setup>
import { ref } from 'vue'

const emit = defineEmits(['authenticated'])
const username = ref('')
const password = ref('')
const showPassword = ref(false)
const submitting = ref(false)
const error = ref('')
const demoAccounts = [
  { username: 'admin', password: 'admin123', role: '管理员' },
  { username: 'purchase', password: '123456', role: '采购员' },
  { username: 'saler', password: '123456', role: '销售员' },
  { username: 'warehouse', password: '123456', role: '仓管员' },
  { username: 'finance', password: '123456', role: '经营管理者' }
]

async function login() {
  error.value = ''
  submitting.value = true
  try {
    const response = await fetch('/api/auth/login', {
      method: 'POST', headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ username: username.value.trim(), password: password.value })
    })
    if (!response.ok) {
      let message = '用户名或密码错误'
      try { const body = await response.json(); message = body.detail || body.message || message } catch (_) { /* ignore */ }
      throw new Error(message)
    }
    localStorage.setItem('nova-last-username', username.value.trim())
    emit('authenticated', await response.json())
  } catch (exception) { error.value = exception.message || '暂时无法登录，请稍后重试' }
  finally { submitting.value = false }
}

try { username.value = localStorage.getItem('nova-last-username') || '' } catch (_) { /* local storage unavailable */ }
</script>

<template>
  <main class="login-page">
    <div class="login-grid"></div><div class="login-orbit orbit-one"></div><div class="login-orbit orbit-two"></div>
    <section class="login-intro"><div class="intro-mark">N</div><p>NOVA ERP</p><h1>让每一笔业务<br />都有据可循</h1><span>采购、销售、仓储与经营数据的一体化管理平台</span><div class="intro-points"><i></i><i></i><i></i></div></section>
    <form class="login-card" @submit.prevent="login"><div class="login-brand"><span class="login-brand-mark">N</span><div><strong>NOVA ERP</strong><small>企业资源管理平台</small></div></div><div class="login-title"><p>WELCOME BACK</p><h2>登录工作台</h2><span>请输入你的账号信息，进入经营概览。</span></div><label>用户名<input v-model.trim="username" autocomplete="username" maxlength="50" placeholder="请输入用户名" required autofocus /></label><label>密码<div class="password-field"><input v-model="password" :type="showPassword ? 'text' : 'password'" autocomplete="current-password" placeholder="请输入密码" required /><button type="button" @click="showPassword = !showPassword">{{ showPassword ? '隐藏' : '显示' }}</button></div></label><div v-if="error" class="login-error"><b>!</b>{{ error }}</div><button class="login-submit" type="submit" :disabled="submitting">{{ submitting ? '正在验证身份…' : '登录系统 →' }}</button><div class="login-demo-accounts"><p>课堂演示账号（点击可填充）</p><button v-for="account in demoAccounts" :key="account.username" type="button" class="demo-account" @click="username = account.username; password = account.password"><span>{{ account.username }} / {{ account.password }}</span><em>{{ account.role }}</em></button></div><p class="login-security">受保护的业务工作台 · 请勿在公共设备保存密码</p></form>
  </main>
</template>

<style scoped>
.login-page { min-height:100vh; overflow:hidden; position:relative; display:grid; place-items:center; background:radial-gradient(circle at 18% 20%,#255cb7 0,transparent 30%),radial-gradient(circle at 80% 80%,#123465 0,transparent 34%),linear-gradient(135deg,#071b3b,#112f61 56%,#173d78); color:#fff; font-family:inherit; }.login-grid { position:absolute; inset:0; opacity:.18; background-image:linear-gradient(rgba(205,224,255,.25) 1px,transparent 1px),linear-gradient(90deg,rgba(205,224,255,.25) 1px,transparent 1px); background-size:52px 52px; mask-image:linear-gradient(90deg,#000,transparent 84%); }.login-orbit { position:absolute; border:1px solid rgba(171,207,255,.18); border-radius:50%; }.orbit-one { width:780px; height:780px; left:-270px; bottom:-420px; }.orbit-two { width:560px; height:560px; right:-300px; top:-260px; }.login-intro { position:absolute; width:min(420px,30vw); left:max(8vw,40px); top:50%; transform:translateY(-50%); z-index:1; }.intro-mark,.login-brand-mark { display:grid; place-items:center; border-radius:15px; background:linear-gradient(145deg,#5d98ff,#2858bf); box-shadow:0 12px 28px rgba(4,19,48,.34); font-weight:900; }.intro-mark { width:58px; height:58px; font-size:29px; }.login-intro>p { margin:30px 0 10px; color:#8bb6ff; font-size:12px; font-weight:800; letter-spacing:2.8px; }.login-intro h1 { margin:0; font-size:clamp(32px,3vw,50px); line-height:1.2; letter-spacing:-1.4px; }.login-intro span { display:block; margin-top:20px; color:#b9cbe9; line-height:1.8; }.intro-points { display:flex; gap:9px; margin-top:42px; }.intro-points i { width:58px; height:3px; border-radius:3px; background:rgba(138,182,255,.25); }.intro-points i:first-child { background:#80aeff; }.login-card { position:relative; z-index:2; width:min(420px,calc(100vw - 34px)); box-sizing:border-box; border:1px solid rgba(255,255,255,.55); border-radius:24px; padding:35px 38px 28px; color:#18335e; background:rgba(255,255,255,.96); box-shadow:0 28px 75px rgba(2,15,42,.35); backdrop-filter:blur(15px); }.login-brand { display:flex; gap:11px; align-items:center; }.login-brand-mark { width:35px; height:35px; border-radius:10px; font-size:18px; }.login-brand strong,.login-brand small { display:block; }.login-brand strong { font-size:15px; letter-spacing:.7px; }.login-brand small { margin-top:2px; font-size:11px; color:#8798b1; }.login-title { margin:30px 0 23px; }.login-title p { margin:0 0 7px; color:#4c7ee1; font-weight:800; font-size:10px; letter-spacing:2px; }.login-title h2 { margin:0; font-size:28px; letter-spacing:-.6px; }.login-title span { display:block; margin-top:8px; color:#8393aa; font-size:13px; }.login-card label { display:block; color:#536883; font-size:13px; font-weight:700; margin:15px 0; }.login-card input { width:100%; box-sizing:border-box; border:1px solid #dce5f1; border-radius:10px; background:#fbfcfe; margin-top:7px; padding:12px 13px; outline:none; font:inherit; color:#18335e; transition:border-color .18s,box-shadow .18s; }.login-card input:focus { border-color:#4d7fe3; box-shadow:0 0 0 3px rgba(61,112,220,.12); }.password-field { position:relative; }.password-field input { padding-right:58px; }.password-field button { position:absolute; right:8px; bottom:8px; border:0; background:transparent; color:#4b74c8; cursor:pointer; font-weight:700; font-size:12px; padding:5px; }.login-error { display:flex; gap:8px; align-items:center; color:#bf4747; background:#fff0f0; border-radius:9px; padding:10px 11px; font-size:13px; }.login-error b { display:grid; place-items:center; width:17px; height:17px; border-radius:50%; background:#da5d5d; color:#fff; }.login-submit { width:100%; margin-top:19px; border:0; border-radius:11px; padding:13px; cursor:pointer; color:white; background:linear-gradient(100deg,#326bdd,#234fae); box-shadow:0 10px 20px rgba(45,91,195,.26); font:inherit; font-weight:800; transition:transform .18s,box-shadow .18s; }.login-submit:hover:not(:disabled) { transform:translateY(-1px); box-shadow:0 13px 24px rgba(45,91,195,.33); }.login-submit:disabled { opacity:.7; cursor:wait; }.login-demo-accounts { margin-top:18px; padding:13px 14px; border:1px solid #e1e8f2; border-radius:12px; background:#f6f8fb; }.login-demo-accounts>p { margin:0 0 8px; color:#58739a; font-size:12px; font-weight:800; }.demo-account { width:100%; display:flex; justify-content:space-between; gap:10px; border:0; border-top:1px solid #e8edf4; padding:7px 0 0; margin-top:7px; background:transparent; color:#45658d; cursor:pointer; text-align:left; font:inherit; font-size:12px; }.demo-account:hover { color:#2459b4; }.demo-account em { color:#8798ad; font-style:normal; white-space:nowrap; }.login-security { margin:16px 0 0; text-align:center; color:#93a1b4; font-size:11px; } @media(max-width:820px) { .login-intro { display:none; }.login-card { padding:30px 27px 24px; } }
</style>
