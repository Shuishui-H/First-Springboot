package com.erp.demo.procurement;

import java.time.LocalDate;
import java.util.List;

public record PurchaseReceipt(
        Long id,
        String receiptNo,
        Long purchaseOrderId,
        String purchaseOrderNo,
        Long warehouseId,
        String warehouseName,
        LocalDate stockInDate,
        String status,
        Integer totalQuantity,
        String remark,
        List<Item> items
) {
    public record Item(Long id, Long purchaseOrderItemId, Long productId, String productName, int receivedQuantity) {
    }
}
