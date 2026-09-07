package com.erp.demo.persistence;

import com.erp.demo.procurement.PurchaseService;
import com.erp.demo.procurement.SupplierService;
import com.erp.demo.product.ProductService;
import com.erp.demo.sales.CustomerService;
import com.erp.demo.sales.SalesService;
import com.erp.demo.warehouse.WarehouseService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * V2 运行时状态持久化边界：当前已实现的业务对象以一个一致性快照写入 MySQL。
 * 每次成功的写操作结束后保存，应用就绪时恢复，避免重启丢失演示业务数据。
 */
/**
 * Legacy snapshot reader retained for the V3 migration toolchain only.
 * It is deliberately not a Spring bean: V3 runtime services read and write relation tables.
 */
public class ApplicationStatePersistenceService {
    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;
    private final ProductService productService;
    private final SupplierService supplierService;
    private final CustomerService customerService;
    private final WarehouseService warehouseService;
    private final PurchaseService purchaseService;
    private final SalesService salesService;

    public ApplicationStatePersistenceService(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper,
                                              ProductService productService, SupplierService supplierService,
                                              CustomerService customerService, WarehouseService warehouseService,
                                              PurchaseService purchaseService, SalesService salesService) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
        this.productService = productService;
        this.supplierService = supplierService;
        this.customerService = customerService;
        this.warehouseService = warehouseService;
        this.purchaseService = purchaseService;
        this.salesService = salesService;
    }

    @EventListener(ApplicationReadyEvent.class)
    @Order(10)
    @Transactional
    public synchronized void restoreOrInitialize() {
        try {
            String json = jdbcTemplate.queryForObject("SELECT state_json FROM erp_application_state WHERE id = 1", String.class);
            StateSnapshot state = objectMapper.readValue(json, StateSnapshot.class);
            productService.restoreState(state.products());
            supplierService.restoreState(state.suppliers());
            customerService.restoreState(state.customers());
            warehouseService.restoreState(state.warehouses());
            purchaseService.restoreState(state.purchases());
            salesService.restoreState(state.sales());
        } catch (EmptyResultDataAccessException ignored) {
            persist();
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("无法读取 ERP 持久化业务数据", exception);
        }
    }

    @Transactional
    public synchronized void persist() {
        try {
            String json = objectMapper.writeValueAsString(snapshot());
            jdbcTemplate.update("INSERT INTO erp_application_state (id, state_json) VALUES (1, ?) "
                    + "ON DUPLICATE KEY UPDATE state_json = VALUES(state_json), updated_at = CURRENT_TIMESTAMP", json);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("无法保存 ERP 业务数据", exception);
        }
    }

    private StateSnapshot snapshot() {
        return new StateSnapshot(productService.exportState(), supplierService.exportState(), customerService.exportState(),
                warehouseService.exportState(), purchaseService.exportState(), salesService.exportState());
    }

    public record StateSnapshot(ProductService.State products, SupplierService.State suppliers, CustomerService.State customers,
                                WarehouseService.State warehouses, PurchaseService.State purchases, SalesService.State sales) {}
}
