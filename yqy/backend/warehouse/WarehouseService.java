package com.erp.demo.warehouse;

import com.erp.demo.product.Product;
import com.erp.demo.product.ProductService;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 仓储管理服务（内存数据，风格与 ProductService / ProcurementService 一致）
 * 库存余额为“商品+仓库”维度，初始从商品档案现有库存同步到主仓；
 * 手工出入库在维护本模块分仓余额的同时，同步调用 ProductService 增减商品档案总库存，实现两处数据联动一致。
 */
@Service
public class WarehouseService {

    private static final DateTimeFormatter FLOW_DATE_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd");

    private final AtomicLong nextWarehouseId = new AtomicLong(3);
    private final AtomicLong nextBalanceId = new AtomicLong(100);
    private final AtomicLong nextFlowId = new AtomicLong(9001);

    private final ProductService productService;
    private final List<Warehouse> warehouses = new ArrayList<>();
    private final List<InventoryBalance> balances = new ArrayList<>();
    private final List<StockFlow> flows = new ArrayList<>();

    public WarehouseService(ProductService productService) {
        this.productService = productService;
        warehouses.add(new Warehouse(1L, "WH-MAIN", "主仓", "张伟", "启用"));
        warehouses.add(new Warehouse(2L, "WH-EAST", "华东仓", "李娜", "启用"));

        // 从商品档案同步初始库存到主仓，保证演示数据一致
        for (Product product : productService.findAll()) {
            balances.add(new InventoryBalance(nextBalanceId.getAndIncrement(), 1L, "主仓",
                    product.id(), product.sku(), product.name(), product.stock(), 0,
                    product.stock(), product.safetyStock(), product.unit()));
        }
    }

    public synchronized List<Warehouse> findWarehouses(String keyword, String status) {
        String query = keyword == null ? "" : keyword.trim().toLowerCase();
        return warehouses.stream()
                .filter(warehouse -> query.isBlank()
                        || warehouse.code().toLowerCase().contains(query)
                        || warehouse.name().toLowerCase().contains(query)
                        || (warehouse.manager() != null && warehouse.manager().toLowerCase().contains(query)))
                .filter(warehouse -> status == null || status.isBlank() || "全部状态".equals(status)
                        || warehouse.status().equals(status))
                .sorted(Comparator.comparing(Warehouse::id))
                .toList();
    }

