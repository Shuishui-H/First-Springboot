package com.erp.demo.warehouse;

import java.time.LocalDateTime;

/**
 * 库存流水实体（字段参考 PRD.md：流水号、商品、仓库、业务类型、变动数量、来源单号、操作人、时间）
 */
public record StockFlow(
        Long id,
        String flowNo,
        Long warehouseId,
        String warehouseName,
        Long productId,
        String productSku,
        String productName,
        String businessType,
        Integer changeQuantity,
        String sourceNo,
        String operator,
        LocalDateTime time
) {
}
