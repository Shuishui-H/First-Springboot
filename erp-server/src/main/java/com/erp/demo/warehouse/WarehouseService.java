package com.erp.demo.warehouse;

import com.erp.demo.product.Product;
import com.erp.demo.product.ProductService;
import com.erp.demo.audit.OperationLogService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.beans.factory.ObjectProvider;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;

/** 仓储管理服务：分仓余额与商品档案总库存保持联动。 */
@Service
public class WarehouseService {
    private static final DateTimeFormatter FLOW_DATE_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd");
    private final AtomicLong nextWarehouseId = new AtomicLong(3);
    private final AtomicLong nextBalanceId = new AtomicLong(100);
    private final AtomicLong nextFlowId = new AtomicLong(9001);
    private final AtomicLong nextTransferId = new AtomicLong(1);
    private final AtomicLong nextStocktakeId = new AtomicLong(1);
    private final ProductService productService;
    private final OperationLogService operationLogService;
    private final JdbcTemplate jdbcTemplate;
    private final List<Warehouse> warehouses = new ArrayList<>();
    private final List<InventoryBalance> balances = new ArrayList<>();
    private final List<StockFlow> flows = new ArrayList<>();
    private final List<TransferOrder> transfers = new ArrayList<>();
    private final List<StocktakeOrder> stocktakes = new ArrayList<>();

    public WarehouseService(ProductService productService, OperationLogService operationLogService) {
        this(productService, operationLogService, null);
    }

    @Autowired
    public WarehouseService(ProductService productService, OperationLogService operationLogService, ObjectProvider<JdbcTemplate> jdbcTemplateProvider) {
        this.productService = productService;
        this.operationLogService = operationLogService;
        this.jdbcTemplate = jdbcTemplateProvider == null ? null : jdbcTemplateProvider.getIfAvailable();
        warehouses.add(new Warehouse(1L, "WH-MAIN", "主仓", "张伟", "启用"));
        warehouses.add(new Warehouse(2L, "WH-EAST", "华东仓", "李娜", "启用"));
        for (Product product : productService.findAll()) {
            balances.add(new InventoryBalance(nextBalanceId.getAndIncrement(), 1L, "主仓", product.id(), product.sku(),
                    product.name(), product.stock(), 0, product.stock(), product.safetyStock(), product.unit(), 0));
        }
    }

    public synchronized List<Warehouse> findWarehouses(String keyword, String status) {
        if (relationalDataAvailable()) {
            String query = keyword == null ? "" : keyword.trim().toLowerCase();
            return jdbcTemplate.query("SELECT id, code, name, manager, status FROM md_warehouse ORDER BY id", (rs, row) ->
                    new Warehouse(rs.getLong("id"), rs.getString("code"), rs.getString("name"), rs.getString("manager"), rs.getInt("status") == 1 ? "启用" : "停用"))
                    .stream().filter(item -> query.isBlank() || item.code().toLowerCase().contains(query) || item.name().toLowerCase().contains(query)
                            || (item.manager() != null && item.manager().toLowerCase().contains(query)))
                    .filter(item -> status == null || status.isBlank() || "全部状态".equals(status) || item.status().equals(status)).toList();
        }
        String query = keyword == null ? "" : keyword.trim().toLowerCase();
        return warehouses.stream()
                .filter(item -> query.isBlank() || item.code().toLowerCase().contains(query) || item.name().toLowerCase().contains(query)
                        || (item.manager() != null && item.manager().toLowerCase().contains(query)))
                .filter(item -> status == null || status.isBlank() || "全部状态".equals(status) || item.status().equals(status))
                .sorted(Comparator.comparing(Warehouse::id)).toList();
    }

