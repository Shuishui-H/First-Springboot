package com.erp.demo.overview;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/** 经营概览首屏的只读聚合返回模型。 */
public record OverviewResponse(Meta meta, Metrics metrics, List<Todo> todos, List<Risk> risks,
                               List<Activity> activities, List<TrendPoint> trend) {
    public record Meta(String range, LocalDate startDate, LocalDate endDate, LocalDateTime generatedAt) {}
    public record Metrics(BigDecimal salesAmount, BigDecimal purchaseAmount, int stockOutQuantity,
                          BigDecimal inventoryValue, int lowStockCount) {}
    public record Todo(String key, String title, int count, String module, String status, String action) {}
    public record Risk(Long productId, String sku, String name, String warehouseName, int totalStock, int availableQuantity,
                       int safetyStock, int gap, String warningType) {}
    public record Activity(String flowNo, String businessType, String productName, String warehouseName,
                           int changeQuantity, String sourceNo, LocalDateTime time) {}
    public record TrendPoint(LocalDate date, BigDecimal purchaseAmount, BigDecimal salesAmount,
                             int stockInQuantity, int stockOutQuantity) {}
}
