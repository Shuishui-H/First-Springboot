---
AIGC:
    Label: "1"
    ContentProducer: 001191440300708461136T1XGW3
    ProduceID: a25d49aa6756ed428407a42c61493389_c16d8272a83011f1a604525400461939
    ReservedCode1: JZN5Z6/2c7yrTy3t1S/YQLRZE1hQ93wizNaUBabhGogZ7jVifUvBjxWC+3KbMZYnPslt/HNk4CKTIA8bJyzAas0b+AUlq2mgVfepLPZJmFYFCOY4x+EwLSvQn2fjkSLwNotDAiK0T/fkuz48FGzA2vj8iA+UgwKe3ogi1BnN1oxmC3As4XY3du0egJI=
    ContentPropagator: 001191440300708461136T1XGW3
    PropagateID: a25d49aa6756ed428407a42c61493389_c16d8272a83011f1a604525400461939
    ReservedCode2: JZN5Z6/2c7yrTy3t1S/YQLRZE1hQ93wizNaUBabhGogZ7jVifUvBjxWC+3KbMZYnPslt/HNk4CKTIA8bJyzAas0b+AUlq2mgVfepLPZJmFYFCOY4x+EwLSvQn2fjkSLwNotDAiK0T/fkuz48FGzA2vj8iA+UgwKe3ogi1BnN1oxmC3As4XY3du0egJI=
---

# ERP 系统设置前端模块（SET-01 ~ SET-06）

基于《系统设置开发文档》实现的前端工程，覆盖 SET-01 登录与当前用户、SET-02 用户管理、SET-03 角色权限、SET-04 仓库管理、SET-05 供应商管理、SET-06 客户管理六个 Must 级功能。

- 技术栈：Vue 3 + Vite + vue-router（Hash 模式），无第三方 UI 库，样式对齐 erp-web（深色侧栏 + 浅色内容区 + 卡片 + 结构化表格，蓝灰主调、圆润扁平）
- 默认使用**内置 Mock**（localStorage 持久化），无需后端即可完整体验；可一键切换真实后端（接口与《系统设置开发文档》完全对齐）
- 独立可运行，同时预留合并入口，可挂载进 erp-web 主工程与其它同学模块共存

## 一、目录结构

```
zjq
├─ package.json / vite.config.js / index.html
├─ README.md
└─ src
   ├─ main.js                  # 独立运行入口（合并时不用它）
   ├─ App.vue                  # 根组件（仅为路由出口）
   ├─ style.css                # 全局样式（对齐 erp-web 视觉）
   ├─ module/
   │  └─ index.js              # ★ 合并入口：根组件 + 路由 + 配置 + api
   ├─ api/
   │  ├─ config.js             # apiConfig：baseURL / useMock / tokenKey / setApiBase 等
   │  ├─ mock.js               # 内置 Mock 数据层（登录、菜单、角色、用户、主数据）
   │  └─ index.js              # 统一 api 封装（Mock 与真实后端自动切换）
   ├─ layout/
   │  └─ SystemLayout.vue      # 深色侧栏 + 顶栏（面包屑/用户信息/退出）+ 内容区
   ├─ components/
   │  └─ MasterDataTable.vue   # 通用主数据表格（仓库/供应商/客户共用）
   └─ views/
      ├─ LoginView.vue         # SET-01 登录页
      ├─ UserManage.vue        # SET-02 用户管理
      ├─ RolePermission.vue    # SET-03 角色权限
      ├─ WarehouseManage.vue   # SET-04 仓库管理
      ├─ SupplierManage.vue    # SET-05 供应商管理
      └─ CustomerManage.vue    # SET-06 客户管理
```

## 二、单独安装运行

前置：Node.js ≥ 18（开发环境 Node v24 已验证）。

```bash
cd D:\erp_proj\zjq
npm install
npm run dev
```

浏览器打开 http://localhost:5199 ，访问 http://localhost:5199/#/login 登录。

生产构建：

```bash
npm run build        # 产物输出到 dist/
npm run preview      # 本地预览构建产物
```

### 演示账号（Mock 模式）

| 账号 | 密码 | 角色 |
| --- | --- | --- |
| admin | Admin@123 | 管理员（全部功能） |
| purchaser01 | 123456 | 采购员（仅可见供应商等授权菜单） |
| seller01 | 123456 | 销售员 |
| whkeeper01 | 123456 | 仓库管理员 |