    public synchronized Warehouse findWarehouse(Long id) {
        if (relationalDataAvailable()) return findWarehouses(null, null).stream().filter(item -> item.id().equals(id)).findFirst()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "仓库不存在"));
        return warehouses.stream().filter(item -> item.id().equals(id)).findFirst()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "仓库不存在"));
    }

    public synchronized Warehouse createWarehouse(WarehouseRequest request) {
        if (relationalDataAvailable()) {
            if (jdbcTemplate.queryForObject("SELECT COUNT(*) FROM md_warehouse WHERE LOWER(code) = LOWER(?)", Integer.class, request.code().trim()) > 0)
                throw conflict("仓库编码已存在");
            jdbcTemplate.update("INSERT INTO md_warehouse (code, name, manager, status, created_by, updated_by) VALUES (?, ?, ?, ?, 0, 0)",
                    request.code().trim(), request.name().trim(), blankToNull(request.manager()), "启用".equals(request.status()) ? 1 : 0);
            return findWarehouses(null, null).stream().filter(item -> item.code().equalsIgnoreCase(request.code().trim())).findFirst().orElseThrow();
        }
        assertCodeAvailable(request.code(), null);
        Warehouse warehouse = new Warehouse(nextWarehouseId.getAndIncrement(), request.code().trim(), request.name().trim(),
                blankToNull(request.manager()), blankToDefault(request.status(), "启用"));
        warehouses.add(warehouse);
        return warehouse;
    }

    public synchronized Warehouse updateWarehouse(Long id, WarehouseRequest request) {
        if (relationalDataAvailable()) {
            findWarehouse(id);
            if (jdbcTemplate.queryForObject("SELECT COUNT(*) FROM md_warehouse WHERE LOWER(code) = LOWER(?) AND id <> ?", Integer.class, request.code().trim(), id) > 0)
                throw conflict("仓库编码已存在");
            jdbcTemplate.update("UPDATE md_warehouse SET code = ?, name = ?, manager = ?, status = ?, updated_by = 0 WHERE id = ?",
                    request.code().trim(), request.name().trim(), blankToNull(request.manager()), "启用".equals(request.status()) ? 1 : 0, id);
            return findWarehouse(id);
        }
        Warehouse existing = findWarehouse(id);
        assertCodeAvailable(request.code(), id);
        Warehouse updated = new Warehouse(existing.id(), request.code().trim(), request.name().trim(), blankToNull(request.manager()),
                blankToDefault(request.status(), existing.status()));
        warehouses.set(warehouses.indexOf(existing), updated);
        for (int i = 0; i < balances.size(); i++) {
            InventoryBalance balance = balances.get(i);
            if (balance.warehouseId().equals(id)) {
                balances.set(i, new InventoryBalance(balance.id(), balance.warehouseId(), updated.name(), balance.productId(), balance.productSku(),
                        balance.productName(), balance.quantity(), balance.lockedQuantity(), balance.availableQuantity(), balance.safetyStock(), balance.unit(), balance.version()));
            }
        }
        return updated;
    }

    public synchronized void deleteWarehouse(Long id) {
        if (relationalDataAvailable()) {
            findWarehouse(id);
            if (jdbcTemplate.queryForObject("SELECT COUNT(*) FROM wh_inventory_balance WHERE warehouse_id = ? AND quantity > 0", Integer.class, id) > 0)
                throw conflict("该仓库仍有库存，不能删除");
            try { jdbcTemplate.update("DELETE FROM md_warehouse WHERE id = ?", id); }
            catch (org.springframework.dao.DataAccessException exception) { throw conflict("该仓库已被业务单据引用，不能删除"); }
            return;
        }
        Warehouse existing = findWarehouse(id);
        if (balances.stream().anyMatch(item -> item.warehouseId().equals(id) && item.quantity() > 0)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "该仓库仍有库存，不能删除");
        }
        warehouses.remove(existing);
        balances.removeIf(item -> item.warehouseId().equals(id));
    }

    public synchronized List<InventoryBalance> findBalances(Long warehouseId, Long productId, String keyword) {
        if (relationalDataAvailable()) {
            String query = keyword == null ? "" : keyword.trim().toLowerCase();
            return jdbcTemplate.query("""
                    SELECT b.id, b.warehouse_id, w.name warehouse_name, b.product_id, p.sku, p.name product_name,
                           b.quantity, b.locked_quantity, b.available_quantity, p.safety_stock, p.unit, b.version
                      FROM wh_inventory_balance b JOIN md_warehouse w ON w.id = b.warehouse_id JOIN md_product p ON p.id = b.product_id
                     ORDER BY b.id
                    """, (rs, row) -> new InventoryBalance(rs.getLong("id"), rs.getLong("warehouse_id"), rs.getString("warehouse_name"),
                    rs.getLong("product_id"), rs.getString("sku"), rs.getString("product_name"), rs.getInt("quantity"),
                    rs.getInt("locked_quantity"), rs.getInt("available_quantity"), rs.getBigDecimal("safety_stock").intValue(), rs.getString("unit"), rs.getLong("version")))
                    .stream().filter(item -> warehouseId == null || item.warehouseId().equals(warehouseId)).filter(item -> productId == null || item.productId().equals(productId))
                    .filter(item -> query.isBlank() || item.productName().toLowerCase().contains(query) || item.productSku().toLowerCase().contains(query) || item.warehouseName().toLowerCase().contains(query)).toList();
        }
        String query = keyword == null ? "" : keyword.trim().toLowerCase();
        return balances.stream()
                .filter(item -> warehouseId == null || item.warehouseId().equals(warehouseId))
                .filter(item -> productId == null || item.productId().equals(productId))
                .filter(item -> query.isBlank() || item.productName().toLowerCase().contains(query) || item.productSku().toLowerCase().contains(query)
                        || item.warehouseName().toLowerCase().contains(query))
                .sorted(Comparator.comparing(InventoryBalance::id)).toList();
    }

    public synchronized List<StockFlow> findFlows(Long warehouseId, Long productId, String businessType, String keyword) {
        if (relationalDataAvailable()) {
            String query = keyword == null ? "" : keyword.trim().toLowerCase();
            return jdbcTemplate.query("SELECT id, flow_no, warehouse_id, warehouse_name, product_id, sku, product_name, business_type, change_quantity, source_no, operator_name, created_at, before_quantity, after_quantity FROM wh_stock_flow ORDER BY id DESC",
                    (rs, row) -> new StockFlow(rs.getLong("id"), rs.getString("flow_no"), rs.getLong("warehouse_id"), rs.getString("warehouse_name"), rs.getLong("product_id"), rs.getString("sku"), rs.getString("product_name"), rs.getString("business_type"), rs.getInt("change_quantity"), rs.getString("source_no"), rs.getString("operator_name"), rs.getTimestamp("created_at").toLocalDateTime(), rs.getInt("before_quantity"), rs.getInt("after_quantity")))
                    .stream().filter(item -> warehouseId == null || item.warehouseId().equals(warehouseId)).filter(item -> productId == null || item.productId().equals(productId))
                    .filter(item -> businessType == null || businessType.isBlank() || "全部类型".equals(businessType) || item.businessType().equals(businessType))
                    .filter(item -> query.isBlank() || item.flowNo().toLowerCase().contains(query) || item.productName().toLowerCase().contains(query) || item.productSku().toLowerCase().contains(query)).toList();
        }
        String query = keyword == null ? "" : keyword.trim().toLowerCase();
        return flows.stream()
                .filter(item -> warehouseId == null || item.warehouseId().equals(warehouseId))
                .filter(item -> productId == null || item.productId().equals(productId))
                .filter(item -> businessType == null || businessType.isBlank() || "全部类型".equals(businessType) || item.businessType().equals(businessType))
                .filter(item -> query.isBlank() || item.flowNo().toLowerCase().contains(query) || item.productName().toLowerCase().contains(query)
                        || item.productSku().toLowerCase().contains(query))
                .sorted(Comparator.comparing(StockFlow::id).reversed()).toList();
    }

    @Transactional
    public synchronized StockFlow createMovement(WarehouseController.StockMovementRequest request) {
        if (relationalDataAvailable()) return applyMovementRelational(request.warehouseId(), request.productId(), request.businessType(), request.quantity(), request.remark(), null, true);
        return applyMovement(request.warehouseId(), request.productId(), request.businessType(), request.quantity(), request.remark(), null, true);
    }

    /** 供采购、销售等业务单据登记分仓库存和流水；商品总库存由调用方负责更新。 */
    @Transactional
    public synchronized StockFlow recordBusinessMovement(Long warehouseId, Long productId, int quantity, boolean inbound,
                                                          String businessType, String sourceNo, String remark) {
        if (relationalDataAvailable()) return applyMovementRelational(warehouseId, productId, businessType, quantity, remark, sourceNo, false);
        return applyMovement(warehouseId, productId, businessType, quantity, remark, sourceNo, false);
    }

    /**
     * 商品档案的“当前库存”在 V1 中代表主仓库存。新增或修改商品档案时，
     * 将其同步为主仓余额，避免商品总库存和实际出库仓库存不一致。
     */
    public synchronized void syncProductStockToMainWarehouse(Product product) {
        if (relationalDataAvailable()) {
            jdbcTemplate.update("""
                    INSERT INTO wh_inventory_balance (warehouse_id, product_id, quantity, locked_quantity, available_quantity, version, updated_by)
                    VALUES (1, ?, ?, 0, ?, 0, 0)
                    ON DUPLICATE KEY UPDATE quantity = VALUES(quantity), available_quantity = VALUES(available_quantity), version = version + 1, updated_by = 0
                    """, product.id(), product.stock(), product.stock());
            return;
        }
        Warehouse mainWarehouse = findWarehouse(1L);
        InventoryBalance existing = balances.stream()
                .filter(item -> item.warehouseId().equals(mainWarehouse.id()) && item.productId().equals(product.id()))
                .findFirst().orElse(null);
        if (existing == null) {
            balances.add(new InventoryBalance(nextBalanceId.getAndIncrement(), mainWarehouse.id(), mainWarehouse.name(), product.id(),
                    product.sku(), product.name(), product.stock(), 0, product.stock(), product.safetyStock(), product.unit(), 0));
            return;
        }
        balances.set(balances.indexOf(existing), new InventoryBalance(existing.id(), existing.warehouseId(), mainWarehouse.name(),
                product.id(), product.sku(), product.name(), product.stock(), 0, product.stock(), product.safetyStock(), product.unit(), existing.version() + 1));
    }

    /** 在扣减商品总库存前预校验出库仓的可用库存，保证失败时不产生半次扣减。 */
    public synchronized void assertAvailableStock(Long warehouseId, Long productId, int quantity) {
        if (quantity <= 0) throw badRequest("出库数量必须大于 0");
        if (relationalDataAvailable()) {
            Integer available = jdbcTemplate.query("SELECT available_quantity FROM wh_inventory_balance WHERE warehouse_id = ? AND product_id = ?", rs -> rs.next() ? rs.getInt(1) : null, warehouseId, productId);
            if (available == null) throw conflict("出库仓没有该商品库存，请先登记入库");
            if (available < quantity) throw conflict("出库数量超过仓库可用库存");
            return;
        }
        InventoryBalance balance = balances.stream()
                .filter(item -> item.warehouseId().equals(warehouseId) && item.productId().equals(productId))
                .findFirst().orElse(null);
        if (balance == null) throw conflict("出库仓没有该商品库存，请先登记入库");
        if (balance.availableQuantity() < quantity) throw conflict("出库数量超过仓库可用库存");
    }

    public synchronized List<TransferOrder> findTransfers(String status, Long warehouseId, String keyword) {
        if (relationalDataAvailable()) {
            return jdbcTemplate.query("SELECT id FROM wh_transfer_order ORDER BY id DESC", (rs, row) -> rs.getLong(1)).stream().map(this::readTransfer)
                    .filter(item -> status == null || status.isBlank() || "全部状态".equals(status) || status.equals(item.status()))
                    .filter(item -> warehouseId == null || warehouseId.equals(item.fromWarehouseId()) || warehouseId.equals(item.toWarehouseId()))
                    .filter(item -> keyword == null || keyword.isBlank() || item.transferNo().toLowerCase().contains(keyword.trim().toLowerCase())
                            || item.fromWarehouseName().toLowerCase().contains(keyword.trim().toLowerCase()) || item.toWarehouseName().toLowerCase().contains(keyword.trim().toLowerCase())).toList();
        }
        String query = keyword == null ? "" : keyword.trim().toLowerCase();
        return transfers.stream()
                .filter(item -> status == null || status.isBlank() || "全部状态".equals(status) || status.equals(item.status()))
                .filter(item -> warehouseId == null || warehouseId.equals(item.fromWarehouseId()) || warehouseId.equals(item.toWarehouseId()))
                .filter(item -> query.isBlank() || item.transferNo().toLowerCase().contains(query)
                        || item.fromWarehouseName().toLowerCase().contains(query) || item.toWarehouseName().toLowerCase().contains(query)
                        || item.items().stream().anyMatch(line -> line.productName().toLowerCase().contains(query) || line.productSku().toLowerCase().contains(query)))
                .sorted(Comparator.comparing(TransferOrder::id).reversed()).toList();
    }

    public synchronized TransferOrder findTransfer(Long id) {
        if (relationalDataAvailable()) return readTransfer(id);
        return transfers.stream().filter(item -> item.id().equals(id)).findFirst()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "调拨单不存在"));
    }

    @Transactional
    public synchronized TransferOrder createTransfer(TransferOrderRequest request) {
        if (relationalDataAvailable()) return createTransferRelational(request);
        if (request.fromWarehouseId().equals(request.toWarehouseId())) throw badRequest("调出仓与调入仓不能相同");
        Warehouse from = findWarehouse(request.fromWarehouseId());
        Warehouse to = findWarehouse(request.toWarehouseId());
        if (!"启用".equals(from.status()) || !"启用".equals(to.status())) throw badRequest("停用仓库不能创建调拨单");
        if (request.items().size() > 50) throw badRequest("单张调拨单最多 50 条明细");
        Set<Long> productIds = new HashSet<>();
        List<TransferOrderItem> items = new ArrayList<>();
        for (TransferOrderRequest.Item line : request.items()) {
            if (!productIds.add(line.productId())) throw badRequest("同一商品不能重复填写");
            Product product = productService.findById(line.productId());
            if (!"启用".equals(product.status())) throw badRequest("停用商品不能调拨");
            items.add(new TransferOrderItem(product.id(), product.sku(), product.name(), product.unit(), line.quantity(), blankToNull(line.remark())));
        }
        long id = nextTransferId.getAndIncrement();
        TransferOrder transfer = new TransferOrder(id, nextTransferNo(id), from.id(), from.name(), to.id(), to.name(),
                request.transferDate() == null ? LocalDate.now() : request.transferDate(), "待确认", blankToNull(request.remark()),
                List.copyOf(items), "系统管理员", LocalDateTime.now(), null, null, 0);
        transfers.add(transfer);
        operationLogService.log("WAREHOUSE", "TRANSFER_CREATE", "TRANSFER", transfer.id(), transfer.transferNo(), transfer.status());
        return transfer;
    }

    @Transactional
    public synchronized TransferOrder confirmTransfer(Long id) {
        if (relationalDataAvailable()) return confirmTransferRelational(id);
        TransferOrder existing = findTransfer(id);
        if ("已确认".equals(existing.status())) throw conflict("调拨单已确认，请勿重复操作");
        if (!"待确认".equals(existing.status())) throw badRequest("当前状态不能确认调拨");
        for (TransferOrderItem item : existing.items()) assertAvailableStock(existing.fromWarehouseId(), item.productId(), item.quantity());
        for (TransferOrderItem item : existing.items()) {
            applyMovement(existing.fromWarehouseId(), item.productId(), "仓库调出", item.quantity(), existing.remark(), existing.transferNo(), false);
            applyMovement(existing.toWarehouseId(), item.productId(), "仓库调入", item.quantity(), existing.remark(), existing.transferNo(), false);
        }
        TransferOrder confirmed = new TransferOrder(existing.id(), existing.transferNo(), existing.fromWarehouseId(), existing.fromWarehouseName(),
                existing.toWarehouseId(), existing.toWarehouseName(), existing.transferDate(), "已确认", existing.remark(), existing.items(),
                existing.createdBy(), existing.createdAt(), "系统管理员", LocalDateTime.now(), existing.version() + 1);
        transfers.set(transfers.indexOf(existing), confirmed);
        operationLogService.log("WAREHOUSE", "TRANSFER_CONFIRM", "TRANSFER", confirmed.id(), confirmed.transferNo(), confirmed.status());
        return confirmed;
    }

    @Transactional
    public synchronized TransferOrder cancelTransfer(Long id) {
        if (relationalDataAvailable()) {
            TransferOrder existing = findTransfer(id);
            if (!"待确认".equals(existing.status())) throw badRequest("仅待确认调拨单可取消");
            if (jdbcTemplate.update("UPDATE wh_transfer_order SET status = '已取消', version = version + 1 WHERE id = ? AND status = '待确认' AND version = ?", id, existing.version()) != 1)
                throw conflict("调拨单状态已变化，请刷新后重试");
            operationLogService.log("WAREHOUSE", "TRANSFER_CANCEL", "TRANSFER", id, existing.transferNo(), "已取消");
            return findTransfer(id);
        }
        TransferOrder existing = findTransfer(id);
        if (!"待确认".equals(existing.status())) throw badRequest("仅待确认调拨单可取消");
        TransferOrder cancelled = new TransferOrder(existing.id(), existing.transferNo(), existing.fromWarehouseId(), existing.fromWarehouseName(),
                existing.toWarehouseId(), existing.toWarehouseName(), existing.transferDate(), "已取消", existing.remark(), existing.items(),
                existing.createdBy(), existing.createdAt(), null, null, existing.version() + 1);
        transfers.set(transfers.indexOf(existing), cancelled);
        operationLogService.log("WAREHOUSE", "TRANSFER_CANCEL", "TRANSFER", cancelled.id(), cancelled.transferNo(), cancelled.status());
        return cancelled;
    }

    public synchronized List<StocktakeOrder> findStocktakes(String status, Long warehouseId, String keyword) {
        if (relationalDataAvailable()) {
            return jdbcTemplate.query("SELECT id FROM wh_stocktake_order ORDER BY id DESC", (rs, row) -> rs.getLong(1)).stream().map(this::readStocktake)
                    .filter(item -> status == null || status.isBlank() || "全部状态".equals(status) || status.equals(item.status()))
                    .filter(item -> warehouseId == null || warehouseId.equals(item.warehouseId()))
                    .filter(item -> keyword == null || keyword.isBlank() || item.stocktakeNo().toLowerCase().contains(keyword.trim().toLowerCase()) || item.warehouseName().toLowerCase().contains(keyword.trim().toLowerCase())).toList();
        }
        String query = keyword == null ? "" : keyword.trim().toLowerCase();
        return stocktakes.stream()
                .filter(item -> status == null || status.isBlank() || "全部状态".equals(status) || status.equals(item.status()))
                .filter(item -> warehouseId == null || warehouseId.equals(item.warehouseId()))
                .filter(item -> query.isBlank() || item.stocktakeNo().toLowerCase().contains(query) || item.warehouseName().toLowerCase().contains(query)
                        || item.items().stream().anyMatch(line -> line.productName().toLowerCase().contains(query) || line.productSku().toLowerCase().contains(query)))
                .sorted(Comparator.comparing(StocktakeOrder::id).reversed()).toList();
    }

    public synchronized StocktakeOrder findStocktake(Long id) {
        if (relationalDataAvailable()) return readStocktake(id);
        return stocktakes.stream().filter(item -> item.id().equals(id)).findFirst()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "盘点单不存在"));
    }

    @Transactional
    public synchronized StocktakeOrder createStocktake(StocktakeOrderRequest request) {
        if (relationalDataAvailable()) return createStocktakeRelational(request);
        Warehouse warehouse = findWarehouse(request.warehouseId());
        if (!"启用".equals(warehouse.status())) throw badRequest("停用仓库不能创建盘点单");
        if (request.items().size() > 50) throw badRequest("单张盘点单最多 50 条明细");
        Set<Long> productIds = new HashSet<>();
        List<StocktakeOrderItem> items = new ArrayList<>();
        for (StocktakeOrderRequest.Item line : request.items()) {
            if (!productIds.add(line.productId())) throw badRequest("同一商品不能重复填写");
            Product product = productService.findById(line.productId());
            InventoryBalance balance = findBalance(warehouse.id(), product.id());
            int book = balance == null ? 0 : balance.quantity();
            int difference = line.countedQuantity() - book;
            items.add(new StocktakeOrderItem(product.id(), product.sku(), product.name(), product.unit(), book, line.countedQuantity(), difference,
                    line.reason().trim(), balance == null ? 0 : balance.version()));
        }
        long id = nextStocktakeId.getAndIncrement();
        StocktakeOrder order = new StocktakeOrder(id, nextStocktakeNo(id), warehouse.id(), warehouse.name(),
                request.stocktakeDate() == null ? LocalDate.now() : request.stocktakeDate(), "待确认", blankToNull(request.remark()),
                List.copyOf(items), "系统管理员", LocalDateTime.now(), null, null, 0);
        stocktakes.add(order);
        operationLogService.log("WAREHOUSE", "STOCKTAKE_CREATE", "STOCKTAKE", order.id(), order.stocktakeNo(), order.status());
        return order;
    }

    @Transactional
    public synchronized StocktakeOrder confirmStocktake(Long id) {
        if (relationalDataAvailable()) return confirmStocktakeRelational(id);
        StocktakeOrder existing = findStocktake(id);
        if ("已确认".equals(existing.status())) throw conflict("盘点单已确认，请勿重复操作");
        if (!"待确认".equals(existing.status())) throw badRequest("当前状态不能确认盘点");
        for (StocktakeOrderItem item : existing.items()) {
            InventoryBalance current = findBalance(existing.warehouseId(), item.productId());
            int quantity = current == null ? 0 : current.quantity();
            if (quantity != item.bookQuantity() || (current == null ? 0 : current.version()) != item.balanceVersion()) throw conflict("账面库存已变化，请重新创建盘点单");
        }
        for (StocktakeOrderItem item : existing.items()) {
            if (item.differenceQuantity() > 0) applyMovement(existing.warehouseId(), item.productId(), "盘盈调整", item.differenceQuantity(), item.reason(), existing.stocktakeNo(), true);
            if (item.differenceQuantity() < 0) applyMovement(existing.warehouseId(), item.productId(), "盘亏调整", -item.differenceQuantity(), item.reason(), existing.stocktakeNo(), true);
        }
        StocktakeOrder confirmed = new StocktakeOrder(existing.id(), existing.stocktakeNo(), existing.warehouseId(), existing.warehouseName(),
                existing.stocktakeDate(), "已确认", existing.remark(), existing.items(), existing.createdBy(), existing.createdAt(),
                "系统管理员", LocalDateTime.now(), existing.version() + 1);
        stocktakes.set(stocktakes.indexOf(existing), confirmed);
        operationLogService.log("WAREHOUSE", "STOCKTAKE_CONFIRM", "STOCKTAKE", confirmed.id(), confirmed.stocktakeNo(), confirmed.status());
        return confirmed;
    }

    private InventoryBalance findBalance(Long warehouseId, Long productId) {
        return balances.stream().filter(item -> item.warehouseId().equals(warehouseId) && item.productId().equals(productId)).findFirst().orElse(null);
    }

    private TransferOrder createTransferRelational(TransferOrderRequest request) {
        if (request.fromWarehouseId().equals(request.toWarehouseId())) throw badRequest("调出仓与调入仓不能相同");
        Warehouse from = findWarehouse(request.fromWarehouseId());
        Warehouse to = findWarehouse(request.toWarehouseId());
        if (!"启用".equals(from.status()) || !"启用".equals(to.status())) throw badRequest("停用仓库不能创建调拨单");
        if (request.items().size() > 50) throw badRequest("单张调拨单最多 50 条明细");
        Set<Long> productIds = new HashSet<>();
        List<TransferOrderItem> items = new ArrayList<>();
        for (TransferOrderRequest.Item line : request.items()) {
            if (!productIds.add(line.productId())) throw badRequest("同一商品不能重复填写");
            Product product = productService.findById(line.productId());
            if (!"启用".equals(product.status())) throw badRequest("停用商品不能调拨");
            items.add(new TransferOrderItem(product.id(), product.sku(), product.name(), product.unit(), line.quantity(), blankToNull(line.remark())));
        }
        long id = jdbcTemplate.queryForObject("SELECT COALESCE(MAX(id), 0) + 1 FROM wh_transfer_order", Long.class);
        String no = nextTransferNo(id);
        jdbcTemplate.update("INSERT INTO wh_transfer_order (id, transfer_no, from_warehouse_id, to_warehouse_id, transfer_date, status, remark, created_by, created_at, version) VALUES (?, ?, ?, ?, ?, '待确认', ?, 0, CURRENT_TIMESTAMP, 0)",
                id, no, from.id(), to.id(), request.transferDate() == null ? LocalDate.now() : request.transferDate(), blankToNull(request.remark()));
        for (TransferOrderItem item : items) jdbcTemplate.update("INSERT INTO wh_transfer_order_item (transfer_order_id, product_id, quantity, remark) VALUES (?, ?, ?, ?)", id, item.productId(), item.quantity(), item.remark());
        operationLogService.log("WAREHOUSE", "TRANSFER_CREATE", "TRANSFER", id, no, "待确认");
        return readTransfer(id);
    }

    private TransferOrder confirmTransferRelational(Long id) {
        TransferOrder existing = findTransfer(id);
        if ("已确认".equals(existing.status())) throw conflict("调拨单已确认，请勿重复操作");
        if (!"待确认".equals(existing.status())) throw badRequest("当前状态不能确认调拨");
        for (TransferOrderItem item : existing.items()) assertAvailableStock(existing.fromWarehouseId(), item.productId(), item.quantity());
        for (TransferOrderItem item : existing.items()) {
            applyMovementRelational(existing.fromWarehouseId(), item.productId(), "仓库调出", item.quantity(), existing.remark(), existing.transferNo(), false);
            applyMovementRelational(existing.toWarehouseId(), item.productId(), "仓库调入", item.quantity(), existing.remark(), existing.transferNo(), false);
        }
        if (jdbcTemplate.update("UPDATE wh_transfer_order SET status = '已确认', confirmed_by = 0, confirmed_at = CURRENT_TIMESTAMP, version = version + 1 WHERE id = ? AND status = '待确认' AND version = ?", id, existing.version()) != 1)
            throw conflict("调拨单状态已变化，请刷新后重试");
        operationLogService.log("WAREHOUSE", "TRANSFER_CONFIRM", "TRANSFER", id, existing.transferNo(), "已确认");
        return findTransfer(id);
    }

    private TransferOrder readTransfer(Long id) {
        List<TransferOrder> orders = jdbcTemplate.query("""
                SELECT t.id, t.transfer_no, t.from_warehouse_id, fw.name from_name, t.to_warehouse_id, tw.name to_name,
                       t.transfer_date, t.status, t.remark, t.created_at, t.confirmed_at, t.version
                  FROM wh_transfer_order t JOIN md_warehouse fw ON fw.id = t.from_warehouse_id JOIN md_warehouse tw ON tw.id = t.to_warehouse_id
                 WHERE t.id = ?
                """, (rs, row) -> new TransferOrder(rs.getLong("id"), rs.getString("transfer_no"), rs.getLong("from_warehouse_id"), rs.getString("from_name"),
                rs.getLong("to_warehouse_id"), rs.getString("to_name"), rs.getDate("transfer_date").toLocalDate(), rs.getString("status"), rs.getString("remark"),
                readTransferItems(id), "系统任务", rs.getTimestamp("created_at").toLocalDateTime(), "已确认".equals(rs.getString("status")) ? "系统任务" : null,
                rs.getTimestamp("confirmed_at") == null ? null : rs.getTimestamp("confirmed_at").toLocalDateTime(), rs.getLong("version")), id);
        if (orders.isEmpty()) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "调拨单不存在");
        return orders.get(0);
    }

    private List<TransferOrderItem> readTransferItems(Long id) {
        return jdbcTemplate.query("SELECT i.product_id, p.sku, p.name, p.unit, i.quantity, i.remark FROM wh_transfer_order_item i JOIN md_product p ON p.id = i.product_id WHERE i.transfer_order_id = ? ORDER BY i.id",
                (rs, row) -> new TransferOrderItem(rs.getLong("product_id"), rs.getString("sku"), rs.getString("name"), rs.getString("unit"), rs.getInt("quantity"), rs.getString("remark")), id);
    }

    private StocktakeOrder createStocktakeRelational(StocktakeOrderRequest request) {
        Warehouse warehouse = findWarehouse(request.warehouseId());
        if (!"启用".equals(warehouse.status())) throw badRequest("停用仓库不能创建盘点单");
        if (request.items().size() > 50) throw badRequest("单张盘点单最多 50 条明细");
        Set<Long> productIds = new HashSet<>();
        List<StocktakeOrderItem> items = new ArrayList<>();
        for (StocktakeOrderRequest.Item line : request.items()) {
            if (!productIds.add(line.productId())) throw badRequest("同一商品不能重复填写");
            Product product = productService.findById(line.productId());
            InventoryBalance balance = findBalances(warehouse.id(), product.id(), null).stream().findFirst().orElse(null);
            int book = balance == null ? 0 : balance.quantity();
            items.add(new StocktakeOrderItem(product.id(), product.sku(), product.name(), product.unit(), book, line.countedQuantity(), line.countedQuantity() - book,
                    line.reason().trim(), balance == null ? 0 : balance.version()));
        }
        long id = jdbcTemplate.queryForObject("SELECT COALESCE(MAX(id), 0) + 1 FROM wh_stocktake_order", Long.class);
        String no = nextStocktakeNo(id);
        jdbcTemplate.update("INSERT INTO wh_stocktake_order (id, stocktake_no, warehouse_id, stocktake_date, status, remark, created_by, created_at, version) VALUES (?, ?, ?, ?, '待确认', ?, 0, CURRENT_TIMESTAMP, 0)",
                id, no, warehouse.id(), request.stocktakeDate() == null ? LocalDate.now() : request.stocktakeDate(), blankToNull(request.remark()));
        for (StocktakeOrderItem item : items) jdbcTemplate.update("INSERT INTO wh_stocktake_order_item (stocktake_order_id, product_id, book_quantity, counted_quantity, difference_quantity, reason, balance_version) VALUES (?, ?, ?, ?, ?, ?, ?)",
                id, item.productId(), item.bookQuantity(), item.countedQuantity(), item.differenceQuantity(), item.reason(), item.balanceVersion());
        operationLogService.log("WAREHOUSE", "STOCKTAKE_CREATE", "STOCKTAKE", id, no, "待确认");
        return readStocktake(id);
    }

    private StocktakeOrder confirmStocktakeRelational(Long id) {
        StocktakeOrder existing = findStocktake(id);
        if ("已确认".equals(existing.status())) throw conflict("盘点单已确认，请勿重复操作");
        if (!"待确认".equals(existing.status())) throw badRequest("当前状态不能确认盘点");
        for (StocktakeOrderItem item : existing.items()) {
            InventoryBalance current = findBalances(existing.warehouseId(), item.productId(), null).stream().findFirst().orElse(null);
            if ((current == null ? 0 : current.quantity()) != item.bookQuantity() || (current == null ? 0 : current.version()) != item.balanceVersion())
                throw conflict("账面库存已变化，请重新创建盘点单");
        }
        for (StocktakeOrderItem item : existing.items()) {
            if (item.differenceQuantity() > 0) applyMovementRelational(existing.warehouseId(), item.productId(), "盘盈调整", item.differenceQuantity(), item.reason(), existing.stocktakeNo(), true);
            if (item.differenceQuantity() < 0) applyMovementRelational(existing.warehouseId(), item.productId(), "盘亏调整", -item.differenceQuantity(), item.reason(), existing.stocktakeNo(), true);
        }
        if (jdbcTemplate.update("UPDATE wh_stocktake_order SET status = '已确认', confirmed_by = 0, confirmed_at = CURRENT_TIMESTAMP, version = version + 1 WHERE id = ? AND status = '待确认' AND version = ?", id, existing.version()) != 1)
            throw conflict("盘点单状态已变化，请刷新后重试");
        operationLogService.log("WAREHOUSE", "STOCKTAKE_CONFIRM", "STOCKTAKE", id, existing.stocktakeNo(), "已确认");
        return findStocktake(id);
    }

    private StocktakeOrder readStocktake(Long id) {
        List<StocktakeOrder> orders = jdbcTemplate.query("""
                SELECT s.id, s.stocktake_no, s.warehouse_id, w.name warehouse_name, s.stocktake_date, s.status, s.remark,
                       s.created_at, s.confirmed_at, s.version FROM wh_stocktake_order s JOIN md_warehouse w ON w.id = s.warehouse_id WHERE s.id = ?
                """, (rs, row) -> new StocktakeOrder(rs.getLong("id"), rs.getString("stocktake_no"), rs.getLong("warehouse_id"), rs.getString("warehouse_name"),
                rs.getDate("stocktake_date").toLocalDate(), rs.getString("status"), rs.getString("remark"), readStocktakeItems(id), "系统任务",
                rs.getTimestamp("created_at").toLocalDateTime(), "已确认".equals(rs.getString("status")) ? "系统任务" : null,
                rs.getTimestamp("confirmed_at") == null ? null : rs.getTimestamp("confirmed_at").toLocalDateTime(), rs.getLong("version")), id);
        if (orders.isEmpty()) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "盘点单不存在");
        return orders.get(0);
    }

    private List<StocktakeOrderItem> readStocktakeItems(Long id) {
        return jdbcTemplate.query("SELECT i.product_id, p.sku, p.name, p.unit, i.book_quantity, i.counted_quantity, i.difference_quantity, i.reason, i.balance_version FROM wh_stocktake_order_item i JOIN md_product p ON p.id = i.product_id WHERE i.stocktake_order_id = ? ORDER BY i.id",
                (rs, row) -> new StocktakeOrderItem(rs.getLong("product_id"), rs.getString("sku"), rs.getString("name"), rs.getString("unit"), rs.getInt("book_quantity"), rs.getInt("counted_quantity"), rs.getInt("difference_quantity"), rs.getString("reason"), rs.getLong("balance_version")), id);
    }

    private StockFlow applyMovement(Long warehouseId, Long productId, String requestedBusinessType, Integer quantity,
                                    String remark, String sourceNo, boolean syncProductTotal) {
        if (warehouseId == null || productId == null) throw badRequest("仓库与商品不能为空");
        if (quantity == null || quantity <= 0) throw badRequest("变动数量必须大于 0");
        Warehouse warehouse = findWarehouse(warehouseId);
        if (!"启用".equals(warehouse.status())) throw badRequest("仓库已停用，不能进行出入库操作");
        Product product = productService.findById(productId);
        String businessType = blankToDefault(requestedBusinessType, "手工入库");
        boolean inbound = "手工入库".equals(businessType);
        boolean outbound = "手工出库".equals(businessType);
        if (!inbound && !outbound && !"采购入库".equals(businessType) && !"销售出库".equals(businessType) && !"销售退货".equals(businessType)
                && !"仓库调入".equals(businessType) && !"仓库调出".equals(businessType) && !"盘盈调整".equals(businessType) && !"盘亏调整".equals(businessType)) {
            throw badRequest("业务类型不受支持");
        }
        boolean actualInbound = inbound || "采购入库".equals(businessType) || "销售退货".equals(businessType)
                || "仓库调入".equals(businessType) || "盘盈调整".equals(businessType);
        InventoryBalance existing = balances.stream().filter(item -> item.warehouseId().equals(warehouse.id()) && item.productId().equals(product.id())).findFirst().orElse(null);
        int beforeQuantity = existing == null ? 0 : existing.quantity();
        int change = actualInbound ? quantity : -quantity;
        int newQuantity = beforeQuantity + change;
        if (!actualInbound && (existing == null || newQuantity < 0)) throw conflict(existing == null ? "该仓库无此商品库存，不能出库" : "出库数量超过当前库存");
        if (syncProductTotal) {
            if (actualInbound) productService.increaseStock(product.id(), quantity); else productService.decreaseStock(product.id(), quantity);
        }
        if (existing == null) {
            balances.add(new InventoryBalance(nextBalanceId.getAndIncrement(), warehouse.id(), warehouse.name(), product.id(), product.sku(), product.name(),
                    newQuantity, 0, newQuantity, product.safetyStock(), product.unit(), 0));
        } else {
            balances.set(balances.indexOf(existing), new InventoryBalance(existing.id(), existing.warehouseId(), existing.warehouseName(), existing.productId(),
                    existing.productSku(), existing.productName(), newQuantity, existing.lockedQuantity(), newQuantity - existing.lockedQuantity(), existing.safetyStock(), existing.unit(), existing.version() + 1));
        }
        long flowId = nextFlowId.getAndIncrement();
        String flowSource = sourceNo == null ? blankToNull(remark) : blankToNull(sourceNo);
        StockFlow flow = new StockFlow(flowId, nextFlowNo(flowId, actualInbound), warehouse.id(), warehouse.name(), product.id(), product.sku(), product.name(),
                businessType, change, flowSource, "系统管理员", LocalDateTime.now(), beforeQuantity, newQuantity);
        flows.add(flow);
        return flow;
    }

    private StockFlow applyMovementRelational(Long warehouseId, Long productId, String requestedBusinessType, Integer quantity,
                                              String remark, String sourceNo, boolean syncProductTotal) {
        if (warehouseId == null || productId == null) throw badRequest("仓库与商品不能为空");
        if (quantity == null || quantity <= 0) throw badRequest("变动数量必须大于 0");
        Warehouse warehouse = findWarehouse(warehouseId);
        if (!"启用".equals(warehouse.status())) throw badRequest("仓库已停用，不能进行出入库操作");
        Product product = productService.findById(productId);
        String businessType = blankToDefault(requestedBusinessType, "手工入库");
        boolean inbound = "手工入库".equals(businessType);
        boolean outbound = "手工出库".equals(businessType);
        if (!inbound && !outbound && !Set.of("采购入库", "销售出库", "销售退货", "仓库调入", "仓库调出", "盘盈调整", "盘亏调整").contains(businessType))
            throw badRequest("业务类型不受支持");
        boolean actualInbound = inbound || Set.of("采购入库", "销售退货", "仓库调入", "盘盈调整").contains(businessType);
        Integer before = jdbcTemplate.query("SELECT quantity FROM wh_inventory_balance WHERE warehouse_id = ? AND product_id = ? FOR UPDATE",
                rs -> rs.next() ? rs.getInt(1) : null, warehouseId, productId);
        int beforeQuantity = before == null ? 0 : before;
        int change = actualInbound ? quantity : -quantity;
        int afterQuantity = beforeQuantity + change;
        if (!actualInbound && afterQuantity < 0) throw conflict(before == null ? "该仓库无此商品库存，不能出库" : "出库数量超过当前库存");
        if (before == null) {
            jdbcTemplate.update("INSERT INTO wh_inventory_balance (warehouse_id, product_id, quantity, locked_quantity, available_quantity, version, updated_by) VALUES (?, ?, ?, 0, ?, 0, 0)",
                    warehouseId, productId, afterQuantity, afterQuantity);
        } else {
            jdbcTemplate.update("UPDATE wh_inventory_balance SET quantity = ?, available_quantity = ? - locked_quantity, version = version + 1, updated_by = 0 WHERE warehouse_id = ? AND product_id = ?",
                    afterQuantity, afterQuantity, warehouseId, productId);
        }
        long flowId = jdbcTemplate.queryForObject("SELECT COALESCE(MAX(id), 9000) + 1 FROM wh_stock_flow", Long.class);
        String flowNo = nextFlowNo(flowId, actualInbound);
        String flowSource = sourceNo == null ? blankToNull(remark) : blankToNull(sourceNo);
        String operator = currentOperator();
        jdbcTemplate.update("""
                INSERT INTO wh_stock_flow (id, flow_no, warehouse_id, warehouse_name, product_id, sku, product_name, unit,
                    business_type, change_quantity, before_quantity, after_quantity, source_type, source_id, source_no, remark, operator_name, created_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, CURRENT_TIMESTAMP)
                """, flowId, flowNo, warehouse.id(), warehouse.name(), product.id(), product.sku(), product.name(), product.unit(), businessType,
                change, beforeQuantity, afterQuantity, sourceType(businessType), flowId, flowSource == null ? flowNo : flowSource, blankToNull(remark), operator);
        operationLogService.log("WAREHOUSE", "STOCK_FLOW_CREATE", "STOCK_FLOW", flowId, flowNo, businessType);
        return new StockFlow(flowId, flowNo, warehouse.id(), warehouse.name(), product.id(), product.sku(), product.name(), businessType,
                change, flowSource, operator, LocalDateTime.now(), beforeQuantity, afterQuantity);
    }

    private String currentOperator() {
        var authentication = org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication();
        return authentication == null || "anonymousUser".equals(authentication.getName()) ? "系统任务" : authentication.getName();
    }

    private boolean relationalDataAvailable() { return jdbcTemplate != null && jdbcTemplate.queryForObject("SELECT COUNT(*) FROM md_warehouse", Integer.class) > 0; }

    private String nextFlowNo(long id, boolean inbound) { return (inbound ? "IN" : "OUT") + LocalDateTime.now().format(FLOW_DATE_FORMAT) + "-" + String.format("%05d", id - 9000); }
    private String nextTransferNo(long id) { return "TRF" + LocalDate.now().format(FLOW_DATE_FORMAT) + "-" + String.format("%05d", id); }
    private String nextStocktakeNo(long id) { return "STK" + LocalDate.now().format(FLOW_DATE_FORMAT) + "-" + String.format("%05d", id); }
    private String sourceType(String businessType) {
        return switch (businessType) {
            case "采购入库" -> "PURCHASE";
            case "销售出库" -> "SALE";
            case "销售退货" -> "SALE_RETURN";
            case "仓库调入", "仓库调出" -> "TRANSFER";
            case "盘盈调整", "盘亏调整" -> "STOCKTAKE";
            default -> "MANUAL";
        };
    }
    private void assertCodeAvailable(String code, Long currentId) {
        if (warehouses.stream().anyMatch(item -> item.code().equalsIgnoreCase(code.trim()) && !item.id().equals(currentId))) throw conflict("仓库编码已存在");
    }
    private String blankToNull(String value) { return value == null || value.isBlank() ? null : value.trim(); }
    private String blankToDefault(String value, String fallback) { return value == null || value.isBlank() ? fallback : value.trim(); }
    private ResponseStatusException badRequest(String message) { return new ResponseStatusException(HttpStatus.BAD_REQUEST, message); }
    private ResponseStatusException conflict(String message) { return new ResponseStatusException(HttpStatus.CONFLICT, message); }

    public synchronized State exportState() { return new State(List.copyOf(warehouses), List.copyOf(balances), List.copyOf(flows), List.copyOf(transfers), List.copyOf(stocktakes), nextWarehouseId.get(), nextBalanceId.get(), nextFlowId.get(), nextTransferId.get(), nextStocktakeId.get()); }
    public synchronized void restoreState(State state) {
        warehouses.clear(); warehouses.addAll(state.warehouses()); balances.clear(); balances.addAll(state.balances()); flows.clear(); flows.addAll(state.flows());
        transfers.clear(); if (state.transfers() != null) transfers.addAll(state.transfers());
        stocktakes.clear(); if (state.stocktakes() != null) stocktakes.addAll(state.stocktakes());
        nextWarehouseId.set(state.nextWarehouseId()); nextBalanceId.set(state.nextBalanceId()); nextFlowId.set(state.nextFlowId());
        nextTransferId.set(state.nextTransferId() > 0 ? state.nextTransferId() : 1);
        nextStocktakeId.set(state.nextStocktakeId() > 0 ? state.nextStocktakeId() : 1);
    }
    public record State(List<Warehouse> warehouses, List<InventoryBalance> balances, List<StockFlow> flows,
                        List<TransferOrder> transfers, List<StocktakeOrder> stocktakes, long nextWarehouseId,
                        long nextBalanceId, long nextFlowId, long nextTransferId, long nextStocktakeId) {}
}
