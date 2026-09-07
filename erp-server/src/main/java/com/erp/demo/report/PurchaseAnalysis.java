package com.erp.demo.report;

import java.math.BigDecimal;
import java.util.List;

public record PurchaseAnalysis(List<SupplierLine> suppliers, List<StatusLine> statuses, List<MonthlyPoint> months) {
    public record SupplierLine(Long id, String name, int orderCount, int orderQuantity, BigDecimal orderAmount,
                               int receivedQuantity, BigDecimal receivedAmount) {}
    public record StatusLine(String status, int orderCount, BigDecimal orderAmount) {}
    public record MonthlyPoint(String month, BigDecimal orderAmount, BigDecimal receivedAmount) {}
}
