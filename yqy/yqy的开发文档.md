---
AIGC:
    Label: "1"
    ContentProducer: 001191440300708461136T1XGW3
    ProduceID: 72cc4012907e6df231089f1d373729fa_513258b4a81111f1a604525400461939
    ReservedCode1: P9FpESS+jQ8KrCKHbu+eLiCc/BiT8lhYHY5cAiv5jt+c01sZ2FR5eE9R8/MdQUH3t61NFKUm6ILYJlw/Uugahc+wOHnFAVjJ2jxhZCrvqiH18KG48qzRVw01vBeLKfqNekCOLaXsL+b0OPIX4ctz54jdNtgNuY8zPzTRLd8qnNJwLC00v9t3ni4KLC8=
    ContentPropagator: 001191440300708461136T1XGW3
    PropagateID: 72cc4012907e6df231089f1d373729fa_513258b4a81111f1a604525400461939
    ReservedCode2: P9FpESS+jQ8KrCKHbu+eLiCc/BiT8lhYHY5cAiv5jt+c01sZ2FR5eE9R8/MdQUH3t61NFKUm6ILYJlw/Uugahc+wOHnFAVjJ2jxhZCrvqiH18KG48qzRVw01vBeLKfqNekCOLaXsL+b0OPIX4ctz54jdNtgNuY8zPzTRLd8qnNJwLC00v9t3ni4KLC8=
---

# yqy 的开发文档 —— 仓储管理模块

> 本文档基于「仓储管理」模块从草稿区编写到合入现有 ERP 项目的完整开发过程整理，涵盖项目结构、模块设计、前端结构、接入方式、编译校验与后续扩展建议。内容基于正式工程最终实现编写（含库存联动改造）。

---

## 一、项目结构说明

本项目采用「后端 + 前端 + 草稿暂存区」三段式结构，位于 `D:\three\hqyj\mygit` 下：

```
mygit
├─ erp-server/                 # Spring Boot 3.4.5 + Java 17 后端（单应用、单端口 8080）
│  └─ src/main/java/com/erp/demo/
│     ├─ ErpServerApplication.java   # 启动类（组件扫描 com.erp.demo）
│     ├─ product/                    # 商品档案模块（原有）
│     ├─ procurement/                # 采购模块（原有）
│     └─ warehouse/                  # 仓储管理模块（本次新增）
├─ erp-web/                    # Vue3 + Vite 前端（无路由，单页侧边栏切换）
│  └─ src/
│     ├─ App.vue                    # 主入口：侧边栏 + 模块切换 + 各模块页面
│     ├─ WarehouseView.vue          # 仓储管理页面（本次新增）
│     ├─ style.css                  # 全局样式（复用）
│     └─ main.js
└─ yqy/                        # 草稿暂存区（本模块的开发草稿与本文档）
   ├─ yqy.docx                      # 原始占位草稿（未参与开发）
   ├─ backend/warehouse/            # 后端草稿：6 个 Java 文件
   ├─ frontend/WarehouseView.vue    # 前端草稿
   └─ yqy的开发文档.md               # 本文档
```

### 1.1 各目录职责

| 目录 | 职责 |
| --- | --- |
| `erp-server` | 唯一可运行的 Spring Boot 应用。所有模块处于同一进程、同一组件扫描（`com.erp.demo`），共享内存数据 |
| `erp-web` | Vue3 + Vite 前端，通过 `fetch('/api/xxx')` 调用后端接口，无路由库 |
| `yqy` | 开发草稿暂存区，用于先写代码再合入正式工程；**不作为独立运行项目** |

> 关键约束：`yqy` 内的后端若作为独立 Spring Boot 运行，会与 erp-server 端口冲突（同为 8080），且无法被 `com.erp.demo` 包扫描、无法注入现有 Service，数据彼此隔离。因此**最终代码必须合入 erp-server / erp-web**，yqy 仅作草稿与归档。

---

## 二、仓储模块设计

### 2.1 模块定位

在现有「商品档案（product）+ 采购（procurement）」基础上，新增**仓储管理（warehouse）**，覆盖：

- 仓库档案管理（编码、名称、负责人、状态）
- 库存余额查询（按「商品 + 仓库」维度）
- 库存流水记录
- 手工入库 / 出库登记

### 2.2 实体设计（record 不可变对象）

| 实体 | 文件 | 字段 |
| --- | --- | --- |
| `Warehouse` 仓库档案 | `Warehouse.java` | `id, code, name, manager, status` |
| `InventoryBalance` 库存余额 | `InventoryBalance.java` | `id, warehouseId, warehouseName, productId, productSku, productName, quantity, lockedQuantity, availableQuantity, safetyStock, unit` |
| `StockFlow` 库存流水 | `StockFlow.java` | `id, flowNo, warehouseId, warehouseName, productId, productSku, productName, businessType, changeQuantity, sourceNo, operator, time` |

字段来源参考 PRD.md：仓库（编码、名称、负责人、状态）；库存余额（商品、仓库、现存数量、锁定数量、可用数量）；流水（流水号、商品、仓库、业务类型、变动数量、来源单号、操作人、时间）。

