package com.erp.demo.warehouse;

/**
 * 库存余额实体（字段参考 PRD.md：商品、仓库、现存数量、锁定数量、可用数量）
 */
public record InventoryBalance(
        Long id,
        Long warehouseId,
        String warehouseName,
        Long productId,
        String productSku,
        String productName,
        Integer quantity,
        Integer lockedQuantity,
        Integer availableQuantity,
        Integer safetyStock,
        String unit
) {
}
