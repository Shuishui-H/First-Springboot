package com.erp.demo.sales;

import java.time.LocalDate;
import java.util.List;

public record SalesReturnOrder(Long id, String returnNo, Long stockOutId, String stockOutNo, Long salesOrderId,
                               String salesOrderNo, String customerName, Long warehouseId, String warehouseName,
                               LocalDate returnDate, String status, Integer totalQuantity, String reason,
                               String remark, List<Item> items) {
    public record Item(Long id, Long stockOutItemId, Long salesOrderItemId, Long productId,
                       String productName, Integer returnedQuantity) {}
}