    public synchronized Warehouse findWarehouse(Long id) {
        return warehouses.stream()
                .filter(warehouse -> warehouse.id().equals(id))
                .findFirst()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "仓库不存在"));
    }

    public synchronized Warehouse createWarehouse(WarehouseRequest request) {
        assertCodeAvailable(request.code(), null);
        Warehouse warehouse = new Warehouse(nextWarehouseId.getAndIncrement(),
                request.code().trim(), request.name().trim(),
                blankToNull(request.manager()), blankToDefault(request.status(), "启用"));
        warehouses.add(warehouse);
        return warehouse;
    }

    public synchronized Warehouse updateWarehouse(Long id, WarehouseRequest request) {
        Warehouse existing = findWarehouse(id);
        assertCodeAvailable(request.code(), id);
        Warehouse updated = new Warehouse(existing.id(), request.code().trim(), request.name().trim(),
                blankToNull(request.manager()), blankToDefault(request.status(), existing.status()));
        warehouses.set(warehouses.indexOf(existing), updated);

        // 同步余额中的仓库名称
        for (int i = 0; i < balances.size(); i++) {
            InventoryBalance balance = balances.get(i);
            if (balance.warehouseId().equals(id)) {
                balances.set(i, new InventoryBalance(balance.id(), balance.warehouseId(), updated.name(),
                        balance.productId(), balance.productSku(), balance.productName(), balance.quantity(),
                        balance.lockedQuantity(), balance.availableQuantity(), balance.safetyStock(), balance.unit()));
            }
        }
        return updated;
    }

    public synchronized void deleteWarehouse(Long id) {
        Warehouse existing = findWarehouse(id);
        boolean hasStock = balances.stream()
                .anyMatch(balance -> balance.warehouseId().equals(id) && balance.quantity() > 0);
        if (hasStock) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "该仓库仍有库存，不能删除");
        }
        warehouses.remove(existing);
        balances.removeIf(balance -> balance.warehouseId().equals(id));
    }

    public synchronized List<InventoryBalance> findBalances(Long warehouseId, Long productId, String keyword) {
        String query = keyword == null ? "" : keyword.trim().toLowerCase();
        return balances.stream()
                .filter(balance -> warehouseId == null || balance.warehouseId().equals(warehouseId))
                .filter(balance -> productId == null || balance.productId().equals(productId))
                .filter(balance -> query.isBlank()
                        || balance.productName().toLowerCase().contains(query)
                        || balance.productSku().toLowerCase().contains(query)
                        || balance.warehouseName().toLowerCase().contains(query))
                .sorted(Comparator.comparing(InventoryBalance::id))
                .toList();
    }

    public synchronized List<StockFlow> findFlows(Long warehouseId, Long productId, String businessType, String keyword) {
        String query = keyword == null ? "" : keyword.trim().toLowerCase();
        return flows.stream()
                .filter(flow -> warehouseId == null || flow.warehouseId().equals(warehouseId))
                .filter(flow -> productId == null || flow.productId().equals(productId))
                .filter(flow -> businessType == null || businessType.isBlank() || "全部类型".equals(businessType)
                        || flow.businessType().equals(businessType))
                .filter(flow -> query.isBlank()
                        || flow.flowNo().toLowerCase().contains(query)
                        || flow.productName().toLowerCase().contains(query)
                        || flow.productSku().toLowerCase().contains(query))
                .sorted(Comparator.comparing(StockFlow::id).reversed())
                .toList();
    }

    public synchronized StockFlow createMovement(WarehouseController.StockMovementRequest request) {
        if (request.warehouseId() == null || request.productId() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "仓库与商品不能为空");
        }
        if (request.quantity() == null || request.quantity() <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "变动数量必须大于 0");
        }
        Warehouse warehouse = findWarehouse(request.warehouseId());
        if (!"启用".equals(warehouse.status())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "仓库已停用，不能进行出入库操作");
        }
        Product product = productService.findById(request.productId());

        String businessType = blankToDefault(request.businessType(), "手工入库");
        boolean inbound = "手工入库".equals(businessType);
        boolean outbound = "手工出库".equals(businessType);
        if (!inbound && !outbound) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "业务类型仅支持：手工入库 / 手工出库");
        }
        int change = inbound ? request.quantity() : -request.quantity();

        InventoryBalance existing = balances.stream()
                .filter(balance -> balance.warehouseId().equals(warehouse.id())
                        && balance.productId().equals(product.id()))
                .findFirst().orElse(null);

        // 出库前先校验分仓库存是否充足（不产生任何状态变更）
        int newQuantity;
        if (existing == null) {
            if (outbound) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "该仓库无此商品库存，不能出库");
            }
            newQuantity = change;
        } else {
            newQuantity = existing.quantity() + change;
            if (newQuantity < 0) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "出库数量超过当前库存");
            }
        }

        // 取消库存隔离：分仓校验通过后，同步更新商品档案总库存，保证两处数据联动一致
        if (inbound) {
            productService.increaseStock(product.id(), request.quantity());
        } else {
            productService.decreaseStock(product.id(), request.quantity());
        }

        // 写入分仓余额
        if (existing == null) {
            balances.add(new InventoryBalance(nextBalanceId.getAndIncrement(),
                    warehouse.id(), warehouse.name(), product.id(), product.sku(), product.name(),
                    newQuantity, 0, newQuantity, product.safetyStock(), product.unit()));
        } else {
            balances.set(balances.indexOf(existing), new InventoryBalance(
                    existing.id(), existing.warehouseId(), existing.warehouseName(), existing.productId(),
                    existing.productSku(), existing.productName(), newQuantity, existing.lockedQuantity(),
                    newQuantity - existing.lockedQuantity(), existing.safetyStock(), existing.unit()));
        }

        long flowId = nextFlowId.getAndIncrement();
        StockFlow flow = new StockFlow(flowId, nextFlowNo(flowId, inbound), warehouse.id(), warehouse.name(),
                product.id(), product.sku(), product.name(), businessType, change,
                blankToNull(request.remark()), "系统管理员", LocalDateTime.now());
        flows.add(flow);
        return flow;
    }

    private String nextFlowNo(long id, boolean inbound) {
        String prefix = inbound ? "IN" : "OUT";
        return prefix + LocalDateTime.now().format(FLOW_DATE_FORMAT) + "-" + String.format("%05d", id - 9000);
    }

    private void assertCodeAvailable(String code, Long currentId) {
        boolean exists = warehouses.stream().anyMatch(warehouse -> warehouse.code().equalsIgnoreCase(code.trim())
                && !warehouse.id().equals(currentId));
        if (exists) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "仓库编码已存在");
        }
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private String blankToDefault(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.trim();
    }
}
