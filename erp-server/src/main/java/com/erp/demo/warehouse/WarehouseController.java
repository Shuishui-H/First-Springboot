package com.erp.demo.warehouse;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/** 仓库档案、库存余额、库存流水和手工出入库接口。 */
@RestController
@RequestMapping("/api")
public class WarehouseController {
    private final WarehouseService warehouseService;

    public WarehouseController(WarehouseService warehouseService) { this.warehouseService = warehouseService; }

    @GetMapping("/warehouses")
    @PreAuthorize("hasAuthority('base:warehouse:list')")
    public List<Warehouse> findAllWarehouses(@RequestParam(required = false) String keyword,
                                             @RequestParam(required = false) String status) {
        return warehouseService.findWarehouses(keyword, status);
    }

    @PostMapping("/warehouses")
    @PreAuthorize("hasAuthority('base:warehouse:manage')")
    @ResponseStatus(HttpStatus.CREATED)
    public Warehouse createWarehouse(@Valid @RequestBody WarehouseRequest request) { return warehouseService.createWarehouse(request); }

    @PutMapping("/warehouses/{id}")
    @PreAuthorize("hasAuthority('base:warehouse:manage')")
    public Warehouse updateWarehouse(@PathVariable Long id, @Valid @RequestBody WarehouseRequest request) { return warehouseService.updateWarehouse(id, request); }

    @DeleteMapping("/warehouses/{id}")
    @PreAuthorize("hasAuthority('base:warehouse:manage')")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteWarehouse(@PathVariable Long id) { warehouseService.deleteWarehouse(id); }

    @GetMapping("/inventory")
    @PreAuthorize("hasAuthority('inventory:balance:view')")
    public List<InventoryBalance> findBalances(@RequestParam(required = false) Long warehouseId,
                                               @RequestParam(required = false) Long productId,
                                               @RequestParam(required = false) String keyword) {
        return warehouseService.findBalances(warehouseId, productId, keyword);
    }

    @GetMapping("/stock-flows")
    @PreAuthorize("hasAuthority('inventory:flow:view')")
    public List<StockFlow> findFlows(@RequestParam(required = false) Long warehouseId,
                                     @RequestParam(required = false) Long productId,
                                     @RequestParam(required = false) String businessType,
                                     @RequestParam(required = false) String keyword) {
        return warehouseService.findFlows(warehouseId, productId, businessType, keyword);
    }

    @PostMapping("/stock-flows")
    @PreAuthorize("hasAuthority('inventory:movement:manage')")
    @ResponseStatus(HttpStatus.CREATED)
    public StockFlow createMovement(@Valid @RequestBody StockMovementRequest request) { return warehouseService.createMovement(request); }

    @GetMapping("/inventory/transfers")
    @PreAuthorize("hasAuthority('inventory:transfer:manage')")
    public List<TransferOrder> findTransfers(@RequestParam(required = false) String status,
                                              @RequestParam(required = false) Long warehouseId,
                                              @RequestParam(required = false) String keyword) {
        return warehouseService.findTransfers(status, warehouseId, keyword);
    }

    @GetMapping("/inventory/transfers/{id}")
    @PreAuthorize("hasAuthority('inventory:transfer:manage')")
    public TransferOrder findTransfer(@PathVariable Long id) { return warehouseService.findTransfer(id); }

    @PostMapping("/inventory/transfers")
    @PreAuthorize("hasAuthority('inventory:transfer:manage')")
    @ResponseStatus(HttpStatus.CREATED)
    public TransferOrder createTransfer(@Valid @RequestBody TransferOrderRequest request) { return warehouseService.createTransfer(request); }

    @PostMapping("/inventory/transfers/{id}/confirm")
    @PreAuthorize("hasAuthority('inventory:transfer:manage')")
    public TransferOrder confirmTransfer(@PathVariable Long id) { return warehouseService.confirmTransfer(id); }

    @PostMapping("/inventory/transfers/{id}/cancel")
    @PreAuthorize("hasAuthority('inventory:transfer:manage')")
    public TransferOrder cancelTransfer(@PathVariable Long id) { return warehouseService.cancelTransfer(id); }

    @GetMapping("/inventory/stocktakes")
    @PreAuthorize("hasAuthority('inventory:stocktake:manage')")
    public List<StocktakeOrder> findStocktakes(@RequestParam(required = false) String status,
                                                @RequestParam(required = false) Long warehouseId,
                                                @RequestParam(required = false) String keyword) {
        return warehouseService.findStocktakes(status, warehouseId, keyword);
    }

    @GetMapping("/inventory/stocktakes/{id}")
    @PreAuthorize("hasAuthority('inventory:stocktake:manage')")
    public StocktakeOrder findStocktake(@PathVariable Long id) { return warehouseService.findStocktake(id); }

    @PostMapping("/inventory/stocktakes")
    @PreAuthorize("hasAuthority('inventory:stocktake:manage')")
    @ResponseStatus(HttpStatus.CREATED)
    public StocktakeOrder createStocktake(@Valid @RequestBody StocktakeOrderRequest request) { return warehouseService.createStocktake(request); }

    @PostMapping("/inventory/stocktakes/{id}/confirm")
    @PreAuthorize("hasAuthority('inventory:stocktake:manage')")
    public StocktakeOrder confirmStocktake(@PathVariable Long id) { return warehouseService.confirmStocktake(id); }

    public record StockMovementRequest(
            @NotNull(message = "仓库不能为空") Long warehouseId,
            @NotNull(message = "商品不能为空") Long productId,
            String businessType,
            @NotNull(message = "变动数量不能为空") @Min(value = 1, message = "变动数量必须大于 0") Integer quantity,
            String remark
    ) {
    }
}
