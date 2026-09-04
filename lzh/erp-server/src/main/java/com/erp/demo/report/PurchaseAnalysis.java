package com.erp.demo.report;

import java.math.BigDecimal;
import java.util.List;

/**
 * 新增：业务报表模块 —— 采购分析聚合结果（对应开发文档 RPT-03）。
 * 三个维度：按供应商 / 按订单状态 / 近 12 个月采购趋势。
 */
public record PurchaseAnalysis(
        List<SupplierLine> suppliers, // 按供应商维度
        List<StatusLine> statuses,    // 按订单状态维度
        List<MonthlyPoint> months     // 近 12 个月采购趋势
) {
    /**
     * 新增：供应商采购汇总行。
     */
    public record SupplierLine(
            Long id,
            String name,
            int orderCount,          // 订单数
            int orderQuantity,       // 订单采购数量
            BigDecimal orderAmount,  // 订单采购金额
            int receivedQuantity,    // 已入库数量
            BigDecimal receivedAmount // 已入库金额
    ) {
    }

    /**
     * 新增：订单状态汇总行。
     */
    public record StatusLine(
            String status,           // 订单状态（草稿/待审核/已审核/已完成/已驳回/已作废）
            int orderCount,          // 订单数
            BigDecimal orderAmount   // 订单金额合计
    ) {
    }

    /**
     * 新增：月度采购金额趋势点。月份格式 yyyy-MM。
     */
    public record MonthlyPoint(
            String month,
            BigDecimal orderAmount,   // 当月订单采购金额
            BigDecimal receivedAmount // 当月已确认入库金额
    ) {
    }
}
