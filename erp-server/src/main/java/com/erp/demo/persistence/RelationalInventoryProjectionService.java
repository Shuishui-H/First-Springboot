package com.erp.demo.persistence;

import com.erp.demo.product.Product;
import com.erp.demo.product.ProductService;
import com.erp.demo.procurement.Supplier;
import com.erp.demo.procurement.SupplierService;
import com.erp.demo.sales.Customer;
import com.erp.demo.sales.CustomerService;
import com.erp.demo.warehouse.InventoryBalance;
import com.erp.demo.warehouse.StockFlow;
import com.erp.demo.warehouse.StocktakeOrder;
import com.erp.demo.warehouse.StocktakeOrderItem;
import com.erp.demo.warehouse.TransferOrder;
import com.erp.demo.warehouse.TransferOrderItem;
import com.erp.demo.warehouse.Warehouse;
import com.erp.demo.warehouse.WarehouseService;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.annotation.Profile;
import org.springframework.context.event.EventListener;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 将 V2 已保存的商品、仓库与分仓库存投影到 V3 关系表。
 * 迁移期间快照仍是采购/销售历史的兼容来源；库存主数据和余额从这里开始与关系表同步。
 */
@Service
@Profile("mysql")
public class RelationalInventoryProjectionService {
    private static final String MIGRATION_KEY = "INVENTORY_PROJECTION_V3";
    private final JdbcTemplate jdbcTemplate;
    private final ProductService productService;
    private final WarehouseService warehouseService;
    private final SupplierService supplierService;
    private final CustomerService customerService;

    public RelationalInventoryProjectionService(JdbcTemplate jdbcTemplate, ProductService productService, WarehouseService warehouseService,
                                                SupplierService supplierService, CustomerService customerService) {
        this.jdbcTemplate = jdbcTemplate;
        this.productService = productService;
        this.warehouseService = warehouseService;
        this.supplierService = supplierService;
        this.customerService = customerService;
    }

    @EventListener(ApplicationReadyEvent.class)
    @Order(20)
    @Transactional
    public void initializeProjection() { synchronize(); }

