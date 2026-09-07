package com.erp.demo.warehouse;

/** 商品与仓库维度的库存余额。 */
public record InventoryBalance(Long id, Long warehouseId, String warehouseName, Long productId,
                               String productSku, String productName, Integer quantity, Integer lockedQuantity,
                               Integer availableQuantity, Integer safetyStock, String unit, long version) {
}