### 2.3 数据模型与库存联动设计

- `WarehouseService` 持有内存 `List<Warehouse>` / `List<InventoryBalance>` / `List<StockFlow>`，主键用 `AtomicLong` 自增（风格与 ProductService / ProcurementService 一致）。
- 构造函数注入 `ProductService`：**启动时把商品档案的现有库存同步到主仓（WH-MAIN）**，保证仓储余额与商品档案初始一致。
- **核心设计——取消库存隔离，实现联动**：
  - 原设计：手工出入库仅修改本模块余额、生成流水，不改商品档案总库存（数据隔离）。
  - 现设计：手工出入库在**分仓库存校验通过后**，同步调用 `ProductService` 的 `increaseStock / decreaseStock` 更新商品档案总库存，实现两处数据联动一致。
  - 出库先校验分仓余额、再联动总库存，避免中途失败导致两边数据不一致。
  - 联动关系：`商品档案总库存 = 各仓库余额之和`（初始成立，此后每次出入库两侧同量增减保持恒等）。

### 2.4 接口清单（Controller：`WarehouseController`，统一前缀 `/api`）

| 方法 | 路径 | 说明 | 入参 |
| --- | --- | --- | --- |
| GET | `/api/warehouses` | 仓库列表（支持 keyword / status 过滤） | 可选：keyword, status |
| POST | `/api/warehouses` | 新建仓库 | `WarehouseRequest` |
| PUT | `/api/warehouses/{id}` | 修改仓库（同步刷新余额中仓库名） | `WarehouseRequest` |
| DELETE | `/api/warehouses/{id}` | 删除仓库（有库存时拒绝） | 路径 id |
| GET | `/api/inventory` | 库存余额查询（支持 warehouseId / productId / keyword） | 可选：warehouseId, productId, keyword |
| GET | `/api/stock-flows` | 流水列表（支持 warehouseId / productId / businessType / keyword） | 可选：多过滤条件 |
| POST | `/api/stock-flows` | 手工入库 / 出库登记 | `StockMovementRequest{warehouseId, productId, businessType, quantity, remark}` |

`StockMovementRequest` 为 Controller 内置 record，`businessType` 仅支持「手工入库 / 手工出库」。

### 2.5 核心业务逻辑：手工出入库数据流

```
POST /api/stock-flows
  1. 校验必填项：warehouseId / productId / quantity(>0)
  2. 校验仓库存在且状态为「启用」
  3. 通过 ProductService.findById 校验商品存在
  4. 解析业务类型：手工入库(inbound) / 手工出库(outbound)
  5. 分仓校验（无状态变更）：
     - 出库且该仓库无该商品余额  → 报错「该仓库无此商品库存」
     - 出库后分仓数量 < 0        → 报错「出库数量超过当前库存」
  6. 联动商品总库存（库存联动设计）：
     - 入库 → productService.increaseStock(productId, quantity)
     - 出库 → productService.decreaseStock(productId, quantity)
       （decreaseStock 校验总库存不足时返回 409）
  7. 更新分仓余额（新增 / 覆盖 InventoryBalance）
  8. 生成 StockFlow 流水（流水号 IN/OUT + yyyyMMdd + 序号，操作人“系统管理员”）
```

### 2.6 ProductService 库存方法（本次扩展）

| 方法 | 说明 |
| --- | --- |
| `increaseStock(Long id, int quantity)` | 原有。增加商品总库存，`quantity <= 0` 报 400 |
| `decreaseStock(Long id, int quantity)`（本次新增） | 减少商品总库存；`quantity <= 0` 报 400；库存不足返回 409 CONFLICT |

两者均为 `synchronized`，与现有风格一致，保证并发下库存计数安全。

---

## 三、前端页面结构

`erp-web/src/WarehouseView.vue` 为仓储管理页面，采用单页组件 + 三个 Tab 结构：

```
WarehouseView.vue
├─ 概览区（顶部统计卡片）
│   ├─ 仓库数量 / 库存品种数 / 库存预警数（低于安全库存）
├─ Tab 1 仓库档案
│   ├─ 搜索（关键词 / 状态）+ 新增仓库按钮
│   ├─ 表格：编码、名称、负责人、状态、操作（编辑 / 删除）
│   └─ 新增/编辑弹窗（校验必填）
├─ Tab 2 库存余额
│   ├─ 搜索（商品名称、编码或仓库）
│   ├─ 表格：仓库、商品信息、现存数量、锁定/可用、安全库存、库存状态
│   ├─ 「手工出入库登记」入口 → 弹窗（选仓库 + 商品 + 类型 + 数量 + 备注）
│   └─ 底部说明（初始同步 / 出入库联动商品总库存）
└─ Tab 3 库存流水
    ├─ 过滤（类型 / 关键词）
    └─ 表格：流水号、业务类型、商品信息、仓库、变动数量、来源/备注、操作人、时间
```

