package com.erp.demo.procurement;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record PurchaseOrder(
        Long id,
        String orderNo,
        Long supplierId,
        String supplierName,
        LocalDate orderDate,
        LocalDate expectedArrivalDate,
        String status,
        Integer totalQuantity,
        BigDecimal totalAmount,
        String remark,
        String approvalComment,
        List<PurchaseOrderItem> items
) {
    public int receivedQuantity() {
        return items.stream().mapToInt(PurchaseOrderItem::receivedQuantity).sum();
    }

    public int pendingQuantity() {
        return items.stream().mapToInt(PurchaseOrderItem::pendingQuantity).sum();
    }

    public int progress() {
        return totalQuantity == 0 ? 0 : Math.round(receivedQuantity() * 100f / totalQuantity);
    }
}
