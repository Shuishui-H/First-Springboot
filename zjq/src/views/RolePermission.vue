<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { api } from '../api/index.js'

const roles = ref([])
const loading = ref(false)
const error = ref('')
const notice = ref('')

const menus = ref([])
const menuTree = ref([])
const checkedIds = ref(new Set())
const currentRole = ref(null)
const showPermModal = ref(false)
const saving = ref(false)
const permLoadError = ref('')

const BUILTIN_ROLES = ['admin', 'purchaser', 'purchase_manager', 'seller', 'sales_manager', 'warehouse', 'manager']

function showNotice(message) {
  notice.value = message
  window.setTimeout(() => {
    if (notice.value === message) notice.value = ''
  }, 2800)
}

async function loadRoles() {
  loading.value = true
  error.value = ''
  try {
    const res = await api.list('roles', { page: 1, size: 100, filters: {} })
    roles.value = res.records
  } catch (exception) {
    error.value = exception.message || '加载角色失败'
  } finally {
    loading.value = false
  }
}

function buildTree(list) {
  const tops = list.filter((m) => m.parentId === 0 || !m.parentId)
  return tops.map((top) => ({
    ...top,
    children: list
      .filter((m) => m.parentId === top.id)
      .map((child) => ({
        ...child,
        children: list.filter((m) => m.parentId === child.id)
      }))
  }))
}

async function openPermissionModal(role) {
  currentRole.value = role
  permLoadError.value = ''
  checkedIds.value = new Set()
  showPermModal.value = true
  try {
    const [menuRes, roleMenus] = await Promise.all([
      api.getMenus(),
      api.getRoleMenus(role.roleCode)
    ])
    menus.value = menuRes
    menuTree.value = buildTree(menuRes)
    checkedIds.value = new Set(roleMenus)
  } catch (exception) {
    permLoadError.value = exception.message || '加载权限菜单失败'
  }
}

function descendants(node) {
  const result = [node]
  ;(node.children || []).forEach((child) => {
    result.push(...descendants(child))
  })
  return result
}

function nodeState(node) {
  if (checkedIds.value.has(node.id)) return 'checked'
  if ((node.children || []).some((c) => nodeState(c) !== 'none')) return 'partial'
  return 'none'
}

function toggleNode(node) {
  const ids = descendants(node).map((n) => n.id)
  const isChecked = checkedIds.value.has(node.id)
  if (isChecked) {
    ids.forEach((id) => checkedIds.value.delete(id))
  } else {
    ids.forEach((id) => checkedIds.value.add(id))
  }
  // 强制触发响应式
  checkedIds.value = new Set(checkedIds.value)
}

function toggleAll() {
  const allIds = menus.value.map((m) => m.id)
  const allChecked = allIds.every((id) => checkedIds.value.has(id))
  checkedIds.value = new Set(allChecked ? [] : allIds)
}

async function saveMenus() {
  saving.value = true
  permLoadError.value = ''
  try {
    await api.saveRoleMenus(currentRole.value.roleCode, [...checkedIds.value])
    showPermModal.value = false
    await loadRoles()
    showNotice(`已保存「${currentRole.value.roleName}」的权限配置`)
  } catch (exception) {
    permLoadError.value = exception.message || '保存失败'
  } finally {
    saving.value = false
  }
}

async function toggleRoleStatus(role) {
  if (role.roleCode === 'admin') {
    error.value = '系统内置管理员角色（admin）不允许停用，防止系统失去管理入口'
    return
  }
  error.value = ''
  try {
    const next = role.status === 1 ? 0 : 1
    await api.toggleStatus('roles', role.id, next)
    await loadRoles()
    showNotice(`角色「${role.roleName}」已${next === 1 ? '启用' : '停用'}`)
  } catch (exception) {
    error.value = exception.message || '状态更新失败'
  }
}

const allChecked = computed(() => {
  if (!menus.value.length) return false
  return menus.value.every((m) => checkedIds.value.has(m.id))
})

onMounted(loadRoles)
</script>