> 登录用户名/密码、菜单与角色权限分别校验；停用用户无法登录，停用角色权限即时失效（与《系统设置开发文档》业务逻辑一致）。

### 对接真实后端

1. 修改 `src/api/config.js` 中 `useMock: false`（或在运行时调用 `setApiConfig({ useMock: false })`）。
2. 后端运行在 8080 时无需改动 `baseURL`（vite 已代理 `/api → http://localhost:8080`）；否则通过 `setApiBase('http://xxx')` 覆盖。
3. 前端已按文档实现统一返回结构解析：`{ code, message, data, fieldErrors }`，409 编码冲突 / 401 登录失败 / 403 无权限均有对应提示。

接口对齐清单：登录 `POST /api/auth/login`、当前用户 `GET /api/auth/me`、退出 `POST /api/auth/logout`、用户 `GET/POST /api/system/users`、`PUT /api/system/users/:id`、`PUT .../status`、`PUT .../password`、角色 `GET /api/system/roles`、`GET/PUT /api/system/roles/:roleCode/menus`、`PUT .../status`、菜单 `GET /api/system/menus`、仓库/供应商/客户 `GET/POST /api/base/{warehouses|suppliers|customers}` 及启用下拉 `/enabled`。

## 三、合并进 erp-web 主工程

两种方式任选，推荐「变量导入」方式（不改动主工程构建配置）。

### 方式 A：变量导入（推荐）

1. 将本目录整个复制到主工程内，例如 `erp-web/src/settings/`（保持 `src/module/index.js`、`src/api`、`src/views`、`src/layout`、`src/components`、`src/style.css` 的相对结构不变）。

2. 在主工程路由（如 `erp-web/src/router/index.js`）中挂载：

```js
import { SystemSettingsLayout, createSystemSettingsRoutes, setApiConfig } from '@/settings/src/module/index.js'
// 若无 @ 别名可用相对路径：import ... from '../../settings/src/module/index.js'

// 对接共享后端 / 使用主工程的 API 基址
setApiConfig({ useMock: false, baseURL: '/api' })

export const routes = [
  // ……主工程既有路由、其它同学页面……
  {
    path: '/setting',
    component: SystemSettingsLayout,          // 深色侧栏 + 顶栏 + 内容区
    meta: { requiresAuth: true, title: '系统设置' },
    children: createSystemSettingsRoutes()    // users / roles / warehouses / suppliers / customers
  }
]
```

3. 在主工程侧栏导航中加入「系统设置」菜单（可直接用导出的 `systemSettingsNav`），并复用其 `requiresAuth` 登录守卫。子菜单建议带上 `meta.perms` 做权限过滤（组件内部同样按 `permissions` 控制按钮显隐）。

4. 主工程 `main.js` 仅需注册主工程自己的路由；本模块不执行任何挂载副操作。

### 方式 B：组件级复用

若主工程已有成熟的布局与 Tab 体系，可只复用具体页面组件（页面含各自筛选栏/表格/弹窗，无外部依赖）：

```js
import { UserManage, RolePermission, WarehouseManage, SupplierManage, CustomerManage } from '@/settings/src/module/index.js'
```

然后把它们放进主工程的任意路由/标签页，顶部栏、面包屑由主工程统一渲染（页面自身不强制包裹 SystemLayout）。

### 其它模块共存约定（协同）

- 本模块不在全局注册任何组件/指令，不影响其它同学页面。
- API 统一走 `src/api/index.js` 的 `api` 对象并自动携带 `Authorization: Bearer <token>`；token 存储键 `z_token` 可通过 `setApiConfig({ tokenKey })` 与主工程对齐。
- Mock 数据键为 `z_mock_db_v1`，仅 Mock 模式下使用；合并到真实后端后 `useMock=false` 即完全走 HTTP。
- 登录守卫：主工程全局 `beforeEach` 统一处理即可；本模块路由的 `requiresAuth` 元信息已就绪，主工程可借此判断。

## 四、权限说明（对齐文档「双重控制」）

前端：`SystemLayout` 按登录返回的 `permissions` 过滤侧栏菜单；各页面操作按钮（新增/编辑/启停/重置密码/配置权限）按 `perms` 控制显隐。后端接口鉴权为最终边界，前端隐藏仅为体验层。
*（内容由AI生成，仅供参考）*
