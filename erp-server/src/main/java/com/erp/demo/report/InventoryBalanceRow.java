package com.erp.demo.report;

import java.math.BigDecimal;

public record InventoryBalanceRow(Long productId, String sku, String name, String category, String unit,
                                  int openingStock, int stockIn, int stockOut, int currentStock,
                                  int availableStock, int safetyStock, BigDecimal stockValue, String status) {
}
