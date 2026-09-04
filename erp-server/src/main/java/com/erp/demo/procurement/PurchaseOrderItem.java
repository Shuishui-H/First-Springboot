package com.erp.demo.procurement;

import java.math.BigDecimal;

public record PurchaseOrderItem(
        Long id,
        Long productId,
        String sku,
        String productName,
        String unit,
        Integer orderedQuantity,
        Integer receivedQuantity,
        BigDecimal unitPrice,
        BigDecimal lineAmount
) {
    public int pendingQuantity() {
        return orderedQuantity - receivedQuantity;
    }
}
