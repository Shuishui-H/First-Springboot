package com.erp.demo.report;

import java.math.BigDecimal;
import java.util.List;

/**
 * 新增：业务报表模块 —— 经营看板聚合结果（只读展示，不落库）。
 * 字段口径说明：
 * - 采购/入库口径：以「已确认」采购入库单为准（对应开发文档 RPT-01）；
 * - 销售口径：saleQuantity / saleAmount 依赖销售模块，当前架构未实现，统一返回 0，
 *   待后续新增销售模块后，在此处并入对应 Service 即可完成整合（见 ReportService）。
 */
public record DashboardReport(
        int productCount,            // 商品总数
        int stockTotal,              // 库存总量（全部计量单位合计）
        BigDecimal inventoryValue,   // 库存货值（按当前销售价估算）
        int lowStockCount,           // 低于安全库存的商品数
        int purchaseOrderCount,      // 采购订单总数
        int purchasePendingCount,    // 待审核采购订单数
        int stockInQuantity,         // 累计已确认入库数量
        BigDecimal purchaseAmount,   // 累计已确认采购金额
        int saleQuantity,            // 累计销售出库数量（待销售模块，当前为 0）
        BigDecimal saleAmount,       // 累计销售金额（待销售模块，当前为 0）
        List<DailyPoint> trend       // 近 7 日趋势
) {
    /**
     * 新增：趋势单日指标。
     */
    public record DailyPoint(
            String date,             // 日期 yyyy-MM-dd
            int stockIn,             // 当日入库数量
            BigDecimal purchaseAmount, // 当日入库采购金额
            int sale,                // 当日销售数量（待销售模块，当前为 0）
            BigDecimal saleAmount    // 当日销售金额（待销售模块，当前为 0）
    ) {
    }
}