<template>
  <div class="page-content">
    <section class="page-heading">
      <div>
        <p class="eyebrow">ACCESS · ROLE & PERMISSION</p>
        <h1>角色权限</h1>
        <p>按角色配置菜单与按钮权限；停用角色后，对应用户的菜单与接口权限立即失效。</p>
      </div>
      <div class="heading-actions">
        <button class="secondary-button" @click="loadRoles"><span>↻</span> 刷新数据</button>
      </div>
    </section>

    <section class="metrics">
      <article class="metric-card blue"><div class="metric-icon">角</div><div><span>角色总数</span><strong>{{ roles.length }}</strong><small>预置 7 类业务角色</small></div><b>RBAC</b></article>
      <article class="metric-card green"><div class="metric-icon">启</div><div><span>已启用</span><strong>{{ roles.filter((r) => r.status === 1).length }}</strong><small>可正常分配权限</small></div><b>正常</b></article>
      <article class="metric-card orange"><div class="metric-icon">停</div><div><span>已停用</span><strong>{{ roles.filter((r) => r.status === 0).length }}</strong><small>停用后权限失效</small></div><b>冻结</b></article>
      <article class="metric-card violet"><div class="metric-icon">权</div><div><span>权限点</span><strong>{{ menus.filter((m) => m.menuType === 2).length }}</strong><small>按钮级权限标识</small></div><b>perms</b></article>
    </section>

    <section class="content-card">
      <div class="list-heading">
        <div><h2>角色列表</h2><p>内置角色不可删除；admin 不可停用，仅可通过配置权限调整可见范围。</p></div>
        <button class="refresh-button" :disabled="loading" @click="loadRoles">↻ <span>{{ loading ? '刷新中' : '刷新数据' }}</span></button>
      </div>

      <div v-if="error" class="message error-message"><span>!</span><p>{{ error }}</p><button @click="error = ''">×</button></div>

      <div class="table-wrap">
        <table style="min-width: 760px;">
          <thead><tr><th>角色编码</th><th>角色名称</th><th>状态</th><th>备注</th><th class="action-column">操作</th></tr></thead>
          <tbody>
            <tr v-if="loading"><td colspan="5" class="empty-state"><span class="spinner"></span><strong>正在读取角色数据</strong><small>请稍候…</small></td></tr>
            <tr v-else-if="roles.length === 0"><td colspan="5" class="empty-state"><span class="empty-icon">⌕</span><strong>暂无角色数据</strong></td></tr>
            <tr v-for="role in roles" v-else :key="role.roleCode">
              <td><span class="code-chip">{{ role.roleCode }}<span v-if="role.roleCode === 'admin'" style="margin-left:5px;color:#d15b5b;">内置</span></span></td>
              <td><div class="cell-main"><strong>{{ role.roleName }}</strong><code>{{ BUILTIN_ROLES.includes(role.roleCode) ? '系统预置角色' : '自定义角色' }}</code></div></td>
              <td><button :class="['status-switch', { off: role.status !== 1 }]" @click="toggleRoleStatus(role)"><i></i>{{ role.status === 1 ? '启用' : '停用' }}</button></td>
              <td><span :class="{ 'cell-note': role.remark }">{{ role.remark || '—' }}</span></td>
              <td class="row-actions">
                <button class="restock-button" @click="openPermissionModal(role)">配置权限</button>
                <button @click="toggleRoleStatus(role)">{{ role.status === 1 ? '停用' : '启用' }}</button>
              </td>
            </tr>
          </tbody>
        </table>
      </div>
      <footer class="list-footer"><span>显示 {{ roles.length }} 条角色记录</span><span>权限双重控制：前端隐藏菜单 + 后端接口鉴权（最终边界）</span></footer>
    </section>

    <div v-if="showPermModal" class="modal-backdrop" @click.self="showPermModal = false">
      <div class="modal" style="width: min(560px, 100%);">
        <div class="modal-title">
          <div>
            <p class="eyebrow">ROLE PERMISSION</p>
            <h2>配置权限 · {{ currentRole ? currentRole.roleName : '' }}</h2>
            <p>勾选该角色可见的菜单与按钮权限点，保存后立即生效。</p>
          </div>
          <button type="button" class="icon-button" @click="showPermModal = false">×</button>
        </div>

        <div v-if="permLoadError" class="message error-message compact"><span>!</span><p>{{ permLoadError }}</p></div>

        <div v-else class="permission-tree">
          <div class="tree-row" style="font-weight:700; color:#24324b;">
            <label>
              <input type="checkbox" :checked="allChecked" @change="toggleAll" />
              全选 / 全不选
            </label>
            <span class="tree-count">已选 {{ checkedIds.size }} 项</span>
          </div>
          <div v-for="top in menuTree" :key="top.id" class="tree-node">
            <div class="tree-row level-1">
              <label><input type="checkbox" :checked="nodeState(top) !== 'none'" :indeterminate="nodeState(top) === 'partial'" @change="toggleNode(top)" />{{ top.menuName }}</label>
              <span class="tree-count">顶层菜单</span>
            </div>
            <div v-for="child in top.children" :key="child.id" class="tree-node">
              <div class="tree-row level-2">
                <label><input type="checkbox" :checked="nodeState(child) !== 'none'" :indeterminate="nodeState(child) === 'partial'" @change="toggleNode(child)" />{{ child.menuName }}</label>
                <span class="tree-count">{{ child.children.length }} 个按钮</span>
              </div>
              <div v-for="leaf in child.children" :key="leaf.id" class="tree-row level-3">
                <label>
                  <input type="checkbox" :checked="nodeState(leaf) !== 'none'" @change="toggleNode(leaf)" />
                  <span class="tree-leaf">{{ leaf.menuName }}</span>
                </label>
                <code style="color:#9aa5b5; font-size:9px;">{{ leaf.perms }}</code>
              </div>
            </div>
          </div>
        </div>

        <div class="modal-actions">
          <button type="button" class="secondary-button" @click="showPermModal = false">取消</button>
          <button type="button" class="primary-button" :disabled="saving" @click="saveMenus">{{ saving ? '保存中…' : '保存配置' }}</button>
        </div>
      </div>
    </div>

    <transition name="toast"><div v-if="notice" class="toast-message"><span>✓</span>{{ notice }}</div></transition>
  </div>
</template>

<style scoped>
.cell-note { color: #5f6e87; }
</style>