- 组件复用 `erp-web/src/style.css` 全局样式。
- 数据交互统一走 `fetch('/api/xxx')`，与商品、采购页面一致。

---

## 四、接入方式（草稿区 + 合入双区模式）

仓储模块**仅新增文件**，未改动任何既有 Java 代码结构、未改动 `application.yml`：

### 4.1 开发流程

1. 在 `yqy/backend/warehouse/`（后端草稿）与 `yqy/frontend/`（前端草稿）先完成全部代码。
2. 通过「复制合入」将草稿同步到正式工程，仅新增文件：
   - 后端包 → `erp-server/src/main/java/com/erp/demo/warehouse/`；
   - 前端组件 → `erp-web/src/WarehouseView.vue`。
3. 在 `erp-web/src/App.vue` 中接入（见 4.3）。
4. 用 `mvn compile` 校验后端编译。

### 4.2 后端接入

1. 利用 Spring 组件扫描：`@RestController` / `@Service` 自动注册，`WarehouseService` 通过构造器注入 `ProductService` 与现有模块互通。
2. 无需改动启动类与配置文件。

### 4.3 前端接入

在 `erp-web/src/App.vue` 中：

1. `import WarehouseView from './WarehouseView.vue'`；
2. 侧边栏新增「仓储管理」`nav-item`；
3. 渲染区新增 `v-else-if` 分支切换 `<WarehouseView />`。

接口路径统一为 `/api/warehouses`、`/api/inventory`、`/api/stock-flows`。

### 4.4 库存联动改动清单

| 文件 | 改动 |
| --- | --- |
| `ProductService.java` | 新增 `decreaseStock` 方法 |
| `WarehouseService.java` | `createMovement` 中分仓校验通过后同步调用 increaseStock / decreaseStock；更新类注释 |
| `WarehouseView.vue` | 底部说明文案为「手工出入库同步更新商品总库存」 |

---

## 五、编译构建与联调校验步骤

### 5.1 后端编译校验

```bash
cd D:\three\hqyj\mygit\erp-server
mvn compile
```

预期输出：`BUILD SUCCESS`，退出码 0。

### 5.2 前端构建校验

```bash
cd D:\three\hqyj\mygit\erp-web
npm run build   # 或 vite build
```

预期输出：构建成功，生成 `dist/`。

### 5.3 运行联调建议

1. 启动 `erp-server`（端口 8080），再启动 `erp-web` 开发服务器（Vite 默认 5173，已配置 `/api` 代理到 8080）。
2. 验证链路：
   - 商品档案 → 仓储管理：初始库存应已同步至主仓；
   - 在仓储页对某商品做**手工入库** → 查看商品档案该商品库存应同步增加；
   - 做**手工出库** → 商品档案库存应同步减少；分仓余额不足时提示「出库数量超过当前库存」；
   - 在仓储页查看流水记录：流水号、类型、变动数量、时间正确。
3. 验证库存预警：使某商品总库存低于安全库存，仓储页「库存预警」统计与商品页「库存偏低」标签应一致。
4. **注意**：修改后端代码后必须重启 `erp-server` 进程，否则新接口（如 `/api/warehouses`）不会注册，前端会报 404。

---

## 六、后续扩展建议（含 MySQL 持久化）

### 6.1 数据持久化（MySQL）

当前为内存 List 存储，重启丢失。扩展建议：

1. **引入依赖**：`spring-boot-starter-data-jpa` + `mysql-connector-j`，在 `application.yml` 配置数据源与 `ddl-auto: update`。
2. **实体改造**：为 `Warehouse` / `InventoryBalance` / `StockFlow` 增加 `@Entity` / `@Table` 注解与 `@Id @GeneratedValue` 主键；`Product` 同理。
3. **Repository 层**：新增 `WarehouseRepository` / `InventoryBalanceRepository` / `StockFlowRepository`（`extends JpaRepository`），替换 `List` 内存读写。
4. **流水表设计**：`stock_flow` 增加 `source_no` 外键（关联采购收货单）、索引 `(warehouse_id, product_id)`、`(flow_no)` 唯一索引。
5. **事务**：出入库涉及「分仓余额 + 商品总库存 + 流水」三处写入，在 `WarehouseService.createMovement` 上加 `@Transactional`，保证原子性；`increaseStock / decreaseStock` 同样纳入事务。
6. **并发**：数据库行锁（`SELECT ... FOR UPDATE` 或 `@Version` 乐观锁）替代当前 `synchronized`。

### 6.2 功能增强

- 库存流水增加**来源单据**关联（采购收货、销售出库），支持按来源单号追溯；
- 增加**安全库存预警通知**、按仓库维度库存报表；
- 库存盘点（盘盈/盘亏）与调拨（跨仓调拨）流程；
- 出入库审批流与操作人审计。

### 6.3 前端

- 引入 Vue Router 实现独立路由与页面级懒加载；
- 库存余额页支持分页与按仓库/商品维度汇总透视。

---

*文档编写时间：2026-09-04，内容基于正式工程最终实现（含库存联动改造）。*
*（内容由AI生成，仅供参考）*
