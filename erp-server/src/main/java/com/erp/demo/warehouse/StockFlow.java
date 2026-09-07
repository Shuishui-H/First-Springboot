package com.erp.demo.warehouse;

import java.time.LocalDateTime;

/** 库存变动流水。 */
public record StockFlow(Long id, String flowNo, Long warehouseId, String warehouseName, Long productId,
                        String productSku, String productName, String businessType, Integer changeQuantity,
                        String sourceNo, String operator, LocalDateTime time, Integer beforeQuantity,
                        Integer afterQuantity) {
}
