package com.erp.demo.sales;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.LocalDate;
import java.util.List;

public record StockOutOrder(Long id, String stockOutNo, Long salesOrderId, String salesOrderNo,
                            String customerName, Long warehouseId, String warehouseName, LocalDate stockOutDate,
                            String status, Integer totalQuantity, String remark, List<Item> items) {
    public record Item(Long id, Long salesOrderItemId, Long productId, String productName,
                       Integer shippedQuantity, Integer returnedQuantity) {
        @JsonProperty("returnableQuantity")
        public int returnableQuantity() { return shippedQuantity - returnedQuantity; }
        public int getReturnableQuantity() { return returnableQuantity(); }
    }
}
