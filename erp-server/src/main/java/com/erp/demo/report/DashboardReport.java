package com.erp.demo.report;

import java.math.BigDecimal;
import java.util.List;

public record DashboardReport(int productCount, int stockTotal, BigDecimal inventoryValue, int lowStockCount,
                              int purchaseOrderCount, int purchasePendingCount, int stockInQuantity,
                              BigDecimal purchaseAmount, int saleQuantity, BigDecimal saleAmount,
                              List<DailyPoint> trend) {
    public record DailyPoint(String date, int stockIn, BigDecimal purchaseAmount, int sale, BigDecimal saleAmount) {}
}