    @Transactional
    public synchronized void synchronize() {
        // V3 的 Flyway seed 已经建立关系表时，关系表是唯一运行时来源；
        // 只有空库首次启动才允许把旧演示状态导入一次。
        if (jdbcTemplate.queryForObject("SELECT COUNT(*) FROM md_product", Integer.class) > 0
                && jdbcTemplate.queryForObject("SELECT COUNT(*) FROM md_warehouse", Integer.class) > 0) return;
        jdbcTemplate.update("""
                INSERT INTO sys_data_migration (migration_key, status, started_at)
                VALUES (?, '进行中', CURRENT_TIMESTAMP)
                ON DUPLICATE KEY UPDATE status = '进行中', started_at = CURRENT_TIMESTAMP, error_message = NULL
                """, MIGRATION_KEY);
        try {
            var products = productService.findAll();
            Map<Long, Product> productsById = products.stream().collect(Collectors.toMap(Product::id, product -> product));
            for (Product product : products) {
                jdbcTemplate.update("""
                        INSERT INTO md_product (id, sku, name, category, unit, price, safety_stock, status, created_by, updated_by)
                        VALUES (?, ?, ?, ?, ?, ?, ?, ?, 0, 0)
                        ON DUPLICATE KEY UPDATE sku = VALUES(sku), name = VALUES(name), category = VALUES(category), unit = VALUES(unit),
                            price = VALUES(price), safety_stock = VALUES(safety_stock), status = VALUES(status), updated_by = 0
                        """, product.id(), product.sku(), product.name(), product.category(), product.unit(), product.price(), product.safetyStock(),
                        "启用".equals(product.status()) ? 1 : 0);
            }
            for (Warehouse warehouse : warehouseService.findWarehouses(null, null)) {
                jdbcTemplate.update("""
                        INSERT INTO md_warehouse (id, code, name, manager, address, status, remark, created_by, updated_by)
                        VALUES (?, ?, ?, ?, NULL, ?, 'V3 库存投影', 0, 0)
                        ON DUPLICATE KEY UPDATE code = VALUES(code), name = VALUES(name), manager = VALUES(manager),
                            status = VALUES(status), updated_by = 0
                        """, warehouse.id(), warehouse.code(), warehouse.name(), warehouse.manager(), "启用".equals(warehouse.status()) ? 1 : 0);
            }
            for (Supplier supplier : supplierService.findAll(null, null)) {
                jdbcTemplate.update("""
                        INSERT INTO md_supplier (id, code, name, contact, phone, status, remark, created_by, updated_by)
                        VALUES (?, ?, ?, ?, ?, ?, 'V3 数据投影', 0, 0)
                        ON DUPLICATE KEY UPDATE code = VALUES(code), name = VALUES(name), contact = VALUES(contact),
                            phone = VALUES(phone), status = VALUES(status), updated_by = 0
                        """, supplier.id(), supplier.code(), supplier.name(), supplier.contact(), supplier.phone(),
                        "启用".equals(supplier.status()) ? 1 : 0);
            }
            for (Customer customer : customerService.findAll(null, null)) {
                jdbcTemplate.update("""
                        INSERT INTO md_customer (id, code, name, contact, phone, status, remark, created_by, updated_by)
                        VALUES (?, ?, ?, ?, ?, ?, 'V3 数据投影', 0, 0)
                        ON DUPLICATE KEY UPDATE code = VALUES(code), name = VALUES(name), contact = VALUES(contact),
                            phone = VALUES(phone), status = VALUES(status), updated_by = 0
                        """, customer.id(), customer.code(), customer.name(), customer.contact(), customer.phone(),
                        "启用".equals(customer.status()) ? 1 : 0);
            }
            var balances = warehouseService.findBalances(null, null, null);
            int skippedOrphanBalances = 0;
            for (InventoryBalance balance : balances) {
                Product product = productsById.get(balance.productId());
                if (product == null || !product.sku().equals(balance.productSku())) {
                    skippedOrphanBalances++;
                    continue;
                }
                jdbcTemplate.update("""
                        INSERT INTO wh_inventory_balance (warehouse_id, product_id, quantity, locked_quantity, available_quantity, version, updated_by)
                        VALUES (?, ?, ?, ?, ?, 0, 0)
                        ON DUPLICATE KEY UPDATE quantity = VALUES(quantity), locked_quantity = VALUES(locked_quantity),
                            available_quantity = VALUES(available_quantity), version = version + 1, updated_by = 0
                        """, balance.warehouseId(), balance.productId(), balance.quantity(), balance.lockedQuantity(), balance.availableQuantity());
            }
            for (TransferOrder transfer : warehouseService.findTransfers(null, null, null)) {
                jdbcTemplate.update("""
                        INSERT INTO wh_transfer_order (id, transfer_no, from_warehouse_id, to_warehouse_id, transfer_date, status, remark,
                            created_by, created_at, confirmed_by, confirmed_at, version)
                        VALUES (?, ?, ?, ?, ?, ?, ?, 0, ?, NULL, ?, ?)
                        ON DUPLICATE KEY UPDATE transfer_no = VALUES(transfer_no), from_warehouse_id = VALUES(from_warehouse_id),
                            to_warehouse_id = VALUES(to_warehouse_id), transfer_date = VALUES(transfer_date), status = VALUES(status),
                            remark = VALUES(remark), confirmed_at = VALUES(confirmed_at), version = VALUES(version)
                        """, transfer.id(), transfer.transferNo(), transfer.fromWarehouseId(), transfer.toWarehouseId(), transfer.transferDate(),
                        transfer.status(), transfer.remark(), transfer.createdAt(), transfer.confirmedAt(), transfer.version());
                jdbcTemplate.update("DELETE FROM wh_transfer_order_item WHERE transfer_order_id = ?", transfer.id());
                for (TransferOrderItem item : transfer.items()) {
                    jdbcTemplate.update("""
                            INSERT INTO wh_transfer_order_item (transfer_order_id, product_id, quantity, remark)
                            VALUES (?, ?, ?, ?)
                            """, transfer.id(), item.productId(), item.quantity(), item.remark());
                }
            }
            for (StocktakeOrder stocktake : warehouseService.findStocktakes(null, null, null)) {
                jdbcTemplate.update("""
                        INSERT INTO wh_stocktake_order (id, stocktake_no, warehouse_id, stocktake_date, status, remark,
                            created_by, created_at, confirmed_by, confirmed_at, version)
                        VALUES (?, ?, ?, ?, ?, ?, 0, ?, NULL, ?, ?)
                        ON DUPLICATE KEY UPDATE stocktake_no = VALUES(stocktake_no), warehouse_id = VALUES(warehouse_id),
                            stocktake_date = VALUES(stocktake_date), status = VALUES(status), remark = VALUES(remark),
                            confirmed_at = VALUES(confirmed_at), version = VALUES(version)
                        """, stocktake.id(), stocktake.stocktakeNo(), stocktake.warehouseId(), stocktake.stocktakeDate(), stocktake.status(),
                        stocktake.remark(), stocktake.createdAt(), stocktake.confirmedAt(), stocktake.version());
                jdbcTemplate.update("DELETE FROM wh_stocktake_order_item WHERE stocktake_order_id = ?", stocktake.id());
                for (StocktakeOrderItem item : stocktake.items()) {
                    jdbcTemplate.update("""
                            INSERT INTO wh_stocktake_order_item (stocktake_order_id, product_id, book_quantity, counted_quantity,
                                difference_quantity, reason, balance_version)
                            VALUES (?, ?, ?, ?, ?, ?, ?)
                            """, stocktake.id(), item.productId(), item.bookQuantity(), item.countedQuantity(), item.differenceQuantity(), item.reason(), item.balanceVersion());
                }
            }
            int projectedFlows = 0;
            int skippedLegacyFlows = 0;
            for (StockFlow flow : warehouseService.findFlows(null, null, null, null)) {
                Product product = productsById.get(flow.productId());
                // V3 之前的快照没有记录变动前后数量，不能把它伪造成可审计的正式流水。
                if (product == null || flow.beforeQuantity() == null || flow.afterQuantity() == null) {
                    skippedLegacyFlows++;
                    continue;
                }
                jdbcTemplate.update("""
                        INSERT INTO wh_stock_flow (id, flow_no, warehouse_id, warehouse_name, product_id, sku, product_name, unit,
                            business_type, change_quantity, before_quantity, after_quantity, source_type, source_id, source_no,
                            remark, operator_id, operator_name, created_at)
                        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, NULL, NULL, ?, ?)
                        ON DUPLICATE KEY UPDATE warehouse_id = VALUES(warehouse_id), warehouse_name = VALUES(warehouse_name),
                            product_id = VALUES(product_id), sku = VALUES(sku), product_name = VALUES(product_name), unit = VALUES(unit),
                            business_type = VALUES(business_type), change_quantity = VALUES(change_quantity),
                            before_quantity = VALUES(before_quantity), after_quantity = VALUES(after_quantity),
                            source_type = VALUES(source_type), source_id = VALUES(source_id), source_no = VALUES(source_no),
                            operator_name = VALUES(operator_name), created_at = VALUES(created_at)
                        """, flow.id(), flow.flowNo(), flow.warehouseId(), flow.warehouseName(), flow.productId(), flow.productSku(),
                        flow.productName(), product.unit(), flow.businessType(), flow.changeQuantity(), flow.beforeQuantity(), flow.afterQuantity(),
                        sourceType(flow.businessType()), flow.id(), flow.sourceNo() == null ? flow.flowNo() : flow.sourceNo(),
                        flow.operator(), flow.time());
                projectedFlows++;
            }
            jdbcTemplate.update("""
                    UPDATE sys_data_migration
                       SET status = '成功', finished_at = CURRENT_TIMESTAMP,
                           summary_json = JSON_OBJECT('products', ?, 'warehouses', ?, 'balances', ?, 'transfers', ?, 'stocktakes', ?, 'flows', ?, 'skippedLegacyFlows', ?, 'skippedOrphanBalances', ?), error_message = NULL
                     WHERE migration_key = ?
                    """, products.size(), warehouseService.findWarehouses(null, null).size(), balances.size() - skippedOrphanBalances,
                    warehouseService.findTransfers(null, null, null).size(), warehouseService.findStocktakes(null, null, null).size(),
                    projectedFlows, skippedLegacyFlows, skippedOrphanBalances, MIGRATION_KEY);
        } catch (RuntimeException exception) {
            jdbcTemplate.update("UPDATE sys_data_migration SET status = '失败', finished_at = CURRENT_TIMESTAMP, error_message = ? WHERE migration_key = ?",
                    exception.getMessage() == null ? "库存投影失败" : exception.getMessage().substring(0, Math.min(1000, exception.getMessage().length())), MIGRATION_KEY);
            throw exception;
        }
    }

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
}
