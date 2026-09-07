package com.erp.demo.report;

public record InventoryWarningRow(Long productId, String sku, String name, String category, String unit,
                                  int currentStock, int safetyStock, int gap, String warningType, String status) {
}
