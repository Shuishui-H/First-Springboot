package com.erp.demo.warehouse;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 仓储管理接口：
 *  /api/warehouses   —— 仓库档案 CRUD
 *  /api/inventory    —— 库存余额查询（商品+仓库维度）
 *  /api/stock-flows  —— 库存流水列表与手工出入库登记
 */
@RestController
@RequestMapping("/api")
public class WarehouseController {

    private final WarehouseService warehouseService;

    public WarehouseController(WarehouseService warehouseService) {
        this.warehouseService = warehouseService;
    }

    // ===== 仓库档案 CRUD =====
    @GetMapping("/warehouses")
    public List<Warehouse> findAllWarehouses(@RequestParam(required = false) String keyword,
                                             @RequestParam(required = false) String status) {
        return warehouseService.findWarehouses(keyword, status);
    }

    @PostMapping("/warehouses")
    @ResponseStatus(HttpStatus.CREATED)
    public Warehouse createWarehouse(@Valid @RequestBody WarehouseRequest request) {
        return warehouseService.createWarehouse(request);
    }

    @PutMapping("/warehouses/{id}")
    public Warehouse updateWarehouse(@PathVariable Long id, @Valid @RequestBody WarehouseRequest request) {
        return warehouseService.updateWarehouse(id, request);
    }

    @DeleteMapping("/warehouses/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteWarehouse(@PathVariable Long id) {
        warehouseService.deleteWarehouse(id);
    }

    // ===== 库存余额查询 =====
    @GetMapping("/inventory")
    public List<InventoryBalance> findBalances(@RequestParam(required = false) Long warehouseId,
                                               @RequestParam(required = false) Long productId,
                                               @RequestParam(required = false) String keyword) {
        return warehouseService.findBalances(warehouseId, productId, keyword);
    }

    // ===== 库存流水列表 =====
    @GetMapping("/stock-flows")
    public List<StockFlow> findFlows(@RequestParam(required = false) Long warehouseId,
                                     @RequestParam(required = false) Long productId,
                                     @RequestParam(required = false) String businessType,
                                     @RequestParam(required = false) String keyword) {
        return warehouseService.findFlows(warehouseId, productId, businessType, keyword);
    }

    // ===== 手工出入库登记 =====
    @PostMapping("/stock-flows")
    @ResponseStatus(HttpStatus.CREATED)
    public StockFlow createMovement(@Valid @RequestBody StockMovementRequest request) {
        return warehouseService.createMovement(request);
    }

    public record StockMovementRequest(
            @NotNull(message = "仓库不能为空") Long warehouseId,
            @NotNull(message = "商品不能为空") Long productId,
            String businessType,
            @NotNull(message = "变动数量不能为空") Integer quantity,
            String remark
    ) {
    }
}
