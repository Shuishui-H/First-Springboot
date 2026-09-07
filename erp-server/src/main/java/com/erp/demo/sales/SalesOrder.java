package com.erp.demo.sales;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record SalesOrder(Long id, String orderNo, Long customerId, String customerName, Long warehouseId,
                         String warehouseName, LocalDate orderDate, LocalDate requiredShipDate, String status,
                         Integer totalQuantity, Integer shippedQuantity, Integer returnedQuantity,
                         BigDecimal totalAmount, String remark, String approvalComment, List<SalesOrderItem> items) {
    @JsonProperty("pendingQuantity")
    public int pendingQuantity() { return items.stream().mapToInt(SalesOrderItem::pendingQuantity).sum(); }
    public int getPendingQuantity() { return pendingQuantity(); }
    @JsonProperty("returnableQuantity")
    public int returnableQuantity() { return items.stream().mapToInt(SalesOrderItem::returnableQuantity).sum(); }
    public int getReturnableQuantity() { return returnableQuantity(); }
    @JsonProperty("progress")
    public int progress() { return totalQuantity == 0 ? 0 : Math.round(shippedQuantity * 100f / totalQuantity); }
    public int getProgress() { return progress(); }
}
