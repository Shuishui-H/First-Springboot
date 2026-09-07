package com.erp.demo.sales;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.math.BigDecimal;

public record SalesOrderItem(Long id, Long productId, String sku, String productName, String unit,
                             Integer orderedQuantity, Integer shippedQuantity, Integer returnedQuantity,
                             BigDecimal unitPrice, BigDecimal lineAmount) {
    @JsonProperty("pendingQuantity")
    public int pendingQuantity() { return orderedQuantity - shippedQuantity; }
    public int getPendingQuantity() { return pendingQuantity(); }
    @JsonProperty("returnableQuantity")
    public int returnableQuantity() { return shippedQuantity - returnedQuantity; }
    public int getReturnableQuantity() { return returnableQuantity(); }
    @JsonProperty("netQuantity")
    public int netQuantity() { return shippedQuantity - returnedQuantity; }
    public int getNetQuantity() { return netQuantity(); }
}
